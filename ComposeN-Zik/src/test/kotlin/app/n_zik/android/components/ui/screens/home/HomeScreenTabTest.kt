package app.n_zik.android.components.ui.screens.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Full offline mode home-tab behavior (spec-full-offline-mode): the quickpicks tab
 * leaves the tab list, the never-empty fallback becomes the first local tab, and a
 * live mode-on flip force-switches a tab index that pointed at quickpicks.
 */
class HomeScreenTabTest {

    private val defaultOrder = listOf("quickpicks", "songs", "artists", "albums", "playlists")

    @Test
    fun `offline mode drops the quickpicks tab keeping the rest in order`() {
        assertEquals(
            listOf("songs", "artists", "albums", "playlists"),
            activeHomeTabIds(defaultOrder, true, true, true, true, true, true)
        )
    }

    @Test
    fun `offline mode only filtering quickpicks is identical to a quickpicks-disabled online setup`() {
        assertEquals(
            activeHomeTabIds(defaultOrder, false, true, true, true, true, false),
            activeHomeTabIds(defaultOrder, true, true, true, true, true, true)
        )
    }

    @Test
    fun `offline mode with only quickpicks enabled falls back to songs`() {
        assertEquals(
            listOf("songs"),
            activeHomeTabIds(defaultOrder, true, false, false, false, false, true)
        )
    }

    @Test
    fun `offline mode with every tab disabled still falls back to songs`() {
        assertEquals(
            listOf("songs"),
            activeHomeTabIds(defaultOrder, false, false, false, false, false, true)
        )
    }

    @Test
    fun `online mode with every tab disabled still falls back to quickpicks`() {
        assertEquals(
            listOf("quickpicks"),
            activeHomeTabIds(defaultOrder, true, false, false, false, false, false)
        )
    }

    private val defaultActive = listOf("songs", "artists", "albums", "playlists")

    @Test
    fun `force switch preserves the tab identity when the list shrinks on mode on`() {
        // Default order, user on "songs" (index 1): quickpicks leaves, songs lands at 0
        assertEquals(0, forceSwitchHomeTabIndex(defaultOrder, defaultActive, 1))
        // User on quickpicks at index 1 of a custom order: the removed tab maps to 0
        val order = listOf("songs", "quickpicks")
        assertEquals(0, forceSwitchHomeTabIndex(order, listOf("songs"), 1))
        // Quickpicks before the active tab: the active tab keeps its identity at its new position
        val order2 = listOf("quickpicks", "songs", "albums")
        assertEquals(1, forceSwitchHomeTabIndex(order2, listOf("songs", "albums"), 2))
    }

    @Test
    fun `force switch preserves the tab identity when quickpicks re-enters on mode off`() {
        // Default order, user on "songs" (index 0 of the offline list): quickpicks
        // re-enters at 0, songs shifts back to 1
        assertEquals(1, forceSwitchHomeTabIndex(defaultActive, defaultOrder, 0))
        // User on the last tab: it follows the shift
        assertEquals(4, forceSwitchHomeTabIndex(defaultActive, defaultOrder, 3))
    }

    @Test
    fun `force switch returns null when no index change is needed`() {
        // Quickpicks at index 0 on mode-on: target 0 already is the index
        assertNull(forceSwitchHomeTabIndex(defaultOrder, defaultActive, 0))
        // Quickpicks ordered after the active tab: no position shift on either flip
        val order = listOf("songs", "quickpicks")
        assertNull(forceSwitchHomeTabIndex(order, listOf("songs"), 0))
        assertNull(forceSwitchHomeTabIndex(listOf("songs"), order, 0))
        // Stale index out of range: no pre-flip tab id to preserve
        assertNull(forceSwitchHomeTabIndex(defaultOrder, defaultActive, 99))
    }
}
