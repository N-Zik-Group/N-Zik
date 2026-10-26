package app.n_zik.android.components.settings.appearance

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import app.it.fast4x.rimusic.enums.AnimatedGradient
import app.it.fast4x.rimusic.enums.CarouselSize
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.enums.IconLikeType
import app.it.fast4x.rimusic.enums.PlayerBackgroundColors
import app.it.fast4x.rimusic.enums.PlayerControlsType
import app.it.fast4x.rimusic.enums.PlayerInfoType
import app.it.fast4x.rimusic.enums.PlayerPlayButtonType
import app.it.fast4x.rimusic.enums.PlayerTimelineSize
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.enums.QueueType
import app.it.fast4x.rimusic.enums.ThumbnailType
import app.n_zik.android.components.dialog.common.AppearancePresetDialog
import app.it.fast4x.rimusic.utils.actionspacedevenlyKey
import app.it.fast4x.rimusic.utils.animatedGradientKey
import app.it.fast4x.rimusic.utils.blurStrengthKey
import app.it.fast4x.rimusic.utils.bottomgradientKey
import app.it.fast4x.rimusic.utils.carouselSizeKey
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.colorPaletteNameKey
import app.it.fast4x.rimusic.utils.expandedplayerKey
import app.it.fast4x.rimusic.utils.expandedplayertoggleKey
import app.it.fast4x.rimusic.utils.fadingedgeKey
import app.it.fast4x.rimusic.utils.iconLikeTypeKey
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.noblurKey
import app.it.fast4x.rimusic.utils.playerBackgroundColorsKey
import app.it.fast4x.rimusic.utils.playerControlsTypeKey
import app.it.fast4x.rimusic.utils.playerInfoShowIconsKey
import app.it.fast4x.rimusic.utils.playerInfoTypeKey
import app.it.fast4x.rimusic.utils.playerPlayButtonTypeKey
import app.it.fast4x.rimusic.utils.playerTimelineSizeKey
import app.it.fast4x.rimusic.utils.playerTimelineTypeKey
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.it.fast4x.rimusic.utils.queueTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showButtonPlayerAddToPlaylistKey
import app.it.fast4x.rimusic.utils.showButtonPlayerArrowKey
import app.it.fast4x.rimusic.utils.showButtonPlayerDiscoverKey
import app.it.fast4x.rimusic.utils.showButtonPlayerDownloadKey
import app.it.fast4x.rimusic.utils.showButtonPlayerLoopKey
import app.it.fast4x.rimusic.utils.showButtonPlayerLyricsKey
import app.it.fast4x.rimusic.utils.showButtonPlayerMenuKey
import app.it.fast4x.rimusic.utils.showButtonPlayerShuffleKey
import app.it.fast4x.rimusic.utils.showButtonPlayerSleepTimerKey
import app.it.fast4x.rimusic.utils.showButtonPlayerStartRadioKey
import app.it.fast4x.rimusic.utils.showButtonPlayerVideoKey
import app.it.fast4x.rimusic.utils.showNextSongsInPlayerKey
import app.it.fast4x.rimusic.utils.showRemainingSongTimeKey
import app.it.fast4x.rimusic.utils.showTopActionsBarKey
import app.it.fast4x.rimusic.utils.showTotalTimeQueueKey
import app.it.fast4x.rimusic.utils.showlyricsthumbnailKey
import app.it.fast4x.rimusic.utils.showthumbnailKey
import app.it.fast4x.rimusic.utils.thumbnailFadeExKey
import app.it.fast4x.rimusic.utils.thumbnailFadeKey
import app.it.fast4x.rimusic.utils.thumbnailSpacingKey
import app.it.fast4x.rimusic.utils.thumbnailTypeKey
import app.it.fast4x.rimusic.utils.topPaddingKey
import app.it.fast4x.rimusic.utils.transparentBackgroundPlayerActionBarKey
import app.it.fast4x.rimusic.utils.transparentbarKey
import app.it.fast4x.rimusic.utils.visualizerEnabledKey
import app.n_zik.android.components.tab.Search
import app.it.fast4x.rimusic.utils.thumbnailSizeDpKey
import app.it.fast4x.rimusic.utils.thumbnailRoundnessDpKey
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
    var playerBackgroundColors by rememberPreference(
        playerBackgroundColorsKey,
        PlayerBackgroundColors.AnimatedGradient
    )
    var showTopActionsBar by rememberPreference(showTopActionsBarKey, true)
    var playerControlsType by rememberPreference(playerControlsTypeKey, PlayerControlsType.Essential)
    var playerInfoType by rememberPreference(playerInfoTypeKey, PlayerInfoType.Modern)
    var transparentBackgroundActionBarPlayer by rememberPreference(
        transparentBackgroundPlayerActionBarKey,
        true
    )
    var iconLikeType by rememberPreference(iconLikeTypeKey, IconLikeType.Essential)
    var actionspacedevenly by rememberPreference(actionspacedevenlyKey, true)
    var thumbnailType by rememberPreference(thumbnailTypeKey, ThumbnailType.Modern)
    var playerType by rememberPreference(playerTypeKey, PlayerType.Essential)
    var queueType by rememberPreference(queueTypeKey, QueueType.Modern)
    var noblur by rememberPreference(noblurKey, true)
    var fadingedge by rememberPreference(fadingedgeKey, false)
    var carouselSize by rememberPreference(carouselSizeKey, CarouselSize.Biggest)
    var playerInfoShowIcons by rememberPreference(playerInfoShowIconsKey, true)
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
    var appearanceChooser by remember { mutableStateOf(false) }
    val searchCtx_0_1 = search.inputValue.isBlank() || stringResource(R.string.player_presets_and_layout).contains(search.inputValue, true) || stringResource(R.string.appearancepresets).contains(search.inputValue, true) || stringResource(R.string.appearancepresetssecondary).contains(search.inputValue, true) || stringResource(R.string.playertype).contains(search.inputValue, true) || stringResource(R.string.queuetype).contains(search.inputValue, true) || stringResource(R.string.thumbnailtype).contains(search.inputValue, true) || stringResource(R.string.pinfo_type).contains(search.inputValue, true) || stringResource(R.string.pinfo_album_and_artist_name).contains(search.inputValue, true) || stringResource(R.string.pinfo_show_icons).contains(search.inputValue, true) || stringResource(R.string.pcontrols_type).contains(search.inputValue, true) || stringResource(R.string.icon_like_button).contains(search.inputValue, true)
    AnimatedVisibility(
                visible = searchCtx_0_1,
                enter = settingsCardEnter,
                exit = settingsCardExit
            ) {
                SettingsSectionCard(
                    modifier = Modifier.testTag("appearance.card.presets"),
                    title = stringResource(R.string.player_presets_and_layout),
                    icon = R.drawable.ui,
                    content = {

            if (appearanceChooser){
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
                    thumbnailSizeDp = 100f
                    showTotalTimeQueue = false
                    transparentBackgroundActionBarPlayer = true
                    showRemainingSongTime = true
                    showlyricsthumbnail = false
                    thumbnailType = ThumbnailType.Modern
                    thumbnailRoundnessDp = 36f
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
                    thumbnailSizeDp = 75f
                    showTotalTimeQueue = false
                    transparentBackgroundActionBarPlayer = true
                    showRemainingSongTime = true
                    showlyricsthumbnail = false
                    thumbnailType = ThumbnailType.Modern
                    thumbnailRoundnessDp = 36f
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
                if (search.inputValue.isBlank() || stringResource(R.string.appearancepresets).contains(search.inputValue, true) || (stringResource(R.string.appearancepresetssecondary)).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_presets_and_layout.appearancepresets"),
                        title = stringResource(R.string.appearancepresets),
                        text = stringResource(R.string.appearancepresetssecondary),
                        icon = R.drawable.ui,
                        onClick = { appearanceChooser = true }
                    )
                }
            }

            if (search.inputValue.isBlank() || stringResource(R.string.playertype).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.playertype).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_presets_and_layout.playertype"),
                        icon = R.drawable.play,
                        title = stringResource(R.string.playertype),
                        selectedValue = playerType,
                        onValueSelected = {
                            playerType = it
                        },
                        valueText = { it.text },
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.queuetype).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.queuetype).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_presets_and_layout.queuetype"),
                        icon = R.drawable.sort_grid,
                        title = stringResource(R.string.queuetype),
                        selectedValue = queueType,
                        onValueSelected = {
                            queueType = it
                        },
                        valueText = { it.text },
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.thumbnailtype).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.thumbnailtype).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        icon = R.drawable.cd,
                        title = stringResource(R.string.thumbnailtype),
                        selectedValue = thumbnailType,
                        onValueSelected = {
                            thumbnailType = it
                        },
                        valueText = { it.text },
                        modifier = (Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)).settingsSearchTarget("appearance.player_presets_and_layout.thumbnailtype")
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.pinfo_type).contains(
                    search.inputValue,
                    true
                )
            ) {
                if (search.inputValue.isBlank() || stringResource(R.string.pinfo_type).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_presets_and_layout.pinfo_type"),
                        icon = R.drawable.information,
                        title = stringResource(R.string.pinfo_type),
                        selectedValue = playerInfoType,
                        onValueSelected = {
                            playerInfoType = it
                        },
                        valueText = { it.text },
                    )
                }
                SettingsDescription(text = stringResource(R.string.pinfo_album_and_artist_name))

                AnimatedVisibility( visible = playerInfoType == PlayerInfoType.Modern) {
                    Column {
                        if (search.inputValue.isBlank() || stringResource(R.string.pinfo_show_icons).contains(
                                search.inputValue,
                                true
                            )
                        )
                            if (search.inputValue.isBlank() || stringResource(R.string.pinfo_show_icons).contains(search.inputValue, true)) {
                                OtherSwitchSettingEntry(
                        icon = R.drawable.information,
                                    title = stringResource(R.string.pinfo_show_icons),
                                    text = "",
                                    isChecked = playerInfoShowIcons,
                                    onCheckedChange = { playerInfoShowIcons = it },
                                    modifier = (Modifier
                                        .padding(start = 25.dp)).settingsSearchTarget("appearance.player_presets_and_layout.pinfo_show_icons")
                                )
                            }
                    }
                }

            }

            if (search.inputValue.isBlank() || stringResource(R.string.pcontrols_type).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.pcontrols_type).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_presets_and_layout.pcontrols_type"),
                        icon = R.drawable.burger,
                        title = stringResource(R.string.pcontrols_type),
                        selectedValue = playerControlsType,
                        onValueSelected = {
                            playerControlsType = it
                        },
                        valueText = { it.text }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.icon_like_button).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.icon_like_button).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.player_presets_and_layout.icon_like_button"),
                        icon = R.drawable.heart,
                        title = stringResource(R.string.icon_like_button),
                        selectedValue = iconLikeType,
                        onValueSelected = {
                            iconLikeType = it
                        },
                        valueText = { it.text },
                    )
                }


            }
        )
            /* Removed Spacer */
            }
}
