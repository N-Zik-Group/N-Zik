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
import app.it.fast4x.rimusic.enums.SongSortBy
import app.it.fast4x.rimusic.enums.OnDeviceSongSortBy
import app.it.fast4x.rimusic.enums.Drawable
import app.it.fast4x.rimusic.utils.*
import app.kreate.android.me.knighthat.utils.Toaster
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.unit.dp
import android.content.Context

object HomeSongsSortSettingsDialog : Dialog {

    private val songSortIds = listOf(
        "Title", "Artist", "AlbumName", "Duration", "PlayCount",
        "PlayTime", "RelativePlayTime", "DateAdded", "DatePlayed", "DateLiked", "Downloaded", "Custom"
    )

    private val onDeviceSortIds = listOf("Title", "DateAdded", "Artist", "Duration", "Album")

    private val topSortIds = listOf("Today", "OneWeek", "OneMonth", "ThreeMonths", "SixMonths", "OneYear", "All")

    private val tabAvailableIds = mapOf(
        BuiltInPlaylist.All to songSortIds,
        BuiltInPlaylist.Favorites to songSortIds.filter { it != "Custom" },
        BuiltInPlaylist.Offline to songSortIds.filter { it != "Custom" && it != "Downloaded" },
        BuiltInPlaylist.Downloaded to songSortIds.filter { it != "Custom" && it != "Downloaded" },
        BuiltInPlaylist.Top to topSortIds,
        BuiltInPlaylist.OnDevice to onDeviceSortIds
    )

    private fun getTabPrefix(tab: BuiltInPlaylist): String = when (tab) {
        BuiltInPlaylist.All -> "all"
        BuiltInPlaylist.Favorites -> "favs"
        BuiltInPlaylist.Offline -> "off"
        BuiltInPlaylist.Downloaded -> "dl"
        BuiltInPlaylist.Top -> "top"
        BuiltInPlaylist.OnDevice -> "dev"
        else -> "x"
    }

    private fun getSortOrderKey(tab: BuiltInPlaylist): String = when (tab) {
        BuiltInPlaylist.All -> homeSongsAllSortMenuOrderKey
        BuiltInPlaylist.Favorites -> homeSongsFavoritesSortMenuOrderKey
        BuiltInPlaylist.Offline -> homeSongsCachedSortMenuOrderKey
        BuiltInPlaylist.Downloaded -> homeSongsDownloadedSortMenuOrderKey
        BuiltInPlaylist.Top -> homeSongsTopSortMenuOrderKey
        BuiltInPlaylist.OnDevice -> homeSongsOnDeviceSortMenuOrderKey
        else -> homeSongsAllSortMenuOrderKey
    }

    override val dialogTitle: String @Composable get() = stringResource(R.string.home_songs_settings) + " - Sort"
    override var isActive: Boolean by mutableStateOf(false)

    private fun parseOrder(s: String, tab: BuiltInPlaylist): List<String> {
        val available = tabAvailableIds[tab] ?: songSortIds
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
        "Title" -> R.drawable.text
        "Artist" -> R.drawable.artist
        "AlbumName" -> R.drawable.album
        "Duration" -> R.drawable.time
        "PlayCount" -> R.drawable.play
        "PlayTime" -> R.drawable.trending
        "RelativePlayTime" -> R.drawable.stats_chart
        "DateAdded" -> R.drawable.time
        "DatePlayed" -> R.drawable.calendar
        "DateLiked" -> R.drawable.heart
        "Downloaded" -> R.drawable.downloaded
        "Custom" -> R.drawable.position
        "Album" -> R.drawable.album
        "Today" -> R.drawable.stat_today
        "OneWeek" -> R.drawable.stat_week
        "OneMonth" -> R.drawable.stat_month
        "ThreeMonths" -> R.drawable.stat_3months
        "SixMonths" -> R.drawable.stat_6months
        "OneYear" -> R.drawable.stat_year
        "All" -> R.drawable.calendar_clear
        else -> R.drawable.text
    }

    @Composable
    private fun getSortLabel(id: String): String = when (id) {
        "Title" -> stringResource(R.string.sort_title)
        "Artist" -> stringResource(R.string.sort_artist)
        "AlbumName" -> stringResource(R.string.sort_album)
        "Duration" -> stringResource(R.string.sort_duration)
        "PlayCount" -> stringResource(R.string.sort_play_count)
        "PlayTime" -> stringResource(R.string.sort_listening_time)
        "RelativePlayTime" -> stringResource(R.string.relative_listening_time)
        "DateAdded" -> stringResource(R.string.sort_date_added)
        "DatePlayed" -> stringResource(R.string.sort_date_played)
        "DateLiked" -> stringResource(R.string.sort_date_liked)
        "Downloaded" -> stringResource(R.string.sort_downloaded)
        "Custom" -> stringResource(R.string.sort_custom_order)
        "Album" -> stringResource(R.string.sort_album)
        "Today" -> stringResource(R.string.today)
        "OneWeek" -> stringResource(R.string._1_week)
        "OneMonth" -> stringResource(R.string._1_month)
        "ThreeMonths" -> stringResource(R.string._3_month)
        "SixMonths" -> stringResource(R.string._6_month)
        "OneYear" -> stringResource(R.string._1_year)
        "All" -> stringResource(R.string.all)
        else -> id
    }

