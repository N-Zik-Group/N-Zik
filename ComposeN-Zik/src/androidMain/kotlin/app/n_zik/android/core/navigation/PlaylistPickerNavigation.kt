package app.n_zik.android.core.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavController
import app.n_zik.android.components.menu.isCurrentMenuEntity
import app.n_zik.android.enums.NavRoutes

/** The host opens a playlist selected by a picker attached to a track row. */
internal val LocalPlaylistPickerNavigation = compositionLocalOf<((Long, Boolean) -> Unit)?> { null }

internal fun navigateToPickedPlaylist(navController: NavController, playlistId: Long, onDevice: Boolean = false) {
    val destination = NavRoutes.localPlaylist
    val current = navController.currentBackStackEntry
    val currentRoute = current?.destination?.route
    val arguments = current?.arguments?.takeIf { currentRoute?.startsWith("${destination.name}/") == true }
    val samePlaylist = isCurrentMenuEntity(
        destination, playlistId.toString(), currentRoute,
        arguments?.getLong("id")?.toString(),
    )
    val currentOnDevice = arguments?.getBoolean("onDevice") == true
    if (!samePlaylist || currentOnDevice != onDevice) {
        val scope = if (onDevice) "?onDevice=true" else ""
        navController.navigate("${destination.name}/$playlistId$scope")
    }
}
