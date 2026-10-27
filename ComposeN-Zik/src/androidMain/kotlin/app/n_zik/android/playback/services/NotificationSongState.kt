package app.n_zik.android.playback.services

import androidx.media3.common.MediaItem
import app.it.fast4x.rimusic.models.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Keeps the requested player identity attached to asynchronous Room results. */
internal data class NotificationSongState(val mediaId: String?, val likedAt: Long?)

/** Resets likes before waiting for each new track's Room row. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun notificationSongStates(
    mediaItems: Flow<MediaItem?>,
    findSong: (String) -> Flow<Song?>,
): Flow<NotificationSongState> = mediaItems
    .map { it?.mediaId }
    .distinctUntilChanged()
    .flatMapLatest { mediaId ->
        val unknown = NotificationSongState(mediaId, null)
        if (mediaId == null) {
            flowOf(unknown)
        } else {
            val songId = mediaId.substringAfterLast('/')
            findSong(songId)
                .map { song -> NotificationSongState(mediaId, song?.takeIf { it.id == songId }?.likedAt) }
                .onStart { emit(unknown) }
        }
    }
