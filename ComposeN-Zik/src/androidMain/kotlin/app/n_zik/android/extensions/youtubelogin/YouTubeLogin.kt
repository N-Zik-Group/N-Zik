package app.n_zik.android.extensions.youtubelogin

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.n_zik.android.R
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DefaultDialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.SettingsChoiceRadio
import app.n_zik.android.colorPalette
import app.n_zik.android.components.ui.AccountSheetWebView
import app.n_zik.android.components.ui.UiButton
import app.n_zik.android.components.ui.UiTextButton
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.typography
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.encryptedPreferences
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YouTubeChannel
import app.n_zik.innertube.YouTubeSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val GOOGLE_SIGN_IN =
    "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
private const val ADD_GOOGLE_ACCOUNT =
    "https://accounts.google.com/AddSession?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com"
private const val VERIFY_TIMEOUT = 20_000L
private const val CAPTURE_TIMEOUT = 8_000L
private const val GOOGLE_ORIGIN = "https://accounts.google.com/"

private enum class LoginStage { Browser, Opening, Discovering, Selecting, Verifying, Failed, Finished }

private class WebLoginAttempt {
    val page = YouTubeLoginPageGate()
    var operation: Job? = null
    var navigationTimeout: Job? = null
    var pendingChannel: YouTubeChannel? = null

    fun invalidatePage() {
        page.invalidate()
        operation?.cancel()
        operation = null
    }

    fun stop() {
        invalidatePage()
        navigationTimeout?.cancel()
        navigationTimeout = null
        pendingChannel = null
    }
}

