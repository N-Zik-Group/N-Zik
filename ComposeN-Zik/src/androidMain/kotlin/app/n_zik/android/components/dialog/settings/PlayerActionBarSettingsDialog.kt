package app.n_zik.android.components.dialog.settings

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.it.fast4x.rimusic.utils.showButtonPlayerAddToPlaylistKey
import app.it.fast4x.rimusic.utils.showButtonPlayerArrowKey
import app.it.fast4x.rimusic.utils.showButtonPlayerDiscoverKey
import app.it.fast4x.rimusic.utils.showButtonPlayerDownloadKey
import app.it.fast4x.rimusic.utils.showButtonPlayerLoopKey
import app.it.fast4x.rimusic.utils.showButtonPlayerLyricsKey
import app.it.fast4x.rimusic.utils.showButtonPlayerMenuKey
import app.it.fast4x.rimusic.utils.showButtonPlayerShuffleKey
import app.it.fast4x.rimusic.utils.showButtonPlayerSleepTimerKey
import app.it.fast4x.rimusic.utils.showButtonPlayerStartRadioKey
import app.it.fast4x.rimusic.utils.showButtonPlayerSystemEqualizerKey
import app.it.fast4x.rimusic.utils.showButtonPlayerVideoKey
import app.it.fast4x.rimusic.utils.expandedplayertoggleKey
import app.it.fast4x.rimusic.utils.visualizerEnabledKey
import app.it.fast4x.rimusic.utils.playerActionBarButtonOrderKey
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.kreate.android.me.knighthat.utils.Toaster
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState
import android.content.Context

private val defaultButtonOrder = listOf(
    "video", "start_radio", "discover", "download", "add_to_playlist",
    "shuffle", "loop", "lyrics", "visualizer", "expanded_player",
    "sleep_timer", "equalizer", "arrow", "menu"
)

private data class ButtonDef(
    val id: String,
    val preferenceKey: String,
    val iconRes: Int,
    val labelRes: Int,
    val defaultValue: Boolean
)

private fun buildButtonDefs(): Map<String, ButtonDef> = mapOf(
    "video" to ButtonDef("video", showButtonPlayerVideoKey, R.drawable.video, R.string.action_bar_show_video_button, true),
    "discover" to ButtonDef("discover", showButtonPlayerDiscoverKey, R.drawable.discover, R.string.action_bar_show_discover_button, false),
    "download" to ButtonDef("download", showButtonPlayerDownloadKey, R.drawable.download, R.string.action_bar_show_download_button, true),
    "add_to_playlist" to ButtonDef("add_to_playlist", showButtonPlayerAddToPlaylistKey, R.drawable.add_in_playlist, R.string.action_bar_show_add_to_playlist_button, true),
    "loop" to ButtonDef("loop", showButtonPlayerLoopKey, R.drawable.repeat, R.string.action_bar_show_loop_button, false),
    "shuffle" to ButtonDef("shuffle", showButtonPlayerShuffleKey, R.drawable.shuffle, R.string.action_bar_show_shuffle_button, true),
    "lyrics" to ButtonDef("lyrics", showButtonPlayerLyricsKey, R.drawable.song_lyrics, R.string.action_bar_show_lyrics_button, true),
    "visualizer" to ButtonDef("visualizer", visualizerEnabledKey, R.drawable.sound_effect, R.string.action_bar_show_visualizer_button, false),
    "expanded_player" to ButtonDef("expanded_player", expandedplayertoggleKey, R.drawable.maximize, R.string.expandedplayer, false),
    "sleep_timer" to ButtonDef("sleep_timer", showButtonPlayerSleepTimerKey, R.drawable.sleep, R.string.action_bar_show_sleep_timer_button, false),
    "equalizer" to ButtonDef("equalizer", showButtonPlayerSystemEqualizerKey, R.drawable.equalizer, R.string.show_equalizer, false),
    "arrow" to ButtonDef("arrow", showButtonPlayerArrowKey, R.drawable.chevron_up, R.string.action_bar_show_arrow_button_to_open_queue, true),
    "start_radio" to ButtonDef("start_radio", showButtonPlayerStartRadioKey, R.drawable.radio, R.string.action_bar_show_start_radio_button, false),
    "menu" to ButtonDef("menu", showButtonPlayerMenuKey, R.drawable.ellipsis_vertical, R.string.action_bar_show_menu_button, false)
)

