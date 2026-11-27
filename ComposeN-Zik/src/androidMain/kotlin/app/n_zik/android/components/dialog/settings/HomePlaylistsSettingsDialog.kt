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
import app.n_zik.android.components.settings.common.SettingsNestedOption
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.n_zik.android.utils.showYtPlaylistsKey
import app.n_zik.android.utils.showOnDevicePlaylistsKey

import app.n_zik.android.utils.showPinnedPlaylistsKey
import app.n_zik.android.utils.showMonthlyPlaylistsKey
import app.n_zik.android.utils.homePlaylistsOrderKey
import app.n_zik.android.core.utils.Toaster
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState
import android.content.Context
import app.n_zik.android.utils.preferences
internal val playlistsDefaultOrder = listOf("all", "pinned_playlists", "rewind", "yt_playlists", "on_device")

internal fun libraryPlaylistChipOrder(serializedOrder: String): List<String> {
    val saved = runCatching {
        val items = JSONArray(serializedOrder)
        List(items.length(), items::getString).filter { it in playlistsDefaultOrder }
    }.getOrDefault(emptyList())
    return (listOf("all") + saved + playlistsDefaultOrder).distinct()
}

internal val playlistsThemeToggleKeys = mapOf(
    "yt_playlists" to showYtPlaylistsKey,
    "on_device" to showOnDevicePlaylistsKey,
    "pinned_playlists" to showPinnedPlaylistsKey,
    "rewind" to showMonthlyPlaylistsKey
)

object HomePlaylistsSettingsDialog : ContentFramedDialog {

    /** Exact searchable options from the same definitions used by this dialog. */
    internal fun searchOptions(): List<SettingsNestedOption> = playlistsDefaultOrder.map { SettingsNestedOption(it, searchLabelResources.getValue(it)) }

    private val searchLabelResources = mapOf(
        "all" to R.string.common_option_all,
        "yt_playlists" to R.string.library_youtube_playlists_label,
        "on_device" to R.string.library_on_device_title,
        "pinned_playlists" to R.string.playlist_pinned_title,
        "rewind" to R.string.rewind_title,
    )
    override val dialogTitle: String @Composable get() = stringResource(R.string.home_playlists_settings)
    override var isActive: Boolean by mutableStateOf(false)

    private fun serializeOrder(order: List<String>): String { val a = JSONArray(); order.forEach { a.put(it) }; return a.toString() }

    @Composable
    override fun DialogBody() {
        val ctx = LocalContext.current
        val prefs = remember { ctx.preferences }
        var workingOrder by remember(prefs) {
            mutableStateOf(libraryPlaylistChipOrder(prefs.getString(homePlaylistsOrderKey, "").orEmpty()))
        }

        val prefKeys = playlistsThemeToggleKeys

        var workingToggles by remember {
            mutableStateOf(
                playlistsDefaultOrder.associateWith { id ->
                    val pk = prefKeys[id]
                    if (pk != null) prefs.getBoolean(pk, true) else true
                }.toMutableMap()
            )
        }

        val allLabel = stringResource(searchLabelResources.getValue("all"))
        val ytLabel = stringResource(searchLabelResources.getValue("yt_playlists"))

        val pinnedLabel = stringResource(searchLabelResources.getValue("pinned_playlists"))
        val rewindLabel = stringResource(searchLabelResources.getValue("rewind"))

        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromIndex = workingOrder.indexOf(from.key)
            val toIndex = workingOrder.indexOf(to.key)
            if (fromIndex > 0 && toIndex > 0) {
                workingOrder = workingOrder.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            }
        }

        val items = workingOrder.map { id ->
            when (id) {
                "all" -> ToggleItem(id, R.drawable.library, allLabel, "always_true_playlists", true)
                "on_device" -> ToggleItem(id, R.drawable.folder, stringResource(R.string.library_on_device_title), showOnDevicePlaylistsKey, true)
                "yt_playlists" -> ToggleItem(id, R.drawable.logo_youtube, ytLabel, showYtPlaylistsKey, true)

                "pinned_playlists" -> ToggleItem(id, R.drawable.pin_filled, pinnedLabel, showPinnedPlaylistsKey, true)
                "rewind" -> ToggleItem(id, R.drawable.calendar, rewindLabel, showMonthlyPlaylistsKey, true)
                else -> null
            }
        }.filterNotNull()

        ToggleListDialog(
            frameTitle = dialogTitle,
            onDismiss = ::hideDialog,
            items = items, lazyListState = lazyListState, reorderableState = reorderableState,
            enforceMinOneChecked = true,
            pinnedItemCount = 1,
            lockedCheckedIds = setOf("all"),
            checkedStatesOverride = items.map { workingToggles[it.id] ?: true },
            onCheckedChange = { index, newValue ->
                val id = items[index].id
                val m = workingToggles.toMutableMap()
                m[id] = newValue
                workingToggles = m
            },
            onReset = {
                workingOrder = playlistsDefaultOrder
                val m = playlistsDefaultOrder.associateWith { true }.toMutableMap()
                workingToggles = m
            },
            onCancel = { hideDialog() },
            onConfirm = {
                val edit = prefs.edit()
                edit.putString(homePlaylistsOrderKey, serializeOrder(workingOrder))
                prefKeys.forEach { (id, pk) ->
                    edit.putBoolean(pk, workingToggles[id] ?: true)
                }
                edit.apply()
                Toaster.s(R.string.settings_preference_saved_message); hideDialog()
            }
        )
    }

    fun reset(context: Context) {
        val prefs = context.preferences
        prefs.edit()
            .putString(homePlaylistsOrderKey, serializeOrder(playlistsDefaultOrder))
            .putBoolean(showOnDevicePlaylistsKey, true)
            .putBoolean(showYtPlaylistsKey, true)
            .putBoolean(showPinnedPlaylistsKey, true)
            .putBoolean(showMonthlyPlaylistsKey, true)
            .apply()
    }
}
