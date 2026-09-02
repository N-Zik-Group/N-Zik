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
import app.it.fast4x.rimusic.enums.AlbumSortBy
import app.it.fast4x.rimusic.enums.Drawable
import app.it.fast4x.rimusic.utils.*
import app.kreate.android.me.knighthat.utils.Toaster
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.unit.dp
import android.content.Context

object HomeAlbumsSortSettingsDialog : Dialog {

    private val librarySortIds = listOf("AlbumName", "Artist", "Year", "DateAdded", "PlayCount", "Duration", "Custom")

    private val favoritesSortIds = listOf("AlbumName", "Artist", "Year", "DateAdded", "PlayCount", "Duration")

    private enum class AlbumTab(val textId: Int, val availableIds: List<String>) {
        Library(R.string.library, librarySortIds),
        Favorites(R.string.favorites, favoritesSortIds)
    }

    private val tabs = AlbumTab.entries

    override val dialogTitle: String @Composable get() = stringResource(R.string.home_albums_settings) + " - Sort"
    override var isActive: Boolean by mutableStateOf(false)

    private fun parseOrder(s: String, available: List<String>): List<String> {
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

    private fun getSortIcon(id: String): Int = when (id) {
        "AlbumName" -> R.drawable.text
        "Artist" -> R.drawable.artist
        "Year" -> R.drawable.calendar
        "DateAdded" -> R.drawable.time
        "PlayCount" -> R.drawable.play
        "Duration" -> R.drawable.time
        "Custom" -> R.drawable.position
        else -> R.drawable.text
    }

    @Composable
    private fun getSortLabel(id: String): String = when (id) {
        "AlbumName" -> stringResource(R.string.sort_name)
        "Artist" -> stringResource(R.string.sort_artist)
        "Year" -> stringResource(R.string.sort_album_year)
        "DateAdded" -> stringResource(R.string.sort_date_added)
        "PlayCount" -> stringResource(R.string.sort_play_count)
        "Duration" -> stringResource(R.string.sort_duration)
        "Custom" -> stringResource(R.string.sort_custom_order)
        else -> id
    }

    @Composable
    override fun DialogBody() {
        val ctx = LocalContext.current
        val prefs = remember { ctx.getSharedPreferences("preferences", Context.MODE_PRIVATE) }

        var selectedTabIndex by remember { mutableStateOf(0) }

        var workingOrders by remember {
            mutableStateOf(
                tabs.associate { tab ->
                    val key = if (tab == AlbumTab.Library) homeAlbumsLibrarySortMenuOrderKey else homeAlbumsFavoritesSortMenuOrderKey
                    tab to parseOrder(prefs.getString(key, "") ?: "", tab.availableIds).toMutableList()
                }.toMutableMap()
            )
        }

        var workingToggles by remember {
            mutableStateOf(
                tabs.associate { tab ->
                    val prefix = if (tab == AlbumTab.Library) "alb_lib" else "alb_fav"
                    tab to tab.availableIds.associateWith { id ->
                        prefs.getBoolean("${prefix}_sort_${id}_visible", true)
                    }.toMutableMap()
                }.toMutableMap()
            )
        }

        val currentTab = tabs[selectedTabIndex]
        val prefix = if (currentTab == AlbumTab.Library) "alb_lib" else "alb_fav"
        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val o = (workingOrders[currentTab] ?: return@rememberReorderableLazyListState).toMutableList()
            val fi = o.indexOfFirst { "${prefix}_$it" == from.key }
            val ti = o.indexOfFirst { "${prefix}_$it" == to.key }
            if (fi != -1 && ti != -1) {
                val item = o.removeAt(fi); o.add(ti, item)
                workingOrders = workingOrders.toMutableMap().apply { this[currentTab] = o }
            }
        }

        val items = (workingOrders[currentTab] ?: emptyList()).distinct().map { id ->
            ToggleItem("${prefix}_$id", getSortIcon(id), getSortLabel(id), "${prefix}_sort_${id}_visible", true)
        }

        Column {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex, containerColor = androidx.compose.ui.graphics.Color.Transparent,
                divider = {}, edgePadding = 8.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(selected = selectedTabIndex == index, onClick = { selectedTabIndex = index }, text = { Text(stringResource(tab.textId)) })
                }
            }
            ToggleListDialog(
                items = items, lazyListState = lazyListState, reorderableState = reorderableState,
                enforceMinOneChecked = true,
                checkedStatesOverride = items.map { (workingToggles[currentTab] ?: emptyMap())[it.id.removePrefix("${prefix}_")] ?: true },
                onCheckedChange = { index, newValue ->
                    val id = items[index].id.removePrefix("${prefix}_")
                    val m = (workingToggles[currentTab] ?: emptyMap()).toMutableMap(); m[id] = newValue
                    workingToggles = workingToggles.toMutableMap().apply { this[currentTab] = m }
                },
                onReset = {
                    workingToggles = workingToggles.toMutableMap().apply { this[currentTab] = currentTab.availableIds.associateWith { true }.toMutableMap() }
                    workingOrders = workingOrders.toMutableMap().apply { this[currentTab] = currentTab.availableIds.toMutableList() }
                },
                onCancel = { hideDialog() },
                onConfirm = {
                    val edit = prefs.edit()
                    tabs.forEach { tab ->
                        val prefix2 = if (tab == AlbumTab.Library) "alb_lib" else "alb_fav"
                        val key = if (tab == AlbumTab.Library) homeAlbumsLibrarySortMenuOrderKey else homeAlbumsFavoritesSortMenuOrderKey
                        (workingToggles[tab] ?: emptyMap()).forEach { (id, isChecked) -> edit.putBoolean("${prefix2}_sort_${id}_visible", isChecked) }
                        val finalOrder = (workingOrders[tab] ?: emptyList()).filter { id -> (workingToggles[tab] ?: emptyMap())[id] == true }
                        edit.putString(key, serializeOrder(finalOrder))
                    }
                    edit.apply(); Toaster.s(R.string.toast_preference_saved); hideDialog()
                }
            )
        }
    }

    fun reset(context: Context) {
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        tabs.forEach { tab ->
            val prefix2 = if (tab == AlbumTab.Library) "alb_lib" else "alb_fav"
            val key = if (tab == AlbumTab.Library) homeAlbumsLibrarySortMenuOrderKey else homeAlbumsFavoritesSortMenuOrderKey
            tab.availableIds.forEach { id -> edit.putBoolean("${prefix2}_sort_${id}_visible", true) }
            edit.putString(key, serializeOrder(tab.availableIds))
        }
        edit.apply()
    }
}
