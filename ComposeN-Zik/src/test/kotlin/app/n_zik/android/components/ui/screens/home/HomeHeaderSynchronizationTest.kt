package app.n_zik.android.components.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHeaderSynchronizationTest {
    @Test fun landscapeHomeToolbarSharesTheAppHeaderScroll() {
        assertTrue(homeHeaderSharesAppScroll(horizontalNavigation = true, enabled = true))
        assertFalse(homeHeaderSharesAppScroll(horizontalNavigation = false, enabled = true))
    }

    @Test fun toolbarFadesOnTheSameCollapseFraction() {
        assertEquals(1f, homeHeaderVisibleFraction(180, 0f), 0f)
        assertEquals(0.5f, homeHeaderVisibleFraction(180, -90f), 0f)
        assertEquals(0f, homeHeaderVisibleFraction(180, -180f), 0f)
    }

    @Test fun homeToolbarUsesTheSameCollapseFractionAsAppHeader() {
        assertEquals(0f, homeHeaderOffsetForAppHeader(0f, 64f, 180), 0f)
        assertEquals(-90f, homeHeaderOffsetForAppHeader(-32f, 64f, 180), 0f)
        assertEquals(-180f, homeHeaderOffsetForAppHeader(-64f, 64f, 180), 0f)
        assertEquals(-180f, homeHeaderOffsetForAppHeader(-100f, 64f, 180), 0f)
        assertEquals(0f, homeHeaderOffsetForAppHeader(8f, 64f, 180), 0f)
    }
}
