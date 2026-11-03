package app.n_zik.android.components.settings.appearance

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import app.it.fast4x.rimusic.utils.clickOnLyricsTextKey
import app.it.fast4x.rimusic.utils.playerEnableLyricsPopupMessageKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showBackgroundLyricsKey
import app.it.fast4x.rimusic.utils.showLyricsStateKey
import app.it.fast4x.rimusic.utils.showlyricsthumbnailKey
import app.it.fast4x.rimusic.utils.thumbnailTapEnabledKey
import app.n_zik.android.components.tab.Search
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.components.dialog.common.DialogColorPicker
import app.n_zik.android.enums.lyrics.LyricsColor
import app.it.fast4x.rimusic.utils.lyricsColorKey
import app.it.fast4x.rimusic.utils.lyricsCustomColorKey
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherEnumValueSelectorSettingsEntry
import androidx.compose.ui.platform.testTag
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit

@OptIn(ExperimentalAnimationApi::class)
@UnstableApi
@Composable
internal fun AppearanceLyricsCard(search: Search) {
    var showlyricsthumbnail by rememberPreference(showlyricsthumbnailKey, true)
    var thumbnailTapEnabled by rememberPreference(thumbnailTapEnabledKey, true)
    var clickLyricsText by rememberPreference(clickOnLyricsTextKey, true)
    var lyricsColor by rememberPreference(lyricsColorKey, LyricsColor.White)
    var lyricsCustomColor by rememberPreference(lyricsCustomColorKey, android.graphics.Color.WHITE)
    var showBackgroundLyrics by rememberPreference(showBackgroundLyricsKey, false)
    var playerEnableLyricsPopupMessage by rememberPreference(
        playerEnableLyricsPopupMessageKey,
        true
    )
    var showLyricsStateKey by rememberPreference(showLyricsStateKey, false)
    val searchCtx_lyrics = search.inputValue.isBlank() || stringResource(R.string.settings_player_tap_artwork_toggle_lyrics_label).contains(search.inputValue, true) || stringResource(R.string.settings_lyrics_seek_on_line_press_label).contains(search.inputValue, true) || stringResource(R.string.lyrics_color).contains(search.inputValue, true) || stringResource(R.string.settings_lyrics_remember_state_label).contains(search.inputValue, true) || stringResource(R.string.settings_lyrics_match_player_background_label).contains(search.inputValue, true) || stringResource(R.string.player_enable_lyrics_popup_message).contains(search.inputValue, true)
    AnimatedVisibility(
                visible = searchCtx_lyrics,
                enter = settingsCardEnter,
                exit = settingsCardExit
            ) {
                SettingsSectionCard(
                    modifier = Modifier.testTag("appearance.card.lyrics"),
                    title = stringResource(R.string.lyrics_title),
                    icon = R.drawable.song_lyrics,
                    content = {
            if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_artwork_visible_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_artwork_visible_label).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.txt_lyrics.show_lyrics_thumbnail"),
                        icon = R.drawable.image,
                        title = stringResource(R.string.settings_lyrics_artwork_visible_label),
                        text = stringResource(R.string.lyrics_artwork_overlay_description),
                        isChecked = showlyricsthumbnail,
                        onCheckedChange = { showlyricsthumbnail = it }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_tap_artwork_toggle_lyrics_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_tap_artwork_toggle_lyrics_label).contains(search.inputValue, true) || (stringResource(R.string.settings_player_tap_artwork_toggle_lyrics_description)).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.txt_lyrics.toggle_lyrics"),
                        icon = R.drawable.song_lyrics,
                        title = stringResource(R.string.settings_player_tap_artwork_toggle_lyrics_label),
                        text = stringResource(R.string.settings_player_tap_artwork_toggle_lyrics_description),
                        isChecked = thumbnailTapEnabled,
                        onCheckedChange = { thumbnailTapEnabled = it }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_seek_on_line_press_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_seek_on_line_press_label).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.txt_lyrics.click_lyrics_text"),
                        icon = R.drawable.arrow_down,
                        title = stringResource(R.string.settings_lyrics_seek_on_line_press_label),
                        text = stringResource(R.string.lyrics_tap_to_seek_description),
                        isChecked = clickLyricsText,
                        onCheckedChange = { clickLyricsText = it }
                    )
                }

                if (search.inputValue.isBlank() || stringResource(R.string.lyrics_color).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.lyrics_color_source_description), stringResource(when(lyricsColor) { LyricsColor.White -> R.string.common_color_white; LyricsColor.Thememode -> R.string.common_color_source_theme; LyricsColor.Cover -> R.string.common_color_source_artwork; LyricsColor.Custom -> R.string.common_color_custom })),
                        modifier = Modifier.settingsSearchTarget("appearance.txt_lyrics.lyricscolor"),
                        title = stringResource(R.string.lyrics_color),
                        selectedValue = lyricsColor,
                        onValueSelected = { lyricsColor = it },
                        valueText = { stringResource(when(it) { LyricsColor.White -> R.string.common_color_white; LyricsColor.Thememode -> R.string.common_color_source_theme; LyricsColor.Cover -> R.string.common_color_source_artwork; LyricsColor.Custom -> R.string.common_color_custom }) },
                        icon = R.drawable.color_palette
                    )
                }

                AnimatedVisibility(visible = lyricsColor == LyricsColor.Custom) {
                    var showColorPicker by remember { mutableStateOf(false) }
                    val customColorString = stringResource(R.string.common_color_custom)
                    if (search.inputValue.isBlank() || true) {
                        OtherSettingsEntry(
                            title = customColorString,
                            text = stringResource(R.string.lyrics_custom_color_description),
                            icon = R.drawable.color_palette,
                            onClick = { showColorPicker = true },
                            modifier = (Modifier.padding(start = 25.dp)).settingsSearchTarget("appearance.txt_lyrics.color_custom"),
                            trailingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(androidx.compose.ui.graphics.Color(lyricsCustomColor))
                                        .border(BorderStroke(1.dp, androidx.compose.ui.graphics.Color.LightGray))
                                )
                            }
                        )
                    }
                    DialogPresence(visible = showColorPicker) {
                        DialogColorPicker(onDismiss = { showColorPicker = false }) {
                            lyricsCustomColor = it.toArgb()
                            showColorPicker = false
                            Toaster.n(R.string.settings_theme_color_applied_format, customColorString)
                        }
                    }
                }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_remember_state_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_remember_state_label).contains(search.inputValue, true) || (stringResource(R.string.settings_lyrics_remember_state_description)).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.txt_lyrics.save_lyrics_state"),
                        icon = R.drawable.bookmark,
                        title = stringResource(R.string.settings_lyrics_remember_state_label),
                        text = stringResource(R.string.settings_lyrics_remember_state_description),
                        isChecked = showLyricsStateKey,
                        onCheckedChange = { showLyricsStateKey = it }
                    )
                }

            if (showlyricsthumbnail)
                if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_match_player_background_label).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_match_player_background_label).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.txt_lyrics.show_background_in_lyrics"),
                        icon = R.drawable.image,
                            title = stringResource(R.string.settings_lyrics_match_player_background_label),
                            text = stringResource(R.string.lyrics_background_enabled_description),
                            isChecked = showBackgroundLyrics,
                            onCheckedChange = { showBackgroundLyrics = it }
                        )
                    }

            if (search.inputValue.isBlank() || stringResource(R.string.player_enable_lyrics_popup_message).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.player_enable_lyrics_popup_message).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.txt_lyrics.player_enable_lyrics_popup_message"),
                        icon = R.drawable.alert,
                        title = stringResource(R.string.player_enable_lyrics_popup_message),
                        text = stringResource(R.string.lyrics_popup_messages_description),
                        isChecked = playerEnableLyricsPopupMessage,
                        onCheckedChange = { playerEnableLyricsPopupMessage = it }
                    )
                }
                    }
                )
            }
}
