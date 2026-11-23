package app.n_zik.android.playback.services

import java.io.IOException
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import timber.log.Timber
import app.n_zik.android.R
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.playback.services.diagnostics.PLAYBACK_DIAG_TAG

@UnstableApi
internal class PlaybackLoadErrorPolicy(
    private val shouldSkipOnError: () -> Boolean,
    private val onRetryCount: (Int) -> Unit,
    private val isNetworkAvailable: () -> Boolean,
    private val isStaleMonitorOfflineFailure: (Throwable) -> Boolean,
) : DefaultLoadErrorHandlingPolicy() {
    // Let the service handle exhausted retries instead of asking Media3 for a nonexistent fallback.
    override fun isEligibleForFallback(exception: IOException) = false

    override fun getRetryDelayMsFor(
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
    ): Long {
        // Invalidate cached stream URL on every retry so we fetch fresh URLs
        val mediaId = runCatching<String?> {
            loadErrorInfo.loadEventInfo.dataSpec.key
                ?: loadErrorInfo.loadEventInfo.dataSpec.uri.toString().substringAfter("watch?v=", "").takeIf { it.isNotEmpty() }
        }.getOrNull()
        if (mediaId != null) {
            streamUrlCache.invalidate(mediaId)
        }

        val skipOnError = shouldSkipOnError()
        val count = loadErrorInfo.errorCount

        // Issue #881 (gh-881): the stall watchdog report includes this counter
        onRetryCount(count)

        // Use the same DNS result for the gate log.
        val dnsFailure = isDnsFailure(loadErrorInfo.exception)
        val staleMonitorOffline = isStaleMonitorOfflineFailure(loadErrorInfo.exception)

        val delayMs: Long
        if (loadErrorInfo.exception.isFatalCustomException()) {
            delayMs = C.TIME_UNSET
        } else if (isOfflineLoadFailure(loadErrorInfo.exception, isNetworkAvailable()) ||
            staleMonitorOffline
        ) {
            // Offline recovery is handled by the player error callback.
            Timber.tag(PLAYBACK_DIAG_TAG).w(
                "LOAD_GATE offline=%b dnsFailure=%b staleMonitorOffline=%b mediaId=%s -> giving up, deferring to the onPlayerError gate",
                !isNetworkAvailable(),
                dnsFailure,
                staleMonitorOffline,
                mediaId,
            )
            return C.TIME_UNSET
        } else if (count <= 7) {
            // Normal exponential backoff up to 7 retries
            delayMs = (count * 2000L).coerceAtMost(10_000L)
        } else if (!skipOnError) {
            // A positive delay prevents Media3 from skipping the item.
            Toaster.w(R.string.network_stream_still_retrying, formatArgs = arrayOf(count.toString()))
            delayMs = 15_000L
        } else {
            delayMs = C.TIME_UNSET // skipOnError is ON - let ExoPlayer give up and trigger onPlayerError
        }

        Timber.tag(PLAYBACK_DIAG_TAG).w(
            "LOAD_RETRY count=%d mediaId=%s exception=%s delayMs=%d",
            count,
            mediaId,
            diagnosticExceptionSummary(loadErrorInfo.exception),
            delayMs,
        )
        return delayMs
    }
}
