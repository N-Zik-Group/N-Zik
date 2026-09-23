package utils

import app.it.fast4x.rimusic.models.Event
import app.n_zik.android.components.ui.screens.rewind.calendarDayStats
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract for the monthly deck's day-by-day chart data: [calendarDayStats] returns one entry
 * per calendar day of the month (days without plays stay at zero), in day order, with the
 * minutes and plays of that day.
 */
class RewindCalendarDayStatsTest {

    private val zone = ZoneId.systemDefault()

    private fun event(day: LocalDate, playTimeMs: Long): Event =
        Event(
            songId = "s",
            timestamp = day.atStartOfDay().atZone(zone).toInstant().toEpochMilli(),
            playTime = playTimeMs
        )

    @Test
    fun returnsOneEntryPerCalendarDayInOrder() {
        val stats = calendarDayStats(2026, 1, emptyList(), zone)
        assertEquals(31, stats.size)
        assertEquals(1, stats.first().day)
        assertEquals(31, stats.last().day)
        assertEquals((1..stats.size).toList(), stats.map { it.day })
    }

    @Test
    fun zerosDaysWithoutPlaysAndSumsMinutesPerDay() {
        val events = listOf(event(LocalDate.of(2026, 1, 15), 300_000L))
        val stats = calendarDayStats(2026, 1, events, zone)
        val day15 = stats.first { it.day == 15 }
        assertEquals(5L, day15.minutes)
        assertEquals(1, day15.plays)
        assertEquals(0L, stats.first { it.day == 14 }.minutes)
        assertEquals(0, stats.first { it.day == 16 }.plays)
    }

    @Test
    fun countsEveryPlayOfTheDay() {
        val events = listOf(
            event(LocalDate.of(2026, 2, 3), 120_000L),
            event(LocalDate.of(2026, 2, 3), 120_000L),
            event(LocalDate.of(2026, 2, 4), 60_000L)
        )
        val stats = calendarDayStats(2026, 2, events, zone)
        assertEquals(28, stats.size)
        val day3 = stats.first { it.day == 3 }
        assertEquals(4L, day3.minutes)
        assertEquals(2, day3.plays)
    }

    @Test
    fun leapYearFebruaryHasTwentyNineDays() {
        assertEquals(29, calendarDayStats(2024, 2, emptyList(), zone).size)
    }
}
