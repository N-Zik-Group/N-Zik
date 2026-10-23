package app.n_zik.android.extensions.discord

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Durable Discord RPC errors surfaced to the user (item 9).
 *
 * The service-side [DiscordPresenceManager] is the only writer; the settings banner
 * observes it with `collectAsStateWithLifecycle()` (same process). No persistence: a
 * fresh start re-evaluates the token (valid token → no error), so nothing is stored
 * on disk.
 */
enum class DiscordRpcError {
    /** The gateway closed with 4004 (token invalid/expired) or the one-shot token validation reported the token as invalid. */
    INVALID_TOKEN,

    /** The gateway exhausted its reconnection budget (7 attempts): the RPC stays offline until the next media event, a token change, or an app restart. */
    RECONNECT_FAILED,
}

/**
 * Process-wide durable error state for Discord RPC (item 9).
 */
object DiscordRpcErrorState {

    private val _error = MutableStateFlow<DiscordRpcError?>(null)

    /** The current durable error, or null when the RPC is healthy (or the error was dismissed). */
    val error: StateFlow<DiscordRpcError?> = _error

    fun set(newError: DiscordRpcError) {
        _error.value = newError
    }

    /** Dismisses the banner (user action) or clears it on a new token / fresh connection. */
    fun clear() {
        _error.value = null
    }
}
