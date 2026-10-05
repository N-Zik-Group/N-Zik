package app.n_zik.android.bridge.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract §10.1 (since 1.7.3, feature `library.sortMenu`): [SongsSortMenu] — the wire order of
 * each chip's options and a chip's effective menu: the saved order ∩ the visible options, the
 * rest in the native order, the unknown ids dropped — the phone's own
 * `Sort.readSortedEnumConstants` / `PeriodSelector.readSortedEntries` semantics.
 */
class SongsSortMenuTest {

    private val allVisible: (String) -> Boolean = { false }

    @Test
    fun `the native order is the phone enum order as wire values`() {
        assertEquals(
            listOf("title", "artist", "album", "duration", "playCount", "playTime", "relativePlayTime", "dateAdded", "datePlayed", "dateLiked", "downloaded", "custom"),
            SongsSortMenu.nativeOrder,
        )
    }

    @Test
    fun `an untouched sort chip shows the twelve options in the native order`() {
        assertEquals(SongsSortMenu.nativeOrder, SongsSortMenu.effective(SongsSortMenu.all, "", allVisible))
        assertEquals(SongsSortMenu.nativeOrder, SongsSortMenu.effective(SongsSortMenu.all, null, allVisible))
    }

    @Test
    fun `the top chip serves its seven periods, in the phone enum order as wire values`() {
        assertEquals(
            listOf("today", "week", "month", "3months", "6months", "year", "all"),
            SongsSortMenu.effective(SongsSortMenu.top, "", allVisible),
        )
    }

    @Test
    fun `the ondevice chip serves its five options, in the phone enum order as wire values`() {
        assertEquals(
            listOf("title", "dateAdded", "artist", "duration", "album"),
            SongsSortMenu.effective(SongsSortMenu.onDevice, "", allVisible),
        )
    }

    @Test
    fun `a hidden option leaves the menu`() {
        val menu = SongsSortMenu.effective(SongsSortMenu.all, "", { id -> id == "Duration" })

        assertEquals(
            listOf("title", "artist", "album", "playCount", "playTime", "relativePlayTime", "dateAdded", "datePlayed", "dateLiked", "downloaded", "custom"),
            menu,
        )
    }

    @Test
    fun `a user reordered chip shows its saved order first, the rest in the native order`() {
        val menu = SongsSortMenu.effective(SongsSortMenu.all, """["DateAdded","Title"]""", allVisible)

        assertEquals(12, menu.size)
        assertEquals(listOf("dateAdded", "title"), menu.take(2))
        assertEquals(
            listOf("artist", "album", "duration", "playCount", "playTime", "relativePlayTime", "datePlayed", "dateLiked", "downloaded", "custom"),
            menu.drop(2),
        )
    }

    @Test
    fun `the saved order keeps only the visible options`() {
        // DateAdded is hidden: it drops out of the saved order, Title stays first
        val menu = SongsSortMenu.effective(SongsSortMenu.all, """["DateAdded","Title"]""", { id -> id == "DateAdded" })

        assertEquals(11, menu.size)
        assertEquals("title", menu.first())
        assertFalse("dateAdded" in menu)
        assertEquals(
            listOf("artist", "album", "duration", "playCount", "playTime", "relativePlayTime", "datePlayed", "dateLiked", "downloaded", "custom"),
            menu.drop(1),
        )
    }

    @Test
    fun `unknown ids in the saved order are dropped, as on the phone`() {
        // The Top chip's period selector shares its order key: a period name is not a sort option
        val menu = SongsSortMenu.effective(SongsSortMenu.all, """["OneWeek","Title","Month"]""", allVisible)

        assertEquals(12, menu.size)
        assertEquals("title", menu.first())
        assertFalse(menu.any { it !in SongsSortMenu.nativeOrder })
    }

    @Test
    fun `the top chip keeps its saved period order, its hidden periods dropped`() {
        val menu = SongsSortMenu.effective(SongsSortMenu.top, """["OneYear","Today","OneWeek"]""", { id -> id == "OneWeek" })

        assertEquals(listOf("year", "today", "month", "3months", "6months", "all"), menu)
    }

    @Test
    fun `duplicate ids in the saved order are deduplicated`() {
        val menu = SongsSortMenu.effective(SongsSortMenu.all, """["Title","Title","Artist"]""", allVisible)

        assertEquals(12, menu.size)
        assertEquals(listOf("title", "artist"), menu.take(2))
    }

    @Test
    fun `an unreadable saved order falls back to the native order`() {
        assertEquals(SongsSortMenu.nativeOrder, SongsSortMenu.effective(SongsSortMenu.all, "not a json", allVisible))
        assertEquals(SongsSortMenu.nativeOrder, SongsSortMenu.effective(SongsSortMenu.all, "", allVisible))
    }

    @Test
    fun `a chip with every option hidden shows an empty menu`() {
        assertEquals(emptyList<String>(), SongsSortMenu.effective(SongsSortMenu.all, "", { true }))
    }

    @Test
    fun `each wire filter reads the menu of its phone chip`() {
        assertEquals(SongsSortMenu.all, SongsSortMenu.chipKeys(SongFilter.ALL))
        // The bridge's local filter is the phone's OnDevice tab: it reads the OnDevice chip's menu
        assertEquals(SongsSortMenu.onDevice, SongsSortMenu.chipKeys(SongFilter.LOCAL))
        assertEquals(SongsSortMenu.favs, SongsSortMenu.chipKeys(SongFilter.LIKED))
        assertEquals(SongsSortMenu.off, SongsSortMenu.chipKeys(SongFilter.OFFLINE))
        assertEquals(SongsSortMenu.dl, SongsSortMenu.chipKeys(SongFilter.DOWNLOADED))
        assertEquals(SongsSortMenu.top, SongsSortMenu.chipKeys(SongFilter.TOP))
        assertEquals(SongsSortMenu.disliked, SongsSortMenu.chipKeys(SongFilter.DISLIKED))
    }

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
