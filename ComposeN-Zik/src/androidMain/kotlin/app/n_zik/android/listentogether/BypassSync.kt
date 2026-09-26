package app.n_zik.android.listentogether

import kotlinx.coroutines.delay

/** Poll delay for the bounded readiness wait (bypass playback-state apply). */
internal const val BYPASS_READY_WAIT_DELAY_MS = 50L

/** Poll budget for the bounded readiness wait (bypass playback-state apply). */
internal const val BYPASS_READY_WAIT_ATTEMPTS = 100

/**
 * Outcome of the bounded readiness wait used by bypass (manual sync /
 * reconnect) playback-state applies.
 */
internal data class BypassReadyWait(
    val ready: Boolean,
    val waitedMs: Long,
)

/**
 * Waits, in [delayMs] steps, until [isReady] reports true or the budget of
 * [maxAttempts] polls is exhausted.
 *
 * Callers MUST apply the pending play/pause regardless of [ready]: ExoPlayer
 * queues playWhenReady until the stream is ready, so the historical behaviour
 * (dropping the play command when the outcome was not-ready) left guests
 * stuck paused after a reconnect whenever the stream took longer than the
 * budget to load.
 */
internal suspend fun runBypassReadyWait(
    isReady: () -> Boolean,
    delayMs: Long = BYPASS_READY_WAIT_DELAY_MS,
    maxAttempts: Int = BYPASS_READY_WAIT_ATTEMPTS,
): BypassReadyWait {
    var attempts = 0
    while (!isReady() && attempts < maxAttempts) {
        delay(delayMs)
        attempts++
    }
    return BypassReadyWait(ready = isReady(), waitedMs = attempts.toLong() * delayMs)
}
