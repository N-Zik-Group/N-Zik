package app.n_zik.android.components.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerProgressTest {
    private val playing = MiniPlayerProgressSample(positionMs = 4_000L, durationMs = 10_000L, isPlaying = true)

    @Test
    fun `consecutive forward playback samples interpolate`() {
        val next = playing.copy(positionMs = 4_100L)

        assertTrue(next.follows(playing))
        assertEquals(.41f, next.fraction, 0f)
        assertFalse(playing.follows(next))
    }

    @Test
    fun `forward seek and track change snap even when duration and direction match`() {
        val seek = playing.copy(positionMs = 4_100L, discontinuity = 1)
        val nextTrack = seek.copy(positionMs = 5_000L, discontinuity = 2)

        assertFalse(seek.follows(playing))
        assertFalse(nextTrack.follows(seek))
        assertTrue(nextTrack.copy(positionMs = 5_100L).follows(nextTrack))
    }

    @Test
    fun `pausing or resuming snaps to the actual position`() {
        val paused = playing.copy(positionMs = 4_040L, isPlaying = false)
        val resumed = paused.copy(positionMs = 4_050L, isPlaying = true)

        assertFalse(paused.follows(playing))
        assertFalse(resumed.follows(paused))
        assertEquals(.404f, paused.fraction, .00001f)
    }

    @Test
    fun `unknown or changing duration snaps and fill stays between zero and one`() {
        val unknown = playing.copy(durationMs = C.TIME_UNSET)
        val resolved = unknown.copy(durationMs = 11_000L)

        assertFalse(unknown.follows(playing))
        assertFalse(unknown.copy(positionMs = 4_100L).follows(unknown))
        assertFalse(resolved.follows(unknown))
        assertFalse(resolved.follows(playing))
        assertEquals(0f, unknown.fraction, 0f)
        assertEquals(0f, playing.copy(durationMs = 0L).fraction, 0f)
        assertEquals(0f, playing.copy(positionMs = -10L).fraction, 0f)
        assertEquals(1f, playing.copy(positionMs = 15_000L).fraction, 0f)
    }
}
