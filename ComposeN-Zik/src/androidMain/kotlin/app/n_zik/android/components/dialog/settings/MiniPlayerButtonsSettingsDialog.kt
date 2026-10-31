package app.n_zik.android.components.dialog.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.SettingsNestedOption
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.it.fast4x.rimusic.utils.miniPlayerButtonOrderKey
import app.it.fast4x.rimusic.utils.showMiniPlayerPlayPauseKey
import app.it.fast4x.rimusic.utils.showMiniPlayerSkipBackKey
import app.it.fast4x.rimusic.utils.showMiniPlayerSkipForwardKey
import app.it.fast4x.rimusic.utils.showMiniPlayerShuffleKey
import app.it.fast4x.rimusic.utils.showMiniPlayerRepeatKey
import app.it.fast4x.rimusic.utils.showMiniPlayerLikeKey
import app.it.fast4x.rimusic.utils.showMiniPlayerAddToPlaylistKey
import app.it.fast4x.rimusic.utils.showMiniPlayerDownloadKey
import app.it.fast4x.rimusic.utils.showMiniPlayerShareKey
import app.it.fast4x.rimusic.utils.showMiniPlayerRadioKey
import app.it.fast4x.rimusic.utils.showMiniPlayerAudioOutputKey
import app.it.fast4x.rimusic.utils.showMiniPlayerSleepTimerKey
import app.it.fast4x.rimusic.utils.showMiniPlayerLyricsKey
import app.it.fast4x.rimusic.utils.showMiniPlayerVisualizerKey
import app.it.fast4x.rimusic.utils.showMiniPlayerQueueKey
import app.it.fast4x.rimusic.utils.showMiniPlayerVideoKey
import app.it.fast4x.rimusic.utils.showMiniPlayerDiscoverKey
import app.it.fast4x.rimusic.utils.visualizerEnabledKey
import app.kreate.android.me.knighthat.utils.Toaster
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.SharedPreferences

private val defaultButtonOrder = listOf(
    "skip_back",
    "play_pause",
    "skip_forward",

    "shuffle",
    "repeat",
    "queue",
    "audio_output",
    "sleep_timer",

    "like",
    "add_to_playlist",
    "download",
    "share",

    "radio",
    "discover",

    "lyrics",
    "visualizer",
    "video"
)

private data class MiniPlayerButtonDef(
    val id: String,
    val preferenceKey: String,
    val iconRes: Int,
    val labelRes: Int,
    val defaultValue: Boolean
)

private fun buildButtonDefs(): Map<String, MiniPlayerButtonDef> = mapOf(
    "skip_back" to MiniPlayerButtonDef("skip_back", showMiniPlayerSkipBackKey, R.drawable.play_skip_back, R.string.miniplayer_button_skip_back, true),
    "play_pause" to MiniPlayerButtonDef("play_pause", showMiniPlayerPlayPauseKey, R.drawable.play, R.string.miniplayer_button_play_pause, true),
    "skip_forward" to MiniPlayerButtonDef("skip_forward", showMiniPlayerSkipForwardKey, R.drawable.play_skip_forward, R.string.miniplayer_button_skip_forward, true),
    "like" to MiniPlayerButtonDef("like", showMiniPlayerLikeKey, R.drawable.heart, R.string.miniplayer_button_like, false),
    "download" to MiniPlayerButtonDef("download", showMiniPlayerDownloadKey, R.drawable.download, R.string.miniplayer_button_download, false),
    "audio_output" to MiniPlayerButtonDef("audio_output", showMiniPlayerAudioOutputKey, R.drawable.devices, R.string.miniplayer_button_audio_output, true),
    "shuffle" to MiniPlayerButtonDef("shuffle", showMiniPlayerShuffleKey, R.drawable.shuffle, R.string.miniplayer_button_shuffle, false),
    "repeat" to MiniPlayerButtonDef("repeat", showMiniPlayerRepeatKey, R.drawable.repeat, R.string.miniplayer_button_repeat, false),
    "add_to_playlist" to MiniPlayerButtonDef("add_to_playlist", showMiniPlayerAddToPlaylistKey, R.drawable.add_in_playlist, R.string.miniplayer_button_add_to_playlist, false),
    "queue" to MiniPlayerButtonDef("queue", showMiniPlayerQueueKey, R.drawable.reorder, R.string.miniplayer_button_queue, false),
    "video" to MiniPlayerButtonDef("video", showMiniPlayerVideoKey, R.drawable.video, R.string.miniplayer_button_video, false),
    "share" to MiniPlayerButtonDef("share", showMiniPlayerShareKey, R.drawable.share_social, R.string.miniplayer_button_share, false),
    "radio" to MiniPlayerButtonDef("radio", showMiniPlayerRadioKey, R.drawable.radio, R.string.miniplayer_button_radio, false),
    "discover" to MiniPlayerButtonDef("discover", showMiniPlayerDiscoverKey, R.drawable.discover, R.string.miniplayer_button_discover, false),
    "sleep_timer" to MiniPlayerButtonDef("sleep_timer", showMiniPlayerSleepTimerKey, R.drawable.sleep, R.string.miniplayer_button_sleep_timer, false),
    "lyrics" to MiniPlayerButtonDef("lyrics", showMiniPlayerLyricsKey, R.drawable.song_lyrics, R.string.miniplayer_button_lyrics, false),
    "visualizer" to MiniPlayerButtonDef("visualizer", showMiniPlayerVisualizerKey, R.drawable.sound_effect, R.string.miniplayer_button_visualizer, false)
)

