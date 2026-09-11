package app.n_zik.android.components.ui.screens.home.quickpicks

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuickPicksLoadGuardTest {

    @Test
    fun `skip load when loaded flag true and home page data present`() {
        assertTrue(shouldSkipQuickPicksLoad(loadedData = true, homePagePresent = true))
    }

    @Test
    fun `reload when loaded flag true but home page data missing (stuck state)`() {
        assertFalse(shouldSkipQuickPicksLoad(loadedData = true, homePagePresent = false))
    }

    @Test
    fun `reload when loaded flag false even if home page data present`() {
        assertFalse(shouldSkipQuickPicksLoad(loadedData = false, homePagePresent = true))
    }

    @Test
    fun `reload when loaded flag false and home page data missing`() {
        assertFalse(shouldSkipQuickPicksLoad(loadedData = false, homePagePresent = false))
    }
}
