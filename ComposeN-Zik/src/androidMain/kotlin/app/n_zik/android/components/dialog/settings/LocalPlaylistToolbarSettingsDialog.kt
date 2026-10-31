package app.n_zik.android.components.dialog.settings

import android.content.Context
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.SettingsNestedOption
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.it.fast4x.rimusic.utils.localPlaylistToolbarOrderKey
import app.kreate.android.me.knighthat.utils.Toaster
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState

object LocalPlaylistToolbarSettingsDialog : ContentFramedDialog {

    /** Exact searchable options from the same definitions used by this dialog. */
    internal fun searchOptions(): List<SettingsNestedOption> = allButtonIds.map { SettingsNestedOption("pl_$it", searchLabelResources.getValue(it)) }

    private val searchLabelResources = mapOf(
        "insertion_position" to R.string.playlist_insertion_position,
        "pin" to R.string.info_pin_unpin_playlist,
        "search" to R.string.search,
        "position_lock" to R.string.info_lock_unlock_reorder_songs,
        "match" to R.string.match_album_audio_version,
        "renumber" to R.string.renumber_songs_positions,
        "download_all" to R.string.info_download_all_songs,
        "delete_downloads" to R.string.info_remove_all_downloaded_songs,
        "item_selector" to R.string.item_select,
        "play_next" to R.string.play_next,
        "enqueue" to R.string.enqueue,
        "add_to_favorite" to R.string.add_to_favorites,
        "add_to_playlist" to R.string.add_to_playlist,
        "sync" to R.string.sync,
        "refresh_on_open" to R.string.playlist_refresh_on_open,
        "listen_on_yt" to R.string.listen_on_youtube,
        "import_menu" to R.string.import_playlist,
        "rename" to R.string.rename_playlist,
        "delete" to R.string.delete,
        "export" to R.string.export_playlist,
        "thumbnail_picker" to R.string.edit_thumbnail,
        "reset_thumbnail" to R.string.reset_thumbnail,
        "update" to R.string.update,
    )


    val allButtonIds = listOf(
        "insertion_position",
        "pin", "search", "position_lock", "match", "renumber",
        "download_all", "delete_downloads",
        "item_selector",
        "play_next", "enqueue", "add_to_favorite", "add_to_playlist",
        "sync", "refresh_on_open", "listen_on_yt",
        "import_menu", "rename", "delete", "export",
        "thumbnail_picker", "reset_thumbnail", "update"
    )

    override val dialogTitle: String @Composable get() =
        stringResource(R.string.local_playlist_sort) + " - " + stringResource(R.string.toolbar)
    override var isActive: Boolean by mutableStateOf(false)

    /** Restores saved ordering while retaining the insertion action's original leading position. */
    fun parseOrder(s: String): List<String> {
        if (s.isBlank()) return allButtonIds
        return try {
            val a = JSONArray(s)
            val savedIds = mutableListOf<String>()
            val seen = mutableSetOf<String>()
            for (i in 0 until a.length()) {
                val id = a.getString(i)
                if (id in allButtonIds && seen.add(id)) savedIds.add(id)
            }
            if ("refresh_on_open" !in savedIds && "sync" in savedIds) {
                savedIds.add(savedIds.indexOf("sync") + 1, "refresh_on_open")
            }
            val missingIds = allButtonIds.filter { it !in savedIds && it != "insertion_position" }
            val leadingIds = if ("insertion_position" in savedIds) emptyList() else listOf("insertion_position")
            leadingIds + savedIds + missingIds
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

        val positionLockLabel = stringResource(searchLabelResources.getValue("position_lock"))
        val downloadAllLabel = stringResource(searchLabelResources.getValue("download_all"))
        val deleteDownloadsLabel = stringResource(searchLabelResources.getValue("delete_downloads"))
        val itemSelectorLabel = stringResource(searchLabelResources.getValue("item_selector"))
        val playNextLabel = stringResource(searchLabelResources.getValue("play_next"))
        val enqueueLabel = stringResource(searchLabelResources.getValue("enqueue"))
        val addToFavoriteLabel = stringResource(searchLabelResources.getValue("add_to_favorite"))
        val addToPlaylistLabel = stringResource(searchLabelResources.getValue("add_to_playlist"))
        val importMenuLabel = stringResource(searchLabelResources.getValue("import_menu"))
        val exportDialogLabel = stringResource(searchLabelResources.getValue("export"))
        val matchLabel = stringResource(searchLabelResources.getValue("match"))
        val syncLabel = stringResource(searchLabelResources.getValue("sync"))
        val refreshOnOpenLabel = stringResource(searchLabelResources.getValue("refresh_on_open"))
        val listenOnYTLabel = stringResource(searchLabelResources.getValue("listen_on_yt"))
        val renameLabel = stringResource(searchLabelResources.getValue("rename"))
        val deleteLabel = stringResource(searchLabelResources.getValue("delete"))
        val thumbnailLabel = stringResource(searchLabelResources.getValue("thumbnail_picker"))
        val resetThumbnailLabel = stringResource(searchLabelResources.getValue("reset_thumbnail"))
        val updateLabel = stringResource(searchLabelResources.getValue("update"))
        val pinLabel = stringResource(searchLabelResources.getValue("pin"))
        val searchLabel = stringResource(searchLabelResources.getValue("search"))
        val renumberLabel = stringResource(searchLabelResources.getValue("renumber"))
        val insertionPositionLabel = stringResource(searchLabelResources.getValue("insertion_position"))

        val lockedIds = setOf("pl_pin", "pl_position_lock", "pl_match")

        val items = workingOrder.distinct().map { id ->
            val pk = "pl_ts_$id"
            val uid = "pl_$id"
            when (id) {
                "insertion_position" -> ToggleItem(uid, R.drawable.sort_vertical, insertionPositionLabel, pk, true)
                "pin" -> ToggleItem(uid, R.drawable.pin_filled, pinLabel, pk, true)
                "search" -> ToggleItem(uid, R.drawable.search_circle, searchLabel, pk, true)
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
                "refresh_on_open" -> ToggleItem(uid, R.drawable.sync, refreshOnOpenLabel, pk, true)
                "listen_on_yt" -> ToggleItem(uid, R.drawable.play, listenOnYTLabel, pk, true)
                "import_menu" -> ToggleItem(uid, R.drawable.import_outline, importMenuLabel, pk, true)
                "rename" -> ToggleItem(uid, R.drawable.title_edit, renameLabel, pk, true)
                "delete" -> ToggleItem(uid, R.drawable.trash, deleteLabel, pk, true)
                "export" -> ToggleItem(uid, R.drawable.export_outline, exportDialogLabel, pk, true)
                "thumbnail_picker" -> ToggleItem(uid, R.drawable.image, thumbnailLabel, pk, true)
                "reset_thumbnail" -> ToggleItem(uid, R.drawable.image, resetThumbnailLabel, pk, true)
                "update" -> ToggleItem(uid, R.drawable.refresh, updateLabel, pk, true)
                else -> null
            }
        }.filterNotNull()

        ToggleListDialog(
            frameTitle = dialogTitle,
            onDismiss = ::hideDialog,
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
                // Always save the full order (all IDs) to preserve user ordering
                edit.putString(localPlaylistToolbarOrderKey, serializeOrder(workingOrder))
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
