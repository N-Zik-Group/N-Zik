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

object LocalPlaylistToolbarSettingsDialog : Dialog {

    val allButtonIds = listOf(
        "pin", "position_lock", "match", "renumber",
        "download_all", "delete_downloads",
        "item_selector",
        "play_next", "enqueue", "add_to_favorite", "add_to_playlist",
        "sync", "listen_on_yt",
        "import_menu", "rename", "delete", "export",
        "thumbnail_picker", "reset_thumbnail", "reset_cache"
    )

    override val dialogTitle: String @Composable get() = stringResource(R.string.playlists) + " - Toolbar"
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

        var workingOrder by remember { mutableStateOf(parseOrder(prefs.getString(localPlaylistToolbarOrderKey, "") ?: "").toMutableList()) }
        var workingToggles by remember {
            mutableStateOf(allButtonIds.associateWith { id -> prefs.getBoolean("pl_ts_$id", true) }.toMutableMap())
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

        val positionLockLabel = stringResource(R.string.info_lock_unlock_reorder_songs)
        val downloadAllLabel = stringResource(R.string.info_download_all_songs)
        val deleteDownloadsLabel = stringResource(R.string.info_remove_all_downloaded_songs)
        val itemSelectorLabel = stringResource(R.string.item_select)
        val playNextLabel = stringResource(R.string.play_next)
        val enqueueLabel = stringResource(R.string.enqueue)
        val addToFavoriteLabel = stringResource(R.string.add_to_favorites)
        val addToPlaylistLabel = stringResource(R.string.add_to_playlist)
        val importMenuLabel = stringResource(R.string.import_playlist)
        val exportDialogLabel = stringResource(R.string.export_playlist)
        val matchLabel = stringResource(R.string.match_album_audio_version)
        val syncLabel = stringResource(R.string.sync)
        val listenOnYTLabel = stringResource(R.string.listen_on_youtube)
        val renameLabel = stringResource(R.string.rename_playlist)
        val deleteLabel = stringResource(R.string.delete)
        val thumbnailLabel = stringResource(R.string.edit_thumbnail)
        val resetThumbnailLabel = stringResource(R.string.reset_thumbnail)
        val resetCacheLabel = stringResource(R.string.title_reset_cache)
        val pinLabel = stringResource(R.string.info_pin_unpin_playlist)
        val renumberLabel = stringResource(R.string.renumber_songs_positions)

        val lockedIds = setOf("pl_pin", "pl_position_lock", "pl_match")

        val items = workingOrder.distinct().map { id ->
            val pk = "pl_ts_$id"
            val uid = "pl_$id"
            when (id) {
                "pin" -> ToggleItem(uid, R.drawable.pin_filled, pinLabel, pk, true)
                "position_lock" -> ToggleItem(uid, R.drawable.locked, positionLockLabel, pk, true)
                "match" -> ToggleItem(uid, R.drawable.alert, matchLabel, pk, true)
                "renumber" -> ToggleItem(uid, R.drawable.position, renumberLabel, pk, true)
                "download_all" -> ToggleItem(uid, R.drawable.downloaded, downloadAllLabel, pk, true)
                "delete_downloads" -> ToggleItem(uid, R.drawable.download, deleteDownloadsLabel, pk, true)
                "item_selector" -> ToggleItem(uid, R.drawable.checked_filled, itemSelectorLabel, pk, true)
                "play_next" -> ToggleItem(uid, R.drawable.play_skip_forward, playNextLabel, pk, true)
                "enqueue" -> ToggleItem(uid, R.drawable.enqueue, enqueueLabel, pk, true)
                "add_to_favorite" -> ToggleItem(uid, R.drawable.heart, addToFavoriteLabel, pk, true)
                "add_to_playlist" -> ToggleItem(uid, R.drawable.add_in_playlist, addToPlaylistLabel, pk, true)
                "sync" -> ToggleItem(uid, R.drawable.sync, syncLabel, pk, true)
                "listen_on_yt" -> ToggleItem(uid, R.drawable.play, listenOnYTLabel, pk, true)
                "import_menu" -> ToggleItem(uid, R.drawable.import_outline, importMenuLabel, pk, true)
                "rename" -> ToggleItem(uid, R.drawable.title_edit, renameLabel, pk, true)
                "delete" -> ToggleItem(uid, R.drawable.trash, deleteLabel, pk, true)
                "export" -> ToggleItem(uid, R.drawable.export_outline, exportDialogLabel, pk, true)
                "thumbnail_picker" -> ToggleItem(uid, R.drawable.image, thumbnailLabel, pk, true)
                "reset_thumbnail" -> ToggleItem(uid, R.drawable.image, resetThumbnailLabel, pk, true)
                "reset_cache" -> ToggleItem(uid, R.drawable.refresh_circle, resetCacheLabel, pk, true)
                else -> null
            }
        }.filterNotNull()

        ToggleListDialog(
            items = items, lazyListState = lazyListState, reorderableState = reorderableState,
            enforceMinOneChecked = false,
            lockedCheckedIds = lockedIds,
            checkedStatesOverride = items.map { workingToggles[it.id.removePrefix("pl_")] ?: true },
            onCheckedChange = { index, newValue ->
                val id = items[index].id.removePrefix("pl_")
                workingToggles = workingToggles.toMutableMap().apply { this[id] = newValue }
            },
            onReset = {
                workingToggles = allButtonIds.associateWith { true }.toMutableMap()
                workingOrder = allButtonIds.toMutableList()
            },
            onCancel = { hideDialog() },
            onConfirm = {
                val edit = prefs.edit()
                workingToggles.forEach { (id, isChecked) -> edit.putBoolean("pl_ts_$id", isChecked) }
                val finalOrder = workingOrder.filter { id -> workingToggles[id] == true }
                edit.putString(localPlaylistToolbarOrderKey, serializeOrder(finalOrder))
                edit.apply(); Toaster.s(R.string.toast_preference_saved); hideDialog()
            }
        )
    }

    fun reset(context: Context) {
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        allButtonIds.forEach { id -> edit.putBoolean("pl_ts_$id", true) }
        edit.putString(localPlaylistToolbarOrderKey, serializeOrder(allButtonIds))
        edit.apply()
    }
}
