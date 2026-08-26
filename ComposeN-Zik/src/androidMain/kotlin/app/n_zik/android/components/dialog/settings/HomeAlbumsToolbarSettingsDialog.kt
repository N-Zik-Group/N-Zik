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
import app.it.fast4x.rimusic.utils.*
import app.kreate.android.me.knighthat.utils.Toaster
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.unit.dp
import android.content.Context

object HomeAlbumsToolbarSettingsDialog : Dialog {

    val allButtonIds = listOf(
        "sort", "position_lock", "sync", "search", "randomizer", "shuffle", "item_selector", "play_next", "enqueue", "add_to_playlist", "export_dialog", "item_size"
    )

    private val lockedIds = setOf("sort", "sync", "position_lock")

    private fun getTabPrefix(tab: String): String = when (tab) {
        "favs" -> "favs"
        else -> "all"
    }

    override val dialogTitle: String @Composable get() = stringResource(R.string.albums) + " - Toolbar"
    override var isActive: Boolean by mutableStateOf(false)

    private fun parseOrder(s: String): List<String> {
        if (s.isBlank()) return allButtonIds
        return try {
            val a = JSONArray(s)
            val savedIds = mutableListOf<String>()
            val seen = mutableSetOf<String>()
            for (i in 0 until a.length()) {
                val id = a.getString(i)
                if (id in allButtonIds && seen.add(id)) savedIds.add(id)
            }
            val missingIds = allButtonIds.filter { it !in savedIds }
            savedIds + missingIds
        } catch (_: Exception) { allButtonIds }
    }

    private fun serializeOrder(order: List<String>): String { val a = JSONArray(); order.forEach { a.put(it) }; return a.toString() }

    @Composable
    override fun DialogBody() {
        val ctx = LocalContext.current
        val prefs = remember { ctx.getSharedPreferences("preferences", Context.MODE_PRIVATE) }

        val tabs = listOf(
            "lib" to homeAlbumsLibraryToolbarOrderKey,
            "favs" to homeAlbumsFavoritesToolbarOrderKey
        )

        var selectedTabIndex by remember { mutableStateOf(0) }

        var workingOrders by remember {
            mutableStateOf(
                tabs.associate { (tab, key) -> tab to parseOrder(prefs.getString(key, "") ?: "").toMutableList() }.toMutableMap()
            )
        }

        var workingToggles by remember {
            mutableStateOf(
                tabs.associate { (tab, _) ->
                    val tp = getTabPrefix(tab)
                    tab to allButtonIds.associateWith { id ->
                        prefs.getBoolean("${tp}_alb_$id", true)
                    }.toMutableMap()
                }.toMutableMap()
            )
        }

        val currentTabKey = tabs[selectedTabIndex].first
        val currentWorkingOrder = workingOrders[currentTabKey]!!
        val currentWorkingToggles = workingToggles[currentTabKey]!!
        val tabPrefix = getTabPrefix(currentTabKey)

        val sortLabel = stringResource(R.string.sorting_order)
        val positionLockLabel = stringResource(R.string.info_lock_unlock_reorder_songs)
        val syncLabel = stringResource(R.string.autosync_albums)
        val searchLabel = stringResource(R.string.search)
        val randomizerLabel = stringResource(R.string.randomizer)
        val shuffleLabel = stringResource(R.string.info_shuffle)
        val itemSelectorLabel = stringResource(R.string.item_select)
        val playNextLabel = stringResource(R.string.play_next)
        val enqueueLabel = stringResource(R.string.enqueue)
        val itemSizeLabel = stringResource(R.string.size)

        val currentLockedIds = lockedIds.map { "${tabPrefix}_$it" }.toSet()

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

        val items = currentWorkingOrder.distinct().map { id ->
            val pk = "${tabPrefix}_alb_$id"
            val uid = "${tabPrefix}_$id"
            when (id) {
                "sort" -> ToggleItem(uid, R.drawable.arrow_up, sortLabel, pk, true)
                "position_lock" -> ToggleItem(uid, R.drawable.locked, positionLockLabel, pk, true)
                "sync" -> ToggleItem(uid, R.drawable.sync, syncLabel, pk, true)
                "search" -> ToggleItem(uid, R.drawable.search_circle, searchLabel, pk, true)
                "randomizer" -> ToggleItem(uid, R.drawable.dice, randomizerLabel, pk, true)
                "shuffle" -> ToggleItem(uid, R.drawable.shuffle, shuffleLabel, pk, true)
                "item_selector" -> ToggleItem(uid, R.drawable.checked_filled, itemSelectorLabel, pk, true)
                "play_next" -> ToggleItem(uid, R.drawable.play_skip_forward, playNextLabel, pk, true)
                "enqueue" -> ToggleItem(uid, R.drawable.enqueue, enqueueLabel, pk, true)
                "item_size" -> ToggleItem(uid, R.drawable.resize, itemSizeLabel, pk, true)
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
                        text = { Text(stringResource(when (tab.first) { "favs" -> R.string.favorites; else -> R.string.all })) }
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
                    val m = workingToggles[currentTabKey]!!.toMutableMap()
                    m[id] = newValue
                    workingToggles = workingToggles.toMutableMap().apply { this[currentTabKey] = m }
                },
                onReset = {
                    val m = allButtonIds.associateWith { true }.toMutableMap()
                    workingToggles = workingToggles.toMutableMap().apply { this[currentTabKey] = m }
                    workingOrders = workingOrders.toMutableMap().apply { this[currentTabKey] = allButtonIds.toMutableList() }
                },
                onCancel = { hideDialog() },
                onConfirm = {
                    val edit = prefs.edit()
                    tabs.forEach { (tab, key) ->
                        val tp = getTabPrefix(tab)
                        val order = workingOrders[tab]!!
                        val toggles = workingToggles[tab]!!
                        
                        toggles.forEach { (id, isChecked) ->
                            edit.putBoolean("${tp}_alb_$id", isChecked)
                        }
                        
                        val finalOrder = order.filter { id ->
                            toggles[id] == true || id in lockedIds
                        }
                        edit.putString(key, serializeOrder(finalOrder))
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
            "favs" to homeAlbumsFavoritesToolbarOrderKey,
            "all" to homeAlbumsToolbarOrderKey
        )
        tabs.forEach { (tab, key) ->
            val tp = getTabPrefix(tab)
            allButtonIds.forEach { id ->
                edit.putBoolean("${tp}_alb_$id", true)
            }
            edit.putString(key, serializeOrder(allButtonIds))
        }
        edit.apply()
    }
}
