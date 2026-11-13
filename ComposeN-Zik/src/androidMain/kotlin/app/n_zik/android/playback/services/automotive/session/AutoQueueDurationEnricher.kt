package app.n_zik.android.playback.services.automotive.session

import androidx.media3.common.MediaItem
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.SessionDurationMetadata
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Adds known local lengths to stale browse snapshots without changing their queue identity. */
internal suspend fun enrichAutoQueueDurations(
    items: List<MediaItem>,
    findSongs: (List<String>) -> List<Song>,
): List<MediaItem> = withContext(NzikDispatchers.DATA) {
    val missingIds = items.filterNot { it.hasDeclaredDuration() }.map { it.mediaId }.distinct()
    if (missingIds.isEmpty()) return@withContext items
    val durations = mutableMapOf<String, Long>()
    try {
        // Stay below SQLite's conservative 999 bind-argument limit, including long AA queues.
        for (ids in missingIds.chunked(900)) {
            currentCoroutineContext().ensureActive()
            findSongs(ids).forEach { song ->
                SessionDurationMetadata.fromDurationText(song.durationText)?.let { durations[song.id] = it }
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // Optional metadata must not make an otherwise playable selection fail.
        Timber.tag("AutoQueueDuration").w(error, "Could not read stored track lengths")
        return@withContext items
    }
    currentCoroutineContext().ensureActive()
    items.map { item ->
        val duration = durations[item.mediaId]?.takeUnless { item.hasDeclaredDuration() }
        if (duration == null) item else item.buildUpon()
            .setMediaMetadata(item.mediaMetadata.buildUpon().setDurationMs(duration).build()).build()
    }
}

private fun MediaItem.hasDeclaredDuration(): Boolean = mediaMetadata.durationMs?.takeIf { it > 0 } != null ||
    SessionDurationMetadata.fromDurationText(mediaMetadata.extras?.getString("durationText")) != null
