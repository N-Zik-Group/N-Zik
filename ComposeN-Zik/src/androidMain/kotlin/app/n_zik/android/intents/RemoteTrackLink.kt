package app.n_zik.android.intents

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.utils.asMediaItem
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.nextPage
import app.n_zik.innertube.requests.song
import kotlinx.coroutines.CancellationException

private val youtubeVideoId = Regex("[A-Za-z0-9_-]{11}")

internal data class RemoteTrackLink(val item: MediaItem, val explicit: Boolean?)

@androidx.annotation.OptIn(UnstableApi::class)
internal suspend fun resolveRemoteTrackLink(videoId: String): RemoteTrackLink? {
    if (!youtubeVideoId.matches(videoId)) return null
    val track = Innertube.song(videoId).valueOrThrowCancellation()?.takeIf { it.key == videoId }
        ?: Innertube.nextPage(videoId = videoId).valueOrThrowCancellation()?.itemsPage?.items
            ?.firstOrNull { it.info?.endpoint?.videoId == videoId }
    return if (track != null) {
        RemoteTrackLink(track.asMediaItem, track.explicit)
    } else {
        val item = MediaItem.Builder().setMediaId(videoId).setUri(videoId).setCustomCacheKey(videoId).build()
        RemoteTrackLink(item, explicit = null)
    }
}

private fun <T> Result<T>?.valueOrThrowCancellation(): T? =
    this?.onFailure { if (it is CancellationException) throw it }?.getOrNull()
