package app.n_zik.android.playback.services.diagnostics

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import app.n_zik.android.utils.coroutines.NzikDispatchers

/** Samples the current primary player while playWhenReady is true. */
@UnstableApi
internal class PlaybackServiceDiagnostics(
    private val coroutineScope: CoroutineScope,
    private val player: () -> Player,
    private val isNetworkAvailable: () -> Boolean,
) : Player.Listener {
    private val stallWatchdog = PlaybackStallWatchdog()
    private var stallWatchdogJob: Job? = null

    @Volatile
    private var diagnosticIsLoading = false

    @Volatile
    var diagnosticRetryCount = 0

    override fun onPlaybackStateChanged(playbackState: Int) {
        Timber.tag(PLAYBACK_DIAG_TAG).d("STATE -> %s", diagnosticStateName(playbackState))
    }

    override fun onIsLoadingChanged(isLoading: Boolean) {
        diagnosticIsLoading = isLoading
        Timber.tag(PLAYBACK_DIAG_TAG).d("LOADING -> %s", isLoading)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // Keep the previous track's retry count out of later stall reports.
        diagnosticRetryCount = 0
        Timber.tag(PLAYBACK_DIAG_TAG).d(
            "MEDIA_TRANSITION reason=%s mediaId=%s",
            diagnosticTransitionName(reason),
            mediaItem?.mediaId,
        )
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        Timber.tag(PLAYBACK_DIAG_TAG).d(
            "PLAY_WHEN_READY -> %s reason=%s",
            playWhenReady,
            diagnosticPlayWhenReadyReasonName(reason),
        )
        if (playWhenReady) startStallWatchdogSampling() else stopStallWatchdogSampling()
    }

    private fun startStallWatchdogSampling() {
        stallWatchdogJob?.cancel()
        stallWatchdogJob = coroutineScope.launch(NzikDispatchers.UI) {
            while (isActive) {
                sampleStallWatchdog()
                delay(PlaybackStallWatchdog.DEFAULT_SAMPLING_INTERVAL_MS)
            }
        }
    }

    fun stopStallWatchdogSampling() {
        stallWatchdogJob?.cancel()
        stallWatchdogJob = null
        // Measure only continuous stalling since playback resumed.
        stallWatchdog.reset()
    }

    private fun sampleStallWatchdog() {
        val p = player()
        if (!p.playWhenReady) return
        when (val decision = stallWatchdog.sample(
            nowMs = SystemClock.elapsedRealtime(),
            playWhenReady = true,
            playbackState = p.playbackState,
            isLoading = diagnosticIsLoading,
            positionMs = p.currentPosition,
            // Use -1 for streams without a duration in the diagnostics report.
            durationMs = if (p.duration == C.TIME_UNSET) -1L else p.duration,
            mediaId = p.currentMediaItem?.mediaId,
            retryCount = diagnosticRetryCount,
            networkAvailable = isNetworkAvailable(),
            playerIdentity = System.identityHashCode(p),
        )) {
            is PlaybackStallWatchdog.Decision.Ok -> Unit
            is PlaybackStallWatchdog.Decision.Stalled -> {
                val r = decision.report
                Timber.tag(PLAYBACK_DIAG_TAG).w(
                    "STALL mediaId=%s state=%s loading=%s position=%dms duration=%dms stallMs=%d retries=%d network=%s playerIdentity=%s",
                    r.mediaId,
                    diagnosticStateName(r.playbackState),
                    r.isLoading,
                    r.positionMs,
                    r.durationMs,
                    r.stallDurationMs,
                    r.retryCount,
                    r.networkAvailable,
                    r.playerIdentity,
                )
            }
            PlaybackStallWatchdog.Decision.Recovered ->
                Timber.tag(PLAYBACK_DIAG_TAG).i("STALL_RECOVERED")
        }
    }

    /** Issue #881: human-readable player state for diagnostic lines. */
    private fun diagnosticStateName(state: Int): String = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        else -> "UNKNOWN($state)"
    }

    /** Issue #881: human-readable media-item transition reason for diagnostic lines. */
    private fun diagnosticTransitionName(reason: Int): String = when (reason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
        Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
        else -> "UNKNOWN($reason)"
    }

    /** Issue #881: human-readable playWhenReady change reason for diagnostic lines. */
    private fun diagnosticPlayWhenReadyReasonName(reason: Int): String = when (reason) {
        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
        Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
        Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "END_OF_MEDIA_ITEM"
        Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG -> "SUPPRESSED_TOO_LONG"
        else -> "UNKNOWN($reason)"
    }
}
