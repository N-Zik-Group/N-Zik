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
    val searchCtx_1 = search.inputValue.isBlank() || stringResource(R.string.player_action_bar).contains(search.inputValue, true) || stringResource(R.string.action_bar_transparent_background).contains(search.inputValue, true) || stringResource(R.string.actionspacedevenly).contains(search.inputValue, true) || stringResource(R.string.tapqueue).contains(search.inputValue, true) || stringResource(R.string.swipe_up_to_open_the_queue).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_video_button).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_discover_button).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_download_button).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_add_to_playlist_button).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_loop_button).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_shuffle_button).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_lyrics_button).contains(search.inputValue, true) || stringResource(R.string.expandedplayer).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_sleep_timer_button).contains(search.inputValue, true) || stringResource(R.string.show_equalizer).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_arrow_button_to_open_queue).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_start_radio_button).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_menu_button).contains(search.inputValue, true)
    AnimatedVisibility(
                visible = searchCtx_1,
                enter = settingsCardEnter,
                exit = settingsCardExit
            ) {
                SettingsSectionCard(
                    modifier = Modifier.testTag("appearance.card.actionBar"),
                    title = stringResource(R.string.player_action_bar),
                    icon = R.drawable.ellipsis_vertical,
                    content = {

            if (search.inputValue.isBlank() || stringResource(R.string.action_bar_transparent_background).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.action_bar_transparent_background).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.action_bar_transparent_background"),
                        icon = R.drawable.eye,
                        title = stringResource(R.string.action_bar_transparent_background),
                        text = "",
                        isChecked = transparentBackgroundActionBarPlayer,
                        onCheckedChange = { transparentBackgroundActionBarPlayer = it }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.actionspacedevenly).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.actionspacedevenly).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.actionspacedevenly"),
                        icon = R.drawable.horizontal_bold_line,
                        title = stringResource(R.string.actionspacedevenly),
                        text = "",
                        isChecked = actionspacedevenly,
                        onCheckedChange = { actionspacedevenly = it }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.tapqueue).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.tapqueue).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.tapqueue"),
                        icon = R.drawable.gesture,
                        title = stringResource(R.string.tapqueue),
                        text = "",
                        isChecked = tapqueue,
                        onCheckedChange = { tapqueue = it }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.swipe_up_to_open_the_queue).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.swipe_up_to_open_the_queue).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.swipe_up_to_open_the_queue"),
                        icon = R.drawable.gesture,
                        title = stringResource(R.string.swipe_up_to_open_the_queue),
                        text = "",
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