private class ChannelSelection(
    val session: YouTubeSession,
    val pageUrl: String,
    val revision: Long,
    val channels: List<YouTubeChannel>,
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLogin(
    onLogin: (String) -> Unit,
    onCancel: () -> Unit,
    changingAccount: Boolean = false,
    isActive: () -> Boolean,
) {
    val palette = colorPalette()
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.encryptedPreferences }
    val profile = remember(context) { getActiveProfile(context) }
    val scope = rememberCoroutineScope()
    val login by rememberUpdatedState(onLogin)
    val cancel by rememberUpdatedState(onCancel)
    val sheetIsActive by rememberUpdatedState(isActive)
    val attempt = remember { WebLoginAttempt() }
    val initialUrl = if (changingAccount) MUSIC_HOME else GOOGLE_SIGN_IN
    var stage by remember { mutableStateOf(LoginStage.Browser) }
    var browser by remember { mutableStateOf<WebView?>(null) }
    var selection by remember { mutableStateOf<ChannelSelection?>(null) }
    var selected by remember { mutableStateOf<YouTubeChannel?>(null) }
    var devicePromptUrl by remember { mutableStateOf<String?>(null) }
    var resumedGoogleSession by remember { mutableStateOf(false) }
    val supportsBrowserProfiles = remember { supportsYouTubeBrowserProfiles() }
    var knownAccounts by remember(prefs) { mutableStateOf(readYouTubeBrowserAccounts(prefs)) }
    var webProfileName by remember(prefs) {
        mutableStateOf(
            readActiveYouTubeBrowserAccount(prefs)?.takeIf { supportsBrowserProfiles }
                ?: newYouTubeBrowserProfileName().takeIf {
                    supportsBrowserProfiles && prefs.readYouTubeSession().cookie == null
                },
        )
    }
    var browserStartUrl by remember { mutableStateOf(initialUrl) }
    var pendingSavedChannel by remember { mutableStateOf<YouTubeBrowserChannel?>(null) }
    var googleSessionBeforePrompt by remember { mutableStateOf(emptyMap<String, String>()) }

    fun fail() {
        attempt.stop()
        selection = null
        selected = null
        stage = LoginStage.Failed
    }

    fun isCurrent(view: WebView, url: String, revision: Long): Boolean {
        if (!sheetIsActive() || browser !== view || !attempt.page.isCurrent(revision) ||
            stage == LoginStage.Finished
        ) return false
        if (view.url == url && getActiveProfile(context) == profile && context.encryptedPreferences === prefs) return true
        fail()
        return false
    }

    fun open(view: WebView, url: String, channel: YouTubeChannel? = null) {
        attempt.stop()
        devicePromptUrl = null
        val cookies = view.youtubeLoginCookieManager()
        googleSessionBeforePrompt = googleSessionCookies(cookies.getCookie(GOOGLE_ORIGIN))
        attempt.pendingChannel = channel
        selection = null
        selected = null
        stage = if (isYouTubeMusicPage(url) || channel != null) LoginStage.Opening else LoginStage.Browser
        attempt.navigationTimeout = scope.launch(NzikDispatchers.UI) {
            delay(VERIFY_TIMEOUT)
            if (stage == LoginStage.Opening || attempt.pendingChannel != null) fail()
        }
        view.loadUrl(url)
    }

    fun openBrowserAccount(profileName: String?, url: String, channel: YouTubeBrowserChannel? = null) {
        pendingSavedChannel = channel
        resumedGoogleSession = false
        if (profileName == webProfileName) {
            browser?.let { open(it, url) }
            return
        }
        attempt.stop()
        devicePromptUrl = null
        selection = null
        selected = null
        stage = LoginStage.Browser
        browserStartUrl = url
        webProfileName = profileName
    }

    fun signOutBrowserAccount(profileName: String) {
        if (profileName == webProfileName) attempt.stop()
        clearYouTubeBrowserProfile(profileName)
        if (profileName == readActiveYouTubeBrowserAccount(prefs)) clearSavedYouTubeSession(prefs)
        removeYouTubeBrowserAccount(prefs, profileName)
        knownAccounts = knownAccounts.filter { it.profileName != profileName }
        if (profileName == webProfileName) {
            val fallback = knownAccounts.firstOrNull()
            if (fallback == null) openBrowserAccount(newYouTubeBrowserProfileName(), GOOGLE_SIGN_IN)
            else openBrowserAccount(fallback.profileName, MUSIC_HOME)
        }
    }

    fun close() {
        attempt.stop()
        stage = LoginStage.Finished
        browser?.stopLoading()
        cancel()
    }

    suspend fun validate(
        session: YouTubeSession,
        channel: YouTubeChannel?,
        freshlyCaptured: Boolean,
        current: () -> Boolean,
    ): Boolean {
        val details = withTimeoutOrNull(VERIFY_TIMEOUT) {
            verifyYouTubeChoice(
                session, channel, freshlyCaptured, current,
                fetchAccount = Innertube::verifyYouTubeSession,
                fetchChoices = Innertube::discoverYouTubeChannels,
            )
        } ?: return false
        val cookie = session.cookie ?: return false
        val view = browser ?: return false
        val url = view.url ?: return false
        val cookies = view.youtubeLoginCookieManager()
        if (!current() || cookies.getCookie(url) != cookie) return false
        val account = details.info
        val email = account.email ?: channel?.email ?: withTimeoutOrNull(3_000) {
            try {
                Innertube.fetchYouTubeChannelEmail(session, details)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        if (!current() || cookies.getCookie(url) != cookie) return false
        saveVerifiedYouTubeSession(prefs, session, account.copy(email = email), webProfileName, knownAccounts)
        stage = LoginStage.Finished
        browser?.stopLoading()
        login(cookie)
        return true
    }

    fun select(view: WebView, source: ChannelSelection, channel: YouTubeChannel) {
        val current = { isCurrent(view, source.pageUrl, source.revision) }
        if (!current()) {
            fail()
            return
        }
        stage = LoginStage.Verifying
        attempt.operation = scope.launch(NzikDispatchers.UI) {
            val session = channel.currentOrDirectSession(source.session)
            val validated = try {
                session != null && validate(session, channel, session == source.session, current)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (!validated && current()) {
                val url = channel.navigationUrl()
                if (url != null) open(view, url, channel) else fail()
            }
        }
    }

    BackHandler(enabled = stage != LoginStage.Finished) {
        val view = browser
        if (stage == LoginStage.Browser && view?.canGoBack() == true) view.goBack() else close()
    }

    LaunchedEffect(browser, devicePromptUrl, stage, resumedGoogleSession) {
        val view = browser ?: return@LaunchedEffect
        val promptUrl = devicePromptUrl ?: return@LaunchedEffect
        if (stage != LoginStage.Browser || resumedGoogleSession) return@LaunchedEffect
        val cookies = view.youtubeLoginCookieManager()
        while (sheetIsActive() && stage == LoginStage.Browser && devicePromptUrl == promptUrl) {
            delay(1_000)
            if (view.url != promptUrl) continue
            val current = googleSessionCookies(cookies.getCookie(GOOGLE_ORIGIN))
            if (!sheetIsActive() || view.url != promptUrl || stage != LoginStage.Browser) break
            if (hasNewGoogleSession(googleSessionBeforePrompt, current)) {
                resumedGoogleSession = true
                open(view, GOOGLE_SIGN_IN, attempt.pendingChannel)
                break
            }
        }
    }

    key(webProfileName) {
        val boundProfileName = webProfileName
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        AccountSheetWebView(viewContext).apply {
                            setYouTubeBrowserProfile(boundProfileName)
                            browser = this
                            // Google checks its YouTube sign-in session across origins.
                            youtubeLoginCookieManager().setAcceptThirdPartyCookies(this, true)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = false
                                allowContentAccess = false
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                    request.isForMainFrame && !isYouTubeLoginPage(request.url.toString())

                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    if (browser !== view) return
                                    attempt.invalidatePage()
                                    devicePromptUrl = url?.takeIf(::isGoogleDevicePrompt)
                                    if (stage == LoginStage.Failed || stage == LoginStage.Finished) return
                                    if (url != null && !isYouTubeMusicPage(url)) stage = LoginStage.Browser
                                    else if (stage != LoginStage.Opening) stage = LoginStage.Browser
                                }

                                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                    if (browser !== view) return
                                    devicePromptUrl = url?.takeIf(::isGoogleDevicePrompt)
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    if (browser !== view || url == null || !isYouTubeMusicPage(url) || view.url != url ||
                                        stage !in setOf(LoginStage.Browser, LoginStage.Opening) ||
                                        attempt.operation?.isActive == true
                                    ) return
                                    val revision = attempt.page.revision
                                    val channel = attempt.pendingChannel
                                    val current = { isCurrent(view, url, revision) }
                                    attempt.navigationTimeout?.cancel()
                                    stage = if (channel == null) LoginStage.Discovering else LoginStage.Verifying
                                    attempt.operation = scope.launch(NzikDispatchers.UI) {
                                        try {
                                            val session = view.captureYouTubeSession(url, current)
                                            if (!current()) return@launch
                                            if (session == null) {
                                                fail()
                                                return@launch
                                            }
                                            if (channel != null) {
                                                if (!validate(session, channel, true, current) && current()) fail()
                                                return@launch
                                            }
                                            val discovered = withTimeoutOrNull(VERIFY_TIMEOUT) {
                                                Innertube.discoverYouTubeChannels(session)
                                            }
                                            if (!current()) return@launch
                                            if (discovered == null) {
                                                fail()
                                                return@launch
                                            }
                                            val choices = selectableYouTubeChannels(discovered, session)
                                            if (choices.size > 1 || (changingAccount && choices.size == 1)) {
                                                val discoveredChannels = withTimeoutOrNull(3_000) {
                                                    try {
                                                        Innertube.fetchYouTubeChannelEmails(session, choices)
                                                    } catch (cancelled: CancellationException) {
                                                        throw cancelled
                                                    } catch (_: Exception) {
                                                        null
                                                    }
                                                } ?: choices
                                                if (!current()) return@launch
                                                knownAccounts = rememberYouTubeBrowserAccount(prefs, webProfileName, discoveredChannels)
                                                val channels = knownAccounts.uniqueDiscoveredChannels(webProfileName, discoveredChannels)
                                                val source = ChannelSelection(session, url, revision, channels)
                                                selection = source
                                                val requested = pendingSavedChannel
                                                pendingSavedChannel = null
                                                val matchingChoice = requested?.let { saved -> channels.firstOrNull(saved::matches) }
                                                if (matchingChoice != null) {
                                                    select(view, source, matchingChoice)
                                                    return@launch
                                                }
                                                val storedSession = prefs.readYouTubeSession()
                                                selected = channels.firstOrNull {
                                                    webProfileName == readActiveYouTubeBrowserAccount(prefs) &&
                                                        it.matchesStoredSession(storedSession)
                                                }
                                                    ?: channels.firstOrNull { it.selected } ?: channels.firstOrNull()
                                                stage = LoginStage.Selecting
                                            } else if (choices.size == 1) {
                                                knownAccounts = rememberYouTubeBrowserAccount(prefs, webProfileName, choices)
                                                select(view, ChannelSelection(session, url, revision, choices), choices.single())
                                            } else if (changingAccount) {
                                                fail()
                                            } else if (!validate(session, null, true, current) && current()) {
                                                fail()
                                            }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            if (current()) fail()
                                        }
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError,
                                ) {
                                    if (request.isForMainFrame && view.url == request.url.toString() &&
                                        stage != LoginStage.Finished
                                    ) fail()
                                }

                                override fun onReceivedHttpError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    errorResponse: WebResourceResponse,
                                ) {
                                    if (request.isForMainFrame && view.url == request.url.toString() &&
                                        stage != LoginStage.Finished
                                    ) fail()
                                }
                            }
                            open(this, browserStartUrl)
                        }
                    },
                    update = { it.visibility = if (stage == LoginStage.Browser) View.VISIBLE else View.INVISIBLE },
                    onRelease = {
                        if (browser === it) {
                            attempt.stop()
                            browser = null
                        }
                        it.stopLoading()
                        it.destroy()
                    },
                )

                when (stage) {
                    LoginStage.Selecting -> selection?.let { source ->
                        ChannelPicker(
                            channels = source.channels,
                            selection = selected,
                            accounts = knownAccounts,
                            currentProfile = webProfileName,
                            onSelect = { channel -> browser?.let { select(it, source, channel) } },
                            onSelectAccount = { account, channel -> openBrowserAccount(account.profileName, MUSIC_HOME, channel) },
                            onSignOutAccount = ::signOutBrowserAccount,
                            onAddAccount = {
                                if (supportsBrowserProfiles) {
                                    openBrowserAccount(newYouTubeBrowserProfileName(), GOOGLE_SIGN_IN)
                                } else {
                                    openBrowserAccount(null, ADD_GOOGLE_ACCOUNT)
                                }
                            },
                        )
                    }
                    LoginStage.Opening, LoginStage.Discovering, LoginStage.Verifying -> Column(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = palette.accent)
                        Text(stringResource(R.string.account_youtube_checking_login), color = palette.text)
                    }
                    LoginStage.Failed -> Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.account_youtube_verify_failed), color = palette.text)
                        UiButton(
                            onClick = { browser?.let { open(it, initialUrl) } },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                        ) {
                            Text(stringResource(R.string.common_action_retry))
                        }
                        val signInUrl = if (changingAccount && !supportsBrowserProfiles) ADD_GOOGLE_ACCOUNT else GOOGLE_SIGN_IN
                        val signInLabel = if (changingAccount) R.string.account_youtube_add_google_account else R.string.account_action_login
                        UiTextButton(onClick = { openBrowserAccount(webProfileName, signInUrl) }) {
                            Text(stringResource(signInLabel), color = palette.accent)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun ChannelPicker(
    channels: List<YouTubeChannel>,
    selection: YouTubeChannel?,
    accounts: List<YouTubeBrowserAccount>,
    currentProfile: String?,
    onSelect: (YouTubeChannel) -> Unit,
    onSelectAccount: (YouTubeBrowserAccount, YouTubeBrowserChannel?) -> Unit,
    onSignOutAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
) {
    val palette = colorPalette()
    var signOutTarget by remember { mutableStateOf<String?>(null) }
    signOutTarget?.let { profileName ->
        DefaultDialog(
            title = stringResource(R.string.account_youtube_google_sign_out),
            onDismiss = { signOutTarget = null },
            footer = {
                ButtonRow(
                    listOf(
                        DialogButton(stringResource(R.string.common_action_cancel), { signOutTarget = null }),
                        DialogButton(
                            text = stringResource(R.string.account_action_logout),
                            danger = true,
                            onClick = {
                                signOutTarget = null
                                onSignOutAccount(profileName)
                            },
                        ),
                    ),
                )
            },
        ) {
            Text(
                text = stringResource(R.string.account_youtube_google_sign_out_description),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = typography().xs,
                color = palette.text,
            )
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.account_youtube_choose_channel),
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
            style = typography().s.semiBold,
            color = palette.text,
            textAlign = TextAlign.Center,
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).selectableGroup()) {
            for (account in accounts) {
                val signOut = account.profileName?.let { name -> { signOutTarget = name } }
                if (account.profileName == currentProfile && channels.isNotEmpty()) {
                    channels.groupBy { it.email }.entries.forEachIndexed { index, (email, group) ->
                        GoogleAccountHeader(
                            email = email ?: account.label.takeIf(String::isNotBlank),
                            modifier = Modifier.padding(bottom = 4.dp),
                            onSignOut = signOut.takeIf { index == 0 },
                        )
                        for (channel in group) {
                            ChannelRadioRow(channel.name, channel.handle, channel.thumbnailUrl, selection == channel) { onSelect(channel) }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    continue
                }
                GoogleAccountHeader(account.label.takeIf(String::isNotBlank), Modifier.padding(bottom = 4.dp), signOut)
                val savedChannels = accounts.uniqueSavedChannels(account)
                if (savedChannels.isEmpty()) {
                    if (account.profileName != currentProfile) {
                        UiTextButton(onClick = { onSelectAccount(account, null) }) {
                            Text(stringResource(R.string.account_youtube_google_select), color = palette.accent)
                        }
                    }
                } else {
                    for (channel in savedChannels) {
                        ChannelRadioRow(channel.name, channel.handle, channel.avatar, false) {
                            onSelectAccount(account, channel)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        UiButton(
            onClick = onAddAccount,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.background2, contentColor = palette.text),
        ) {
            Text(stringResource(R.string.account_youtube_add_google_account))
        }
    }
}

@Composable
private fun ChannelRadioRow(name: String, handle: String?, avatar: String?, selected: Boolean, onClick: () -> Unit) {
    val palette = colorPalette()
    val avatarShape = artistThumbnailShape()
    Row(
        modifier = Modifier.fillMaxWidth().clip(uiRoundnessShape())
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsChoiceRadio(selected = selected)
        if (avatar != null) {
            ImageCacheFactory.AsyncImage(avatar, modifier = Modifier.size(40.dp).clip(avatarShape), shape = avatarShape)
        } else {
            Icon(
                painter = painterResource(R.drawable.person),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(avatarShape).padding(8.dp),
                tint = palette.textSecondary,
            )
        }
        Column {
            Text(name, style = typography().xs.semiBold, color = palette.text)
            handle?.let { Text(it, style = typography().xxs, color = palette.textSecondary) }
        }
    }
}

@Composable
private fun GoogleAccountHeader(email: String?, modifier: Modifier, onSignOut: (() -> Unit)?) {
    val palette = colorPalette()
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.account_youtube_google_account), style = typography().xs.semiBold, color = palette.text)
            email?.let { Text(it, style = typography().xxs, color = palette.textSecondary) }
        }
        if (onSignOut != null) {
            UiButton(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = palette.red, contentColor = Color.White),
            ) {
                Text(stringResource(R.string.account_action_logout))
            }
        }
    }
}

