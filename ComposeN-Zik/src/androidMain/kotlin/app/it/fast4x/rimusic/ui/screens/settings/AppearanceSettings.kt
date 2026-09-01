package app.it.fast4x.rimusic.ui.screens.settings
import app.n_zik.android.LocalPlayerAwareWindowInsets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.AnimatedGradient
import app.it.fast4x.rimusic.enums.BackgroundProgress
import app.it.fast4x.rimusic.enums.CarouselSize
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.enums.IconLikeType
import app.it.fast4x.rimusic.enums.MiniPlayerType
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.NotificationButtons
import app.it.fast4x.rimusic.enums.PlayerBackgroundColors
import app.n_zik.android.enums.PlayerControlsColors
import app.it.fast4x.rimusic.enums.PlayerControlsType
import app.it.fast4x.rimusic.enums.PlayerInfoType
import app.it.fast4x.rimusic.enums.PlayerPlayButtonType
import app.it.fast4x.rimusic.enums.PlayerTimelineSize
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.enums.PlayerVisualizerType
import app.it.fast4x.rimusic.enums.PrevNextSongs
import app.it.fast4x.rimusic.enums.QueueType
import app.it.fast4x.rimusic.enums.SongsNumber
import app.it.fast4x.rimusic.enums.SwipeAnimationNoThumbnail
import app.it.fast4x.rimusic.enums.ThumbnailCoverType
import app.it.fast4x.rimusic.enums.ThumbnailType
import app.it.fast4x.rimusic.enums.WallpaperType
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.themed.AppearancePresetDialog
import app.n_zik.android.components.dialog.settings.PlayerActionBarSettingsDialog
import app.n_zik.android.components.dialog.settings.MiniPlayerButtonsSettingsDialog
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.RestartPlayerService
import app.it.fast4x.rimusic.utils.actionExpandedKey
import app.it.fast4x.rimusic.utils.actionspacedevenlyKey
import app.it.fast4x.rimusic.utils.albumCoverRotationKey
import app.it.fast4x.rimusic.utils.animatedGradientKey
import app.it.fast4x.rimusic.utils.backgroundProgressKey
import app.it.fast4x.rimusic.utils.blackgradientKey
import app.it.fast4x.rimusic.utils.blurStrengthKey
import app.it.fast4x.rimusic.utils.bottomgradientKey
import app.it.fast4x.rimusic.utils.buttonzoomoutKey
import app.it.fast4x.rimusic.utils.carouselKey
import app.it.fast4x.rimusic.utils.carouselSizeKey
import app.it.fast4x.rimusic.utils.clickOnLyricsTextKey
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.colorPaletteNameKey
import app.it.fast4x.rimusic.utils.controlsExpandedKey
import app.it.fast4x.rimusic.utils.coverThumbnailAnimationKey
import app.it.fast4x.rimusic.utils.disablePlayerHorizontalSwipeKey
import app.it.fast4x.rimusic.utils.playerSwipeSensitivityKey
import app.n_zik.android.enums.PlayerSwipeSensitivity
import app.it.fast4x.rimusic.utils.shakeSensitivityThemeKey
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.effectRotationKey
import app.it.fast4x.rimusic.utils.enableWallpaperKey
import app.it.fast4x.rimusic.utils.expandedplayerKey
import app.it.fast4x.rimusic.utils.expandedplayertoggleKey
import app.it.fast4x.rimusic.utils.fadingedgeKey
import app.it.fast4x.rimusic.utils.iconLikeTypeKey
import app.it.fast4x.rimusic.utils.isAtLeastAndroid7
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.isShowingThumbnailInLockscreenKey
import app.it.fast4x.rimusic.utils.keepPlayerMinimizedKey
import app.it.fast4x.rimusic.utils.lastPlayerPlayButtonTypeKey
import app.it.fast4x.rimusic.utils.miniPlayerTypeKey
import app.it.fast4x.rimusic.utils.miniQueueExpandedKey
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.noblurKey
import app.it.fast4x.rimusic.utils.notificationPlayerFirstIconKey
import app.it.fast4x.rimusic.utils.notificationPlayerSecondIconKey
import app.it.fast4x.rimusic.utils.playerBackgroundColorsKey
import app.it.fast4x.rimusic.utils.playerControlsTypeKey
import app.it.fast4x.rimusic.utils.playerControlsColorsKey
import app.it.fast4x.rimusic.utils.playerEnableLyricsPopupMessageKey
import app.it.fast4x.rimusic.utils.playerInfoShowIconsKey
import app.it.fast4x.rimusic.utils.playerInfoTypeKey
import app.it.fast4x.rimusic.utils.playerPlayButtonTypeKey
import app.it.fast4x.rimusic.utils.playerSwapControlsWithTimelineKey
import app.it.fast4x.rimusic.utils.playerTimelineSizeKey
import app.it.fast4x.rimusic.utils.playerTimelineTypeKey
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.it.fast4x.rimusic.utils.prevNextSongsKey
import app.it.fast4x.rimusic.utils.queueDurationExpandedKey
import app.it.fast4x.rimusic.utils.queueTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.rotatingAlbumCoverKey
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.showBackgroundLyricsKey
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
import app.it.fast4x.rimusic.utils.showButtonPlayerSystemEqualizerKey
import app.it.fast4x.rimusic.utils.showButtonPlayerVideoKey
import app.it.fast4x.rimusic.utils.showCoverThumbnailAnimationKey
import app.it.fast4x.rimusic.utils.showDownloadButtonBackgroundPlayerKey
import app.it.fast4x.rimusic.utils.showLikeButtonBackgroundPlayerKey
import app.it.fast4x.rimusic.utils.showLyricsStateKey
import app.it.fast4x.rimusic.utils.showNextSongsInPlayerKey
import app.it.fast4x.rimusic.utils.showRemainingSongTimeKey
import app.it.fast4x.rimusic.utils.showSkipTimeButtonsKey
import app.it.fast4x.rimusic.utils.showTopActionsBarKey
import app.it.fast4x.rimusic.utils.showTotalTimeQueueKey
import app.it.fast4x.rimusic.utils.showVisualizerStateKey
import app.it.fast4x.rimusic.utils.showalbumcoverKey
import app.it.fast4x.rimusic.utils.showlyricsthumbnailKey
import app.it.fast4x.rimusic.utils.showsongsKey
import app.it.fast4x.rimusic.utils.showthumbnailKey
import app.it.fast4x.rimusic.utils.showvisthumbnailKey
import app.it.fast4x.rimusic.utils.showVisualizerButtonsKey
import app.it.fast4x.rimusic.utils.blackBackgroundForVisThumbnailKey
import app.it.fast4x.rimusic.utils.playerVisualizerTypeKey
import app.it.fast4x.rimusic.utils.statsExpandedKey
import app.it.fast4x.rimusic.utils.statsfornerdsKey
import app.it.fast4x.rimusic.utils.swipeAnimationsNoThumbnailKey
import app.it.fast4x.rimusic.utils.swipeUpQueueKey
import app.it.fast4x.rimusic.utils.tapqueueKey
import app.it.fast4x.rimusic.utils.textoutlineKey
import app.it.fast4x.rimusic.utils.thumbnailFadeExKey
import app.it.fast4x.rimusic.utils.thumbnailFadeKey
import app.it.fast4x.rimusic.utils.thumbnailSpacingKey
import app.it.fast4x.rimusic.utils.thumbnailTapEnabledKey
import app.it.fast4x.rimusic.utils.thumbnailTypeKey
import app.it.fast4x.rimusic.utils.thumbnailpauseKey
import app.it.fast4x.rimusic.utils.cropVideoThumbnailsKey
import app.it.fast4x.rimusic.utils.timelineExpandedKey
import app.it.fast4x.rimusic.utils.titleExpandedKey
import app.it.fast4x.rimusic.utils.topPaddingKey
import app.it.fast4x.rimusic.utils.transparentBackgroundPlayerActionBarKey
import app.it.fast4x.rimusic.utils.transparentbarKey
import app.it.fast4x.rimusic.utils.visualizerEnabledKey
import app.it.fast4x.rimusic.utils.wallpaperTypeKey
import app.n_zik.android.components.tab.Search
import app.kreate.android.me.knighthat.utils.Toaster
import app.it.fast4x.rimusic.ui.components.themed.DialogColorPicker
import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
import app.n_zik.android.enums.ShakeSensitivityTheme
import app.n_zik.android.enums.lyrics.LyricsColor
import app.it.fast4x.rimusic.utils.uiRoundnessDpKey
import androidx.compose.foundation.shape.CircleShape
import app.it.fast4x.rimusic.utils.visualizerWhiteColorOptionKey
import androidx.compose.material3.TextButton
import app.it.fast4x.rimusic.utils.thumbnailSizeLDpKey
import app.n_zik.android.uiRoundnessShape
import androidx.compose.foundation.layout.Row
import app.it.fast4x.rimusic.utils.thumbnailSizeDpKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import app.it.fast4x.rimusic.utils.lyricsColorKey
import app.it.fast4x.rimusic.utils.thumbnailRoundnessDpKey
import app.it.fast4x.rimusic.utils.artistThumbnailRoundnessDpKey
import androidx.compose.ui.text.font.FontWeight
import app.it.fast4x.rimusic.utils.lyricsCustomColorKey
import androidx.compose.runtime.LaunchedEffect
import app.it.fast4x.rimusic.utils.visualizerLineThicknessKey
import app.it.fast4x.rimusic.utils.visualizerCustomColorKey
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun DefaultAppearanceSettings() {
    var isShowingThumbnailInLockscreen by rememberPreference(
        isShowingThumbnailInLockscreenKey,
        true
    )
    isShowingThumbnailInLockscreen = true

    var showthumbnail by rememberPreference(showthumbnailKey, true)
    showthumbnail = true

    var transparentbar by rememberPreference(transparentbarKey, true)
    transparentbar = true

    var blackgradient by rememberPreference(blackgradientKey, false)
    blackgradient = false

    var showlyricsthumbnail by rememberPreference(showlyricsthumbnailKey, true)
    showlyricsthumbnail = true

    var expandedplayer by rememberPreference(expandedplayerKey, false)
    expandedplayer = false

    var playerPlayButtonType by rememberPreference(
        playerPlayButtonTypeKey,
        PlayerPlayButtonType.CircularRibbed
    )
    playerPlayButtonType = PlayerPlayButtonType.CircularRibbed

    var bottomgradient by rememberPreference(bottomgradientKey, false)
    bottomgradient = false

    var textoutline by rememberPreference(textoutlineKey, false)
    textoutline = false

    var lastPlayerPlayButtonType by rememberPreference(
        lastPlayerPlayButtonTypeKey,
        PlayerPlayButtonType.Rectangular
    )
    lastPlayerPlayButtonType = PlayerPlayButtonType.Rectangular

    var disablePlayerHorizontalSwipe by rememberPreference(disablePlayerHorizontalSwipeKey, false)
    disablePlayerHorizontalSwipe = false

    var playerSwipeSensitivity by rememberPreference(playerSwipeSensitivityKey, PlayerSwipeSensitivity.Medium)
    playerSwipeSensitivity = PlayerSwipeSensitivity.Medium

    var disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    disableScrollingText = false

    var showLikeButtonBackgroundPlayer by rememberPreference(
        showLikeButtonBackgroundPlayerKey,
        true
    )
    showLikeButtonBackgroundPlayer = true

    var showDownloadButtonBackgroundPlayer by rememberPreference(
        showDownloadButtonBackgroundPlayerKey,
        true
    )
    showDownloadButtonBackgroundPlayer = true

    var visualizerEnabled by rememberPreference(visualizerEnabledKey, false)
    visualizerEnabled = false

    var visualizerWhiteColorOption by rememberPreference(
        visualizerWhiteColorOptionKey,
        enums.VisualizerWhiteColorOption.White
    )
    visualizerWhiteColorOption = enums.VisualizerWhiteColorOption.White

    var visualizerCustomColor by rememberPreference(
        visualizerCustomColorKey,
        android.graphics.Color.WHITE
    )
    visualizerCustomColor = android.graphics.Color.WHITE

    var playerVisualizerType by rememberPreference(
        playerVisualizerTypeKey,
        PlayerVisualizerType.Disabled
    )
    playerVisualizerType = PlayerVisualizerType.Disabled

    var visualizerLineThickness by rememberPreference(
        visualizerLineThicknessKey,
        6f
    )
    visualizerLineThickness = 6f

    var playerTimelineType by rememberPreference(playerTimelineTypeKey, PlayerTimelineType.Wavy)
    playerTimelineType = PlayerTimelineType.Wavy

    var thumbnailSizeDp by rememberPreference(thumbnailSizeDpKey, 90f)
    thumbnailSizeDp = 90f

    var thumbnailSizeLDp by rememberPreference(thumbnailSizeLDpKey, 0f)
    thumbnailSizeLDp = 0f

    var playerTimelineSize by rememberPreference(
        playerTimelineSizeKey,
        PlayerTimelineSize.Biggest
    )
    playerTimelineSize = PlayerTimelineSize.Biggest

    var effectRotationEnabled by rememberPreference(effectRotationKey, false)
    effectRotationEnabled = false

    var thumbnailTapEnabled by rememberPreference(thumbnailTapEnabledKey, true)
    thumbnailTapEnabled = true

    var showButtonPlayerAddToPlaylist by rememberPreference(showButtonPlayerAddToPlaylistKey, true)
    showButtonPlayerAddToPlaylist = true

    var showButtonPlayerArrow by rememberPreference(showButtonPlayerArrowKey, true)
    showButtonPlayerArrow = true

    var showButtonPlayerDownload by rememberPreference(showButtonPlayerDownloadKey, true)
    showButtonPlayerDownload = true

    var showButtonPlayerLoop by rememberPreference(showButtonPlayerLoopKey, false)
    showButtonPlayerLoop = false

    var showButtonPlayerLyrics by rememberPreference(showButtonPlayerLyricsKey, true)
    showButtonPlayerLyrics = true

    var expandedplayertoggle by rememberPreference(expandedplayertoggleKey, false)
    expandedplayertoggle = false

    var showButtonPlayerShuffle by rememberPreference(showButtonPlayerShuffleKey, true)
    showButtonPlayerShuffle = true

    var showButtonPlayerSleepTimer by rememberPreference(showButtonPlayerSleepTimerKey, false)
    showButtonPlayerSleepTimer = false

    var showButtonPlayerMenu by rememberPreference(showButtonPlayerMenuKey, false)
    showButtonPlayerMenu = false

    var showButtonPlayerStartradio by rememberPreference(showButtonPlayerStartRadioKey, false)
    showButtonPlayerStartradio = false

    var showButtonPlayerSystemEqualizer by rememberPreference(
        showButtonPlayerSystemEqualizerKey,
        false
    )
    showButtonPlayerSystemEqualizer = false

    var showButtonPlayerDiscover by rememberPreference(showButtonPlayerDiscoverKey, false)
    showButtonPlayerDiscover = false

    var showButtonPlayerVideo by rememberPreference(showButtonPlayerVideoKey, true)
    showButtonPlayerVideo = true

    var showTotalTimeQueue by rememberPreference(showTotalTimeQueueKey, true)
    showTotalTimeQueue = true

    var backgroundProgress by rememberPreference(
        backgroundProgressKey,
        BackgroundProgress.MiniPlayer
    )
    backgroundProgress = BackgroundProgress.MiniPlayer

    var showNextSongsInPlayer by rememberPreference(showNextSongsInPlayerKey, false)
    showNextSongsInPlayer = false

    var showRemainingSongTime by rememberPreference(showRemainingSongTimeKey, true)
    showRemainingSongTime = true

    var showSkipTimeButtons by rememberPreference(showSkipTimeButtonsKey, true)
    showSkipTimeButtons = true

    var clickLyricsText by rememberPreference(clickOnLyricsTextKey, true)
    clickLyricsText = true

    var lyricsColor by rememberPreference(lyricsColorKey, LyricsColor.White)
    lyricsColor = LyricsColor.White

    var lyricsCustomColor by rememberPreference(lyricsCustomColorKey, android.graphics.Color.WHITE)
    lyricsCustomColor = android.graphics.Color.WHITE

    var showBackgroundLyrics by rememberPreference(showBackgroundLyricsKey, false)
    showBackgroundLyrics = false

    var thumbnailRoundnessDp by rememberPreference(thumbnailRoundnessDpKey, 12f)
    thumbnailRoundnessDp = 12f

    var artistThumbnailRoundnessDp by rememberPreference(artistThumbnailRoundnessDpKey, 48f)
    artistThumbnailRoundnessDp = 48f

    var uiRoundnessDp by rememberPreference(uiRoundnessDpKey, 25f)
    uiRoundnessDp = 25f

    var miniPlayerType by rememberPreference(
        miniPlayerTypeKey,
        MiniPlayerType.Essential
    )
    miniPlayerType = MiniPlayerType.Essential

    var playerBackgroundColors by rememberPreference(
        playerBackgroundColorsKey,
        PlayerBackgroundColors.AnimatedGradient
    )
    playerBackgroundColors = PlayerBackgroundColors.AnimatedGradient

    var showTopActionsBar by rememberPreference(showTopActionsBarKey, true)
    showTopActionsBar = true

    var playerControlsType by rememberPreference(playerControlsTypeKey, PlayerControlsType.Essential)
    playerControlsType = PlayerControlsType.Essential

    var playerControlsColors by rememberPreference(playerControlsColorsKey, PlayerControlsColors.Monochrome)
    playerControlsColors = PlayerControlsColors.Monochrome

    var playerInfoType by rememberPreference(playerInfoTypeKey, PlayerInfoType.Modern)
    playerInfoType = PlayerInfoType.Modern

    var transparentBackgroundActionBarPlayer by rememberPreference(
        transparentBackgroundPlayerActionBarKey,
        true
    )
    transparentBackgroundActionBarPlayer = true

    var iconLikeType by rememberPreference(iconLikeTypeKey, IconLikeType.Essential)
    iconLikeType = IconLikeType.Essential

    var playerSwapControlsWithTimeline by rememberPreference(
        playerSwapControlsWithTimelineKey,
        false
    )
    playerSwapControlsWithTimeline = false

    var playerEnableLyricsPopupMessage by rememberPreference(
        playerEnableLyricsPopupMessageKey,
        true
    )
    playerEnableLyricsPopupMessage = true

    var actionspacedevenly by rememberPreference(actionspacedevenlyKey, true)
    actionspacedevenly = true

    var thumbnailType by rememberPreference(thumbnailTypeKey, ThumbnailType.Modern)
    thumbnailType = ThumbnailType.Modern

    var showvisthumbnail by rememberPreference(showvisthumbnailKey, true)
    showvisthumbnail = true

    var blackBackgroundForVisThumbnail by rememberPreference(blackBackgroundForVisThumbnailKey, true)
    blackBackgroundForVisThumbnail = true

    var showVisualizerButtons by rememberPreference(showVisualizerButtonsKey, true)
    showVisualizerButtons = true

    var buttonzoomout by rememberPreference(buttonzoomoutKey, true)
    buttonzoomout = true

    var thumbnailpause by rememberPreference(thumbnailpauseKey, true)
    thumbnailpause = true

    var cropVideoThumbnails by rememberPreference(cropVideoThumbnailsKey, true)
    cropVideoThumbnails = true

    var showsongs by rememberPreference(showsongsKey, SongsNumber.`2`)
    showsongs = SongsNumber.`2`

    var showalbumcover by rememberPreference(showalbumcoverKey, true)
    showalbumcover = true

    var prevNextSongs by rememberPreference(prevNextSongsKey, PrevNextSongs.twosongs)
    prevNextSongs = PrevNextSongs.twosongs

    var tapqueue by rememberPreference(tapqueueKey, true)
    tapqueue = true

    var swipeUpQueue by rememberPreference(swipeUpQueueKey, true)
    swipeUpQueue = true

    var statsfornerds by rememberPreference(statsfornerdsKey, false)
    statsfornerds = false

    var playerType by rememberPreference(playerTypeKey, PlayerType.Essential)
    playerType = PlayerType.Essential

    var queueType by rememberPreference(queueTypeKey, QueueType.Modern)
    queueType = QueueType.Modern

    var noblur by rememberPreference(noblurKey, true)
    noblur = true

    var fadingedge by rememberPreference(fadingedgeKey, false)
    fadingedge = false

    var carousel by rememberPreference(carouselKey, true)
    carousel = true

    var carouselSize by rememberPreference(carouselSizeKey, CarouselSize.Biggest)
    carouselSize = CarouselSize.Biggest

    var keepPlayerMinimized by rememberPreference(keepPlayerMinimizedKey,true)
    keepPlayerMinimized = true

    var playerInfoShowIcons by rememberPreference(playerInfoShowIconsKey, true)
    playerInfoShowIcons = true

    var queueDurationExpanded by rememberPreference(queueDurationExpandedKey, true)
    queueDurationExpanded = true

    var titleExpanded by rememberPreference(titleExpandedKey, true)
    titleExpanded = true

    var timelineExpanded by rememberPreference(timelineExpandedKey, true)
    timelineExpanded = true

    var controlsExpanded by rememberPreference(controlsExpandedKey, true)
    controlsExpanded = true

    var miniQueueExpanded by rememberPreference(miniQueueExpandedKey, true)
    miniQueueExpanded = true

    var statsExpanded by rememberPreference(statsExpandedKey, true)
    statsExpanded = true

    var actionExpanded by rememberPreference(actionExpandedKey, true)
    actionExpanded = true

    var showCoverThumbnailAnimation by rememberPreference(showCoverThumbnailAnimationKey, false)
    showCoverThumbnailAnimation = false

    var coverThumbnailAnimation by rememberPreference(coverThumbnailAnimationKey, ThumbnailCoverType.Vinyl)
    coverThumbnailAnimation = ThumbnailCoverType.Vinyl

    var showLyricsStateKey by rememberPreference(showLyricsStateKey, false)
    showLyricsStateKey = false

    var showVisualizerStateKey by rememberPreference(showVisualizerStateKey, false)
    showVisualizerStateKey = false

    var notificationPlayerFirstIcon by rememberPreference(notificationPlayerFirstIconKey, NotificationButtons.Download)
    notificationPlayerFirstIcon = NotificationButtons.Download

    var notificationPlayerSecondIcon by rememberPreference(notificationPlayerSecondIconKey, NotificationButtons.Favorites)
    notificationPlayerSecondIcon = NotificationButtons.Favorites

    var enableWallpaper by rememberPreference(enableWallpaperKey, false)
    enableWallpaper = false

    var wallpaperType by rememberPreference(wallpaperTypeKey, WallpaperType.Lockscreen)
    wallpaperType = WallpaperType.Lockscreen

    var topPadding by rememberPreference(topPaddingKey, true)
    topPadding = true

    var animatedGradient by rememberPreference(
        animatedGradientKey,
        AnimatedGradient.M3EMorphingCover
    )
    animatedGradient = AnimatedGradient.M3EMorphingCover

    var shakeSensitivityTheme by rememberPreference(shakeSensitivityThemeKey, ShakeSensitivityTheme.High)
    shakeSensitivityTheme = ShakeSensitivityTheme.High

    var albumCoverRotation by rememberPreference(albumCoverRotationKey, false)
    albumCoverRotation = false

    var blurStrength by rememberPreference(blurStrengthKey, 25f)
    blurStrength = 25f

    var thumbnailFadeEx  by rememberPreference(thumbnailFadeExKey, 5f)
    thumbnailFadeEx = 5f

    var thumbnailFade  by rememberPreference(thumbnailFadeKey, 5f)
    thumbnailFade = 5f

    var thumbnailSpacing  by rememberPreference(thumbnailSpacingKey, 0f)
    thumbnailSpacing = 0f

    var colorPaletteName by rememberPreference(colorPaletteNameKey, ColorPaletteName.Dynamic)
    colorPaletteName = ColorPaletteName.Dynamic

    var colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)
    colorPaletteMode = ColorPaletteMode.Dark

    var swipeAnimationNoThumbnail by rememberPreference(swipeAnimationsNoThumbnailKey, SwipeAnimationNoThumbnail.Sliding)
    swipeAnimationNoThumbnail = SwipeAnimationNoThumbnail.Sliding

    var isRotatingCoverEnabled by rememberPreference( rotatingAlbumCoverKey, false )
    isRotatingCoverEnabled = false

}

