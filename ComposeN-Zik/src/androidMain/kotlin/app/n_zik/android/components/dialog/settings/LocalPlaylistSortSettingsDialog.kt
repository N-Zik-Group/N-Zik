package app.n_zik.android.components.dialog.settings

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.Dialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.it.fast4x.rimusic.utils.*
import app.kreate.android.me.knighthat.utils.Toaster
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState
import android.content.Context

object LocalPlaylistSortSettingsDialog : Dialog {

    private val sortIds = listOf(
        "Title", "Artist", "Album", "ArtistAndAlbum", "Duration",
        "PlayCount", "PlayTime", "RelativePlayTime", "DateAdded",
        "DatePlayed", "DateLiked", "AlbumYear", "Downloaded", "Custom"
    )

    override val dialogTitle: String @Composable get() = stringResource(R.string.playlists) + " - Sort"
    override var isActive: Boolean by mutableStateOf(false)

    private fun parseOrder(s: String): List<String> {
        if (s.isBlank()) return sortIds
        return try {
            val a = JSONArray(s)
            val savedIds = mutableListOf<String>(); val seen = mutableSetOf<String>()
            for (i in 0 until a.length()) { val id = a.getString(i); if (id in sortIds && seen.add(id)) savedIds.add(id) }
            savedIds + sortIds.filter { it !in savedIds }
        } catch (_: Exception) { sortIds }
    }
    private fun serializeOrder(order: List<String>): String { val a = JSONArray(); order.forEach { a.put(it) }; return a.toString() }

    private fun getSortIcon(id: String): Int = when (id) {
        "Title" -> R.drawable.text
        "Artist" -> R.drawable.artist
        "Album" -> R.drawable.album
        "ArtistAndAlbum" -> R.drawable.artist
        "Duration" -> R.drawable.time
        "PlayCount" -> R.drawable.play
        "PlayTime" -> R.drawable.trending
        "RelativePlayTime" -> R.drawable.stats_chart
        "DateAdded" -> R.drawable.time
        "DatePlayed" -> R.drawable.up_right_arrow
        "DateLiked" -> R.drawable.heart
        "AlbumYear" -> R.drawable.calendar
        "Downloaded" -> R.drawable.downloaded
        "Custom" -> R.drawable.position
        else -> R.drawable.text
    }
    @Composable
    private fun getSortLabel(id: String): String = when (id) {
        "Title" -> stringResource(R.string.sort_title)
        "Artist" -> stringResource(R.string.sort_artist)
        "Album" -> stringResource(R.string.sort_album)
        "ArtistAndAlbum" -> "${stringResource(R.string.sort_artist)}, ${stringResource(R.string.sort_album)}"
        "Duration" -> stringResource(R.string.sort_duration)
        "PlayCount" -> stringResource(R.string.sort_play_count)
        "PlayTime" -> stringResource(R.string.sort_listening_time)
        "RelativePlayTime" -> stringResource(R.string.relative_listening_time)
        "DateAdded" -> stringResource(R.string.sort_date_added)
        "DatePlayed" -> stringResource(R.string.sort_date_played)
        "DateLiked" -> stringResource(R.string.sort_date_liked)
        "AlbumYear" -> stringResource(R.string.sort_album_year)
        "Downloaded" -> stringResource(R.string.sort_downloaded)
        "Custom" -> stringResource(R.string.sort_custom_order)
        else -> id
    }

    @Composable
    override fun DialogBody() {
        val ctx = LocalContext.current
        val prefs = remember { ctx.getSharedPreferences("preferences", Context.MODE_PRIVATE) }

        var workingOrder by remember { mutableStateOf(parseOrder(prefs.getString(localPlaylistSortMenuOrderKey, "") ?: "").toMutableList()) }
        var workingToggles by remember {
            mutableStateOf(sortIds.associateWith { id -> prefs.getBoolean("pl_sort_${id}_visible", true) }.toMutableMap())
        }

        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val o = workingOrder.toMutableList()
            val fi = o.indexOfFirst { "pl_$it" == from.key }
            val ti = o.indexOfFirst { "pl_$it" == to.key }
            if (fi != -1 && ti != -1) {
                val item = o.removeAt(fi)
                o.add(ti, item)
                workingOrder = o
            }
        }
        val items = workingOrder.distinct().map { id ->
            ToggleItem("pl_$id", getSortIcon(id), getSortLabel(id), "pl_sort_${id}_visible", true)
        }

        ToggleListDialog(
            items = items, lazyListState = lazyListState, reorderableState = reorderableState, enforceMinOneChecked = true,
            checkedStatesOverride = items.map { workingToggles[it.id.removePrefix("pl_")] ?: true },
            onCheckedChange = { index, newValue ->
                val id = items[index].id.removePrefix("pl_")
                workingToggles = workingToggles.toMutableMap().apply { this[id] = newValue }
            },
            onReset = {
                workingToggles = sortIds.associateWith { true }.toMutableMap(); workingOrder = sortIds.toMutableList()
            },
            onCancel = { hideDialog() },
            onConfirm = {
                val edit = prefs.edit()
                workingToggles.forEach { (id, isChecked) -> edit.putBoolean("pl_sort_${id}_visible", isChecked) }
                edit.putString(localPlaylistSortMenuOrderKey, serializeOrder(workingOrder.filter { id -> workingToggles[id] == true }))
                edit.apply(); Toaster.s(R.string.toast_preference_saved); hideDialog()
            }
        )
    }

    fun reset(context: Context) {
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        sortIds.forEach { id -> edit.putBoolean("pl_sort_${id}_visible", true) }
        edit.putString(localPlaylistSortMenuOrderKey, serializeOrder(sortIds))
        edit.apply()
    }
}
