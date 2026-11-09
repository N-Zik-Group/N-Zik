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
    fun `partial and non-finite numbers are rejected`() {
        assertNull(parseSliderInput("5,5 dB", Locale.FRANCE))
        assertNull(parseSliderInput("NaN", Locale.US))
    }
}