@ExperimentalAnimationApi
@UnstableApi
@Composable
fun AppearanceSettings(
    navController: NavController,
) {

    var isShowingThumbnailInLockscreen by rememberPreference(
        isShowingThumbnailInLockscreenKey,
        true
    )

    var showthumbnail by rememberPreference(showthumbnailKey, true)
    var transparentbar by rememberPreference(transparentbarKey, true)
    var blackgradient by rememberPreference(blackgradientKey, false)
    var showlyricsthumbnail by rememberPreference(showlyricsthumbnailKey, true)
    var expandedplayer by rememberPreference(expandedplayerKey, false)
    var playerPlayButtonType by rememberPreference(
        playerPlayButtonTypeKey,
        PlayerPlayButtonType.CircularRibbed
    )
    var bottomgradient by rememberPreference(bottomgradientKey, false)
    var textoutline by rememberPreference(textoutlineKey, false)

    var lastPlayerPlayButtonType by rememberPreference(
        lastPlayerPlayButtonTypeKey,
        PlayerPlayButtonType.Rectangular
    )
    var disablePlayerHorizontalSwipe by rememberPreference(disablePlayerHorizontalSwipeKey, false)
    var playerSwipeSensitivity by rememberPreference(playerSwipeSensitivityKey, PlayerSwipeSensitivity.Medium)

    var disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    var showLikeButtonBackgroundPlayer by rememberPreference(
        showLikeButtonBackgroundPlayerKey,
        true
    )
    var showDownloadButtonBackgroundPlayer by rememberPreference(
        showDownloadButtonBackgroundPlayerKey,
        true
    )
    var visualizerEnabled by rememberPreference(visualizerEnabledKey, false)
    var visualizerWhiteColorOption by rememberPreference(
        visualizerWhiteColorOptionKey,
        enums.VisualizerWhiteColorOption.White
    )
    var visualizerCustomColor by rememberPreference(
        visualizerCustomColorKey,
        android.graphics.Color.WHITE
    )
    var playerVisualizerType by rememberPreference(
        playerVisualizerTypeKey,
        PlayerVisualizerType.Disabled
    )
    var visualizerLineThickness by rememberPreference(
        visualizerLineThicknessKey,
        6f
    )

    var playerTimelineType by rememberPreference(playerTimelineTypeKey, PlayerTimelineType.Wavy)
    var thumbnailSizeDp by rememberPreference(thumbnailSizeDpKey, 90f)
    var thumbnailSizeLDp by rememberPreference(thumbnailSizeLDpKey, 0f)
    var playerTimelineSize by rememberPreference(
        playerTimelineSizeKey,
        PlayerTimelineSize.Biggest
    )
    //

    var effectRotationEnabled by rememberPreference(effectRotationKey, false)

    var thumbnailTapEnabled by rememberPreference(thumbnailTapEnabledKey, true)


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
    var showButtonPlayerSystemEqualizer by rememberPreference(
        showButtonPlayerSystemEqualizerKey,
        false
    )
    var showButtonPlayerDiscover by rememberPreference(showButtonPlayerDiscoverKey, false)
    var showButtonPlayerVideo by rememberPreference(showButtonPlayerVideoKey, true)

    val navigationBarPosition by rememberPreference(
        navigationBarPositionKey,
        NavigationBarPosition.BottomFloating
    )

    //var isGradientBackgroundEnabled by rememberPreference(isGradientBackgroundEnabledKey, false)
    var showTotalTimeQueue by rememberPreference(showTotalTimeQueueKey, true)
    var backgroundProgress by rememberPreference(
        backgroundProgressKey,
        BackgroundProgress.MiniPlayer
    )
    var showNextSongsInPlayer by rememberPreference(showNextSongsInPlayerKey, false)
    var showRemainingSongTime by rememberPreference(showRemainingSongTimeKey, true)
    var showSkipTimeButtons by rememberPreference(showSkipTimeButtonsKey, true)
    var clickLyricsText by rememberPreference(clickOnLyricsTextKey, true)
    var lyricsColor by rememberPreference(lyricsColorKey, LyricsColor.White)
    var lyricsCustomColor by rememberPreference(lyricsCustomColorKey, android.graphics.Color.WHITE)
    var showBackgroundLyrics by rememberPreference(showBackgroundLyricsKey, false)

    val search = Search()

    var thumbnailRoundnessDp by rememberPreference(thumbnailRoundnessDpKey, 12f)
    var artistThumbnailRoundnessDp by rememberPreference(artistThumbnailRoundnessDpKey, 48f)
    var uiRoundnessDp by rememberPreference(uiRoundnessDpKey, 25f)

    var miniPlayerType by rememberPreference(
        miniPlayerTypeKey,
        MiniPlayerType.Essential
    )
    var playerBackgroundColors by rememberPreference(
        playerBackgroundColorsKey,
        PlayerBackgroundColors.AnimatedGradient
    )

    var showTopActionsBar by rememberPreference(showTopActionsBarKey, true)
    var playerControlsType by rememberPreference(playerControlsTypeKey, PlayerControlsType.Essential)
    var playerControlsColors by rememberPreference(playerControlsColorsKey, PlayerControlsColors.Monochrome)
    var playerInfoType by rememberPreference(playerInfoTypeKey, PlayerInfoType.Modern)
    var transparentBackgroundActionBarPlayer by rememberPreference(
        transparentBackgroundPlayerActionBarKey,
        true
    )
    var iconLikeType by rememberPreference(iconLikeTypeKey, IconLikeType.Essential)
    var playerSwapControlsWithTimeline by rememberPreference(
        playerSwapControlsWithTimelineKey,
        false
    )
    var playerEnableLyricsPopupMessage by rememberPreference(
        playerEnableLyricsPopupMessageKey,
        true
    )
    var actionspacedevenly by rememberPreference(actionspacedevenlyKey, true)
    var thumbnailType by rememberPreference(thumbnailTypeKey, ThumbnailType.Modern)
    var showvisthumbnail by rememberPreference(showvisthumbnailKey, true)
    var blackBackgroundForVisThumbnail by rememberPreference(blackBackgroundForVisThumbnailKey, true)
    var showVisualizerButtons by rememberPreference(showVisualizerButtonsKey, true)
    var buttonzoomout by rememberPreference(buttonzoomoutKey, true)
    var thumbnailpause by rememberPreference(thumbnailpauseKey, true)
    var cropVideoThumbnails by rememberPreference(cropVideoThumbnailsKey, true)
    var showsongs by rememberPreference(showsongsKey, SongsNumber.`2`)
    var showalbumcover by rememberPreference(showalbumcoverKey, true)
    var prevNextSongs by rememberPreference(prevNextSongsKey, PrevNextSongs.twosongs)
    var tapqueue by rememberPreference(tapqueueKey, true)
    var swipeUpQueue by rememberPreference(swipeUpQueueKey, true)
    var statsfornerds by rememberPreference(statsfornerdsKey, false)

    var playerType by rememberPreference(playerTypeKey, PlayerType.Essential)
    var queueType by rememberPreference(queueTypeKey, QueueType.Modern)
    var noblur by rememberPreference(noblurKey, true)
    var fadingedge by rememberPreference(fadingedgeKey, false)
    var carousel by rememberPreference(carouselKey, true)
    var carouselSize by rememberPreference(carouselSizeKey, CarouselSize.Biggest)
    var keepPlayerMinimized by rememberPreference(keepPlayerMinimizedKey,true)
    var playerInfoShowIcons by rememberPreference(playerInfoShowIconsKey, true)
    var queueDurationExpanded by rememberPreference(queueDurationExpandedKey, true)
    var titleExpanded by rememberPreference(titleExpandedKey, true)
    var timelineExpanded by rememberPreference(timelineExpandedKey, true)
    var controlsExpanded by rememberPreference(controlsExpandedKey, true)
    var miniQueueExpanded by rememberPreference(miniQueueExpandedKey, true)
    var statsExpanded by rememberPreference(statsExpandedKey, true)
    var actionExpanded by rememberPreference(actionExpandedKey, true)
    var restartService by rememberSaveable { mutableStateOf(false) }
    var showCoverThumbnailAnimation by rememberPreference(showCoverThumbnailAnimationKey, false)
    var coverThumbnailAnimation by rememberPreference(coverThumbnailAnimationKey, ThumbnailCoverType.Vinyl)
    var showLyricsStateKey by rememberPreference(showLyricsStateKey, false)
    var showVisualizerStateKey by rememberPreference(showVisualizerStateKey, false)

    var notificationPlayerFirstIcon by rememberPreference(notificationPlayerFirstIconKey, NotificationButtons.Download)
    var notificationPlayerSecondIcon by rememberPreference(notificationPlayerSecondIconKey, NotificationButtons.Favorites)
    var enableWallpaper by rememberPreference(enableWallpaperKey, false)
    var wallpaperType by rememberPreference(wallpaperTypeKey, WallpaperType.Lockscreen)
    var topPadding by rememberPreference(topPaddingKey, true)
    var animatedGradient by rememberPreference(
        animatedGradientKey,
        AnimatedGradient.M3EMorphingCover
    )
    var shakeSensitivityTheme by rememberPreference(shakeSensitivityThemeKey, ShakeSensitivityTheme.High)
    var appearanceChooser by remember{ mutableStateOf(false)}
    var albumCoverRotation by rememberPreference(albumCoverRotationKey, false)

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            //.fillMaxSize()
            .fillMaxHeight()
            .fillMaxWidth(
                if (navigationBarPosition == NavigationBarPosition.Left ||
                    navigationBarPosition == NavigationBarPosition.Top ||
                    navigationBarPosition == NavigationBarPosition.Bottom
                ) 1f
                else Dimensions.contentWidthRightBar
            )
            .verticalScroll(rememberScrollState())
        /*
        .padding(
            LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues()
        )

         */
    ) {
        HeaderWithIcon(
            title = stringResource(R.string.player_appearance),
            iconId = R.drawable.color_palette,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {}
        )

        SettingsDescription(
            text = stringResource(R.string.appearance_settings_description),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        search.ToolBarButton()
        search.SearchBar( this )

        //SettingsEntryGroupText(stringResource(R.string.user_interface))

        if (playerBackgroundColors != PlayerBackgroundColors.BlurredCoverColor)
            showthumbnail = true
        if (!showthumbnail) {showlyricsthumbnail = false}
        if (playerType == PlayerType.Modern) {
            showlyricsthumbnail = false
            showvisthumbnail = false
            thumbnailpause = false
            //keepPlayerMinimized = false
        }
        var blurStrength by rememberPreference(blurStrengthKey, 25f)
        var thumbnailFadeEx  by rememberPreference(thumbnailFadeExKey, 5f)
        var thumbnailFade  by rememberPreference(thumbnailFadeKey, 5f)
        var thumbnailSpacing  by rememberPreference(thumbnailSpacingKey, 0f)
        var colorPaletteName by rememberPreference(colorPaletteNameKey, ColorPaletteName.Dynamic)
        var colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)
        var swipeAnimationNoThumbnail by rememberPreference(swipeAnimationsNoThumbnailKey, SwipeAnimationNoThumbnail.Sliding)

        val searchCtx_0_1 = search.inputValue.isBlank() || stringResource(R.string.player_presets_and_layout).contains(search.inputValue, true) || stringResource(R.string.appearancepresets).contains(search.inputValue, true) || stringResource(R.string.appearancepresetssecondary).contains(search.inputValue, true) || stringResource(R.string.playertype).contains(search.inputValue, true) || stringResource(R.string.queuetype).contains(search.inputValue, true) || stringResource(R.string.thumbnailtype).contains(search.inputValue, true) || stringResource(R.string.pinfo_type).contains(search.inputValue, true) || stringResource(R.string.pinfo_album_and_artist_name).contains(search.inputValue, true) || stringResource(R.string.pinfo_show_icons).contains(search.inputValue, true) || stringResource(R.string.pcontrols_type).contains(search.inputValue, true) || stringResource(R.string.icon_like_button).contains(search.inputValue, true)
        val searchCtx_0_2 = search.inputValue.isBlank() || stringResource(R.string.cover_and_thumbnail).contains(search.inputValue, true) || stringResource(R.string.show_thumbnail).contains(search.inputValue, true) || stringResource(R.string.swipe_Animation_No_Thumbnail).contains(search.inputValue, true) || stringResource(R.string.te_slide_vertical).contains(search.inputValue, true) || stringResource(R.string.te_fade).contains(search.inputValue, true) || stringResource(R.string.te_scale).contains(search.inputValue, true) || stringResource(R.string.carousel).contains(search.inputValue, true) || stringResource(R.string.vt_circular).contains(search.inputValue, true) || stringResource(R.string.fadingedge).contains(search.inputValue, true) || stringResource(R.string.carouselsize).contains(search.inputValue, true) || stringResource(R.string.thumbnailpause).contains(search.inputValue, true) || stringResource(R.string.show_lyrics_thumbnail).contains(search.inputValue, true) || stringResource(R.string.showvisthumbnail).contains(search.inputValue, true) || stringResource(R.string.show_cover_thumbnail_animation).contains(search.inputValue, true) || stringResource(R.string.cover_thumbnail_animation_type).contains(search.inputValue, true) || stringResource(R.string.player_thumbnail_size).contains(search.inputValue, true) || stringResource(R.string.thumbnail_roundness).contains(search.inputValue, true) || stringResource(R.string.noblur).contains(search.inputValue, true) || stringResource(R.string.statsfornerdsplayer).contains(search.inputValue, true)
        val searchCtx_0_3 = search.inputValue.isBlank() || stringResource(R.string.timeline_and_controls).contains(search.inputValue, true) || stringResource(R.string.timelinesize).contains(search.inputValue, true) || stringResource(R.string.miniplayertype).contains(search.inputValue, true) || stringResource(R.string.player_swap_controls_with_timeline).contains(search.inputValue, true) || stringResource(R.string.timeline).contains(search.inputValue, true) || stringResource(R.string.transparentbar).contains(search.inputValue, true) || stringResource(R.string.play_button).contains(search.inputValue, true) || stringResource(R.string.buttonzoomout).contains(search.inputValue, true)
        val searchCtx_0_4 = search.inputValue.isBlank() || stringResource(R.string.colors_and_background).contains(search.inputValue, true) || stringResource(R.string.background_colors).contains(search.inputValue, true) || stringResource(R.string.gradienttype).contains(search.inputValue, true) || stringResource(R.string.blackgradient).contains(search.inputValue, true) || stringResource(R.string.albumCoverRotation).contains(search.inputValue, true) || stringResource(R.string.bottomgradient).contains(search.inputValue, true) || stringResource(R.string.textoutline).contains(search.inputValue, true) || stringResource(R.string.show_total_time_of_queue).contains(search.inputValue, true) || stringResource(R.string.show_remaining_song_time).contains(search.inputValue, true) || stringResource(R.string.shake_sensitivity_theme).contains(search.inputValue, true)
        val searchCtx_0_5 = search.inputValue.isBlank() || stringResource(R.string.player_behavior_and_visuals).contains(search.inputValue, true) || stringResource(R.string.show_player_top_actions_bar).contains(search.inputValue, true) || stringResource(R.string.blankspace).contains(search.inputValue, true) || stringResource(R.string.show_next_songs_in_player).contains(search.inputValue, true) || stringResource(R.string.showtwosongs).contains(search.inputValue, true) || stringResource(R.string.songs_number_to_show).contains(search.inputValue, true) || stringResource(R.string.showalbumcover).contains(search.inputValue, true) || stringResource(R.string.disable_scrolling_text).contains(search.inputValue, true) || stringResource(R.string.scrolling_text_is_used_for_long_texts).contains(search.inputValue, true) || stringResource(R.string.player_rotating_buttons).contains(search.inputValue, true) || stringResource(R.string.player_enable_rotation_buttons).contains(search.inputValue, true) || stringResource(R.string.toggle_lyrics).contains(search.inputValue, true) || stringResource(R.string.by_tapping_on_the_thumbnail).contains(search.inputValue, true) || stringResource(R.string.click_lyrics_text).contains(search.inputValue, true) || stringResource(R.string.save_lyrics_state).contains(search.inputValue, true) || stringResource(R.string.save_lyrics_state_description).contains(search.inputValue, true) || stringResource(R.string.show_background_in_lyrics).contains(search.inputValue, true) || stringResource(R.string.player_enable_lyrics_popup_message).contains(search.inputValue, true) || stringResource(R.string.background_progress_bar).contains(search.inputValue, true) || stringResource(R.string.visualizer).contains(search.inputValue, true) || stringResource(R.string.save_visualizer_state).contains(search.inputValue, true) || stringResource(R.string.save_visualizer_state_description).contains(search.inputValue, true) || stringResource(R.string.require_mic_permission).contains(search.inputValue, true)
        PlayerActionBarSettingsDialog.Render()
        MiniPlayerButtonsSettingsDialog.Render()
        Column {
        AnimatedVisibility(
            visible = searchCtx_0_1,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f)
        ) {
            SettingsSectionCard(
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
                ///////ACTION BAR BUTTONS////////////////
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
                ///////////////////////////
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
                ///////ACTION BAR BUTTONS////////////////
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
                ///////////////////////////
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
                ///////ACTION BAR BUTTONS////////////////
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
                ///////////////////////////
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
                ///////ACTION BAR BUTTONS////////////////
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
                ///////////////////////////
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
                ///////ACTION BAR BUTTONS////////////////
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
                ///////////////////////////
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
                ///////ACTION BAR BUTTONS////////////////
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
                ///////////////////////////
                appearanceChooser = false
            }
            )
        }

        if (!isLandscape) {
            if (search.inputValue.isBlank() || stringResource(R.string.appearancepresets).contains(search.inputValue, true) || (stringResource(R.string.appearancepresetssecondary)).contains(search.inputValue, true)) {
                OtherSettingsEntry(
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
                    modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                )
            }

        if (search.inputValue.isBlank() || stringResource(R.string.pinfo_type).contains(
                search.inputValue,
                true
            )
        ) {
            if (search.inputValue.isBlank() || stringResource(R.string.pinfo_type).contains(search.inputValue, true)) {
                OtherEnumValueSelectorSettingsEntry(
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
                                modifier = Modifier
                                    .padding(start = 25.dp)
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
        } // close prev AnimatedVisibility
        AnimatedVisibility(
            visible = searchCtx_0_2,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f)
        ) {
    SettingsSectionCard(
        title = stringResource(R.string.cover_and_thumbnail),
        icon = R.drawable.album,
        content = {
        if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) {
            if (search.inputValue.isBlank() || stringResource(R.string.show_thumbnail).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.show_thumbnail).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                    icon = R.drawable.image,
                        title = stringResource(R.string.show_thumbnail),
                        text = "",
                        isChecked = showthumbnail,
                        onCheckedChange = {showthumbnail = it},
                    )
                }
        }
        AnimatedVisibility(visible = !showthumbnail && playerType == PlayerType.Modern && !isLandscape) {
            if (search.inputValue.isBlank() || stringResource(R.string.swipe_Animation_No_Thumbnail).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.swipe_Animation_No_Thumbnail).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                    icon = R.drawable.gesture,
                        title = stringResource(R.string.swipe_Animation_No_Thumbnail),
                        selectedValue = swipeAnimationNoThumbnail,
                        onValueSelected = { swipeAnimationNoThumbnail = it },
                        valueText = {
                            when (it) {
                                SwipeAnimationNoThumbnail.Sliding -> stringResource(R.string.te_slide_vertical)
                                SwipeAnimationNoThumbnail.Fade -> stringResource(R.string.te_fade)
                                SwipeAnimationNoThumbnail.Scale -> stringResource(R.string.te_scale)
                                SwipeAnimationNoThumbnail.Carousel -> stringResource(R.string.carousel)
                                SwipeAnimationNoThumbnail.Circle -> stringResource(R.string.vt_circular)
                            }
                        },
                        modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                    )
                }
        }
        AnimatedVisibility(visible = showthumbnail) {
            Column {
                if (playerType == PlayerType.Modern) {
                    if (search.inputValue.isBlank() || stringResource(R.string.fadingedge).contains(
                            search.inputValue,
                            true
                        )
                    )
                        if (search.inputValue.isBlank() || stringResource(R.string.fadingedge).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                    icon = R.drawable.drop_blur,
                                title = stringResource(R.string.fadingedge),
                                text = "",
                                isChecked = fadingedge,
                                onCheckedChange = { fadingedge = it },
                                modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                            )
                        }
                }

                if (playerType == PlayerType.Modern && !isLandscape && (expandedplayertoggle || expandedplayer)) {
                    if (search.inputValue.isBlank() || stringResource(R.string.carousel).contains(
                            search.inputValue,
                            true
                        )
                    )
                        if (search.inputValue.isBlank() || stringResource(R.string.carousel).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                    icon = R.drawable.images_sharp,
                                title = stringResource(R.string.carousel),
                                text = "",
                                isChecked = carousel,
                                onCheckedChange = { carousel = it },
                                modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                            )
                        }

                    if (search.inputValue.isBlank() || stringResource(R.string.carouselsize).contains(
                            search.inputValue,
                            true
                        )
                    )
                        if (search.inputValue.isBlank() || stringResource(R.string.carouselsize).contains(search.inputValue, true)) {
                            OtherEnumValueSelectorSettingsEntry(
                    icon = R.drawable.resize,
                                title = stringResource(R.string.carouselsize),
                                selectedValue = carouselSize,
                                onValueSelected = { carouselSize = it },
                                valueText = { it.text },
                                modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                            )
                        }
                }
                if (playerType == PlayerType.Essential) {

                    if (search.inputValue.isBlank() || stringResource(R.string.thumbnailpause).contains(
                            search.inputValue,
                            true
                        )
                    )
                        if (search.inputValue.isBlank() || stringResource(R.string.thumbnailpause).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                    icon = R.drawable.pause,
                                title = stringResource(R.string.thumbnailpause),
                                text = "",
                                isChecked = thumbnailpause,
                                onCheckedChange = { thumbnailpause = it },
                                modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                            )
                        }
                        
                    if (search.inputValue.isBlank() || stringResource(R.string.crop_video_thumbnails).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            icon = R.drawable.images_sharp,
                            title = stringResource(R.string.crop_video_thumbnails),
                            text = "",
                            isChecked = cropVideoThumbnails,
                            onCheckedChange = { cropVideoThumbnails = it },
                            modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                        )
                    }


                }

                if (search.inputValue.isBlank() || stringResource(R.string.show_cover_thumbnail_animation).contains(
                        search.inputValue,
                        true
                    )
                ) {
                    if (search.inputValue.isBlank() || stringResource(R.string.show_cover_thumbnail_animation).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                    icon = R.drawable.images_sharp,
                            title = stringResource(R.string.show_cover_thumbnail_animation),
                            text = "",
                            isChecked = showCoverThumbnailAnimation,
                            onCheckedChange = { showCoverThumbnailAnimation = it },
                            modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                        )
                    }
                    AnimatedVisibility(visible = showCoverThumbnailAnimation) {
                        Column {
                            if (search.inputValue.isBlank() || stringResource(R.string.cover_thumbnail_animation_type).contains(search.inputValue, true)) {
                                OtherEnumValueSelectorSettingsEntry(
                    icon = R.drawable.images_sharp,
                                    title = stringResource(R.string.cover_thumbnail_animation_type),
                                    selectedValue = coverThumbnailAnimation,
                                    onValueSelected = { coverThumbnailAnimation = it },
                                    valueText = { it.text },
                                    modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 50.dp else 25.dp)
                                )
                            }
                        }
                    }
                }



                if (isLandscape) {
                    if (search.inputValue.isBlank() || stringResource(R.string.player_thumbnail_size).contains(
                            search.inputValue,
                            true
                        )
                    )
                        if (search.inputValue.isBlank() || stringResource(R.string.player_thumbnail_size).contains(search.inputValue, true)) {
                            Column(modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)) {
                                SliderSettingsEntry(
                                    icon = R.drawable.resize,
                                    title = stringResource(R.string.player_thumbnail_size),
                                    text = "",
                                    state = thumbnailSizeLDp,
                                    range = 0f..100f,
                                    stepSize = 0f,
                                    defaultValue = 100f,
                                    drawValuePoints = true,
                                    isIntegerOnly = true,
                                    onSlide = { thumbnailSizeLDp = kotlin.math.round(it) },
                                    toDisplay = { it.toInt().toString() },
                                    trailingContent = @Composable { Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(36.dp).border(width = 1.dp, color = colorPalette().accent, shape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f))), contentAlignment = Alignment.Center) { Box(modifier = Modifier.padding(((100f - thumbnailSizeLDp) * 0.15f).dp).fillMaxSize().background(color = colorPalette().accent.copy(alpha = 0.5f), shape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f)))) } } }
                                )
                                
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                listOf(0f to "Min", 25f to "25%", 50f to "50%", 75f to "75%", 100f to "Max").forEach { (v, label) ->
                                    val isSelected = thumbnailSizeLDp.toInt() == v.toInt()
                                    TextButton(
                                        onClick = { thumbnailSizeLDp = v },
                                        shape = uiRoundnessShape(),
                                        colors = ButtonDefaults.textButtonColors(containerColor = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            color = if (isSelected) colorPalette().accent else colorPalette().text,
                                            fontWeight = if (isSelected) FontWeight.Bold else null
                                        )
                                    }
                                }
                            }
                            }
                        }
                } else {
                    if (search.inputValue.isBlank() || stringResource(R.string.player_thumbnail_size).contains(
                            search.inputValue,
                            true
                        )
                    )
                        if (search.inputValue.isBlank() || stringResource(R.string.player_thumbnail_size).contains(search.inputValue, true)) {
                            Column(modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)) {
                                SliderSettingsEntry(
                                    icon = R.drawable.resize,
                                    title = stringResource(R.string.player_thumbnail_size),
                                    text = "",
                                    state = thumbnailSizeDp,
                                    range = 0f..100f,
                                    stepSize = 0f,
                                    defaultValue = 90f,
                                    drawValuePoints = true,
                                    isIntegerOnly = true,
                                    onSlide = { thumbnailSizeDp = kotlin.math.round(it) },
                                    toDisplay = { it.toInt().toString() },
                                    trailingContent = @Composable { Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(36.dp).border(width = 1.dp, color = colorPalette().accent, shape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f))), contentAlignment = Alignment.Center) { Box(modifier = Modifier.padding(((100f - thumbnailSizeDp) * 0.15f).dp).fillMaxSize().background(color = colorPalette().accent.copy(alpha = 0.5f), shape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f)))) } } }
                                )

                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                listOf(0f to "Min", 25f to "25%", 50f to "50%", 75f to "75%", 100f to "Max").forEach { (v, label) ->
                                    val isSelected = thumbnailSizeDp.toInt() == v.toInt()
                                    TextButton(
                                        onClick = { thumbnailSizeDp = v },
                                        shape = uiRoundnessShape(),
                                        colors = ButtonDefaults.textButtonColors(containerColor = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            color = if (isSelected) colorPalette().accent else colorPalette().text,
                                            fontWeight = if (isSelected) FontWeight.Bold else null
                                        )
                                    }
                                }
                            }

                        }
                        }
                }

                if (search.inputValue.isBlank() || stringResource(R.string.thumbnail_roundness).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.thumbnail_roundness).contains(search.inputValue, true)) {
                        Column(modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)) {
                            SliderSettingsEntry(
                                icon = R.drawable.image,
                                title = stringResource(R.string.thumbnail_roundness),
                                text = "",
                                state = thumbnailRoundnessDp,
                                range = 0f..48f,
                                stepSize = 0f,
                                defaultValue = 12f,
                                drawValuePoints = true,
                                isIntegerOnly = true,
                                onSlide = { thumbnailRoundnessDp = kotlin.math.round(it) },
                                toDisplay = { it.toInt().toString() },
                                trailingContent = @Composable { Row(verticalAlignment = Alignment.CenterVertically) { Spacer(modifier = Modifier.size(36.dp).background(color = colorPalette().accent.copy(alpha = 0.5f), shape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f))).border(width = 1.dp, color = colorPalette().accent, shape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f)))) } }
                            )

                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf(0f to "0", 12f to "12", 24f to "24", 36f to "36", 48f to "Max").forEach { (v, label) ->
                                val isSelected = thumbnailRoundnessDp.toInt() == v.toInt()
                                TextButton(
                                    onClick = { thumbnailRoundnessDp = v },
                                    shape = uiRoundnessShape(),
                                    colors = ButtonDefaults.textButtonColors(containerColor = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        color = if (isSelected) colorPalette().accent else colorPalette().text,
                                        fontWeight = if (isSelected) FontWeight.Bold else null
                                    )
                                }
                            }
                        }
                        }
                    }

                if (search.inputValue.isBlank() || "artist ui roundness".contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || "artist ui roundness".contains(search.inputValue, true)) {
                        Column(modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)) {
                            SliderSettingsEntry(
                                icon = R.drawable.image,
                                title = stringResource(R.string.artist_roundness),
                                text = "",
                                state = artistThumbnailRoundnessDp,
                                range = 0f..48f,
                                stepSize = 0f,
                                defaultValue = 48f,
                                drawValuePoints = true,
                                isIntegerOnly = true,
                                onSlide = { artistThumbnailRoundnessDp = kotlin.math.round(it) },
                                toDisplay = { it.toInt().toString() },
                                trailingContent = @Composable { Row(verticalAlignment = Alignment.CenterVertically) { Spacer(modifier = Modifier.size(36.dp).background(color = colorPalette().accent.copy(alpha = 0.5f), shape = if (artistThumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(artistThumbnailRoundnessDp.dp, 0.25f))).border(width = 1.dp, color = colorPalette().accent, shape = if (artistThumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(artistThumbnailRoundnessDp.dp, 0.25f)))) } }
                            )

                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf(0f to "0", 12f to "12", 24f to "24", 36f to "36", 48f to "Max").forEach { (v, label) ->
                                val isSelected = artistThumbnailRoundnessDp.toInt() == v.toInt()
                                TextButton(
                                    onClick = { artistThumbnailRoundnessDp = v },
                                    shape = uiRoundnessShape(),
                                    colors = ButtonDefaults.textButtonColors(containerColor = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        color = if (isSelected) colorPalette().accent else colorPalette().text,
                                        fontWeight = if (isSelected) FontWeight.Bold else null
                                    )
                                }
                            }
                        }
                        }
                    }

                if (search.inputValue.isBlank() || stringResource(R.string.ui_roundness).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.ui_roundness).contains(search.inputValue, true)) {
                        SliderSettingsEntry(
                            icon = R.drawable.ui,
                            title = stringResource(R.string.ui_roundness),
                            text = "",
                            state = uiRoundnessDp,
                            range = 0f..40f,
                            stepSize = 0f,
                            defaultValue = 25f,
                            drawValuePoints = true,
                            isIntegerOnly = true,
                            onSlide = { uiRoundnessDp = kotlin.math.round(it) },
                            toDisplay = { it.toInt().toString() },
                            trailingContent = @Composable { Row(verticalAlignment = Alignment.CenterVertically) { Spacer(modifier = Modifier.size(36.dp).background(color = colorPalette().accent.copy(alpha = 0.5f), shape = RoundedCornerShape(BoundedCornerSize(uiRoundnessDp.dp, 0.4f))).border(width = 1.dp, color = colorPalette().accent, shape = RoundedCornerShape(BoundedCornerSize(uiRoundnessDp.dp, 0.4f)))) } },
                            modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf(0f to "0", 10f to "10", 20f to "20", 30f to "30", 40f to "Max").forEach { (v, label) ->
                                val isSelected = uiRoundnessDp.toInt() == v.toInt()
                                TextButton(
                                    onClick = { uiRoundnessDp = v },
                                    shape = uiRoundnessShape(),
                                    colors = ButtonDefaults.textButtonColors(containerColor = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        color = if (isSelected) colorPalette().accent else colorPalette().text,
                                        fontWeight = if (isSelected) FontWeight.Bold else null
                                    )
                                }
                            }
                        }
                    }
            }
        }

        if (!showthumbnail) {
            if (search.inputValue.isBlank() || stringResource(R.string.noblur).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.noblur).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                    icon = R.drawable.eye_off,
                        title = stringResource(R.string.noblur),
                        text = "",
                        isChecked = noblur,
                        onCheckedChange = { noblur = it }
                    )
                }


        }

        if (!(showthumbnail && playerType == PlayerType.Essential)){
            if (search.inputValue.isBlank() || stringResource(R.string.statsfornerdsplayer).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.statsfornerdsplayer).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                    icon = R.drawable.stats_chart,
                        title = stringResource(R.string.statsfornerdsplayer),
                        text = "",
                        isChecked = statsfornerds,
                        onCheckedChange = { statsfornerds = it }
                    )
                }
        }
        }
    )
        /* Removed Spacer */
        } // close prev AnimatedVisibility
        AnimatedVisibility(
            visible = searchCtx_0_3,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f)
        ) {
    SettingsSectionCard(
        title = stringResource(R.string.timeline_and_controls),
        icon = R.drawable.horizontal_straight_line,
        content = {
        if (search.inputValue.isBlank() || stringResource(R.string.timelinesize).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.timelinesize).contains(search.inputValue, true)) {
                OtherEnumValueSelectorSettingsEntry(
                    icon = R.drawable.resize,
                    title = stringResource(R.string.timelinesize),
                    selectedValue = playerTimelineSize,
                    onValueSelected = { playerTimelineSize = it },
                    valueText = { it.text }
                )
            }





        if (search.inputValue.isBlank() || stringResource(R.string.miniplayer_buttons_config).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.miniplayer_buttons_config).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    icon = R.drawable.ui,
                    title = stringResource(R.string.miniplayer_buttons_config),
                    text = stringResource(R.string.miniplayer_buttons_config_description),
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
                    icon = R.drawable.sync,
                    title = stringResource(R.string.player_swap_controls_with_timeline),
                    text = "",
                    isChecked = playerSwapControlsWithTimeline,
                    onCheckedChange = { playerSwapControlsWithTimeline = it }
                )
            }

        if (search.inputValue.isBlank() || stringResource(R.string.timeline).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.timeline).contains(search.inputValue, true)) {
                OtherEnumValueSelectorSettingsEntry(
                    icon = R.drawable.playing_indicator,
                    title = stringResource(R.string.timeline),
                    selectedValue = playerTimelineType,
                    onValueSelected = { playerTimelineType = it },
                    valueText = { stringResource(it.textId) }
                )
                AnimatedVisibility(visible = playerTimelineType == PlayerTimelineType.VisualizerBar) {
                    ImportantSettingsDescription(text = stringResource(R.string.require_mic_permission))
                }
            }

        if (search.inputValue.isBlank() || stringResource(R.string.transparentbar).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.transparentbar).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    icon = R.drawable.eye_off,
                    title = stringResource(R.string.transparentbar),
                    text = "",
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
                    icon = R.drawable.color_palette,
                    title = stringResource(R.string.player_controls_colors),
                    selectedValue = playerControlsColors,
                    onValueSelected = {
                        playerControlsColors = it
                    },
                    valueText = { it.text }
                )
            }


        if (search.inputValue.isBlank() || stringResource(R.string.play_button).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.play_button).contains(search.inputValue, true)) {
                OtherEnumValueSelectorSettingsEntry(
                    icon = R.drawable.play,
                    title = stringResource(R.string.play_button),
                    selectedValue = playerPlayButtonType,
                    onValueSelected = {
                        playerPlayButtonType = it
                        lastPlayerPlayButtonType = it
                    },
                    valueText = { it.text }
                )
            }

        if (search.inputValue.isBlank() || stringResource(R.string.buttonzoomout).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.buttonzoomout).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    icon = R.drawable.resize,
                    title = stringResource(R.string.buttonzoomout),
                    text = "",
                    isChecked = buttonzoomout,
                    onCheckedChange = { buttonzoomout = it }
                )
            }


        if (search.inputValue.isBlank() || stringResource(R.string.play_button).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.icon_like_button).contains(search.inputValue, true)) {
                OtherEnumValueSelectorSettingsEntry(
                    icon = R.drawable.play,
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
        } // close prev AnimatedVisibility
        AnimatedVisibility(
            visible = searchCtx_0_4,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f)
        ) {
    SettingsSectionCard(
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
        } // close prev AnimatedVisibility
        AnimatedVisibility(
            visible = searchCtx_0_5,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f)
        ) {
    SettingsSectionCard(
        title = stringResource(R.string.player_behavior_and_visuals),
        icon = R.drawable.equalizer,
        content = {
        if (!isLandscape) {
            if (search.inputValue.isBlank() || stringResource(R.string.show_player_top_actions_bar).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.show_player_top_actions_bar).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                    icon = R.drawable.enqueue,
                        title = stringResource(R.string.show_player_top_actions_bar),
                        text = "",
                        isChecked = showTopActionsBar,
                        onCheckedChange = { showTopActionsBar = it }
                    )
                }

            if (!showTopActionsBar) {
                if (search.inputValue.isBlank() || stringResource(R.string.blankspace).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.blankspace).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                    icon = R.drawable.space,
                            title = stringResource(R.string.blankspace),
                            text = "",
                            isChecked = topPadding,
                            onCheckedChange = { topPadding = it }
                        )
                    }
            }
        }

        if (search.inputValue.isBlank() || stringResource(R.string.show_next_songs_in_player).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.show_next_songs_in_player).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    icon = R.drawable.arrow_forward,
                    title = stringResource(R.string.show_next_songs_in_player),
                    text = "",
                    isChecked = showNextSongsInPlayer,
                    onCheckedChange = { showNextSongsInPlayer = it }
                )
            }
        AnimatedVisibility( visible = showNextSongsInPlayer) {
          Column {
              if (search.inputValue.isBlank() || stringResource(R.string.showtwosongs).contains(search.inputValue,true))
                  OtherEnumValueSelectorSettingsEntry(
                icon = R.drawable.information,
                      title = stringResource(R.string.songs_number_to_show),
                      selectedValue = showsongs,
                      onValueSelected = {
                          showsongs = it
                      },
                      valueText = { it.name },
                      modifier = Modifier
                          .padding(start = 25.dp)
                  )


            if (search.inputValue.isBlank() || stringResource(R.string.showalbumcover).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.showalbumcover).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                    icon = R.drawable.album,
                        title = stringResource(R.string.showalbumcover),
                        text = "",
                        isChecked = showalbumcover,
                        onCheckedChange = { showalbumcover = it },
                          modifier = Modifier.padding(start = 25.dp)
                      )
                }
          }
        }

        if (search.inputValue.isBlank() || stringResource(R.string.disable_scrolling_text).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.disable_scrolling_text).contains(search.inputValue, true) || (stringResource(R.string.scrolling_text_is_used_for_long_texts)).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    icon = R.drawable.text,
                    title = stringResource(R.string.disable_scrolling_text),
                    text = stringResource(R.string.scrolling_text_is_used_for_long_texts),
                    isChecked = disableScrollingText,
                    onCheckedChange = { disableScrollingText = it }
                )
            }

        if (search.inputValue.isBlank() || stringResource(if (playerType == PlayerType.Modern && !isLandscape) R.string.disable_horizontal_swipe else R.string.disable_vertical_swipe).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(if (playerType == PlayerType.Modern && !isLandscape) R.string.disable_vertical_swipe else R.string.disable_horizontal_swipe).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    icon = R.drawable.sync,
                    title = stringResource(if (playerType == PlayerType.Modern && !isLandscape) R.string.disable_vertical_swipe else R.string.disable_horizontal_swipe),
                    text = stringResource(if (playerType == PlayerType.Modern && !isLandscape) R.string.disable_vertical_swipe_secondary else R.string.disable_song_switching_via_swipe),
                    isChecked = disablePlayerHorizontalSwipe,
                    onCheckedChange = { disablePlayerHorizontalSwipe = it }
                )
            }

        AnimatedVisibility(visible = !disablePlayerHorizontalSwipe) {
            if (search.inputValue.isBlank() || stringResource(R.string.player_swipe_sensitivity).contains(search.inputValue, true)) {
                OtherEnumValueSelectorSettingsEntry(
                    icon = R.drawable.gesture,
                    title = stringResource(R.string.player_swipe_sensitivity),
                    selectedValue = playerSwipeSensitivity,
                    onValueSelected = { playerSwipeSensitivity = it },
                    valueText = { it.text },
                    modifier = Modifier.padding(start = 25.dp)
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
                    icon = R.drawable.refresh,
                    title = stringResource(R.string.player_rotating_buttons),
                    text = stringResource(R.string.player_enable_rotation_buttons),
                    isChecked = effectRotationEnabled,
                    onCheckedChange = { effectRotationEnabled = it }
                )
            }



        if (search.inputValue.isBlank() || stringResource(R.string.background_progress_bar).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.background_progress_bar).contains(search.inputValue, true)) {
                OtherEnumValueSelectorSettingsEntry(
                    icon = R.drawable.horizontal_straight_line,
                    title = stringResource(R.string.background_progress_bar),
                    selectedValue = backgroundProgress,
                    onValueSelected = {
                        backgroundProgress = it
                    },
                    valueText = { it.text },
                )
            }

        if (search.inputValue.isBlank() || stringResource(R.string.visualizer).contains(
                search.inputValue,
                true
            )
        ) {
            if (search.inputValue.isBlank() || stringResource(R.string.visualizer).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    icon = R.drawable.sound_effect,
                    title = stringResource(R.string.visualizer),
                    text = "",
                    isChecked = visualizerEnabled,
                    onCheckedChange = { visualizerEnabled = it }
                )
            }


            AnimatedVisibility(visible = visualizerEnabled) {
                Column {
                    if (search.inputValue.isBlank() || stringResource(R.string.save_visualizer_state).contains(search.inputValue, true) || (stringResource(R.string.save_visualizer_state_description)).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                    icon = R.drawable.bookmark,
                            title = stringResource(R.string.save_visualizer_state),
                            text = stringResource(R.string.save_visualizer_state_description),
                            isChecked = showVisualizerStateKey,
                            onCheckedChange = { showVisualizerStateKey = it },
                            modifier = Modifier.padding(start = 25.dp)
                        )
                    }

                    if (search.inputValue.isBlank() || stringResource(R.string.visualizer_white_color_option).contains(search.inputValue, true)) {
                        OtherEnumValueSelectorSettingsEntry(
                            title = stringResource(R.string.visualizer_white_color_option),
                            selectedValue = visualizerWhiteColorOption,
                            onValueSelected = { visualizerWhiteColorOption = it },
                            valueText = { stringResource(when(it) { enums.VisualizerWhiteColorOption.White -> R.string.color_white; enums.VisualizerWhiteColorOption.Theme -> R.string.bg_colors_background_from_theme; enums.VisualizerWhiteColorOption.Cover -> R.string.bg_colors_background_from_cover; enums.VisualizerWhiteColorOption.Custom -> R.string.color_custom }) },
                            modifier = Modifier.padding(start = 25.dp),
                            icon = R.drawable.color_palette
                        )
                    }

                    if (search.inputValue.isBlank() || stringResource(R.string.visualizer_line_thickness).contains(search.inputValue, true)) {
                        SliderSettingsEntry(
                            icon = R.drawable.settings,
                            title = stringResource(R.string.visualizer_line_thickness),
                            text = "",
                            state = visualizerLineThickness,
                            onSlide = { visualizerLineThickness = kotlin.math.round(it) },
                            range = 1f..10f,
                            stepSize = 0f,
                            defaultValue = 6f,
                            drawValuePoints = true,
                            isIntegerOnly = true,
                            toDisplay = { it.toInt().toString() },
                            modifier = Modifier.padding(start = 25.dp)
                        )
                    }

                    AnimatedVisibility(visible = visualizerWhiteColorOption == enums.VisualizerWhiteColorOption.Custom) {
                        var showColorPicker by remember { mutableStateOf(false) }
                        val customColorString = stringResource(R.string.color_custom)
                        if (search.inputValue.isBlank() || true) {
                            OtherSettingsEntry(
                                title = customColorString,
                                text = "",
                                icon = R.drawable.color_palette,
                                onClick = { showColorPicker = true },
                                modifier = Modifier.padding(start = 40.dp),
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
                        if (showColorPicker) {
                            DialogColorPicker(onDismiss = { showColorPicker = false }) {
                                visualizerCustomColor = it.toArgb()
                                showColorPicker = false
                                Toaster.n(R.string.info_color_s_applied, customColorString)
                            }
                        }
                    }
                    
                    if (search.inputValue.isBlank() || stringResource(R.string.showvisthumbnail).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            icon = R.drawable.equalizer,
                            title = stringResource(R.string.showvisthumbnail),
                            text = "",
                            isChecked = showvisthumbnail,
                            onCheckedChange = { showvisthumbnail = it },
                            modifier = Modifier.padding(start = 25.dp)
                        )
                    }
                    AnimatedVisibility(visible = showvisthumbnail) {
                        if (search.inputValue.isBlank() || stringResource(R.string.black_background_for_visualizer).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                                icon = R.drawable.images_sharp,
                                title = stringResource(R.string.black_background_for_visualizer),
                                text = "",
                                isChecked = blackBackgroundForVisThumbnail,
                                onCheckedChange = { blackBackgroundForVisThumbnail = it },
                                modifier = Modifier.padding(start = 45.dp)
                            )
                        }
                    }
                    if (search.inputValue.isBlank() || stringResource(R.string.show_visualizer_buttons).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            icon = R.drawable.menu,
                            title = stringResource(R.string.show_visualizer_buttons),
                            text = "",
                            isChecked = showVisualizerButtons,
                            onCheckedChange = { showVisualizerButtons = it },
                            modifier = Modifier.padding(start = 25.dp)
                        )
                    }
                }
            }
        }

        ImportantSettingsDescription(text = stringResource(R.string.require_mic_permission))

                }
            )
            }
        }
        /* Removed Spacer */
        val searchCtx_lyrics = search.inputValue.isBlank() || stringResource(R.string.toggle_lyrics).contains(search.inputValue, true) || stringResource(R.string.click_lyrics_text).contains(search.inputValue, true) || stringResource(R.string.lyricscolor).contains(search.inputValue, true) || stringResource(R.string.save_lyrics_state).contains(search.inputValue, true) || stringResource(R.string.show_background_in_lyrics).contains(search.inputValue, true) || stringResource(R.string.player_enable_lyrics_popup_message).contains(search.inputValue, true)
        AnimatedVisibility(
            visible = searchCtx_lyrics,
            enter = fadeIn(animationSpec = tween(700)) + scaleIn(animationSpec = tween(700), initialScale = 0.9f)
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.txt_lyrics),
                icon = R.drawable.song_lyrics,
                content = {
        if (search.inputValue.isBlank() || stringResource(R.string.show_lyrics_thumbnail).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.show_lyrics_thumbnail).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    icon = R.drawable.image,
                    title = stringResource(R.string.show_lyrics_thumbnail),
                    text = "",
                    isChecked = showlyricsthumbnail,
                    onCheckedChange = { showlyricsthumbnail = it }
                )
            }

        if (search.inputValue.isBlank() || stringResource(R.string.toggle_lyrics).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.toggle_lyrics).contains(search.inputValue, true) || (stringResource(R.string.by_tapping_on_the_thumbnail)).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    icon = R.drawable.song_lyrics,
                    title = stringResource(R.string.toggle_lyrics),
                    text = stringResource(R.string.by_tapping_on_the_thumbnail),
                    isChecked = thumbnailTapEnabled,
                    onCheckedChange = { thumbnailTapEnabled = it }
                )
            }

        if (search.inputValue.isBlank() || stringResource(R.string.click_lyrics_text).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.click_lyrics_text).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    icon = R.drawable.arrow_down,
                    title = stringResource(R.string.click_lyrics_text),
                    text = "",
                    isChecked = clickLyricsText,
                    onCheckedChange = { clickLyricsText = it }
                )
            }

            if (search.inputValue.isBlank() || stringResource(R.string.lyricscolor).contains(search.inputValue, true)) {
                OtherEnumValueSelectorSettingsEntry(
                    title = stringResource(R.string.lyricscolor),
                    selectedValue = lyricsColor,
                    onValueSelected = { lyricsColor = it },
                    valueText = { stringResource(when(it) { LyricsColor.White -> R.string.color_white; LyricsColor.Thememode -> R.string.bg_colors_background_from_theme; LyricsColor.Cover -> R.string.bg_colors_background_from_cover; LyricsColor.Custom -> R.string.color_custom }) },
                    icon = R.drawable.color_palette
                )
            }

            AnimatedVisibility(visible = lyricsColor == LyricsColor.Custom) {
                var showColorPicker by remember { mutableStateOf(false) }
                val customColorString = stringResource(R.string.color_custom)
                if (search.inputValue.isBlank() || true) {
                    OtherSettingsEntry(
                        title = customColorString,
                        text = "",
                        icon = R.drawable.color_palette,
                        onClick = { showColorPicker = true },
                        modifier = Modifier.padding(start = 25.dp),
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
                if (showColorPicker) {
                    DialogColorPicker(onDismiss = { showColorPicker = false }) {
                        lyricsCustomColor = it.toArgb()
                        showColorPicker = false
                        Toaster.n(R.string.info_color_s_applied, customColorString)
                    }
                }
            }

        if (search.inputValue.isBlank() || stringResource(R.string.save_lyrics_state).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.save_lyrics_state).contains(search.inputValue, true) || (stringResource(R.string.save_lyrics_state_description)).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    icon = R.drawable.bookmark,
                    title = stringResource(R.string.save_lyrics_state),
                    text = stringResource(R.string.save_lyrics_state_description),
                    isChecked = showLyricsStateKey,
                    onCheckedChange = { showLyricsStateKey = it }
                )
            }

        if (showlyricsthumbnail)
            if (search.inputValue.isBlank() || stringResource(R.string.show_background_in_lyrics).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.show_background_in_lyrics).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                    icon = R.drawable.image,
                        title = stringResource(R.string.show_background_in_lyrics),
                        text = "",
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
                    icon = R.drawable.alert,
                    title = stringResource(R.string.player_enable_lyrics_popup_message),
                    text = "",
                    isChecked = playerEnableLyricsPopupMessage,
                    onCheckedChange = { playerEnableLyricsPopupMessage = it }
                )
            }
                }
            )
        }

        /* Removed Spacer */
        val searchCtx_1 = search.inputValue.isBlank() || stringResource(R.string.player_action_bar).contains(search.inputValue, true) || stringResource(R.string.action_bar_transparent_background).contains(search.inputValue, true) || stringResource(R.string.actionspacedevenly).contains(search.inputValue, true) || stringResource(R.string.tapqueue).contains(search.inputValue, true) || stringResource(R.string.swipe_up_to_open_the_queue).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_video_button).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_discover_button).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_download_button).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_add_to_playlist_button).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_loop_button).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_shuffle_button).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_lyrics_button).contains(search.inputValue, true) || stringResource(R.string.expandedplayer).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_sleep_timer_button).contains(search.inputValue, true) || stringResource(R.string.show_equalizer).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_arrow_button_to_open_queue).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_start_radio_button).contains(search.inputValue, true) || stringResource(R.string.action_bar_show_menu_button).contains(search.inputValue, true)
        AnimatedVisibility(
            visible = searchCtx_1,
            enter = fadeIn(animationSpec = tween(700)) + scaleIn(
                animationSpec = tween(700),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.player_action_bar),
                icon = R.drawable.ellipsis_vertical,
                content = {

        if (search.inputValue.isBlank() || stringResource(R.string.action_bar_transparent_background).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.action_bar_transparent_background).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    icon = R.drawable.eye,
                    title = stringResource(R.string.action_bar_transparent_background),
                    text = "",
                    isChecked = transparentBackgroundActionBarPlayer,
                    onCheckedChange = { transparentBackgroundActionBarPlayer = it }
                )
            }

        if (search.inputValue.isBlank() || stringResource(R.string.actionspacedevenly).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.actionspacedevenly).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    icon = R.drawable.horizontal_bold_line,
                    title = stringResource(R.string.actionspacedevenly),
                    text = "",
                    isChecked = actionspacedevenly,
                    onCheckedChange = { actionspacedevenly = it }
                )
            }

        if (search.inputValue.isBlank() || stringResource(R.string.tapqueue).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.tapqueue).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    icon = R.drawable.gesture,
                    title = stringResource(R.string.tapqueue),
                    text = "",
                    isChecked = tapqueue,
                    onCheckedChange = { tapqueue = it }
                )
            }

        if (search.inputValue.isBlank() || stringResource(R.string.swipe_up_to_open_the_queue).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.swipe_up_to_open_the_queue).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    icon = R.drawable.gesture,
                    title = stringResource(R.string.swipe_up_to_open_the_queue),
                    text = "",
                    isChecked = swipeUpQueue,
                    onCheckedChange = { swipeUpQueue = it }
                )
            }

        if (search.inputValue.isBlank() || stringResource(R.string.player_action_bar_buttons).contains(
                search.inputValue,
                true
            )
        )
            if (search.inputValue.isBlank() || stringResource(R.string.player_action_bar_buttons).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    icon = R.drawable.ellipsis_vertical,
                    title = stringResource(R.string.player_action_bar_buttons),
                    text = stringResource(R.string.player_action_bar_buttons_description),
                    onClick = { PlayerActionBarSettingsDialog.showDialog() }
                )
            }

                }
            )
        }

        if (!showlyricsthumbnail) {
        /* Removed Spacer */
            val searchCtx_2 = search.inputValue.isBlank() || stringResource(R.string.full_screen_lyrics_components).contains(search.inputValue, true) || stringResource(R.string.show_total_time_of_queue).contains(search.inputValue, true) || stringResource(R.string.titleartist).contains(search.inputValue, true) || stringResource(R.string.timeline).contains(search.inputValue, true) || stringResource(R.string.controls).contains(search.inputValue, true) || stringResource(R.string.statsfornerds).contains(search.inputValue, true) || stringResource(R.string.actionbar).contains(search.inputValue, true) || stringResource(R.string.miniqueue).contains(search.inputValue, true)
            AnimatedVisibility(
                visible = searchCtx_2,
                enter = fadeIn(animationSpec = tween(800)) + scaleIn(
                    animationSpec = tween(800),
                    initialScale = 0.9f
                )
            ) {
                SettingsSectionCard(
                    title = stringResource(R.string.full_screen_lyrics_components),
                    icon = R.drawable.song_lyrics,
                    content = {

            if (showTotalTimeQueue) {
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
                            isChecked = queueDurationExpanded,
                            onCheckedChange = { queueDurationExpanded = it }
                        )
                    }
            }

            if (search.inputValue.isBlank() || stringResource(R.string.titleartist).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.titleartist).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                    icon = R.drawable.title_edit,
                        title = stringResource(R.string.titleartist),
                        text = "",
                        isChecked = titleExpanded,
                        onCheckedChange = { titleExpanded = it }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.timeline).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.timeline).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                    icon = R.drawable.ellipsis_horizontal,
                        title = stringResource(R.string.timeline),
                        text = "",
                        isChecked = timelineExpanded,
                        onCheckedChange = { timelineExpanded = it }
                    )
                }

            if (search.inputValue.isBlank() || stringResource(R.string.controls).contains(
                    search.inputValue,
                    true
                )
            )
                if (search.inputValue.isBlank() || stringResource(R.string.controls).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                    icon = R.drawable.ellipsis_horizontal,
                        title = stringResource(R.string.controls),
                        text = "",
                        isChecked = controlsExpanded,
                        onCheckedChange = { controlsExpanded = it }
                    )
                }


            if (statsfornerds && (!(showthumbnail && playerType == PlayerType.Essential))){
                if (search.inputValue.isBlank() || stringResource(R.string.statsfornerds).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.statsfornerds).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                    icon = R.drawable.stats_chart,
                            title = stringResource(R.string.statsfornerds),
                            text = "",
                            isChecked = statsExpanded,
                            onCheckedChange = { statsExpanded = it }
                        )
                    }
            }

            if (
                showButtonPlayerDownload ||
                showButtonPlayerAddToPlaylist ||
                showButtonPlayerLoop ||
                showButtonPlayerShuffle ||
                showButtonPlayerLyrics ||
                showButtonPlayerSleepTimer ||
                showButtonPlayerSystemEqualizer ||
                showButtonPlayerArrow ||
                showButtonPlayerMenu ||
                expandedplayertoggle ||
                showButtonPlayerDiscover ||
                showButtonPlayerVideo
            ){
                if (search.inputValue.isBlank() || stringResource(R.string.actionbar).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.actionbar).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                    icon = R.drawable.menu,
                            title = stringResource(R.string.actionbar),
                            text = "",
                            isChecked = actionExpanded,
                            onCheckedChange = {
                                actionExpanded = it
                            }
                        )
                    }
            }
            if (showNextSongsInPlayer && actionExpanded) {
                if (search.inputValue.isBlank() || stringResource(R.string.miniqueue).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.miniqueue).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                    icon = R.drawable.sort_vertical,
                            title = stringResource(R.string.miniqueue),
                            text = "",
                            isChecked = miniQueueExpanded,
                            onCheckedChange = { miniQueueExpanded = it }
                        )
                    }
            }



                    }
                )
            }
        }

        /* Removed Spacer */
        val searchCtx_3 = search.inputValue.isBlank() || stringResource(R.string.notification_player).contains(search.inputValue, true) || stringResource(R.string.notificationPlayerFirstIcon).contains(search.inputValue, true) || stringResource(R.string.notificationPlayerSecondIcon).contains(search.inputValue, true)
        AnimatedVisibility(
            visible = searchCtx_3,
            enter = fadeIn(animationSpec = tween(900)) + scaleIn(
                animationSpec = tween(900),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.notification_player),
                icon = R.drawable.notification1,
                content = {

        if (search.inputValue.isBlank() || stringResource(R.string.notification_player).contains(
                search.inputValue,
                true
            )
        ) {
            if (search.inputValue.isBlank() || stringResource(R.string.notificationPlayerFirstIcon).contains(search.inputValue, true)) {
                OtherEnumValueSelectorSettingsEntry(
                    icon = R.drawable.notification2,
                    title = stringResource(R.string.notificationPlayerFirstIcon),
                    selectedValue = notificationPlayerFirstIcon,
                    onValueSelected = {
                        notificationPlayerFirstIcon = it
                        restartService = true
                    },
                    valueText = { it.text },
                )
            }
            if (search.inputValue.isBlank() || stringResource(R.string.notificationPlayerSecondIcon).contains(search.inputValue, true)) {
                OtherEnumValueSelectorSettingsEntry(
                    icon = R.drawable.notification2,
                    title = stringResource(R.string.notificationPlayerSecondIcon),
                    selectedValue = notificationPlayerSecondIcon,
                    onValueSelected = {
                        notificationPlayerSecondIcon = it
                        restartService = true
                    },
                    valueText = { it.text },
                )
            }
            RestartPlayerService(restartService, onRestart = { restartService = false })
        }

                }
            )
        }

        if (isAtLeastAndroid7) {
        /* Removed Spacer */
            val searchCtx_4 = search.inputValue.isBlank() || stringResource(R.string.wallpaper).contains(search.inputValue, true) || stringResource(R.string.enable_wallpaper).contains(search.inputValue, true) || stringResource(R.string.set_cover_thumbnail_as_wallpaper).contains(search.inputValue, true)
            AnimatedVisibility(
                visible = searchCtx_4,
                enter = fadeIn(animationSpec = tween(1000)) + scaleIn(
                    animationSpec = tween(1000),
                    initialScale = 0.9f
                )
            ) {
                SettingsSectionCard(
                    title = stringResource(R.string.wallpaper),
                    icon = R.drawable.image,
                    content = {
                        if (search.inputValue.isBlank() || stringResource(R.string.enable_wallpaper).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                                icon = R.drawable.images_sharp,
                                title = stringResource(R.string.enable_wallpaper),
                                text = "",
                                isChecked = enableWallpaper,
                                onCheckedChange = { enableWallpaper = it }
                            )
                        }
                        AnimatedVisibility(visible = enableWallpaper) {
                            Column {
                                if (search.inputValue.isBlank() || stringResource(R.string.set_cover_thumbnail_as_wallpaper).contains(search.inputValue, true)) {
                                    OtherEnumValueSelectorSettingsEntry(
                                        icon = R.drawable.settings,
                                        title = stringResource(R.string.set_cover_thumbnail_as_wallpaper),
                                        selectedValue = wallpaperType,
                                        onValueSelected = {
                                            wallpaperType = it
                                            restartService = true
                                        },
                                        valueText = { it.text },
                                        modifier = Modifier.padding(start = 25.dp)
                                    )
                                }
                                RestartPlayerService(restartService, onRestart = { restartService = false })
                            }
                        }
                    }
                )
            }
        }

        /* Removed Spacer */
        val searchCtx_5 = search.inputValue.isBlank() || stringResource(R.string.settings_reset).contains(search.inputValue, true) || stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true)
                val searchCtx_Reset = search.inputValue.isBlank() || stringResource(R.string.settings_reset).contains(search.inputValue, true) || stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true)
        AnimatedVisibility(
            visible = searchCtx_Reset,
            enter = fadeIn(animationSpec = tween(1100)) + scaleIn(animationSpec = tween(1100), initialScale = 0.9f)
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.settings_reset),
                icon = R.drawable.refresh,
                content = {
                    var resetToDefault by remember { mutableStateOf(false) }
                    val context = LocalContext.current
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true) || stringResource(R.string.settings_reset).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.settings_reset),
                            text = stringResource(R.string.settings_restore_default_settings),
                            icon = R.drawable.refresh,
                            onClick = { 
                                resetToDefault = true
                                restartService = true
                                MiniPlayerButtonsSettingsDialog.reset(context)
                                PlayerActionBarSettingsDialog.reset(context)
                                Toaster.done()
                            }
                        )
                    }

                    if (resetToDefault) {
                        DefaultAppearanceSettings()
                        LaunchedEffect(Unit) {
                            resetToDefault = false
                        }
                    }
                }
            )
        }
        
        RestartPlayerService(restartService, onRestart = { restartService = false })

        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )
    }
}




