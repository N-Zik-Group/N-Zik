package app.n_zik.android.components.dialog.settings

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

import app.it.fast4x.rimusic.enums.BuiltInPlaylist
import app.it.fast4x.rimusic.utils.homeSongsDislikedToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeSongsDownloadedToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeSongsFavoritesToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeSongsOfflineToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeSongsOnDeviceToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeSongsToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeSongsTopToolbarOrderKey
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.SettingsNestedOption

object HomeSongsToolbarSettingsDialog : ContentFramedDialog {

    /** Exact searchable options from the same definitions used by this dialog. */
    internal fun searchOptions(): List<SettingsNestedOption> = listOf(BuiltInPlaylist.All, BuiltInPlaylist.Favorites, BuiltInPlaylist.Disliked, BuiltInPlaylist.Offline, BuiltInPlaylist.Downloaded, BuiltInPlaylist.Top, BuiltInPlaylist.OnDevice)
        .flatMapIndexed { index, tab -> tabAvailableIds.getValue(tab).map { id ->
            SettingsNestedOption("${getTabPrefix(tab)}_$id", if (id == "sort" && tab == BuiltInPlaylist.Top) R.string.statistics else searchLabelResources.getValue(id), index, tab.textId)
        } }

    private val searchLabelResources = mapOf(
        "position_lock" to R.string.info_lock_unlock_reorder_songs,
        "match" to R.string.match_album_audio_version,
        "search" to R.string.search,
        "locator" to R.string.info_find_the_song_that_is_playing,
        "download_all" to R.string.info_download_all_songs,
        "delete_downloads" to R.string.info_remove_all_downloaded_songs,
        "update" to R.string.update,
        "shuffle" to R.string.info_shuffle,
        "smart_shuffle" to R.string.info_smart_recommendation,
        "item_selector" to R.string.item_select,
        "play_next" to R.string.play_next,
        "enqueue" to R.string.enqueue,
        "add_to_favorite" to R.string.add_to_favorites,
        "add_to_playlist" to R.string.add_to_playlist,
        "import_menu" to R.string.import_playlist,
        "export_dialog" to R.string.export_playlist,
        "export_cache" to R.string.export_cached,
        "smart_trash" to R.string.smart_trash,
        "sync_ytm_likes" to R.string.autosync_likes,
        "sort" to R.string.sorting_order,
    )


    val allButtonIds = listOf(
        "sort", "position_lock", "match", "search", "sync_ytm_likes", "locator",
        "download_all", "delete_downloads",
        "shuffle", "smart_shuffle", "item_selector",
        "play_next", "enqueue", "add_to_favorite", "add_to_playlist",
        "import_menu", "export_dialog", "export_cache", "update", "smart_trash"
    )

    val tabAvailableIds = mapOf(
        BuiltInPlaylist.All to allButtonIds.filter { it != "export_cache" },
        BuiltInPlaylist.Favorites to allButtonIds.filter { it != "export_cache" },
        BuiltInPlaylist.Offline to allButtonIds.filter { it != "import_menu" && it != "sync_ytm_likes" },
        BuiltInPlaylist.Downloaded to allButtonIds.filter { it != "import_menu" && it != "sync_ytm_likes" },
        BuiltInPlaylist.Top to allButtonIds.filter { it != "import_menu" && it != "position_lock" && it != "export_cache" && it != "sync_ytm_likes" },
        BuiltInPlaylist.OnDevice to allButtonIds.filter { it !in setOf("import_menu", "export_dialog", "export_cache", "smart_trash", "match", "download_all", "delete_downloads", "sync_ytm_likes", "update") },
        BuiltInPlaylist.Disliked to allButtonIds.filter { it != "export_cache" && it != "sync_ytm_likes" && it != "import_menu" }
    )

    /** Toolbar buttons that must remain available. */
    val lockedIds = setOf("sort", "position_lock", "match", "sync_ytm_likes")

    fun getTabPrefix(tab: BuiltInPlaylist): String = when (tab) {
        BuiltInPlaylist.All -> "all"
        BuiltInPlaylist.Favorites -> "favs"
        BuiltInPlaylist.Offline -> "off"
        BuiltInPlaylist.Downloaded -> "dl"
        BuiltInPlaylist.Top -> "top"
        BuiltInPlaylist.OnDevice -> "dev"
        BuiltInPlaylist.Disliked -> "disliked"
        else -> "x"
    }

    override val dialogTitle: String @Composable get() = stringResource(R.string.home_songs_settings) + " - Toolbar"
    override var isActive: Boolean by mutableStateOf(false)

    private fun parseOrder(s: String, tab: BuiltInPlaylist): List<String> {
        val available = tabAvailableIds[tab] ?: allButtonIds
        if (s.isBlank()) return available
        return try {
            val a = JSONArray(s)
            val savedIds = mutableListOf<String>()
            val seen = mutableSetOf<String>()
            for (i in 0 until a.length()) {
                val id = a.getString(i)
                if (id in available && seen.add(id)) savedIds.add(id)
            }
            val missingIds = available.filter { it !in savedIds }
            savedIds + missingIds
        } catch (_: Exception) { available }
    }

