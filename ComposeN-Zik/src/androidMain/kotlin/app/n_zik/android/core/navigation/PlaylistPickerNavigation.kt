package app.n_zik.android.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import app.n_zik.android.components.navigation.COLLECTION_SOURCE_ONLY_ARGUMENT
import app.n_zik.android.enums.NavRoutes

/** The host opens a playlist selected by a picker attached to a track row. */
internal val LocalPlaylistPickerNavigation = compositionLocalOf<((Long, Boolean) -> Unit)?> { null }

private class DisplayedPlaylistAlias : ViewModel() {
    var playlistId: Long? = null

    companion object {
        private val factory = viewModelFactory { initializer { DisplayedPlaylistAlias() } }

        fun of(entry: NavBackStackEntry): DisplayedPlaylistAlias =
            ViewModelProvider(entry.viewModelStore, factory)[DisplayedPlaylistAlias::class.java]
    }
}

/** A collection alias can render a local playlist without changing its navigation route. */
@Composable
internal fun RegisterDisplayedPlaylistAlias(entry: NavBackStackEntry, playlistId: Long) {
    DisposableEffect(entry, playlistId) {
        val displayed = DisplayedPlaylistAlias.of(entry)
        displayed.playlistId = playlistId
        onDispose {
            if (displayed.playlistId == playlistId) displayed.playlistId = null
        }
    }
}

internal fun navigateToPickedPlaylist(navController: NavController, playlistId: Long, onDevice: Boolean = false) {
    val current = navController.currentBackStackEntry
    val arguments = current?.arguments
    val currentRoute = current?.destination?.route?.substringBefore('/')
    val currentPlaylistId = when (currentRoute) {
        NavRoutes.localPlaylist.name -> arguments?.getLong("id")
        NavRoutes.playlist.name, NavRoutes.podcast.name ->
            current?.takeUnless { arguments?.getBoolean(COLLECTION_SOURCE_ONLY_ARGUMENT) == true }
                ?.let { DisplayedPlaylistAlias.of(it).playlistId }
        else -> null
    }
    val currentOnDevice = currentRoute == NavRoutes.localPlaylist.name && arguments?.getBoolean("onDevice") == true
    if (currentPlaylistId != playlistId || currentOnDevice != onDevice) {
        val scope = if (onDevice) "?onDevice=true" else ""
        navController.navigate("${NavRoutes.localPlaylist.name}/$playlistId$scope")
    }
}
