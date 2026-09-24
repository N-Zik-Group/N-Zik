package app.n_zik.android.components.ui.screens.rewind

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the Rewind home's completion gates ([isMonthComplete], [isYearComplete]):
 * a month is complete from the first millisecond of the following month and a year from
 * the first millisecond of the following year — the same instant their completion
 * notification would fire, so deck access never depends on whether that notification was
 * actually delivered. In-progress and future periods stay incomplete until that instant.
 */
class RewindPeriodCompletionTest {

    private fun millisOf(day: LocalDate, time: LocalTime = LocalTime.MIN): Long =
        day.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun monthCompletesAtTheFirstMillisecondOfTheNextMonth() {
        val octoberStarts = millisOf(LocalDate.of(2026, 10, 1))
        assertFalse(isMonthComplete(2026, 9, octoberStarts - 1))
        assertTrue(isMonthComplete(2026, 9, octoberStarts))
    }

    @Test
    fun decemberCompletesAtTheStartOfJanuary() {
        val januaryStarts = millisOf(LocalDate.of(2027, 1, 1))
        assertFalse(isMonthComplete(2026, 12, januaryStarts - 1))
        assertTrue(isMonthComplete(2026, 12, januaryStarts))
    }

    @Test
    fun inProgressAndFutureMonthsStayIncomplete() {
        val now = millisOf(LocalDate.of(2026, 9, 15), LocalTime.NOON)
        assertFalse(isMonthComplete(2026, 9, now))
        assertFalse(isMonthComplete(2026, 10, now))
        assertFalse(isMonthComplete(2026, 12, now))
        assertFalse(isMonthComplete(2027, 1, now))
    }

    @Test
    fun finishedMonthsAreComplete() {
        val now = millisOf(LocalDate.of(2026, 9, 15), LocalTime.NOON)
        assertTrue(isMonthComplete(2026, 8, now))
        assertTrue(isMonthComplete(2026, 1, now))
        assertTrue(isMonthComplete(2025, 12, now))
    }

    @Test
    fun yearCompletesAtTheFirstMillisecondOfTheNextYear() {
        val year2027Starts = millisOf(LocalDate.of(2027, 1, 1))
        assertFalse(isYearComplete(2026, year2027Starts - 1))
        assertTrue(isYearComplete(2026, year2027Starts))
        assertTrue(isYearComplete(2025, year2027Starts))
    }
}
