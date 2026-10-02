package app.n_zik.android.playback.services.diagnostics

import androidx.media3.common.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PlaybackStallWatchdogTest {

    private val watchdog = PlaybackStallWatchdog()

    /** `Player.STATE_BUFFERING` — see the watchdog's PLAYER_STATE_READY mirror. */
    private val buffering = 2

    private fun sample(
        nowMs: Long,
        playWhenReady: Boolean = true,
        playbackState: Int = PlaybackStallWatchdog.PLAYER_STATE_READY,
        isLoading: Boolean = false,
        positionMs: Long,
        durationMs: Long = 200_000L,
        mediaId: String? = "media-1",
        retryCount: Int = 0,
        networkAvailable: Boolean = true,
        playerIdentity: Int = 42,
    ) = watchdog.sample(
        nowMs = nowMs,
        playWhenReady = playWhenReady,
        playbackState = playbackState,
        isLoading = isLoading,
        positionMs = positionMs,
        durationMs = durationMs,
        mediaId = mediaId,
        retryCount = retryCount,
        networkAvailable = networkAvailable,
        playerIdentity = playerIdentity,
    )

    /** Samples every 3 s from [startMs], advancing [positionStepMs] per sample when [advancing]. */
    private fun decisions(
        startMs: Long = 0L,
        count: Int = 10,
        advancing: Boolean = true,
        playbackState: Int = PlaybackStallWatchdog.PLAYER_STATE_READY,
        mediaId: String? = "media-1",
        playerIdentity: Int = 42,
        retryCount: Int = 0,
        networkAvailable: Boolean = true,
    ): List<PlaybackStallWatchdog.Decision> = (0 until count).map { i ->
        val position = if (advancing) i * 3_000L else 0L
        sample(
            nowMs = startMs + i * PlaybackStallWatchdog.DEFAULT_SAMPLING_INTERVAL_MS,
            playbackState = playbackState,
            positionMs = position,
            mediaId = mediaId,
            playerIdentity = playerIdentity,
            retryCount = retryCount,
            networkAvailable = networkAvailable,
        )
    }

    @Test
    fun `PLAYER_STATE_* mirror the ExoPlayer constants`() {
        assertEquals(Player.STATE_IDLE, PlaybackStallWatchdog.PLAYER_STATE_IDLE)
        assertEquals(Player.STATE_BUFFERING, PlaybackStallWatchdog.PLAYER_STATE_BUFFERING)
        assertEquals(Player.STATE_READY, PlaybackStallWatchdog.PLAYER_STATE_READY)
        assertEquals(Player.STATE_ENDED, PlaybackStallWatchdog.PLAYER_STATE_ENDED)
    }

    @Test
    fun `default thresholds match the spec`() {
        assertEquals(3_000L, PlaybackStallWatchdog.DEFAULT_SAMPLING_INTERVAL_MS)
        assertEquals(15_000L, PlaybackStallWatchdog.DEFAULT_STALL_THRESHOLD_MS)
    }

    @Nested
    inner class StablePlayback {

        @Test
        fun `advancing ready playback produces no report`() {
            val decisions = decisions(count = 20)

            assertTrue(decisions.all { it is PlaybackStallWatchdog.Decision.Ok })
        }
    }

    @Nested
    inner class SustainedBuffering {

        @Test
        fun `buffering beyond the threshold emits exactly one stall report`() {
            val decisions = decisions(count = 10, advancing = false, playbackState = buffering)

            val stalled = decisions.filterIsInstance<PlaybackStallWatchdog.Decision.Stalled>()
            assertEquals(1, stalled.size, "one report per stall, no duplicates")
            // Samples: t=0 (baseline), stall clock starts at t=3000 (first stalled sample) →
            // first sample with elapsed ≥ 15 000 ms is t=18000, the 7th sample.
            assertEquals(7, decisions.indexOf(stalled.first()) + 1, "report fires at the 7th sample (t=18 s)")
        }

        @Test
        fun `stall report carries every diagnostic field`() {
            val decisions = decisions(
                count = 10,
                advancing = false,
                playbackState = buffering,
                mediaId = "abc123",
                retryCount = 9,
                networkAvailable = false,
                playerIdentity = 99,
            )
            val report = decisions.filterIsInstance<PlaybackStallWatchdog.Decision.Stalled>()
                .first().report

            assertEquals("abc123", report.mediaId)
            assertEquals(buffering, report.playbackState)
            assertEquals(0L, report.positionMs)
            assertEquals(200_000L, report.durationMs)
            assertTrue(report.stallDurationMs >= 15_000L)
            assertEquals(9, report.retryCount)
            assertEquals(false, report.networkAvailable)
            assertEquals(99, report.playerIdentity)
        }

        @Test
        fun `buffering below the threshold emits nothing`() {
            // 12 s of buffering (5 samples incl. baseline) then recovery — under the 15 s threshold.
            val stalled = decisions(count = 5, advancing = false, playbackState = buffering)
                .filterIsInstance<PlaybackStallWatchdog.Decision.Stalled>()

            assertTrue(stalled.isEmpty())
        }
    }

    @Nested
    inner class FrozenPositionInReady {

        @Test
        fun `ready with frozen position emits a stall report`() {
            val decisions = decisions(count = 10, advancing = false)

            val stalled = decisions.filterIsInstance<PlaybackStallWatchdog.Decision.Stalled>()
            assertEquals(1, stalled.size)
            assertEquals(PlaybackStallWatchdog.PLAYER_STATE_READY, stalled.first().report.playbackState)
        }
    }

    @Nested
    inner class Recovery {

        @Test
        fun `recovery emits exactly one recovered decision and re-arms the watchdog`() {
            // Stall (10 samples), recovery (3 advancing samples), stall again (7 samples).
            val firstStall = decisions(count = 10, advancing = false)
            // The recovery samples must continue from the last frozen position (0) — same
            // watchdog instance, so the position baseline carries over.
            val recoveryDecisions = listOf(
                sample(nowMs = 30_000L, positionMs = 3_000L),
                sample(nowMs = 33_000L, positionMs = 6_000L),
                sample(nowMs = 36_000L, positionMs = 9_000L),
            )
            val secondStall = listOf(
                sample(nowMs = 39_000L, positionMs = 9_000L),
                sample(nowMs = 42_000L, positionMs = 9_000L),
                sample(nowMs = 45_000L, positionMs = 9_000L),
                sample(nowMs = 48_000L, positionMs = 9_000L),
                sample(nowMs = 51_000L, positionMs = 9_000L),
                sample(nowMs = 54_000L, positionMs = 9_000L),
                sample(nowMs = 57_000L, positionMs = 9_000L),
            )

            val all = firstStall + recoveryDecisions + secondStall
            val stalled = all.filterIsInstance<PlaybackStallWatchdog.Decision.Stalled>()
            val recovered = all.filterIsInstance<PlaybackStallWatchdog.Decision.Recovered>()

            assertEquals(2, stalled.size, "watchdog re-armed after recovery reports the second stall")
            assertEquals(1, recovered.size, "exactly one recovered decision per stall")
        }

        @Test
        fun `transient stall without report emits no recovered decision`() {
            val transient = decisions(count = 5, advancing = false, playbackState = buffering)
            val recovered = listOf(
                sample(nowMs = 15_000L, positionMs = 3_000L),
                sample(nowMs = 18_000L, positionMs = 6_000L),
            )

            val all = transient + recovered
            assertTrue(all.filterIsInstance<PlaybackStallWatchdog.Decision.Stalled>().isEmpty())
            assertTrue(
                all.filterIsInstance<PlaybackStallWatchdog.Decision.Recovered>().isEmpty(),
                "recovered is only emitted for a previously REPORTED stall",
            )
        }
    }

    @Nested
    inner class Resets {

        @Test
        fun `paused player resets tracking`() {
            val stalled = decisions(count = 10, advancing = false)
            val paused = sample(nowMs = 30_000L, playWhenReady = false, positionMs = 0L)
            val resumed = listOf(
                sample(nowMs = 33_000L, playWhenReady = true, playbackState = buffering, positionMs = 0L),
                sample(nowMs = 36_000L, playWhenReady = true, playbackState = buffering, positionMs = 0L),
            )

            val all = stalled + paused + resumed
            assertEquals(1, all.filterIsInstance<PlaybackStallWatchdog.Decision.Stalled>().size)
            // After the pause, the resumed buffering restarts the threshold clock: the two
            // post-resume samples (6 s total) must not report a second stall.
            assertTrue(
                resumed.none { it is PlaybackStallWatchdog.Decision.Stalled },
                "resume restarts the stall threshold — no second report yet",
            )
        }

        @Test
        fun `media change resets the baseline`() {
            val stalled = decisions(count = 10, advancing = false, mediaId = "media-1")
            val newMedia = listOf(
                sample(nowMs = 30_000L, mediaId = "media-2", positionMs = 0L),
                sample(nowMs = 33_000L, mediaId = "media-2", positionMs = 0L),
            )

            val all = stalled + newMedia
            assertEquals(1, all.filterIsInstance<PlaybackStallWatchdog.Decision.Stalled>().size)
            assertTrue(newMedia.none { it is PlaybackStallWatchdog.Decision.Stalled })
        }

        @Test
        fun `player identity change resets the baseline like a crossfade swap`() {
            val stalled = decisions(count = 10, advancing = false, playerIdentity = 11)
            val swapped = listOf(
                sample(nowMs = 30_000L, playerIdentity = 22, positionMs = 0L),
                sample(nowMs = 33_000L, playerIdentity = 22, positionMs = 0L),
            )

            val all = stalled + swapped
            assertEquals(1, all.filterIsInstance<PlaybackStallWatchdog.Decision.Stalled>().size)
            assertTrue(swapped.none { it is PlaybackStallWatchdog.Decision.Stalled })
        }
    }

    @Nested
    inner class StoppedPlayback {

        @Test
        fun `idle playback never reports a stall`() {
            // Queue exhausted: the player sits in IDLE while playWhenReady stays true.
            // Counting it as a stall would report a phantom STALL ~15 s after playback ends.
            val decisions = decisions(count = 10, advancing = false, playbackState = PlaybackStallWatchdog.PLAYER_STATE_IDLE)

            assertTrue(decisions.all { it is PlaybackStallWatchdog.Decision.Ok })
        }

        @Test
        fun `ended playback resets tracking like a pause and emits no recovered`() {
            val stalled = decisions(count = 10, advancing = false, playbackState = buffering)
            val ended = listOf(
                sample(nowMs = 30_000L, playbackState = PlaybackStallWatchdog.PLAYER_STATE_ENDED, positionMs = 0L),
                sample(nowMs = 33_000L, playbackState = PlaybackStallWatchdog.PLAYER_STATE_ENDED, positionMs = 0L),
            )

            val all = stalled + ended
            assertEquals(1, all.filterIsInstance<PlaybackStallWatchdog.Decision.Stalled>().size)
            assertTrue(
                all.filterIsInstance<PlaybackStallWatchdog.Decision.Recovered>().isEmpty(),
                "playback ending is not a recovery — no STALL_RECOVERED line after the queue ends",
            )
        }

        @Test
        fun `reset() mid-stall restarts the threshold clock`() {
            val watchdog = PlaybackStallWatchdog()
            fun s(nowMs: Long) = watchdog.sample(
                nowMs = nowMs,
                playWhenReady = true,
                playbackState = PlaybackStallWatchdog.PLAYER_STATE_BUFFERING,
                isLoading = false,
                positionMs = 0L,
                durationMs = 200_000L,
                mediaId = "media-1",
                retryCount = 0,
                networkAvailable = true,
                playerIdentity = 42,
            )

            // Six stalled samples (t=0…15 s) — the report would fire at the 7th sample.
            val before = (0 until 6).map { s(it * 3_000L) }
            assertTrue(before.all { it is PlaybackStallWatchdog.Decision.Ok }, "no report before the reset")
            watchdog.reset() // e.g. the user pauses (stopStallWatchdogSampling)
            // Sampling resumes at t=21 s: the clock restarts from the first post-reset sample.
            val after = (0 until 8).map { s((21 + it * 3) * 1_000L) }

            val report = (after.filterIsInstance<PlaybackStallWatchdog.Decision.Stalled>())
            assertEquals(1, report.size, "the watchdog re-armed after the reset")
            assertEquals(15_000L, report.first().report.stallDurationMs, "the pause time is not counted")
        }
    }
}
