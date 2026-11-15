package app.n_zik.android.components.settings.accounts

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.glass.CustomModalBottomSheet
import app.n_zik.android.components.menu.MenuSheetDragHandle
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.encryptedPreferences
import java.net.URL
import android.content.ClipData
import android.content.ClipboardManager
import android.os.SystemClock
import java.net.URLEncoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val clientId = "Ov23lidRTeOCWplQz9iZ"
private const val identityKey = "identity"
private const val githubDevicePage = "https://github.com/login/device"

internal fun nextGitHubPoll(interval: Int, error: String): Int? = when (error) {
    "authorization_pending" -> interval
    "slow_down" -> interval + 5
    else -> null
}

internal object GitHubAccount {
    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)
    private var attempt: Job? = null
    val login = MutableStateFlow<String?>(null)
    val awaiting = MutableStateFlow(false)
    val code = MutableStateFlow<String?>(null)
    private data class PublicationSession(val id: Long, val token: String, val expires: Long)
    private var publicationToken: PublicationSession? = null
    fun tokenFor(context: Context, id: Long): String? {
        if (userId(context) != id) return null
        return publicationToken?.takeIf { it.id == id && it.expires > System.currentTimeMillis() }?.token ?: runCatching {
            val session = JSONObject(context.encryptedPreferences.getString("githubPublicationSession", null) ?: return null)
            session.getString("token").takeIf {
                session.getLong("id") == id && session.getLong("expires") > System.currentTimeMillis()
            }
        }.getOrNull()
    }

    fun clearPublicationToken(context: Context) {
        publicationToken = null
        runCatching { context.encryptedPreferences.edit().remove("githubPublicationSession").apply() }
    }

    fun requireWorkflowAuthorization(context: Context) {
        val id = userId(context) ?: return
        store(context).edit().putBoolean("publicationWorkflowScope:$id", true).apply()
        clearPublicationToken(context)
    }

    private fun store(context: Context) =
        context.applicationContext.getSharedPreferences("github_account", Context.MODE_PRIVATE)

    fun userId(context: Context): Long? = store(context).getString(identityKey, null)
        ?.let { runCatching { JSONObject(it).getLong("id") }.getOrNull() }

    fun accountLogin(context: Context): String? = store(context).getString(identityKey, null)
        ?.let { runCatching { JSONObject(it).getString("login") }.getOrNull() }

    fun read(context: Context) {
        login.value = store(context).getString(identityKey, null)
            .let { current ->
                current ?: runCatching { context.encryptedPreferences.getString("githubBackupIdentity", null) }.getOrNull()
                    ?.takeIf { restored -> runCatching {
                        val identity = JSONObject(restored)
                        identity.getLong("id") > 0 && identity.getString("login").matches(Regex("[A-Za-z0-9-]{1,39}"))
                    }.getOrDefault(false) }
                    ?.also { store(context).edit().putString(identityKey, it).apply() }
            }
            ?.let { runCatching { JSONObject(it).getString("login") }.getOrNull() }
    }

    fun start(context: Context, expectedId: Long? = null, onAuthorized: (() -> Unit)? = null) {
        if (awaiting.value || OfflineMode.enabled(context)) return
        val app = context.applicationContext
        val needsWorkflow = (expectedId ?: userId(app))?.let { id ->
            store(app).getBoolean("publicationWorkflowScope:$id", false)
        } == true
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
                code.value = userCode
                while (true) {
                    delay(interval * 1000L)
                    check(SystemClock.elapsedRealtime() < deadline && !OfflineMode.enabled(app))
                    val result = withContext(NzikDispatchers.DATA) {
                        request("https://github.com/login/oauth/access_token", mapOf(
                            "client_id" to clientId,
                            "device_code" to deviceCode,
                            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                        ))
                    }
                    val token = result.optString("access_token")
                    if (token.isEmpty()) {
                        interval = checkNotNull(nextGitHubPoll(interval, result.optString("error")))
                        continue
                    }
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
                    if (blockedMessage != null) {
                        disconnect(app)
                        Toaster.e(blockedMessage, android.widget.Toast.LENGTH_LONG)
                        break
                    }
                    store(app).edit().putString(identityKey, JSONObject().put("id", id).put("login", handle).toString()).apply()
                    val expiresIn = result.optLong("expires_in", 0)
                    val expires = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000L else Long.MAX_VALUE
                    publicationToken = PublicationSession(id, token, expires)
                    runCatching {
                        val session = JSONObject().put("id", id).put("token", token).put("expires", expires)
                        app.encryptedPreferences.edit()
                            .putString("githubPublicationSession", session.toString())
                            .putString("githubBackupIdentity", JSONObject().put("id", id).put("login", handle).toString())
                            .apply()
                    }
                    read(app)
                    Toaster.s(R.string.github_account_connected)
                    onAuthorized?.invoke()
                    break
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toaster.e(R.string.github_account_login_failed)
            } finally {
                code.value = null
                awaiting.value = false
            }
        }
    }

    fun cancel() { attempt?.cancel() }

    fun disconnect(context: Context) {
        clearPublicationToken(context)
        runCatching { context.encryptedPreferences.edit().remove("githubBackupIdentity").apply() }
        store(context).edit().clear().apply()
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
internal fun GitHubAccountCard(horizontalPadding: Dp? = null) {
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
                    login != null -> GitHubAccount.disconnect(context)
                    else -> {
                        showLogin = true
                        if (!awaiting) GitHubAccount.start(context)
                    }
                }
            },
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
    if (showLogin && awaiting && userCode != null) {
        GitHubLoginWebView(userCode) {
            showLogin = false
            GitHubAccount.cancel()
        }
    }
    androidx.compose.runtime.LaunchedEffect(awaiting) { if (!awaiting) showLogin = false }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun GitHubLoginWebView(code: String, dismiss: () -> Unit) {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(code) {
        context.getSystemService(ClipboardManager::class.java)
            ?.let {
                it.setPrimaryClip(ClipData.newPlainText("GitHub", code))
                Toaster.s(R.string.github_account_code_copied)
            }
    }
    CustomModalBottomSheet(
        showSheet = true,
        onDismissRequest = dismiss,
        containerColor = colorPalette().background0,
        contentColor = colorPalette().text,
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = uiRoundnessShape(),
        dragHandle = { MenuSheetDragHandle() },
    ) {
        Column(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { webContext ->
                    WebView(webContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                request.url.scheme != "https" || request.url.host != "github.com"
                        }
                        loadUrl(githubDevicePage)
                    }
                },
                onRelease = { it.destroy() },
                update = {},
            )
        }
    }
}