    @Composable
    override fun DialogBody() {
        val ctx = LocalContext.current
        val prefs = remember { ctx.getSharedPreferences("preferences", Context.MODE_PRIVATE) }

        val tabs = listOf(
            BuiltInPlaylist.All,
            BuiltInPlaylist.Favorites,
            BuiltInPlaylist.Offline,
            BuiltInPlaylist.Downloaded,
            BuiltInPlaylist.Top,
            BuiltInPlaylist.OnDevice
        )

        var selectedTabIndex by remember { mutableStateOf(0) }

        var workingOrders by remember {
            mutableStateOf(
                tabs.associate { tab ->
                    val key = getSortOrderKey(tab)
                    tab to parseOrder(prefs.getString(key, "") ?: "", tab).toMutableList()
                }.toMutableMap()
            )
        }

        var workingToggles by remember {
            mutableStateOf(
                tabs.associate { tab ->
                    val tp = getTabPrefix(tab)
                    val available = tabAvailableIds[tab] ?: songSortIds
                    tab to available.associateWith { id ->
                        prefs.getBoolean("${tp}_sort_${id}_visible", true)
                    }.toMutableMap()
                }.toMutableMap()
            )
        }

        val currentTab = tabs[selectedTabIndex]
        val currentWorkingOrder = workingOrders[currentTab] ?: emptyList()
        val currentWorkingToggles = workingToggles[currentTab] ?: emptyMap()
        val tabPrefix = getTabPrefix(currentTab)

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
            val pk = "${tabPrefix}_sort_${id}_visible"
            val uid = "${tabPrefix}_$id"
            ToggleItem(uid, getSortIcon(id), getSortLabel(id), pk, true)
        }

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
                        text = { Text(stringResource(tab.textId)) }
                    )
                }
            }

            ToggleListDialog(
                items = items, lazyListState = lazyListState, reorderableState = reorderableState,
                enforceMinOneChecked = true,
                checkedStatesOverride = items.map { currentWorkingToggles[it.id.removePrefix("${tabPrefix}_")] ?: true },
                onCheckedChange = { index, newValue ->
                    val id = items[index].id.removePrefix("${tabPrefix}_")
                    val m = (workingToggles[currentTab] ?: emptyMap()).toMutableMap()
                    m[id] = newValue
                    workingToggles = workingToggles.toMutableMap().apply { this[currentTab] = m }
                },
                onReset = {
                    val available = tabAvailableIds[currentTab] ?: songSortIds
                    val m = available.associateWith { true }.toMutableMap()
                    workingToggles = workingToggles.toMutableMap().apply { this[currentTab] = m }
                    workingOrders = workingOrders.toMutableMap().apply { this[currentTab] = available.toMutableList() }
                },
                onCancel = { hideDialog() },
                onConfirm = {
                    val edit = prefs.edit()
                    tabs.forEach { tab ->
                        val tp = getTabPrefix(tab)
                        val key = getSortOrderKey(tab)
                        val order = workingOrders[tab] ?: emptyList()
                        val toggles = workingToggles[tab] ?: emptyMap()

                        toggles.forEach { (id, isChecked) ->
                            edit.putBoolean("${tp}_sort_${id}_visible", isChecked)
                        }

                        val finalOrder = order.filter { id -> toggles[id] == true }
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
            BuiltInPlaylist.All, BuiltInPlaylist.Favorites, BuiltInPlaylist.Offline,
            BuiltInPlaylist.Downloaded, BuiltInPlaylist.Top, BuiltInPlaylist.OnDevice
        )
        tabs.forEach { tab ->
            val tp = getTabPrefix(tab)
            val key = getSortOrderKey(tab)
            val available = tabAvailableIds[tab] ?: songSortIds
            available.forEach { id ->
                // "Downloaded" sort should not be visible in Downloaded/Offline tabs
                val isVisible = if (id == "Downloaded" && (tab == BuiltInPlaylist.Downloaded || tab == BuiltInPlaylist.Offline)) {
                    false
                } else {
                    true
                }
                edit.putBoolean("${tp}_sort_${id}_visible", isVisible)
            }
            edit.putString(key, serializeOrder(available))
        }
        edit.apply()
    }
}
