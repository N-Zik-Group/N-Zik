package app.n_zik.android.components.settings.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.components.dialog.settings.PlayerActionBarSettingsDialog
import app.n_zik.android.components.dialog.settings.appearanceToolbarSettingsDialogs
import app.n_zik.android.components.settings.common.OtherEnumValueSelectorSettingsEntry
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.components.tab.Search
import app.n_zik.android.enums.PlayerOpeningMode
import app.n_zik.android.utils.actionspacedevenlyKey
import app.n_zik.android.utils.isLandscape
import app.n_zik.android.utils.playerOpeningModeKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.rememberQueueScrollPositionKey
import app.n_zik.android.utils.swipeUpQueueKey
import app.n_zik.android.utils.tapqueueKey
import app.n_zik.android.utils.topPaddingKey
import app.n_zik.android.utils.transparentBackgroundPlayerActionBarKey

@UnstableApi
@Composable
internal fun AppearanceActionBarCard(search: Search) {
    var transparentBackground by rememberPreference(transparentBackgroundPlayerActionBarKey, true)
    var evenlySpaced by rememberPreference(actionspacedevenlyKey, true)
    var tapQueue by rememberPreference(tapqueueKey, true)
    var swipeUpQueue by rememberPreference(swipeUpQueueKey, true)
    var rememberQueueScroll by rememberPreference(rememberQueueScrollPositionKey, true)
    var openingMode by rememberPreference(playerOpeningModeKey, PlayerOpeningMode.Audio)
    var topPadding by rememberPreference(topPaddingKey, true)
    val openingModeLabels = mapOf(
        PlayerOpeningMode.Audio to R.string.player_audio_mode,
        PlayerOpeningMode.Video to R.string.player_video_mode,
        PlayerOpeningMode.RememberLast to R.string.settings_player_opening_mode_remember_last,
    )
    val context = LocalContext.current
    val query = search.inputValue
    val title = stringResource(R.string.settings_section_player_action_bar_title)
    val showAll = query.isBlank() || title.contains(query, ignoreCase = true)
    fun matches(vararg labels: Int) = showAll || labels.any { context.getString(it).contains(query, ignoreCase = true) }

    val showBackground = matches(R.string.settings_player_action_bar_transparent_background_label)
    val showSpacing = matches(R.string.settings_player_actions_spaced_evenly_label)
    val showTap = matches(R.string.settings_player_queue_tap_to_open_label)
    val showSwipe = matches(R.string.settings_player_queue_swipe_up_label)
    val showQueueScroll = matches(R.string.settings_player_queue_remember_scroll_label,
        R.string.settings_player_queue_remember_scroll_description)
    val showPlayerActions = matches(R.string.settings_toolbar_player_action_bar_configure,
        R.string.settings_toolbar_player_action_bar_description,
        R.string.player_position_top_label, R.string.player_position_bottom_label) ||
        PlayerActionBarSettingsDialog.searchOptions().any { matches(it.labelRes) }
    val showOpeningMode = matches(R.string.settings_player_opening_mode_label,
        R.string.settings_player_opening_mode_description) || openingModeLabels.values.any { matches(it) }
    val showTopPadding = !isLandscape &&
        matches(R.string.settings_lyrics_top_spacing_label, R.string.player_top_spacing_description)
    val toolbarDialogs = appearanceToolbarSettingsDialogs.filter { dialog ->
        matches(dialog.label) || dialog.searchOptions().any { matches(it.labelRes) }
    }

    AnimatedVisibility(
        visible = showBackground || showSpacing || showTap || showSwipe || showQueueScroll || showPlayerActions ||
            showOpeningMode || showTopPadding || toolbarDialogs.isNotEmpty(),
        enter = settingsCardEnter,
        exit = settingsCardExit,
    ) {
        SettingsSectionCard(
            modifier = Modifier.testTag("appearance.card.actionBar"),
            title = title,
            icon = R.drawable.ellipsis_vertical,
            content = {
                if (showBackground) OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.action_bar_transparent_background"),
                    icon = R.drawable.eye,
                    title = stringResource(R.string.settings_player_action_bar_transparent_background_label),
                    text = stringResource(R.string.player_action_bar_transparent_background_description),
                    isChecked = transparentBackground,
                    onCheckedChange = { transparentBackground = it },
                )
                if (showSpacing) OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.actionspacedevenly"),
                    icon = R.drawable.horizontal_bold_line,
                    title = stringResource(R.string.settings_player_actions_spaced_evenly_label),
                    text = stringResource(R.string.player_action_bar_even_button_spacing_description),
                    isChecked = evenlySpaced,
                    onCheckedChange = { evenlySpaced = it },
                )
                if (showTap) OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.tapqueue"),
                    icon = R.drawable.gesture,
                    title = stringResource(R.string.settings_player_queue_tap_to_open_label),
                    text = stringResource(R.string.player_action_bar_queue_preview_tap_description),
                    isChecked = tapQueue,
                    onCheckedChange = { tapQueue = it },
                )
                if (showSwipe) OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.swipe_up_to_open_the_queue"),
                    icon = R.drawable.gesture,
                    title = stringResource(R.string.settings_player_queue_swipe_up_label),
                    text = stringResource(R.string.player_action_bar_swipe_up_to_open_the_queue_description),
                    isChecked = swipeUpQueue,
                    onCheckedChange = { swipeUpQueue = it },
                )
                if (showQueueScroll) OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("ui.user_interface.remember_queue_scroll_position"),
                    icon = R.drawable.playlist,
                    title = stringResource(R.string.settings_player_queue_remember_scroll_label),
                    text = stringResource(R.string.settings_player_queue_remember_scroll_description),
                    isChecked = rememberQueueScroll,
                    onCheckedChange = { rememberQueueScroll = it },
                )
                if (showPlayerActions) OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.player_action_bar_buttons"),
                    icon = R.drawable.ellipsis_vertical,
                    title = stringResource(R.string.settings_toolbar_player_action_bar_configure),
                    text = stringResource(R.string.settings_toolbar_player_action_bar_description),
                    onClick = PlayerActionBarSettingsDialog::showDialog,
                )
                if (showOpeningMode) OtherEnumValueSelectorSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("appearance.player_behavior_and_visuals.opening_mode"),
                    icon = R.drawable.video,
                    title = stringResource(R.string.settings_player_opening_mode_label),
                    text = stringResource(
                        R.string.settings_description_with_value,
                        stringResource(R.string.settings_player_opening_mode_description),
                        stringResource(openingModeLabels.getValue(openingMode)),
                    ),
                    selectedValue = openingMode,
                    onValueSelected = { openingMode = it },
                    valueText = { stringResource(openingModeLabels.getValue(it)) },
                )
                if (showTopPadding) OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("appearance.player_behavior_and_visuals.blankspace"),
                    icon = R.drawable.space,
                    title = stringResource(R.string.settings_lyrics_top_spacing_label),
                    text = stringResource(R.string.player_top_spacing_description),
                    isChecked = topPadding,
                    onCheckedChange = { topPadding = it },
                )
                toolbarDialogs.forEach { dialog ->
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget(dialog.entryId),
                        icon = dialog.icon,
                        title = stringResource(dialog.label),
                        text = stringResource(R.string.settings_library_toolbar_customization_description),
                        onClick = dialog::showDialog,
                    )
                }
            },
        )
    }
}
