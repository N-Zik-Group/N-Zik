package app.n_zik.android.components.settings.accounts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Retains only an authorization ticket, never a callback or an Activity from its requesting screen. */
internal class GitHubPublicationAuthorization {
    data class Request(val key: String, val profile: String, val authorized: Boolean = false)

    private val mutableState = MutableStateFlow<Request?>(null)
    val state = mutableState.asStateFlow()

    fun begin(profile: String): String {
        val request = Request(UUID.randomUUID().toString(), profile)
        mutableState.value = request
        return request.key
    }

    fun authorize(key: String, profile: String) {
        mutableState.value?.takeIf { it.key == key && it.profile == profile }
            ?.let { mutableState.compareAndSet(it, it.copy(authorized = true)) }
    }

    fun consume(key: String, profile: String): Boolean {
        val request = mutableState.value ?: return false
        return request.key == key && request.profile == profile && request.authorized &&
            mutableState.compareAndSet(request, null)
    }

    fun finish(key: String) {
        mutableState.value?.takeIf { it.key == key && !it.authorized }
            ?.let { mutableState.compareAndSet(it, null) }
    }

    fun cancel() { mutableState.value = null }
}

@Composable
internal fun rememberGitHubPublicationAuthorization(onAuthorized: () -> Unit): (Long?) -> Unit {
    val context = LocalContext.current
    val profile = app.n_zik.android.utils.getActiveProfile(context)
    var requestKey by rememberSaveable(profile) { mutableStateOf<String?>(null) }
    val currentCallback by rememberUpdatedState(onAuthorized)
    val request by GitHubAccount.publicationAuthorization.state.collectAsStateWithLifecycle()
    val awaiting by GitHubAccount.awaiting.collectAsStateWithLifecycle()
    val code by GitHubAccount.code.collectAsStateWithLifecycle()
    LaunchedEffect(requestKey, request, profile) {
        val key = requestKey ?: return@LaunchedEffect
        if (GitHubAccount.publicationAuthorization.consume(key, profile)) {
            requestKey = null
            currentCallback()
        } else if (GitHubAccount.publicationAuthorization.state.value?.key != key) {
            requestKey = null
        }
    }
    if (requestKey != null && awaiting) {
        code?.let { GitHubLoginWebView(it) { GitHubAccount.cancel() } }
    }
    return { id ->
        if (requestKey == null) requestKey = GitHubAccount.startPublicationAuthorization(context, id)
    }
}
