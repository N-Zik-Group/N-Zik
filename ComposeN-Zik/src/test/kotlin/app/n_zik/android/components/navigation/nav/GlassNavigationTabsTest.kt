package app.n_zik.android.components.navigation.nav

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlassNavigationTabsTest {
    @Test
    fun `mini player clearance accommodates the expanded glass selector`() {
        val pressedHeight = (GlassNavigationBarHeight.value - 8f) * (78f / 56f)
        val topOverhang = (pressedHeight - GlassNavigationBarHeight.value) / 2f
        assertTrue(GlassNavigationPlayerGap.value >= topOverhang + 4f)
        val overshootHeight = (GlassNavigationBarHeight.value - 8f) * 1.45f
        assertTrue(GlassNavigationPlayerGap.value >= (overshootHeight - GlassNavigationBarHeight.value) / 2f + 4f)
    }

    @Test
    fun `dragging moves across actual tab extent and clamps at ends`() {
        assertEquals(1.5f, glassDraggedTab(1f, 40f, 320f, 4, false))
        assertEquals(3f, glassDraggedTab(1f, 1000f, 320f, 4, false))
        assertEquals(0f, glassDraggedTab(1f, -1000f, 320f, 4, false))
    }

    @Test
    fun `rtl reverses horizontal travel without reversing vertical navigation`() {
        assertEquals(0.5f, glassDraggedTab(1f, 40f, 320f, 4, true))
        assertEquals(1.5f, glassDraggedTab(1f, 40f, 320f, 4, false))
    }

    @Test
    fun `release selects nearest tab while incomplete measurement cannot create invalid positions`() {
        assertEquals(1, glassSettledTab(1.49f, 4))
        assertEquals(2, glassSettledTab(1.5f, 4))
        assertEquals(3, glassSettledTab(8f, 4))
        assertEquals(0, glassSettledTab(Float.NaN, 4))
        assertEquals(0, glassSettledTab(2f, 0))
        assertEquals(1f, glassDraggedTab(1f, 40f, 0f, 4, false))
        assertEquals(1f, glassDraggedTab(1f, Float.NaN, 320f, 4, false))
    }
    @Test
    fun `pressed selector stretches along travel and remains bounded at extreme velocity`() {
        val pressed = 78f / 56f
        assertEquals(pressed, glassTabStretch(pressed, 0f, true))
        assertEquals(pressed, glassTabStretch(pressed, 0f, false))
        assertEquals(pressed / 0.8f, glassTabStretch(pressed, 10000f, true))
        assertEquals(pressed * 0.8f, glassTabStretch(pressed, 10000f, false))
        assertEquals(pressed / 1.2f, glassTabStretch(pressed, -10000f, true))
        assertEquals(pressed * 1.2f, glassTabStretch(pressed, -10000f, false))
    }
    @Test
    fun `short rail enables scrolling before utility buttons lose their available height`() {
        assertFalse(glassRailNeedsScroll(300.dp, 3, false, true, false))
        assertTrue(glassRailNeedsScroll(299.dp, 3, false, true, false))
        assertFalse(glassRailNeedsScroll(350.dp, 3, false, true, true))
        assertTrue(glassRailNeedsScroll(349.dp, 3, false, true, true))
        assertFalse(glassRailNeedsScroll(398.dp, 3, true, true, true))
        assertTrue(glassRailNeedsScroll(397.dp, 3, true, true, true))
        assertFalse(glassRailNeedsScroll(200.dp, 3, false, false, false))
        assertTrue(glassRailNeedsScroll(199.dp, 3, false, false, false))
    }

    @Test
    fun `side header cells are included before deciding whether the rail fits`() {
        // Three tabs (200), logo (48), search and profile (80).
        assertFalse(glassRailNeedsScroll(328.dp, 3, false, false, false, 128.dp))
        assertTrue(glassRailNeedsScroll(327.dp, 3, false, false, false, 128.dp))
        // A secondary page adds back (48); ViMusic keeps statistics and settings (100).
        assertFalse(glassRailNeedsScroll(476.dp, 3, false, true, false, 176.dp))
        assertTrue(glassRailNeedsScroll(475.dp, 3, false, true, false, 176.dp))
        // Search pages remove their redundant search shortcut (40).
        assertFalse(glassRailNeedsScroll(436.dp, 3, false, true, false, 136.dp))
        assertTrue(glassRailNeedsScroll(435.dp, 3, false, true, false, 136.dp))
        // Native header cells use the full 48dp touch target each.
        assertFalse(glassRailNeedsScroll(492.dp, 3, false, true, false, 192.dp))
        assertTrue(glassRailNeedsScroll(491.dp, 3, false, true, false, 192.dp))
    }
}