private suspend fun WebView.captureYouTubeSession(
    pageUrl: String,
    isCurrent: () -> Boolean,
): YouTubeSession? {
    val cookies = youtubeLoginCookieManager()
    var partial: YouTubeSession? = null
    val complete = withTimeoutOrNull(CAPTURE_TIMEOUT) {
        while (isCurrent()) {
            val raw = suspendCancellableCoroutine<String?> { continuation ->
                evaluateJavascript(CAPTURE_CONFIG) { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
            }
            if (!isCurrent()) return@withTimeoutOrNull null
            val cookie = cookies.getCookie(pageUrl)
            parseYouTubePageSession(raw, pageUrl, cookie)?.let { return@withTimeoutOrNull it }
            partial = parseYouTubePageSession(raw, pageUrl, cookie, requireRoutingFields = false)
            delay(150)
        }
        null
    }
    return (complete ?: partial)?.takeIf { isCurrent() && cookies.getCookie(pageUrl) == it.cookie }
}

private val CAPTURE_CONFIG = """
    (() => {
        if (location.origin !== "https://music.youtube.com" || document.readyState !== "complete") return null;
        const read = (key) => window.ytcfg?.get?.(key) ?? window.yt?.config_?.[key] ?? null;
        return {
            url: location.href,
            loggedIn: read("LOGGED_IN"),
            visitorData: read("VISITOR_DATA"),
            dataSyncId: read("DATASYNC_ID"),
            authUser: read("SESSION_INDEX")
        };
    })()
""".trimIndent()
