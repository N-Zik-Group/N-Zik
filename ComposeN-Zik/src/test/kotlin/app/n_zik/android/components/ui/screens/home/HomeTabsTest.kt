package app.n_zik.android.components.ui.screens.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeTabsTest {

    private val defaultOrder = listOf("quickpicks", "songs", "artists", "albums", "playlists")

    @Test
    fun `all tabs enabled keeps every tab in order`() {
        assertEquals(
            listOf("quickpicks", "songs", "artists", "albums", "playlists"),
            activeHomeTabIds(defaultOrder, true, true, true, true, true)
        )
    }

    @Test
    fun `disabled tabs are filtered out keeping the remaining order`() {
        assertEquals(
            listOf("quickpicks", "albums"),
            activeHomeTabIds(defaultOrder, true, false, false, true, false)
        )
    }

    @Test
    fun `a single remaining tab is kept`() {
        assertEquals(
            listOf("songs"),
            activeHomeTabIds(defaultOrder, false, true, false, false, false)
        )
    }

    @Test
    fun `unknown tab ids are dropped`() {
        assertEquals(
            listOf("quickpicks", "songs"),
            activeHomeTabIds(listOf("quickpicks", "bogus", "songs"), true, true, false, false, false)
        )
    }

    @Test
    fun `all disabled tabs fall back to a single quickpicks tab`() {
        assertEquals(
            listOf("quickpicks"),
            activeHomeTabIds(defaultOrder, false, false, false, false, false)
        )
    }

    @Test
    fun `a custom order is preserved among the enabled tabs`() {
        assertEquals(
            listOf("playlists", "quickpicks"),
            activeHomeTabIds(listOf("playlists", "quickpicks", "songs"), true, false, false, false, true)
        )
    }
}
