package app.n_zik.android.components.menu.playlist

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.components.menu.isCurrentMenu
import app.n_zik.android.components.menu.navigateToMenuEntity
import app.n_zik.android.components.navigation.LibraryBrowseKind
import app.n_zik.android.components.navigation.LibraryBrowseDestination
import app.n_zik.android.components.navigation.collectionMenuOnlineRoute
import app.n_zik.android.components.navigation.onlinePlaylistBrowseIdFromLink
import app.n_zik.android.components.navigation.onlinePlaylistDestination
import app.n_zik.android.components.navigation.resolveLibraryBrowseDestination
import app.n_zik.android.core.database.isPodcastBrowseId
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.preferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens the linked online source without redirecting to a saved local copy. */
internal class OpenCollectionMenuAction(
    private val navController: NavController,
    private val menuState: MenuState,
    private val browseId: String,
    @param:DrawableRes override val iconId: Int,
    private val onNavigate: () -> Unit = {},
    private val useExactSource: Boolean = false,
) : MenuIcon, Descriptive {
    private val podcast = onlinePlaylistDestination(browseId) == NavRoutes.podcast
    override val messageId: Int
        get() = if (podcast) R.string.podcast_action_open else R.string.playlist_action_open

    @get:Composable
    override val menuIconTitle: String get() = stringResource(messageId)

    override fun onShortClick() {
        val context = appContext()
        val owner = ProfileWorkOwner.capture(context) ?: return
        if (!ProfileWorkOwner.isBound(context, owner)) return
        val menuKey = menuState.transitionKey
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            val kind = if (podcast) LibraryBrowseKind.Podcast else LibraryBrowseKind.Playlist
            val resolved = if (useExactSource) LibraryBrowseDestination(browseId)
                else resolveLibraryBrowseDestination(browseId, kind, context.preferences.all)
            val sourceId = onlinePlaylistBrowseIdFromLink(resolved.browseId.removePrefix(MODIFIED_PREFIX))
            withContext(NzikDispatchers.UI) {
                if (menuState.isCurrentMenu(menuKey) && ProfileWorkOwner.isBound(context, owner)) {
                    navigateToMenuEntity(
                        navController, menuState, onlinePlaylistDestination(sourceId), sourceId,
                        route = collectionMenuOnlineRoute(sourceId),
                        onNavigate = onNavigate,
                        reopenCurrent = resolved.localPlaylistId != null,
                    )
                }
            }
        }
    }
}
