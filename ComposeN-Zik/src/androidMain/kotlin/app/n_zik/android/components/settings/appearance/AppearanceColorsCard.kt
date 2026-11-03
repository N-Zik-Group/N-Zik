package app.n_zik.android.components.settings.appearance

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.it.fast4x.rimusic.enums.AnimatedGradient
import app.it.fast4x.rimusic.enums.PlayerBackgroundColors
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.utils.albumCoverRotationKey
import app.it.fast4x.rimusic.utils.animatedGradientKey
import app.it.fast4x.rimusic.utils.blackgradientKey
import app.it.fast4x.rimusic.utils.bottomgradientKey
import app.it.fast4x.rimusic.utils.shakeSensitivityThemeKey
import app.it.fast4x.rimusic.utils.playerBackgroundColorsKey
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.rotatingAlbumCoverKey
import app.it.fast4x.rimusic.utils.textoutlineKey
import app.n_zik.android.components.tab.Search
import app.n_zik.android.enums.ShakeSensitivityTheme
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.OtherEnumValueSelectorSettingsEntry
import androidx.compose.ui.platform.testTag
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.playerBackgroundColorLabel
import app.n_zik.android.components.settings.common.animatedGradientLabel

@OptIn(ExperimentalAnimationApi::class)
@UnstableApi
@Composable
internal fun AppearanceColorsCard(search: Search) {
    val context = LocalContext.current
    var blackgradient by rememberPreference(blackgradientKey, false)
    var bottomgradient by rememberPreference(bottomgradientKey, false)
    var textoutline by rememberPreference(textoutlineKey, false)
    var playerBackgroundColors by rememberPreference(
        playerBackgroundColorsKey,
        PlayerBackgroundColors.AnimatedGradient
    )
    var playerType by rememberPreference(playerTypeKey, PlayerType.Essential)
    var animatedGradient by rememberPreference(
        animatedGradientKey,
        AnimatedGradient.M3EMorphingCover
    )
    var shakeSensitivityTheme by rememberPreference(shakeSensitivityThemeKey, ShakeSensitivityTheme.Disabled)
    var albumCoverRotation by rememberPreference(albumCoverRotationKey, false)
    val searchCtx_0_4 = search.inputValue.isBlank() || stringResource(R.string.settings_player_background_section_title).contains(search.inputValue, true) || stringResource(R.string.settings_player_background_colors_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_gradient_style_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_bottom_black_gradient_label).contains(search.inputValue, true) || stringResource(R.string.player_artwork_rotation).contains(search.inputValue, true) || stringResource(R.string.settings_player_action_bar_gradient_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_text_outline_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_m3e_motion_sensitivity_label).contains(search.inputValue, true)
    AnimatedVisibility(
                visible = searchCtx_0_4,
                enter = settingsCardEnter,
                exit = settingsCardExit
            ) {
        SettingsSectionCard(
                    modifier = Modifier.testTag("appearance.card.colors"),
            title = stringResource(R.string.settings_player_background_section_title),
            icon = R.drawable.color_palette,
            content = {
            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_background_colors_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_background_colors_label).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_background_style_description), playerBackgroundColorLabel(playerBackgroundColors, context::getString)),
                        modifier = Modifier.settingsSearchTarget("appearance.colors_and_background.background_colors"),
                        icon = R.drawable.droplet,
                        title = stringResource(R.string.settings_player_background_colors_label),
                        selectedValue = playerBackgroundColors,
                        onValueSelected = {
                            playerBackgroundColors = it
                        },
                        valueText = { playerBackgroundColorLabel(it, context::getString) }
                    )
                }

            AnimatedVisibility(visible = playerBackgroundColors == PlayerBackgroundColors.AnimatedGradient) {
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_gradient_style_label).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_player_gradient_style_label).contains(search.inputValue, true)) {
                        OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_background_animation_description), animatedGradientLabel(animatedGradient, context::getString)),
                        icon = R.drawable.color_palette,
                            title = stringResource(R.string.settings_player_gradient_style_label),
                            selectedValue = animatedGradient,
                            onValueSelected = {
                                animatedGradient = it
                            },
                            valueText = { animatedGradientLabel(it, context::getString) },
                            modifier = (Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.AnimatedGradient) 25.dp else 0.dp)).settingsSearchTarget("appearance.colors_and_background.gradienttype")
                        )
                    }
            }

            val isM3E = animatedGradient == AnimatedGradient.M3EMorphingTheme || animatedGradient == AnimatedGradient.M3EMorphingCover
            AnimatedVisibility(visible = playerBackgroundColors == PlayerBackgroundColors.AnimatedGradient && isM3E) {
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_m3e_motion_sensitivity_label).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_background_shake_sensitivity_description), shakeSensitivityTheme.text),
                        icon = R.drawable.shake_gesture,
                        title = stringResource(R.string.settings_player_m3e_motion_sensitivity_label),
                        selectedValue = shakeSensitivityTheme,
                        onValueSelected = { shakeSensitivityTheme = it },
                        valueText = { it.text },
                        modifier = (Modifier.padding(start = 25.dp)).settingsSearchTarget("appearance.colors_and_background.shake_sensitivity_theme")
                    )
                }
            }
            var isRotatingCoverEnabled by rememberPreference( rotatingAlbumCoverKey, false )
            AnimatedVisibility( playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor ) {
                if ( search.inputValue.isBlank() || stringResource( R.string.settings_player_rotating_artwork_title ).contains(search.inputValue, true) )
                    if (search.inputValue.isBlank() || stringResource( R.string.settings_player_rotating_artwork_title ).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        icon = R.drawable.sync,
                            title = stringResource( R.string.settings_player_rotating_artwork_title ),
                            text = stringResource(R.string.player_background_rotating_cover_description),
                            isChecked = isRotatingCoverEnabled,
                            onCheckedChange = { isRotatingCoverEnabled = it },
                            modifier = (Modifier.padding( start = 25.dp )).settingsSearchTarget("appearance.colors_and_background.rotating_cover_title")
                        )
                    }
            }


            if ((playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient) || (playerBackgroundColors == PlayerBackgroundColors.ThemeColorGradient))
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_bottom_black_gradient_label).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_player_bottom_black_gradient_label).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.colors_and_background.blackgradient"),
                        icon = R.drawable.drop_half_fill,
                            title = stringResource(R.string.settings_player_bottom_black_gradient_label),
                            text = stringResource(R.string.player_background_dark_gradient_description),
                            isChecked = blackgradient,
                            onCheckedChange = { blackgradient = it }
                        )
                    }

            if ((playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) && (playerType == PlayerType.Modern))
                if (search.inputValue.isBlank() || stringResource(R.string.player_artwork_rotation).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.player_artwork_rotation).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        icon = R.drawable.sync,
                            title = stringResource(R.string.player_artwork_rotation),
                            text = stringResource(R.string.player_background_artwork_rotation_description),
                            isChecked = albumCoverRotation,
                            onCheckedChange = { albumCoverRotation = it },
                            modifier = (Modifier
                                .padding(start = 25.dp)).settingsSearchTarget("appearance.colors_and_background.albumCoverRotation")
                        )
                    }

            if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor)
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_action_bar_gradient_label).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_player_action_bar_gradient_label).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.colors_and_background.bottomgradient"),
                        icon = R.drawable.droplet,
                            title = stringResource(R.string.settings_player_action_bar_gradient_label),
                            text = stringResource(R.string.player_background_action_bar_gradient_description),
                            isChecked = bottomgradient,
                            onCheckedChange = { bottomgradient = it }
                        )
                    }
            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_text_outline_label).contains(
                  search.inputValue,
                  true
                  )
             )
                 if (search.inputValue.isBlank() || stringResource(R.string.settings_player_text_outline_label).contains(search.inputValue, true)) {
                     OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.colors_and_background.textoutline"),
                        icon = R.drawable.text,
                         title = stringResource(R.string.settings_player_text_outline_label),
                         text = stringResource(R.string.player_background_text_outline_description),
                         isChecked = textoutline,
                         onCheckedChange = { textoutline = it }
                     )
                 }


            }
        )
            }
}
