package app.it.fast4x.rimusic.utils

import app.n_zik.android.core.database.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.compose.persist.persist
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.albumPage
import it.fast4x.innertube.requests.artistPage
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.SongAlbumMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.kreate.android.me.knighthat.utils.PropUtils

@Composable
fun UpdateYoutubeArtist(browseId: String) {

    var artistPage by persist<Innertube.ArtistInfoPage?>("artist/$browseId/artistPage")
    var artist by persist<Artist?>("artist/$browseId/artist")
    val tabIndex by rememberPreference(artistScreenTabIndexKey, defaultValue = 0)

    LaunchedEffect(browseId) {
        if (browseId.startsWith("LOCAL_ARTIST_")) return@LaunchedEffect
        
        Database.artistTable
                .findById( browseId )
                .combine(snapshotFlow { tabIndex }.map { it != 4 }) { artist, mustFetch -> artist to mustFetch }
                .distinctUntilChanged()
                .collect { (currentArtist, mustFetch) ->
                    artist = currentArtist

                    if (artistPage == null && (currentArtist?.timestamp == null || mustFetch)) {
                        withContext(Dispatchers.IO) {
                            Innertube.artistPage(browseId = browseId.removePrefix(MODIFIED_PREFIX))
                                ?.onSuccess { currentArtistPage ->
                                    artistPage = currentArtistPage

                                    Database.artistTable.upsert(
                                        Artist(
                                            id = browseId,
                                            name = PropUtils.retainIfModified(currentArtist?.name, currentArtistPage.name) ?: currentArtistPage.name,
                                            thumbnailUrl = PropUtils.retainIfModified(currentArtist?.thumbnailUrl, currentArtistPage.thumbnail?.url),
                                            timestamp = System.currentTimeMillis(),
                                            bookmarkedAt = currentArtist?.bookmarkedAt,
                                            lastFetch = System.currentTimeMillis()
                                        )
                                    )
                                }
                        }
                    }
                }
    }

}

@UnstableApi
@Composable
fun UpdateYoutubeAlbum (browseId: String) {
    var album by persist<Album?>("album/$browseId/album")
    var albumPage by persist<Innertube.PlaylistOrAlbumPage?>("album/$browseId/albumPage")
    val tabIndex by rememberSaveable {mutableStateOf(0)}
    LaunchedEffect(browseId) {
        if (browseId.startsWith("LOCAL_ALBUM_")) return@LaunchedEffect
        
        Database.albumTable
                .findById( browseId )
                .combine(snapshotFlow { tabIndex }) { album, tabIndex -> album to tabIndex }
                .collect { (currentAlbum, tabIndex) ->
                    album = currentAlbum

                    if (albumPage == null && (currentAlbum?.timestamp == null || tabIndex == 1)) {
                        withContext(Dispatchers.IO) {
                            Innertube.albumPage(browseId = browseId.removePrefix(MODIFIED_PREFIX))
                                ?.onSuccess { currentAlbumPage ->
                                    albumPage = currentAlbumPage

                                    Database.songAlbumMapTable.clear( browseId )

                                    Database.albumTable.upsert(
                                        Album(
                                            id = browseId,
                                            title = PropUtils.retainIfModified(album?.title, currentAlbumPage.title),
                                            thumbnailUrl = PropUtils.retainIfModified(album?.thumbnailUrl, currentAlbumPage.thumbnail?.url),
                                            year = currentAlbumPage.year,
                                            authorsText = PropUtils.retainIfModified(album?.authorsText, currentAlbumPage.authors.parseArtists().joinToString(", ").takeIf { it.isNotBlank() }),
                                            shareUrl = currentAlbumPage.url,
                                            timestamp = System.currentTimeMillis(),
                                            bookmarkedAt = album?.bookmarkedAt
                                        )
                                    )
                                    currentAlbumPage.songsPage
                                                    ?.items
                                                    ?.onEach { Database.upsert(it) }
                                                    ?.map(Innertube.SongItem::asMediaItem)
                                                    ?.mapIndexed { position, mediaItem ->
                                                        SongAlbumMap(
                                                            songId = mediaItem.mediaId,
                                                            albumId = browseId,
                                                            position = position
                                                        )
                                                    }
                                                   ?.also( Database.songAlbumMapTable::upsert )
                                }
                        }

                    }
                }
    }
}


