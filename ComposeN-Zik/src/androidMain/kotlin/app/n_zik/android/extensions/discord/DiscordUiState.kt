package app.n_zik.android.extensions.discord

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Global UI state for Discord RPC to track the current Compose navigation route.
 */
object DiscordUiState {
    val currentRoute = MutableStateFlow<String?>(null)
    val currentHomeTab = MutableStateFlow<String?>(null)
}
