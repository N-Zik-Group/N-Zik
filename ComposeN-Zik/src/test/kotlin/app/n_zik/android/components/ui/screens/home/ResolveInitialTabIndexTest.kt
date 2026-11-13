package app.n_zik.android.components.ui.screens.home

import app.n_zik.android.enums.HomeScreenTabs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResolveInitialTabIndexTest {

    private val allTabs = listOf("quickpicks", "songs", "artists", "albums", "playlists")

    @Test
    fun `search shortcut falls back to the preferred tab instead of clamping`() {
        assertEquals(0, resolveInitialTabIndex(OPEN_SEARCH_SHORTCUT, HomeScreenTabs.Default, allTabs))
        assertEquals(allTabs.indexOf("albums"), resolveInitialTabIndex(OPEN_SEARCH_SHORTCUT, HomeScreenTabs.Albums, allTabs))
    }

    @Test
    fun `no shortcut opens the preferred tab or quick picks`() {
        assertEquals(0, resolveInitialTabIndex(-1, HomeScreenTabs.Default, allTabs))
        assertEquals(allTabs.indexOf("songs"), resolveInitialTabIndex(-1, HomeScreenTabs.Songs, allTabs))
    }

    @Test
    fun `tab shortcut opens the matching tab`() {
        assertEquals(allTabs.indexOf("artists"), resolveInitialTabIndex(HomeScreenTabs.Artists.index, HomeScreenTabs.Default, allTabs))
        assertEquals(allTabs.indexOf("playlists"), resolveInitialTabIndex(HomeScreenTabs.Playlists.index, HomeScreenTabs.Default, allTabs))
    }

    @Test
    fun `tab shortcut falls back to the first tab when the tab is disabled`() {
        val withoutAlbums = listOf("quickpicks", "songs", "artists", "playlists")
        assertEquals(0, resolveInitialTabIndex(HomeScreenTabs.Albums.index, HomeScreenTabs.Albums, withoutAlbums))
    }

    @Test
    fun `unknown tab indexes fall back to quick picks`() {
        assertEquals(allTabs.indexOf("quickpicks"), resolveInitialTabIndex(99, HomeScreenTabs.Default, allTabs))
    }

    @Test
    fun `search shortcut falls back to the first tab when the preferred tab is disabled`() {
        val withoutAlbums = listOf("quickpicks", "songs", "artists", "playlists")
        assertEquals(0, resolveInitialTabIndex(OPEN_SEARCH_SHORTCUT, HomeScreenTabs.Albums, withoutAlbums))
    }

    @Test
    fun `tab shortcut resolves against a user-reordered tab order`() {
        val reordered = listOf("songs", "quickpicks", "albums", "artists", "playlists")
        assertEquals(reordered.indexOf("albums"), resolveInitialTabIndex(HomeScreenTabs.Albums.index, HomeScreenTabs.Default, reordered))
    }

    @Test
    fun `search shortcut gate is one-shot`() {
        assertTrue(shouldOpenSearchFromShortcut(OPEN_SEARCH_SHORTCUT, false))
        assertFalse(shouldOpenSearchFromShortcut(OPEN_SEARCH_SHORTCUT, true))
        assertFalse(shouldOpenSearchFromShortcut(-1, false))
        assertFalse(shouldOpenSearchFromShortcut(HomeScreenTabs.Albums.index, false))
    }

    @Test
    fun `tab shortcuts are live-switchable, search and no-op sentinels are not`() {
        assertTrue(shouldSwitchTabFromShortcut(HomeScreenTabs.Albums.index))
        assertTrue(shouldSwitchTabFromShortcut(HomeScreenTabs.Artists.index))
        assertFalse(shouldSwitchTabFromShortcut(OPEN_SEARCH_SHORTCUT))
        assertFalse(shouldSwitchTabFromShortcut(-1))
    }
}
