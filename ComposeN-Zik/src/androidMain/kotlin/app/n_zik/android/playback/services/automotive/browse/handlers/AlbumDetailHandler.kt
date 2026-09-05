package app.n_zik.android.playback.services.automotive.browse.handlers

import it.fast4x.innertube.requests.albumPage
import app.it.fast4x.rimusic.utils.asSong

import android.content.Context
import androidx.media3.common.MediaItem
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.parseArtists
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.AutoSearchState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.SongAlbumMap
import app.kreate.android.me.knighthat.utils.PropUtils
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class AlbumDetailHandler : BrowseHandler {
    override fun handles(parentId: String): Boolean {
        if (!parentId.startsWith("${PlayerServiceModern.ALBUM}/")) return false
        val parts = parentId.split("/")
        return parts.size == 2
    }

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?
    ): List<MediaItem> {
        val parts = parentId.split("/")
        val actualParentId = parts[0]
        val albumId = parts[1]
        var onlineSongs: List<Song>? = null
        
        if (albumId.startsWith("LOCAL_ALBUM_")) {
            val localSongs = database.songAlbumMapTable.allSongsOf(albumId).first()
            return localSongs.mapIndexed { index, song ->
                SessionMediaItemMapper.mapSongToMediaItem(song, actualParentId).let { item ->
                    item.buildUpon()
                        .setMediaMetadata(
                            item.mediaMetadata.buildUpon()
                                .setTrackNumber(index + 1)
                                .build()
                        )
                        .build()
                }
            }
        }
        
        val cleanAlbumId = albumId.removePrefix(MODIFIED_PREFIX)
        
        val online = YtMusic.getAlbum(cleanAlbumId, true).getOrNull()
        if (online != null) {
            val onlineAlbum = online.album
            val authorsText: String? = onlineAlbum.authors.parseArtists().joinToString(", ")
            onlineSongs = online.songs.map { it.asSong }
            
            CoroutineScope(Dispatchers.IO).launch {
                val album = database.albumTable.findById(albumId).first()
                database.asyncTransaction {
                    albumTable.upsert(
                        Album(
                            id = albumId,
                            title = PropUtils.retainIfModified(album?.title, onlineAlbum.title),
                            thumbnailUrl = PropUtils.retainIfModified(album?.thumbnailUrl, onlineAlbum.thumbnail?.url),
                            year = onlineAlbum.year,
                            authorsText = PropUtils.retainIfModified(album?.authorsText, authorsText),
                            shareUrl = online.url,
                            timestamp = album?.timestamp ?: System.currentTimeMillis(),
                            bookmarkedAt = album?.bookmarkedAt,
                            isYoutubeAlbum = album?.isYoutubeAlbum ?: true,
                            position = album?.position ?: -1
                        )
                    )

                    online.songs.map { it.asMediaItem }.onEach { insertIgnore(it) }
                        .mapIndexed { position, mediaItem ->
                            SongAlbumMap(
                                songId = mediaItem.mediaId,
                                albumId = albumId,
                                position = position
                            )
                        }
                        .also(songAlbumMapTable::upsert)
                }
            }
        } else {
            val albumPage = Innertube.albumPage(browseId = cleanAlbumId)?.getOrNull()
            onlineSongs = albumPage?.songsPage?.items?.toList()?.map { item -> item.asSong }
        }
        
        return if (!onlineSongs.isNullOrEmpty()) {
            AutoSearchState.searchedSongs = (AutoSearchState.searchedSongs + onlineSongs).distinctBy { s -> s.id }
            onlineSongs.mapIndexed { index, song ->
                SessionMediaItemMapper.mapSongToMediaItem(song, actualParentId).let { item ->
                    item.buildUpon()
                        .setMediaMetadata(
                            item.mediaMetadata.buildUpon()
                                .setTrackNumber(index + 1)
                                .build()
                        )
                        .build()
                }
            }
        } else {
            val localSongs = database.songAlbumMapTable.allSongsOf(albumId).first()
            localSongs.mapIndexed { index, song ->
                SessionMediaItemMapper.mapSongToMediaItem(song, actualParentId).let { item ->
                    item.buildUpon()
                        .setMediaMetadata(
                            item.mediaMetadata.buildUpon()
                                .setTrackNumber(index + 1)
                                .build()
                        )
                        .build()
                }
            }
        }
    }
}
