package app.n_zik.android.components.settings.appearance

import app.n_zik.android.utils.effectRotationDefault
import app.n_zik.android.components.settings.common.CardCategoryLabel
import app.n_zik.android.components.settings.common.SettingsCardSectionHeading
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.enums.BackgroundProgress
import app.n_zik.android.enums.PlayerType
import app.n_zik.android.utils.backgroundProgressKey
import app.n_zik.android.utils.disablePlayerHorizontalSwipeKey
import app.n_zik.android.utils.playerSwipeSensitivityKey
import app.n_zik.android.enums.PlayerSwipeSensitivity
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.effectRotationKey
import app.n_zik.android.utils.isLandscape
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.showVisualizerStateKey
import app.n_zik.android.utils.showvisthumbnailKey
import app.n_zik.android.utils.showVisualizerButtonsKey
import app.n_zik.android.utils.blackBackgroundForVisThumbnailKey
import app.n_zik.android.utils.visualizerEnabledKey
import app.n_zik.android.components.tab.Search
import app.n_zik.android.utils.visualizerWhiteColorOptionKey
import app.n_zik.android.utils.visualizerLineThicknessKey
import app.n_zik.android.utils.visualizerCustomColorKey
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.ImportantSettingsDescription
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.ColorSettingEntry
import app.n_zik.android.components.settings.common.OtherEnumValueSelectorSettingsEntry
import app.n_zik.android.components.settings.common.SliderSettingsEntry
import androidx.compose.ui.platform.testTag
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit

private const val PlayerBehaviorTarget = "appearance.player_behavior_and_visuals"

internal enum class PlayerSongSwipeSetting(val target: String, val labelRes: Int, val descriptionRes: Int) {
    Horizontal(
        "$PlayerBehaviorTarget.disable_horizontal_swipe",
        R.string.settings_player_disable_horizontal_swipe_label,
        R.string.settings_player_disable_song_swipe_label,
    ),
    Vertical(
        "$PlayerBehaviorTarget.disable_vertical_swipe",
        R.string.settings_player_disable_vertical_song_swipe_label,
        R.string.settings_player_vertical_swipe_expansion_description,
    );

    companion object {
        fun forLayout(playerType: PlayerType, isLandscape: Boolean): PlayerSongSwipeSetting =
            if (playerType == PlayerType.Modern && !isLandscape) Vertical else Horizontal
    }
}

internal fun appearanceBehaviorSearchFallback(target: String, hasTarget: (String) -> Boolean): String? {
    val candidates = when (target) {
        "$PlayerBehaviorTarget.disable_vertical_swipe" -> listOf("$PlayerBehaviorTarget.disable_horizontal_swipe")
        "$PlayerBehaviorTarget.disable_horizontal_swipe" -> listOf("$PlayerBehaviorTarget.disable_vertical_swipe")
        "$PlayerBehaviorTarget.player_swipe_sensitivity" -> listOf(
            "$PlayerBehaviorTarget.disable_vertical_swipe", "$PlayerBehaviorTarget.disable_horizontal_swipe",
        )
        "$PlayerBehaviorTarget.color_custom" -> listOf(
            "$PlayerBehaviorTarget.visualizer_white_color_option", "$PlayerBehaviorTarget.visualizer",
        )
        "$PlayerBehaviorTarget.black_background_for_visualizer" -> listOf(
            "$PlayerBehaviorTarget.showvisthumbnail", "$PlayerBehaviorTarget.visualizer",
        )
        "$PlayerBehaviorTarget.save_visualizer_state", "$PlayerBehaviorTarget.visualizer_white_color_option",
        "$PlayerBehaviorTarget.visualizer_line_thickness", "$PlayerBehaviorTarget.showvisthumbnail",
        "$PlayerBehaviorTarget.show_visualizer_buttons" -> listOf("$PlayerBehaviorTarget.visualizer")
        else -> return null
    }
    return candidates.firstOrNull(hasTarget)
}

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
    var effectRotationEnabled by rememberPreference(effectRotationKey, effectRotationDefault)
    var backgroundProgress by rememberPreference(
        backgroundProgressKey,
        BackgroundProgress.MiniPlayer
    )
    var showvisthumbnail by rememberPreference(showvisthumbnailKey, true)
    var blackBackgroundForVisThumbnail by rememberPreference(blackBackgroundForVisThumbnailKey, true)
    var showVisualizerButtons by rememberPreference(showVisualizerButtonsKey, true)
    val playerType by rememberPreference(playerTypeKey, PlayerType.Essential)
    val swipeSetting = PlayerSongSwipeSetting.forLayout(playerType, isLandscape)
    var showVisualizerStateKey by rememberPreference(showVisualizerStateKey, false)
    val searchCtx_0_5 = search.inputValue.isBlank() || stringResource(R.string.player_behavior_and_visuals).contains(search.inputValue, true) || stringResource(R.string.settings_player_disable_text_marquee_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_disable_text_marquee_description).contains(search.inputValue, true) || stringResource(R.string.player_rotating_buttons).contains(search.inputValue, true) || stringResource(R.string.player_enable_rotation_buttons).contains(search.inputValue, true) || stringResource(R.string.settings_player_tap_artwork_toggle_lyrics_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_tap_artwork_toggle_lyrics_description).contains(search.inputValue, true) || stringResource(R.string.settings_lyrics_seek_on_line_press_label).contains(search.inputValue, true) || stringResource(R.string.settings_lyrics_remember_state_label).contains(search.inputValue, true) || stringResource(R.string.settings_lyrics_remember_state_description).contains(search.inputValue, true) || stringResource(R.string.settings_lyrics_match_player_background_label).contains(search.inputValue, true) || stringResource(R.string.player_enable_lyrics_popup_message).contains(search.inputValue, true) || stringResource(R.string.settings_player_background_progress_label).contains(search.inputValue, true) || stringResource(R.string.visualizer_title).contains(search.inputValue, true) || stringResource(R.string.settings_visualizer_remember_state_label).contains(search.inputValue, true) || stringResource(R.string.settings_visualizer_remember_state_description).contains(search.inputValue, true) || stringResource(R.string.visualizer_microphone_permission_message).contains(search.inputValue, true)
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
                if (search.inputValue.isBlank()) CardCategoryLabel(stringResource(R.string.settings_group_player_gestures))
                if (search.inputValue.isBlank() || stringResource(swipeSetting.labelRes).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget(swipeSetting.target),
                        icon = R.drawable.sync,
                        title = stringResource(swipeSetting.labelRes),
                        text = stringResource(swipeSetting.descriptionRes),
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

            if (search.inputValue.isBlank()) SettingsCardSectionHeading(R.string.settings_group_text_and_visualizer)

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
                            ColorSettingEntry(
                                title = stringResource(R.string.common_color_custom),
                                text = stringResource(R.string.visualizer_custom_color_description),
                                color = androidx.compose.ui.graphics.Color(visualizerCustomColor),
                                onColorSelected = { visualizerCustomColor = it.toArgb() },
                                modifier = Modifier.padding(start = 40.dp)
                                    .settingsSearchTarget("appearance.player_behavior_and_visuals.color_custom"),
                            )
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
