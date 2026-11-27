package app.n_zik.android.components.menu.lyrics

import app.n_zik.android.components.menu.MenuSectionTitle
import app.n_zik.android.components.menu.AnimatedMenuContent
import app.n_zik.android.components.menu.animatedMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.n_zik.android.components.dialog.common.AboveMenuDialogPresence
import app.n_zik.android.components.dialog.common.DialogColorPicker
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi

import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.settings.common.CoverDimmingSlider
import app.n_zik.android.components.player.lyrics.karaokeClipRectsDebugKey
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.enums.Languages
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.lyrics.LyricsAlignment
import app.n_zik.android.enums.lyrics.LyricsBackground
import app.n_zik.android.enums.lyrics.LyricsColor
import app.n_zik.android.enums.lyrics.LyricsFontSize
import app.n_zik.android.enums.lyrics.LyricsHighlight
import app.n_zik.android.enums.lyrics.LyricsOutline
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Menu
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.clickOnLyricsTextKey
import app.n_zik.android.utils.contentTranslationChoices
import app.n_zik.android.utils.karaokeRespectAgentPositionKey
import app.n_zik.android.utils.landscapeControlsKey
import app.n_zik.android.utils.lyricsAlignmentKey
import app.n_zik.android.utils.lyricsBackgroundKey
import app.n_zik.android.utils.defaultLyricsCoverDimmingOpacity
import app.n_zik.android.utils.lyricsCoverDimmingEnabledKey
import app.n_zik.android.utils.lyricsCoverDimmingOpacityKey
import app.n_zik.android.utils.lyricsColorKey
import app.n_zik.android.utils.lyricsCustomColorKey
import app.n_zik.android.utils.lyricsFontSizeKey
import app.n_zik.android.utils.lyricsHighlightKey
import app.n_zik.android.utils.lyricsIntervalIndicatorKey
import app.n_zik.android.utils.lyricsOutlineKey
import app.n_zik.android.utils.lyricsSizeAnimateKey
import app.n_zik.android.utils.lyricsTypeKey
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.otherLanguageAppLyricsKey
import app.n_zik.android.utils.playerEnableLyricsPopupMessageKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.romanizationEnabledKey
import app.n_zik.android.utils.showBackgroundLyricsKey
import app.n_zik.android.utils.showButtonPlayerLyricsKey
import app.n_zik.android.utils.showLyricsStateKey
import app.n_zik.android.utils.showSecondLineKey
import app.n_zik.android.utils.showlyricsthumbnailKey
import app.n_zik.android.utils.thumbnailTapEnabledKey

