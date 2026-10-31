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

import app.it.fast4x.rimusic.enums.BuiltInPlaylist
import app.it.fast4x.rimusic.utils.homeSongsAllSortMenuOrderKey
import app.it.fast4x.rimusic.utils.homeSongsCachedSortMenuOrderKey
import app.it.fast4x.rimusic.utils.homeSongsDislikedSortMenuOrderKey
import app.it.fast4x.rimusic.utils.homeSongsDownloadedSortMenuOrderKey
import app.it.fast4x.rimusic.utils.homeSongsFavoritesSortMenuOrderKey
import app.it.fast4x.rimusic.utils.homeSongsOnDeviceSortMenuOrderKey
import app.it.fast4x.rimusic.utils.homeSongsTopSortMenuOrderKey
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.SettingsNestedOption

object HomeSongsSortSettingsDialog : ContentFramedDialog {

    /** Exact searchable options from the same definitions used by this dialog. */
    internal fun searchOptions(): List<SettingsNestedOption> = listOf(BuiltInPlaylist.All, BuiltInPlaylist.Favorites, BuiltInPlaylist.Disliked, BuiltInPlaylist.Offline, BuiltInPlaylist.Downloaded, BuiltInPlaylist.Top, BuiltInPlaylist.OnDevice)
        .flatMapIndexed { index, tab -> tabAvailableIds.getValue(tab).map { id ->
            SettingsNestedOption("${getTabPrefix(tab)}_$id", requireNotNull(getSortLabelRes(id)), index, tab.textId)
        } }


    private val songSortIds = listOf(
        "Title", "Artist", "AlbumName", "Duration", "PlayCount",
        "PlayTime", "RelativePlayTime", "DateAdded", "DatePlayed", "DateLiked", "Downloaded", "Custom"
    )

    private val onDeviceSortIds = listOf("Title", "DateAdded", "Artist", "Duration", "Album")

    private val topSortIds = listOf("Today", "OneWeek", "OneMonth", "ThreeMonths", "SixMonths", "OneYear", "All")

    // Match each home tab's sort choices, including Custom and the cached/downloaded exclusions.
    internal val tabAvailableIds = mapOf(
        BuiltInPlaylist.All to songSortIds,
        BuiltInPlaylist.Favorites to songSortIds,
        BuiltInPlaylist.Offline to songSortIds.filter { it != "Downloaded" },
        BuiltInPlaylist.Downloaded to songSortIds.filter { it != "Downloaded" },
        BuiltInPlaylist.Top to topSortIds,
        BuiltInPlaylist.OnDevice to onDeviceSortIds,
        BuiltInPlaylist.Disliked to songSortIds
    )

    private fun getTabPrefix(tab: BuiltInPlaylist): String = when (tab) {
        BuiltInPlaylist.All -> "all"
        BuiltInPlaylist.Favorites -> "favs"
        BuiltInPlaylist.Offline -> "off"
        BuiltInPlaylist.Downloaded -> "dl"
        BuiltInPlaylist.Top -> "top"
        BuiltInPlaylist.OnDevice -> "dev"
        BuiltInPlaylist.Disliked -> "disliked"
        else -> "x"
    }

    private fun getSortOrderKey(tab: BuiltInPlaylist): String = when (tab) {
        BuiltInPlaylist.All -> homeSongsAllSortMenuOrderKey
        BuiltInPlaylist.Favorites -> homeSongsFavoritesSortMenuOrderKey
        BuiltInPlaylist.Offline -> homeSongsCachedSortMenuOrderKey
        BuiltInPlaylist.Downloaded -> homeSongsDownloadedSortMenuOrderKey
        BuiltInPlaylist.Top -> homeSongsTopSortMenuOrderKey
        BuiltInPlaylist.OnDevice -> homeSongsOnDeviceSortMenuOrderKey
        BuiltInPlaylist.Disliked -> homeSongsDislikedSortMenuOrderKey
        else -> homeSongsAllSortMenuOrderKey
    }

    override val dialogTitle: String @Composable get() = stringResource(R.string.home_songs_settings) + " - Sort"
    override var isActive: Boolean by mutableStateOf(false)

    // Preserve saved known IDs and append newly available options.
    internal fun parseOrder(s: String, tab: BuiltInPlaylist): List<String> {
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

    private fun getSortLabelRes(id: String): Int? = when (id) {
        "Title" -> R.string.sort_title
        "Artist" -> R.string.sort_artist
        "AlbumName" -> R.string.sort_album
        "Duration" -> R.string.sort_duration
        "PlayCount" -> R.string.sort_play_count
        "PlayTime" -> R.string.sort_listening_time
        "RelativePlayTime" -> R.string.relative_listening_time
        "DateAdded" -> R.string.sort_date_added
        "DatePlayed" -> R.string.sort_date_played
        "DateLiked" -> R.string.sort_date_liked
        "Downloaded" -> R.string.sort_downloaded
        "Custom" -> R.string.sort_custom_order
        "Album" -> R.string.sort_album
        "Today" -> R.string.today
        "OneWeek" -> R.string._1_week
        "OneMonth" -> R.string._1_month
        "ThreeMonths" -> R.string._3_month
        "SixMonths" -> R.string._6_month
        "OneYear" -> R.string._1_year
        "All" -> R.string.all
        else -> null
    }

    @Composable
    private fun getSortLabel(id: String): String = getSortLabelRes(id)?.let { stringResource(it) } ?: id

    @Composable
    override fun DialogBody() {
        val ctx = LocalContext.current
        val prefs = remember { ctx.getSharedPreferences("preferences", Context.MODE_PRIVATE) }

        val tabs = listOf(
            BuiltInPlaylist.All,
            BuiltInPlaylist.Favorites,
            BuiltInPlaylist.Disliked,
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

        ToggleListDialog(
            frameTitle = dialogTitle,
            onDismiss = ::hideDialog,
            bodyHeader = {
                SettingsDialogTabRow(
                    labels = tabs.map { stringResource(it.textId) },
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { selectedTabIndex = it },
                )
                Spacer(modifier = Modifier.fillMaxWidth().height(8.dp).testTag("toggleListDialogTabGap"))
            },
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

    fun reset(context: Context) {
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        val tabs = listOf(
            BuiltInPlaylist.All, BuiltInPlaylist.Favorites, BuiltInPlaylist.Disliked,
            BuiltInPlaylist.Offline, BuiltInPlaylist.Downloaded, BuiltInPlaylist.Top,
            BuiltInPlaylist.OnDevice
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
