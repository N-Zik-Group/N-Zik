package app.n_zik.android.legacyoffmain.player

import app.kreate.android.themed.rimusic.screen.player.timeline.timeRemainingOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Bug 2026-09-26 ("valeurs infinis au chargement", Listen Together host track changes):
 * while the stream is loading the player reports an unknown duration — C.DURATION_UNSET
 * (Long.MIN_VALUE) was observed on device, producing a `2562047788015:…` flash in the
 * remaining-time label because `duration - position` overflows signed 64-bit.
 *
 * [timeRemainingOf] is the pure core of that label's computation, extracted from
 * `DurationIndicator.kt` (legacy). It is tested here in a plain JVM unit test; `internal`
 * visibility is module-scoped in Kotlin, so it resolves without sharing the legacy package
 * (same convention as `SeekBarWavedTest`).
 */
class DurationIndicatorTimeRemainingOffMainTest {

    @Test
    fun `unknown duration sentinel MIN_VALUE does not overflow - returns unknown`() {
        // The exact on-device values from the bug: position 2:15 while the LT track buffers.
        val remaining = timeRemainingOf(duration = Long.MIN_VALUE, position = 135_000L)
        assertEquals(-1L, remaining)
        // Regression property: the legacy expression `duration - position` overflows to a
        // huge positive here — the guard must never surface that.
        val legacyOverflow = (Long.MIN_VALUE - 135_000L).coerceAtLeast(0L)
        assertTrue(
            legacyOverflow > 1_000_000_000_000L,
            "sanity: the unguarded expression must overflow (otherwise the guard is pointless)",
        )
        assertTrue(remaining <= 0, "unknown duration must map to the unknown sentinel, not a garbage value")
    }

    @Test
    fun `TIME_UNSET and zero durations map to unknown`() {
        assertEquals(-1L, timeRemainingOf(duration = -1L, position = 0L))
        assertEquals(-1L, timeRemainingOf(duration = -1L, position = 45_000L))
        assertEquals(-1L, timeRemainingOf(duration = 0L, position = 0L))
        assertEquals(-1L, timeRemainingOf(duration = 0L, position = 45_000L))
    }

    @Test
    fun `known duration returns the clamped remaining time`() {
        assertEquals(117_000L, timeRemainingOf(duration = 252_000L, position = 135_000L))
        assertEquals(252_000L, timeRemainingOf(duration = 252_000L, position = 0L))
    }

    @Test
    fun `position past the duration clamps to zero instead of negative`() {
        // Late seeks / reporting jitter can put the position beyond the (known) duration.
        assertEquals(0L, timeRemainingOf(duration = 252_000L, position = 253_000L))
    }
}
