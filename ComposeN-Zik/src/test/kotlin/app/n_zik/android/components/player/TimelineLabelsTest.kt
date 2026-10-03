package app.n_zik.android.components.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The remaining-time label ticks on the same whole-second boundary as the elapsed label.
 */
class TimelineLabelsTest {

    @Test
    fun `remaining ticks on the same position as the elapsed label`() {
        // 247 441 ms track: the old `duration - position` changed second at 1 441, 2 441…
        // (441 ms after the elapsed label). Both now change exactly at x 000.
        assertEquals(247_000L, displayedTimeRemainingOf(247_441, 999))
        assertEquals(246_000L, displayedTimeRemainingOf(247_441, 1_000))
        assertEquals(246_000L, displayedTimeRemainingOf(247_441, 1_441))
        assertEquals(245_000L, displayedTimeRemainingOf(247_441, 2_000))
    }

    @Test
    fun `elapsed plus remaining equals the displayed duration`() {
        val duration = 247_441L
        for (position in listOf(0L, 30_500L, 123_999L, 247_000L)) {
            val elapsedSec = position / 1000
            val remainingSec = displayedTimeRemainingOf(duration, position) / 1000
            assertEquals(duration / 1000, elapsedSec + remainingSec, "at $position ms")
        }
    }

    @Test
    fun `unknown duration and past-the-end positions`() {
        assertEquals(-1L, displayedTimeRemainingOf(Long.MIN_VALUE, 30_000))
        assertEquals(-1L, displayedTimeRemainingOf(0, 30_000))
        assertEquals(0L, displayedTimeRemainingOf(247_441, 248_000))
    }
}
