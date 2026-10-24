package app.n_zik.android.components.musicbrainz.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.models.Album
import app.n_zik.android.Dependencies
import app.n_zik.android.core.database.Database
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.requests.ArtistSection
import timber.log.Timber
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Loads the album Insights data: tracks, artist, other albums by the same artist, listening stats and external links. */
class AlbumInsightsViewModel(application: Application) : AndroidViewModel(application) {

    companion object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AlbumInsightsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AlbumInsightsViewModel(Dependencies.application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private val albumTable = Database.albumTable
    private val artistTable = Database.artistTable
    private val eventTable = Database.eventTable
    private val songAlbumMapTable = Database.songAlbumMapTable

    private val _state = MutableStateFlow(AlbumDetailUiState())
    val state: StateFlow<AlbumDetailUiState> = _state.asStateFlow()

    fun loadAlbum(albumId: String) {
        viewModelScope.launch {
            _state.value = AlbumDetailUiState(isLoading = true)

            val album = withContext(NzikDispatchers.DATA) {
                albumTable.findByIdDirect(albumId)
            }
            if (album == null) {
                // Missing DB row (edge case): end the load with the entity null
                // instead of leaving the screen stuck on the Loader forever.
                _state.value = AlbumDetailUiState(isLoading = false)
                return@launch
            }

            val tracks = withContext(NzikDispatchers.DATA) {
                songAlbumMapTable.allSongsOfDirect(albumId)
            }

            val topTracks = withContext(NzikDispatchers.DATA) {
                // Same tracks, reordered by the album's total play time per song
                // instead of disc position; drop songs that were never listened to.
                songAlbumMapTable.getTopSongsOfDirect(albumId)
                    .filter { it.totalPlayTimeMs >= 1 }
            }

            val artist = withContext(NzikDispatchers.DATA) {
                val authors = album.authorsText?.trim().orEmpty()
                val byName = if (authors.isNotBlank()) artistTable.findByNameDirect(authors) else null
                // Name matching is fragile (casing, "feat.", collab text); fall back to the
                // actual SongArtistMap relation of the album's own tracks, same source used
//
                // by "Go to artist" elsewhere in the app.
                byName ?: tracks.firstOrNull()?.let { song ->
                    artistTable.findBySongIdDirect(song.id).firstOrNull()
                }
            }

            // MusicBrainz-matched local albums carry an albumType ("Album", "Single",
            // "EP", "Live", "Compilation", "Remix"); split on it so a local single/EP
//
            // lands in the same section as its online counterpart. Albums never matched
            // against MusicBrainz have a null albumType and default to "Other albums".
            val (localOtherAlbums, localSinglesAndEps) = withContext(NzikDispatchers.DATA) {
                val artistName = album.authorsText?.trim().orEmpty()
                if (artistName.isBlank()) {
                    emptyList<Album>() to emptyList()
                } else {
                    val singleOrEpTypes = setOf("single", "ep")
                    albumTable.getOtherAlbumsByArtistName(artistName, albumId, limit = Int.MAX_VALUE)
                        .partition { it.albumType?.lowercase() !in singleOrEpTypes }
                }
            }
            val localAlbumsAll = localOtherAlbums + localSinglesAndEps

            // Local library only tracks albums the user has already interacted with, so
            // fetch the artist's YouTube Music page to fill in what's missing: full
//
            // albums go into "Other albums", singles/EPs into their own section
            // (YTM bundles EPs with singles under "Singles & EPs", no way to split them).
            val (missingAlbums, missingSinglesAndEps) = withContext(NzikDispatchers.DATA) {
                val artistId = artist?.id?.takeIf { it.isNotBlank() && !it.startsWith("local:") }
                if (artistId == null) {
                    Timber.tag("AlbumInsights").d(
                        "missingAlbums skipped: no usable YouTube artist id (artist.id=${artist?.id})"
                    )
                    emptyList<Album>() to emptyList()
                } else {
                    val knownIds = localAlbumsAll.mapTo(mutableSetOf()) { it.id }
                    val knownTitles = localAlbumsAll.mapNotNullTo(mutableSetOf()) {
                        it.title?.let(::cleanPrefix)?.trim()?.lowercase()
                    }

                    fun mapSection(section: ArtistSection?): List<Album> = section
                        ?.items
                        ?.filterIsInstance<Innertube.AlbumItem>()
                        ?.mapNotNull { item ->
                            val id = item.info?.endpoint?.browseId
                                ?.takeIf { it != albumId && it !in knownIds }
                                ?: return@mapNotNull null
                            val title = item.title
                            if (title != null && cleanPrefix(title).trim().lowercase() in knownTitles) {
                                return@mapNotNull null
                            }
                            Album(
                                id = id,
                                title = title,
                                thumbnailUrl = item.thumbnail?.url,
                                year = item.year,
                                authorsText = album.authorsText
                            )
                        }
                        ?.distinctBy { it.id }
                        .orEmpty()

                    val artistPageResult = YtMusic.getArtistPage(artistId)
                    val artistPage = artistPageResult.getOrNull()
                    val albumsSection = artistPage?.sections?.firstOrNull { it.title.equals("Albums", ignoreCase = true) }
                    val singlesSection = artistPage?.sections?.firstOrNull { it.title.contains("Singles", ignoreCase = true) }

                    val albumsResult = mapSection(albumsSection)
                    val singlesResult = mapSection(singlesSection)

                    Timber.tag("AlbumInsights").d(
                        "missingAlbums: artistId=$artistId fetchSuccess=${artistPageResult.isSuccess} " +
                            "sections=${artistPage?.sections?.map { it.title }} " +
                            "localOther=${localOtherAlbums.size} localSingles=${localSinglesAndEps.size} " +
                            "missing=${albumsResult.size} missingSingles=${singlesResult.size}"
                    )
                    if (artistPageResult.isFailure) {
                        Timber.tag("AlbumInsights").e(artistPageResult.exceptionOrNull(), "getArtistPage failed")
                    }

                    albumsResult to singlesResult
                }
            }

            val otherAlbums = localOtherAlbums + missingAlbums
            val singlesAndEps = localSinglesAndEps + missingSinglesAndEps

            val stats = withContext(NzikDispatchers.DATA) {
                val likedSongs = tracks.count { it.likedAt != null }
                val totalPlayTimeMs = eventTable.getAlbumTotalPlayTime(albumId).first()
                val playCount = eventTable.getAlbumPlayCount(albumId).first()
                AlbumStats(totalPlayTimeMs, playCount, likedSongs, tracks.size)
            }

            _state.value = AlbumDetailUiState(
                isLoading = false,
                album = album,
                tracks = tracks,
                topTracks = topTracks,
                artist = artist,
                otherAlbums = otherAlbums,
                singlesAndEps = singlesAndEps,
                externalLinks = album.links.orEmpty(),
                stats = stats
            )

            // "Other albums"/"Singles & EPs" cards only get their rating badge once the
            // user actually opens that specific album (AlbumScreen's own on-view sync) —
//
            // opening this Insights page must not itself fan out into fetching MusicBrainz
//
            // metadata for a dozen unrelated albums (missing=4 missingSingles=10 is not
            // unusual), which is exactly the kind of bulk-fetch-on-a-single-view pattern
//
            // MusicBrainz's rate limiting is meant to discourage.
        }
    }
}
