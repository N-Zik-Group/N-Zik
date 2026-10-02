package app.n_zik.android.components.menu.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.enums.Languages
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Menu
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.utils.clickOnLyricsTextKey
import app.it.fast4x.rimusic.utils.karaokeRespectAgentPositionKey
import app.it.fast4x.rimusic.utils.landscapeControlsKey
import app.it.fast4x.rimusic.utils.lyricsAlignmentKey
import app.it.fast4x.rimusic.utils.lyricsBackgroundKey
import app.it.fast4x.rimusic.utils.lyricsColorKey
import app.it.fast4x.rimusic.utils.lyricsCustomColorKey
import app.it.fast4x.rimusic.utils.lyricsFontSizeKey
import app.it.fast4x.rimusic.utils.lyricsHighlightKey
import app.it.fast4x.rimusic.utils.lyricsIntervalIndicatorKey
import app.it.fast4x.rimusic.utils.lyricsOutlineKey
import app.it.fast4x.rimusic.utils.lyricsSizeAnimateKey
import app.it.fast4x.rimusic.utils.lyricsTypeKey
import app.it.fast4x.rimusic.utils.otherLanguageAppLyricsKey
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.playerEnableLyricsPopupMessageKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.romanizationEnabledKey
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.showBackgroundLyricsKey
import app.it.fast4x.rimusic.utils.showButtonPlayerLyricsKey
import app.it.fast4x.rimusic.utils.showLyricsStateKey
import app.it.fast4x.rimusic.utils.showSecondLineKey
import app.it.fast4x.rimusic.utils.showlyricsthumbnailKey
import app.it.fast4x.rimusic.utils.thumbnailTapEnabledKey
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.player.lyrics.karaokeClipRectsDebugKey
import app.n_zik.android.components.ui.toggles.Switch
import app.n_zik.android.enums.lyrics.LyricsAlignment
import app.n_zik.android.enums.lyrics.LyricsBackground
import app.n_zik.android.enums.lyrics.LyricsColor
import app.n_zik.android.enums.lyrics.LyricsFontSize
import app.n_zik.android.enums.lyrics.LyricsHighlight
import app.n_zik.android.enums.lyrics.LyricsOutline
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.ui.components.themed.ValueSelectorDialog