    private fun serializeOrder(order: List<String>): String { val a = JSONArray(); order.forEach { a.put(it) }; return a.toString() }

    @Composable
    override fun DialogBody() {
        val ctx = LocalContext.current
        val prefs = remember { ctx.getSharedPreferences("preferences", Context.MODE_PRIVATE) }

        val tabs = listOf(
            BuiltInPlaylist.All to homeSongsToolbarOrderKey,
            BuiltInPlaylist.Favorites to homeSongsFavoritesToolbarOrderKey,
            BuiltInPlaylist.Disliked to homeSongsDislikedToolbarOrderKey,
            BuiltInPlaylist.Offline to homeSongsOfflineToolbarOrderKey,
            BuiltInPlaylist.Downloaded to homeSongsDownloadedToolbarOrderKey,
            BuiltInPlaylist.Top to homeSongsTopToolbarOrderKey,
            BuiltInPlaylist.OnDevice to homeSongsOnDeviceToolbarOrderKey
        )

        var selectedTabIndex by remember { mutableStateOf(0) }

        var workingOrders by remember {
            mutableStateOf(
                tabs.associate { (tab, key) -> tab to parseOrder(prefs.getString(key, "") ?: "", tab).toMutableList() }.toMutableMap()
            )
        }

        var workingToggles by remember {
            mutableStateOf(
                tabs.associate { (tab, _) ->
                    val tp = getTabPrefix(tab)
                    val available = tabAvailableIds[tab] ?: allButtonIds
                    tab to available.associateWith { id ->
                        prefs.getBoolean("${tp}_ts_$id", true)
                    }.toMutableMap()
                }.toMutableMap()
            )
        }

        val currentTab = tabs[selectedTabIndex].first
        val currentWorkingOrder = workingOrders[currentTab] ?: run {
            Timber.w("Missing workingOrders for tab $currentTab")
            return
        }
        val currentWorkingToggles = workingToggles[currentTab] ?: run {
            Timber.w("Missing workingToggles for tab $currentTab")
            return
        }
        val isTopTab = currentTab == BuiltInPlaylist.Top
        val tabPrefix = getTabPrefix(currentTab)

        val sortLabel = stringResource(searchLabelResources.getValue("sort"))
        val topSortLabel = stringResource(R.string.statistics)
        val positionLockLabel = stringResource(searchLabelResources.getValue("position_lock"))
        val downloadAllLabel = stringResource(searchLabelResources.getValue("download_all"))
        val deleteDownloadsLabel = stringResource(searchLabelResources.getValue("delete_downloads"))
        val updateLabel = stringResource(searchLabelResources.getValue("update"))
        val searchLabel = stringResource(searchLabelResources.getValue("search"))
        val locatorLabel = stringResource(searchLabelResources.getValue("locator"))
        val shuffleLabel = stringResource(searchLabelResources.getValue("shuffle"))
        val smartShuffleLabel = stringResource(searchLabelResources.getValue("smart_shuffle"))
        val itemSelectorLabel = stringResource(searchLabelResources.getValue("item_selector"))
        val playNextLabel = stringResource(searchLabelResources.getValue("play_next"))
        val enqueueLabel = stringResource(searchLabelResources.getValue("enqueue"))
        val addToFavoriteLabel = stringResource(searchLabelResources.getValue("add_to_favorite"))
        val addToPlaylistLabel = stringResource(searchLabelResources.getValue("add_to_playlist"))
        val importMenuLabel = stringResource(searchLabelResources.getValue("import_menu"))
        val exportDialogLabel = stringResource(searchLabelResources.getValue("export_dialog"))
        val exportCacheLabel = stringResource(searchLabelResources.getValue("export_cache"))
        val smartTrashLabel = stringResource(searchLabelResources.getValue("smart_trash"))
        val matchLabel = stringResource(searchLabelResources.getValue("match"))
        val syncLabel = stringResource(searchLabelResources.getValue("sync_ytm_likes"))

        val currentLockedIds = lockedIds.map { "${tabPrefix}_$it" }.toSet()

        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val o = currentWorkingOrder.toMutableList()
            val fi = o.indexOfFirst { "${tabPrefix}_$it" == from.key }
            val ti = o.indexOfFirst { "${tabPrefix}_$it" == to.key }
            if (fi != -1 && ti != -1) {
                val item = o.removeAt(fi)
                o.add(ti, item)
                workingOrders = workingOrders.toMutableMap().apply { this[currentTab] = o }
            }
        }

