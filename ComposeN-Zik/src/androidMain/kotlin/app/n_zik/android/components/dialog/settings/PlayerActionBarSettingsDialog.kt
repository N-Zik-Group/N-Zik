package app.n_zik.android.components.dialog.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.LocalSettingsTargets
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsNestedOption
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.components.settings.ui.ToolbarAction
import app.n_zik.android.components.settings.ui.ToolbarSurface
import app.n_zik.android.utils.showButtonPlayerAddToPlaylistKey
import app.n_zik.android.utils.showButtonPlayerArrowKey
import app.n_zik.android.utils.showButtonPlayerDiscoverKey
import app.n_zik.android.utils.showButtonPlayerDownloadKey
import app.n_zik.android.utils.showButtonPlayerLoopKey
import app.n_zik.android.utils.showButtonPlayerLyricsKey
import app.n_zik.android.utils.showButtonPlayerMenuKey
import app.n_zik.android.utils.showButtonPlayerShuffleKey
import app.n_zik.android.utils.showButtonPlayerSleepTimerKey
import app.n_zik.android.utils.showButtonPlayerStartRadioKey
import app.n_zik.android.utils.showButtonPlayerSystemEqualizerKey
import app.n_zik.android.utils.expandedplayertoggleKey
import app.n_zik.android.utils.visualizerEnabledKey
import app.n_zik.android.utils.playerActionBarButtonOrderKey
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.themed.n_zik.screen.player.defaultPlayerActionBarOrder
import app.n_zik.android.themed.n_zik.screen.player.defaultPlayerActionBarTopSlots
import app.n_zik.android.themed.n_zik.screen.player.emptyPlayerActionBarTopSlot
import app.n_zik.android.themed.n_zik.screen.player.playerActionBarBottomAudioVideoKey
import app.n_zik.android.themed.n_zik.screen.player.playerActionBarTopSlotsKey
import app.n_zik.android.themed.n_zik.screen.player.readPlayerActionBarTopSlots
import app.n_zik.android.themed.n_zik.screen.player.resolvePlayerActionBarOrder
import app.n_zik.android.utils.preferences
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState

internal fun playerActionBarThemeIds(): Set<String> = defaultPlayerActionBarOrder.toSet()

private data class ButtonDef(
    val id: String,
    val preferenceKey: String,
    val iconRes: Int,
    val labelRes: Int,
    val defaultValue: Boolean
)

private fun buildButtonDefs(): Map<String, ButtonDef> = mapOf(
    "discover" to ButtonDef("discover", showButtonPlayerDiscoverKey, R.drawable.discover, R.string.settings_player_action_bar_discover_visible_label, false),
    "download" to ButtonDef("download", showButtonPlayerDownloadKey, R.drawable.download, R.string.settings_player_action_bar_download_visible_label, true),
    "add_to_playlist" to ButtonDef("add_to_playlist", showButtonPlayerAddToPlaylistKey, R.drawable.playlist, R.string.playlist_actions_title, true),
    "loop" to ButtonDef("loop", showButtonPlayerLoopKey, R.drawable.repeat, R.string.settings_player_action_bar_loop_visible_label, false),
    "shuffle" to ButtonDef("shuffle", showButtonPlayerShuffleKey, R.drawable.shuffle, R.string.settings_player_action_bar_shuffle_visible_label, true),
    "lyrics" to ButtonDef("lyrics", showButtonPlayerLyricsKey, R.drawable.song_lyrics, R.string.settings_action_bar_show_lyrics_button, true),
    "visualizer" to ButtonDef("visualizer", visualizerEnabledKey, R.drawable.sound_effect, R.string.settings_player_action_bar_visualizer_visible_label, false),
    "expanded_player" to ButtonDef("expanded_player", expandedplayertoggleKey, R.drawable.maximize, R.string.settings_player_expanded_label, false),
    "sleep_timer" to ButtonDef("sleep_timer", showButtonPlayerSleepTimerKey, R.drawable.sleep, R.string.settings_player_action_bar_sleep_timer_visible_label, false),
    "equalizer" to ButtonDef("equalizer", showButtonPlayerSystemEqualizerKey, R.drawable.equalizer, R.string.settings_player_action_bar_equalizer_visible_label, false),
    "arrow" to ButtonDef("arrow", showButtonPlayerArrowKey, R.drawable.chevron_up, R.string.settings_player_action_bar_open_queue_label, true),
    "start_radio" to ButtonDef("start_radio", showButtonPlayerStartRadioKey, R.drawable.radio, R.string.settings_player_action_bar_radio_visible_label, false),
    "menu" to ButtonDef("menu", showButtonPlayerMenuKey, R.drawable.ellipsis_vertical, R.string.settings_player_action_bar_menu_visible_label, false),
    "audioVideo" to ButtonDef("audioVideo", playerActionBarBottomAudioVideoKey, R.drawable.video, R.string.settings_toolbar_audio_video, false),
)

