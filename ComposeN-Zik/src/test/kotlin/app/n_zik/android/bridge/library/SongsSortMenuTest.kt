package app.n_zik.android.bridge.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract §10.3 (since 1.7.3, feature `library.live`): [SongsSortMenu] — the single source of
 * truth of the preference keys the phone's songs sort menus read, shared by the live observer
 * (its `libraryChanged` deltas). The §10.1 effective-menu read was removed in 1.11.0 (the client
 * PC stopped consuming it; the phone's own screens read their preferences directly).
 */
class SongsSortMenuTest {

    @Test
    fun `the sort menu keys are the chip order keys and their option visibility flags`() {
        val keys = SongsSortMenu.sortMenuKeys

        for (chip in SongsSortMenu.chips) {
            assertTrue(chip.orderKey in keys, chip.orderKey)
        }
        // Option visibility flags, with the phone's own enum ids
        assertTrue("all_sort_Title_visible" in keys)
        assertTrue("favs_sort_Custom_visible" in keys)
        // The Top chip's period selector shares its order key: its ids are StatisticsType names
        assertTrue("top_sort_OneWeek_visible" in keys)
        // The OnDevice chip reads its own OnDeviceSongSortBy ids ("Album", not "AlbumName")
        assertTrue("dev_sort_Album_visible" in keys)
        assertFalse("dev_sort_AlbumName_visible" in keys)
        assertFalse("local_sort_Title_visible" in keys)
        // 7 order keys + each chip's own option ids: 5 sort chips x 12 SongSortBy, Top x 7
        // StatisticsType, OnDevice x 5 OnDeviceSongSortBy
        assertEquals(7 + 5 * 12 + 7 + 5, keys.size)
    }
}
