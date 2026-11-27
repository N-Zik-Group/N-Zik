package app.n_zik.android.components.dialog.settings

import android.app.Application
import android.content.Context
import app.n_zik.android.components.settings.appearance.preview.PortableControlList
import app.n_zik.android.enums.BuiltInPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HomeToolbarPreferencesTest {
    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("home-toolbar-order-test", Context.MODE_PRIVATE)
    private val lists = listOf(
        HomeArtistsToolbarSettingsDialog.portableLists().getValue("favs"),
        HomeAlbumsToolbarSettingsDialog.portableLists().getValue("dev"),
        HomeLibraryToolbarSettingsDialog.portableLists().getValue("yt"),
    )

    @Test fun legacyPlaylistChipOrderKeepsAllFirstAndPreservesTheOtherChips() {
        val saved = """["on_device","yt_playlists","all","on_device","unknown"]"""
        assertEquals(
            listOf("all", "on_device", "yt_playlists", "pinned_playlists", "rewind"),
            libraryPlaylistChipOrder(saved),
        )
        assertEquals(playlistsDefaultOrder, libraryPlaylistChipOrder("invalid"))
    }

    @Test fun legacySyncIdRemainsPortableWhenItIsNotAvailableInTheAllEditor() {
        val syncId = "sync_ytm_likes"
        val dialog = HomeSongsToolbarSettingsDialog
        assertTrue(syncId in dialog.portableLists().getValue("all").ids)
        assertFalse(syncId in dialog.tabAvailableIds.getValue(BuiltInPlaylist.All))
        assertTrue(syncId in dialog.tabAvailableIds.getValue(BuiltInPlaylist.Favorites))
    }

    @Test fun positionLockIsOnlyAvailableWhereTheSortSupportsCustomOrder() {
        val positionLock = "position_lock"
        val albums = HomeAlbumsToolbarSettingsDialog
        val artists = HomeArtistsToolbarSettingsDialog
        val library = HomeLibraryToolbarSettingsDialog
        val songs = HomeSongsToolbarSettingsDialog

        val expected = listOf("all_position_lock", "dev_position_lock")
        assertEquals(expected, albums.searchOptions().map { it.itemId }.filter { it.endsWith("_$positionLock") })
        assertEquals(expected, artists.searchOptions().map { it.itemId }.filter { it.endsWith("_$positionLock") })
        assertEquals(expected, library.searchOptions().map { it.itemId }.filter { it.endsWith("_$positionLock") })
        assertFalse(positionLock in songs.tabAvailableIds.getValue(BuiltInPlaylist.OnDevice))

        assertTrue(positionLock in albums.portableLists().getValue("favs").ids)
        assertTrue(positionLock in artists.portableLists().getValue("disliked").ids)
        assertTrue(positionLock in library.portableLists().getValue("yt").ids)
        assertTrue(positionLock in songs.portableLists().getValue("dev").ids)
    }

    @Test fun smartShuffleKeepsItsPortableIdButIsUnavailableOnDevice() {
        val smartShuffle = "smart_shuffle"
        val dialog = HomeSongsToolbarSettingsDialog

        assertFalse(smartShuffle in dialog.tabAvailableIds.getValue(BuiltInPlaylist.OnDevice))
        assertTrue(smartShuffle in dialog.tabAvailableIds.getValue(BuiltInPlaylist.All))
        assertTrue(smartShuffle in dialog.portableLists().getValue("dev").ids)
    }

    @Test fun explicitVisibilityAppliesWithoutASavedOrder() {
        lists.forEach { list ->
            prefs.edit().putBoolean(list.visibilityKey("shuffle"), false)
                .putBoolean(list.visibilityKey("sort"), false).commit()

            val resolved = list.readHomeToolbar(prefs)
            assertEquals(list.ids, resolved.order)
            assertEquals(list.ids - "shuffle", resolved.visibleIds)
        }
    }

    @Test fun legacyOmissionsSurviveUntilAnExplicitToggleOverridesThem() {
        lists.forEach { list ->
            prefs.edit().putString(list.orderKey, """["enqueue","sort","search"]""")
                .putBoolean(list.visibilityKey("search"), false)
                .putBoolean(list.visibilityKey("shuffle"), true).commit()

            val resolved = list.readHomeToolbar(prefs)
            assertEquals(listOf("enqueue", "sort", "search"), resolved.order.take(3))
            assertEquals(list.ids.toSet(), resolved.order.toSet())
            assertEquals(listOf("enqueue", "sort"), resolved.visibleIds.take(2))
            assertTrue("shuffle" in resolved.visibleIds)
            assertFalse("search" in resolved.visibleIds)
            assertFalse("add_to_playlist" in resolved.visibleIds)
        }
    }

    @Test fun fullSavedOrderKeepsHiddenButtonsInPlaceWhenVisibilityChanges() {
        lists.forEach { list ->
            val order = list.ids.reversed()
            val shuffleKey = list.visibilityKey("shuffle")
            prefs.edit().putString(list.orderKey, org.json.JSONArray(order).toString())
                .putBoolean(shuffleKey, false).commit()

            val resolved = list.readHomeToolbar(prefs)
            assertEquals(order, resolved.order)
            assertEquals(order - "shuffle", resolved.visibleIds)

            prefs.edit().putBoolean(shuffleKey, true).commit()
            assertEquals(order, list.readHomeToolbar(prefs).visibleIds)
        }
    }

    private fun PortableControlList.visibilityKey(id: String) = visibilityPrefix + id + visibilitySuffix
}
