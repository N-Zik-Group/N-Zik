package app.n_zik.android.components.settings.accounts

import android.content.Context
import android.view.View
import android.webkit.WebResourceError
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.n_zik.android.components.ui.AccountSheetWebView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.glass.CustomModalBottomSheet
import app.n_zik.android.components.menu.MenuSheetDragHandle
import app.n_zik.android.components.settings.appearance.preview.prepareCreatorThemeStoreFork
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.ui.screens.profiles.profileSecurePrefs
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getActiveProfile
import java.net.URI
import java.net.URL
import android.content.ClipData
import android.content.ClipboardManager
import android.os.SystemClock
import java.net.URLEncoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

private const val clientId = "Ov23lidRTeOCWplQz9iZ"
private const val githubDevicePage = "https://github.com/login/device"
private const val githubPersonalInstallationsPage = "https://github.com/settings/installations"
private const val githubAppInstallationPage = "https://github.com/apps/n-zik-sign-in/installations/new"
private const val githubAppInstallationTargetPage = "https://github.com/apps/n-zik-sign-in/installations/select_target"

internal fun confirmedGitHubInstallationAccount(url: String, notice: String): String? {
    val location = runCatching { URI(url) }.getOrNull() ?: return null
    if (location.scheme != "https" || location.host != "github.com" ||
        !Regex("(?:/organizations/[A-Za-z0-9-]+)?/settings/installations/[0-9]+/?").matches(location.path.orEmpty())
    ) return null
    return Regex("Okay, N-Zik Sign In was installed on the @([A-Za-z0-9-]{1,39}) account[.]")
        .find(notice)?.groupValues?.get(1)
}

private fun isPersonalInstallationPage(url: String): Boolean {
    val location = runCatching { URI(url) }.getOrNull() ?: return false
    return location.scheme == "https" && location.host == "github.com" &&
        Regex("/settings/installations/[0-9]+/?").matches(location.path.orEmpty())
}

/** Reads saved repository access, excluding an unsaved change to the installation form. */
internal fun githubInstallationAccessScript(expectedLogin: String): String = """
    (() => {
        const login = document.querySelector('meta[name="user-login"]')?.content?.toLowerCase();
        const main = document.querySelector('main') || document.body;
        const lines = main.innerText.toLowerCase().split(/\n/).map(line => line.trim());
        const allRepositories = Array.from(main.querySelectorAll('input[type="radio"][value="all"]'))
            .some(input => input.checked && input.defaultChecked);
        return {
            login: login || '',
            application: lines.includes('n-zik sign in'),
            fork: lines.includes(${JSONObject.quote(expectedLogin.lowercase() + "/n-zik-theme-store")}),
            allRepositories
        };
    })()
""".trimIndent()

internal fun confirmedGitHubInstallationAccess(url: String, expectedLogin: String, pageState: String): String? {
    if (!isPersonalInstallationPage(url)) return null
    val state = runCatching { JSONObject(pageState) }.getOrNull() ?: return null
    if (!state.optString("login").equals(expectedLogin, ignoreCase = true) || !state.optBoolean("application")) return null
    return expectedLogin.takeIf { state.optBoolean("fork") || state.optBoolean("allRepositories") }
}

internal fun nextGitHubPoll(interval: Int, error: String): Int? = when (error) {
    "authorization_pending" -> interval
    "slow_down" -> interval + 5
    else -> null
}

