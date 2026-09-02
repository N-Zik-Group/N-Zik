package app.n_zik.android.components.song

import app.n_zik.android.core.database.*

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import app.n_zik.android.R
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.bodies.NextBody
import it.fast4x.innertube.requests.nextPage
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.components.MenuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import java.util.Optional
import app.it.fast4x.rimusic.MODIFIED_PREFIX

class GoToAlbum(
    private val navController: NavController,
    private val song: Song,
    private val menuState: MenuState
): MenuIcon, Descriptive {

    override val iconId: Int = R.drawable.album
    override val messageId: Int = R.string.go_to_album
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )




    override fun onShortClick() {
        menuState.hide()
        
        CoroutineScope( Dispatchers.IO ).launch {
            val id = Database.albumTable
                    .findBySongId( song.id )
                    .first()
                    ?.id
                    
            val isValid = id != null && id.removePrefix(MODIFIED_PREFIX).let { it.length > 11 && it.matches("^[A-Za-z0-9_-]+\$".toRegex()) }
            
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (isValid && id != null) {
                    NavRoutes.album.navigateHere( navController, id )
                } else {
                    Toaster.i( R.string.looking_up_album_from_the_internet )
                    
                    CoroutineScope( Dispatchers.IO ).launch {
                        try {
                            val hasValidId = song.id.length == 11 && !song.id.startsWith("local:")
                            
                            var albumEndpoint: NavigationEndpoint.Endpoint.Browse? = null
                            
                            if (hasValidId) {
                                albumEndpoint = Innertube.nextPage(NextBody(videoId = song.id))
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
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    Toaster.s( R.string.album_found_online_verify )
                                    NavRoutes.album.navigateHere( navController, path )
                                }
                            } else {
                                val query = "${song.cleanTitle()} ${song.cleanArtistsText()}".trim()
                                Timber.tag("go_to_album").d("Search query: %s", query)
                                val searchResult = Innertube.searchPage<Innertube.SongItem>(
                                    SearchBody(query = query, params = Innertube.SearchFilter.Song.value),
                                    { content -> Innertube.SongItem.from(content) }
                                )?.getOrNull()
                                
                                Timber.tag("go_to_album").d("Search result items count: %s", searchResult?.items?.size)
                                
                                val foundSong = searchResult?.items?.firstOrNull { it.key == song.id }
                                    ?: searchResult?.items?.firstOrNull { it.title.equals(song.cleanTitle(), ignoreCase = true) }
                                    ?: searchResult?.items?.firstOrNull()
                                
                                val fallbackEndpoint = foundSong?.album?.endpoint
                                
                                if (fallbackEndpoint != null && !fallbackEndpoint.browseId.isNullOrBlank()) {
                                    Timber.tag("go_to_album").d("Found album ID: %s", fallbackEndpoint.browseId)
                                    val path = "${fallbackEndpoint.browseId}?params=${fallbackEndpoint.params.orEmpty()}"
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        Toaster.s( R.string.album_found_online_verify )
                                        NavRoutes.album.navigateHere( navController, path )
                                    }
                                } else {
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        Toaster.e( R.string.failed_to_fetch_album )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag("go_to_album").e( e, "Failed to fetch album" )
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                Toaster.e( R.string.failed_to_fetch_album )
                            }
                        }
                    }
                }
            }
        }
    }
}