private val slotLabels = listOf(
    R.string.settings_toolbar_player_left_slot,
    R.string.settings_toolbar_player_center_slot,
    R.string.settings_toolbar_player_right_slot,
)

private val topActions = listOf(ToolbarAction.COLLAPSE_PLAYER, ToolbarAction.AUDIO_VIDEO, ToolbarAction.MENU)

private fun topSlotChoices(buttonDefs: Map<String, ButtonDef>): Map<String, Int> = buildMap {
    put(emptyPlayerActionBarTopSlot, R.string.common_option_none)
    topActions.forEach { put(it.id, it.label) }
    defaultPlayerActionBarOrder.forEach { id ->
        if (id !in this) put(id, buttonDefs.getValue(id).labelRes)
    }
}

internal fun canAssignPlayerTopSlot(slots: List<String>, index: Int, action: String): Boolean =
    action == emptyPlayerActionBarTopSlot || slots[index] == action || action !in slots

internal fun assignPlayerTopSlot(slots: List<String>, index: Int, action: String): List<String> {
    if (!canAssignPlayerTopSlot(slots, index, action)) return slots
    return slots.toMutableList().apply { this[index] = action }
}

object PlayerActionBarSettingsDialog : ContentFramedDialog {

    /** Exact searchable options from the same definitions used by this dialog. */
    internal fun searchOptions(): List<SettingsNestedOption> {
        val buttonDefs = buildButtonDefs()
        return slotLabels.mapIndexed { index, label ->
            SettingsNestedOption("top.slot.$index", label, 0, R.string.player_position_top_label)
        } + topSlotChoices(buttonDefs).map { (id, label) ->
            SettingsNestedOption("top.action.$id", label, 0, R.string.player_position_top_label)
        } + buttonDefs.values.map {
            SettingsNestedOption(it.id, it.labelRes, 1, R.string.player_position_bottom_label)
        }
    }


    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.settings_section_player_action_bar_title)

    override var isActive: Boolean by mutableStateOf(false)

    private fun serializeOrder(order: List<String>): String {
        val arr = JSONArray()
        order.forEach { arr.put(it) }
        return arr.toString()
    }

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        val prefs = remember { context.preferences }
        val buttonDefs = remember { buildButtonDefs() }
        val tabTransition = rememberSettingsDialogTabTransition(tabCount = 2, scopeKey = prefs)
        val tabIndex = tabTransition.displayedIndex
        var workingSlots by remember(prefs) { mutableStateOf(readPlayerActionBarTopSlots(prefs)) }
        var topEdited by remember(prefs) { mutableStateOf(false) }
        var openSlot by remember(prefs) { mutableStateOf<Int?>(null) }
        val topLabels = remember(buttonDefs) { topSlotChoices(buttonDefs) }
        val nestedTarget = LocalSettingsTargets.current?.nestedTarget
        val nestedItemId = nestedTarget?.nestedDestination?.itemId
        val targetAction = nestedItemId?.takeIf { it.startsWith("top.action.") }?.removePrefix("top.action.")
        val targetSlot = targetAction?.let { action -> workingSlots.indexOf(action).takeIf { it >= 0 } }
        LaunchedEffect(nestedTarget?.id) {
            if (targetAction != null && targetSlot == null) {
                openSlot = workingSlots.indexOf(emptyPlayerActionBarTopSlot).takeIf { it >= 0 }
                    ?: workingSlots.indices.firstOrNull { canAssignPlayerTopSlot(workingSlots, it, targetAction) }
            }
        }

        val initial = remember {
            val orderSerialized = prefs.getString(playerActionBarButtonOrderKey, "") ?: ""
            val order = resolvePlayerActionBarOrder(orderSerialized).toMutableList()
            val toggles = order.map { id ->
                val def = buttonDefs[id]
                if (def != null) prefs.getBoolean(def.preferenceKey, def.defaultValue) else false
            }.toMutableList()
            order to toggles
        }

        var workingOrder by remember { mutableStateOf(initial.first) }
        var workingToggles by remember { mutableStateOf(initial.second) }
        val bottomMenuRequired = ToolbarAction.MENU.id !in workingSlots

        val lazyListState = rememberLazyListState()
        val slotScrollState = rememberScrollState()

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

        val items = if (tabIndex == 0) emptyList() else workingOrder.mapNotNull { buttonId ->
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
            lockedCheckedIds = if (bottomMenuRequired) setOf(ToolbarAction.MENU.id) else emptySet(),
            unavailableIds = workingSlots.filterTo(mutableSetOf()) { it in buttonDefs },
            checkedStatesOverride = if (tabIndex == 0) emptyList() else workingToggles.toList(),
            onCheckedChange = { index, newValue ->
                val newToggles = workingToggles.toMutableList()
                newToggles[index] = newValue
                workingToggles = newToggles
            },
            onReset = {
                if (tabIndex == 0) {
                    workingSlots = defaultPlayerActionBarTopSlots
                    topEdited = true
                } else {
                    workingOrder = defaultPlayerActionBarOrder.toMutableList()
                    workingToggles = defaultPlayerActionBarOrder.map { id ->
                        buttonDefs[id]?.defaultValue ?: false
                    }.toMutableList()
                }
            },
            onCancel = {
                hideDialog()
            },
            onConfirm = {
                val editor = prefs.edit()
                if (topEdited) editor.putString(playerActionBarTopSlotsKey, serializeOrder(workingSlots))
                editor.putString(playerActionBarButtonOrderKey, serializeOrder(workingOrder))
                workingOrder.forEachIndexed { index, id ->
                    val def = buttonDefs[id] ?: return@forEachIndexed
                    editor.putBoolean(def.preferenceKey, workingToggles[index])
                }
                editor.apply()
                Toaster.s(R.string.settings_preference_saved_message)
                hideDialog()
            },
            tabContentAlpha = { tabTransition.alpha },
            tabContent = if (tabIndex == 0) ({
                Column(Modifier.fillMaxWidth().verticalScroll(slotScrollState)) {
                    workingSlots.forEachIndexed { index, action ->
                        val icon = topActions.firstOrNull { it.id == action }?.icon
                            ?: buttonDefs[action]?.iconRes ?: R.drawable.unchecked_outline
                        val highlighted = nestedItemId == "top.slot.$index" || targetSlot == index
                        OtherSettingsEntry(
                            title = stringResource(slotLabels[index]),
                            text = "",
                            icon = icon,
                            selectedValueText = stringResource(topLabels.getValue(action)),
                            modifier = Modifier.wrapContentHeight(unbounded = true).then(
                                if (highlighted && nestedTarget != null) Modifier.settingsSearchTarget(nestedTarget.id)
                                else Modifier,
                            ),
                            onClick = { openSlot = index },
                        )
                    }
                }
            }) else null,
            bodyHeader = {
                SettingsDialogTabRow(
                    labels = listOf(
                        stringResource(R.string.player_position_top_label),
                        stringResource(R.string.player_position_bottom_label),
                    ),
                    selectedTabIndex = tabTransition.selectedIndex,
                    onTabSelected = tabTransition::select,
                )
                Spacer(Modifier.height(8.dp))
            },
        )
        DialogPresence(target = openSlot) { index ->
            ValueSelectorDialog(
                onDismiss = { openSlot = null },
                title = stringResource(slotLabels[index]),
                selectedValue = workingSlots[index],
                values = topLabels.keys.filter { canAssignPlayerTopSlot(workingSlots, index, it) },
                valueText = { stringResource(topLabels.getValue(it)) },
                valueModifier = { action ->
                    if (action == targetAction && targetSlot == null && nestedTarget != null) {
                        Modifier.settingsSearchTarget(nestedTarget.id)
                    } else Modifier
                },
                onValueSelected = { action ->
                    val updated = assignPlayerTopSlot(workingSlots, index, action)
                    if (updated != workingSlots) {
                        workingSlots = updated
                        topEdited = true
                    }
                },
            )
        }
    }

    fun reset(context: Context) {
        val prefs = context.preferences
        val editor = prefs.edit()
        editor.remove(playerActionBarTopSlotsKey)
        editor.remove(ToolbarSurface.PLAYER_TOP.orderKey)
        topActions.forEach { editor.remove(ToolbarSurface.PLAYER_TOP.visibilityKey(it)) }
        editor.putString(playerActionBarButtonOrderKey, serializeOrder(defaultPlayerActionBarOrder))
        buildButtonDefs().forEach { (_, def) ->
            editor.putBoolean(def.preferenceKey, def.defaultValue)
        }
        editor.apply()
    }
}