@UnstableApi
class LyricsSettingsMenu private constructor(
    private val isLandscape: Boolean,
    private val translateEnabled: MutableState<Boolean>,
    private val isLyricsNotNull: () -> Boolean,
    private val actualLyricsType: () -> LyricsType?,
    private val onShowLyricsSizeDialog: () -> Unit,
    private val onEditLyrics: () -> Unit,
    private val onCopyLyrics: () -> Unit,
    private val onSearchLyricsOnline: () -> Unit,
    private val onFetchLyricsAgain: () -> Unit,
    private val onPickFromLrcLib: () -> Unit,
    private val onShowOffsetDialog: () -> Unit,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>
) : Menu {

    companion object {
        @Composable
        operator fun invoke(
            isLandscape: Boolean,
            translateEnabled: MutableState<Boolean>,
            isLyricsNotNull: () -> Boolean,
            actualLyricsType: () -> LyricsType?,
            onShowLyricsSizeDialog: () -> Unit,
            onEditLyrics: () -> Unit,
            onCopyLyrics: () -> Unit,
            onSearchLyricsOnline: () -> Unit,
            onFetchLyricsAgain: () -> Unit,
            onPickFromLrcLib: () -> Unit,
            onShowOffsetDialog: () -> Unit
        ): LyricsSettingsMenu =
            LyricsSettingsMenu(
                isLandscape = isLandscape,
                translateEnabled = translateEnabled,
                isLyricsNotNull = isLyricsNotNull,
                actualLyricsType = actualLyricsType,
                onShowLyricsSizeDialog = onShowLyricsSizeDialog,
                onEditLyrics = onEditLyrics,
                onCopyLyrics = onCopyLyrics,
                onSearchLyricsOnline = onSearchLyricsOnline,
                onFetchLyricsAgain = onFetchLyricsAgain,
                onPickFromLrcLib = onPickFromLrcLib,
                onShowOffsetDialog = onShowOffsetDialog,
                menuState = LocalMenuState.current,
                styleState = rememberPreference(menuStyleKey, MenuStyle.List)
            )
    }

    private lateinit var buttons: List<Button>
    override var menuStyle: MenuStyle by styleState

    @Composable
    override fun ListMenu() { /* Handled in MenuComponent */ }

    @Composable
    override fun GridMenu() { /* Handled in MenuComponent */ }

    @Composable
    override fun MenuComponent() {
        // Preferences
        var landscapeControls by rememberPreference(landscapeControlsKey, true)
        var lyricsAlignment by rememberPreference(lyricsAlignmentKey, LyricsAlignment.Center)
        // Shared default: the lyrics screen renders with the same fallback,
        // so the menu and the rendered size always agree on fresh installs
        var fontSize by rememberPreference(lyricsFontSizeKey, LyricsFontSize.DEFAULT)
        var showClipRectsDebug by rememberPreference(karaokeClipRectsDebugKey, false)
        var lyricsColor by rememberPreference(lyricsColorKey, LyricsColor.White)
        var lyricsCustomColor by rememberPreference(lyricsCustomColorKey, android.graphics.Color.WHITE)
        var lyricsOutline by rememberPreference(lyricsOutlineKey, LyricsOutline.None)
        var romanizationEnabled by rememberPreference(romanizationEnabledKey, true)
        var showIntervalIndicator by rememberPreference(lyricsIntervalIndicatorKey, true)
        var showSecondLine by rememberPreference(showSecondLineKey, false)
        var lyricsSizeAnimate by rememberPreference(lyricsSizeAnimateKey, false)
        var lyricsHighlight by rememberPreference(lyricsHighlightKey, LyricsHighlight.None)
        var lyricsBackground by rememberPreference(lyricsBackgroundKey, LyricsBackground.Black)
        var lyricsType by rememberPreference(lyricsTypeKey, LyricsType.Auto)
        var showlyricsthumbnail by rememberPreference(showlyricsthumbnailKey, true)
        var showButtonPlayerLyrics by rememberPreference(showButtonPlayerLyricsKey, true)
        var thumbnailTapEnabled by rememberPreference(thumbnailTapEnabledKey, true)
        var clickLyricsText by rememberPreference(clickOnLyricsTextKey, true)
        var showLyricsStateKeyPref by rememberPreference(showLyricsStateKey, false)
        var showBackgroundLyrics by rememberPreference(showBackgroundLyricsKey, false)
        var playerEnableLyricsPopupMessage by rememberPreference(playerEnableLyricsPopupMessageKey, true)
        var karaokeRespectAgentPosition by rememberPreference(karaokeRespectAgentPositionKey, true)
        var otherLanguageApp by rememberPreference(otherLanguageAppLyricsKey, Languages.System)

        // Settings content for list
        @Composable
        fun settingsContent() {
            // Section: Display Mode
            SectionTitle(stringResource(R.string.section_lyrics_display))

            // Lyrics Type
            EnumSettingEntry(
                title = stringResource(R.string.show),
                icon = R.drawable.time,
                selectedValue = lyricsType,
                values = LyricsType.entries.toList(),
                valueText = { value ->
                    when (value) {
                        LyricsType.Karaoke -> stringResource(R.string.karaoke_lyrics)
                        LyricsType.Synced -> stringResource(R.string.synchronized_lyrics)
                        LyricsType.Unsynced -> stringResource(R.string.unsynchronized_lyrics)
                        LyricsType.Auto -> stringResource(R.string.auto_lyrics)
                    }
                },
                onValueSelected = { lyricsType = it }
            )

            val actualStateString = actualLyricsType()?.let {
                when (it) {
                    LyricsType.Karaoke -> stringResource(R.string.karaoke_lyrics)
                    LyricsType.Synced -> stringResource(R.string.synchronized_lyrics)
                    LyricsType.Unsynced -> stringResource(R.string.unsynchronized_lyrics)
                    LyricsType.Auto -> stringResource(R.string.auto_lyrics)
                }
            }

            AnimatedContent(
                targetState = actualStateString,
                label = "LyricsStateAnimation"
            ) { stateString ->
                if (stateString != null) {
                    ActionSettingEntry(
                        title = stringResource(R.string.current_lyrics_state, stateString),
                        icon = R.drawable.information,
                        enabled = false,
                        onClick = {}
                    )
                }
            }

            // Show Lyrics Thumbnail
            ToggleSettingEntry(
                title = stringResource(R.string.show_lyrics_thumbnail),
                icon = R.drawable.image,
                isChecked = showlyricsthumbnail,
                onCheckedChange = { showlyricsthumbnail = it }
            )

            // Show Button Player Lyrics
            ToggleSettingEntry(
                title = stringResource(R.string.action_bar_show_lyrics_button),
                icon = R.drawable.song_lyrics,
                isChecked = showButtonPlayerLyrics,
                onCheckedChange = { showButtonPlayerLyrics = it }
            )

            // Landscape Controls
            if (isLandscape && !showlyricsthumbnail) {
                ToggleSettingEntry(
                    title = stringResource(R.string.toggle_controls_landscape),
                    icon = R.drawable.play,
                    isChecked = landscapeControls,
                    onCheckedChange = { landscapeControls = it }
                )
            }

            // Section: Text Style
            SectionTitle(stringResource(R.string.section_lyrics_text_style))

            // Font Size
            EnumSettingEntry(
                title = stringResource(R.string.lyrics_size),
                icon = R.drawable.text,
                selectedValue = fontSize,
                values = LyricsFontSize.entries.toList(),
                valueText = { value ->
                    when (value) {
                        LyricsFontSize.Light -> stringResource(R.string.light)
                        LyricsFontSize.Medium -> stringResource(R.string.medium)
                        LyricsFontSize.Heavy -> stringResource(R.string.heavy)
                        LyricsFontSize.Large -> stringResource(R.string.large)
                        LyricsFontSize.Custom -> stringResource(R.string.custom)
                    }
                },
                onValueSelected = { fontSize = it },
                trailingContent = {
                    if (fontSize == LyricsFontSize.Custom) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(uiRoundnessShape())
                                .background(colorPalette().accent.copy(alpha = 0.1f))
                                .clickable { onShowLyricsSizeDialog() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.text),
                                tint = colorPalette().accent,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )

            // Debug: karaoke clip rects overlay
            ToggleSettingEntry(
                title = stringResource(R.string.lyrics_show_clip_rects),
                icon = R.drawable.eye,
                isChecked = showClipRectsDebug,
                onCheckedChange = { showClipRectsDebug = it }
            )

            // Color
            EnumSettingEntry(
                title = stringResource(R.string.lyricscolor),
                icon = R.drawable.droplet,
                selectedValue = lyricsColor,
                values = LyricsColor.entries.toList(),
                valueText = { value ->
                    when (value) {
                        LyricsColor.White -> stringResource(R.string.white)
                        LyricsColor.Thememode -> stringResource(R.string.theme)
                        LyricsColor.Cover -> stringResource(R.string.bg_colors_background_from_cover)
                        LyricsColor.Custom -> stringResource(R.string.color_custom)
                    }
                },
                onValueSelected = { lyricsColor = it }
            )

            // Custom Color
            if (lyricsColor == LyricsColor.Custom) {
                ListMenu.Entry(
                    text = stringResource(R.string.color_custom),
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
            if (!(lyricsType == LyricsType.Karaoke && karaokeRespectAgentPosition)) {
                EnumSettingEntry(
                    title = stringResource(R.string.lyricsalignment),
                    icon = R.drawable.text,
                    selectedValue = lyricsAlignment,
                    values = LyricsAlignment.entries.toList(),
                    valueText = { value ->
                        when (value) {
                            LyricsAlignment.Left -> stringResource(R.string.direction_left)
                            LyricsAlignment.Center -> stringResource(R.string.center)
                            LyricsAlignment.Right -> stringResource(R.string.direction_right)
                        }
                    },
                    onValueSelected = { lyricsAlignment = it }
                )
            }

            // Outline
            if (!showlyricsthumbnail && lyricsType == LyricsType.Synced) {
                EnumSettingEntry(
                    title = stringResource(R.string.lyricsoutline),
                    icon = R.drawable.horizontal_bold_line,
                    selectedValue = lyricsOutline,
                    values = LyricsOutline.entries.filter {
                        it != LyricsOutline.Glow || lyricsType != LyricsType.Unsynced
                    },
                    valueText = { value ->
                        when (value) {
                            LyricsOutline.None -> stringResource(R.string.none)
                            LyricsOutline.Thememode -> stringResource(R.string.theme)
                            LyricsOutline.White -> stringResource(R.string.white)
                            LyricsOutline.Black -> stringResource(R.string.black)
                            LyricsOutline.Rainbow -> stringResource(R.string.fluidrainbow)
                            LyricsOutline.Glow -> stringResource(R.string.glow)
                        }
                    },
                    onValueSelected = { lyricsOutline = it }
                )
            }

            // Highlight
            if (!showlyricsthumbnail) {
                EnumSettingEntry(
                    title = stringResource(R.string.highlight),
                    icon = R.drawable.horizontal_bold_line_rounded,
                    selectedValue = lyricsHighlight,
                    values = LyricsHighlight.entries.toList(),
                    valueText = { value ->
                        when (value) {
                            LyricsHighlight.None -> stringResource(R.string.none)
                            LyricsHighlight.White -> stringResource(R.string.white)
                            LyricsHighlight.Black -> stringResource(R.string.black)
                        }
                    },
                    onValueSelected = { lyricsHighlight = it }
                )
            }

            // Background
            if (!showlyricsthumbnail) {
                EnumSettingEntry(
                    title = stringResource(R.string.lyricsbackground),
                    icon = R.drawable.droplet,
                    selectedValue = lyricsBackground,
                    values = LyricsBackground.entries.toList(),
                    valueText = { value ->
                        when (value) {
                            LyricsBackground.None -> stringResource(R.string.none)
                            LyricsBackground.Black -> stringResource(R.string.black)
                            LyricsBackground.White -> stringResource(R.string.white)
                        }
                    },
                    onValueSelected = { lyricsBackground = it }
                )
            }

            // Section: Behavior
            SectionTitle(stringResource(R.string.section_lyrics_behavior))

            // Toggle Lyrics
            ToggleSettingEntry(
                title = stringResource(R.string.toggle_lyrics),
                subtitle = stringResource(R.string.by_tapping_on_the_thumbnail),
                icon = R.drawable.song_lyrics,
                isChecked = thumbnailTapEnabled,
                onCheckedChange = { thumbnailTapEnabled = it }
            )

            // Click Lyrics Text
            ToggleSettingEntry(
                title = stringResource(R.string.click_lyrics_text),
                icon = R.drawable.arrow_down,
                isChecked = clickLyricsText,
                onCheckedChange = { clickLyricsText = it }
            )

            // Save Lyrics State
            ToggleSettingEntry(
                title = stringResource(R.string.save_lyrics_state),
                subtitle = stringResource(R.string.save_lyrics_state_description),
                icon = R.drawable.bookmark,
                isChecked = showLyricsStateKeyPref,
                onCheckedChange = { showLyricsStateKeyPref = it }
            )

            // Show Background Lyrics
            if (showlyricsthumbnail) {
                ToggleSettingEntry(
                    title = stringResource(R.string.show_background_in_lyrics),
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
            if (lyricsType != LyricsType.Unsynced) {
                ToggleSettingEntry(
                    title = stringResource(R.string.interval_indicator),
                    icon = R.drawable.close,
                    isChecked = showIntervalIndicator,
                    onCheckedChange = { showIntervalIndicator = it }
                )
            }

            // Size Animate
            if (!showlyricsthumbnail && lyricsType != LyricsType.Unsynced) {
                ToggleSettingEntry(
                    title = stringResource(R.string.lyricsanimate),
                    icon = R.drawable.close,
                    isChecked = lyricsSizeAnimate,
                    onCheckedChange = { lyricsSizeAnimate = it }
                )
            }

            // Karaoke Respect Agent Position
            ToggleSettingEntry(
                title = stringResource(R.string.karaoke_respect_agent_position),
                icon = R.drawable.text,
                isChecked = karaokeRespectAgentPosition,
                onCheckedChange = { karaokeRespectAgentPosition = it }
            )

            // Section: Translation
            SectionTitle(stringResource(R.string.section_lyrics_translation))

            // Translate Toggle
            ToggleSettingEntry(
                title = stringResource(R.string.translate_to, otherLanguageApp.text),
                subtitle = otherLanguageApp.text,
                icon = R.drawable.translate,
                isChecked = translateEnabled.value,
                onCheckedChange = { translateEnabled.value = it }
            )

            // Translate Language
            EnumSettingEntry(
                title = stringResource(R.string.translate_to_other_language),
                icon = R.drawable.translate,
                selectedValue = otherLanguageApp,
                values = Languages.entries.toList(),
                valueText = { it.text },
                onValueSelected = { lang ->
                    otherLanguageApp = lang
                    translateEnabled.value = true
                }
            )

            // Romanization
            ToggleSettingEntry(
                title = stringResource(R.string.toggle_romanization),
                icon = R.drawable.text,
                isChecked = romanizationEnabled,
                onCheckedChange = { romanizationEnabled = it }
            )

            // Show Second Line
            ToggleSettingEntry(
                title = stringResource(R.string.showsecondline),
                icon = R.drawable.close,
                isChecked = showSecondLine,
                onCheckedChange = { showSecondLine = it }
            )

            // Section: Actions
            SectionTitle(stringResource(R.string.section_lyrics_actions))

            // Edit Lyrics
            ActionSettingEntry(
                title = stringResource(R.string.edit_lyrics),
                icon = R.drawable.title_edit,
                onClick = {
                    menuState.hide()
                    onEditLyrics()
                }
            )

            // Copy Lyrics
            ActionSettingEntry(
                title = stringResource(R.string.copy_lyrics),
                icon = R.drawable.copy,
                onClick = {
                    menuState.hide()
                    onCopyLyrics()
                }
            )

            // Search Lyrics Online
            ActionSettingEntry(
                title = stringResource(R.string.search_lyrics_online),
                icon = R.drawable.search,
                onClick = {
                    menuState.hide()
                    onSearchLyricsOnline()
                }
            )

            // Fetch Lyrics Again
            ActionSettingEntry(
                title = stringResource(R.string.fetch_lyrics_again),
                icon = R.drawable.sync,
                enabled = isLyricsNotNull(),
                onClick = {
                    if (isLyricsNotNull()) {
                        menuState.hide()
                        onFetchLyricsAgain()
                    }
                }
            )

            // Lyrics Offset
            ActionSettingEntry(
                title = stringResource(R.string.lyrics_offset),
                icon = R.drawable.time,
                onClick = {
                    menuState.hide()
                    onShowOffsetDialog()
                }
            )

            // Pick from LrcLib
            if (lyricsType == LyricsType.Synced || lyricsType == LyricsType.Unsynced) {
                ActionSettingEntry(
                    title = stringResource(R.string.pick_from) + " LrcLib.net",
                    icon = R.drawable.search,
                    onClick = {
                        menuState.hide()
                        onPickFromLrcLib()
                    }
                )
            }

            // Spacer
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Settings content for grid (each item in its own block)
        fun LazyGridScope.settingsGridContent() {
            // Section: Display Mode
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.section_lyrics_display)) }

            // Lyrics Type
            item {
                EnumSettingEntry(
                    title = stringResource(R.string.show),
                    icon = R.drawable.time,
                    selectedValue = lyricsType,
                    values = LyricsType.entries.toList(),
                    valueText = { value ->
                        when (value) {
                            LyricsType.Karaoke -> stringResource(R.string.karaoke_lyrics)
                            LyricsType.Synced -> stringResource(R.string.synchronized_lyrics)
                            LyricsType.Unsynced -> stringResource(R.string.unsynchronized_lyrics)
                            LyricsType.Auto -> stringResource(R.string.auto_lyrics)
                        }
                    },
                    onValueSelected = { lyricsType = it }
                )
            }

            item {
                val actualStateString = when (actualLyricsType()) {
                    LyricsType.Karaoke -> stringResource(R.string.karaoke_lyrics)
                    LyricsType.Synced -> stringResource(R.string.synchronized_lyrics)
                    LyricsType.Unsynced -> stringResource(R.string.unsynchronized_lyrics)
                    LyricsType.Auto -> stringResource(R.string.auto_lyrics)
                    null -> null
                }

                AnimatedContent(
                    targetState = actualStateString,
                    label = "LyricsStateGridAnimation"
                ) { stateString ->
                    if (stateString != null) {
                        ActionSettingEntry(
                            title = stringResource(R.string.current_lyrics_state, stateString),
                            icon = R.drawable.information,
                            enabled = false,
                            onClick = {}
                        )
                    }
                }
            }

            // Show Lyrics Thumbnail
            item {
                ToggleSettingEntry(
                    title = stringResource(R.string.show_lyrics_thumbnail),
                    icon = R.drawable.image,
                    isChecked = showlyricsthumbnail,
                    onCheckedChange = { showlyricsthumbnail = it }
                )
            }

            // Show Button Player Lyrics
            item {
                ToggleSettingEntry(
                    title = stringResource(R.string.action_bar_show_lyrics_button),
                    icon = R.drawable.song_lyrics,
                    isChecked = showButtonPlayerLyrics,
                    onCheckedChange = { showButtonPlayerLyrics = it }
                )
            }

            // Landscape Controls
            if (isLandscape && !showlyricsthumbnail) {
                item {
                    ToggleSettingEntry(
                        title = stringResource(R.string.toggle_controls_landscape),
                        icon = R.drawable.play,
                        isChecked = landscapeControls,
                        onCheckedChange = { landscapeControls = it }
                    )
                }
            }

            // Section: Text Style
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.section_lyrics_text_style)) }

            // Font Size
            item {
                EnumSettingEntry(
                    title = stringResource(R.string.lyrics_size),
                    icon = R.drawable.text,
                    selectedValue = fontSize,
                    values = LyricsFontSize.entries.toList(),
                    valueText = { value ->
                        when (value) {
                            LyricsFontSize.Light -> stringResource(R.string.light)
                            LyricsFontSize.Medium -> stringResource(R.string.medium)
                            LyricsFontSize.Heavy -> stringResource(R.string.heavy)
                            LyricsFontSize.Large -> stringResource(R.string.large)
                            LyricsFontSize.Custom -> stringResource(R.string.custom)
                        }
                    },
                    onValueSelected = { fontSize = it },
                    trailingContent = {
                        if (fontSize == LyricsFontSize.Custom) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(uiRoundnessShape())
                                    .background(colorPalette().accent.copy(alpha = 0.1f))
                                    .clickable { onShowLyricsSizeDialog() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.text),
                                    tint = colorPalette().accent,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                )
            }

            // Debug: karaoke clip rects overlay
            item {
                ToggleSettingEntry(
                    title = stringResource(R.string.lyrics_show_clip_rects),
                    icon = R.drawable.eye,
                    isChecked = showClipRectsDebug,
                    onCheckedChange = { showClipRectsDebug = it }
                )
            }

            // Color
            item {
                EnumSettingEntry(
                    title = stringResource(R.string.lyricscolor),
                    icon = R.drawable.droplet,
                    selectedValue = lyricsColor,
                    values = LyricsColor.entries.toList(),
                    valueText = { value ->
                        when (value) {
                            LyricsColor.White -> stringResource(R.string.white)
                            LyricsColor.Thememode -> stringResource(R.string.theme)
                            LyricsColor.Cover -> stringResource(R.string.bg_colors_background_from_cover)
                            LyricsColor.Custom -> stringResource(R.string.color_custom)
                        }
                    },
                    onValueSelected = { lyricsColor = it }
                )
            }

            // Custom Color
            if (lyricsColor == LyricsColor.Custom) {
                item {
                    GridMenu.Entry(
                        text = stringResource(R.string.color_custom),
                        icon = { SettingIcon(R.drawable.droplet) },
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
            if (!(lyricsType == LyricsType.Karaoke && karaokeRespectAgentPosition)) {
                item {
                    EnumSettingEntry(
                        title = stringResource(R.string.lyricsalignment),
                        icon = R.drawable.text,
                        selectedValue = lyricsAlignment,
                        values = LyricsAlignment.entries.toList(),
                        valueText = { value ->
                            when (value) {
                                LyricsAlignment.Left -> stringResource(R.string.direction_left)
                                LyricsAlignment.Center -> stringResource(R.string.center)
                                LyricsAlignment.Right -> stringResource(R.string.direction_right)
                            }
                        },
                        onValueSelected = { lyricsAlignment = it }
                    )
                }
            }

            // Outline
            if (!showlyricsthumbnail && lyricsType == LyricsType.Synced) {
                item {
                    EnumSettingEntry(
                        title = stringResource(R.string.lyricsoutline),
                        icon = R.drawable.horizontal_bold_line,
                        selectedValue = lyricsOutline,
                        values = LyricsOutline.entries.filter {
                            it != LyricsOutline.Glow || lyricsType != LyricsType.Unsynced
                        },
                        valueText = { value ->
                            when (value) {
                                LyricsOutline.None -> stringResource(R.string.none)
                                LyricsOutline.Thememode -> stringResource(R.string.theme)
                                LyricsOutline.White -> stringResource(R.string.white)
                                LyricsOutline.Black -> stringResource(R.string.black)
                                LyricsOutline.Rainbow -> stringResource(R.string.fluidrainbow)
                                LyricsOutline.Glow -> stringResource(R.string.glow)
                            }
                        },
                        onValueSelected = { lyricsOutline = it }
                    )
                }
            }

            // Highlight
            if (!showlyricsthumbnail) {
                item {
                    EnumSettingEntry(
                        title = stringResource(R.string.highlight),
                        icon = R.drawable.horizontal_bold_line_rounded,
                        selectedValue = lyricsHighlight,
                        values = LyricsHighlight.entries.toList(),
                        valueText = { value ->
                            when (value) {
                                LyricsHighlight.None -> stringResource(R.string.none)
                                LyricsHighlight.White -> stringResource(R.string.white)
                                LyricsHighlight.Black -> stringResource(R.string.black)
                            }
                        },
                        onValueSelected = { lyricsHighlight = it }
                    )
                }
            }

            // Background
            if (!showlyricsthumbnail) {
                item {
                    EnumSettingEntry(
                        title = stringResource(R.string.lyricsbackground),
                        icon = R.drawable.droplet,
                        selectedValue = lyricsBackground,
                        values = LyricsBackground.entries.toList(),
                        valueText = { value ->
                            when (value) {
                                LyricsBackground.None -> stringResource(R.string.none)
                                LyricsBackground.Black -> stringResource(R.string.black)
                                LyricsBackground.White -> stringResource(R.string.white)
                            }
                        },
                        onValueSelected = { lyricsBackground = it }
                    )
                }
            }

            // Section: Behavior
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.section_lyrics_behavior)) }

            // Toggle Lyrics
            item {
                ToggleSettingEntry(
                    title = stringResource(R.string.toggle_lyrics),
                    subtitle = stringResource(R.string.by_tapping_on_the_thumbnail),
                    icon = R.drawable.song_lyrics,
                    isChecked = thumbnailTapEnabled,
                    onCheckedChange = { thumbnailTapEnabled = it }
                )
            }

            // Click Lyrics Text
            item {
                ToggleSettingEntry(
                    title = stringResource(R.string.click_lyrics_text),
                    icon = R.drawable.arrow_down,
                    isChecked = clickLyricsText,
                    onCheckedChange = { clickLyricsText = it }
                )
            }

            // Save Lyrics State
            item {
                ToggleSettingEntry(
                    title = stringResource(R.string.save_lyrics_state),
                    subtitle = stringResource(R.string.save_lyrics_state_description),
                    icon = R.drawable.bookmark,
                    isChecked = showLyricsStateKeyPref,
                    onCheckedChange = { showLyricsStateKeyPref = it }
                )
            }

            // Show Background Lyrics
            if (showlyricsthumbnail) {
                item {
                    ToggleSettingEntry(
                        title = stringResource(R.string.show_background_in_lyrics),
                        icon = R.drawable.image,
                        isChecked = showBackgroundLyrics,
                        onCheckedChange = { showBackgroundLyrics = it }
                    )
                }
            }

            // Popup Message
            item {
                ToggleSettingEntry(
                    title = stringResource(R.string.player_enable_lyrics_popup_message),
                    icon = R.drawable.alert,
                    isChecked = playerEnableLyricsPopupMessage,
                    onCheckedChange = { playerEnableLyricsPopupMessage = it }
                )
            }

            // Interval Indicator
            if (lyricsType != LyricsType.Unsynced) {
                item {
                    ToggleSettingEntry(
                        title = stringResource(R.string.interval_indicator),
                        icon = R.drawable.close,
                        isChecked = showIntervalIndicator,
                        onCheckedChange = { showIntervalIndicator = it }
                    )
                }
            }

            // Size Animate
            if (!showlyricsthumbnail && lyricsType != LyricsType.Unsynced) {
                item {
                    ToggleSettingEntry(
                        title = stringResource(R.string.lyricsanimate),
                        icon = R.drawable.close,
                        isChecked = lyricsSizeAnimate,
                        onCheckedChange = { lyricsSizeAnimate = it }
                    )
                }
            }

            // Karaoke Respect Agent Position
            item {
                ToggleSettingEntry(
                    title = stringResource(R.string.karaoke_respect_agent_position),
                    icon = R.drawable.text,
                    isChecked = karaokeRespectAgentPosition,
                    onCheckedChange = { karaokeRespectAgentPosition = it }
                )
            }

            // Section: Translation
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.section_lyrics_translation)) }

            // Translate Toggle
            item {
                ToggleSettingEntry(
                    title = stringResource(R.string.translate_to, otherLanguageApp.text),
                    subtitle = otherLanguageApp.text,
                    icon = R.drawable.translate,
                    isChecked = translateEnabled.value,
                    onCheckedChange = { translateEnabled.value = it }
                )
            }

            // Translate Language
            item {
                EnumSettingEntry(
                    title = stringResource(R.string.translate_to_other_language),
                    icon = R.drawable.translate,
                    selectedValue = otherLanguageApp,
                    values = Languages.entries.toList(),
                    valueText = { it.text },
                    onValueSelected = { lang ->
                        otherLanguageApp = lang
                        translateEnabled.value = true
                    }
                )
            }

            // Romanization
            item {
                ToggleSettingEntry(
                    title = stringResource(R.string.toggle_romanization),
                    icon = R.drawable.text,
                    isChecked = romanizationEnabled,
                    onCheckedChange = { romanizationEnabled = it }
                )
            }

            // Show Second Line
            item {
                ToggleSettingEntry(
                    title = stringResource(R.string.showsecondline),
                    icon = R.drawable.close,
                    isChecked = showSecondLine,
                    onCheckedChange = { showSecondLine = it }
                )
            }

            // Section: Actions
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.section_lyrics_actions)) }

            // Edit Lyrics
            item {
                ActionSettingEntry(
                    title = stringResource(R.string.edit_lyrics),
                    icon = R.drawable.title_edit,
                    onClick = {
                        menuState.hide()
                        onEditLyrics()
                    }
                )
            }

            // Copy Lyrics
            item {
                ActionSettingEntry(
                    title = stringResource(R.string.copy_lyrics),
                    icon = R.drawable.copy,
                    onClick = {
                        menuState.hide()
                        onCopyLyrics()
                    }
                )
            }

            // Search Lyrics Online
            item {
                ActionSettingEntry(
                    title = stringResource(R.string.search_lyrics_online),
                    icon = R.drawable.search,
                    onClick = {
                        menuState.hide()
                        onSearchLyricsOnline()
                    }
                )
            }

            // Fetch Lyrics Again
            item {
                ActionSettingEntry(
                    title = stringResource(R.string.fetch_lyrics_again),
                    icon = R.drawable.sync,
                    enabled = isLyricsNotNull(),
                    onClick = {
                        if (isLyricsNotNull()) {
                            menuState.hide()
                            onFetchLyricsAgain()
                        }
                    }
                )
            }

            // Lyrics Offset
            item {
                ActionSettingEntry(
                    title = stringResource(R.string.lyrics_offset),
                    icon = R.drawable.time,
                    onClick = {
                        menuState.hide()
                        onShowOffsetDialog()
                    }
                )
            }

            // Pick from LrcLib
            if (lyricsType == LyricsType.Synced || lyricsType == LyricsType.Unsynced) {
                item {
                    ActionSettingEntry(
                        title = stringResource(R.string.pick_from) + " LrcLib.net",
                        icon = R.drawable.search,
                        onClick = {
                            menuState.hide()
                            onPickFromLrcLib()
                        }
                    )
                }
            }

            // Spacer
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // Show menu based on style preference
        if (menuStyle == MenuStyle.List) {
            ListMenu.Menu(title = stringResource(R.string.txt_lyrics)) {
                settingsContent()
            }
        } else {
            GridMenu.Menu(title = stringResource(R.string.txt_lyrics)) {
                settingsGridContent()
            }
        }
    }

    // Helper composables

    @Composable
    private fun SettingIcon(icon: Int) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = colorPalette().accent.copy(alpha = 0.1f),
                    shape = uiRoundnessShape()
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                tint = colorPalette().accent,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    @Composable
    private fun SectionTitle(title: String) {
        BasicText(
            text = title,
            style = typography().xxs.semiBold.copy(
                color = colorPalette().accent,
                textAlign = TextAlign.Start
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp)
        )
    }

    @Composable
    private fun ToggleSettingEntry(
        title: String,
        icon: Int,
        isChecked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        subtitle: String? = null
    ) {
        if (menuStyle == MenuStyle.List) {
            ListMenu.Entry(
                text = title,
                icon = { SettingIcon(icon) },
                subtitle = subtitle,
                onClick = { onCheckedChange(!isChecked) },
                trailingContent = {
                    Switch(
                        checked = isChecked,
                        onCheckedChange = null,
                        modifier = Modifier.scale(0.8f),
                        checkedThumbColor = colorPalette().textSecondary,
                        checkedTrackColor = colorPalette().accent.copy(alpha = 0.3f),
                        uncheckedThumbColor = colorPalette().textSecondary,
                        uncheckedTrackColor = colorPalette().textSecondary.copy(alpha = 0.3f)
                    )
                }
            )
        } else {
            GridMenu.Entry(
                text = title,
                icon = { SettingIcon(icon) },
                subtitle = subtitle,
                onClick = { onCheckedChange(!isChecked) },
                trailingContent = {
                    Switch(
                        checked = isChecked,
                        onCheckedChange = null,
                        modifier = Modifier.scale(0.8f),
                        checkedThumbColor = colorPalette().textSecondary,
                        checkedTrackColor = colorPalette().accent.copy(alpha = 0.3f),
                        uncheckedThumbColor = colorPalette().textSecondary,
                        uncheckedTrackColor = colorPalette().textSecondary.copy(alpha = 0.3f)
                    )
                }
            )
        }
    }

    @Composable
    private fun ActionSettingEntry(
        title: String,
        icon: Int,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        if (menuStyle == MenuStyle.List) {
            ListMenu.Entry(
                text = title,
                icon = { SettingIcon(icon) },
                enabled = enabled,
                onClick = onClick,
                trailingContent = {
                    Icon(
                        painter = painterResource(R.drawable.chevron_forward),
                        tint = colorPalette().textSecondary,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        } else {
            GridMenu.Entry(
                text = title,
                icon = { SettingIcon(icon) },
                enabled = enabled,
                onClick = onClick,
                trailingContent = {
                    Icon(
                        painter = painterResource(R.drawable.chevron_forward),
                        tint = colorPalette().textSecondary,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }

    @Composable
    private inline fun <reified T : Enum<T>> EnumSettingEntry(
        title: String,
        icon: Int,
        selectedValue: T,
        values: List<T>,
        noinline valueText: @Composable (T) -> String,
        noinline onValueSelected: (T) -> Unit,
        noinline trailingContent: @Composable () -> Unit = {
            Icon(
                painter = painterResource(R.drawable.chevron_forward),
                tint = colorPalette().textSecondary,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    ) {
        var isShowingDialog by remember { mutableStateOf(false) }

        if (isShowingDialog) {
            ValueSelectorDialog(
                onDismiss = { isShowingDialog = false },
                title = title,
                selectedValue = selectedValue,
                values = values,
                onValueSelected = {
                    onValueSelected(it)
                    isShowingDialog = false
                },
                valueText = valueText
            )
        }

        if (menuStyle == MenuStyle.List) {
            ListMenu.Entry(
                text = title,
                icon = { SettingIcon(icon) },
                subtitle = valueText(selectedValue),
                onClick = { isShowingDialog = true },
                trailingContent = trailingContent
            )
        } else {
            GridMenu.Entry(
                text = title,
                icon = { SettingIcon(icon) },
                subtitle = valueText(selectedValue),
                onClick = { isShowingDialog = true },
                trailingContent = trailingContent
            )
        }
    }
}
