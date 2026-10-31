package app.n_zik.android.playback.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CrossfadeTimelineTest {
    @Test
    fun `irregular updates follow elapsed clock time instead of update count`() {
        val timeline = CrossfadeTimeline(startedAtMs = 1_000L, playing = true)

        assertEquals(0L, timeline.elapsedMs(1_000L))
        assertEquals(160L, timeline.elapsedMs(1_160L))
        assertEquals(2_700L, timeline.elapsedMs(3_700L))
        assertEquals(8_000L, timeline.elapsedMs(9_000L))
        assertEquals(9_400L, timeline.elapsedMs(10_400L))
    }

    @Test
    fun `initially paused timeline starts counting only after resume`() {
        val timeline = CrossfadeTimeline(startedAtMs = 1_000L, playing = false)

        assertEquals(0L, timeline.elapsedMs(6_000L))
        timeline.setPlaying(playing = true, nowMs = 6_000L)
        assertEquals(0L, timeline.elapsedMs(6_000L))
        assertEquals(500L, timeline.elapsedMs(6_500L))
    }

    @Test
    fun `repeated pause and resume notifications do not reset accumulated time`() {
        val timeline = CrossfadeTimeline(startedAtMs = 1_000L, playing = true)

        timeline.setPlaying(playing = true, nowMs = 1_200L)
        timeline.setPlaying(playing = false, nowMs = 1_500L)
        timeline.setPlaying(playing = false, nowMs = 2_000L)
        assertEquals(500L, timeline.elapsedMs(3_000L))
        timeline.setPlaying(playing = true, nowMs = 3_000L)
        timeline.setPlaying(playing = true, nowMs = 3_200L)
        assertEquals(1_000L, timeline.elapsedMs(3_500L))
        timeline.setPlaying(playing = false, nowMs = 3_500L)
        assertEquals(1_000L, timeline.elapsedMs(7_000L))
        timeline.setPlaying(playing = true, nowMs = 7_000L)
        assertEquals(1_250L, timeline.elapsedMs(7_250L))
    }

    @Test
    fun `pause entirely between fade samples is excluded`() {
        val timeline = CrossfadeTimeline(startedAtMs = 1_000L, playing = true)

        assertEquals(400L, timeline.elapsedMs(1_400L))
        timeline.setPlaying(playing = false, nowMs = 1_450L)
        timeline.setPlaying(playing = true, nowMs = 4_450L)

        assertEquals(500L, timeline.elapsedMs(4_500L))
        assertEquals(8_000L, timeline.elapsedMs(12_000L))
    }
}
