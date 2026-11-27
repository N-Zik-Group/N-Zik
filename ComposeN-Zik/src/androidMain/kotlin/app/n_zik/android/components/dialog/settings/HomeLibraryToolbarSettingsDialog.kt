package app.n_zik.android.components.dialog.settings

import app.n_zik.android.utils.preferences
import app.n_zik.android.components.settings.appearance.preview.PortableControlList
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState
import timber.log.Timber

import app.n_zik.android.utils.homeLibraryMonthlyPlaylistToolbarOrderKey
import app.n_zik.android.utils.homeLibraryPinnedPlaylistToolbarOrderKey
import app.n_zik.android.utils.homeLibraryToolbarOrderKey
import app.n_zik.android.utils.homeLibraryOnDeviceToolbarOrderKey
import app.n_zik.android.utils.homeLibraryYTPlaylistToolbarOrderKey
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.utils.homeLibraryMonthlyPlaylistSortMenuOrderKey
import app.n_zik.android.utils.homeLibraryOnDeviceSortMenuOrderKey
import app.n_zik.android.utils.homeLibraryPinnedPlaylistSortMenuOrderKey
import app.n_zik.android.utils.homeLibraryPlaylistSortMenuOrderKey
import app.n_zik.android.utils.homeLibraryYTPlaylistSortMenuOrderKey
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.SettingsNestedOption

object HomeLibraryToolbarSettingsDialog : ContentFramedDialog {

    /** Exact searchable options from the same definitions used by this dialog. */
    internal fun searchOptions(): List<SettingsNestedOption> = listOf("all" to R.string.common_option_all, "pin" to R.string.playlist_pinned_title, "rw" to R.string.rewind_title, "yt" to R.string.library_youtube_playlists_label, "dev" to R.string.library_on_device_title)
        .flatMapIndexed { index, (tab, label) -> availableButtonIds(tab).map { id ->
            SettingsNestedOption("${getTabPrefix(tab)}_$id", searchLabelResources.getValue(id), index, label)
        } }

    private val searchLabelResources = mapOf(
        "sort" to R.string.library_sort_order_title,
        "position_lock" to R.string.playlist_reorder_lock_toggle_label,
        "search" to R.string.search_title,
        "sync" to R.string.sync_imported_playlists_automatic_title,
        "shuffle" to R.string.player_action_shuffle_hint,
        "smart_shuffle" to R.string.recommendations_action_smart_shuffle,
        "item_selector" to R.string.common_action_select,
        "play_next" to R.string.player_action_play_next,
        "enqueue" to R.string.player_action_enqueue,
        "add_to_playlist" to R.string.playlist_actions_title,
        "import_menu" to R.string.playlist_action_import,
        "export_dialog" to R.string.playlist_action_export,
        "delete_playlists" to R.string.playlist_action_delete_selected,
        "item_size" to R.string.common_size_label,
    )

    val allButtonIds = listOf(
        "sort", "position_lock", "search", "shuffle", "smart_shuffle", "play_next", "enqueue", "item_selector",
        "add_to_playlist", "sync", "import_menu", "export_dialog", "delete_playlists", "item_size"
    )

    internal val onDeviceButtonIds = allButtonIds.filterNot { it in setOf("sync", "export_dialog", "import_menu", "smart_shuffle") }

    private fun portableButtonIds(tab: String): List<String> =
        if (tab == "dev") onDeviceButtonIds else allButtonIds

    private fun availableButtonIds(tab: String): List<String> {
        val sortKey = toolbarTabs.first { it.first == tab }.third
        return portableButtonIds(tab).withoutUnsupportedPositionLock(HomeLibrarySortSettingsDialog.menuIds(sortKey))
    }

    private fun getTabPrefix(tab: String): String = when (tab) {
        "dev" -> "dev"
        "pin" -> "pin"
        "rw" -> "rw"
        "yt" -> "yt"
        else -> "all"
    }

    override val dialogTitle: String @Composable get() = stringResource(R.string.settings_dialog_toolbar_title_format, stringResource(R.string.library_title))
    override var isActive: Boolean by mutableStateOf(false)

    private fun serializeOrder(order: List<String>): String { val a = JSONArray(); order.forEach { a.put(it) }; return a.toString() }

