package app.n_zik.android.components.settings.appearance

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.it.fast4x.rimusic.enums.BackgroundProgress
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.enums.SongsNumber
import app.it.fast4x.rimusic.utils.backgroundProgressKey
import app.it.fast4x.rimusic.utils.disablePlayerHorizontalSwipeKey
import app.it.fast4x.rimusic.utils.playerSwipeSensitivityKey
import app.n_zik.android.enums.PlayerSwipeSensitivity
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.effectRotationKey
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showNextSongsInPlayerKey
import app.it.fast4x.rimusic.utils.showTopActionsBarKey
import app.it.fast4x.rimusic.utils.showVisualizerStateKey
import app.it.fast4x.rimusic.utils.showalbumcoverKey
import app.it.fast4x.rimusic.utils.showsongsKey
import app.it.fast4x.rimusic.utils.showvisthumbnailKey
import app.it.fast4x.rimusic.utils.showVisualizerButtonsKey
import app.it.fast4x.rimusic.utils.blackBackgroundForVisThumbnailKey
import app.it.fast4x.rimusic.utils.topPaddingKey
import app.it.fast4x.rimusic.utils.visualizerEnabledKey
import app.n_zik.android.components.tab.Search
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.components.dialog.common.DialogColorPicker
import app.it.fast4x.rimusic.utils.visualizerWhiteColorOptionKey
import app.it.fast4x.rimusic.utils.visualizerLineThicknessKey
import app.it.fast4x.rimusic.utils.visualizerCustomColorKey
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.ImportantSettingsDescription
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherEnumValueSelectorSettingsEntry
import app.n_zik.android.components.settings.common.SliderSettingsEntry
import androidx.compose.ui.platform.testTag
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit

