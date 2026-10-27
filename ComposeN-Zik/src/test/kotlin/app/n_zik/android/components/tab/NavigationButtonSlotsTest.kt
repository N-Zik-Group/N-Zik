package app.n_zik.android.components.tab

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NavigationButtonSlotsTest {
    @Test
    fun `repeated registration replaces content while preserving navigation index order`() {
        val slots = NavigationButtonSlots<String>()
        slots[4] = "Playlists"
        slots[1] = "Songs"
        slots[0] = "Quick picks"
        slots[1] = "Updated songs"
        assertEquals(3, slots.size)
        assertEquals(listOf("Quick picks", "Updated songs", "Playlists"), slots.toList())
    }

    @Test
    fun `rebuilding removes hidden tabs and orders remaining navigation indices`() {
        val slots = NavigationButtonSlots<String>()
        slots[0] = "Quick picks"
        slots[1] = "Songs"
        slots[4] = "Playlists"
        slots.clear()
        slots[4] = "Playlists"
        slots[1] = "Songs"
        assertEquals(listOf("Songs", "Playlists"), slots.toList())
    }
}