object PlayerActionBarSettingsDialog : ContentFramedDialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.player_action_bar_buttons)

    override var isActive: Boolean by mutableStateOf(false)

    private fun parseOrder(serialized: String): List<String> {
        if (serialized.isBlank()) return defaultButtonOrder
        return try {
            val arr = JSONArray(serialized)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            val validIds = defaultButtonOrder.filter { it in buildButtonDefs().keys }
            val result = list.filter { it in validIds }.toMutableList()
            for (id in validIds) {
                if (id !in result) result.add(id)
            }
            result
        } catch (_: Exception) {
            defaultButtonOrder
        }
    }

    private fun serializeOrder(order: List<String>): String {
        val arr = JSONArray()
        order.forEach { arr.put(it) }
        return arr.toString()
    }

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences("preferences", Context.MODE_PRIVATE) }
        val buttonDefs = remember { buildButtonDefs() }

        val initial = remember {
            val orderSerialized = prefs.getString(playerActionBarButtonOrderKey, "") ?: ""
            val order = parseOrder(orderSerialized).toMutableList()
            val toggles = order.map { id ->
                val def = buttonDefs[id]
                if (def != null) prefs.getBoolean(def.preferenceKey, def.defaultValue) else false
            }.toMutableList()
            order to toggles
        }

        var workingOrder by remember { mutableStateOf(initial.first) }
        var workingToggles by remember { mutableStateOf(initial.second) }

        val lazyListState = rememberLazyListState()

        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val order = workingOrder.toMutableList()
            val toggles = workingToggles.toMutableList()
            val fromIndex = order.indexOf(from.key)
            val toIndex = order.indexOf(to.key)
            if (fromIndex != -1 && toIndex != -1) {
                val item = order.removeAt(fromIndex)
                order.add(toIndex, item)
                val checkedItem = toggles.removeAt(fromIndex)
                toggles.add(toIndex, checkedItem)
                workingOrder = order
                workingToggles = toggles
            }
        }

        val items = workingOrder.mapNotNull { buttonId ->
            val def = buttonDefs[buttonId] ?: return@mapNotNull null
            ToggleItem(
                id = def.id,
                iconRes = def.iconRes,
                label = stringResource(def.labelRes),
                preferenceKey = def.preferenceKey,
                defaultValue = def.defaultValue
            )
        }

        ToggleListDialog(
            frameTitle = dialogTitle,
            onDismiss = ::hideDialog,
            items = items,
            lazyListState = lazyListState,
            reorderableState = reorderableState,
            checkedStatesOverride = workingToggles.toList(),
            onCheckedChange = { index, newValue ->
                val newToggles = workingToggles.toMutableList()
                newToggles[index] = newValue
                workingToggles = newToggles
            },
            onReset = {
                workingOrder = defaultButtonOrder.toMutableList()
                workingToggles = defaultButtonOrder.map { id ->
                    buttonDefs[id]?.defaultValue ?: false
                }.toMutableList()
            },
            onCancel = {
                hideDialog()
            },
            onConfirm = {
                val editor = prefs.edit()
                editor.putString(playerActionBarButtonOrderKey, serializeOrder(workingOrder))
                workingOrder.forEachIndexed { index, id ->
                    val def = buttonDefs[id] ?: return@forEachIndexed
                    editor.putBoolean(def.preferenceKey, workingToggles[index])
                }
                editor.apply()
                Toaster.s(R.string.toast_preference_saved)
                hideDialog()
            }
        )
    }

    fun reset(context: Context) {
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(playerActionBarButtonOrderKey, serializeOrder(defaultButtonOrder))
        buildButtonDefs().forEach { (_, def) ->
            editor.putBoolean(def.preferenceKey, def.defaultValue)
        }
        editor.apply()
    }
}
