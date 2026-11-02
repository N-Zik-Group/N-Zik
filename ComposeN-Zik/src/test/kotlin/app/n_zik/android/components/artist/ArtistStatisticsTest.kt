package app.n_zik.android.components.artist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArtistStatisticsTest {
    @Test
    fun `audience contains subscribers and monthly listeners when provided`() {
        assertEquals("10 subscribers • 20 monthly listeners",
            artistStatisticsText("10 subscribers", "20 monthly listeners"))
    }

    @Test
    fun `missing statistics never leave a separator or invented audience`() {
        assertEquals("20 monthly listeners", artistStatisticsText(null, "20 monthly listeners"))
        assertEquals("10 subscribers", artistStatisticsText("10 subscribers", " "))
        assertEquals("", artistStatisticsText(null, null))
        assertEquals("", artistStatisticsText(" ", ""))
    }
}
