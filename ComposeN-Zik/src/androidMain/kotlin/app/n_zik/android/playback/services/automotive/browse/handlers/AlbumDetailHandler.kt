package app.n_zik.android.playback.services.automotive.browse.handlers

import app.n_zik.innertube.requests.albumPage

import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
import android.content.Context
import androidx.media3.common.MediaItem
import app.n_zik.android.models.Song
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.core.database.artistEntryNames
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.CancellableAutoMediaItemMapper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.playback.services.automotive.models.AutoSearchState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.n_zik.android.models.Album
import app.n_zik.android.models.SongAlbumMap
import app.n_zik.android.core.utils.PropUtils
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YtMusic
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.withPlaybackAlbum

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
        val albumId = parts[1]
        var onlineSongs: List<Song>? = null
        val storedAlbum = database.albumTable.findByIdDirect(albumId)
        var albumTitle = storedAlbum?.cleanTitle()
        suspend fun albumItems(songs: List<Song>): List<MediaItem> = songs.mapIndexed { index, song ->
            val item = CancellableAutoMediaItemMapper.mapSongToMediaItem(song, parentId, loadArtwork = song.isLocal)
                .withPlaybackAlbum(albumId, albumTitle)
            item.buildUpon()
                .setMediaMetadata(item.mediaMetadata.buildUpon().setTrackNumber(index + 1).build())
                .build()
        }
        // The detail list mirrors the phone's album detail (AlbumScreen), which shows
        // every track of the album — no triple dislike filter (the phone filters only
//
        // the playback queue via Shuffler.play, never the detail list; re-negotiation
        // 2026-10-01 « suit l'app »).

        if (!AutoAccessPolicy(context, database, downloadHelper, binder).online || albumId.startsWith("LOCAL_ALBUM_")) {
            val localSongs = database.songAlbumMapTable.allSongsOf(albumId).first()
            return albumItems(localSongs)
        }
        
        val cleanAlbumId = albumId.removePrefix(MODIFIED_PREFIX)
        
        val online = YtMusic.getAlbum(cleanAlbumId, true).getOrNull()
        if (online != null) {
            val onlineAlbum = online.album
            val authorsText: String? = onlineAlbum.authors.artistEntryNames().joinToString(", ")
            albumTitle = storedAlbum?.takeIf { it.title?.startsWith(MODIFIED_PREFIX, true) == true }?.cleanTitle()
                ?: onlineAlbum.title ?: albumTitle
            onlineSongs = online.songs.map { it.asSong }
            
            NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
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
                            dislikedAt = album?.dislikedAt,
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
                        .also { songAlbumMapTable.upsert(it) }
                }
            }
        } else {
            val albumPage = Innertube.albumPage(browseId = cleanAlbumId)?.getOrNull()
            albumTitle = albumTitle?.takeIf(String::isNotBlank) ?: albumPage?.title
            onlineSongs = albumPage?.songsPage?.items?.toList()?.map { item -> item.asSong }
        }
        
        return if (!onlineSongs.isNullOrEmpty()) {
            AutoSearchState.searchedSongs = (AutoSearchState.searchedSongs + onlineSongs).distinctBy { s -> s.id }
            albumItems(onlineSongs)
        } else {
            val localSongs = database.songAlbumMapTable.allSongsOf(albumId).first()
            albumItems(localSongs)
        }
    }
}
