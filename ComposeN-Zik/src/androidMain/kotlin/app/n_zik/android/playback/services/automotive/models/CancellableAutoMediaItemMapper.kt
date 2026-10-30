package app.n_zik.android.playback.services.automotive.models

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext

/** Suspends through artwork loading so callbacks never block their DATA worker while waiting. */
@UnstableApi
object CancellableAutoMediaItemMapper {
    suspend fun mapSongToMediaItem(
        song: Song,
        path: String,
        loadArtwork: Boolean = false,
    ): MediaItem = withContext(NzikDispatchers.DATA) {
        if (loadArtwork) SessionMediaItemMapper.mapSongWithArtwork(song, path)
        else SessionMediaItemMapper.mapSongToMediaItem(song, path)
    }

    suspend fun mapSongToMediaItem(
        song: Song,
        isFromPersistentQueue: Boolean = false,
        loadArtwork: Boolean = false,
    ): MediaItem = withContext(NzikDispatchers.DATA) {
        if (loadArtwork) SessionMediaItemMapper.mapSongWithArtwork(song, isFromPersistentQueue)
        else SessionMediaItemMapper.mapSongToMediaItem(song, isFromPersistentQueue)
    }

    suspend fun mapArtistToMediaItem(
        parentId: String,
        id: String,
        name: String,
        thumbnailUrl: String?,
        subtext: String? = null,
        searchPath: String = "",
        loadArtwork: Boolean = false,
    ): MediaItem = withContext(NzikDispatchers.DATA) {
        if (loadArtwork) SessionMediaItemMapper.mapArtistWithArtwork(
            parentId, id, name, thumbnailUrl, subtext, searchPath,
        ) else SessionMediaItemMapper.mapArtistToMediaItem(
            parentId, id, name, thumbnailUrl, subtext, searchPath,
        )
    }

    suspend fun mapAlbumToMediaItem(
        parentId: String,
        id: String,
        title: String,
        authorsText: String?,
        thumbnailUrl: String?,
        searchPath: String = "",
        loadArtwork: Boolean = false,
    ): MediaItem = withContext(NzikDispatchers.DATA) {
        if (loadArtwork) SessionMediaItemMapper.mapAlbumWithArtwork(
            parentId, id, title, authorsText, thumbnailUrl, searchPath,
        ) else SessionMediaItemMapper.mapAlbumToMediaItem(
            parentId, id, title, authorsText, thumbnailUrl, searchPath,
        )
    }
}
