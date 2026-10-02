package app.n_zik.android.components

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomBottomSheetBackgroundTest {

    @Test
    fun `the card base background is drawn when fully expanded`() {
        // Regression guard for issue #855: the sheet's opaque base must persist
        // at progress 1, otherwise the player background's transparent regions
        // expose the screen behind the sheet.
        assertTrue(shouldDrawCardBackground(1f))
    }

    @Test
    fun `the card base background is drawn mid deploy`() {
        assertTrue(shouldDrawCardBackground(0.5f))
    }

    @Test
    fun `the card base background is skipped while collapsed`() {
        assertFalse(shouldDrawCardBackground(0f))
    }

    @Test
    fun `progress outside 0 to 1 is clamped`() {
        assertFalse(shouldDrawCardBackground(-0.4f))
        assertTrue(shouldDrawCardBackground(1.3f))
    }

    @Test
    fun `the card base background is drawn at the exact removed upper boundary`() {
        // The old guard stopped drawing just below 0.99f — the gap that
        // exposed the screen behind the sheet (issue #855). Pin it.
        assertTrue(shouldDrawCardBackground(0.99f))
    }

    @Test
    fun `the skip band starts exactly at the collapsed epsilon`() {
        assertFalse(shouldDrawCardBackground(CARD_BG_COLLAPSED_EPSILON))
        assertTrue(shouldDrawCardBackground(CARD_BG_COLLAPSED_EPSILON * 2f))
    }
}