@OptIn(ExperimentalAnimationApi::class)
@UnstableApi
@Composable
internal fun AppearanceBehaviorCard(search: Search) {
    var disablePlayerHorizontalSwipe by rememberPreference(disablePlayerHorizontalSwipeKey, false)
    var playerSwipeSensitivity by rememberPreference(playerSwipeSensitivityKey, PlayerSwipeSensitivity.Medium)
    var disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    var visualizerEnabled by rememberPreference(visualizerEnabledKey, false)
    var visualizerWhiteColorOption by rememberPreference(
        visualizerWhiteColorOptionKey,
        enums.VisualizerWhiteColorOption.White
    )
    var visualizerCustomColor by rememberPreference(
        visualizerCustomColorKey,
        android.graphics.Color.WHITE
    )
    var visualizerLineThickness by rememberPreference(
        visualizerLineThicknessKey,
        6f
    )
    var effectRotationEnabled by rememberPreference(effectRotationKey, false)
    var backgroundProgress by rememberPreference(
        backgroundProgressKey,
        BackgroundProgress.MiniPlayer
    )
    var showNextSongsInPlayer by rememberPreference(showNextSongsInPlayerKey, false)
    var showTopActionsBar by rememberPreference(showTopActionsBarKey, true)
    var showvisthumbnail by rememberPreference(showvisthumbnailKey, true)
    var blackBackgroundForVisThumbnail by rememberPreference(blackBackgroundForVisThumbnailKey, true)
    var showVisualizerButtons by rememberPreference(showVisualizerButtonsKey, true)
    var showsongs by rememberPreference(showsongsKey, SongsNumber.`2`)
    var showalbumcover by rememberPreference(showalbumcoverKey, true)
    var playerType by rememberPreference(playerTypeKey, PlayerType.Essential)
    var showVisualizerStateKey by rememberPreference(showVisualizerStateKey, false)
    var topPadding by rememberPreference(topPaddingKey, true)
    val searchCtx_0_5 = search.inputValue.isBlank() || stringResource(R.string.player_behavior_and_visuals).contains(search.inputValue, true) || stringResource(R.string.settings_player_top_action_bar_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_lyrics_top_spacing_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_next_queue_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_next_two_songs_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_queue_visible_count_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_album_cover_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_disable_text_marquee_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_disable_text_marquee_description).contains(search.inputValue, true) || stringResource(R.string.player_rotating_buttons).contains(search.inputValue, true) || stringResource(R.string.player_enable_rotation_buttons).contains(search.inputValue, true) || stringResource(R.string.settings_player_tap_artwork_toggle_lyrics_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_tap_artwork_toggle_lyrics_description).contains(search.inputValue, true) || stringResource(R.string.settings_lyrics_seek_on_line_press_label).contains(search.inputValue, true) || stringResource(R.string.settings_lyrics_remember_state_label).contains(search.inputValue, true) || stringResource(R.string.settings_lyrics_remember_state_description).contains(search.inputValue, true) || stringResource(R.string.settings_lyrics_match_player_background_label).contains(search.inputValue, true) || stringResource(R.string.player_enable_lyrics_popup_message).contains(search.inputValue, true) || stringResource(R.string.settings_player_background_progress_label).contains(search.inputValue, true) || stringResource(R.string.visualizer_title).contains(search.inputValue, true) || stringResource(R.string.settings_visualizer_remember_state_label).contains(search.inputValue, true) || stringResource(R.string.settings_visualizer_remember_state_description).contains(search.inputValue, true) || stringResource(R.string.visualizer_microphone_permission_message).contains(search.inputValue, true)
    AnimatedVisibility(
                visible = searchCtx_0_5,
                enter = settingsCardEnter,
                exit = settingsCardExit
            ) {
        SettingsSectionCard(
                    modifier = Modifier.testTag("appearance.card.behavior"),
            title = stringResource(R.string.player_behavior_and_visuals),
            icon = R.drawable.equalizer,
            content = {
            if (!isLandscape) {
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_top_action_bar_visible_label).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_player_top_action_bar_visible_label).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_behavior_and_visuals.show_player_top_actions_bar"),
                        icon = R.drawable.enqueue,
                            title = stringResource(R.string.settings_player_top_action_bar_visible_label),
                            text = stringResource(R.string.player_show_player_top_actions_bar_description),
                            isChecked = showTopActionsBar,
                            onCheckedChange = { showTopActionsBar = it }
                        )
                    }

                if (!showTopActionsBar) {
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_top_spacing_label).contains(
                            search.inputValue,
                            true
                        )
                    )
                        if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_top_spacing_label).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_behavior_and_visuals.blankspace"),
                        icon = R.drawable.space,
                                title = stringResource(R.string.settings_lyrics_top_spacing_label),
                                text = stringResource(R.string.player_top_spacing_description),
                                isChecked = topPadding,
                                onCheckedChange = { topPadding = it }
                            )
                        }
                }
            }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_next_queue_visible_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_next_queue_visible_label).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_behavior_and_visuals.show_next_songs_in_player"),
                        icon = R.drawable.arrow_forward,
                        title = stringResource(R.string.settings_player_next_queue_visible_label),
                        text = stringResource(R.string.player_show_next_songs_in_player_description),
                        isChecked = showNextSongsInPlayer,
                        onCheckedChange = { showNextSongsInPlayer = it }
                    )
                }
            AnimatedVisibility( visible = showNextSongsInPlayer) {
              Column {
                  if (search.inputValue.isBlank() || stringResource(R.string.settings_player_next_two_songs_visible_label).contains(search.inputValue,true))
                      OtherEnumValueSelectorSettingsEntry(
                    text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_queue_preview_song_count_description), showsongs.name),
                    icon = R.drawable.information,
                          title = stringResource(R.string.settings_player_queue_visible_count_label),
                          selectedValue = showsongs,
                          onValueSelected = {
                              showsongs = it
                          },
                          valueText = { it.name },
                          modifier = (Modifier
                              .padding(start = 25.dp)).settingsSearchTarget("appearance.player_behavior_and_visuals.songs_number_to_show")
                      )


                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_album_cover_visible_label).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_player_album_cover_visible_label).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        icon = R.drawable.album,
                            title = stringResource(R.string.settings_player_album_cover_visible_label),
                            text = stringResource(R.string.player_queue_preview_artwork_description),
                            isChecked = showalbumcover,
                            onCheckedChange = { showalbumcover = it },
                              modifier = (Modifier.padding(start = 25.dp)).settingsSearchTarget("appearance.player_behavior_and_visuals.showalbumcover")
                          )
                    }
              }
            }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_disable_text_marquee_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_disable_text_marquee_label).contains(search.inputValue, true) || (stringResource(R.string.settings_player_disable_text_marquee_description)).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_behavior_and_visuals.disable_scrolling_text"),
                        icon = R.drawable.text,
                        title = stringResource(R.string.settings_player_disable_text_marquee_label),
                        text = stringResource(R.string.settings_player_disable_text_marquee_description),
                        isChecked = disableScrollingText,
                        onCheckedChange = { disableScrollingText = it }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(if (playerType == PlayerType.Modern && !isLandscape) R.string.settings_player_disable_horizontal_swipe_label else R.string.settings_player_disable_vertical_song_swipe_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(if (playerType == PlayerType.Modern && !isLandscape) R.string.settings_player_disable_vertical_song_swipe_label else R.string.settings_player_disable_horizontal_swipe_label).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget(if (playerType == PlayerType.Modern && !isLandscape) "appearance.player_behavior_and_visuals.disable_vertical_swipe" else "appearance.player_behavior_and_visuals.disable_horizontal_swipe"),
                        icon = R.drawable.sync,
                        title = stringResource(if (playerType == PlayerType.Modern && !isLandscape) R.string.settings_player_disable_vertical_song_swipe_label else R.string.settings_player_disable_horizontal_swipe_label),
                        text = stringResource(if (playerType == PlayerType.Modern && !isLandscape) R.string.settings_player_vertical_swipe_expansion_description else R.string.settings_player_disable_song_swipe_label),
                        isChecked = disablePlayerHorizontalSwipe,
                        onCheckedChange = { disablePlayerHorizontalSwipe = it }
                    )
                }

            AnimatedVisibility(visible = !disablePlayerHorizontalSwipe) {
                if (search.inputValue.isBlank() || stringResource(R.string.player_swipe_sensitivity).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_track_swipe_sensitivity_description), playerSwipeSensitivity.text),
                        icon = R.drawable.gesture,
                        title = stringResource(R.string.player_swipe_sensitivity),
                        selectedValue = playerSwipeSensitivity,
                        onValueSelected = { playerSwipeSensitivity = it },
                        valueText = { it.text },
                        modifier = (Modifier.padding(start = 25.dp)).settingsSearchTarget("appearance.player_behavior_and_visuals.player_swipe_sensitivity")
                    )
                }
            }

            if (search.inputValue.isBlank() || stringResource(R.string.player_rotating_buttons).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.player_rotating_buttons).contains(search.inputValue, true) || (stringResource(R.string.player_enable_rotation_buttons)).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_behavior_and_visuals.player_rotating_buttons"),
                        icon = R.drawable.refresh,
                        title = stringResource(R.string.player_rotating_buttons),
                        text = stringResource(R.string.player_enable_rotation_buttons),
                        isChecked = effectRotationEnabled,
                        onCheckedChange = { effectRotationEnabled = it }
                    )
                }



            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_background_progress_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_background_progress_label).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_background_progress_description), backgroundProgress.text),
                        modifier = Modifier.settingsSearchTarget("appearance.player_behavior_and_visuals.background_progress_bar"),
                        icon = R.drawable.horizontal_straight_line,
                        title = stringResource(R.string.settings_player_background_progress_label),
                        selectedValue = backgroundProgress,
                        onValueSelected = {
                            backgroundProgress = it
                        },
                        valueText = { it.text },
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.visualizer_title).contains(
                    search.inputValue,
                    true
                )
            ) {
                if (search.inputValue.isBlank() || stringResource(R.string.visualizer_title).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_behavior_and_visuals.visualizer"),
                        icon = R.drawable.sound_effect,
                        title = stringResource(R.string.visualizer_title),
                        text = stringResource(R.string.visualizer_enabled_description),
                        isChecked = visualizerEnabled,
                        onCheckedChange = { visualizerEnabled = it }
                    )
                }


                AnimatedVisibility(visible = visualizerEnabled) {
                    Column {
                        if (search.inputValue.isBlank() || stringResource(R.string.settings_visualizer_remember_state_label).contains(search.inputValue, true) || (stringResource(R.string.settings_visualizer_remember_state_description)).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                        icon = R.drawable.bookmark,
                                title = stringResource(R.string.settings_visualizer_remember_state_label),
                                text = stringResource(R.string.settings_visualizer_remember_state_description),
                                isChecked = showVisualizerStateKey,
                                onCheckedChange = { showVisualizerStateKey = it },
                                modifier = (Modifier.padding(start = 25.dp)).settingsSearchTarget("appearance.player_behavior_and_visuals.save_visualizer_state")
                            )
                        }

                        if (search.inputValue.isBlank() || stringResource(R.string.visualizer_color_source).contains(search.inputValue, true)) {
                            OtherEnumValueSelectorSettingsEntry(
                                text = stringResource(R.string.settings_description_with_value, stringResource(R.string.visualizer_color_source_description), stringResource(when(visualizerWhiteColorOption) { enums.VisualizerWhiteColorOption.White -> R.string.common_color_white; enums.VisualizerWhiteColorOption.Theme -> R.string.common_color_source_theme; enums.VisualizerWhiteColorOption.Cover -> R.string.common_color_source_artwork; enums.VisualizerWhiteColorOption.Custom -> R.string.common_color_custom })),
                                title = stringResource(R.string.visualizer_color_source),
                                selectedValue = visualizerWhiteColorOption,
                                onValueSelected = { visualizerWhiteColorOption = it },
                                valueText = { stringResource(when(it) { enums.VisualizerWhiteColorOption.White -> R.string.common_color_white; enums.VisualizerWhiteColorOption.Theme -> R.string.common_color_source_theme; enums.VisualizerWhiteColorOption.Cover -> R.string.common_color_source_artwork; enums.VisualizerWhiteColorOption.Custom -> R.string.common_color_custom }) },
                                modifier = (Modifier.padding(start = 25.dp)).settingsSearchTarget("appearance.player_behavior_and_visuals.visualizer_white_color_option"),
                                icon = R.drawable.color_palette
                            )
                        }

                        if (search.inputValue.isBlank() || stringResource(R.string.visualizer_line_thickness).contains(search.inputValue, true)) {
                            SliderSettingsEntry(
                                icon = R.drawable.settings,
                                title = stringResource(R.string.visualizer_line_thickness),
                                text = stringResource(R.string.visualizer_line_thickness_description),
                                state = visualizerLineThickness,
                                onSlide = { visualizerLineThickness = kotlin.math.round(it) },
                                range = 1f..10f,
                                stepSize = 0f,
                                defaultValue = 6f,
                                drawValuePoints = true,
                                isIntegerOnly = true,
                                toDisplay = { it.toInt().toString() },
                                modifier = (Modifier.padding(start = 25.dp)).settingsSearchTarget("appearance.player_behavior_and_visuals.visualizer_line_thickness")
                            )
                        }

                        AnimatedVisibility(visible = visualizerWhiteColorOption == enums.VisualizerWhiteColorOption.Custom) {
                            var showColorPicker by remember { mutableStateOf(false) }
                            val customColorString = stringResource(R.string.common_color_custom)
                            if (search.inputValue.isBlank() || true) {
                                OtherSettingsEntry(
                                    title = customColorString,
                                    text = stringResource(R.string.visualizer_custom_color_description),
                                    icon = R.drawable.color_palette,
                                    onClick = { showColorPicker = true },
                                    modifier = (Modifier.padding(start = 40.dp)).settingsSearchTarget("appearance.player_behavior_and_visuals.color_custom"),
                                    trailingContent = {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(androidx.compose.ui.graphics.Color(visualizerCustomColor))
                                                .border(BorderStroke(1.dp, androidx.compose.ui.graphics.Color.LightGray))
                                        )
                                    }
                                )
                            }
                            DialogPresence(visible = showColorPicker) {
                                DialogColorPicker(onDismiss = { showColorPicker = false }) {
                                    visualizerCustomColor = it.toArgb()
                                    showColorPicker = false
                                    Toaster.n(R.string.settings_theme_color_applied_format, customColorString)
                                }
                            }
                        }

                        if (search.inputValue.isBlank() || stringResource(R.string.settings_visualizer_artwork_visible_label).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                                icon = R.drawable.equalizer,
                                title = stringResource(R.string.settings_visualizer_artwork_visible_label),
                                text = stringResource(R.string.visualizer_artwork_visible_description),
                                isChecked = showvisthumbnail,
                                onCheckedChange = { showvisthumbnail = it },
                                modifier = (Modifier.padding(start = 25.dp)).settingsSearchTarget("appearance.player_behavior_and_visuals.showvisthumbnail")
                            )
                        }
                        AnimatedVisibility(visible = showvisthumbnail) {
                            if (search.inputValue.isBlank() || stringResource(R.string.visualizer_dark_background_title).contains(search.inputValue, true)) {
                                OtherSwitchSettingEntry(
                                    icon = R.drawable.images_sharp,
                                    title = stringResource(R.string.visualizer_dark_background_title),
                                    text = stringResource(R.string.visualizer_dark_background_description),
                                    isChecked = blackBackgroundForVisThumbnail,
                                    onCheckedChange = { blackBackgroundForVisThumbnail = it },
                                    modifier = (Modifier.padding(start = 45.dp)).settingsSearchTarget("appearance.player_behavior_and_visuals.black_background_for_visualizer")
                                )
                            }
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string.visualizer_auto_hide_buttons_title).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                                icon = R.drawable.menu,
                                title = stringResource(R.string.visualizer_auto_hide_buttons_title),
                                text = stringResource(R.string.visualizer_auto_hide_buttons_description),
                                isChecked = showVisualizerButtons,
                                onCheckedChange = { showVisualizerButtons = it },
                                modifier = (Modifier.padding(start = 25.dp)).settingsSearchTarget("appearance.player_behavior_and_visuals.show_visualizer_buttons")
                            )
                        }
                    }
                }
            }

            ImportantSettingsDescription(text = stringResource(R.string.visualizer_microphone_permission_message))

                    }
                )
                }
}
