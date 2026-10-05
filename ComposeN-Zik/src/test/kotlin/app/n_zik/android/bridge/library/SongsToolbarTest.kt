package app.n_zik.android.bridge.library

import app.it.fast4x.rimusic.enums.BuiltInPlaylist
import app.n_zik.android.components.dialog.settings.HomeSongsToolbarSettingsDialog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract §10.1 (since 1.8.0, feature `library.toolbar`): [SongsToolbar] — the wire content of
 * each chip's effective toolbar: the saved order ∩ the tab's available buttons (the rest in
 * their default order), the hidden buttons dropped, the locked ones always kept — the phone's
 * own `HomeSongsScreen.kt` semantics.
 */
class SongsToolbarTest {

    private val allVisible: (String) -> Boolean = { false }

    private val allTabIds: List<String> = HomeSongsToolbarSettingsDialog.tabAvailableIds.getValue(BuiltInPlaylist.All)

    @Test
    fun `an untouched chip serves its tab buttons in their default order`() {
        assertEquals(allTabIds, SongsToolbar.effective(SongsToolbar.all, "", allVisible))
        assertEquals(allTabIds, SongsToolbar.effective(SongsToolbar.all, null, allVisible))
    }

    @Test
    fun `the top chip serves its tab buttons, position_lock among them excluded`() {
        val served = SongsToolbar.effective(SongsToolbar.top, "", allVisible)

        assertEquals(HomeSongsToolbarSettingsDialog.tabAvailableIds.getValue(BuiltInPlaylist.Top), served)
        assertFalse("position_lock" in served)
        assertTrue("sort" in served)
    }

    @Test
    fun `the ondevice chip serves only its tab buttons`() {
        val served = SongsToolbar.effective(SongsToolbar.onDevice, "", allVisible)

        assertEquals(
            HomeSongsToolbarSettingsDialog.tabAvailableIds.getValue(BuiltInPlaylist.OnDevice),
            served,
        )
        for (excluded in setOf("import_menu", "export_dialog", "export_cache", "smart_trash", "match", "download_all", "delete_downloads", "sync_ytm_likes", "update")) {
            assertFalse(excluded in served, excluded)
        }
    }

    @Test
    fun `a user reordered chip shows its saved order first, the rest in the default order`() {
        val served = SongsToolbar.effective(SongsToolbar.all, """["locator","search"]""", allVisible)

        assertEquals(allTabIds.size, served.size)
        assertEquals(listOf("locator", "search"), served.take(2))
        assertEquals(allTabIds.filter { it != "locator" && it != "search" }, served.drop(2))
    }

    @Test
    fun `a hidden button leaves the toolbar`() {
        val served = SongsToolbar.effective(SongsToolbar.all, "", { id -> id == "shuffle" })

        assertEquals(allTabIds.size - 1, served.size)
        assertFalse("shuffle" in served)
    }

    @Test
    fun `the locked buttons stay even when their toggle is off`() {
        val served = SongsToolbar.effective(
            SongsToolbar.all,
            """["locator"]""",
            { id -> id in setOf("sort", "position_lock", "match", "sync_ytm_likes") },
        )

        assertEquals("locator", served.first())
        for (locked in HomeSongsToolbarSettingsDialog.lockedIds) assertTrue(locked in served, locked)
    }

    @Test
    fun `unknown or other-tab ids in the saved order are dropped, as on the phone`() {
        // The top tab has no "position_lock": it cannot enter the saved order either
        val served = SongsToolbar.effective(SongsToolbar.top, """["position_lock","locator","bogus"]""", allVisible)

        assertEquals("locator", served.first())
        assertFalse("position_lock" in served)
        assertFalse("bogus" in served)
    }

    @Test
    fun `duplicate ids in the saved order are deduplicated`() {
        val served = SongsToolbar.effective(SongsToolbar.all, """["search","search","locator"]""", allVisible)

        assertEquals(allTabIds.size, served.size)
        assertEquals(listOf("search", "locator"), served.take(2))
    }

    @Test
    fun `an unreadable saved order falls back to the default order`() {
        assertEquals(allTabIds, SongsToolbar.effective(SongsToolbar.all, "not a json", allVisible))
        assertEquals(allTabIds, SongsToolbar.effective(SongsToolbar.all, "", allVisible))
    }

    @Test
    fun `each wire filter reads the toolbar of its phone chip`() {
        assertEquals(SongsToolbar.all, SongsToolbar.chipKeys(SongFilter.ALL))
        // The bridge's local filter is the phone's OnDevice tab: it reads the OnDevice chip's toolbar
        assertEquals(SongsToolbar.onDevice, SongsToolbar.chipKeys(SongFilter.LOCAL))
        assertEquals(SongsToolbar.favs, SongsToolbar.chipKeys(SongFilter.LIKED))
        assertEquals(SongsToolbar.off, SongsToolbar.chipKeys(SongFilter.OFFLINE))
        assertEquals(SongsToolbar.dl, SongsToolbar.chipKeys(SongFilter.DOWNLOADED))
        assertEquals(SongsToolbar.top, SongsToolbar.chipKeys(SongFilter.TOP))
        assertEquals(SongsToolbar.disliked, SongsToolbar.chipKeys(SongFilter.DISLIKED))
    }

    @Test
    fun `each chip's default toolbar is its tab's available buttons`() {
        for (chip in SongsToolbar.chips) {
            assertEquals(
                HomeSongsToolbarSettingsDialog.tabAvailableIds[chip.tab],
                SongsToolbar.effective(chip, "", allVisible),
                chip.orderKey,
            )
        }
    }
}
