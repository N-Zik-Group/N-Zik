package app.n_zik.android.components.settings.appearance

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.enums.AnimatedGradient
import app.n_zik.android.enums.CarouselSize
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.PlayerBackgroundColors
import app.n_zik.android.enums.PlayerControlsType
import app.n_zik.android.enums.PlayerInfoType
import app.n_zik.android.enums.PlayerPlayButtonType
import app.n_zik.android.enums.PlayerTimelineSize
import app.n_zik.android.enums.PlayerTimelineType
import app.n_zik.android.enums.PlayerType
import app.n_zik.android.enums.QueueType
import app.n_zik.android.enums.ThumbnailType
import app.n_zik.android.components.dialog.common.AppearancePresetDialog
import app.n_zik.android.utils.actionspacedevenlyKey
import app.n_zik.android.utils.animatedGradientKey
import app.n_zik.android.utils.blurStrengthKey
import app.n_zik.android.utils.bottomgradientKey
import app.n_zik.android.utils.carouselKey
import app.n_zik.android.utils.carouselSizeKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.expandedplayerKey
import app.n_zik.android.utils.expandedplayertoggleKey
import app.n_zik.android.utils.fadingedgeKey
import app.n_zik.android.utils.isLandscape
import app.n_zik.android.utils.noblurKey
import app.n_zik.android.utils.playerBackgroundColorsKey
import app.n_zik.android.utils.playerControlsTypeKey
import app.n_zik.android.utils.playerInfoShowIconsKey
import app.n_zik.android.utils.playerInfoTypeKey
import app.n_zik.android.utils.playerPlayButtonTypeKey
import app.n_zik.android.utils.playerTimelineSizeKey
import app.n_zik.android.utils.playerTimelineTypeKey
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.utils.queueTypeKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.showButtonPlayerAddToPlaylistKey
import app.n_zik.android.utils.showButtonPlayerArrowKey
import app.n_zik.android.utils.showButtonPlayerDiscoverKey
import app.n_zik.android.utils.showButtonPlayerDownloadKey
import app.n_zik.android.utils.showButtonPlayerLoopKey
import app.n_zik.android.utils.showButtonPlayerLyricsKey
import app.n_zik.android.utils.showButtonPlayerMenuKey
import app.n_zik.android.utils.showButtonPlayerShuffleKey
import app.n_zik.android.utils.showButtonPlayerSleepTimerKey
import app.n_zik.android.utils.showButtonPlayerStartRadioKey
import app.n_zik.android.utils.showButtonPlayerVideoKey
import app.n_zik.android.utils.showNextSongsInPlayerKey
import app.n_zik.android.utils.showRemainingSongTimeKey
import app.n_zik.android.utils.showTopActionsBarKey
import app.n_zik.android.utils.showTotalTimeQueueKey
import app.n_zik.android.utils.showlyricsthumbnailKey
import app.n_zik.android.utils.showthumbnailKey
import app.n_zik.android.utils.thumbnailFadeExKey
import app.n_zik.android.utils.thumbnailFadeKey
import app.n_zik.android.utils.thumbnailSpacingKey
import app.n_zik.android.utils.thumbnailTypeKey
import app.n_zik.android.utils.topPaddingKey
import app.n_zik.android.utils.transparentBackgroundPlayerActionBarKey
import app.n_zik.android.utils.transparentbarKey
import app.n_zik.android.utils.visualizerEnabledKey
import app.n_zik.android.components.tab.Search
import app.n_zik.android.utils.thumbnailSizeDpKey
import app.n_zik.android.utils.thumbnailRoundnessDpKey
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherEnumValueSelectorSettingsEntry
import androidx.compose.ui.platform.testTag
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit

@OptIn(ExperimentalAnimationApi::class)
@UnstableApi
@Composable
internal fun AppearancePresetsCard(search: Search) {
    var playerBackgroundColors by rememberPreference(
        playerBackgroundColorsKey,
        PlayerBackgroundColors.AnimatedGradient
    )
    var playerControlsType by rememberPreference(playerControlsTypeKey, PlayerControlsType.Essential)
    var playerInfoType by rememberPreference(playerInfoTypeKey, PlayerInfoType.Modern)
    var thumbnailType by rememberPreference(thumbnailTypeKey, ThumbnailType.Modern)
    var playerType by rememberPreference(playerTypeKey, PlayerType.Essential)
    var queueType by rememberPreference(queueTypeKey, QueueType.Modern)
    var playerInfoShowIcons by rememberPreference(playerInfoShowIconsKey, true)
    var appearanceChooser by remember { mutableStateOf(false) }
    val searchCtx_0_1 = search.inputValue.isBlank() || stringResource(R.string.player_presets_and_layout).contains(search.inputValue, true) || stringResource(R.string.settings_player_appearance_presets_title).contains(search.inputValue, true) || stringResource(R.string.settings_player_appearance_presets_preview_description).contains(search.inputValue, true) || stringResource(R.string.player_type).contains(search.inputValue, true) || stringResource(R.string.settings_player_queue_style_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_artwork_style_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_header_style_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_header_album_artist_description).contains(search.inputValue, true) || stringResource(R.string.settings_player_header_icons_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_controls_style_label).contains(search.inputValue, true)
    AnimatedVisibility(
                visible = searchCtx_0_1,
                enter = settingsCardEnter,
                exit = settingsCardExit
            ) {
                SettingsSectionCard(
                    modifier = Modifier.testTag("appearance.card.presets"),
                    title = stringResource(R.string.player_presets_and_layout),
                    icon = R.drawable.images_sharp,
                    content = {

            DialogPresence(visible = appearanceChooser) {
                // Preset-only preferences are needed only while the chooser is open.
                var showthumbnail by rememberPreference(showthumbnailKey, true)
                var transparentbar by rememberPreference(transparentbarKey, true)
                var showlyricsthumbnail by rememberPreference(showlyricsthumbnailKey, true)
                var expandedplayer by rememberPreference(expandedplayerKey, false)
                var playerPlayButtonType by rememberPreference(
                    playerPlayButtonTypeKey,
                    PlayerPlayButtonType.CircularRibbed
                )
                var bottomgradient by rememberPreference(bottomgradientKey, false)
                var visualizerEnabled by rememberPreference(visualizerEnabledKey, false)
                var playerTimelineType by rememberPreference(playerTimelineTypeKey, PlayerTimelineType.Wavy)
                var thumbnailSizeDp by rememberPreference(thumbnailSizeDpKey, 90f)
                var playerTimelineSize by rememberPreference(
                    playerTimelineSizeKey,
                    PlayerTimelineSize.Biggest
                )
                var showButtonPlayerAddToPlaylist by rememberPreference(showButtonPlayerAddToPlaylistKey, true)
                var showButtonPlayerArrow by rememberPreference(showButtonPlayerArrowKey, true)
                var showButtonPlayerDownload by rememberPreference(showButtonPlayerDownloadKey, true)
                var showButtonPlayerLoop by rememberPreference(showButtonPlayerLoopKey, false)
                var showButtonPlayerLyrics by rememberPreference(showButtonPlayerLyricsKey, true)
                var expandedplayertoggle by rememberPreference(expandedplayertoggleKey, false)
                var showButtonPlayerShuffle by rememberPreference(showButtonPlayerShuffleKey, true)
                var showButtonPlayerSleepTimer by rememberPreference(showButtonPlayerSleepTimerKey, false)
                var showButtonPlayerMenu by rememberPreference(showButtonPlayerMenuKey, false)
                var showButtonPlayerStartradio by rememberPreference(showButtonPlayerStartRadioKey, false)
                var showButtonPlayerDiscover by rememberPreference(showButtonPlayerDiscoverKey, false)
                var showButtonPlayerVideo by rememberPreference(showButtonPlayerVideoKey, true)
                var showTotalTimeQueue by rememberPreference(showTotalTimeQueueKey, true)
                var showNextSongsInPlayer by rememberPreference(showNextSongsInPlayerKey, false)
                var showRemainingSongTime by rememberPreference(showRemainingSongTimeKey, true)
                var thumbnailRoundnessDp by rememberPreference(thumbnailRoundnessDpKey, 12f)
                var showTopActionsBar by rememberPreference(showTopActionsBarKey, true)
                var transparentBackgroundActionBarPlayer by rememberPreference(
                    transparentBackgroundPlayerActionBarKey,
                    true
                )
                var actionspacedevenly by rememberPreference(actionspacedevenlyKey, true)
                var noblur by rememberPreference(noblurKey, true)
                var fadingedge by rememberPreference(fadingedgeKey, false)
                var carousel by rememberPreference(carouselKey, true)
                var carouselSize by rememberPreference(carouselSizeKey, CarouselSize.Biggest)
                var topPadding by rememberPreference(topPaddingKey, true)
                var animatedGradient by rememberPreference(
                    animatedGradientKey,
                    AnimatedGradient.M3EMorphingCover
                )
                var blurStrength by rememberPreference(blurStrengthKey, 25f)
                var thumbnailFadeEx  by rememberPreference(thumbnailFadeExKey, 5f)
                var thumbnailFade  by rememberPreference(thumbnailFadeKey, 5f)
                var thumbnailSpacing  by rememberPreference(thumbnailSpacingKey, 0f)
                var colorPaletteName by rememberPreference(colorPaletteNameKey, ColorPaletteName.Dynamic)
                var colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)

                AppearancePresetDialog(
                onDismiss = {appearanceChooser = false},
                onClick0 = {
                    showTopActionsBar = true
                    showthumbnail = true
                    playerBackgroundColors = PlayerBackgroundColors.BlurredCoverColor
                    blurStrength = 50f
                    thumbnailRoundnessDp = 0f
                    playerInfoType = PlayerInfoType.Essential
                    playerTimelineType = PlayerTimelineType.ThinBar
                    playerTimelineSize = PlayerTimelineSize.Biggest
                    playerControlsType = PlayerControlsType.Essential
                    playerPlayButtonType = PlayerPlayButtonType.CircularRibbed
                    transparentbar = true
                    playerType = PlayerType.Essential
                    showlyricsthumbnail = false
                    expandedplayer = true
                    thumbnailType = ThumbnailType.Modern
                    thumbnailSizeDp = 75f
                    showTotalTimeQueue = false
                    bottomgradient = true
                    showRemainingSongTime = true
                    showNextSongsInPlayer = false
                    colorPaletteName = ColorPaletteName.Dynamic
                    colorPaletteMode = ColorPaletteMode.System
                    transparentBackgroundActionBarPlayer = true
                    actionspacedevenly = true
                    showButtonPlayerVideo = false
                    showButtonPlayerDiscover = false
                    showButtonPlayerDownload = false
                    showButtonPlayerAddToPlaylist = true
                    showButtonPlayerLoop = false
                    showButtonPlayerShuffle = true
                    showButtonPlayerLyrics = false
                    expandedplayertoggle = false
                    showButtonPlayerSleepTimer = false
                    visualizerEnabled = false
                    appearanceChooser = false
                    showButtonPlayerArrow = true
                    showButtonPlayerStartradio = false
                    showButtonPlayerMenu = true
                    appearanceChooser = false
                },
                onClick1 = {
                    showTopActionsBar = true
                    showthumbnail = true
                    playerBackgroundColors = PlayerBackgroundColors.BlurredCoverColor
                    blurStrength = 50f
                    playerInfoType = PlayerInfoType.Essential
                    playerPlayButtonType = PlayerPlayButtonType.Disabled
                    playerTimelineType = PlayerTimelineType.ThinBar
                    playerControlsType = PlayerControlsType.Essential
                    transparentbar = true
                    playerType = PlayerType.Modern
                    expandedplayer = true
                    fadingedge = true
                    thumbnailFadeEx = 4f
                    thumbnailSpacing = -32f
                    thumbnailType = ThumbnailType.Essential
                    carousel = true
                    carouselSize = CarouselSize.Big
                    thumbnailSizeDp = 100f
                    showTotalTimeQueue = false
                    transparentBackgroundActionBarPlayer = true
                    showRemainingSongTime = true
                    bottomgradient = true
                    showlyricsthumbnail = false
                    thumbnailRoundnessDp = 18f
                    showNextSongsInPlayer = true
                    colorPaletteName = ColorPaletteName.Dynamic
                    colorPaletteMode = ColorPaletteMode.System
                    transparentBackgroundActionBarPlayer = true
                    actionspacedevenly = true
                    showButtonPlayerVideo = false
                    showButtonPlayerDiscover = false
                    showButtonPlayerDownload = false
                    showButtonPlayerAddToPlaylist = true
                    showButtonPlayerLoop = false
                    showButtonPlayerShuffle = false
                    showButtonPlayerLyrics = false
                    expandedplayertoggle = true
                    showButtonPlayerSleepTimer = false
                    visualizerEnabled = false
                    appearanceChooser = false
                    showButtonPlayerArrow = true
                    showButtonPlayerStartradio = false
                    showButtonPlayerMenu = true
                    appearanceChooser = false
                },
                onClick2 = {
                    showTopActionsBar = false
                    showthumbnail = false
                    noblur = true
                    topPadding = false
                    playerBackgroundColors = PlayerBackgroundColors.BlurredCoverColor
                    blurStrength = 50f
                    playerPlayButtonType = PlayerPlayButtonType.Disabled
                    playerInfoType = PlayerInfoType.Modern
                    playerInfoShowIcons = false
                    playerTimelineType = PlayerTimelineType.ThinBar
                    playerControlsType = PlayerControlsType.Essential
                    transparentbar = true
                    playerType = PlayerType.Modern
                    expandedplayer = true
                    showTotalTimeQueue = false
                    transparentBackgroundActionBarPlayer = true
                    showRemainingSongTime = true
                    bottomgradient = true
                    showlyricsthumbnail = false
                    showNextSongsInPlayer = false
                    colorPaletteName = ColorPaletteName.Dynamic
                    colorPaletteMode = ColorPaletteMode.System
                    transparentBackgroundActionBarPlayer = true
                    actionspacedevenly = true
                    showButtonPlayerVideo = false
                    showButtonPlayerDiscover = false
                    showButtonPlayerDownload = false
                    showButtonPlayerAddToPlaylist = false
                    showButtonPlayerLoop = false
                    showButtonPlayerShuffle = false
                    showButtonPlayerLyrics = false
                    expandedplayertoggle = false
                    showButtonPlayerSleepTimer = false
                    visualizerEnabled = false
                    appearanceChooser = false
                    showButtonPlayerArrow = true
                    showButtonPlayerStartradio = false
                    showButtonPlayerMenu = true
                    appearanceChooser = false
                },
                onClick3 = {
                    carousel = false
                    showTopActionsBar = false
                    topPadding = false
                    showthumbnail = true
                    playerBackgroundColors = PlayerBackgroundColors.BlurredCoverColor
                    blurStrength = 50f
                    playerInfoType = PlayerInfoType.Essential
                    playerTimelineType = PlayerTimelineType.AudioWaves
                    playerTimelineSize = PlayerTimelineSize.Biggest
                    playerControlsType = PlayerControlsType.Modern
                    playerPlayButtonType = PlayerPlayButtonType.Disabled
                    colorPaletteName = ColorPaletteName.PureBlack
                    transparentbar = false
                    playerType = PlayerType.Essential
                    expandedplayer = false
                    thumbnailSizeDp = 100f
                    showTotalTimeQueue = false
                    transparentBackgroundActionBarPlayer = true
                    showRemainingSongTime = true
                    bottomgradient = true
                    showlyricsthumbnail = false
                    thumbnailType = ThumbnailType.Essential
                    thumbnailRoundnessDp = 9f
                    playerType = PlayerType.Modern
                    fadingedge = true
                    thumbnailFade = 5f
                    showNextSongsInPlayer = false
                    transparentBackgroundActionBarPlayer = true
                    actionspacedevenly = true
                    showButtonPlayerVideo = false
                    showButtonPlayerDiscover = false
                    showButtonPlayerDownload = false
                    showButtonPlayerAddToPlaylist = false
                    showButtonPlayerLoop = true
                    showButtonPlayerShuffle = true
                    showButtonPlayerLyrics = false
                    expandedplayertoggle = false
                    showButtonPlayerSleepTimer = false
                    visualizerEnabled = false
                    appearanceChooser = false
                    showButtonPlayerArrow = true
                    showButtonPlayerStartradio = false
                    showButtonPlayerMenu = true
                    appearanceChooser = false
                },
                onClick4 = {
                    carousel = false
                    showTopActionsBar = false
                    topPadding = true
                    showthumbnail = true
                    playerBackgroundColors = PlayerBackgroundColors.AnimatedGradient
                    animatedGradient = AnimatedGradient.Linear
                    playerInfoType = PlayerInfoType.Essential
                    playerTimelineType = PlayerTimelineType.PinBar
                    playerTimelineSize = PlayerTimelineSize.Biggest
                    playerControlsType = PlayerControlsType.Essential
                    playerPlayButtonType = PlayerPlayButtonType.Square
                    colorPaletteName = ColorPaletteName.Dynamic
                    colorPaletteMode = ColorPaletteMode.PitchBlack
                    transparentbar = false
                    playerType = PlayerType.Modern
                    expandedplayer = false
                    thumbnailSizeDp = 85f
                    showTotalTimeQueue = false
                    transparentBackgroundActionBarPlayer = true
                    showRemainingSongTime = true
                    showlyricsthumbnail = false
                    thumbnailType = ThumbnailType.Modern
                    thumbnailRoundnessDp = 20f
                    fadingedge = true
                    thumbnailFade = 0f
                    thumbnailFadeEx = 5f
                    thumbnailSpacing = -32f
                    showNextSongsInPlayer = false
                    transparentBackgroundActionBarPlayer = true
                    actionspacedevenly = true
                    showButtonPlayerVideo = false
                    showButtonPlayerDiscover = false
                    showButtonPlayerDownload = true
                    showButtonPlayerAddToPlaylist = false
                    showButtonPlayerLoop = false
                    showButtonPlayerShuffle = false
                    showButtonPlayerLyrics = false
                    expandedplayertoggle = true
                    showButtonPlayerSleepTimer = false
                    visualizerEnabled = false
                    appearanceChooser = false
                    showButtonPlayerArrow = true
                    showButtonPlayerStartradio = false
                    showButtonPlayerMenu = true
                    appearanceChooser = false
                },
                onClick5 = {
                    showTopActionsBar = true
                    showthumbnail = true
                    playerBackgroundColors = PlayerBackgroundColors.CoverColorGradient
                    playerInfoType = PlayerInfoType.Essential
                    playerTimelineType = PlayerTimelineType.Wavy
                    playerTimelineSize = PlayerTimelineSize.Biggest
                    playerControlsType = PlayerControlsType.Essential
                    playerPlayButtonType = PlayerPlayButtonType.CircularRibbed
                    colorPaletteName = ColorPaletteName.Dynamic
                    colorPaletteMode = ColorPaletteMode.System
                    transparentbar = false
                    playerType = PlayerType.Essential
                    expandedplayer = true
                    thumbnailSizeDp = 80f
                    showTotalTimeQueue = false
                    transparentBackgroundActionBarPlayer = true
                    showRemainingSongTime = true
                    showlyricsthumbnail = false
                    thumbnailType = ThumbnailType.Modern
                    thumbnailRoundnessDp = 20f
                    showNextSongsInPlayer = false
                    transparentBackgroundActionBarPlayer = true
                    actionspacedevenly = true
                    showButtonPlayerVideo = false
                    showButtonPlayerDiscover = false
                    showButtonPlayerDownload = false
                    showButtonPlayerAddToPlaylist = false
                    showButtonPlayerLoop = false
                    showButtonPlayerShuffle = true
                    showButtonPlayerLyrics = true
                    expandedplayertoggle = false
                    showButtonPlayerSleepTimer = false
                    visualizerEnabled = false
                    appearanceChooser = false
                    showButtonPlayerArrow = true
                    showButtonPlayerStartradio = false
                    showButtonPlayerMenu = true
                    appearanceChooser = false
                }
                )
            }

            if (!isLandscape) {
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_appearance_presets_title).contains(search.inputValue, true) || (stringResource(R.string.settings_player_appearance_presets_preview_description)).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_presets_and_layout.appearancepresets"),
                        title = stringResource(R.string.settings_player_appearance_presets_title),
                        text = stringResource(R.string.settings_player_appearance_presets_preview_description),
                        icon = R.drawable.ui,
                        onClick = { appearanceChooser = true }
                    )
                }
            }

            if (search.inputValue.isBlank() || stringResource(R.string.player_type).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.player_type).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_layout_player_style_description), playerType.text),
                        modifier = Modifier.settingsSearchTarget("appearance.player_presets_and_layout.playertype"),
                        icon = R.drawable.play,
                        title = stringResource(R.string.player_type),
                        selectedValue = playerType,
                        onValueSelected = {
                            playerType = it
                        },
                        valueText = { it.text },
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_queue_style_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_queue_style_label).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_layout_queue_style_description), queueType.text),
                        modifier = Modifier.settingsSearchTarget("appearance.player_presets_and_layout.queuetype"),
                        icon = R.drawable.sort_grid,
                        title = stringResource(R.string.settings_player_queue_style_label),
                        selectedValue = queueType,
                        onValueSelected = {
                            queueType = it
                        },
                        valueText = { it.text },
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_style_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_style_label).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_layout_artwork_style_description), thumbnailType.text),
                        icon = R.drawable.cd,
                        title = stringResource(R.string.settings_player_artwork_style_label),
                        selectedValue = thumbnailType,
                        onValueSelected = {
                            thumbnailType = it
                        },
                        valueText = { it.text },
                        modifier = (Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)).settingsSearchTarget("appearance.player_presets_and_layout.thumbnailtype")
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_header_style_label).contains(
                    search.inputValue,
                    true
                )
            ) {
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_header_style_label).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_layout_information_style_description), playerInfoType.text),
                        modifier = Modifier.settingsSearchTarget("appearance.player_presets_and_layout.pinfo_type"),
                        icon = R.drawable.information,
                        title = stringResource(R.string.settings_player_header_style_label),
                        selectedValue = playerInfoType,
                        onValueSelected = {
                            playerInfoType = it
                        },
                        valueText = { it.text },
                    )
                }
                SettingsDescription(text = stringResource(R.string.settings_player_header_album_artist_description))

                AnimatedVisibility( visible = playerInfoType == PlayerInfoType.Modern) {
                    Column {
                        if (search.inputValue.isBlank() || stringResource(R.string.settings_player_header_icons_visible_label).contains(
                                search.inputValue,
                                true
                            )
                        )
                            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_header_icons_visible_label).contains(search.inputValue, true)) {
                                OtherSwitchSettingEntry(
                        icon = R.drawable.information,
                                    title = stringResource(R.string.settings_player_header_icons_visible_label),
                                    text = stringResource(R.string.player_layout_information_icons_description),
                                    isChecked = playerInfoShowIcons,
                                    onCheckedChange = { playerInfoShowIcons = it },
                                    modifier = (Modifier
                                        .padding(start = 25.dp)).settingsSearchTarget("appearance.player_presets_and_layout.pinfo_show_icons")
                                )
                            }
                    }
                }

            }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_controls_style_label).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_controls_style_label).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_layout_controls_style_description), playerControlsType.text),
                        modifier = Modifier.settingsSearchTarget("appearance.player_presets_and_layout.pcontrols_type"),
                        icon = R.drawable.burger,
                        title = stringResource(R.string.settings_player_controls_style_label),
                        selectedValue = playerControlsType,
                        onValueSelected = {
                            playerControlsType = it
                        },
                        valueText = { it.text }
                    )
                }




            }
        )
            }
}
