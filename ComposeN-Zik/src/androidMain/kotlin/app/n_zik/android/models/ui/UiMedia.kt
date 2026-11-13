package app.n_zik.android.models.ui

import androidx.media3.common.MediaItem
import app.n_zik.android.playback.services.isLocal

data class UiMedia(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Long,
    val isLocal: Boolean
)

fun MediaItem.toUiMedia(duration: Long) = UiMedia(
    id = mediaId,
    title = mediaMetadata.title?.toString() ?: "",
    artist = mediaMetadata.artist?.toString() ?: "",
    duration = duration,
    isLocal = isLocal
)


