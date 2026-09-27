package app.n_zik.android.components.ui.screens.rewind

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Contract of the monthly reminder's scheduling math (spec GH-275, re-review:
 * schedule delay untested): [RewindReminderWorker.msUntilNextMonthStart] must land
 * on the 1st of the next month at 00:00 in the local zone (DST and year rollover
 * included), and [RewindReminderWorker.applyJitter] must clamp the final delay to
 * non-negative so an install never schedules in the past.
 */
class RewindReminderSchedulingTest {

    private val zone = ZoneId.of("Europe/Paris")

    @Test
    fun midMonthNowDelaysUntilTheNextFirst() {
        val now = ZonedDateTime.of(2026, 3, 15, 10, 0, 0, 0, zone)
        val target = ZonedDateTime.of(2026, 4, 1, 0, 0, 0, 0, zone)
        val expectedMs = (target.toEpochSecond() - now.toEpochSecond()) * 1000L
        assertEquals(expectedMs, RewindReminderWorker.msUntilNextMonthStart(now))
    }

    @Test
    fun nowOnTheFirstDelaysExactlyOneMonth() {
        val now = ZonedDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneId.of("UTC"))
        val target = ZonedDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneId.of("UTC"))
        val expectedMs = (target.toEpochSecond() - now.toEpochSecond()) * 1000L
        assertEquals(expectedMs, RewindReminderWorker.msUntilNextMonthStart(now))
    }

    @Test
    fun decemberNowRollsIntoJanuary() {
        val now = ZonedDateTime.of(2025, 12, 15, 22, 0, 0, 0, ZoneId.of("UTC"))
        val target = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"))
        val expectedMs = (target.toEpochSecond() - now.toEpochSecond()) * 1000L
        assertEquals(expectedMs, RewindReminderWorker.msUntilNextMonthStart(now))
    }

    @Test
    fun lateJanuaryLandsOnFebruaryOfALeapYear() {
        val now = ZonedDateTime.of(2028, 1, 31, 23, 0, 0, 0, ZoneId.of("UTC"))
        val target = ZonedDateTime.of(2028, 2, 1, 0, 0, 0, 0, ZoneId.of("UTC"))
        assertEquals(3_600_000L, RewindReminderWorker.msUntilNextMonthStart(now)) // exactly one hour
        assertEquals(
            (target.toEpochSecond() - now.toEpochSecond()) * 1000L,
            RewindReminderWorker.msUntilNextMonthStart(now)
        )
    }

    @Test
    fun jitterIsAppliedAndClampedToZero() {
        assertEquals(150L, RewindReminderWorker.applyJitter(100L, 50L))
        assertEquals("a jitter larger than the delay must clamp to zero", 0L, RewindReminderWorker.applyJitter(100L, -200L))
        assertEquals(0L, RewindReminderWorker.applyJitter(0L, -10 * 60 * 1000L))
        assertEquals(10 * 60 * 1000L, RewindReminderWorker.applyJitter(0L, 10 * 60 * 1000L))
    }
}
