package app.n_zik.android.components.settings.appearance

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.components.dialog.settings.PlayerActionBarSettingsDialog
import app.it.fast4x.rimusic.utils.actionspacedevenlyKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.swipeUpQueueKey
import app.it.fast4x.rimusic.utils.tapqueueKey
import app.it.fast4x.rimusic.utils.transparentBackgroundPlayerActionBarKey
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import androidx.compose.ui.platform.testTag
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit

@OptIn(ExperimentalAnimationApi::class)
@UnstableApi
@Composable
internal fun AppearanceActionBarCard(search: Search) {
    var transparentBackgroundActionBarPlayer by rememberPreference(
        transparentBackgroundPlayerActionBarKey,
        true
    )
    var actionspacedevenly by rememberPreference(actionspacedevenlyKey, true)
    var tapqueue by rememberPreference(tapqueueKey, true)
    var swipeUpQueue by rememberPreference(swipeUpQueueKey, true)
    val searchCtx_1 = search.inputValue.isBlank() || stringResource(R.string.settings_section_player_action_bar_title).contains(search.inputValue, true) || stringResource(R.string.settings_player_action_bar_transparent_background_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_actions_spaced_evenly_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_queue_tap_to_open_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_queue_swipe_up_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_action_bar_video_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_action_bar_discover_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_action_bar_download_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_action_bar_playlist_add_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_action_bar_loop_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_action_bar_shuffle_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_action_bar_show_lyrics_button).contains(search.inputValue, true) || stringResource(R.string.settings_player_expanded_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_action_bar_sleep_timer_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_action_bar_equalizer_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_action_bar_queue_arrow_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_action_bar_radio_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_action_bar_menu_visible_label).contains(search.inputValue, true)
    AnimatedVisibility(
                visible = searchCtx_1,
                enter = settingsCardEnter,
                exit = settingsCardExit
            ) {
                SettingsSectionCard(
                    modifier = Modifier.testTag("appearance.card.actionBar"),
                    title = stringResource(R.string.settings_section_player_action_bar_title),
                    icon = R.drawable.ellipsis_vertical,
                    content = {

            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_action_bar_transparent_background_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_action_bar_transparent_background_label).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.action_bar_transparent_background"),
                        icon = R.drawable.eye,
                        title = stringResource(R.string.settings_player_action_bar_transparent_background_label),
                        text = stringResource(R.string.player_action_bar_transparent_background_description),
                        isChecked = transparentBackgroundActionBarPlayer,
                        onCheckedChange = { transparentBackgroundActionBarPlayer = it }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_actions_spaced_evenly_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_actions_spaced_evenly_label).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.actionspacedevenly"),
                        icon = R.drawable.horizontal_bold_line,
                        title = stringResource(R.string.settings_player_actions_spaced_evenly_label),
                        text = stringResource(R.string.player_action_bar_even_button_spacing_description),
                        isChecked = actionspacedevenly,
                        onCheckedChange = { actionspacedevenly = it }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_queue_tap_to_open_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_queue_tap_to_open_label).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.tapqueue"),
                        icon = R.drawable.gesture,
                        title = stringResource(R.string.settings_player_queue_tap_to_open_label),
                        text = stringResource(R.string.player_action_bar_queue_preview_tap_description),
                        isChecked = tapqueue,
                        onCheckedChange = { tapqueue = it }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_queue_swipe_up_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_queue_swipe_up_label).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.swipe_up_to_open_the_queue"),
                        icon = R.drawable.gesture,
                        title = stringResource(R.string.settings_player_queue_swipe_up_label),
                        text = stringResource(R.string.player_action_bar_swipe_up_to_open_the_queue_description),
                        isChecked = swipeUpQueue,
                        onCheckedChange = { swipeUpQueue = it }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.player_action_bar_buttons).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.player_action_bar_buttons).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.player_action_bar_buttons"),
                        icon = R.drawable.ellipsis_vertical,
                        title = stringResource(R.string.player_action_bar_buttons),
                        text = stringResource(R.string.player_action_bar_buttons_description),
                        onClick = { PlayerActionBarSettingsDialog.showDialog() }
                    )
                }

                    }
                )
            }
}
