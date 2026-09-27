package app.n_zik.android.components.ui.screens.rewind

import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the period kicker labels ([periodMonthLabel] + [RewindPeriod.label]): gates
 * every slide's kicker and the intro tagline branch. The assertions are locale-agnostic in
 * structure — the month part must be the localized short month name, uppercased, dotted,
 * followed by " <year>" — plus a round-trip against the default locale itself, so the test
 * passes on any device locale (spec GH-275, patch "rewindPeriodLabel untested").
 */
class RewindPeriodLabelTest {

    private val previousLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun monthLabelIsUppercaseDottedShortMonthThenYear() {
        val label = periodMonthLabel(2026, 3)
        assertTrue(label.endsWith(" 2026"))
        val monthPart = label.removeSuffix(" 2026")
        assertTrue("missing dot: $monthPart", monthPart.endsWith('.'))
        assertTrue("not uppercase: $monthPart", monthPart == monthPart.uppercase(Locale.ROOT))
        // A short month name is at least 3 letters, at most 6 before the dot
        assertTrue("implausible length: $monthPart", monthPart.length in 4..7)
    }

    @Test
    fun monthLabelMatchesTheDefaultLocaleShortMonthName() {
        val raw = Month.of(3).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        val expected = (if (raw.endsWith('.')) raw.uppercase(Locale.getDefault())
            else "${raw.uppercase(Locale.getDefault())}.") + " 2026"
        assertEquals(expected, periodMonthLabel(2026, 3))
    }

    @Test
    fun englishMonthLabelIsMarDotYear() {
        // The US locale pins the exact "MAR. 2026" shape; other locales keep the same
        // structure (dotted, uppercased short month name + " <year>") as checked above
        Locale.setDefault(Locale.US)
        assertEquals("MAR. 2026", periodMonthLabel(2026, 3))
    }

    @Test
    fun periodLabelsAreStableAcrossTheThreePeriods() {
        assertEquals("2026", RewindPeriod.Year(2026).label())
        assertEquals("GLOBAL", RewindPeriod.Global.label())
        assertEquals(periodMonthLabel(2026, 3), RewindPeriod.Month(2026, 3).label())
    }
}
