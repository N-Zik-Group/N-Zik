package app.n_zik.android.components.settings.common

import java.text.NumberFormat
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LocalizedSliderInputTest {
    @Test
    fun `manual input accepts the localized value shown by the slider`() {
        val arabic = Locale.forLanguageTag("ar-EG")
        val formatted = NumberFormat.getNumberInstance(arabic).format(-5.5)
        assertEquals(-5.5f, parseSliderInput(formatted, arabic))
        assertEquals(5.5f, parseSliderInput("5,5", Locale.FRANCE))
        assertEquals(5.5f, parseSliderInput("5.5", Locale.FRANCE))
    }

    @Test
    fun `manual settings convert decibels percentages and depth into slider units`() {
        assertEquals(0.5f, parseSliderSettingValue("7,5", Locale.FRANCE, 15f, 0f..1f))
        assertEquals(0.5f, parseSliderSettingValue("50", Locale.US, 100f, 0f..1f))
        assertEquals(1.5f, parseSliderSettingValue("75", Locale.US, 50f, 0f..2f))
        assertEquals(0.5f, parseSliderSettingValue("24", Locale.US, 48f, 0f..1f))
        assertEquals(1f, parseSliderSettingValue("101", Locale.US, 100f, 0f..1f))
        assertNull(parseSliderSettingValue("50x", Locale.US, 100f, 0f..1f))
    }

    @Test
    fun `partial and non-finite numbers are rejected`() {
        assertNull(parseSliderInput("5,5 dB", Locale.FRANCE))
        assertNull(parseSliderInput("NaN", Locale.US))
    }
}