internal class GitHubInstallationSetup(
    private val scope: CoroutineScope,
    private val prepareFork: suspend (GitHubAccount.SetupSession) -> Unit,
    private val onFailure: (Exception) -> Unit,
) {
    internal data class State(val preparing: Boolean = false, val session: GitHubAccount.SetupSession? = null)

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private var preparation: Job? = null
    private var generation = 0L

    fun prepare(session: GitHubAccount.SetupSession) {
        cancel()
        val operation = generation
        mutableState.value = State(preparing = true)
        preparation = scope.launch {
            try {
                prepareFork(session)
                currentCoroutineContext().ensureActive()
                if (generation == operation) mutableState.value = State(session = session)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == operation) {
                    mutableState.value = State()
                    onFailure(error)
                }
            } finally {
                if (generation == operation) preparation = null
            }
        }
    }

    fun consume(session: GitHubAccount.SetupSession): Boolean {
        if (state.value.session !== session) return false
        cancel()
        return true
    }

    fun cancel() {
        generation++
        preparation?.cancel()
        preparation = null
        mutableState.value = State()
    }
}

internal object GitHubAccount {
    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)
    private var attempt: Job? = null
    val installation = GitHubInstallationSetup(
        scope,
        prepareFork = { prepareCreatorThemeStoreFork(it.login, it.token) },
        onFailure = {
            Timber.tag("GitHubAccount").w(it, "Could not prepare Theme Store fork")
            Toaster.e(R.string.github_app_installation_fork_lookup_failed)
        },
    )
    val login = MutableStateFlow<String?>(null)
    val awaiting = MutableStateFlow(false)
    val code = MutableStateFlow<String?>(null)
    val publicationAuthorization = GitHubPublicationAuthorization()
    private var accountStorage: Pair<Context, GitHubAccountStorage>? = null

    @Synchronized
    private fun storage(context: Context): GitHubAccountStorage {
        val app = context.applicationContext
        accountStorage?.let { (owner, storage) -> if (owner === app) return storage }
        return GitHubAccountStorage(
            activeProfile = { getActiveProfile(app) },
            identityStore = { app.getSharedPreferences("github_account_profile_$it", Context.MODE_PRIVATE) },
            encryptedStore = { profile -> profileSecurePrefs(app, profile) },
            legacyIdentityStore = app.getSharedPreferences("github_account", Context.MODE_PRIVATE),
        ).also { accountStorage = app to it }
    }

    fun tokenFor(context: Context, id: Long): String? = storage(context).tokenFor(id)
    fun clearPublicationToken(context: Context) = storage(context).clearPublicationToken()
    fun requireWorkflowAuthorization(context: Context) = storage(context).requireWorkflow()
    fun userId(context: Context): Long? = storage(context).userId()
    fun accountLogin(context: Context): String? = storage(context).login()

    fun read(context: Context) {
        login.value = storage(context).login()
    }

    fun restoreIdentity(context: Context, identity: String, profile: String = getActiveProfile(context)) {
        if (getActiveProfile(context) == profile) cancel()
        storage(context).restoreIdentity(identity, profile)
        if (getActiveProfile(context) == profile) read(context)
    }

    internal class SetupSession(val id: Long, val login: String, val token: String, val expires: Long, val profile: String = "default")

    fun start(context: Context, expectedId: Long? = null) =
        authorize(context, expectedId, onSetup = null)

    fun startSetup(context: Context) =
        authorize(context, expectedId = null, onSetup = installation::prepare)

    fun startPublicationAuthorization(context: Context, expectedId: Long?): String? {
        if (!canAuthorize(context)) return null
        val key = publicationAuthorization.begin(getActiveProfile(context))
        authorize(context, expectedId, onSetup = null, publicationRequest = key)
        return key
    }

    private fun canAuthorize(context: Context): Boolean =
        !awaiting.value && !installation.state.value.preparing && installation.state.value.session == null &&
            !OfflineMode.enabled(context)

    private fun authorize(
        context: Context,
        expectedId: Long?,
        onSetup: ((SetupSession) -> Unit)?,
        publicationRequest: String? = null,
    ) {
        if (!canAuthorize(context)) return
        val app = context.applicationContext
        val profileId = getActiveProfile(app)
        val needsWorkflow = (expectedId ?: userId(app))?.let { storage(app).needsWorkflow(it) } == true
        val requestedScopes = if (needsWorkflow) "public_repo workflow" else "public_repo"
        awaiting.value = true
        attempt = scope.launch {
            try {
                val device = withContext(NzikDispatchers.DATA) {
                    request("https://github.com/login/device/code", mapOf("client_id" to clientId, "scope" to requestedScopes))
                }
                val userCode = device.getString("user_code")
                check(userCode.matches(Regex("[A-Z0-9]{4}-[A-Z0-9]{4}")))
                val deviceCode = device.getString("device_code")
                val deadline = SystemClock.elapsedRealtime() + device.getInt("expires_in") * 1000L
                var interval = device.optInt("interval", 5).coerceAtLeast(5)
                var nextPollAt = SystemClock.elapsedRealtime()
                code.value = userCode
                while (true) {
                    delay((nextPollAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
                    check(SystemClock.elapsedRealtime() < deadline && !OfflineMode.enabled(app) && getActiveProfile(app) == profileId)
                    val pollStartedAt = SystemClock.elapsedRealtime()
                    val result = withContext(NzikDispatchers.DATA) {
                        request("https://github.com/login/oauth/access_token", mapOf(
                            "client_id" to clientId,
                            "device_code" to deviceCode,
                            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                        ))
                    }
                    val token = result.optString("access_token")
                    if (token.isEmpty()) {
                        val error = result.optString("error")
                        interval = checkNotNull(nextGitHubPoll(interval, error))
                        val intervalStart = if (error == "slow_down") SystemClock.elapsedRealtime() else pollStartedAt
                        nextPollAt = intervalStart + interval * 1000L
                        continue
                    }
                    // The browser has served its purpose once GitHub returns a token.
                    if (onSetup == null) code.value = null
                    val profile = withContext(NzikDispatchers.DATA) {
                        request("https://api.github.com/user", token = token)
                    }
                    val id = profile.getLong("id")
                    if (expectedId != null && expectedId != id) {
                        Toaster.e(R.string.theme_store_creator_publisher_account)
                        break
                    }
                    val grantedScopes = result.optString("scope").split(',').map(String::trim)
                    check("public_repo" in grantedScopes && (!needsWorkflow || "workflow" in grantedScopes))
                    val handle = profile.getString("login")
                    check(id > 0 && handle.matches(Regex("[A-Za-z0-9-]{1,39}")))
                    val blockedMessage = githubContributionBlock(id)
                    check(getActiveProfile(app) == profileId)
                    if (blockedMessage != null) {
                        disconnect(app)
                        Toaster.e(blockedMessage, android.widget.Toast.LENGTH_LONG)
                        break
                    }
                    val expiresIn = result.optLong("expires_in", 0)
                    val expires = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000L else Long.MAX_VALUE
                    currentCoroutineContext().ensureActive()
                    check(getActiveProfile(app) == profileId)
                    val session = SetupSession(id, handle, token, expires, profileId)
                    if (onSetup != null) {
                        onSetup(session)
                    } else {
                        saveSession(app, session)
                        Toaster.s(R.string.github_account_connected)
                        publicationRequest?.let { publicationAuthorization.authorize(it, profileId) }
                    }
                    break
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toaster.e(R.string.github_account_login_failed)
            } finally {
                code.value = null
                awaiting.value = false
                publicationRequest?.let(publicationAuthorization::finish)
            }
        }
    }

    fun completeSetup(context: Context, session: SetupSession): Boolean {
        if (!installation.consume(session)) return false
        if (getActiveProfile(context) != session.profile) return false
        saveSession(context.applicationContext, session)
        return true
    }

    private fun saveSession(context: Context, session: SetupSession) {
        check(getActiveProfile(context) == session.profile)
        check(storage(context).save(session.id, session.login, session.token, session.expires, session.profile))
        read(context)
    }

    fun cancel() {
        attempt?.cancel()
        installation.cancel()
        publicationAuthorization.cancel()
    }

    fun disconnect(context: Context) {
        cancel()
        storage(context).disconnect()
        read(context)
    }

    private fun request(url: String, form: Map<String, String>? = null, token: String? = null): JSONObject {
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "N-Zik Sign In")
            token?.let { connection.setRequestProperty("Authorization", "Bearer " + it) }
            if (form != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                val payload = form.entries.joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }
                connection.outputStream.use { it.write(payload.toByteArray()) }
            }
            check(connection.responseCode == 200)
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
internal fun GitHubAccountCard(horizontalPadding: Dp? = null, showAppInstallation: Boolean = false) {
    val context = LocalContext.current
    val login by GitHubAccount.login.collectAsStateWithLifecycle()
    val awaiting by GitHubAccount.awaiting.collectAsStateWithLifecycle()
    val code by GitHubAccount.code.collectAsStateWithLifecycle()
    val offline by OfflineMode.rememberEnabled()
    androidx.compose.runtime.LaunchedEffect(context, login, offline) {
        val id = GitHubAccount.userId(context) ?: return@LaunchedEffect
        if (!offline) {
            try {
                val message = githubContributionBlock(id)
                if (message != null && GitHubAccount.userId(context) == id) {
                    GitHubAccount.disconnect(context)
                    Toaster.e(message, android.widget.Toast.LENGTH_LONG)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Publication and new connections check the policy independently.
            }
        }
    }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var showInstallationManagement by rememberSaveable { mutableStateOf(false) }
    val installationState by GitHubAccount.installation.state.collectAsStateWithLifecycle()
    val pendingSetup = installationState.session
    val checkingFork = installationState.preparing
    androidx.compose.runtime.LaunchedEffect(context) { GitHubAccount.read(context) }
    val content: @Composable () -> Unit = {
        OtherSettingsEntry(
            title = stringResource(if (login == null) R.string.github_account_login else R.string.github_account_logout),
            text = login?.let { "@$it" } ?: code?.let { stringResource(R.string.github_account_code, it) } ?: stringResource(
                if (offline) R.string.github_account_offline else R.string.github_account_description
            ),
            icon = R.drawable.github_icon,
            isEnabled = (!awaiting || code != null) && (!offline || login != null),
            onClick = {
                when {
                    login != null -> {
                        showInstallationManagement = false
                        GitHubAccount.disconnect(context)
                    }
                    else -> {
                        showLogin = true
                        if (!awaiting) {
                            if (showAppInstallation) {
                                GitHubAccount.startSetup(context)
                            } else GitHubAccount.start(context)
                        }
                    }
                }
            },
        )
        val connectedLogin = login
        if (showAppInstallation && connectedLogin != null) OtherSettingsEntry(
            title = stringResource(R.string.github_app_installation),
            text = stringResource(R.string.github_app_installation_description),
            icon = R.drawable.github_icon,
            isEnabled = !offline && !checkingFork,
            onClick = { showInstallationManagement = true },
        )
        if (awaiting) OtherSettingsEntry(
            title = stringResource(android.R.string.cancel),
            text = "",
            icon = R.drawable.github_icon,
            onClick = GitHubAccount::cancel,
        )
    }
    val title = stringResource(R.string.common_brand_github)
    if (horizontalPadding == null) {
        SettingsSectionCard(title = title, icon = R.drawable.github_icon, content = { content() })
    } else {
        SettingsSectionCard(title = title, icon = R.drawable.github_icon, horizontalPadding = horizontalPadding, content = { content() })
    }
    val userCode = code
    val installationPage = showLogin && pendingSetup != null && !offline
    val managementPage = showInstallationManagement && login != null && !offline
    val loginPage = showLogin && (userCode != null || checkingFork)
    if (installationPage || managementPage || loginPage) {
        if (showLogin && userCode != null) CopyGitHubDeviceCode(userCode)
        GitHubWebView(
            when {
                installationPage -> githubAppInstallationPage
                managementPage -> githubPersonalInstallationsPage
                else -> githubDevicePage
            },
            preparingInstallation = checkingFork,
            installationLogin = if (installationPage) pendingSetup?.login else null,
            onInstallationConfirmed = if (installationPage) { account ->
                showInstallationManagement = false
                showLogin = false
                if (account.equals(pendingSetup.login, ignoreCase = true)) {
                    if (GitHubAccount.completeSetup(context, pendingSetup)) {
                        Toaster.s(R.string.github_app_installation_confirmed)
                    }
                } else {
                    GitHubAccount.cancel()
                    Toaster.e(context.getString(R.string.github_app_installation_other_account, account))
                }
            } else null,
        ) {
            showInstallationManagement = false
            showLogin = false
            GitHubAccount.cancel()
        }
    }
    androidx.compose.runtime.LaunchedEffect(awaiting, checkingFork, pendingSetup) {
        if (!awaiting && !checkingFork && pendingSetup == null) showLogin = false
    }
}

@Composable
internal fun GitHubLoginWebView(code: String, dismiss: () -> Unit) {
    CopyGitHubDeviceCode(code)
    GitHubWebView(githubDevicePage, dismiss = dismiss)
}

@Composable
private fun CopyGitHubDeviceCode(code: String) {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(code) {
        context.getSystemService(ClipboardManager::class.java)
            ?.let {
                it.setPrimaryClip(ClipData.newPlainText("GitHub", code))
                Toaster.s(R.string.github_account_code_copied)
            }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun GitHubWebView(
    url: String,
    preparingInstallation: Boolean = false,
    installationLogin: String? = null,
    onInstallationConfirmed: ((String) -> Unit)? = null,
    dismiss: () -> Unit,
) {
    val confirmation by androidx.compose.runtime.rememberUpdatedState(onInstallationConfirmed)
    val expectedInstallationLogin by androidx.compose.runtime.rememberUpdatedState(installationLogin)
    var waitingForNextPage by remember { mutableStateOf(false) }
    val waiting = waitingForNextPage || preparingInstallation
    var loaderTimedOut by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(waiting) {
        loaderTimedOut = false
        if (waiting) {
            delay(5_000)
            loaderTimedOut = true
        }
    }
    val showLoader = waiting && !loaderTimedOut
    val active = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        active.set(true)
        onDispose { active.set(false) }
    }
    CustomModalBottomSheet(
        showSheet = true,
        onDismissRequest = dismiss,
        containerColor = colorPalette().background0,
        contentColor = colorPalette().text,
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = uiRoundnessShape(),
        dragHandle = { MenuSheetDragHandle(minTouchHeight = 48.dp) },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (installationLogin != null) {
                Text(
                    stringResource(R.string.github_app_installation_instructions, installationLogin),
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { webContext ->
                        AccountSheetWebView(webContext).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            webViewClient = object : WebViewClient() {
                                private var checkedPersonalInstallation = false

                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                    request.url.scheme != "https" || request.url.host != "github.com"

                                private fun checkDeviceConfirmation(view: WebView, pageUrl: String) {
                                    if (!active.get() || view.tag != githubDevicePage || view.url != pageUrl) return
                                    val page = android.net.Uri.parse(pageUrl)
                                    if (page.scheme != "https" || page.host != "github.com") return
                                    view.evaluateJavascript(
                                        "document.body?.innerText.includes('Your device is now connected.')",
                                    ) { confirmed ->
                                        if (active.get() && view.tag == githubDevicePage && view.url == pageUrl && confirmed == "true") {
                                            waitingForNextPage = true
                                        }
                                    }
                                }

                                override fun onPageCommitVisible(view: WebView, pageUrl: String) {
                                    if (!active.get() || view.url != pageUrl) return
                                    if (view.tag == githubDevicePage) checkDeviceConfirmation(view, pageUrl)
                                    else waitingForNextPage = false
                                }

                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    if (active.get() && request.isForMainFrame && view.tag != githubDevicePage) {
                                        waitingForNextPage = false
                                    }
                                }

                                override fun onPageFinished(view: WebView, pageUrl: String?) {
                                    if (pageUrl == null) return
                                    checkDeviceConfirmation(view, pageUrl)
                                    if (confirmation == null) return
                                    if (isPersonalInstallationPage(pageUrl)) {
                                        checkConfiguredFork(view, pageUrl)
                                        return
                                    }
                                    val pageLocation = pageUrl.substringBefore('?').substringBefore('#').trimEnd('/')
                                    if (!checkedPersonalInstallation &&
                                        (pageLocation == githubAppInstallationPage || pageLocation == githubAppInstallationTargetPage) &&
                                        view.tag == githubAppInstallationPage
                                    ) {
                                        checkedPersonalInstallation = true
                                        checkExistingPersonalInstallation(view, pageUrl)
                                        return
                                    }
                                    view.evaluateJavascript(
                                        "Array.from(document.querySelectorAll('.flash, [role=status], [role=alert]')).map(e => e.textContent.replace(/\\s+/g, ' ').trim()).join(' ')",
                                    ) { notice ->
                                        if (!active.get() || view.url != pageUrl) return@evaluateJavascript
                                        confirmedGitHubInstallationAccount(pageUrl, notice.orEmpty())?.let { account ->
                                            if (active.compareAndSet(true, false)) confirmation?.invoke(account)
                                        }
                                    }
                                }

                                private fun checkExistingPersonalInstallation(view: WebView, pageUrl: String) {
                                    val expectedLogin = expectedInstallationLogin ?: return
                                    view.evaluateJavascript(
                                        """
                                        (() => {
                                            const login = document.querySelector('meta[name="user-login"]')?.content;
                                            if (!login || login.toLowerCase() !== ${JSONObject.quote(expectedLogin.lowercase())}) return null;
                                            return Array.from(document.querySelectorAll('a[href]')).find(link => {
                                                if (link.origin !== location.origin || !/^\/settings\/installations\/\d+\/?$/.test(link.pathname)) return false;
                                                const words = (link.innerText || '').toLowerCase().split(/\s+/);
                                                return words.includes(login.toLowerCase());
                                            })?.href ?? null;
                                        })()
                                        """.trimIndent(),
                                    ) { configureUrl ->
                                        if (!active.get() || view.url != pageUrl || expectedInstallationLogin != expectedLogin) {
                                            return@evaluateJavascript
                                        }
                                        val target = configureUrl.orEmpty().removeSurrounding("\"")
                                        if (isPersonalInstallationPage(target)) view.loadUrl(target)
                                    }
                                }

                                private fun checkConfiguredFork(view: WebView, pageUrl: String) {
                                    val login = expectedInstallationLogin ?: return
                                    view.evaluateJavascript(
                                        githubInstallationAccessScript(login),
                                    ) { configured ->
                                        if (view.url == pageUrl && expectedInstallationLogin == login &&
                                            confirmedGitHubInstallationAccess(pageUrl, login, configured.orEmpty()) != null &&
                                            active.compareAndSet(true, false)
                                        ) confirmation?.invoke(login)
                                    }
                                }
                            }
                        }
                    },
                    onRelease = { it.destroy() },
                    update = { view ->
                        if (view.tag != url) {
                            if (view.tag == githubDevicePage) waitingForNextPage = true
                            view.tag = url
                            view.loadUrl(url)
                        }
                        view.visibility = if (showLoader) View.INVISIBLE else View.VISIBLE
                    },
                )
                if (showLoader) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colorPalette().accent)
                        Text(
                            stringResource(R.string.github_account_finishing_connection),
                            Modifier.padding(top = 12.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
