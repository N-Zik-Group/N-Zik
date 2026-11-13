package app.n_zik.android.components.song

import app.n_zik.android.core.database.*

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.core.ondevice.isDeviceAlbumId
import app.n_zik.android.playback.services.isLocal
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.requests.nextPage
import app.n_zik.innertube.requests.searchPage
import app.n_zik.innertube.utils.from
import app.n_zik.android.core.database.Database
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.n_zik.android.core.utils.Toaster
import timber.log.Timber
import java.util.Optional
import app.n_zik.android.MODIFIED_PREFIX

class GoToAlbum(
    private val navController: NavController,
    private val song: Song,
    private val menuState: MenuState
): MenuIcon, Descriptive {

    override val iconId: Int = R.drawable.album
    override val messageId: Int = R.string.album_action_open
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )




    override fun onShortClick() {
        menuState.hide()
        
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            if (song.isLocal) {
                val localId = Database.songAlbumMapTable.findAlbumsOfDirect(song.id).firstOrNull { isDeviceAlbumId(it.id) }?.id
                kotlinx.coroutines.withContext(NzikDispatchers.UI) {
                    if (localId != null) NavRoutes.album.navigateHere(navController, localId)
                    else Toaster.e(R.string.album_lookup_failed)
                }
                return@launch
            }

            val id = Database.albumTable
                    .findBySongId( song.id )
                    .first()
                    ?.id
                    
            val isValid = id != null && id.removePrefix(MODIFIED_PREFIX).let { it.length > 11 && it.matches("^[A-Za-z0-9_-]+\$".toRegex()) }
            
            kotlinx.coroutines.withContext(NzikDispatchers.UI) {
                if (isValid && id != null) {
                    NavRoutes.album.navigateHere( navController, id )
                } else {
                    Toaster.i( R.string.album_lookup_running )
                    
                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                        try {
                            val hasValidId = song.id.length == 11 && !song.id.startsWith("local:")
                            
                            var albumEndpoint: NavigationEndpoint.Endpoint.Browse? = null
                            
                            if (hasValidId) {
                                albumEndpoint = Innertube.nextPage(videoId = song.id)
                                    ?.onFailure {
                                        Timber.tag("go_to_album").e( it, "nextPage failed" )
                                    }
                                    ?.getOrNull()
                                    ?.itemsPage
                                    ?.items
                                    ?.firstOrNull { it.key == song.id }
                                    ?.album
                                    ?.endpoint
                                    ?.takeIf { !it.browseId.isNullOrBlank() }
                                    
                                Timber.tag("go_to_album").d("Up Next API album endpoint: %s", albumEndpoint?.browseId)
                            }

                            if (albumEndpoint != null) {
                                val path = "${albumEndpoint.browseId}?params=${albumEndpoint.params.orEmpty()}"
                                kotlinx.coroutines.withContext(NzikDispatchers.UI) {
                                    Toaster.s( R.string.album_found_online_verify )
                                    NavRoutes.album.navigateHere( navController, path )
                                }
                            } else {
                                val query = "${song.cleanTitle()} ${song.cleanArtistsText()}".trim()
                                Timber.tag("go_to_album").d("Search query: %s", query)
                                val searchResult = Innertube.searchPage<Innertube.SongItem>(
                                    query = query, params = Innertube.SearchFilter.Song.value,
                                    fromMusicShelfRendererContent = { content -> Innertube.SongItem.from(content) }
                                )?.getOrNull()
                                
                                Timber.tag("go_to_album").d("Search result items count: %s", searchResult?.items?.size)
                                
                                val foundSong = searchResult?.items?.firstOrNull { it.key == song.id }
                                    ?: searchResult?.items?.firstOrNull { it.title.equals(song.cleanTitle(), ignoreCase = true) }
                                    ?: searchResult?.items?.firstOrNull()
                                
                                val fallbackEndpoint = foundSong?.album?.endpoint
                                
                                if (fallbackEndpoint != null && !fallbackEndpoint.browseId.isNullOrBlank()) {
                                    Timber.tag("go_to_album").d("Found album ID: %s", fallbackEndpoint.browseId)
                                    val path = "${fallbackEndpoint.browseId}?params=${fallbackEndpoint.params.orEmpty()}"
                                    kotlinx.coroutines.withContext(NzikDispatchers.UI) {
                                        Toaster.s( R.string.album_found_online_verify )
                                        NavRoutes.album.navigateHere( navController, path )
                                    }
                                } else {
                                    kotlinx.coroutines.withContext(NzikDispatchers.UI) {
                                        Toaster.e( R.string.album_lookup_failed )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag("go_to_album").e( e, "Failed to fetch album" )
                            kotlinx.coroutines.withContext(NzikDispatchers.UI) {
                                Toaster.e( R.string.album_lookup_failed )
                            }
                        }
                    }
                }
            }
        }
    }
}