    private val toolbarTabs = listOf(
        Triple("all", homeLibraryToolbarOrderKey, homeLibraryPlaylistSortMenuOrderKey),
        Triple("pin", homeLibraryPinnedPlaylistToolbarOrderKey, homeLibraryPinnedPlaylistSortMenuOrderKey),
        Triple("rw", homeLibraryMonthlyPlaylistToolbarOrderKey, homeLibraryMonthlyPlaylistSortMenuOrderKey),
        Triple("yt", homeLibraryYTPlaylistToolbarOrderKey, homeLibraryYTPlaylistSortMenuOrderKey),
        Triple("dev", homeLibraryOnDeviceToolbarOrderKey, homeLibraryOnDeviceSortMenuOrderKey),
    )

    internal fun portableLists(): Map<String, PortableControlList> = toolbarTabs.associate { (tab, key) ->
        val prefix = getTabPrefix(tab)
        prefix to PortableControlList(key, portableButtonIds(tab), "${prefix}_lib_", "")
    }

    @Composable
    override fun DialogBody() {
        val ctx = LocalContext.current
        val prefs = ctx.preferences

        val tabs = toolbarTabs
        val initial = remember(prefs) {
            val lists = portableLists()
            tabs.associate { (tab, _) -> tab to lists.getValue(getTabPrefix(tab)).readHomeToolbar(prefs) }
        }

        val tabTransition = rememberSettingsDialogTabTransition(tabs.size)
        val selectedTabIndex = tabTransition.displayedIndex

        var workingOrders by remember(prefs) {
            mutableStateOf(initial.mapValues { (_, settings) -> settings.order.toMutableList() }.toMutableMap())
        }
        var workingToggles by remember(prefs) {
            mutableStateOf(initial.mapValues { (_, settings) -> settings.visibility.toMutableMap() }.toMutableMap())
        }

        val currentTabKey = tabs[selectedTabIndex].first
        val currentWorkingOrder = workingOrders[currentTabKey] ?: run {
            Timber.tag("HomeLibraryToolbarSettingsDialog").w("Missing workingOrders for tab $currentTabKey")
            return
        }
        val currentWorkingToggles = workingToggles[currentTabKey] ?: run {
            Timber.tag("HomeLibraryToolbarSettingsDialog").w("Missing workingToggles for tab $currentTabKey")
            return
        }
        val tabPrefix = getTabPrefix(currentTabKey)

        val sortLabel = stringResource(searchLabelResources.getValue("sort"))
        val positionLockLabel = stringResource(searchLabelResources.getValue("position_lock"))
        val searchLabel = stringResource(searchLabelResources.getValue("search"))
        val syncLabel = stringResource(searchLabelResources.getValue("sync"))
        val shuffleLabel = stringResource(searchLabelResources.getValue("shuffle"))
        val itemSelectorLabel = stringResource(searchLabelResources.getValue("item_selector"))
        val playNextLabel = stringResource(searchLabelResources.getValue("play_next"))
        val enqueueLabel = stringResource(searchLabelResources.getValue("enqueue"))
        val addToPlaylistLabel = stringResource(searchLabelResources.getValue("add_to_playlist"))
        val importMenuLabel = stringResource(searchLabelResources.getValue("import_menu"))
        val exportDialogLabel = stringResource(searchLabelResources.getValue("export_dialog"))
        val deletePlaylistsLabel = stringResource(searchLabelResources.getValue("delete_playlists"))
        val itemSizeLabel = stringResource(searchLabelResources.getValue("item_size"))

        val currentLockedIds = homeToolbarLockedIds.map { "${tabPrefix}_$it" }.toSet()

        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val o = currentWorkingOrder.toMutableList()
            val fi = o.indexOfFirst { "${tabPrefix}_$it" == from.key }
            val ti = o.indexOfFirst { "${tabPrefix}_$it" == to.key }
            if (fi != -1 && ti != -1) {
                val item = o.removeAt(fi)
                o.add(ti, item)
                workingOrders = workingOrders.toMutableMap().apply { this[currentTabKey] = o }
            }
        }

