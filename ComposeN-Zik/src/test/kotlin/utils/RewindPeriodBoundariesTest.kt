package utils

import app.n_zik.android.components.ui.screens.rewind.rewindDaysInPeriod
import app.n_zik.android.components.ui.screens.rewind.rewindPeriodBoundaries
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract for the rewind period boundaries ([rewindPeriodBoundaries]) and the calendar days
 * in a period ([rewindDaysInPeriod]): the annual deck spans the whole year, the monthly deck
 * spans its single month, and adjacent months are contiguous (the next month starts exactly
 * one millisecond after the previous one ends).
 */
class RewindPeriodBoundariesTest {

    private fun millisOf(day: LocalDate, time: LocalTime = LocalTime.MAX): Long =
        day.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun yearBoundariesSpanTheWholeCalendarYear() {
        val (start, end) = rewindPeriodBoundaries(2026, null)
        assertEquals(millisOf(LocalDate.of(2026, 1, 1), LocalTime.MIN), start)
        assertEquals(millisOf(LocalDate.of(2026, 12, 31)), end)
    }

    @Test
    fun monthBoundariesAreInclusiveAndContiguous() {
        val january = rewindPeriodBoundaries(2026, 1)
        val february = rewindPeriodBoundaries(2026, 2)
        assertEquals(millisOf(LocalDate.of(2026, 1, 1), LocalTime.MIN), january.first)
        assertEquals(millisOf(LocalDate.of(2026, 1, 31)), january.second)
        // February starts the millisecond right after January ends: no play falls between
        assertEquals(january.second + 1L, february.first)
    }

    @Test
    fun decemberEndsItsOwnYear() {
        val december = rewindPeriodBoundaries(2025, 12)
        assertEquals(millisOf(LocalDate.of(2025, 12, 1), LocalTime.MIN), december.first)
        assertEquals(millisOf(LocalDate.of(2025, 12, 31)), december.second)
        val nextJanuary = rewindPeriodBoundaries(2026, 1)
        assertEquals(december.second + 1L, nextJanuary.first)
    }

    @Test
    fun daysInPeriodMatchesTheCalendar() {
        assertEquals(365, rewindDaysInPeriod(2026, null))
        assertEquals(366, rewindDaysInPeriod(2024, null))
        assertEquals(29, rewindDaysInPeriod(2024, 2))
        assertEquals(28, rewindDaysInPeriod(2026, 2))
        assertEquals(31, rewindDaysInPeriod(2026, 12))
        assertEquals(30, rewindDaysInPeriod(2026, 4))
    }
}
