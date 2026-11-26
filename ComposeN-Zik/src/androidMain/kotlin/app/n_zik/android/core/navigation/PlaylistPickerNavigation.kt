package app.n_zik.android.core.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavController
import app.n_zik.android.components.menu.isCurrentMenuEntity
import app.n_zik.android.enums.NavRoutes

/** The host opens a playlist selected by a picker attached to a track row. */
internal val LocalPlaylistPickerNavigation = compositionLocalOf<((Long) -> Unit)?> { null }

internal fun navigateToPickedPlaylist(navController: NavController, playlistId: Long) {
    val destination = NavRoutes.localPlaylist
    val current = navController.currentBackStackEntry
    val currentRoute = current?.destination?.route
    val arguments = current?.arguments?.takeIf { currentRoute?.startsWith("${destination.name}/") == true }
    val samePlaylist = isCurrentMenuEntity(
        destination, playlistId.toString(), currentRoute,
        arguments?.getLong("id")?.toString(),
    )
    if (!samePlaylist || arguments?.getBoolean("onDevice") == true) {
        navController.navigate("${destination.name}/$playlistId")
    }
}
