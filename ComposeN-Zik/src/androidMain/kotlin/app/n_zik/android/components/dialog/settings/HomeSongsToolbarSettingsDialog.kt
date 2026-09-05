package app.n_zik.android.components.dialog.settings

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.Dialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.it.fast4x.rimusic.enums.BuiltInPlaylist
import app.it.fast4x.rimusic.utils.*
import app.kreate.android.me.knighthat.utils.Toaster
import org.json.JSONArray
import timber.log.Timber
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.unit.dp
import android.content.Context
object HomeSongsToolbarSettingsDialog : Dialog {

    val allButtonIds = listOf(
        "sort", "position_lock", "match", "search", "sync_ytm_likes", "locator",
        "download_all", "delete_downloads",
        "shuffle", "smart_shuffle", "item_selector",
        "play_next", "enqueue", "add_to_favorite", "add_to_playlist",
        "import_menu", "export_dialog", "export_cache", "smart_trash"
    )

    val tabAvailableIds = mapOf(
        BuiltInPlaylist.All to allButtonIds.filter { it != "export_cache" },
        BuiltInPlaylist.Favorites to allButtonIds.filter { it != "export_cache" },
        BuiltInPlaylist.Offline to allButtonIds.filter { it != "import_menu" && it != "sync_ytm_likes" },
        BuiltInPlaylist.Downloaded to allButtonIds.filter { it != "import_menu" && it != "sync_ytm_likes" },
        BuiltInPlaylist.Top to allButtonIds.filter { it != "import_menu" && it != "position_lock" && it != "export_cache" && it != "sync_ytm_likes" },
        BuiltInPlaylist.OnDevice to allButtonIds.filter { it !in setOf("import_menu", "export_dialog", "export_cache", "smart_trash", "match", "download_all", "delete_downloads", "sync_ytm_likes") },
        BuiltInPlaylist.Disliked to allButtonIds.filter { it != "export_cache" && it != "sync_ytm_likes" && it != "import_menu" }
    )

    private val lockedIds = setOf("sort", "position_lock", "match", "sync_ytm_likes")

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

        val sortLabel = stringResource(R.string.sorting_order)
        val topSortLabel = stringResource(R.string.statistics)
        val positionLockLabel = stringResource(R.string.info_lock_unlock_reorder_songs)
        val downloadAllLabel = stringResource(R.string.info_download_all_songs)
        val deleteDownloadsLabel = stringResource(R.string.info_remove_all_downloaded_songs)
        val searchLabel = stringResource(R.string.search)
        val locatorLabel = stringResource(R.string.info_find_the_song_that_is_playing)
        val shuffleLabel = stringResource(R.string.info_shuffle)
        val smartShuffleLabel = stringResource(R.string.info_smart_recommendation)
        val itemSelectorLabel = stringResource(R.string.item_select)
        val playNextLabel = stringResource(R.string.play_next)
        val enqueueLabel = stringResource(R.string.enqueue)
        val addToFavoriteLabel = stringResource(R.string.add_to_favorites)
        val addToPlaylistLabel = stringResource(R.string.add_to_playlist)
        val importMenuLabel = stringResource(R.string.import_playlist)
        val exportDialogLabel = stringResource(R.string.export_playlist)
        val exportCacheLabel = stringResource(R.string.export_cached)
        val smartTrashLabel = stringResource(R.string.smart_trash)
        val matchLabel = stringResource(R.string.match_album_audio_version)
        val syncLabel = stringResource(R.string.autosync_likes)

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

        Column {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                divider = {},
                edgePadding = 8.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(stringResource(tab.first.textId)) }
                    )
                }
            }

            ToggleListDialog(
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
