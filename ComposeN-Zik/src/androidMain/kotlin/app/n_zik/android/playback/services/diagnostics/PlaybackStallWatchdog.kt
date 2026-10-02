package app.n_zik.android.playback.services.diagnostics

/**
 * Common Timber tag for every line of the issue #881 instrumentation lot (stall watchdog,
 * transition journal, retry log, resolution timing, screen/network markers) — one grep on
 * the exported log file surfaces the whole diagnostic picture
 * (spec problem-solution-2026-10-02-progress-bar-stuck-gh881, S11).
 */
const val PLAYBACK_DIAG_TAG = "PlaybackDiag"

/**
 * Pure stall-detection state machine for playback diagnostics (issue #881).
 *
 * Feed [sample] periodically (every [DEFAULT_SAMPLING_INTERVAL_MS]) while the player is in
 * `playWhenReady`. A stall is detected when the player stays in `BUFFERING`, or the
 * position stops advancing in `READY`, for at least [stallThresholdMs], and it yields
 * exactly ONE [Decision.Stalled] per stall — subsequent samples while still stalled keep
 * returning [Decision.Ok] (no log spam). Once the player recovers the watchdog re-arms and
 * yields a single [Decision.Recovered]. `IDLE`/`ENDED` (playback legitimately stopped —
 * end of queue, stop, error) reset the tracking like a pause: they are not stalls, and
 * counting them would report a phantom stall right after normal playback ends.
 *
 * Android-free on purpose: unit-testable on the JVM (spec, action 2).
 */
class PlaybackStallWatchdog(
    private val stallThresholdMs: Long = DEFAULT_STALL_THRESHOLD_MS,
) {

    /** Outcome of one [sample]. */
    sealed interface Decision {
        /** Nothing to report. */
        data object Ok : Decision

        /** The stall crossed the threshold — exactly one per stall. */
        data class Stalled(val report: StallReport) : Decision

        /** A previously reported stall cleared — exactly one per stall. */
        data object Recovered : Decision
    }

    /** Everything needed to localize the stall, in a single log line. */
    data class StallReport(
        val mediaId: String?,
        val playbackState: Int,
        val isLoading: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val stallDurationMs: Long,
        val retryCount: Int,
        val networkAvailable: Boolean,
        val playerIdentity: Int,
    )

    private var lastMediaId: String? = null
    private var lastPlayerIdentity: Int = 0
    private var lastPositionMs: Long = 0L
    private var stallStartMs: Long? = null
    private var reportEmitted = false

    /**
     * Discards all tracking state. Call when sampling stops (pause, teardown): a paused
     * player cannot stall, and a post-resume report must measure only the continuous
     * stalling since the resume — the stall clock must not survive the pause
     * (spec S1 threshold; review gh-881).
     */
    fun reset() {
        lastMediaId = null
        lastPlayerIdentity = 0
        lastPositionMs = 0L
        stallStartMs = null
        reportEmitted = false
    }

    /**
     * Records one periodic sample.
     *
     * @param nowMs monotonic clock (ms) — injected so tests can drive time.
     * @param playWhenReady when false all tracking resets (a paused player cannot stall).
     * @param playbackState `Player.STATE_*` value of the sampled player.
     * @param playerIdentity `System.identityHashCode` of the sampled ExoPlayer — a change
     *   (crossfade swap) resets the baseline, since the new player's buffer fill is expected.
     */
    fun sample(
        nowMs: Long,
        playWhenReady: Boolean,
        playbackState: Int,
        isLoading: Boolean,
        positionMs: Long,
        durationMs: Long,
        mediaId: String?,
        retryCount: Int,
        networkAvailable: Boolean,
        playerIdentity: Int,
    ): Decision {
        if (!playWhenReady) {
            reset()
            return Decision.Ok
        }

        // IDLE/ENDED (playback legitimately stopped — queue end, stop, error): not a stall.
        // Drop tracking like a pause so no phantom report fires after normal playback ends
        // and no spurious "recovered" line is emitted (review gh-881).
        if (playbackState != PLAYER_STATE_BUFFERING && playbackState != PLAYER_STATE_READY) {
            reset()
            return Decision.Ok
        }

        // New media or new player instance (track change / crossfade swap): fresh baseline —
        // the new buffer fill is expected, not a stall.
        if (mediaId != lastMediaId || playerIdentity != lastPlayerIdentity) {
            lastMediaId = mediaId
            lastPlayerIdentity = playerIdentity
            lastPositionMs = positionMs
            stallStartMs = null
            reportEmitted = false
            return Decision.Ok
        }

        val stalled = isStalledCondition(playbackState, positionMs)
        return if (!stalled) {
            lastPositionMs = positionMs
            if (reportEmitted) {
                reset()
                Decision.Recovered
            } else {
                Decision.Ok
            }
        } else {
            val start = stallStartMs ?: nowMs.also { stallStartMs = it }
            val stallDurationMs = nowMs - start
            if (stallDurationMs >= stallThresholdMs && !reportEmitted) {
                reportEmitted = true
                Decision.Stalled(
                    StallReport(
                        mediaId = mediaId,
                        playbackState = playbackState,
                        isLoading = isLoading,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        stallDurationMs = stallDurationMs,
                        retryCount = retryCount,
                        networkAvailable = networkAvailable,
                        playerIdentity = playerIdentity,
                    )
                )
            } else {
                Decision.Ok
            }
        }
    }

    /**
     * A player that should be playing is stalled when it is BUFFERING, or READY but the
     * position did not advance since the previous sample (a playing READY player advances
     * on every sample). [sample] has already ruled out IDLE/ENDED, so only these two states
     * reach here. The reported stall duration is an underestimate of at most one sampling
     * interval.
     */
    private fun isStalledCondition(playbackState: Int, positionMs: Long): Boolean =
        playbackState == PLAYER_STATE_BUFFERING || positionMs == lastPositionMs

    companion object {
        /** Sampling period while `playWhenReady` is true (spec: every 3 s). */
        const val DEFAULT_SAMPLING_INTERVAL_MS = 3_000L

        /**
         * A stall only reports after this duration (spec: 15 s — deliberately beyond the
         * initial retry backoff, ~7 × 2 s, so transient buffer fills never report).
         */
        const val DEFAULT_STALL_THRESHOLD_MS = 15_000L

        /** `Player.STATE_IDLE` — duplicated so this file stays Android-free (media3: IDLE is 1, not 0). */
        const val PLAYER_STATE_IDLE = 1

        /** `Player.STATE_BUFFERING` — duplicated so this file stays Android-free. */
        const val PLAYER_STATE_BUFFERING = 2

        /** `Player.STATE_READY` — duplicated so this file stays Android-free. */
        const val PLAYER_STATE_READY = 3

        /** `Player.STATE_ENDED` — duplicated so this file stays Android-free. */
        const val PLAYER_STATE_ENDED = 4
    }
}
