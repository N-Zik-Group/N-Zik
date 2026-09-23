package app.n_zik.android.components.ui.screens.rewind

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract for the rewind period model ([RewindPeriod]): the annual window spans the whole
 * calendar year, the monthly window spans its single month, adjacent months are contiguous
 * (the next month starts exactly one millisecond after the previous one ends), the global
 * window spans from the epoch to now, and every period has a stable file-name token.
 */
class RewindPeriodBoundariesTest {

    private fun millisOf(day: LocalDate, time: LocalTime = LocalTime.MAX): Long =
        day.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun yearBoundariesSpanTheWholeCalendarYear() {
        val (start, end) = RewindPeriod.Year(2026).boundaries()
        assertEquals(millisOf(LocalDate.of(2026, 1, 1), LocalTime.MIN), start)
        assertEquals(millisOf(LocalDate.of(2026, 12, 31)), end)
    }

    @Test
    fun monthBoundariesAreInclusiveAndContiguous() {
        val january = RewindPeriod.Month(2026, 1).boundaries()
        val february = RewindPeriod.Month(2026, 2).boundaries()
        assertEquals(millisOf(LocalDate.of(2026, 1, 1), LocalTime.MIN), january.first)
        assertEquals(millisOf(LocalDate.of(2026, 1, 31)), january.second)
        // February starts the millisecond right after January ends: no play falls between
        assertEquals(january.second + 1L, february.first)
    }

    @Test
    fun decemberEndsItsOwnYear() {
        val december = RewindPeriod.Month(2025, 12).boundaries()
        assertEquals(millisOf(LocalDate.of(2025, 12, 1), LocalTime.MIN), december.first)
        assertEquals(millisOf(LocalDate.of(2025, 12, 31)), december.second)
        val nextJanuary = RewindPeriod.Month(2026, 1).boundaries()
        assertEquals(december.second + 1L, nextJanuary.first)
    }

    @Test
    fun daysInPeriodMatchesTheCalendar() {
        assertEquals(365, RewindPeriod.Year(2026).daysInPeriod())
        assertEquals(366, RewindPeriod.Year(2024).daysInPeriod())
        assertEquals(29, RewindPeriod.Month(2024, 2).daysInPeriod())
        assertEquals(28, RewindPeriod.Month(2026, 2).daysInPeriod())
        assertEquals(31, RewindPeriod.Month(2026, 12).daysInPeriod())
        assertEquals(30, RewindPeriod.Month(2026, 4).daysInPeriod())
    }

    @Test
    fun globalWindowSpansFromTheEpochToNowAndHasNoFixedLength() {
        val now = 1_700_000_000_000L
        assertEquals(0L to now, RewindPeriod.Global.boundaries(now))
        // An all-time window has no fixed length: the fetcher falls back to the data's
        // actual span (allTimeDaysInPeriod) when daysInPeriod is 0
        assertEquals(0, RewindPeriod.Global.daysInPeriod())
    }

    @Test
    fun fileNameTokensAreStableAndFileSafe() {
        assertEquals("2026", RewindPeriod.Year(2026).fileNameToken())
        assertEquals("2026_03", RewindPeriod.Month(2026, 3).fileNameToken())
        assertEquals("GLOBAL", RewindPeriod.Global.fileNameToken())
        listOf(
            RewindPeriod.Year(2026).fileNameToken(),
            RewindPeriod.Month(2026, 3).fileNameToken(),
            RewindPeriod.Global.fileNameToken()
        ).forEach { token ->
            token.forEach { char ->
                org.junit.Assert.assertTrue("'$char' is not file-safe in '$token'", char.isLetterOrDigit() || char == '_')
            }
        }
    }
}
