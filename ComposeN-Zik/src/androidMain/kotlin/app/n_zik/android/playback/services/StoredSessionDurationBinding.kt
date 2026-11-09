package app.n_zik.android.playback.services

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import app.n_zik.android.BuildConfig
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Reuses the active song's local duration without delaying playlist application. */
internal class StoredSessionDurationBinding(
    private val scope: CoroutineScope,
    private val currentPlayer: () -> Player,
    private val readDuration: suspend (String) -> Long?,
    private val onResolved: () -> Unit,
) {
    private var player: Player? = null
    private var mediaId: String? = null
    private var attempted = false
    private var generation = 0L
    private var lookup: Job? = null
    private var storedDuration: Long? = null
    private var closed = false
    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = observe()
        override fun onTimelineChanged(timeline: Timeline, reason: Int) = observe()
    }

    /** Called on the player thread when the session changes its player facade. */
    fun refreshPlayer() {
        if (closed) return
        val latest = currentPlayer()
        if (player !== latest) {
            player?.removeListener(listener)
            reset()
            player = latest
            latest.addListener(listener)
        }
        observe()
    }

    fun forMediaId(id: String): Long? = storedDuration.takeIf { !closed && mediaId == id }

    fun close() {
        closed = true
        player?.removeListener(listener)
        player = null
        reset()
    }

    private fun reset() {
        generation++
        lookup?.cancel()
        lookup = null
        mediaId = null
        attempted = false
        storedDuration = null
    }

    private fun observe() {
        val observed = player ?: return
        val item = observed.currentMediaItem
        if (item == null || observed.isCurrentMediaItemLive) {
            reset()
            return
        }
        if (mediaId != item.mediaId) {
            reset()
            mediaId = item.mediaId
        }
        if (attempted || observed.duration > 0 || observed.mediaMetadata.durationMs?.let { it > 0 } == true ||
            item.mediaMetadata.durationMs?.let { it > 0 } == true ||
            SessionDurationMetadata.fromDurationText(item.mediaMetadata.extras?.getString("durationText")) != null
        ) return
        attempted = true
        val requestedId = item.mediaId
        val requestedGeneration = generation
        val requestedAt = SystemClock.elapsedRealtime()
        diagnostic("request", requestedId, requestedGeneration, requestedAt)
        // observe runs on the player thread. Submit the database read now rather than
        // queueing another UI turn behind artwork and other transition callbacks.
        lookup = scope.launch(NzikDispatchers.UI, start = CoroutineStart.UNDISPATCHED) {
            try {
                val duration = withContext(NzikDispatchers.DATA) {
                    diagnostic("read_start", requestedId, requestedGeneration, requestedAt)
                    readDuration(requestedId).also {
                        diagnostic("read_end", requestedId, requestedGeneration, requestedAt, it)
                    }
                }
                ensureActive()
                diagnostic("delivery", requestedId, requestedGeneration, requestedAt, duration)
                if (closed || generation != requestedGeneration || currentPlayer() !== observed ||
                    observed.currentMediaItem?.mediaId != requestedId || observed.isCurrentMediaItemLive
                ) {
                    diagnostic("discarded", requestedId, requestedGeneration, requestedAt, duration)
                    return@launch
                }
                if (duration != null && duration > 0) {
                    storedDuration = duration
                    onResolved()
                }
            } catch (error: CancellationException) {
                diagnostic("cancelled", requestedId, requestedGeneration, requestedAt)
                throw error
            } catch (error: Exception) {
                Timber.tag("SessionDuration").w(error, "Could not read stored track length")
            }
        }
    }

    private fun diagnostic(event: String, id: String, request: Long, startedAt: Long, duration: Long? = null) {
        if (BuildConfig.DEBUG) Timber.tag("StoredSessionDuration").d(
            "%s id=%s request=%d elapsedMs=%d durationMs=%s",
            event, id, request, SystemClock.elapsedRealtime() - startedAt, duration,
        )
    }
}
