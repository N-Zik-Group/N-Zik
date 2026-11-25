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

    @Test fun legacySyncIdRemainsPortableWhenItIsNotAvailableInTheAllEditor() {
        val syncId = "sync_ytm_likes"
        val dialog = HomeSongsToolbarSettingsDialog
        assertTrue(syncId in dialog.portableLists().getValue("all").ids)
        assertFalse(syncId in dialog.tabAvailableIds.getValue(BuiltInPlaylist.All))
        assertTrue(syncId in dialog.tabAvailableIds.getValue(BuiltInPlaylist.Favorites))
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