@UnstableApi
class LyricsSettingsMenu private constructor(
    private val isLandscape: Boolean,
    private val isOnCover: () -> Boolean,
    private val translateEnabled: MutableState<Boolean>,
    private val canRefetchLyrics: () -> Boolean,
    private val actualLyricsType: () -> LyricsType?,
    private val onShowLyricsSizeDialog: () -> Unit,
    private val onEditLyrics: () -> Unit,
    private val canCopyLyrics: () -> Boolean,
    private val onCopyLyrics: () -> Unit,
    private val onSearchLyricsOnline: () -> Unit,
    private val onFetchLyricsAgain: () -> Unit,
    private val onPickFromLrcLib: () -> Unit,
    private val onShowProviders: () -> Unit,
    private val onShowOffsetDialog: () -> Unit,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>
) : Menu {

    companion object {
        @Composable
        operator fun invoke(
            isLandscape: Boolean,
            isOnCover: () -> Boolean,
            translateEnabled: MutableState<Boolean>,
            canRefetchLyrics: () -> Boolean,
            actualLyricsType: () -> LyricsType?,
            onShowLyricsSizeDialog: () -> Unit,
            onEditLyrics: () -> Unit,
            canCopyLyrics: () -> Boolean,
            onCopyLyrics: () -> Unit,
            onSearchLyricsOnline: () -> Unit,
            onFetchLyricsAgain: () -> Unit,
            onPickFromLrcLib: () -> Unit,
            onShowProviders: () -> Unit,
            onShowOffsetDialog: () -> Unit
        ): LyricsSettingsMenu =
            LyricsSettingsMenu(
                isLandscape = isLandscape,
                isOnCover = isOnCover,
                translateEnabled = translateEnabled,
                canRefetchLyrics = canRefetchLyrics,
                actualLyricsType = actualLyricsType,
                onShowLyricsSizeDialog = onShowLyricsSizeDialog,
                onEditLyrics = onEditLyrics,
                canCopyLyrics = canCopyLyrics,
                onCopyLyrics = onCopyLyrics,
                onSearchLyricsOnline = onSearchLyricsOnline,
                onFetchLyricsAgain = onFetchLyricsAgain,
                onPickFromLrcLib = onPickFromLrcLib,
                onShowProviders = onShowProviders,
                onShowOffsetDialog = onShowOffsetDialog,
                menuState = LocalMenuState.current,
                styleState = rememberPreference(menuStyleKey, MenuStyle.List)
            )
    }

    override var menuStyle: MenuStyle by styleState

    @Composable
    override fun ListMenu() { /* Handled in MenuComponent */ }

    @Composable
    override fun GridMenu() { /* Handled in MenuComponent */ }

    @Composable
    override fun MenuComponent() {
        // Network-only actions hidden in full offline mode
        val offlineMode by OfflineMode.rememberEnabled()
        // Preferences
        var landscapeControls by rememberPreference(landscapeControlsKey, true)
        var lyricsAlignment by rememberPreference(lyricsAlignmentKey, LyricsAlignment.Center)
        // Shared default: the lyrics screen renders with the same fallback,
        // so the menu and the rendered size always agree on fresh installs
        var fontSize by rememberPreference(lyricsFontSizeKey, LyricsFontSize.DEFAULT)
        var showClipRectsDebug by rememberPreference(karaokeClipRectsDebugKey, false)
        var lyricsColor by rememberPreference(lyricsColorKey, LyricsColor.White)
        var lyricsCustomColor by rememberPreference(lyricsCustomColorKey, android.graphics.Color.WHITE)
        var showColorPicker by remember { mutableStateOf(false) }
        AboveMenuDialogPresence(menuState = menuState, visible = showColorPicker) {
            DialogColorPicker(onDismiss = { showColorPicker = false }, initialColor = Color(lyricsCustomColor)) { color ->
                lyricsCustomColor = color.toArgb()
                showColorPicker = false
            }
        }
        var lyricsOutline by rememberPreference(lyricsOutlineKey, LyricsOutline.None)
        var romanizationEnabled by rememberPreference(romanizationEnabledKey, true)
        var showIntervalIndicator by rememberPreference(lyricsIntervalIndicatorKey, true)
        var showSecondLine by rememberPreference(showSecondLineKey, false)
        var lyricsSizeAnimate by rememberPreference(lyricsSizeAnimateKey, false)
        var lyricsHighlight by rememberPreference(lyricsHighlightKey, LyricsHighlight.None)
        var lyricsBackground by rememberPreference(lyricsBackgroundKey, LyricsBackground.Black)
        var lyricsType by rememberPreference(lyricsTypeKey, LyricsType.Auto)
        val actualType = actualLyricsType()
        val renderedType = actualType ?: lyricsType
        var preferLyricsOnCover by rememberPreference(showlyricsthumbnailKey, true)
        val lyricsOnCover = isOnCover()
        var coverDimmingEnabled by rememberPreference(lyricsCoverDimmingEnabledKey, true)
        var coverDimmingOpacity by rememberPreference(lyricsCoverDimmingOpacityKey, defaultLyricsCoverDimmingOpacity)
        var showButtonPlayerLyrics by rememberPreference(showButtonPlayerLyricsKey, true)
        var thumbnailTapEnabled by rememberPreference(thumbnailTapEnabledKey, true)
        var clickLyricsText by rememberPreference(clickOnLyricsTextKey, true)
        var showLyricsStateKeyPref by rememberPreference(showLyricsStateKey, false)
        var showBackgroundLyrics by rememberPreference(showBackgroundLyricsKey, false)
        var playerEnableLyricsPopupMessage by rememberPreference(playerEnableLyricsPopupMessageKey, true)
        var karaokeRespectAgentPosition by rememberPreference(karaokeRespectAgentPositionKey, true)
        var otherLanguageApp by rememberPreference(otherLanguageAppLyricsKey, Languages.System)

        @Composable
        fun providerSettingsEntry() {
            ActionSettingEntry(
                title = stringResource(R.string.lyrics_provider_settings_title),
                icon = R.drawable.search,
                onClick = onShowProviders,
            )
        }

        // Settings content for list
        @Composable
        fun dimmingToggle() {
            ToggleSettingEntry(
                title = stringResource(R.string.lyrics_cover_dimming_title),
                icon = R.drawable.images_sharp,
                isChecked = coverDimmingEnabled,
                onCheckedChange = { coverDimmingEnabled = it }
            )
        }

        @Composable
        fun dimmingSlider() {
            CoverDimmingSlider(
                title = stringResource(R.string.lyrics_cover_dimming_opacity),
                opacity = coverDimmingOpacity,
                defaultOpacity = defaultLyricsCoverDimmingOpacity,
                onOpacityChange = { coverDimmingOpacity = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        @Composable
        fun settingsContent() {
            // Section: Display Mode
            MenuSectionTitle(stringResource(R.string.lyrics_display_section_title))

            LyricsTypeEntry(lyricsType) { lyricsType = it }
            providerSettingsEntry()
            LyricsCurrentTypeEntry(actualType, label = "LyricsStateAnimation")

            // Show Lyrics Thumbnail
            ToggleSettingEntry(
                title = stringResource(R.string.settings_lyrics_artwork_visible_label),
                icon = R.drawable.image,
                isChecked = preferLyricsOnCover,
                onCheckedChange = { preferLyricsOnCover = it }
            )

            dimmingToggle()
            AnimatedMenuContent(visible = coverDimmingEnabled) { dimmingSlider() }

            // Show Button Player Lyrics
            ToggleSettingEntry(
                title = stringResource(R.string.settings_action_bar_show_lyrics_button),
                icon = R.drawable.song_lyrics,
                isChecked = showButtonPlayerLyrics,
                onCheckedChange = { showButtonPlayerLyrics = it }
            )

            // Landscape Controls
            AnimatedMenuContent(visible = isLandscape && !lyricsOnCover) {
                ToggleSettingEntry(
                    title = stringResource(R.string.lyrics_action_toggle_landscape_controls),
                    icon = R.drawable.play,
                    isChecked = landscapeControls,
                    onCheckedChange = { landscapeControls = it }
                )
            }

            // Section: Text Style
            MenuSectionTitle(stringResource(R.string.lyrics_text_style_section_title))

            LyricsFontSizeEntry(fontSize, { fontSize = it }, onShowLyricsSizeDialog)

            // Debug: karaoke clip rects overlay
            ToggleSettingEntry(
                title = stringResource(R.string.lyrics_show_clip_rects),
                icon = R.drawable.eye,
                isChecked = showClipRectsDebug,
                onCheckedChange = { showClipRectsDebug = it }
            )

            // Color
            EnumSettingEntry(
                title = stringResource(R.string.lyrics_color),
                icon = R.drawable.droplet,
                selectedValue = lyricsColor,
                values = LyricsColor.entries.toList(),
                valueText = { value ->
                    when (value) {
                        LyricsColor.White -> stringResource(R.string.lyrics_color_white)
                        LyricsColor.Thememode -> stringResource(R.string.common_color_source_theme)
                        LyricsColor.Cover -> stringResource(R.string.common_color_source_artwork)
                        LyricsColor.Custom -> stringResource(R.string.common_color_custom)
                    }
                },
                onValueSelected = { lyricsColor = it }
            )

            // Custom Color
            AnimatedMenuContent(visible = lyricsColor == LyricsColor.Custom) {
                ListMenu.Entry(
                    text = stringResource(R.string.common_color_custom),
                    onClick = { showColorPicker = true },
                    icon = {
                        SettingIcon(R.drawable.droplet)
                    },
                    trailingContent = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color(lyricsCustomColor), shape = uiRoundnessShape())
                        )
                    }
                )
            }

            // Alignment
            AnimatedMenuContent(visible = !(renderedType == LyricsType.Karaoke && karaokeRespectAgentPosition)) {
                EnumSettingEntry(
                    title = stringResource(R.string.lyrics_alignment_label),
                    icon = R.drawable.text,
                    selectedValue = lyricsAlignment,
                    values = LyricsAlignment.entries.toList(),
                    valueText = { value ->
                        when (value) {
                            LyricsAlignment.Left -> stringResource(R.string.lyrics_alignment_left_label)
                            LyricsAlignment.Center -> stringResource(R.string.lyrics_alignment_center_label)
                            LyricsAlignment.Right -> stringResource(R.string.lyrics_alignment_right_label)
                        }
                    },
                    onValueSelected = { lyricsAlignment = it }
                )
            }

            // Outline
            AnimatedMenuContent(visible = !lyricsOnCover && (renderedType == LyricsType.Synced || renderedType == LyricsType.Unsynced)) {
                EnumSettingEntry(
                    title = stringResource(R.string.lyrics_text_outline_label),
                    icon = R.drawable.horizontal_bold_line,
                    selectedValue = lyricsOutline,
                    values = LyricsOutline.entries.filter {
                        it != LyricsOutline.Glow || renderedType != LyricsType.Unsynced
                    },
                    valueText = { value ->
                        when (value) {
                            LyricsOutline.None -> stringResource(R.string.common_option_none)
                            LyricsOutline.Thememode -> stringResource(R.string.common_color_source_theme)
                            LyricsOutline.White -> stringResource(R.string.lyrics_color_white)
                            LyricsOutline.Black -> stringResource(R.string.common_color_black)
                            LyricsOutline.Rainbow -> stringResource(R.string.lyrics_style_fluid_rainbow_label)
                            LyricsOutline.Glow -> stringResource(R.string.lyrics_text_glow_label)
                        }
                    },
                    onValueSelected = { lyricsOutline = it }
                )
            }

            // Highlight
            AnimatedMenuContent(visible = !lyricsOnCover) {
                EnumSettingEntry(
                    title = stringResource(R.string.lyrics_text_highlight_label),
                    icon = R.drawable.horizontal_bold_line_rounded,
                    selectedValue = lyricsHighlight,
                    values = LyricsHighlight.entries.toList(),
                    valueText = { value ->
                        when (value) {
                            LyricsHighlight.None -> stringResource(R.string.common_option_none)
                            LyricsHighlight.White -> stringResource(R.string.lyrics_color_white)
                            LyricsHighlight.Black -> stringResource(R.string.common_color_black)
                        }
                    },
                    onValueSelected = { lyricsHighlight = it }
                )
            }

            // Background
            AnimatedMenuContent(visible = !lyricsOnCover) {
                EnumSettingEntry(
                    title = stringResource(R.string.lyrics_background_label),
                    icon = R.drawable.droplet,
                    selectedValue = lyricsBackground,
                    values = LyricsBackground.entries.toList(),
                    valueText = { value ->
                        when (value) {
                            LyricsBackground.None -> stringResource(R.string.common_option_none)
                            LyricsBackground.Black -> stringResource(R.string.common_color_black)
                            LyricsBackground.White -> stringResource(R.string.lyrics_color_white)
                        }
                    },
                    onValueSelected = { lyricsBackground = it }
                )
            }

            // Section: Behavior
            MenuSectionTitle(stringResource(R.string.lyrics_behavior_section_title))

            // Toggle Lyrics
            ToggleSettingEntry(
                title = stringResource(R.string.settings_player_tap_artwork_toggle_lyrics_label),
                subtitle = stringResource(R.string.settings_player_tap_artwork_toggle_lyrics_description),
                icon = R.drawable.song_lyrics,
                isChecked = thumbnailTapEnabled,
                onCheckedChange = { thumbnailTapEnabled = it }
            )

            // Click Lyrics Text
            ToggleSettingEntry(
                title = stringResource(R.string.settings_lyrics_seek_on_line_press_label),
                icon = R.drawable.arrow_down,
                isChecked = clickLyricsText,
                onCheckedChange = { clickLyricsText = it }
            )

            // Save Lyrics State
            ToggleSettingEntry(
                title = stringResource(R.string.settings_lyrics_remember_state_label),
                subtitle = stringResource(R.string.settings_lyrics_remember_state_description),
                icon = R.drawable.bookmark,
                isChecked = showLyricsStateKeyPref,
                onCheckedChange = { showLyricsStateKeyPref = it }
            )

            // Show Background Lyrics
            AnimatedMenuContent(visible = lyricsOnCover) {
                ToggleSettingEntry(
                    title = stringResource(R.string.settings_lyrics_match_player_background_label),
                    icon = R.drawable.image,
                    isChecked = showBackgroundLyrics,
                    onCheckedChange = { showBackgroundLyrics = it }
                )
            }

            // Popup Message
            ToggleSettingEntry(
                title = stringResource(R.string.player_enable_lyrics_popup_message),
                icon = R.drawable.alert,
                isChecked = playerEnableLyricsPopupMessage,
                onCheckedChange = { playerEnableLyricsPopupMessage = it }
            )

            // Interval Indicator
            AnimatedMenuContent(visible = lyricsType != LyricsType.Unsynced) {
                ToggleSettingEntry(
                    title = stringResource(R.string.lyrics_interval_loader_label),
                    icon = R.drawable.close,
                    isChecked = showIntervalIndicator,
                    onCheckedChange = { showIntervalIndicator = it }
                )
            }

            // Size Animate
            AnimatedMenuContent(visible = !lyricsOnCover && lyricsType != LyricsType.Unsynced) {
                ToggleSettingEntry(
                    title = stringResource(R.string.lyrics_font_size_animation_label),
                    icon = R.drawable.close,
                    isChecked = lyricsSizeAnimate,
                    onCheckedChange = { lyricsSizeAnimate = it }
                )
            }

            // Karaoke Respect Agent Position
            ToggleSettingEntry(
                title = stringResource(R.string.lyrics_karaoke_respect_agent_position_label),
                icon = R.drawable.text,
                isChecked = karaokeRespectAgentPosition,
                onCheckedChange = { karaokeRespectAgentPosition = it }
            )

            if (!offlineMode) {
                // Section: Translation
                MenuSectionTitle(stringResource(R.string.lyrics_translation_section_title))

                // Translate Toggle
                ToggleSettingEntry(
                    title = stringResource(R.string.lyrics_action_translate_to_language_format, otherLanguageApp.text),
                    subtitle = otherLanguageApp.text,
                    icon = R.drawable.translate,
                    isChecked = translateEnabled.value,
                    onCheckedChange = { translateEnabled.value = it }
                )

                // Translate Language
                EnumSettingEntry(
                    title = stringResource(R.string.lyrics_action_choose_translation_language),
                    icon = R.drawable.translate,
                    selectedValue = otherLanguageApp,
                    values = contentTranslationChoices,
                    valueText = { it.text },
                    onValueSelected = { lang ->
                        otherLanguageApp = lang
                        translateEnabled.value = true
                    }
                )

                // Romanization
                ToggleSettingEntry(
                    title = stringResource(R.string.lyrics_action_toggle_romanization),
                    icon = R.drawable.text,
                    isChecked = romanizationEnabled,
                    onCheckedChange = { romanizationEnabled = it }
                )

                // Show Second Line
                ToggleSettingEntry(
                    title = stringResource(R.string.lyrics_translation_second_line_label),
                    icon = R.drawable.close,
                    isChecked = showSecondLine,
                    onCheckedChange = { showSecondLine = it }
                )
            }

            // Section: Actions
            MenuSectionTitle(stringResource(R.string.lyrics_actions_section_title))

            // Edit Lyrics
            ActionSettingEntry(
                title = stringResource(R.string.lyrics_action_edit),
                icon = R.drawable.title_edit,
                onClick = onEditLyrics,
            )

            // Copy Lyrics
            ActionSettingEntry(
                title = stringResource(R.string.lyrics_action_copy),
                icon = R.drawable.copy,
                enabled = canCopyLyrics(),
                onClick = {
                    if (!canCopyLyrics()) return@ActionSettingEntry
                    menuState.hide()
                    onCopyLyrics()
                }
            )

            // Search Lyrics Online
            if (!offlineMode) {
                ActionSettingEntry(
                    title = stringResource(R.string.search_lyrics_online),
                    icon = R.drawable.search,
                    onClick = {
                        onSearchLyricsOnline()
                    }
                )
            }

            // Fetch Lyrics Again (network)
            if (!offlineMode) {
                ActionSettingEntry(
                    title = stringResource(R.string.lyrics_action_refetch),
                    icon = R.drawable.sync,
                    enabled = canRefetchLyrics(),
                    onClick = {
                        if (canRefetchLyrics()) {
                            menuState.hide()
                            onFetchLyricsAgain()
                        }
                    }
                )
            }

            AnimatedMenuContent(visible = renderedType != LyricsType.Unsynced) {
                // Lyrics Offset
                ActionSettingEntry(
                    title = stringResource(R.string.lyrics_offset),
                    icon = R.drawable.time,
                    onClick = onShowOffsetDialog,
                )
            }

            // Pick from LrcLib (network)
            AnimatedMenuContent(visible = !offlineMode && (lyricsType == LyricsType.Synced || lyricsType == LyricsType.Unsynced)) {
                ActionSettingEntry(
                    title = stringResource(R.string.lyrics_provider_picker_label, stringResource(R.string.lyrics_brand_lrclib)),
                    icon = R.drawable.search,
                    onClick = onPickFromLrcLib
                )
            }

            // Spacer
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Settings content for grid (each item in its own block)
        fun LazyGridScope.settingsGridContent() {
            // Section: Display Mode
            animatedMenuItem(R.string.lyrics_display_section_title, fullWidth = true) { MenuSectionTitle(stringResource(R.string.lyrics_display_section_title)) }

            animatedMenuItem(R.string.lyrics_mode_label) { LyricsTypeEntry(lyricsType) { lyricsType = it } }
            animatedMenuItem(R.string.lyrics_provider_settings_title) { providerSettingsEntry() }
            if (actualType != null) {
                animatedMenuItem(R.string.lyrics_current_state_format) {
                    LyricsCurrentTypeEntry(actualType, label = "LyricsStateGridAnimation")
                }
            }

            // Show Lyrics Thumbnail
            animatedMenuItem(R.string.settings_lyrics_artwork_visible_label) {
                ToggleSettingEntry(
                    title = stringResource(R.string.settings_lyrics_artwork_visible_label),
                    icon = R.drawable.image,
                    isChecked = preferLyricsOnCover,
                    onCheckedChange = { preferLyricsOnCover = it }
                )
            }

            animatedMenuItem(R.string.lyrics_cover_dimming_title) { dimmingToggle() }
            if (coverDimmingEnabled) {
                animatedMenuItem(R.string.lyrics_cover_dimming_opacity, fullWidth = true) { dimmingSlider() }
            }

            // Show Button Player Lyrics
            animatedMenuItem(R.string.settings_action_bar_show_lyrics_button) {
                ToggleSettingEntry(
                    title = stringResource(R.string.settings_action_bar_show_lyrics_button),
                    icon = R.drawable.song_lyrics,
                    isChecked = showButtonPlayerLyrics,
                    onCheckedChange = { showButtonPlayerLyrics = it }
                )
            }

            // Landscape Controls
            if (isLandscape && !lyricsOnCover) {
                animatedMenuItem(R.string.lyrics_action_toggle_landscape_controls) {
                    ToggleSettingEntry(
                        title = stringResource(R.string.lyrics_action_toggle_landscape_controls),
                        icon = R.drawable.play,
                        isChecked = landscapeControls,
                        onCheckedChange = { landscapeControls = it }
                    )
                }
            }

            // Section: Text Style
            animatedMenuItem(R.string.lyrics_text_style_section_title, fullWidth = true) { MenuSectionTitle(stringResource(R.string.lyrics_text_style_section_title)) }

            animatedMenuItem(R.string.lyrics_size) { LyricsFontSizeEntry(fontSize, { fontSize = it }, onShowLyricsSizeDialog) }

            // Debug: karaoke clip rects overlay
            animatedMenuItem(R.string.lyrics_show_clip_rects) {
                ToggleSettingEntry(
                    title = stringResource(R.string.lyrics_show_clip_rects),
                    icon = R.drawable.eye,
                    isChecked = showClipRectsDebug,
                    onCheckedChange = { showClipRectsDebug = it }
                )
            }

            // Color
            animatedMenuItem(R.string.lyrics_color) {
                EnumSettingEntry(
                    title = stringResource(R.string.lyrics_color),
                    icon = R.drawable.droplet,
                    selectedValue = lyricsColor,
                    values = LyricsColor.entries.toList(),
                    valueText = { value ->
                        when (value) {
                            LyricsColor.White -> stringResource(R.string.lyrics_color_white)
                            LyricsColor.Thememode -> stringResource(R.string.common_color_source_theme)
                            LyricsColor.Cover -> stringResource(R.string.common_color_source_artwork)
                            LyricsColor.Custom -> stringResource(R.string.common_color_custom)
                        }
                    },
                    onValueSelected = { lyricsColor = it }
                )
            }

            // Custom Color
            if (lyricsColor == LyricsColor.Custom) {
                animatedMenuItem(R.string.common_color_custom) {
                    GridMenu.Entry(
                        text = stringResource(R.string.common_color_custom),
                        onClick = { showColorPicker = true },
                        icon = { SettingIcon(R.drawable.droplet) },
                        iconHasOwnBackground = true,
                        trailingContent = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(lyricsCustomColor), shape = uiRoundnessShape())
                            )
                        }
                    )
                }
            }

            // Alignment
            if (!(renderedType == LyricsType.Karaoke && karaokeRespectAgentPosition)) {
                animatedMenuItem(R.string.lyrics_alignment_label) {
                    EnumSettingEntry(
                        title = stringResource(R.string.lyrics_alignment_label),
                        icon = R.drawable.text,
                        selectedValue = lyricsAlignment,
                        values = LyricsAlignment.entries.toList(),
                        valueText = { value ->
                            when (value) {
                                LyricsAlignment.Left -> stringResource(R.string.lyrics_alignment_left_label)
                                LyricsAlignment.Center -> stringResource(R.string.lyrics_alignment_center_label)
                                LyricsAlignment.Right -> stringResource(R.string.lyrics_alignment_right_label)
                            }
                        },
                        onValueSelected = { lyricsAlignment = it }
                    )
                }
            }

            // Outline
            if (!lyricsOnCover && (renderedType == LyricsType.Synced || renderedType == LyricsType.Unsynced)) {
                animatedMenuItem(R.string.lyrics_text_outline_label) {
                    EnumSettingEntry(
                        title = stringResource(R.string.lyrics_text_outline_label),
                        icon = R.drawable.horizontal_bold_line,
                        selectedValue = lyricsOutline,
                        values = LyricsOutline.entries.filter {
                            it != LyricsOutline.Glow || renderedType != LyricsType.Unsynced
                        },
                        valueText = { value ->
                            when (value) {
                                LyricsOutline.None -> stringResource(R.string.common_option_none)
                                LyricsOutline.Thememode -> stringResource(R.string.common_color_source_theme)
                                LyricsOutline.White -> stringResource(R.string.lyrics_color_white)
                                LyricsOutline.Black -> stringResource(R.string.common_color_black)
                                LyricsOutline.Rainbow -> stringResource(R.string.lyrics_style_fluid_rainbow_label)
                                LyricsOutline.Glow -> stringResource(R.string.lyrics_text_glow_label)
                            }
                        },
                        onValueSelected = { lyricsOutline = it }
                    )
                }
            }

            // Highlight
            if (!lyricsOnCover) {
                animatedMenuItem(R.string.lyrics_text_highlight_label) {
                    EnumSettingEntry(
                        title = stringResource(R.string.lyrics_text_highlight_label),
                        icon = R.drawable.horizontal_bold_line_rounded,
                        selectedValue = lyricsHighlight,
                        values = LyricsHighlight.entries.toList(),
                        valueText = { value ->
                            when (value) {
                                LyricsHighlight.None -> stringResource(R.string.common_option_none)
                                LyricsHighlight.White -> stringResource(R.string.lyrics_color_white)
                                LyricsHighlight.Black -> stringResource(R.string.common_color_black)
                            }
                        },
                        onValueSelected = { lyricsHighlight = it }
                    )
                }
            }

            // Background
            if (!lyricsOnCover) {
                animatedMenuItem(R.string.lyrics_background_label) {
                    EnumSettingEntry(
                        title = stringResource(R.string.lyrics_background_label),
                        icon = R.drawable.droplet,
                        selectedValue = lyricsBackground,
                        values = LyricsBackground.entries.toList(),
                        valueText = { value ->
                            when (value) {
                                LyricsBackground.None -> stringResource(R.string.common_option_none)
                                LyricsBackground.Black -> stringResource(R.string.common_color_black)
                                LyricsBackground.White -> stringResource(R.string.lyrics_color_white)
                            }
                        },
                        onValueSelected = { lyricsBackground = it }
                    )
                }
            }

            // Section: Behavior
            animatedMenuItem(R.string.lyrics_behavior_section_title, fullWidth = true) { MenuSectionTitle(stringResource(R.string.lyrics_behavior_section_title)) }

            // Toggle Lyrics
            animatedMenuItem(R.string.settings_player_tap_artwork_toggle_lyrics_label) {
                ToggleSettingEntry(
                    title = stringResource(R.string.settings_player_tap_artwork_toggle_lyrics_label),
                    subtitle = stringResource(R.string.settings_player_tap_artwork_toggle_lyrics_description),
                    icon = R.drawable.song_lyrics,
                    isChecked = thumbnailTapEnabled,
                    onCheckedChange = { thumbnailTapEnabled = it }
                )
            }

            // Click Lyrics Text
            animatedMenuItem(R.string.settings_lyrics_seek_on_line_press_label) {
                ToggleSettingEntry(
                    title = stringResource(R.string.settings_lyrics_seek_on_line_press_label),
                    icon = R.drawable.arrow_down,
                    isChecked = clickLyricsText,
                    onCheckedChange = { clickLyricsText = it }
                )
            }

            // Save Lyrics State
            animatedMenuItem(R.string.settings_lyrics_remember_state_label) {
                ToggleSettingEntry(
                    title = stringResource(R.string.settings_lyrics_remember_state_label),
                    subtitle = stringResource(R.string.settings_lyrics_remember_state_description),
                    icon = R.drawable.bookmark,
                    isChecked = showLyricsStateKeyPref,
                    onCheckedChange = { showLyricsStateKeyPref = it }
                )
            }

            // Show Background Lyrics
            if (lyricsOnCover) {
                animatedMenuItem(R.string.settings_lyrics_match_player_background_label) {
                    ToggleSettingEntry(
                        title = stringResource(R.string.settings_lyrics_match_player_background_label),
                        icon = R.drawable.image,
                        isChecked = showBackgroundLyrics,
                        onCheckedChange = { showBackgroundLyrics = it }
                    )
                }
            }

            // Popup Message
            animatedMenuItem(R.string.player_enable_lyrics_popup_message) {
                ToggleSettingEntry(
                    title = stringResource(R.string.player_enable_lyrics_popup_message),
                    icon = R.drawable.alert,
                    isChecked = playerEnableLyricsPopupMessage,
                    onCheckedChange = { playerEnableLyricsPopupMessage = it }
                )
            }

            // Interval Indicator
            if (lyricsType != LyricsType.Unsynced) {
                animatedMenuItem(R.string.lyrics_interval_loader_label) {
                    ToggleSettingEntry(
                        title = stringResource(R.string.lyrics_interval_loader_label),
                        icon = R.drawable.close,
                        isChecked = showIntervalIndicator,
                        onCheckedChange = { showIntervalIndicator = it }
                    )
                }
            }

            // Size Animate
            if (!lyricsOnCover && lyricsType != LyricsType.Unsynced) {
                animatedMenuItem(R.string.lyrics_font_size_animation_label) {
                    ToggleSettingEntry(
                        title = stringResource(R.string.lyrics_font_size_animation_label),
                        icon = R.drawable.close,
                        isChecked = lyricsSizeAnimate,
                        onCheckedChange = { lyricsSizeAnimate = it }
                    )
                }
            }

            // Karaoke Respect Agent Position
            animatedMenuItem(R.string.lyrics_karaoke_respect_agent_position_label) {
                ToggleSettingEntry(
                    title = stringResource(R.string.lyrics_karaoke_respect_agent_position_label),
                    icon = R.drawable.text,
                    isChecked = karaokeRespectAgentPosition,
                    onCheckedChange = { karaokeRespectAgentPosition = it }
                )
            }

            if (!offlineMode) {
                // Section: Translation
                animatedMenuItem(R.string.lyrics_translation_section_title, fullWidth = true) { MenuSectionTitle(stringResource(R.string.lyrics_translation_section_title)) }

                // Translate Toggle
                animatedMenuItem(R.string.lyrics_action_translate_to_language_format) {
                    ToggleSettingEntry(
                        title = stringResource(R.string.lyrics_action_translate_to_language_format, otherLanguageApp.text),
                        subtitle = otherLanguageApp.text,
                        icon = R.drawable.translate,
                        isChecked = translateEnabled.value,
                        onCheckedChange = { translateEnabled.value = it }
                    )
                }

                // Translate Language
                animatedMenuItem(R.string.lyrics_action_choose_translation_language) {
                    EnumSettingEntry(
                        title = stringResource(R.string.lyrics_action_choose_translation_language),
                        icon = R.drawable.translate,
                        selectedValue = otherLanguageApp,
                        values = contentTranslationChoices,
                        valueText = { it.text },
                        onValueSelected = { lang ->
                            otherLanguageApp = lang
                            translateEnabled.value = true
                        }
                    )
                }

                // Romanization
                animatedMenuItem(R.string.lyrics_action_toggle_romanization) {
                    ToggleSettingEntry(
                        title = stringResource(R.string.lyrics_action_toggle_romanization),
                        icon = R.drawable.text,
                        isChecked = romanizationEnabled,
                        onCheckedChange = { romanizationEnabled = it }
                    )
                }

                // Show Second Line
                animatedMenuItem(R.string.lyrics_translation_second_line_label) {
                    ToggleSettingEntry(
                        title = stringResource(R.string.lyrics_translation_second_line_label),
                        icon = R.drawable.close,
                        isChecked = showSecondLine,
                        onCheckedChange = { showSecondLine = it }
                    )
                }
            }

            // Section: Actions
            animatedMenuItem(R.string.lyrics_actions_section_title, fullWidth = true) { MenuSectionTitle(stringResource(R.string.lyrics_actions_section_title)) }

            // Edit Lyrics
            animatedMenuItem(R.string.lyrics_action_edit) {
                ActionSettingEntry(
                    title = stringResource(R.string.lyrics_action_edit),
                    icon = R.drawable.title_edit,
                    onClick = onEditLyrics,
                )
            }

            // Copy Lyrics
            animatedMenuItem(R.string.lyrics_action_copy) {
                ActionSettingEntry(
                    title = stringResource(R.string.lyrics_action_copy),
                    icon = R.drawable.copy,
                    enabled = canCopyLyrics(),
                    onClick = {
                        if (!canCopyLyrics()) return@ActionSettingEntry
                        menuState.hide()
                        onCopyLyrics()
                    }
                )
            }

            // Search Lyrics Online
            if (!offlineMode) {
                animatedMenuItem(R.string.search_lyrics_online) {
                    ActionSettingEntry(
                        title = stringResource(R.string.search_lyrics_online),
                        icon = R.drawable.search,
                        onClick = {
                            onSearchLyricsOnline()
                        }
                    )
                }
            }

            // Fetch Lyrics Again (network)
            if (!offlineMode) {
                animatedMenuItem(R.string.lyrics_action_refetch) {
                    ActionSettingEntry(
                        title = stringResource(R.string.lyrics_action_refetch),
                        icon = R.drawable.sync,
                        enabled = canRefetchLyrics(),
                        onClick = {
                            if (canRefetchLyrics()) {
                                menuState.hide()
                                onFetchLyricsAgain()
                            }
                        }
                    )
                }
            }

            if (renderedType != LyricsType.Unsynced) {
                // Lyrics Offset
                animatedMenuItem(R.string.lyrics_offset) {
                    ActionSettingEntry(
                        title = stringResource(R.string.lyrics_offset),
                        icon = R.drawable.time,
                        onClick = onShowOffsetDialog,
                    )
                }
            }

            // Pick from LrcLib (network)
            if (lyricsType == LyricsType.Synced || lyricsType == LyricsType.Unsynced) {
                if (!offlineMode) {
                    animatedMenuItem(R.string.lyrics_provider_picker_label) {
                        ActionSettingEntry(
                            title = stringResource(R.string.lyrics_provider_picker_label, stringResource(R.string.lyrics_brand_lrclib)),
                            icon = R.drawable.search,
                            onClick = onPickFromLrcLib
                        )
                    }
                }
            }

            // Spacer
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // Show menu based on style preference
        if (menuStyle == MenuStyle.List) {
            ListMenu.Menu(title = stringResource(R.string.lyrics_title)) {
                settingsContent()
            }
        } else {
            GridMenu.Menu(title = stringResource(R.string.lyrics_title)) {
                settingsGridContent()
            }
        }
    }
}
