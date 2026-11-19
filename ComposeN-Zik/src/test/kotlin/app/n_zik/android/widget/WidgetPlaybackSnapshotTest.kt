package app.n_zik.android.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetPlaybackSnapshotTest {
    @Test
    fun renderLatencyAdvancesTimelineWithoutChangingChronometerBase() {
        val playback = WidgetPlaybackSnapshot(
            durationMs = 120_000,
            positionMs = 30_000,
            sampledAtElapsedRealtimeMs = 100_000,
            isPlaying = true,
        )

        assertEquals(31_200L, playback.positionAt(101_200))
        assertEquals(2_600, playback.progressLevelAt(101_200))
        assertEquals(70_000L, playback.chronometerBaseAt(101_200))
        assertEquals(70_000L, playback.chronometerBaseAt(102_400))
    }

    @Test
    fun pausedSeekValueStaysFrozenDuringRenderingAndResize() {
        val playback = WidgetPlaybackSnapshot(120_000, 72_500, 100_000, isPlaying = false)

        assertEquals(72_500L, playback.positionAt(150_000))
        assertEquals(6_041, playback.progressLevelAt(150_000))
        assertNull(playback.chronometerBaseAt(150_000))
        assertEquals(0L, playback.copy(positionMs = -1).positionAt(150_000))
    }

    @Test
    fun alternateSpeedsUseProjectedPositionWithoutAHostChronometer() {
        val playback = WidgetPlaybackSnapshot(60_000, 10_000, 100_000, isPlaying = true, speed = 1.5f)
        val slowerPlayback = playback.copy(speed = 0.5f)

        assertEquals(13_000L, playback.positionAt(102_000))
        assertEquals(11_000L, slowerPlayback.positionAt(102_000))
        assertNull(playback.chronometerBaseAt(102_000))
        assertNull(slowerPlayback.chronometerBaseAt(102_000))
    }

    @Test
    fun progressCapsAtKnownEndAndStaysHiddenWithoutDuration() {
        val playback = WidgetPlaybackSnapshot(60_000, 59_500, 100_000, isPlaying = true)
        val unknownDuration = playback.copy(durationMs = 0)

        assertEquals(60_000L, playback.positionAt(102_000))
        assertEquals(10_000, playback.progressLevelAt(102_000))
        assertNull(playback.chronometerBaseAt(102_000))
        assertEquals(0, unknownDuration.progressLevelAt(102_000))
        assertNull(unknownDuration.chronometerBaseAt(102_000))
    }
}