        val items = currentWorkingOrder.distinct().map { id ->
            val pk = "${tabPrefix}_ts_$id"
            val uid = "${tabPrefix}_$id"
            when (id) {
                "sort" -> ToggleItem(uid, if (isTopTab) R.drawable.stat_today else R.drawable.arrow_up,
                    if (isTopTab) topSortLabel else sortLabel, pk, true)
                "position_lock" -> ToggleItem(uid, R.drawable.locked, positionLockLabel, pk, true)
                "match" -> ToggleItem(uid, R.drawable.alert, matchLabel, pk, true)
                "search" -> ToggleItem(uid, R.drawable.search_circle, searchLabel, pk, true)
                "locator" -> ToggleItem(uid, R.drawable.locate, locatorLabel, pk, true)
                "download_all" -> ToggleItem(uid, R.drawable.downloaded, downloadAllLabel, pk, true)
                "delete_downloads" -> ToggleItem(uid, R.drawable.download, deleteDownloadsLabel, pk, true)
                "update" -> ToggleItem(uid, R.drawable.refresh, updateLabel, pk, true)
                "shuffle" -> ToggleItem(uid, R.drawable.shuffle, shuffleLabel, pk, true)
                "smart_shuffle" -> ToggleItem(uid, R.drawable.smart_shuffle, smartShuffleLabel, pk, true)
                "item_selector" -> ToggleItem(uid, R.drawable.checked_filled, itemSelectorLabel, pk, true)
                "play_next" -> ToggleItem(uid, R.drawable.play_skip_forward, playNextLabel, pk, true)
                "enqueue" -> ToggleItem(uid, R.drawable.enqueue, enqueueLabel, pk, true)
                "add_to_favorite" -> ToggleItem(uid, R.drawable.heart, addToFavoriteLabel, pk, true)
                "add_to_playlist" -> ToggleItem(uid, R.drawable.add_in_playlist, addToPlaylistLabel, pk, true)
                "import_menu" -> ToggleItem(uid, R.drawable.import_outline, importMenuLabel, pk, true)
                "export_dialog" -> ToggleItem(uid, R.drawable.export_outline, exportDialogLabel, pk, true)
                "export_cache" -> ToggleItem(uid, R.drawable.export_outline, exportCacheLabel, pk, true)
                "smart_trash" -> ToggleItem(uid, R.drawable.trash, smartTrashLabel, pk, true)
                "sync_ytm_likes" -> ToggleItem(uid, R.drawable.sync, syncLabel, pk, true)
                else -> null
            }
        }.filterNotNull()

        ToggleListDialog(
            frameTitle = dialogTitle,
            onDismiss = ::hideDialog,
            bodyHeader = {
                SettingsDialogTabRow(
                    labels = tabs.map { stringResource(it.first.textId) },
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { selectedTabIndex = it },
                )
                Spacer(modifier = Modifier.fillMaxWidth().height(8.dp).testTag("toggleListDialogTabGap"))
            },
            items = items, lazyListState = lazyListState, reorderableState = reorderableState,
            enforceMinOneChecked = false,
            lockedCheckedIds = currentLockedIds,
            checkedStatesOverride = items.map { currentWorkingToggles[it.id.removePrefix("${tabPrefix}_")] ?: true },
            onCheckedChange = { index, newValue ->
                val id = items[index].id.removePrefix("${tabPrefix}_")
                val m = workingToggles[currentTab]?.toMutableMap() ?: return@ToggleListDialog
                m[id] = newValue
                workingToggles = workingToggles.toMutableMap().apply { this[currentTab] = m }
            },
            onReset = {
                val available = tabAvailableIds[currentTab] ?: allButtonIds
                val m = available.associateWith { true }.toMutableMap()
                workingToggles = workingToggles.toMutableMap().apply { this[currentTab] = m }
                workingOrders = workingOrders.toMutableMap().apply { this[currentTab] = available.toMutableList() }
            },
            onCancel = { hideDialog() },
            onConfirm = {
                val edit = prefs.edit()
                tabs.forEach { (tab, key) ->
                    val tp = getTabPrefix(tab)
                    val order = workingOrders[tab] ?: return@forEach
                    val toggles = workingToggles[tab] ?: return@forEach

                    toggles.forEach { (id, isChecked) ->
                        edit.putBoolean("${tp}_ts_$id", isChecked)
                    }

                    // Always save the full order to preserve user ordering
                    edit.putString(key, serializeOrder(order))
                }
                edit.apply()
                Toaster.s(R.string.toast_preference_saved); hideDialog()
            }
        )
    }

    fun reset(context: Context) {
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        val tabs = listOf(
            BuiltInPlaylist.All to homeSongsToolbarOrderKey,
            BuiltInPlaylist.Favorites to homeSongsFavoritesToolbarOrderKey,
            BuiltInPlaylist.Disliked to homeSongsDislikedToolbarOrderKey,
            BuiltInPlaylist.Offline to homeSongsOfflineToolbarOrderKey,
            BuiltInPlaylist.Downloaded to homeSongsDownloadedToolbarOrderKey,
            BuiltInPlaylist.Top to homeSongsTopToolbarOrderKey,
            BuiltInPlaylist.OnDevice to homeSongsOnDeviceToolbarOrderKey
        )
        tabs.forEach { (tab, key) ->
            val tp = getTabPrefix(tab)
            val available = tabAvailableIds[tab] ?: allButtonIds
            available.forEach { id ->
                edit.putBoolean("${tp}_ts_$id", true)
            }
            edit.putString(key, serializeOrder(available))
        }
        edit.apply()
    }
}