object MiniPlayerButtonsSettingsDialog : ContentFramedDialog {

    /** Exact searchable options from the same definitions used by this dialog. */
    internal fun searchOptions(): List<SettingsNestedOption> = buildButtonDefs().values.map { SettingsNestedOption(it.id, it.labelRes) }


    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.miniplayer_buttons_config)

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

    private fun loadPrefs(prefs: SharedPreferences): Pair<MutableList<String>, MutableList<Boolean>> {
        val orderSerialized = prefs.getString(miniPlayerButtonOrderKey, "") ?: ""
        val order = parseOrder(orderSerialized).toMutableList()
        val toggles = order.map { id ->
            val def = buildButtonDefs()[id]
            if (def != null) prefs.getBoolean(def.preferenceKey, def.defaultValue) else false
        }.toMutableList()
        return order to toggles
    }

    private fun savePrefs(prefs: SharedPreferences, order: List<String>, toggles: Map<String, Boolean>) {
        val editor = prefs.edit()
        editor.putString(miniPlayerButtonOrderKey, serializeOrder(order))
        buildButtonDefs().forEach { (id, def) ->
            editor.putBoolean(def.preferenceKey, toggles[id] ?: def.defaultValue)
        }

        if (toggles["visualizer"] == true) {
            editor.putBoolean(visualizerEnabledKey, true)
        }

        editor.apply()
    }

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences("preferences", Context.MODE_PRIVATE) }
        val buttonDefs = remember { buildButtonDefs() }
        val maxChecked = 4

        val initial = remember { loadPrefs(prefs) }

        var workingOrder by remember { mutableStateOf(initial.first) }
        var workingToggles by remember { mutableStateOf(initial.second) }
        var checkedCount by remember { mutableIntStateOf(workingToggles.count { it }) }

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

        val items = workingOrder.mapIndexed { index, buttonId ->
            val def = buttonDefs[buttonId] ?: return@mapIndexed null
            ToggleItem(
                id = def.id,
                iconRes = def.iconRes,
                label = stringResource(def.labelRes),
                preferenceKey = def.preferenceKey,
                defaultValue = def.defaultValue
            )
        }.filterNotNull()

        ToggleListDialog(
            frameTitle = dialogTitle,
            onDismiss = ::hideDialog,
            items = items,
            lazyListState = lazyListState,
            reorderableState = reorderableState,
            maxChecked = maxChecked,
            checkedStatesOverride = workingToggles.toList(),
            onCheckedChange = { index, newValue ->
                val newToggles = workingToggles.toMutableList()
                newToggles[index] = newValue
                workingToggles = newToggles
                checkedCount = workingToggles.count { it }
            },
            onReset = {
                workingOrder = defaultButtonOrder.toMutableList()
                workingToggles = defaultButtonOrder.map { id ->
                    buttonDefs[id]?.defaultValue ?: false
                }.toMutableList()
                checkedCount = workingToggles.count { it }
            },
            onCancel = {
                hideDialog()
            },
            onConfirm = {
                val toggleMap = mutableMapOf<String, Boolean>()
                workingOrder.forEachIndexed { index, id ->
                    toggleMap[id] = workingToggles[index]
                }
                savePrefs(prefs, workingOrder, toggleMap)
                Toaster.s(R.string.toast_preference_saved)
                hideDialog()
            }
        )
    }
    fun reset(context: Context) {
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val defaultToggles = buildButtonDefs().mapValues { it.value.defaultValue }
        savePrefs(prefs, defaultButtonOrder, defaultToggles)
    }
}
