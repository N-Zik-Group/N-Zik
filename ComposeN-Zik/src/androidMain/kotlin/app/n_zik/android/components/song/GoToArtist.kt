package app.n_zik.android.components.song

import app.n_zik.android.core.database.*

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import app.n_zik.android.R
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.requests.nextPage
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import app.n_zik.android.core.database.Database
import app.n_zik.android.appContext
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

class GoToArtist(
    private val navController: NavController,
    private val song: Song,
    private val menuState: MenuState
): MenuIcon, Descriptive {

    override val iconId: Int = R.drawable.people
    // TODO: Add string "About this artist"
    override val messageId: Int = R.string.artists
    override val menuIconTitle: String
        @Composable
        get() = stringResource(R.string.more_of) + " ${song.cleanArtistsText()}"



    override fun onShortClick() {
        menuState.hide()
        
        CoroutineScope( Dispatchers.IO ).launch {
            val id = Database.artistTable
                    .findBySongId( song.id )
                    .first()
                    .firstOrNull()
                    ?.id
                    
            val isValid = id != null && id.removePrefix(MODIFIED_PREFIX).let { it.length > 11 && it.matches("^[A-Za-z0-9_-]+\$".toRegex()) }
            
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (isValid && id != null) {
                    NavRoutes.artist.navigateHere( navController, id )
                } else {
                    Toaster.i( R.string.looking_up_artist_online, song.cleanArtistsText() )
                    
                    CoroutineScope( Dispatchers.IO ).launch {
                        try {
                            val hasValidId = song.id.length == 11 && !song.id.startsWith("local:")
                            
                            var artistEndpoint: NavigationEndpoint.Endpoint.Browse? = null
                            
                            if (hasValidId) {
                                artistEndpoint = Innertube.nextPage(videoId = song.id)
                                    ?.onFailure {
                                        Timber.tag("go_to_artist").e( it, "nextPage failed" )
                                    }
                                    ?.getOrNull()
                                    ?.itemsPage
                                    ?.items
                                    ?.firstOrNull { it.key == song.id }
                                    ?.authors
                                    ?.firstOrNull()
                                    ?.endpoint
                                    ?.takeIf { !it.browseId.isNullOrBlank() }
                                    
                                Timber.tag("go_to_artist").d("Up Next API artist endpoint: %s", artistEndpoint?.browseId)
                            }

                            if (artistEndpoint != null) {
                                val path = "${artistEndpoint.browseId}?params=${artistEndpoint.params.orEmpty()}"
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    Toaster.s( R.string.artist_found_online_verify )
                                    NavRoutes.artist.navigateHere( navController, path )
                                }
                            } else {
                                val query = song.cleanArtistsText().takeIf { it.isNotBlank() } ?: song.cleanTitle()
                                val songQuery = "${song.cleanTitle()} ${song.cleanArtistsText()}".trim()
                                Timber.tag("go_to_artist").d("Fallback search query: %s", songQuery)
                                
                                // 1. First, search for the song (more accurate for finding exact artists of a track)
                                val songSearchResult = Innertube.searchPage<Innertube.SongItem>(
                                    query = songQuery, params = Innertube.SearchFilter.Song.value,
                                    fromMusicShelfRendererContent = { content -> Innertube.SongItem.from(content) }
                                )?.getOrNull()
                                
                                val foundSong = songSearchResult?.items?.firstOrNull { it.key == song.id }
                                    ?: songSearchResult?.items?.firstOrNull { it.title.equals(song.cleanTitle(), ignoreCase = true) }
                                    ?: songSearchResult?.items?.firstOrNull()
                                    
                                var fallbackEndpoint = foundSong?.authors?.firstOrNull()?.endpoint
                                
                                // 2. If no author endpoint found via song search, fallback to direct artist search
                                if (fallbackEndpoint == null || fallbackEndpoint.browseId.isNullOrBlank()) {
                                    Timber.tag("go_to_artist").d("No artist found from song search, falling back to direct artist search")
                                    val artistSearchResult = Innertube.searchPage<Innertube.ArtistItem>(
                                        query = query, params = Innertube.SearchFilter.Artist.value,
                                        fromMusicShelfRendererContent = { content -> Innertube.ArtistItem.from(content) }
                                    )?.getOrNull()
                                    fallbackEndpoint = artistSearchResult?.items?.firstOrNull()?.info?.endpoint
                                }
                                
                                if (fallbackEndpoint != null && !fallbackEndpoint.browseId.isNullOrBlank()) {
                                    Timber.tag("go_to_artist").d("Found artist ID in search: %s", fallbackEndpoint.browseId)
                                    val path = "${fallbackEndpoint.browseId}?params=${fallbackEndpoint.params.orEmpty()}"
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        Toaster.s( R.string.artist_found_online_verify )
                                        NavRoutes.artist.navigateHere( navController, path )
                                    }
                                } else {
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        Toaster.e( R.string.failed_to_fetch_artist )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag("go_to_artist").e( e, "Failed to fetch artist" )
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                Toaster.e( R.string.failed_to_fetch_artist )
                            }
                        }
                    }
                }
            }
        }
    }
}
