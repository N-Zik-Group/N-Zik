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
import app.it.fast4x.rimusic.enums.IconLikeType
import app.n_zik.android.enums.PlayerControlsColors
import app.it.fast4x.rimusic.enums.PlayerPlayButtonType
import app.it.fast4x.rimusic.enums.PlayerTimelineSize
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import app.n_zik.android.components.dialog.settings.MiniPlayerButtonsSettingsDialog
import app.it.fast4x.rimusic.utils.buttonzoomoutKey
import app.it.fast4x.rimusic.utils.iconLikeTypeKey
import app.it.fast4x.rimusic.utils.lastPlayerPlayButtonTypeKey
import app.it.fast4x.rimusic.utils.playerControlsColorsKey
import app.it.fast4x.rimusic.utils.playerPlayButtonTypeKey
import app.it.fast4x.rimusic.utils.playerSwapControlsWithTimelineKey
import app.it.fast4x.rimusic.utils.playerTimelineSizeKey
import app.it.fast4x.rimusic.utils.playerTimelineTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showSkipTimeButtonsKey
import app.it.fast4x.rimusic.utils.showRemainingSongTimeKey
import app.it.fast4x.rimusic.utils.showTotalTimeQueueKey
import app.it.fast4x.rimusic.utils.transparentbarKey
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.ImportantSettingsDescription
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherEnumValueSelectorSettingsEntry
import androidx.compose.ui.platform.testTag
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit

@OptIn(ExperimentalAnimationApi::class)
@UnstableApi
@Composable
internal fun AppearanceTimelineCard(search: Search) {
    var transparentbar by rememberPreference(transparentbarKey, true)
    var showSkipTimeButtons by rememberPreference(showSkipTimeButtonsKey, true)
    var showRemainingSongTime by rememberPreference(showRemainingSongTimeKey, true)
    var showTotalTimeQueue by rememberPreference(showTotalTimeQueueKey, true)
    var playerPlayButtonType by rememberPreference(
        playerPlayButtonTypeKey,
        PlayerPlayButtonType.CircularRibbed
    )
    var lastPlayerPlayButtonType by rememberPreference(
        lastPlayerPlayButtonTypeKey,
        PlayerPlayButtonType.Rectangular
    )
    var playerTimelineType by rememberPreference(playerTimelineTypeKey, PlayerTimelineType.Wavy)
    var playerTimelineSize by rememberPreference(
        playerTimelineSizeKey,
        PlayerTimelineSize.Biggest
    )
    var playerControlsColors by rememberPreference(playerControlsColorsKey, PlayerControlsColors.Monochrome)
    var iconLikeType by rememberPreference(iconLikeTypeKey, IconLikeType.Essential)
    var playerSwapControlsWithTimeline by rememberPreference(
        playerSwapControlsWithTimelineKey,
        false
    )
    var buttonzoomout by rememberPreference(buttonzoomoutKey, true)
    val searchCtx_0_3 = search.inputValue.isBlank() || stringResource(R.string.settings_player_favorite_button_style_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_timeline_controls_section_title).contains(search.inputValue, true) || stringResource(R.string.settings_player_timeline_size_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_mini_style_label).contains(search.inputValue, true) || stringResource(R.string.player_swap_controls_with_timeline).contains(search.inputValue, true) || stringResource(R.string.settings_player_timeline_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_timeline_transparent_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_play_button_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_button_zoom_out_animation_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_queue_duration_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_remaining_duration_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_seek_time_buttons_label).contains(search.inputValue, true)
    AnimatedVisibility(
                visible = searchCtx_0_3,
                enter = settingsCardEnter,
                exit = settingsCardExit
            ) {
        SettingsSectionCard(
                    modifier = Modifier.testTag("appearance.card.timeline"),
            title = stringResource(R.string.settings_player_timeline_controls_section_title),
            icon = R.drawable.horizontal_straight_line,
            content = {
            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_timeline_size_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_timeline_size_label).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_controls_timeline_size_description), playerTimelineSize.text),
                        modifier = Modifier.settingsSearchTarget("appearance.timeline_and_controls.timelinesize"),
                        icon = R.drawable.resize,
                        title = stringResource(R.string.settings_player_timeline_size_label),
                        selectedValue = playerTimelineSize,
                        onValueSelected = { playerTimelineSize = it },
                        valueText = { it.text }
                    )
                }





            if (search.inputValue.isBlank() || stringResource(R.string.player_mini_buttons_config).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.player_mini_buttons_config).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.timeline_and_controls.miniplayer_buttons_config"),
                        icon = R.drawable.ui,
                        title = stringResource(R.string.player_mini_buttons_config),
                        text = stringResource(R.string.player_mini_buttons_config_description),
                        onClick = { MiniPlayerButtonsSettingsDialog.showDialog() }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.player_swap_controls_with_timeline).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.player_swap_controls_with_timeline).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.timeline_and_controls.player_swap_controls_with_timeline"),
                        icon = R.drawable.sync,
                        title = stringResource(R.string.player_swap_controls_with_timeline),
                        text = stringResource(R.string.player_controls_timeline_position_swapped_description),
                        isChecked = playerSwapControlsWithTimeline,
                        onCheckedChange = { playerSwapControlsWithTimeline = it }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_timeline_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_timeline_label).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_timeline_style_description), stringResource(playerTimelineType.textId)),
                        modifier = Modifier.settingsSearchTarget("appearance.timeline_and_controls.timeline"),
                        icon = R.drawable.playing_indicator,
                        title = stringResource(R.string.settings_player_timeline_label),
                        selectedValue = playerTimelineType,
                        onValueSelected = { playerTimelineType = it },
                        valueText = { stringResource(it.textId) }
                    )
                    AnimatedVisibility(visible = playerTimelineType == PlayerTimelineType.VisualizerBar) {
                        ImportantSettingsDescription(text = stringResource(R.string.visualizer_microphone_permission_message))
                    }
                }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_timeline_transparent_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_timeline_transparent_label).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.timeline_and_controls.transparentbar"),
                        icon = R.drawable.eye_off,
                        title = stringResource(R.string.settings_player_timeline_transparent_label),
                        text = stringResource(R.string.player_controls_mini_player_transparent_background_description),
                        isChecked = transparentbar,
                        onCheckedChange = { transparentbar = it }
                    )
                }



            if (search.inputValue.isBlank() || stringResource(R.string.player_controls_colors).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.player_controls_colors).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_controls_color_style_description), playerControlsColors.text),
                        modifier = Modifier.settingsSearchTarget("appearance.timeline_and_controls.player_controls_colors"),
                        icon = R.drawable.color_palette,
                        title = stringResource(R.string.player_controls_colors),
                        selectedValue = playerControlsColors,
                        onValueSelected = {
                            playerControlsColors = it
                        },
                        valueText = { it.text }
                    )
                }


            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_play_button_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_play_button_label).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_controls_play_pause_style_description), playerPlayButtonType.text),
                        modifier = Modifier.settingsSearchTarget("appearance.timeline_and_controls.play_button"),
                        icon = R.drawable.play,
                        title = stringResource(R.string.settings_player_play_button_label),
                        selectedValue = playerPlayButtonType,
                        onValueSelected = {
                            playerPlayButtonType = it
                            lastPlayerPlayButtonType = it
                        },
                        valueText = { it.text }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_button_zoom_out_animation_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_button_zoom_out_animation_label).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.timeline_and_controls.buttonzoomout"),
                        icon = R.drawable.resize,
                        title = stringResource(R.string.settings_player_button_zoom_out_animation_label),
                        text = stringResource(R.string.player_controls_compact_buttons_description),
                        isChecked = buttonzoomout,
                        onCheckedChange = { buttonzoomout = it }
                    )
                }


                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_favorite_button_style_label).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_controls_like_icon_description), iconLikeType.text),
                        modifier = Modifier.settingsSearchTarget("appearance.timeline_and_controls.icon_like_button"),
                        icon = R.drawable.heart,
                        title = stringResource(R.string.settings_player_favorite_button_style_label),
                        selectedValue = iconLikeType,
                        onValueSelected = {
                            iconLikeType = it
                        },
                        valueText = { it.text },
                    )
                }
           if (search.inputValue.isBlank() || stringResource(R.string.settings_player_queue_duration_visible_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_queue_duration_visible_label).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.colors_and_background.show_total_time_of_queue"),
                        icon = R.drawable.time,
                        title = stringResource(R.string.settings_player_queue_duration_visible_label),
                        text = stringResource(R.string.player_queue_duration_visible_description),
                        isChecked = showTotalTimeQueue,
                        onCheckedChange = { showTotalTimeQueue = it }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_remaining_duration_visible_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_remaining_duration_visible_label).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.colors_and_background.show_remaining_song_time"),
                        icon = R.drawable.time,
                        title = stringResource(R.string.settings_player_remaining_duration_visible_label),
                        text = stringResource(R.string.player_remaining_time_visible_description),
                        isChecked = showRemainingSongTime,
                        onCheckedChange = { showRemainingSongTime = it }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_seek_time_buttons_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_seek_time_buttons_label).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.colors_and_background.skip_time_buttons"),
                        title = stringResource(R.string.settings_player_seek_time_buttons_label),
                        text = stringResource(R.string.player_seek_buttons_visible_description),
                        isChecked = showSkipTimeButtons,
                        onCheckedChange = { showSkipTimeButtons = it },
                        icon = R.drawable.play_forward
                    )
                }

            }
        )
            }
}
