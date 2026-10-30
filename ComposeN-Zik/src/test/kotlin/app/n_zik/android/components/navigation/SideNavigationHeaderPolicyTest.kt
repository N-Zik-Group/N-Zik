package app.n_zik.android.components.navigation

import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.n_zik.android.components.player.miniPlayerTopInset
import app.n_zik.android.components.player.miniPlayerTopPaddingPx
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SideNavigationHeaderPolicyTest {
    @Test
    fun `only target entry draws navigation during push pop and repeated route transitions`() {
        assertTrue(pageOwnsNavigationOverlay("home-1", "home-1"))
        assertFalse(pageOwnsNavigationOverlay("home-1", "search-2"))
        assertTrue(pageOwnsNavigationOverlay("search-2", "search-2"))
        assertFalse(pageOwnsNavigationOverlay("search-2", "home-1"))
        assertTrue(pageOwnsNavigationOverlay("home-1", "home-1"))
        assertFalse(pageOwnsNavigationOverlay("search-2", "search-3"))
        assertFalse(pageOwnsNavigationOverlay(null, null))
        assertFalse(pageOwnsNavigationOverlay("home-1", null))
    }

    @Test
    fun `side Skeleton pages own status clearance but detail viewports retain it`() {
        assertTrue(sidePageOwnsTopInset("home"))
        assertTrue(sidePageOwnsTopInset("search?text={text}"))
        assertTrue(sidePageOwnsTopInset("searchResults/{query}"))
        assertTrue(sidePageOwnsTopInset("playlist/{id}?params={params}"))
        assertTrue(sidePageOwnsTopInset("settings?tab={tab}&focus={focus}"))
        assertFalse(sidePageOwnsTopInset("album/{id}"))
        assertFalse(sidePageOwnsTopInset(null))
    }
    @Test
    fun `configured side rails always own header controls regardless of material`() {
        for (position in NavigationBarPosition.entries) {
            val side = position == NavigationBarPosition.Left || position == NavigationBarPosition.Right
            assertEquals(side, sideNavigationOwnsHeader(position))
            assertEquals(side, needsStandaloneSideHeader(false, position))
            assertFalse(needsStandaloneSideHeader(true, position))
        }
    }

    @Test
    fun `routes without a tab rail retain the side header without duplicating existing rails`() {
        for (position in listOf(NavigationBarPosition.Left, NavigationBarPosition.Right)) {
            assertTrue(sideNavigationOwnsHeader(position))
            assertTrue(needsStandaloneSideHeader(false, position))
            assertFalse(needsStandaloneSideHeader(true, position))
        }
    }

    @Test
    fun `rail header leaves only status clearance for overlay at every scroll offset`() {
        for (offset in listOf(0f, -32f, -64f)) {
            assertEquals(24f, appHeaderOverlayTopPx(0f, 24f, offset))
        }
    }

    @Test
    fun `top mini below side header stays below status bar during scrolling`() {
        val inset = miniPlayerTopInset(24.dp, false, false, headerHeight = 0.dp)
        assertEquals(32.dp, inset)
        for (offset in listOf(0f, -32f, -64f)) {
            assertEquals(32f, miniPlayerTopPaddingPx(inset.value, offset, 0f, 65f))
        }
    }
}
