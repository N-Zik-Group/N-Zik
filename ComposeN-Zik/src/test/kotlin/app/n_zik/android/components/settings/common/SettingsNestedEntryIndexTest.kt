package app.n_zik.android.components.settings.common

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.components.dialog.settings.HomeSongsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeSongsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.DockToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.LocalPlaylistSortSettingsDialog
import app.n_zik.android.components.dialog.settings.buildHomeTabDefs
import app.n_zik.android.components.dialog.settings.buildShortcutDefs
import app.n_zik.android.utils.preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsNestedEntryIndexTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun nestedTargetsResolveRealDialogOpenersWithoutChangingPreferences() {
        val before = context.preferences.all.toMap()
        assertTrue(SettingsNestedEntryTargets.isNotEmpty())
        assertEquals(SettingsNestedEntryTargets.size, SettingsNestedEntryTargets.map { it.id }.toSet().size)
        assertEquals(28, SettingsNestedEntryTargets.map { requireNotNull(it.nestedDestination).dialogId }.toSet().size)
        assertFalse(AllSettingsEntryTargets.any { it.id == "ui.toolbar.history" })
        SettingsNestedEntryTargets.forEach { entry ->
            val destination = requireNotNull(entry.nestedDestination)
            val opener = AllSettingsEntryTargets.single { it.id == destination.openerId }
            assertNotNull(settingsNestedDialog(destination.dialogId))
            assertEquals(opener.parentCard(), entry.parentCard())
            assertTrue(entry.locationDescription(context).contains(context.getString(opener.labelRes)))
            assertTrue(searchSettingsEntries(context, context.getString(entry.labelRes)).contains(entry))
        }
        assertEquals(before, context.preferences.all)
    }

    @Test fun nestedCatalogUsesSourceAvailabilityAndExactCompoundRowIds() {
        val sort = HomeSongsSortSettingsDialog.searchOptions()
        assertTrue(sort.any { it.tabIndex == 1 && it.itemId == "favs_Custom" })
        assertFalse(sort.any { it.itemId == "off_Downloaded" || it.itemId == "dl_Downloaded" })
        val toolbar = HomeSongsToolbarSettingsDialog.searchOptions()
        assertFalse(toolbar.any { it.itemId == "dev_download_all" })
        assertTrue(toolbar.any { it.itemId == "top_sort" && it.labelRes == R.string.statistics_title })
        val combined = LocalPlaylistSortSettingsDialog.searchOptions().single { it.itemId == "pl_ArtistAndAlbum" }
        assertEquals(R.string.library_sort_artist, combined.labelRes)
        assertEquals(R.string.library_sort_album, combined.descriptionRes)
        val shortcutEntries = SettingsNestedEntryTargets.filter { it.nestedDestination?.dialogId == "AppShortcutsSettingsDialog" }
        assertEquals(buildShortcutDefs().keys, shortcutEntries.map { requireNotNull(it.nestedDestination).itemId }.toSet())
        val homeEntries = SettingsNestedEntryTargets.filter { it.nestedDestination?.dialogId == "HomeTabsSettingsDialog" }
        assertEquals(buildHomeTabDefs().keys, homeEntries.map { requireNotNull(it.nestedDestination).itemId }.toSet())
        val dock = DockToolbarSettingsDialog.searchOptions().associate { it.labelRes to it.itemId }
        buildHomeTabDefs().values.forEach { definition ->
            assertEquals(context.resources.getResourceEntryName(definition.iconRes), dock.getValue(definition.labelRes))
        }
    }

    @Test fun playerTabsAndQueueToolbarTargetsOpenFromTheAppearanceActionBarCard() {
        listOf(
            Triple("appearance.player_action_bar.player_action_bar_buttons", "top.action.audioVideo", 0),
            Triple("appearance.player_action_bar.player_action_bar_buttons", "shuffle", 1),
            Triple("ui.toolbar.queue", "queue.select", 0),
        ).forEach { (openerId, itemId, tabIndex) ->
            val opener = AllSettingsEntryTargets.single { it.id == openerId }
            val option = SettingsNestedEntryTargets.single {
                it.nestedDestination?.openerId == openerId && it.nestedDestination.itemId == itemId
            }
            assertEquals(2, opener.tab)
            assertEquals(R.string.settings_section_player_action_bar_title, opener.cardTitleRes)
            assertEquals(opener.parentCard(), option.parentCard())
            assertEquals(tabIndex, option.nestedDestination?.tabIndex)
            assertTrue(searchSettingsEntries(context, context.getString(option.labelRes)).contains(option))
        }
    }

    @Test fun sharedQuickPicksOptionsKeepBothActualLocationsAndDistinctTargets() {
        val targets = SettingsNestedEntryTargets.filter { it.nestedDestination?.dialogId == "QuickPicksContentSettingsDialog" }
        val groups = targets.groupBy { requireNotNull(it.nestedDestination).openerId }
        assertEquals(setOf("ui.home_tabs_settings.quick_picks", "ai.quick_picks.quick_picks_content"), groups.keys)
        assertEquals(groups.values.first().map { it.labelRes }, groups.values.last().map { it.labelRes })
        assertEquals(setOf("interface", "ai"), targets.map { it.parentCard().categoryId() }.toSet())
    }
}
