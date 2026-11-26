package app.n_zik.android.components.song

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.cleanPrefix
import app.n_zik.android.components.menu.isCurrentMenu
import app.n_zik.android.components.menu.navigateToMenuEntity
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class GoToAlbum(
    private val navController: NavController,
    private val song: Song,
    private val menuState: MenuState,
    private val albumId: String? = null,
    private val albumTitle: String? = null,
    private val onNavigate: () -> Unit = {},
) : MenuIcon, Descriptive {
    private val searchQuery = trackAlbumSearchQuery(albumTitle, song.title, song.artistsText)

    override val isEnabled: Boolean
        get() = !albumId.isNullOrBlank() || !searchQuery.isNullOrBlank()

    override val iconId: Int = R.drawable.album
    override val messageId: Int = R.string.album_action_open
    override val menuIconTitle: String
        @Composable
        get() = stringResource(messageId)

    override fun onShortClick() {
        val menuKey = menuState.transitionKey
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            val album = try {
                resolveTrackAlbumLink(song, albumId, albumTitle) {
                    withContext(NzikDispatchers.UI) {
                        if (menuState.isCurrentMenu(menuKey)) Toaster.i(R.string.metadata_link_searching)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.tag("GoToAlbum").w(error, "Album lookup failed for ${song.id}")
                null
            }
            withContext(NzikDispatchers.UI) {
                if (!menuState.isCurrentMenu(menuKey)) return@withContext
                if (album != null) {
                    navigateToMenuEntity(
                        navController, menuState, NavRoutes.album, album.album.id,
                        album.menuRoute(),
                    )
                    onNavigate()
                } else if (openTrackMetadataSearch(navController, searchQuery, TrackMetadataSearch.Album)) {
                    menuState.hide()
                    onNavigate()
                }
            }
        }
    }
}

internal enum class TrackMetadataSearch(val tabIndex: Int) {
    Album(1),
    Artist(2),
}

internal fun trackAlbumSearchQuery(album: String?, song: String?, artist: String?): String? {
    metadataSearchTerm(album)?.let { return it }
    val title = metadataSearchTerm(song)
    val credit = metadataSearchTerm(artist)?.takeUnless { title?.contains(it, ignoreCase = true) == true }
    return listOfNotNull(title, credit).joinToString(" ").takeIf(String::isNotBlank)
}

private fun metadataSearchTerm(value: String?): String? =
    cleanPrefix(value.orEmpty()).trim().takeUnless {
        it.isBlank() || it.equals("null", ignoreCase = true)
    }

internal fun openTrackMetadataSearch(
    navController: NavController,
    text: String?,
    kind: TrackMetadataSearch,
): Boolean {
    val query = metadataSearchTerm(text) ?: return false
    val context = appContext()
    val local = OfflineMode.enabled(context) || !NetworkQualityHelper.isNetworkConnected(context)
    val route = trackMetadataSearchRoute(query, kind, local)
    if (route == null) {
        Toaster.w(R.string.common_load_failed_no_connection)
        return false
    }
    Toaster.i(R.string.metadata_link_manual_required)
    navController.navigate(route)
    return true
}

internal fun trackMetadataSearchRoute(query: String, kind: TrackMetadataSearch, local: Boolean): String? =
    when {
        !local -> "${NavRoutes.searchResults.name}/${Uri.encode(query)}?tab=${kind.tabIndex}"
        kind == TrackMetadataSearch.Artist -> "${NavRoutes.search.name}?text=${Uri.encode(query)}&local=true"
        else -> null
    }
