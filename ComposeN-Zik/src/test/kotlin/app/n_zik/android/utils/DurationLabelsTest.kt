package app.n_zik.android.utils

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DurationLabelsTest {
    @Test
    fun `French play time uses French unit names`() {
        val sevenDays = 7 * 86_400_000L
        assertEquals("7 j", spaces(compactPlayTimeLabel(sevenDays, Locale.FRANCE)))
        assertEquals("7 j", spaces(formatAsTime(sevenDays, Locale.FRANCE)))
        assertEquals("6 h", spaces(compactPlayTimeLabel(6 * 3_600_000L, Locale.FRANCE)))
        assertEquals("5 m", spaces(compactPlayTimeLabel(5 * 60_000L, Locale.FRANCE)))
        assertFalse(compactPlayTimeLabel(sevenDays, Locale.US).contains(" j"))
    }

    @Test
    fun `long durations retain localized days hours and minutes without seconds`() {
        val sevenDays = 7 * 86_400_000L
        val duration = secondsLabel(3, Locale.FRANCE)
        val label = spaces(formatAsTime(sevenDays + 2 * 3_600_000 + 60_000 + 3_999, Locale.FRANCE))
        assertEquals("7 j 2 h 1 m", label)
        assertEquals("18 h 45 m", spaces(formatAsTime(18 * 3_600_000L + 45 * 60_000L + 49_000, Locale.US)))
        assertEquals(duration, formatAsTime(3_999, Locale.FRANCE))
    }

    private fun spaces(label: String): String = label.replace('\u00A0', ' ').replace('\u202F', ' ')
}
