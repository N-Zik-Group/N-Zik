package app.n_zik.android.components.settings.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import app.it.fast4x.rimusic.utils.showRemainingSongTimeKey
import app.it.fast4x.rimusic.utils.showSkipTimeButtonsKey
import app.it.fast4x.rimusic.utils.showTotalTimeQueueKey
import app.it.fast4x.rimusic.utils.textoutlineKey
import app.n_zik.android.components.tab.Search
import app.n_zik.android.enums.ShakeSensitivityTheme
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.OtherEnumValueSelectorSettingsEntry
import androidx.compose.ui.platform.testTag
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit

@OptIn(ExperimentalAnimationApi::class)
@UnstableApi
@Composable
internal fun AppearanceColorsCard(search: Search) {
    var blackgradient by rememberPreference(blackgradientKey, false)
    var bottomgradient by rememberPreference(bottomgradientKey, false)
    var textoutline by rememberPreference(textoutlineKey, false)
    var showTotalTimeQueue by rememberPreference(showTotalTimeQueueKey, true)
    var showRemainingSongTime by rememberPreference(showRemainingSongTimeKey, true)
    var showSkipTimeButtons by rememberPreference(showSkipTimeButtonsKey, true)
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
    val searchCtx_0_4 = search.inputValue.isBlank() || stringResource(R.string.colors_and_background).contains(search.inputValue, true) || stringResource(R.string.background_colors).contains(search.inputValue, true) || stringResource(R.string.gradienttype).contains(search.inputValue, true) || stringResource(R.string.blackgradient).contains(search.inputValue, true) || stringResource(R.string.albumCoverRotation).contains(search.inputValue, true) || stringResource(R.string.bottomgradient).contains(search.inputValue, true) || stringResource(R.string.textoutline).contains(search.inputValue, true) || stringResource(R.string.show_total_time_of_queue).contains(search.inputValue, true) || stringResource(R.string.show_remaining_song_time).contains(search.inputValue, true) || stringResource(R.string.shake_sensitivity_theme).contains(search.inputValue, true)
    AnimatedVisibility(
                visible = searchCtx_0_4,
                enter = settingsCardEnter,
                exit = settingsCardExit
            ) {
        SettingsSectionCard(
                    modifier = Modifier.testTag("appearance.card.colors"),
            title = stringResource(R.string.colors_and_background),
            icon = R.drawable.color_palette,
            content = {
            if (search.inputValue.isBlank() || stringResource(R.string.background_colors).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.background_colors).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        icon = R.drawable.droplet,
                        title = stringResource(R.string.background_colors),
                        selectedValue = playerBackgroundColors,
                        onValueSelected = {
                            playerBackgroundColors = it
                        },
                        valueText = { it.text }
                    )
                }

            AnimatedVisibility(visible = playerBackgroundColors == PlayerBackgroundColors.AnimatedGradient) {
                if (search.inputValue.isBlank() || stringResource(R.string.gradienttype).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.gradienttype).contains(search.inputValue, true)) {
                        OtherEnumValueSelectorSettingsEntry(
                        icon = R.drawable.color_palette,
                            title = stringResource(R.string.gradienttype),
                            selectedValue = animatedGradient,
                            onValueSelected = {
                                animatedGradient = it
                            },
                            valueText = { it.text },
                            modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.AnimatedGradient) 25.dp else 0.dp)
                        )
                    }
            }

            val isM3E = animatedGradient == AnimatedGradient.M3EMorphingTheme || animatedGradient == AnimatedGradient.M3EMorphingCover
            AnimatedVisibility(visible = playerBackgroundColors == PlayerBackgroundColors.AnimatedGradient && isM3E) {
                if (search.inputValue.isBlank() || stringResource(R.string.shake_sensitivity_theme).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        icon = R.drawable.shake_gesture,
                        title = stringResource(R.string.shake_sensitivity_theme),
                        selectedValue = shakeSensitivityTheme,
                        onValueSelected = { shakeSensitivityTheme = it },
                        valueText = { it.text },
                        modifier = Modifier.padding(start = 25.dp)
                    )
                }
            }
            var isRotatingCoverEnabled by rememberPreference( rotatingAlbumCoverKey, false )
            AnimatedVisibility( playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor ) {
                if ( search.inputValue.isBlank() || stringResource( R.string.rotating_cover_title ).contains(search.inputValue, true) )
                    if (search.inputValue.isBlank() || stringResource( R.string.rotating_cover_title ).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        icon = R.drawable.sync,
                            title = stringResource( R.string.rotating_cover_title ),
                            text = "",
                            isChecked = isRotatingCoverEnabled,
                            onCheckedChange = { isRotatingCoverEnabled = it },
                            modifier = Modifier.padding( start = 25.dp )
                        )
                    }
            }


            if ((playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient) || (playerBackgroundColors == PlayerBackgroundColors.ThemeColorGradient))
                if (search.inputValue.isBlank() || stringResource(R.string.blackgradient).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.blackgradient).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        icon = R.drawable.drop_half_fill,
                            title = stringResource(R.string.blackgradient),
                            text = "",
                            isChecked = blackgradient,
                            onCheckedChange = { blackgradient = it }
                        )
                    }

            if ((playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) && (playerType == PlayerType.Modern))
                if (search.inputValue.isBlank() || stringResource(R.string.albumCoverRotation).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.albumCoverRotation).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        icon = R.drawable.sync,
                            title = stringResource(R.string.albumCoverRotation),
                            text = "",
                            isChecked = albumCoverRotation,
                            onCheckedChange = { albumCoverRotation = it },
                            modifier = Modifier
                                .padding(start = 25.dp)
                        )
                    }

            if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor)
                if (search.inputValue.isBlank() || stringResource(R.string.bottomgradient).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.bottomgradient).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        icon = R.drawable.droplet,
                            title = stringResource(R.string.bottomgradient),
                            text = "",
                            isChecked = bottomgradient,
                            onCheckedChange = { bottomgradient = it }
                        )
                    }
            if (search.inputValue.isBlank() || stringResource(R.string.textoutline).contains(
                  search.inputValue,
                  true
                  )
             )
                 if (search.inputValue.isBlank() || stringResource(R.string.textoutline).contains(search.inputValue, true)) {
                     OtherSwitchSettingEntry(
                        icon = R.drawable.text,
                         title = stringResource(R.string.textoutline),
                         text = "",
                         isChecked = textoutline,
                         onCheckedChange = { textoutline = it }
                     )
                 }

           if (search.inputValue.isBlank() || stringResource(R.string.show_total_time_of_queue).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.show_total_time_of_queue).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        icon = R.drawable.time,
                        title = stringResource(R.string.show_total_time_of_queue),
                        text = "",
                        isChecked = showTotalTimeQueue,
                        onCheckedChange = { showTotalTimeQueue = it }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.show_remaining_song_time).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.show_remaining_song_time).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        icon = R.drawable.time,
                        title = stringResource(R.string.show_remaining_song_time),
                        text = "",
                        isChecked = showRemainingSongTime,
                        onCheckedChange = { showRemainingSongTime = it }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.skip_time_buttons).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.skip_time_buttons).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        title = stringResource(R.string.skip_time_buttons),
                        text = "",
                        isChecked = showSkipTimeButtons,
                        onCheckedChange = { showSkipTimeButtons = it },
                        icon = R.drawable.play_forward
                    )
                }

            }
        )
            /* Removed Spacer */
            }
}
