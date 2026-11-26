package app.n_zik.android.components.menu.playlist

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.components.menu.OpenEntityMenuAction
import app.n_zik.android.components.menu.artistMoreFromTitle
import app.n_zik.android.components.menu.artistSearchMenuAction
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.models.Playlist
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint

internal typealias PlaylistOwner = Innertube.Info<NavigationEndpoint.Endpoint.Browse>

internal fun Playlist.withSourcePlaylistOwner(owner: PlaylistOwner?): Playlist {
    owner ?: return this
    val id = owner.endpoint?.browseId?.takeIf(String::isNotBlank) ?: authorBrowseId
    val name = owner.name?.takeIf(String::isNotBlank) ?: return this
    return copy(authorBrowseId = id, authorName = name)
}

internal fun Playlist.sourcePlaylistOwner(): PlaylistOwner? {
    val id = authorBrowseId?.takeIf(String::isNotBlank)
    val name = (if (id == null) authorName ?: displayAuthorName() else displayAuthorName())
        ?.takeIf(String::isNotBlank) ?: return null
    return Innertube.Info(name, id?.let { NavigationEndpoint.Endpoint.Browse(browseId = it) })
}

internal fun playlistOwnerAction(
    owner: PlaylistOwner?,
    navController: NavController,
    menuState: MenuState,
): MenuIcon? {
    owner ?: return null
    val name = owner.name?.takeIf(String::isNotBlank) ?: return null
    val id = owner.endpoint?.browseId?.takeIf(String::isNotBlank)
        ?: return artistSearchMenuAction(name, navController, menuState)
    val action = OpenEntityMenuAction(
        navController,
        menuState,
        NavRoutes.artist,
        id,
        R.drawable.people,
        R.string.library_artists_title,
    )
    return object : MenuIcon, Descriptive {
        override val iconId: Int = action.iconId
        override fun onShortClick() = action.onShortClick()
        override val messageId: Int = action.messageId

        @get:Composable
        override val menuIconTitle: String get() = artistMoreFromTitle(name)
    }
}