        val availableIds = availableButtonIds(currentTabKey).toSet()
        val items = currentWorkingOrder.distinct().filter { it in availableIds }.map { id ->
            val pk = "${tabPrefix}_lib_$id"
            val uid = "${tabPrefix}_$id"
            when (id) {
                "sort" -> ToggleItem(uid, R.drawable.arrow_up, sortLabel, pk, true)
                "position_lock" -> ToggleItem(uid, R.drawable.locked, positionLockLabel, pk, true)
                "search" -> ToggleItem(uid, R.drawable.search_circle, searchLabel, pk, true)
                "sync" -> ToggleItem(uid, R.drawable.sync, syncLabel, pk, true)
                "shuffle" -> ToggleItem(uid, R.drawable.shuffle, shuffleLabel, pk, true)
                "smart_shuffle" -> ToggleItem(uid, R.drawable.smart_shuffle, stringResource(searchLabelResources.getValue(id)), pk, true)
                "item_selector" -> ToggleItem(uid, R.drawable.checked_filled, itemSelectorLabel, pk, true)
                "play_next" -> ToggleItem(uid, R.drawable.play_skip_forward, playNextLabel, pk, true)
                "enqueue" -> ToggleItem(uid, R.drawable.enqueue, enqueueLabel, pk, true)
                "add_to_playlist" -> ToggleItem(uid, R.drawable.playlist, addToPlaylistLabel, pk, true)
                "import_menu" -> ToggleItem(uid, R.drawable.import_playlist_source, importMenuLabel, pk, true)
                "export_dialog" -> ToggleItem(uid, R.drawable.export_playlist, exportDialogLabel, pk, true)
                "delete_playlists" -> ToggleItem(uid, R.drawable.trash, deletePlaylistsLabel, pk, true)
                "item_size" -> ToggleItem(uid, R.drawable.resize, itemSizeLabel, pk, true)
                else -> null
            }
        }.filterNotNull()

        ToggleListDialog(
            frameTitle = dialogTitle,
            onDismiss = ::hideDialog,
            tabContentAlpha = { tabTransition.alpha },
            bodyHeader = {
                SettingsDialogTabRow(
                    labels = tabs.map { tab -> stringResource(when (tab.first) { "dev" -> R.string.library_on_device_title; "pin" -> R.string.playlist_pinned_title; "rw" -> R.string.rewind_title; "yt" -> R.string.library_youtube_playlists_label; else -> R.string.common_option_all }) },
                    selectedTabIndex = tabTransition.selectedIndex,
                    onTabSelected = tabTransition::select,
                )
                Spacer(modifier = Modifier.fillMaxWidth().height(8.dp).testTag("toggleListDialogTabGap"))
            },
            items = items, lazyListState = lazyListState, reorderableState = reorderableState,
            enforceMinOneChecked = false,
            lockedCheckedIds = currentLockedIds,
            checkedStatesOverride = items.map { currentWorkingToggles[it.id.removePrefix("${tabPrefix}_")] ?: true },
            onCheckedChange = { index, newValue ->
                val id = items[index].id.removePrefix("${tabPrefix}_")
                val m = workingToggles[currentTabKey]?.toMutableMap() ?: return@ToggleListDialog
                m[id] = newValue
                workingToggles = workingToggles.toMutableMap().apply { this[currentTabKey] = m }
            },
            onReset = {
                val availableIds = portableButtonIds(currentTabKey)
                val m = availableIds.associateWith { true }.toMutableMap()
                workingToggles = workingToggles.toMutableMap().apply { this[currentTabKey] = m }
                workingOrders = workingOrders.toMutableMap().apply { this[currentTabKey] = availableIds.toMutableList() }
            },
            onCancel = { hideDialog() },
            onConfirm = {
                val edit = prefs.edit()
                tabs.forEach { (tab, key) ->
                    val tp = getTabPrefix(tab)
                    val order = workingOrders[tab] ?: return@forEach
                    val toggles = workingToggles[tab] ?: return@forEach

                    toggles.forEach { (id, isChecked) ->
                        edit.putBoolean("${tp}_lib_$id", isChecked)
                    }

                    edit.putString(key, serializeOrder(order))
                }
                edit.apply()
                Toaster.s(R.string.settings_preference_saved_message); hideDialog()
            }
        )
    }

    fun reset(context: Context) {
        val prefs = context.preferences
        val edit = prefs.edit()
        val tabs = listOf(
            "all" to homeLibraryToolbarOrderKey,
            "pin" to homeLibraryPinnedPlaylistToolbarOrderKey,
            "rw" to homeLibraryMonthlyPlaylistToolbarOrderKey,
            "yt" to homeLibraryYTPlaylistToolbarOrderKey,
            "dev" to homeLibraryOnDeviceToolbarOrderKey
        )
        tabs.forEach { (tab, key) ->
            val tp = getTabPrefix(tab)
            val availableIds = portableButtonIds(tab)
            availableIds.forEach { id ->
                edit.putBoolean("${tp}_lib_$id", true)
            }
            edit.putString(key, serializeOrder(availableIds))
        }
        edit.apply()
    }
}
