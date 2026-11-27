package app.n_zik.android.ui.screens.player

import app.n_zik.android.components.theme.LocalPaletteFadeTarget
import app.n_zik.android.components.theme.paletteDarkness
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState

import app.n_zik.android.ui.screens.player.components.LocalPlayerMusicVideo
import app.n_zik.android.ui.screens.player.components.PlayerMusicVideo
import app.n_zik.android.ui.screens.player.components.PlayerAudioVideoToggle
import app.n_zik.android.ui.screens.player.components.InlineMusicVideo
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.playback.services.canResolvePlayerVideo
import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.utils.actionExpandedKey

import android.util.LruCache

import app.n_zik.android.components.player.playerQueueUnderlayCovered
import app.n_zik.android.components.player.playerGlassSceneCaptureActive
import app.n_zik.android.components.glass.LocalGlassUnderlayCovered
import app.n_zik.android.components.player.LocalPlayerSheetGlassTransition
import app.n_zik.android.components.player.rememberPlayerSheetGlassTransition
import app.n_zik.android.components.player.rememberPlayerControlsGlassSource
import app.n_zik.android.components.player.playerGlassSourceCaptureActive
import app.n_zik.android.components.glass.LocalGlassOpticsTransitionProgress
import app.n_zik.android.components.glass.LocalGlassOpticsEnabled
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassMaterialSource
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.usesQueueToolbarGlass
import app.n_zik.android.components.settings.ui.ToolbarAction
import app.n_zik.android.components.settings.ui.ToolbarSurface
import app.n_zik.android.components.settings.ui.visibleActions
import app.n_zik.android.components.glass.GlassOverlayHost
import app.n_zik.android.components.glass.rememberOverlayTransition
import app.n_zik.android.components.glass.hasOverlayContent
import app.n_zik.android.components.glass.LocalGlassOverlayBackdrop
import app.n_zik.android.components.glass.glassPlayerSceneSource
import app.n_zik.android.components.glass.glassSceneOverlayBackdrop
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCombinedBackdrop
import app.n_zik.android.components.player.animatePlayerCarouselGeometry
import app.n_zik.android.components.player.animatePlayerCarouselDp
import app.n_zik.android.components.player.PlayerGlassBudgetLayout
import app.n_zik.android.components.player.playerQueueOpticsEnabled
import app.n_zik.android.components.player.rememberPlayerArtworkShadowOverflow
import app.n_zik.android.components.player.playerArtworkShadowOverflow
import app.n_zik.android.components.player.playerArtworkOverflowViewport
import app.n_zik.android.components.player.playerArtworkOverflowFade
import app.n_zik.android.components.player.rememberPlayerArtworkControlsBackdrop
import app.n_zik.android.components.player.playerPortraitUsesWeightedInformation
import app.n_zik.android.components.player.playerPortraitPrioritizesArtwork
import app.n_zik.android.components.player.PlayerPortraitThumbnailFrame
import app.n_zik.android.components.player.PlayerPortraitInformationLayout
import app.n_zik.android.components.player.playerPortraitInformationGap
import app.n_zik.android.components.player.playerThumbnailPadding
import app.n_zik.android.components.player.PlayerInformationGlassBlock
import app.n_zik.android.components.player.LocalPlayerTransportGeometry
import app.n_zik.android.components.player.PlayerTransportGeometry
import app.n_zik.android.components.player.playerTransportGeometryHost
import app.n_zik.android.components.player.canSkipStandalonePlayerCover
import app.n_zik.android.components.player.rememberGlassPlayerBackground

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.appRunningInBackground
import app.n_zik.android.ui.saveable.TolerantBoolStateSaver

import app.n_zik.android.utils.PlayerTrackLinks
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.playerTrackLinks

import app.n_zik.android.components.dialog.common.SleepTimerDialog
import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.components.player.slide.landscapeCarouselDrag
import app.n_zik.android.components.player.slide.flingBehaviorFor
import app.n_zik.android.components.player.slide.itemZIndexFor
import app.n_zik.android.components.player.slide.playerThumbnailCover
import app.n_zik.android.components.PLAYER_SHEET_HANDOVER_PROGRESS
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import app.n_zik.android.components.player.playerLandscapeArtworkColumnVisible
import app.n_zik.android.components.player.PlayerLandscapeColumns
import app.n_zik.android.components.player.PlayerLandscapeThumbnailFrame
import app.n_zik.android.components.player.playerLandscapeControlsMargin
import app.n_zik.android.components.player.PlayerCarouselCover
import app.n_zik.android.components.player.PlayerCarouselGeometry
import app.n_zik.android.components.player.CarouselButtonNavigation
import app.n_zik.android.components.player.LocalCarouselButtonNavigation
import app.n_zik.android.components.player.hasArtworkStackOverlap
import app.n_zik.android.components.player.PlayerCarouselCoverScaleStart
import app.n_zik.android.components.player.slide.CAROUSEL_SETTLE_DURATION_MS
import app.n_zik.android.components.player.PlayerArtworkPageKeys
import app.n_zik.android.components.player.playerCoverOverlayPolicy
import app.n_zik.android.components.player.playerCoverArtworkVisible
import app.n_zik.android.components.player.playerStandaloneVisualizerVisible
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import app.n_zik.android.components.player.rememberPlayerArtworkPagerState
import app.n_zik.android.components.player.shouldWaitForPlayerBackdrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import app.n_zik.android.components.player.isPlayerHorizontalSwipeEnabled
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.n_zik.android.components.glass.LocalSurfaceBackdropColor
import app.n_zik.android.components.glass.rememberNeutralForegroundForSurface
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.effectiveSurfaceColor
import app.n_zik.android.components.glass.glassFilmColor
import app.n_zik.android.components.theme.PlayerSystemBarAppearance
import app.n_zik.android.components.glass.LocalForegroundSourcePalette
import app.n_zik.android.components.glass.LocalNeutralForegroundTransition
import app.n_zik.android.components.glass.LocalPlayerFollowsPaletteInk
import app.n_zik.android.components.glass.LocalPlayerGlyphCorrection
import app.n_zik.android.components.glass.SurfaceForegroundContent
import app.n_zik.android.components.player.LocalPlayerArtworkUnderControls
import app.n_zik.android.components.player.LocalPlayerBackdropSampling
import app.n_zik.android.components.player.LocalPlayerContrastTarget
import app.n_zik.android.components.player.LocalPlayerPaletteTransitioning
import app.n_zik.android.components.player.rememberPlayerBackdropRegionColor
import app.n_zik.android.components.player.rememberPlayerBackdropSampling
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInWindow
import app.n_zik.android.components.player.PlayerFluidColors
import app.n_zik.android.components.player.rememberPlayerGradientFade
import app.n_zik.android.components.player.LocalPlayerArtworkPaint
import app.n_zik.android.components.player.LocalPlayerArtworkPaintTarget
import app.n_zik.android.components.theme.dynamicArtworkFallback
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.ui.styling.applyPitchBlack
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.components.player.PlayerArtworkPaint
import app.n_zik.android.components.glass.GlassArtworkTint
import app.n_zik.android.components.glass.LocalGlassArtworkTint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.themed.n_zik.screen.player.ActionBar
import app.n_zik.android.themed.n_zik.screen.player.emptyPlayerActionBarTopSlot
import app.n_zik.android.themed.n_zik.screen.player.playerActionBarActions
import app.n_zik.android.themed.n_zik.screen.player.playerActionBarBottomAudioVideoKey
import app.n_zik.android.themed.n_zik.screen.player.playerActionBarTopSlotsKey
import app.n_zik.android.themed.n_zik.screen.player.resolvePlayerActionBarTopSlots
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.LocalPendingMiniPlayerAction
import app.n_zik.android.LocalPlayerSheetState
import app.n_zik.android.enums.PendingMiniPlayerAction
import app.n_zik.android.cleanPrefix
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import app.n_zik.android.enums.AnimatedGradient
import app.n_zik.android.enums.BackgroundProgress
import app.n_zik.android.enums.CarouselSize
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.PlayerBackgroundColors
import app.n_zik.android.enums.PlayerType
import app.n_zik.android.enums.QueueLoopType
import app.n_zik.android.enums.QueueType
import app.n_zik.android.enums.SwipeAnimationNoThumbnail
import app.n_zik.android.enums.ThumbnailCoverType
import app.n_zik.android.enums.ThumbnailType
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.extensions.nextvisualizer.views.NextVisualizer
import app.n_zik.android.components.menu.player.PlayerMenu
import app.n_zik.android.components.menu.player.PlayerStatsMenuControl
import app.n_zik.android.components.dialog.common.ThumbnailOffsetDialog
import app.n_zik.android.components.player.rememberCoverSpin
import app.n_zik.android.components.player.rememberPlayerDecorationsActive
import app.n_zik.android.ui.styling.favoritesOverlay
import app.n_zik.android.utils.DisposableListener
import app.n_zik.android.utils.VerticalfadingEdge2
import app.n_zik.android.utils.VinylSizeKey
import app.n_zik.android.utils.albumCoverRotationKey
import app.n_zik.android.utils.m3eAlwaysShowShapesKey
import app.n_zik.android.utils.animatedGradientKey
import app.n_zik.android.utils.backgroundProgressKey
import app.n_zik.android.utils.blackgradientKey
import app.n_zik.android.utils.bottomgradientKey
import app.n_zik.android.utils.carouselKey
import app.n_zik.android.utils.carouselSizeKey
import app.n_zik.android.utils.clickOnLyricsTextKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.controlsExpandedKey
import app.n_zik.android.utils.coverThumbnailAnimationKey
import app.n_zik.android.utils.disablePlayerHorizontalSwipeKey
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.discoverKey
import app.n_zik.android.components.player.rememberPlayerQueueDuration
import app.n_zik.android.components.player.playerStandaloneLyricsVisible
import app.n_zik.android.utils.effectRotationKey
import app.n_zik.android.utils.expandedplayerKey
import app.n_zik.android.utils.fadingedgeKey
import app.n_zik.android.utils.isLandscape
import app.n_zik.android.utils.mediaItems
import app.n_zik.android.utils.noblurKey
import app.n_zik.android.utils.playAtIndex
import app.n_zik.android.utils.playNext
import app.n_zik.android.playback.utils.playNextIfAvailable
import app.n_zik.android.playback.utils.playPreviousIfAvailable
import app.n_zik.android.utils.playPrevious
import app.n_zik.android.utils.playbackStateState
import app.n_zik.android.utils.playerBackgroundColorsKey
import app.n_zik.android.components.player.rememberPlayerLandscapeArtworkSize
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.utils.positionAndDurationState
import app.n_zik.android.utils.queueDurationExpandedKey
import app.n_zik.android.utils.queueLoopTypeKey
import app.n_zik.android.utils.queueTypeKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.shouldBePlaying
import app.n_zik.android.utils.showButtonPlayerMenuKey
import app.n_zik.android.utils.showCoverThumbnailAnimationKey
import app.n_zik.android.utils.showTopActionsBarKey
import app.n_zik.android.utils.showlyricsthumbnailKey
import app.n_zik.android.utils.playerSwipeSensitivityKey
import app.n_zik.android.enums.PlayerSwipeSensitivity
import app.n_zik.android.utils.showTotalTimeQueueKey
import app.n_zik.android.utils.showthumbnailKey
import app.n_zik.android.utils.showvisthumbnailKey
import app.n_zik.android.utils.statsExpandedKey
import app.n_zik.android.utils.statsfornerdsKey
import app.n_zik.android.utils.swipeAnimationsNoThumbnailKey
import app.n_zik.android.utils.textoutlineKey
import app.n_zik.android.utils.thumbnailFadeExKey
import app.n_zik.android.utils.thumbnailFadeKey
import app.n_zik.android.utils.thumbnailSpacingKey
import app.n_zik.android.utils.thumbnailSpacingLKey
import app.n_zik.android.utils.thumbnailTapEnabledKey
import app.n_zik.android.utils.thumbnailTypeKey
import app.n_zik.android.utils.timelineExpandedKey
import app.n_zik.android.utils.titleExpandedKey
import app.n_zik.android.utils.topPaddingKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.components.player.PlayerCurrentCover
import app.n_zik.android.components.player.PlayerCoverFrame
import app.n_zik.android.components.player.PlayerCoverIdentity
import app.n_zik.android.components.player.rememberPlayerCoverIdentity
import app.n_zik.android.components.player.PlayerM3EOccurrences
import app.n_zik.android.components.player.m3ePlaybackIsActive
import app.n_zik.android.components.player.ReportFirstPresentedPlayerCover
import app.n_zik.android.components.player.ReportPresentedPlayerCover
import androidx.compose.ui.graphics.painter.Painter
import app.n_zik.android.components.glass.rememberGlassArtwork
import app.n_zik.android.components.player.rememberArtworkDisplayResult
import app.n_zik.android.components.player.M3ECoverColors
import app.n_zik.android.components.player.BlurAdjuster
import app.n_zik.android.components.player.m3eDarkenBy
import app.n_zik.android.components.player.m3eSaturate
import app.n_zik.android.core.utils.Toaster
import kotlin.Float.Companion.POSITIVE_INFINITY
import kotlin.math.absoluteValue
import app.n_zik.android.components.player.lyrics.LyricsScreen
import app.n_zik.android.utils.thumbnailSizeDpKey
import app.n_zik.android.LocalIsShowingVisualizer
import androidx.compose.ui.platform.LocalView
import app.n_zik.android.LocalIsShowingLyrics
import androidx.media3.common.C
import android.app.Activity
import android.view.Window
import android.content.ContextWrapper
import app.n_zik.android.core.sync.YouTubeSync


@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation", "RememberReturnType", "NewApi")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun Player(
    navController: NavController,
    artworkInMotion: MutableState<Boolean>? = null,
    onDismiss: () -> Unit,
) {
    // Essentails
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val menuState = LocalMenuState.current
    val pendingAction = LocalPendingMiniPlayerAction.current
    val preview = LocalThemePreview.current
    val binder = LocalPlayerServiceBinder.current
    val player = preview?.player ?: binder?.player ?: return
    val playerSheetState = LocalPlayerSheetState.current
    // Settings
    val disablePlayerHorizontalSwipePref by rememberPreference(disablePlayerHorizontalSwipeKey, false)
    // Also off until the sheet is completely open: a horizontal drag on a half-open player (the
    // finger stopped in the middle of the opening) must not skip songs.

    // Held as derived State, because the pointer handlers below read it at drag time.
    val currentPlayerSheetState by rememberUpdatedState(playerSheetState)
    val disablePlayerHorizontalSwipe by remember {
        derivedStateOf {
            !isPlayerHorizontalSwipeEnabled(
                disabledByUser = disablePlayerHorizontalSwipePref,
                sheetExpanded = currentPlayerSheetState.isExpanded,
            )
        }
    }
    val showlyricsthumbnail by rememberPreference(showlyricsthumbnailKey, true)
    val effectRotationEnabled by rememberPreference(effectRotationKey, false)
    val thumbnailSizeDp by rememberPreference( thumbnailSizeDpKey, 90f )
    val thumbnailSizeLDp by rememberPlayerLandscapeArtworkSize()
    val showvisthumbnail by rememberPreference(showvisthumbnailKey, true)
    var thumbnailSpacing  by rememberPreference( thumbnailSpacingKey, 0f )
    var thumbnailSpacingL  by rememberPreference( thumbnailSpacingLKey, 0f )
    var thumbnailFade  by rememberPreference( thumbnailFadeKey, 5f )
    var thumbnailFadeEx  by rememberPreference( thumbnailFadeExKey, 5f )
    var imageCoverSize by rememberPreference( VinylSizeKey, 50f )
    val queueDurationExpanded by rememberPreference( queueDurationExpandedKey, true )
    val statsExpanded by rememberPreference( statsExpandedKey, true )
    var showthumbnail by rememberPreference( showthumbnailKey, true )
    val lyricsShownOnCover = showthumbnail && showlyricsthumbnail
    val showButtonPlayerMenu by rememberPreference( showButtonPlayerMenuKey, false )
    val showTotalTimeQueue by rememberPreference( showTotalTimeQueueKey, true )
    val backgroundProgress by rememberPreference( backgroundProgressKey, BackgroundProgress.MiniPlayer )
    var queueLoopState = rememberPreference( queueLoopTypeKey, defaultValue = QueueLoopType.Default )
    val playerType by rememberPreference( playerTypeKey, PlayerType.Essential )
    val queueType by rememberPreference( queueTypeKey, QueueType.Modern )
    val noblur by rememberPreference( noblurKey, true )
    val fadingedge by rememberPreference( fadingedgeKey, false )
    val colorPaletteMode by rememberPreference( colorPaletteModeKey, ColorPaletteMode.Dark )
    val playerBackgroundColors by rememberPreference( playerBackgroundColorsKey, PlayerBackgroundColors.AnimatedGradient )
    val animatedGradient by rememberPreference( animatedGradientKey, AnimatedGradient.M3EMorphingCover )
    val m3eAlwaysShowShapes by rememberPreference(m3eAlwaysShowShapesKey, false)
    val thumbnailTapEnabled by rememberPreference( thumbnailTapEnabledKey, true )
    val legacyShowTopActionsBar by rememberPreference(showTopActionsBarKey, true)
    val legacyTopActions = ToolbarSurface.PLAYER_TOP.visibleActions()
    val topSlotsPreference by rememberPreference(playerActionBarTopSlotsKey, "")
    val hasExplicitTopSlots = topSlotsPreference.isNotBlank()
    val topSlots = if (hasExplicitTopSlots) resolvePlayerActionBarTopSlots(topSlotsPreference)
        else legacyTopActions.map(ToolbarAction::id)
    val showTopActionsBar = if (hasExplicitTopSlots) {
        topSlots.any { it != emptyPlayerActionBarTopSlot }
    } else legacyShowTopActionsBar
    val activeTopSlots = showTopActionsBar && (hasExplicitTopSlots || !isLandscape)
    val movedActionIds = if (activeTopSlots && hasExplicitTopSlots) topSlots.toSet() else emptySet()
    val showMenuInTopSlots = activeTopSlots && ToolbarAction.MENU.id in topSlots &&
        (hasExplicitTopSlots || !showButtonPlayerMenu)
    val bottomAudioVideoEnabled by rememberPreference(playerActionBarBottomAudioVideoKey, false)
    val showBottomAudioVideo = bottomAudioVideoEnabled &&
        (!activeTopSlots || ToolbarAction.AUDIO_VIDEO.id !in topSlots)
    val showLegacyVideoFallback =
        !hasExplicitTopSlots && !showBottomAudioVideo && ToolbarAction.AUDIO_VIDEO in legacyTopActions
    val blackgradient by rememberPreference( blackgradientKey, false )
    val bottomgradient by rememberPreference( bottomgradientKey, false )
    val disableScrollingText by rememberPreference( disableScrollingTextKey, false )
    var discoverState = rememberPreference( discoverKey, false )
    val titleExpanded by rememberPreference( titleExpandedKey, true )
    val timelineExpanded by rememberPreference( timelineExpandedKey, true )
    val controlsExpanded by rememberPreference( controlsExpandedKey, true )
    val showCoverThumbnailAnimation by rememberPreference( showCoverThumbnailAnimationKey, false )
    var coverThumbnailAnimation by rememberPreference( coverThumbnailAnimationKey, ThumbnailCoverType.Vinyl )
    var albumCoverRotation by rememberPreference( albumCoverRotationKey, false )
    val textoutline by rememberPreference( textoutlineKey, false )
    val carousel by rememberPreference( carouselKey, true )
    val carouselSize by rememberPreference( carouselSizeKey, CarouselSize.Biggest )
    val clickLyricsText by rememberPreference( clickOnLyricsTextKey, true )
        val thumbnailType by rememberPreference( thumbnailTypeKey, ThumbnailType.Modern )
    var statsfornerds by rememberPreference(statsfornerdsKey, false)
    val topPadding by rememberPreference( topPaddingKey, true )
    var swipeAnimationNoThumbnail by rememberPreference( swipeAnimationsNoThumbnailKey, SwipeAnimationNoThumbnail.Sliding )
    val playerInLandscape = isLandscape
    val portraitExpansionPreference = rememberPreference(expandedplayerKey, false)
    val expandPlayerState = rememberPlayerExpansionState(playerInLandscape, portraitExpansionPreference)
    val expandedplayer by expandPlayerState
    // Animate the bottom gradient start so it does not flash on expand/back.
    val playerGradientStartY by animateFloatAsState(
        targetValue = if ( playerInLandscape ) 600f else if ( expandedplayer ) 1300f else 950f,
        label = "playerGradientStartY",
    )

    val playerUpdateTrigger by (binder?.playerUpdateTrigger?.collectAsStateWithLifecycle(0, context = NzikDispatchers.DATA) ?: remember { mutableStateOf(0) })

    val nullableMediaItemState = remember {
        mutableStateOf(player.currentMediaItem, neverEqualPolicy())
    }
    remember(playerUpdateTrigger) {
        nullableMediaItemState.value = player.currentMediaItem
        true
    }
    var nullableMediaItem by nullableMediaItemState

    var shouldBePlaying by remember(playerUpdateTrigger) {
        mutableStateOf(player.shouldBePlaying)
    }
    var isActuallyPlaying by remember(playerUpdateTrigger) {
        mutableStateOf(player.isPlaying)
    }

    // Issue #881 (gh-881): tolerant saver — this was the exact 15:23:55 CCE crash slot
    // (Player.kt:478, spec M5): a foreign payload now restores to the default, never throws.
    val rotateState = rememberSaveable(saver = TolerantBoolStateSaver("player.rotateState")) { mutableStateOf( false ) }
    var isRotated by rotateState
    val rotationAngle by animateFloatAsState(
        targetValue = if (isRotated) 360F else 0f,
        animationSpec = tween(durationMillis = 200), label = ""
    )

    // Issue #881 (gh-881): tolerant savers (spec M5).
    val showQueueState = rememberSaveable(saver = TolerantBoolStateSaver("player.showQueue")) { mutableStateOf( false ) }
    var showQueue by showQueueState


    val showVisualizerState = LocalIsShowingVisualizer.current
    var isShowingVisualizer by showVisualizerState
    val showLyricsState = LocalIsShowingLyrics.current
    var isShowingLyrics by showLyricsState
    
    val showSleepTimerState = rememberSaveable(saver = TolerantBoolStateSaver("player.showSleepTimer")) { mutableStateOf( false ) }
    var isShowingSleepTimerDialog by showSleepTimerState

    var suppressSavedRestoreOnNextOpen by remember { mutableStateOf(false) }

    LaunchedEffect(pendingAction.value) {
        when (pendingAction.value) {
            PendingMiniPlayerAction.Lyrics -> {
                suppressSavedRestoreOnNextOpen = true
                isShowingLyrics = !isShowingLyrics
                if (isShowingLyrics) {
                    isShowingVisualizer = false
                }
            }
            PendingMiniPlayerAction.Visualizer -> {
                suppressSavedRestoreOnNextOpen = true
                isShowingVisualizer = !isShowingVisualizer
                if (isShowingVisualizer) {
                    isShowingLyrics = false
                }
            }
            PendingMiniPlayerAction.Queue -> showQueue = true
            PendingMiniPlayerAction.SleepTimer -> isShowingSleepTimerDialog = true
            null -> {}
        }
        if (pendingAction.value != null) {
            pendingAction.value = null
        }
    }

    // User preferences (switch)
    val playerSwipeSensitivity by rememberPreference(playerSwipeSensitivityKey, PlayerSwipeSensitivity.Medium)
   var shouldRememberVisualizerState by rememberPreference("showVisualizerStateKey", false)
   var shouldRememberLyricsState by rememberPreference("showLyricsStateKey", false)

   // Saved states
   var savedVisualizerState by rememberPreference("saveVisualizerStateKey", false)
   var savedLyricsState by rememberPreference("saveLyricsStateKey", false)

    // The player content stays in composition across open/close (unified
    // sheet), so restore and reset are keyed on the sheet visibility

    // transition instead of the composition lifecycle, which only fired
    // once per session and left the saved keys stale after the first open.

    // 0.45f matches the draw/alpha hand-over threshold in CustomBottomSheet.
    val playerContentVisible by remember {
        derivedStateOf { playerSheetState.progress > PLAYER_SHEET_HANDOVER_PROGRESS }
    }

    // Restore saved state each time the player content becomes visible
    LaunchedEffect(playerContentVisible) {
        if (playerContentVisible && pendingAction.value == null) {
            if (suppressSavedRestoreOnNextOpen) {
                suppressSavedRestoreOnNextOpen = false
                return@LaunchedEffect
            }
            if (shouldRememberVisualizerState) {
                isShowingVisualizer = savedVisualizerState
            }
            if (shouldRememberLyricsState) {
                isShowingLyrics = savedLyricsState
            }
        }
    }

    // Reset to hidden when the player content becomes invisible and the
    // user does not ask for the state to be remembered
    LaunchedEffect(playerContentVisible) {
        if (!playerContentVisible) {
            suppressSavedRestoreOnNextOpen = false
            if (!shouldRememberLyricsState) {
                isShowingLyrics = false
            }
            if (!shouldRememberVisualizerState) {
                isShowingVisualizer = false
            }
        }
    }


   // Save only if not closing
   LaunchedEffect(isShowingVisualizer) {
       if (shouldRememberVisualizerState) {
           savedVisualizerState = isShowingVisualizer
       }
       if (isShowingVisualizer) {
           isShowingLyrics = false
       }
   }
   LaunchedEffect(isShowingLyrics) {
       if (shouldRememberLyricsState) {
           savedLyricsState = isShowingLyrics
       }
       if (isShowingLyrics) {
           isShowingVisualizer = false
       }
   }

    // Issue #881 (gh-881): tolerant saver (spec M5).
    var showThumbnailOffsetDialog by rememberSaveable(saver = TolerantBoolStateSaver("player.showThumbnailOffsetDialog")) {
        mutableStateOf(false)
    }

    DialogPresence(visible = showThumbnailOffsetDialog) {

        ThumbnailOffsetDialog(
            onDismiss = { showThumbnailOffsetDialog = false},
            spacingValue = { thumbnailSpacing = it },
            spacingValueL = { thumbnailSpacingL = it },
            fadeValue = { thumbnailFade = it },
            fadeValueEx = { thumbnailFadeEx = it },
            imageCoverSizeValue = { imageCoverSize = it }
        )
    }

    val mediaItemsState = remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    remember(playerUpdateTrigger) {
        mediaItemsState.value = if (player.currentTimeline.windowCount > 0) {
            val items = mutableListOf<MediaItem>()
            for (i in 0 until player.currentTimeline.windowCount) {
                items.add(player.getMediaItemAt(i))
            }
            items
        } else {
            emptyList()
        }
        true
    }
    var mediaItems by mediaItemsState
    var queueTimeline by remember(playerUpdateTrigger) {
        mutableStateOf(player.currentTimeline, referentialEqualityPolicy())
    }
    var playerError by remember(playerUpdateTrigger) {
        mutableStateOf<PlaybackException?>(player.playerError)
    }
    val m3eOccurrences = remember(player, playerUpdateTrigger) { PlayerM3EOccurrences() }

    player.DisposableListener(playerUpdateTrigger) {
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isActuallyPlaying = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                m3eOccurrences.advance()
                nullableMediaItem = mediaItem
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                shouldBePlaying = playerError == null && player.shouldBePlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playerError = player.playerError
                shouldBePlaying = playerError == null && player.shouldBePlaying
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                mediaItems = timeline.mediaItems
                queueTimeline = timeline
                val current = player.currentMediaItem
                if (current != null && current !== nullableMediaItem && current.mediaId == nullableMediaItem?.mediaId) {
                    nullableMediaItem = current
                }
            }
            override fun onPlayerError(playbackException: PlaybackException) {
                playerError = playbackException
            }
        }
    }

    val
            mediaItem = nullableMediaItem ?: return
            
    if (player.currentTimeline.windowCount == 0 || mediaItems.isEmpty()) return

    val pagerState = rememberPlayerArtworkPagerState(
        landscape = isLandscape,
        playingIndex = player.currentMediaItemIndex,
        pageCount = { mediaItems.size },
    )
    val pagerStateFS = rememberPagerState(pageCount = { mediaItems.size })
    val carouselPageKeys = remember(player) { PlayerArtworkPageKeys() }
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val isDraggedFS by pagerStateFS.interactionSource.collectIsDraggedAsState()

    val sleepTimerMillisLeft by (binder?.sleepTimerMillisLeft
        ?: flowOf(null))
        .collectAsStateWithLifecycle(initialValue = null, context = NzikDispatchers.DATA)

    // Issue #881 (gh-881), Phase 3.3: no visibility gate — the gated poll froze the bar
    // whenever gate and visible UI disagreed.

    // See positionAndDurationState KDoc in PlayerState.kt.
    val positionAndDurationState = player.positionAndDurationState(playerUpdateTrigger)
    val playbackState by player.playbackStateState(playerUpdateTrigger)
    val isBuffering = playbackState == Player.STATE_BUFFERING

    val durationState = remember(positionAndDurationState) {
        derivedStateOf { positionAndDurationState.value.second }
    }

    val timeRemainingState = remember(durationState, positionAndDurationState) {
        derivedStateOf { 
            if (durationState.value == C.TIME_UNSET) 0
            else durationState.value.toInt() - positionAndDurationState.value.first.toInt() 
        }
    }
    val timeRemaining by timeRemainingState

    val windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    val linkCache = remember(getActiveProfile(context)) { LruCache<String, PlayerTrackLinks>(48) }
    val currentLinks = mediaItem.playerTrackLinks(linkCache)

    var showCircularSlider by remember {
        mutableStateOf(false)
    }
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    DialogPresence(visible = isShowingSleepTimerDialog) {
        SleepTimerDialog(
            sleepTimerMillisLeft = sleepTimerMillisLeft,
            timeRemaining = timeRemaining.toLong(),
            onDismiss = { isShowingSleepTimerDialog = false },
            onCancelSleepTimer = { binder?.cancelSleepTimer() },
            onStartSleepTimer = { time -> binder?.startSleepTimer(time) }
        )
    }

    val color = colorPalette()
    val lightTheme = colorPaletteMode == ColorPaletteMode.Light ||
        (colorPaletteMode == ColorPaletteMode.System && !isSystemInDarkTheme())

    val isGradientBackgroundEnabled =
        playerBackgroundColors == PlayerBackgroundColors.ThemeColorGradient ||
                playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient ||
                playerBackgroundColors == PlayerBackgroundColors.AnimatedGradient

    val videoSession = LocalPlayerMusicVideo.current
    val offlineMode by OfflineMode.rememberEnabled()
    val toggleSession = if (preview != null) remember { PlayerMusicVideo() } else videoSession
    val showVideoMode = toggleSession != null &&
        (preview != null || !offlineMode && canResolvePlayerVideo(mediaItem))
    LaunchedEffect(videoSession?.selected) {
        if (videoSession?.selected == true) {
            isShowingLyrics = false
            isShowingVisualizer = false
        }
    }
    val showInlineVideo = showVideoMode && videoSession?.selected == true && videoSession.player != null &&
        !isShowingLyrics && !isShowingVisualizer
    val showVideoUnavailable = showVideoMode && videoSession?.failed == true
    LaunchedEffect(showVideoUnavailable) {
        if (showVideoUnavailable) Toaster.w(R.string.player_video_unavailable_search_hint)
    }
    val openManualVideoSearch: () -> Unit = {
        videoSession?.manualSearchQuery = listOfNotNull(
            mediaItem.mediaMetadata.title?.toString()?.let(::cleanPrefix),
            mediaItem.mediaMetadata.artist?.toString()?.let(::cleanPrefix),
        ).joinToString(" ")
    }
    val showLandscapeArtworkColumn = showInlineVideo || !isLandscape ||
        playerLandscapeArtworkColumnVisible(playerType, thumbnailType, expandedplayer, showthumbnail)
    val coverOverlayPolicy = playerCoverOverlayPolicy(
        lyricsOnCover = lyricsShownOnCover && showLandscapeArtworkColumn,
        visualizerOnCover = showvisthumbnail && showthumbnail && showLandscapeArtworkColumn,
    )
    val coverArtworkVisible = playerCoverArtworkVisible(
        showthumbnail, isShowingLyrics, coverOverlayPolicy.lyricsOnCover,
        isShowingVisualizer, coverOverlayPolicy.visualizerOnCover,
    )
    val fullCoverPresentation = !playerLandscapeArtworkColumnVisible(playerType, thumbnailType, expandedplayer, showthumbnail)
    val showFullScreenArtworkPager = playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor &&
        playerType == PlayerType.Modern && (!showthumbnail || albumCoverRotation)
    val artworkPreviewPage = when {
        showFullScreenArtworkPager -> pagerStateFS.currentPage
        playerType == PlayerType.Modern && showLandscapeArtworkColumn && coverArtworkVisible -> pagerState.currentPage
        else -> null
    }
    val currentSwipedMediaItem = artworkPreviewPage
        ?.takeIf { !fullCoverPresentation }
        ?.let(mediaItems::getOrNull) ?: mediaItem
    val playingArtwork = rememberGlassArtwork(player)
    fun coverUrl(item: MediaItem?): String? =
        playingArtwork?.takeIf { it.mediaId == item?.mediaId }?.uri ?: item?.mediaMetadata?.artworkUri?.toString()
    val visiblePlayerArtwork = playerContentVisible && !appRunningInBackground
    val fullscreenPagerOwnsArtwork = showFullScreenArtworkPager && !fullCoverPresentation
    val carouselOwnsArtwork = !showInlineVideo && playerType == PlayerType.Modern &&
        showLandscapeArtworkColumn && coverArtworkVisible &&
        (!isLandscape || !fullCoverPresentation) && !fullscreenPagerOwnsArtwork
    val expandedArtworkStack = carouselOwnsArtwork && carousel && expandedplayer
    val carouselButtonScope = rememberCoroutineScope()
    val carouselButtons = remember(player, pagerState) { CarouselButtonNavigation(player, pagerState, carouselButtonScope) }
    var carouselPagerMounted by remember(pagerState) { mutableStateOf(false) }
    val carouselButtonMotion by rememberUpdatedState(
        carouselOwnsArtwork && visiblePlayerArtwork && !isShowingLyrics,
    )
    val deferMinimalButtonSkip by rememberUpdatedState(
        playerType == PlayerType.Essential && visiblePlayerArtwork && !showInlineVideo,
    )
    val previewCarouselNavigation: (Boolean) -> Boolean = remember(carouselButtons, player) {
        { forward ->
            when {
                carouselButtons.move(forward) {
                    carouselButtonMotion && carouselPagerMounted && pagerState.layoutInfo.visiblePagesInfo.isNotEmpty()
                } -> true
                deferMinimalButtonSkip -> {
                    // Let the click feedback start before dispatching Media3's track callbacks.
                    carouselButtonScope.launch {
                        withFrameNanos { }
                        if (forward) player.playNextIfAvailable() else player.playPreviousIfAvailable()
                    }
                    true
                }
                else -> false
            }
        }
    }
    DisposableEffect(carouselButtons) { onDispose { carouselButtons.cancel() } }
    var carouselArtworkRevision by remember(expandedArtworkStack) { mutableIntStateOf(0) }
    fun reportCarouselArtworkDrawn(drawn: MutableState<Boolean>) {
        if (drawn.value) return
        drawn.value = true
        // A newly visible neighbor can change the pixels behind already measured controls.
        if (expandedArtworkStack) carouselArtworkRevision++
    }
    val thumbnailOwnsArtwork = !showInlineVideo && playerType == PlayerType.Essential &&
        showLandscapeArtworkColumn && coverArtworkVisible
    val colorArtworkItem = if (fullscreenPagerOwnsArtwork) currentSwipedMediaItem else mediaItem
    if (visiblePlayerArtwork) ImageCacheFactory.ObserveDisplayedArtwork()
    val coverDisplayResult = rememberArtworkDisplayResult(
        coverUrl(colorArtworkItem), !lightTheme, awaitArtworkMetadata = colorArtworkItem.mediaId == mediaItem.mediaId,
    )
    val coverDisplay = coverDisplayResult.colors
    val globalTargetPalette = LocalPaletteFadeTarget.current?.colorPalette ?: color
    val paletteName by rememberPreference(colorPaletteNameKey, ColorPaletteName.Dynamic)
    val useArtworkTheme = paletteName == ColorPaletteName.Dynamic
    val artworkFallback = remember(lightTheme) { dynamicArtworkFallback(!lightTheme) }
    val targetCover = rememberPlayerCoverIdentity(mediaItem.mediaId, coverUrl(mediaItem).orEmpty())
    val coverOccurrence = m3eOccurrences.current
    val paletteCover = if (colorArtworkItem.mediaId == mediaItem.mediaId) targetCover
        else rememberPlayerCoverIdentity(colorArtworkItem.mediaId, coverUrl(colorArtworkItem).orEmpty())
    var presentedCover by remember(player, playerUpdateTrigger, isLandscape) { mutableStateOf<PlayerCoverIdentity?>(null) }
    var presentedCoverOccurrence by remember(m3eOccurrences, isLandscape) { mutableStateOf<Long?>(null) }
    fun onCoverSettled(occurrence: Long, identity: PlayerCoverIdentity) {
        if (occurrence != m3eOccurrences.current || identity != targetCover) return
        presentedCover = identity
        presentedCoverOccurrence = occurrence
    }
    val drawsArtwork = !showCoverThumbnailAnimation || (
        coverThumbnailAnimation != ThumbnailCoverType.CD &&
            (coverThumbnailAnimation != ThumbnailCoverType.Vinyl || !carouselOwnsArtwork || imageCoverSize > 0f)
        )
    val hasForegroundArtwork =
        fullscreenPagerOwnsArtwork || (drawsArtwork && (carouselOwnsArtwork || thumbnailOwnsArtwork))
    val expectsForegroundCover = visiblePlayerArtwork && hasForegroundArtwork
    val waitingForCover = expectsForegroundCover && paletteCover.artworkUri.isNotBlank() &&
        paletteCover.artworkUri != "null" && (presentedCover != paletteCover ||
            (!fullscreenPagerOwnsArtwork && presentedCoverOccurrence != coverOccurrence))
    val dynamicColorPalette = coverDisplay?.palette?.let { palette ->
        if (coverDisplay.forceDark) palette.copy(accent = coverDisplay.foreground ?: palette.text) else palette
    } ?: if (useArtworkTheme) artworkFallback else globalTargetPalette
    val holdBackgroundArtwork = waitingForCover || (visiblePlayerArtwork && !coverDisplayResult.resolved)
    val nextBackgroundArtwork = PlayerBackgroundArtwork(paletteCover.mediaId, paletteCover.artworkUri,
        paletteCover.sourceRevision, coverDisplay, dynamicColorPalette)
    val retainedBackgroundArtwork = remember(playerUpdateTrigger) { mutableStateOf(nextBackgroundArtwork) }
    val backgroundArtwork = if (holdBackgroundArtwork) retainedBackgroundArtwork.value else nextBackgroundArtwork
    if (!holdBackgroundArtwork) SideEffect { retainedBackgroundArtwork.value = nextBackgroundArtwork }

    val backgroundDisplay = backgroundArtwork.display
    val backgroundPalette = backgroundArtwork.palette
    val artworkThemePalette = if (useArtworkTheme) remember(backgroundDisplay?.palette, artworkFallback, colorPaletteMode) {
        val palette = backgroundDisplay?.palette ?: artworkFallback
        when (colorPaletteMode) {
            ColorPaletteMode.PitchBlack, ColorPaletteMode.PureBlack -> palette.applyPitchBlack
            else -> palette
        }
    } else null
    val targetPalette = artworkThemePalette ?: globalTargetPalette
    val coverColors = backgroundDisplay?.colors
    val coverSwatches = coverColors ?: backgroundPalette.accent.toArgb().let { accent ->
        M3ECoverColors(accent, accent, accent, accent, accent, accent, accent)
    }
    val coverBackground = if (coverColors != null || backgroundDisplay?.forceDark == true)
        backgroundDisplay?.background ?: backgroundPalette.background1 else backgroundPalette.background1

    val usesFlatCoverBackground =
        playerBackgroundColors == PlayerBackgroundColors.CoverColor ||
        playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient

    fun coverGradientColor(swatch: Int): Color =
        if (backgroundDisplay?.forceDark == true || backgroundDisplay?.isExtremeBlack == true) coverBackground
        else m3eSaturate(swatch, lightTheme).m3eDarkenBy(lightTheme)

    val view = LocalView.current
    val globalIsDark = color.isDark
    
    fun getWindow(): Window? {
        var context = view.context
        while (context is ContextWrapper) {
            if (context is Activity) return context.window
            context = context.baseContext
        }
        return null
    }

    val totalPlayTimes = rememberPlayerQueueDuration(queueTimeline, mediaItems, showTotalTimeQueue, getActiveProfile(context))

    // Issue #881 (gh-881): tolerant saver (spec M5).
    var isShowingStatsForNerds by rememberSaveable(saver = TolerantBoolStateSaver("player.isShowingStatsForNerds")) {
        mutableStateOf(false)
    }


    val glassSettings = rememberGlassSettings()
    val inheritedOpticsEnabled = LocalGlassOpticsEnabled.current
    val inheritedUnderlayCovered = LocalGlassUnderlayCovered.current
    val queuePanelVisibleState = remember { mutableStateOf(false) }
    val isQueuePanelVisible by queuePanelVisibleState
    val queueFullyCoveringState = remember { mutableStateOf(false) }
    val isQueueFullyCovering by queueFullyCoveringState
    val playerUnderlayCovered = playerQueueUnderlayCovered(inheritedUnderlayCovered, isQueuePanelVisible, isQueueFullyCovering)
    val playerExpanded = playerSheetState.isExpanded
    val decorationsVisible = playerContentVisible && !appRunningInBackground && !playerUnderlayCovered
    val decorationsActive = rememberPlayerDecorationsActive(playerExpanded && decorationsVisible)
    val playerTransition = LocalPlayerSheetGlassTransition.current
        ?: rememberPlayerSheetGlassTransition(playerSheetState)
    val blurAdjuster = BlurAdjuster()
    val waitForBlurredCover = playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor
    var presentedBackdrop by remember(waitForBlurredCover, isLandscape) { mutableStateOf<PlayerCoverIdentity?>(null) }
    var settledBackdrop by remember(waitForBlurredCover) { mutableStateOf<PlayerCoverFrame<Painter>?>(null) }
    val backgroundArtworkPager = when {
        showFullScreenArtworkPager -> pagerStateFS
        carouselOwnsArtwork -> pagerState
        else -> null
    }
    val settledArtworkMatches = settledBackdrop?.identity?.let { identity ->
        identity.artworkUri == backgroundArtwork.coverUrl.orEmpty() && identity.sourceRevision == backgroundArtwork.coverRevision
    } == true
    if (artworkInMotion != null) {
        val waitingForBackdrop = shouldWaitForPlayerBackdrop(
            blurredBackground = waitForBlurredCover,
            expectsForegroundCover = expectsForegroundCover,
            fullscreenPagerOwnsPresentation = showFullScreenArtworkPager && fullCoverPresentation,
            artworkUri = backgroundArtwork.coverUrl,
            backdropMatches = settledArtworkMatches,
        )
        val artworkMoving = visiblePlayerArtwork && (holdBackgroundArtwork || waitingForBackdrop || (backgroundArtworkPager != null &&
            (backgroundArtworkPager.isScrollInProgress || backgroundArtworkPager.settledPage != player.currentMediaItemIndex)))
        SideEffect { artworkInMotion.value = artworkMoving }
        DisposableEffect(artworkInMotion) { onDispose { artworkInMotion.value = false } }
    }
    val requestedGradient = remember(animatedGradient, mediaItem.mediaId) {
        if (animatedGradient == AnimatedGradient.Random) AnimatedGradient.entries[(2..13).random()]
        else animatedGradient
    }
    val usesAnimatedBackground = playerBackgroundColors == PlayerBackgroundColors.AnimatedGradient
    val randomGradientReady = animatedGradient != AnimatedGradient.Random ||
        m3eOccurrences.isPresented(coverOccurrence, requestedGradient)
    var displayedGradient by remember(animatedGradient) { mutableStateOf(requestedGradient) }
    val gradientType = if (randomGradientReady) requestedGradient else displayedGradient
    if (randomGradientReady) SideEffect { displayedGradient = gradientType }
    val tracksM3EPresentation = usesAnimatedBackground && (
        animatedGradient == AnimatedGradient.Random ||
            gradientType == AnimatedGradient.M3EMorphingTheme || gradientType == AnimatedGradient.M3EMorphingCover
        )
    var firstPresentedCover by remember(player, playerUpdateTrigger) { mutableStateOf<PlayerCoverIdentity?>(null) }
    val canReportFirstCover = decorationsVisible && expectsForegroundCover && !fullscreenPagerOwnsArtwork &&
        statsfornerds
    val hasRequestedCover = targetCover.artworkUri.isNotBlank() && targetCover.artworkUri != "null"
    fun onFirstCoverPresented(occurrence: Long, identity: PlayerCoverIdentity) {
        if (occurrence != m3eOccurrences.current || identity != targetCover) return
        if (statsfornerds) firstPresentedCover = identity
    }
    val gradientFade = rememberPlayerGradientFade(gradientType, decorationsActive && usesAnimatedBackground)
    val gradientSettled = !usesAnimatedBackground ||
        (gradientFade.target == gradientType && gradientFade.outgoing == null)
    val usesCoverFluidGradient = usesAnimatedBackground &&
        (gradientType == AnimatedGradient.FluidCoverColorGradient || gradientType == AnimatedGradient.M3EMorphingCover)
    // Outgoing renderers still need their paint roles while the next effect fades in.
    val targetFluidColors = when {
        !usesAnimatedBackground -> null
        backgroundDisplay?.isExtremeBlack == true -> PlayerFluidColors.ExtremeBlackCover
        else -> PlayerFluidColors(backgroundPalette.background0, backgroundPalette.background2, backgroundPalette.background1, coverBackground)
    }
    val targetSwatches = if (playerBackgroundColors == PlayerBackgroundColors.ColorPalette || usesAnimatedBackground) {
        val (dominant, vibrant, lightVibrant, darkVibrant, muted, lightMuted, darkMuted) = coverSwatches
        if (usesAnimatedBackground) {
            listOf(dominant, vibrant, lightVibrant, darkVibrant, muted, lightMuted, darkMuted).map(::coverGradientColor)
        } else {
            listOf(Color(dominant), Color(vibrant), Color(lightVibrant), Color(darkVibrant),
                Color(muted), Color(lightMuted), Color(darkMuted))
        }
    } else null
    val coverForeground = backgroundDisplay?.takeIf { it.colors != null || it.forceDark }?.foreground
    val targetArtworkPaint = PlayerArtworkPaint(
        backgroundPalette.accent, coverBackground, targetFluidColors, targetSwatches,
        GlassArtworkTint(backgroundDisplay?.dominant, if (backgroundDisplay?.forceDark == true) 1f else 0f),
        lyricsForeground = coverForeground ?: Color.DarkGray,
        visualizerForeground = coverForeground ?: Color.White,
        lyricsSurface = backgroundDisplay?.takeIf { it.forceDark }?.background ?: targetPalette.accent,
        themePalette = targetPalette,
    )
    val artworkPaintTarget = LocalPlayerArtworkPaintTarget.current
    val contrastTarget = LocalPlayerContrastTarget.current
    val backdropOwnsPresentation = visiblePlayerArtwork && waitForBlurredCover && !expectsForegroundCover
    val backdropPresented = presentedBackdrop == PlayerCoverIdentity(
        backgroundArtwork.mediaId, backgroundArtwork.coverUrl.orEmpty(), backgroundArtwork.coverRevision,
    )
    val paletteReady = !holdBackgroundArtwork && (!backdropOwnsPresentation || backdropPresented)
    if (paletteReady) {
        SideEffect {
            artworkPaintTarget?.value = targetArtworkPaint
            contrastTarget?.value = backgroundArtwork.coverUrl to backgroundArtwork.coverRevision
            if (tracksM3EPresentation && decorationsVisible && backgroundArtwork.mediaId == mediaItem.mediaId) {
                m3eOccurrences.present(coverOccurrence, requestedGradient)
            }
        }
    }
    val scopedArtworkPaint = LocalPlayerArtworkPaint.current
    val latestArtworkTarget = rememberUpdatedState(targetArtworkPaint)
    val artworkPaint = remember(scopedArtworkPaint) {
        derivedStateOf { scopedArtworkPaint?.value ?: latestArtworkTarget.value }
    }
    val latestTargetSwatches = rememberUpdatedState(targetSwatches)
    val displayedSwatches = remember(artworkPaint) {
        derivedStateOf { (artworkPaint.value.swatches ?: latestTargetSwatches.value).orEmpty() }
    }
    val opaqueCoverFill by remember(artworkPaint) {
        derivedStateOf { artworkPaint.value.surface.alpha == 1f }
    }
    val hasOpaqueFlatFill = when (playerBackgroundColors) {
        PlayerBackgroundColors.CoverColor -> opaqueCoverFill
        PlayerBackgroundColors.ThemeColor -> color.background1.alpha == 1f
        else -> false
    }
    var backdropViewportSize by remember(isLandscape) { mutableStateOf(IntSize.Zero) }
    var backdropFillSize by remember(isLandscape) { mutableStateOf(IntSize.Zero) }
    val skipStandaloneCover = canSkipStandalonePlayerCover(
        hasForegroundArtwork, hasOpaqueFlatFill, backdropViewportSize, backdropFillSize,
    )

    val artworkAccent = remember(artworkPaint) { derivedStateOf { artworkPaint.value.accent } }
    val artworkGlass = remember(artworkPaint) { derivedStateOf { artworkPaint.value.glass } }
    SideEffect { videoSession?.controlArtworkAccent = artworkAccent }
    val backdropSettled = remember(artworkPaint, targetArtworkPaint, usesFlatCoverBackground) {
        derivedStateOf {
            val displayed = artworkPaint.value
            (!usesFlatCoverBackground || displayed.surface == targetArtworkPaint.surface) &&
                displayed.fluid == targetArtworkPaint.fluid && displayed.swatches == targetArtworkPaint.swatches
        }
    }.value

    val usesArtworkSwatches = playerBackgroundColors == PlayerBackgroundColors.ColorPalette ||
        (usesAnimatedBackground && (gradientType == AnimatedGradient.Linear || gradientType == AnimatedGradient.Mesh))
    val sampledTheme = targetPalette.takeUnless { waitForBlurredCover || usesCoverFluidGradient || usesArtworkSwatches }
    val sampledCoverBackground = if (backgroundDisplay == null) sampledTheme?.background1 else coverBackground
    // Key the recording to the final palette. Animated values would restart the sampler every frame.
    val contrastDecisionKey = listOf(backgroundArtwork.mediaId, backgroundArtwork.coverUrl, backgroundArtwork.coverRevision,
        coverColors, sampledCoverBackground, targetFluidColors, targetSwatches,
        isLandscape, playerBackgroundColors, sampledTheme?.background0, sampledTheme?.background1, sampledTheme?.background2,
        sampledTheme?.background3, sampledTheme?.background4, sampledTheme?.accent,
        colorPaletteMode, gradientType, blackgradient, bottomgradient,
        blurAdjuster.strength, blurAdjuster.backdrop, settledBackdrop, carouselArtworkRevision)
    var contrastDecisionReady by remember(contrastDecisionKey, playerContentVisible) { mutableStateOf(false) }
    val renderedBackdropReady = when {
        waitForBlurredCover -> settledArtworkMatches && backgroundArtworkPager?.isScrollInProgress != true
        expandedArtworkStack -> !waitingForCover && !pagerState.isScrollInProgress
        else -> null
    }
    val backdropTransitioning = !gradientSettled || !backdropSettled || (sampledTheme != null && color != sampledTheme) ||
        renderedBackdropReady == false || !paletteReady || LocalPlayerPaletteTransitioning.current
    val canSamplePlayer = playerExpanded && playerContentVisible && !appRunningInBackground && !playerUnderlayCovered
    val samplePlayerBackground = canSamplePlayer && !backdropTransitioning
    val captureGlassSource = playerGlassSourceCaptureActive(
        glassSettings, playerTransition, inheritedOpticsEnabled)
    val capturePlayerBackground = (samplePlayerBackground && !contrastDecisionReady) || captureGlassSource
    val playerBackground = rememberGlassPlayerBackground()
    val playerCoverBackground = rememberLayerBackdrop()
    val animatedGradientBackdrop = if (usesAnimatedBackground) rememberLayerBackdrop() else null
    val playerControlsBackdrop = if (animatedGradientBackdrop != null)
        rememberCombinedBackdrop(playerCoverBackground, animatedGradientBackdrop, playerBackground.backdrop)
    else rememberCombinedBackdrop(playerCoverBackground, playerBackground.backdrop)
    val controlsSource = rememberPlayerControlsGlassSource(playerControlsBackdrop)
    val artworkShadowOverflow = rememberPlayerArtworkShadowOverflow()
    // Glass and contrast must see the same passive layer when covers extend behind the controls.
    val artworkControlsSource = rememberPlayerArtworkControlsBackdrop(
        controlsSource, artworkShadowOverflow, captureGlassSource || expandedArtworkStack,
    )
    val usesFlatThemeBackground = playerBackgroundColors == PlayerBackgroundColors.ThemeColor
    val usesThemeFluidGradient = usesAnimatedBackground &&
        (gradientType == AnimatedGradient.FluidThemeColorGradient || gradientType == AnimatedGradient.M3EMorphingTheme)
    val displayedThemeSurface = rememberUpdatedState(
        when {
            usesThemeFluidGradient && gradientSettled ->
                PlayerFluidColors(color.background0, color.background2, color.background1, color.accent).centerSurface()
            usesFlatThemeBackground -> color.background1
            else -> null
        },
    )
    val provisionalCoverSurface = remember(
        artworkPaint, usesCoverFluidGradient, usesFlatCoverBackground, usesFlatThemeBackground, gradientSettled,
    ) {
        derivedStateOf {
            when {
                usesCoverFluidGradient && gradientSettled -> artworkPaint.value.fluid?.centerSurface()
                usesFlatCoverBackground -> artworkPaint.value.surface
                else -> displayedThemeSurface.value
            }
        }
    }
    val backgroundSampling = rememberPlayerBackdropSampling(artworkControlsSource, samplePlayerBackground,
        contrastDecisionKey,
        provisionalSurface = provisionalCoverSurface,
        renderedBackgroundReady = renderedBackdropReady,
        retainSettledContrast = waitForBlurredCover,
    )
    SideEffect { contrastDecisionReady = backgroundSampling.finished }
    val queueNavigationSurface = if (isQueuePanelVisible && !usesQueueToolbarGlass()) color.background1 else null
    PlayerSystemBarAppearance(active = playerSheetState.isExpanded && playerContentVisible,
        sampling = backgroundSampling, fallbackSurface = color.background0,
        navigationSurfaceOverride = queueNavigationSurface)
    val playerVisibleBackdrop = rememberLayerBackdrop()
    val queuePlayerBackdrop = rememberLayerBackdrop()
    val capturePlayerScene = playerGlassSceneCaptureActive(glassSettings, playerSheetState.isExpanded, captureGlassSource)
    val captureQueueUnderlay = capturePlayerScene && (showQueue || isQueuePanelVisible)
    val blurredVeil = if (waitForBlurredCover && bottomgradient)
        lerp(Color.White, Color.Black, paletteDarkness()) else Color.Transparent
    val blurredVeilEnd = blurredVeil.copy(alpha = blurredVeil.alpha * if (isLandscape) .8f else .75f)
    val blurredVeilBase = if (isLandscape) blurredVeil.copy(alpha = blurredVeil.alpha * .25f) else Color.Transparent

    @Composable
    fun PlayerBlurredCover(
        thumbnailUrl: String,
        blurAdjuster: BlurAdjuster,
        showThumbnail: Boolean,
        noBlur: Boolean,
        isShowingLyrics: Boolean,
        isShowingVisualizer: Boolean,
        modifier: Modifier = Modifier,
        captureBackdrop: Boolean = true,
        publishArtworkColors: Boolean = false,
        allowOwnRotation: Boolean = decorationsActive,
        committedRevision: Long? = null,
        fullscreenPresentationMediaId: String? = null,
    ) {
        // Sampling and Glass may toggle during sheet motion; the cover must retain its identity.
        Box(Modifier.fillMaxSize().then(
            if (capturePlayerBackground && captureBackdrop) Modifier.layerBackdrop(playerCoverBackground) else Modifier
        )) {
            val presentationMediaId = fullscreenPresentationMediaId
                ?: backgroundArtwork.mediaId.takeIf { captureBackdrop && backdropOwnsPresentation }
            PlayerCurrentCover(thumbnailUrl, thumbnailUrl, blurAdjuster, showThumbnail, noBlur,
                isShowingLyrics, isShowingVisualizer, modifier, publishArtworkColors,
                allowOwnRotation = allowOwnRotation,
                committedRevision = committedRevision,
                presentationMediaId = presentationMediaId,
                onArtworkPresented = when {
                    fullscreenPresentationMediaId != null -> { identity -> presentedCover = identity }
                    presentationMediaId != null -> { identity -> presentedBackdrop = identity }
                    else -> null
                },
                onBackdropSettled = if (captureBackdrop && waitForBlurredCover) {
                    { settledBackdrop = it }
                } else null,
            )
        }
    }

    var containerModifier = Modifier
        .padding(bottom = 0.dp)
    var playerForegroundClip: Modifier = Modifier
    var deltaX by remember { mutableStateOf(0f) }


    var circleOffsetY by remember {mutableStateOf(0f)}

    @Composable
    fun Modifier.conditional(condition : Boolean, modifier : @Composable Modifier.() -> Modifier) : Modifier {
        return if (condition) {
            then(modifier(Modifier))
        } else {
            this
        }
    }


    @Composable
    fun FullCoverPagerPage() {
        Box(Modifier.fillMaxSize().combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                if (thumbnailTapEnabled && !showthumbnail) {
                    if (isShowingVisualizer) isShowingVisualizer = false
                    isShowingLyrics = !isShowingLyrics
                }
            },
            onDoubleClick = {
                if (!lyricsShownOnCover && !showvisthumbnail) showthumbnail = !showthumbnail
            },
            onLongClick = {
                blurAdjuster.isActive = showthumbnail || (isShowingLyrics && !isShowingVisualizer) || !noblur
            },
        ))
    }

    if (!isGradientBackgroundEnabled) {
        if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor && (playerType == PlayerType.Essential || (showthumbnail && (!albumCoverRotation)))) {
            // Contrast sampling also captures the background without optical Glass.
            // Keep the cached clip after the capture boundary in both cases.
            playerForegroundClip = Modifier.clip(uiRoundnessShape())
            containerModifier = containerModifier
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        1.0f to blurredVeilEnd,
                        startY = playerGradientStartY,
                        endY = POSITIVE_INFINITY
                    )
                )
                .background(blurredVeilBase)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (thumbnailTapEnabled && !showthumbnail) {
                            if (isShowingVisualizer) isShowingVisualizer = false
                            isShowingLyrics = !isShowingLyrics
                        }
                    },
                    onDoubleClick = {
                        if (!lyricsShownOnCover && !showvisthumbnail)
                            showthumbnail = !showthumbnail
                    },
                    onLongClick = {
                        blurAdjuster.isActive =
                            showthumbnail || (isShowingLyrics && !isShowingVisualizer) || !noblur
                    }
                )
                .pointerInput(Unit) {
                    try {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                try {
                                    deltaX += dragAmount
                                } catch (e: IllegalStateException) {
                                    // View may have been detached during drag
                                }
                            },
                            onDragStart = {
                                deltaX = 0f
                            },
                            onDragEnd = {
                                try {
                                    // Guest in a Listen Together room: the skip op reaches the central guard, which no-ops it
                                    // and shows the throttled toast (spec-listen-together-guest-lock-hardening)
                                    if (!disablePlayerHorizontalSwipe && playerType == PlayerType.Essential) {
                                        if (deltaX > playerSwipeSensitivity.threshold) {
                                            player.playPrevious()
                                        } else if (deltaX < -playerSwipeSensitivity.threshold) {
                                            player.playNext()
                                        }
                                    }
                                } catch (e: IllegalStateException) {
                                    // View may have been detached
                                }
                            }
                        )
                    } catch (e: IllegalStateException) {
                        // View may have been detached before gesture detection
                    }
                }

        } else if (playerBackgroundColors == PlayerBackgroundColors.ColorPalette){
            val palette = displayedSwatches
            containerModifier = containerModifier
                .drawBehind {
                    val colors = palette.value
                    val boxheight = (size.height)/7
                    colors.forEachIndexed {i, _ ->
                        drawRect(
                            color = colors[i],
                            topLeft = Offset(0f,i*boxheight),
                            size = Size(size.width,boxheight)
                        )
                    }
                }
        } else if (playerBackgroundColors == PlayerBackgroundColors.CoverColor){
            containerModifier = containerModifier.drawBehind { drawRect(artworkPaint.value.surface) }
        } else if (playerBackgroundColors == PlayerBackgroundColors.ThemeColor){
            containerModifier = containerModifier
                .background(color.background1)
        }
    } else {
        when (playerBackgroundColors) {
            PlayerBackgroundColors.AnimatedGradient -> Unit

            else -> {
                val usesCoverGradient = playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient
                val themeStart = surfaceColor(color.background1)
                val themeEnd = surfaceColor(color.background2)
                containerModifier = containerModifier.drawWithCache {
                    val cover = if (usesCoverGradient) artworkPaint.value.surface else null
                    val brush = Brush.verticalGradient(
                        0.5f to (cover ?: themeStart),
                        1.0f to (if (blackgradient) Color.Black else cover ?: themeEnd),
                        startY = 0.0f,
                        endY = 1500.0f,
                    )
                    onDrawBehind { drawRect(brush) }
                }

            }
        }

    }

    @Composable
    fun BoxScope.AnimatedPlayerBackground() {
        val backdrop = animatedGradientBackdrop ?: return
        PlayerAnimatedBackground(
            backdrop = backdrop,
            capturePlayerBackground = capturePlayerBackground,
            gradientFade = gradientFade,
            decorationsActive = decorationsActive,
            m3eRendered = decorationsVisible,
            m3eAlwaysShowShapes = m3eAlwaysShowShapes,
            color = color,
            targetFluidColors = targetFluidColors,
            artworkPaint = artworkPaint,
            isActuallyPlaying = { isActuallyPlaying },
            shouldPlayM3E = { m3ePlaybackIsActive(playbackState, isActuallyPlaying) },
            coverSwatches = coverSwatches,
            coverGradientColor = ::coverGradientColor,
            m3eOccurrences = m3eOccurrences,
            m3eBurstGradient = gradientType,
            displayedSwatches = displayedSwatches,
        )
    }

    val controllerPreviewPage = if (showthumbnail && playerType == PlayerType.Modern && coverArtworkVisible)
        pagerState.currentPage else artworkPreviewPage
    val isShowingStandaloneLyrics = playerStandaloneLyricsVisible(
        showthumbnail, isShowingLyrics, coverOverlayPolicy.lyricsOnCover)
    val showActionsInFullscreenLyrics by rememberPreference(actionExpandedKey, true)
    val landscapeStandaloneVisualizer = playerStandaloneVisualizerVisible(
        showthumbnail, isShowingVisualizer, coverOverlayPolicy.visualizerOnCover)
    val landscapeModernVisualizerState = rememberOverlayTransition(isLandscape && showLandscapeArtworkColumn &&
        landscapeStandaloneVisualizer && playerType == PlayerType.Modern)
    val landscapeEssentialVisualizerState = rememberOverlayTransition(isLandscape && showLandscapeArtworkColumn &&
        landscapeStandaloneVisualizer && playerType == PlayerType.Essential)

    val showsInlineStats = (!showthumbnail || playerType == PlayerType.Modern) &&
        (isLandscape || !isShowingStandaloneLyrics || statsExpanded)
    fun statsMediaId(): String {
        if (!statsfornerds) return mediaItem.mediaId
        val displayed = when {
            fullscreenPagerOwnsArtwork -> presentedCover
            hasForegroundArtwork && !hasRequestedCover && coverDisplayResult.resolved -> null
            hasForegroundArtwork -> firstPresentedCover ?: presentedCover
            waitForBlurredCover -> presentedBackdrop
            else -> null
        }
        return (if (presentedCover == targetCover) targetCover else displayed)?.mediaId ?: mediaItem.mediaId
    }
    LaunchedEffect(statsfornerds) {
        if (!statsfornerds) firstPresentedCover = null
    }
    val statsMenuControl = rememberUpdatedState(
        when {
            thumbnailOwnsArtwork && playerError == null -> PlayerStatsMenuControl(
                isVisible = { isShowingStatsForNerds },
                onToggle = { isShowingStatsForNerds = !isShowingStatsForNerds },
            )
            showsInlineStats -> PlayerStatsMenuControl(
                isVisible = { statsfornerds },
                onToggle = { statsfornerds = !statsfornerds },
            )
            else -> null
        },
    )

    val thumbnailContent: @Composable () -> Unit = {
        var deltaX by remember { mutableStateOf(0f) }

        PlayerPortraitThumbnailFrame(
            enabled = !isLandscape && playerCoverArtworkVisible(showthumbnail, isShowingLyrics,
                coverOverlayPolicy.lyricsOnCover, isShowingVisualizer, coverOverlayPolicy.visualizerOnCover),
            verticalPadding = if (thumbnailType == ThumbnailType.Modern) 16.dp else 0.dp,
            contentPadding = playerThumbnailPadding(thumbnailSizeDp),
        ) {
            Thumbnail(
                currentArtworkUrl = coverUrl(mediaItem),
                publishArtworkColors = visiblePlayerArtwork && thumbnailOwnsArtwork && !fullscreenPagerOwnsArtwork,
                prefetchAdjacentArtwork = preview == null && visiblePlayerArtwork && thumbnailOwnsArtwork,
                prefetchAfterTransition = !holdBackgroundArtwork && !backdropTransitioning,
                rotationActive = decorationsActive,
                onArtworkPresented = if (expectsForegroundCover && thumbnailOwnsArtwork) {
                    { identity -> onCoverSettled(coverOccurrence, identity) }
                } else null,
                presentationOccurrence = coverOccurrence,
                onArtworkFirstPresented = if (canReportFirstCover && thumbnailOwnsArtwork) {
                    ::onFirstCoverPresented
                } else null,
                thumbnailTapEnabledKey = thumbnailTapEnabled,
                isShowingLyrics = isShowingLyrics,
                onShowLyrics = { isShowingLyrics = it },
                isShowingStatsForNerds = isShowingStatsForNerds,
                onShowStatsForNerds = { isShowingStatsForNerds = it },
                isShowingVisualizer = isShowingVisualizer,
                onShowEqualizer = { isShowingVisualizer = it },
                showthumbnail = showthumbnail,
                verticalPadding = if (isLandscape) 0.dp else 8.dp,
                onMaximize = {},
                onDoubleTap = {
                    val currentMediaItem = player.currentMediaItem
                    currentMediaItem
                        ?.takeIf { it.mediaId == mediaItem.mediaId }
                        ?.let { item ->
                            NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                YouTubeSync.rotateSongLikeState( context, item )
                            }
                        }
                    if (effectRotationEnabled) isRotated = !isRotated
                },
                modifier = Modifier
                    //.nestedScroll( connection = scrollConnection )
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                deltaX += dragAmount
                            },
                            onDragStart = {
                                deltaX = 0f
                            },
                            onDragEnd = {
                                // Guest in a Listen Together room: the skip op reaches the central guard, which no-ops it
                                // and shows the throttled toast (spec-listen-together-guest-lock-hardening)
                                if (!disablePlayerHorizontalSwipe && playerType == PlayerType.Essential) {
                                    if (deltaX > playerSwipeSensitivity.threshold) {
                                        player.playPrevious()
                                    } else if (deltaX < -playerSwipeSensitivity.threshold) {
                                        player.playNext()
                                    }

                                }

                            }

                        )
                    }
                    .padding(all = playerThumbnailPadding(if (isLandscape) thumbnailSizeLDp else thumbnailSizeDp, isLandscape))
                    .then(
                        if (playerType == PlayerType.Essential) Modifier.thumbnailpause(shouldBePlaying)
                        else Modifier
                    )

            )
        }

    }

    @Composable
    fun Controller( 
        displayItem: MediaItem,
        modifier: Modifier, 
        isBuffering: Boolean,
        position: () -> Long,
        duration: () -> Long
    ) {
        PlayerTrackInformation(
            displayItem = displayItem,
            currentMediaId = mediaItem.mediaId,
            currentLinks = currentLinks,
            linkCache = linkCache,
            navController = navController,
            onDismiss = onDismiss,
            blurAdjuster = blurAdjuster,
            expandedplayer = expandedplayer,
            titleExpanded = titleExpanded,
            timelineExpanded = timelineExpanded,
            controlsExpanded = controlsExpanded,
            isShowingStandaloneLyrics = isShowingStandaloneLyrics,
            shouldBePlaying = shouldBePlaying,
            backgroundPalette = backgroundPalette,
            artworkAccent = artworkAccent,
            modifier = modifier,
            isBuffering = isBuffering,
            position = position,
            duration = duration,
        )
    }

    @Composable
    fun CarouselPage(
        pageIndex: Int,
        carouselGeometry: PlayerCarouselGeometry,
        spacingDialogAllowed: () -> Boolean,
        showDragIndicator: () -> Boolean,
    ) {
        val pageItem = mediaItems.getOrNull(pageIndex)
        val pageCover = rememberPlayerCoverIdentity(pageItem?.mediaId.orEmpty(), coverUrl(pageItem).orEmpty())
        val artworkDrawn = remember(pageCover) { mutableStateOf(false) }
        val coverPainter = ImageCacheFactory.Painter(
            thumbnailUrl = pageCover.artworkUri,
            publishArtworkColors = visiblePlayerArtwork && carouselOwnsArtwork &&
                !fullscreenPagerOwnsArtwork && pageIndex == pagerState.currentPage,
            onArtworkDrawn = { reportCarouselArtworkDrawn(artworkDrawn) },
            playerArtworkMediaId = pageItem?.mediaId,
        )
        val activeCoverPage = expectsForegroundCover && pageCover == targetCover &&
            pageIndex == player.currentMediaItemIndex && pageIndex == pagerState.currentPage
        ReportPresentedPlayerCover(
            identity = pageCover,
            ready = artworkDrawn.value,
            active = activeCoverPage,
            motionSettled = pageIndex == pagerState.settledPage && !pagerState.isScrollInProgress,
            occurrence = coverOccurrence,
        ) { onCoverSettled(coverOccurrence, pageCover) }
        if (canReportFirstCover) {
            ReportFirstPresentedPlayerCover(
                occurrence = coverOccurrence,
                identity = pageCover,
                ready = artworkDrawn.value,
                active = activeCoverPage,
                onPresented = ::onFirstCoverPresented,
            )
        }

        val coverModifier = Modifier.playerThumbnailCover(
            pagerState = pagerState,
            shadowOverflow = artworkShadowOverflow,
            page = pageIndex,
            padding = carouselGeometry.coverPadding,
            carousel = carousel,
            replayArtworkOverflow = carouselGeometry.hasArtworkStackOverlap(thumbnailType),
            scaleAlways = false,
            scaleStart = PlayerCarouselCoverScaleStart,
            thumbnailType = thumbnailType,
            showCoverThumbnailAnimation = showCoverThumbnailAnimation,
            onTap = { page ->
                if (page == pagerState.settledPage && thumbnailTapEnabled) {
                    if (isShowingVisualizer) isShowingVisualizer =
                        false
                    isShowingLyrics = !isShowingLyrics
                }
                if (page != pagerState.settledPage) {
                    player.playAtIndex(page)
                }
            },
            onLongClick = { page ->
                if (page == pagerState.settledPage && spacingDialogAllowed())
                    showThumbnailOffsetDialog = true
            }
        )

        val zIndex = itemZIndexFor(pageIndex, pagerState.currentPage)

        PlayerCarouselCover(
            page = pageIndex,
            playingIndex = player.currentMediaItemIndex,
            mediaItem = mediaItems.getOrNull(pageIndex),
            playingMediaId = mediaItem.mediaId,
            showingLyrics = isShowingLyrics,
            lyricsOnCover = coverOverlayPolicy.lyricsOnCover,
            showingVisualizer = isShowingVisualizer,
            visualizerOnCover = coverOverlayPolicy.visualizerOnCover,
            hasPlaybackError = player.playerError != null,
            onDismissLyrics = { isShowingLyrics = false },
            durationProvider = player::getDuration,
            clickLyricsText = clickLyricsText,
            isLandscape = isLandscape,
            modifier = coverModifier.zIndex(zIndex),
        ) {
            PlayerCarouselArtwork(
                coverPainter = coverPainter,
                player = player,
                mediaItems = mediaItems,
                pagerState = pagerState,
                pageIndex = pageIndex,
                showCoverThumbnailAnimation = showCoverThumbnailAnimation,
                imageCoverSize = imageCoverSize,
                decorationsActive = decorationsActive,
                coverThumbnailAnimation = coverThumbnailAnimation,
                showDragIndicator = showDragIndicator,
            )
        }
    }

    blurAdjuster.Render()

    CompositionLocalProvider(
        LocalCarouselButtonNavigation provides previewCarouselNavigation,
        LocalGlassArtworkTint provides artworkGlass,
        LocalPlayerTransportGeometry provides remember { PlayerTransportGeometry() },
        LocalGlassOpticsEnabled provides playerQueueOpticsEnabled(inheritedOpticsEnabled, isQueuePanelVisible),
        LocalGlassOpticsTransitionProgress provides playerTransition,
        LocalGlassUnderlayCovered provides playerUnderlayCovered,
        LocalGlassBackdrop provides artworkControlsSource,
        LocalSurfaceBackdropColor provides null,
        LocalPlayerBackdropSampling provides backgroundSampling,
        LocalPlayerGlyphCorrection provides backgroundSampling.glyphCorrectionProgress,
        LocalPlayerArtworkUnderControls provides (expandedArtworkStack || waitForBlurredCover && blurAdjuster.strength <= 0f),
        LocalPlayerFollowsPaletteInk provides (usesAnimatedBackground && gradientSettled && !expandedArtworkStack &&
            (gradientType == AnimatedGradient.M3EMorphingTheme || gradientType == AnimatedGradient.M3EMorphingCover)),
        LocalForegroundSourcePalette provides color,
        LocalGlassMaterialSource provides artworkControlsSource,
        LocalGlassContainerBackdrop provides artworkControlsSource,
        LocalGlassOverlayBackdrop provides if (capturePlayerScene && playerSheetState.isExpanded) playerVisibleBackdrop
            else if (captureGlassSource) glassSceneOverlayBackdrop(playerVisibleBackdrop) else LocalGlassOverlayBackdrop.current,
    ) {
    val informationSettings = rememberGlassSettings(GlassScope.PlayerHeader)
    val informationFilm = if (informationSettings.usesGlassMaterial(GlassArea.ActionButtons))
        glassFilmColor(informationSettings.forArea(GlassArea.ActionButtons), null, menu = false) else Color.Transparent
    val commonNeutral = rememberNeutralForegroundForSurface {
        backgroundSampling.foregroundSurface?.let { effectiveSurfaceColor(it, informationFilm) }
    }
    CompositionLocalProvider(LocalNeutralForegroundTransition provides commonNeutral) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .playerTransportGeometryHost()
            .onGloballyPositioned { backgroundSampling.coordinates = it }
            .glassPlayerSceneSource(playerVisibleBackdrop, active = capturePlayerScene,
                fullyCovering = playerSheetState.isExpanded)
    ) {
        val playerActions = playerActionBarActions(
            navController = navController,
            mediaItem = mediaItem,
            showQueueState = showQueueState,
            rotateState = rotateState,
            showVisualizerState = showVisualizerState,
            showSleepTimerState = showSleepTimerState,
            showLyricsState = showLyricsState,
            discoverState = discoverState,
            queueLoopState = queueLoopState,
            expandPlayerState = expandPlayerState,
            onDismiss = onDismiss,
            statsMenuControl = statsMenuControl,
            topActionIds = movedActionIds,
            showBottomMenu = !showMenuInTopSlots,
            bottomAudioVideo = if (showBottomAudioVideo && showVideoMode && toggleSession != null) {
                {
                    PlayerAudioVideoToggle(
                        toggleSession,
                        onSearchManually = openManualVideoSearch,
                        interactive = preview == null,
                    )
                }
            } else null,
        )
        val topActionsById = playerActions.associateBy { it.id }

        @Composable
        fun TopActions(useTopPadding: Boolean) {
            PlayerPortraitHeader(
                showTopActionsBar = activeTopSlots,
                actions = topSlots,
                placedActions = topActionsById,
                fixedSlots = hasExplicitTopSlots,
                topPadding = useTopPadding,
                showMenu = showMenuInTopSlots,
                windowInsets = if (isLandscape) WindowInsets(0, 0, 0, 0) else windowInsets,
                glassSettings = glassSettings,
                rotationAngle = rotationAngle,
                showVideoMode = showVideoMode,
                toggleSession = toggleSession,
                videoInteractive = preview == null,
                openManualVideoSearch = openManualVideoSearch,
                onDismiss = onDismiss,
                onOpenMenu = {
                    menuState.display {
                        PlayerMenu(
                            navController = navController,
                            onDismiss = menuState::hide,
                            mediaItem = mediaItem,
                            binder = binder ?: return@display,
                            onClosePlayer = onDismiss,
                            onShowSleepTimer = {
                                isShowingSleepTimerDialog = true
                                menuState.hide()
                            },
                            disableScrollingText = disableScrollingText,
                            statsMenuControl = statsMenuControl,
                        )
                    }
                },
            )
        }

        @Composable
        fun ActionsBar() {
            val bottomActions = playerActions.filterNot { it.id in movedActionIds }
            val visibleActions = if (isShowingStandaloneLyrics && !showActionsInFullscreenLyrics) {
                bottomActions.filter { it.id == ToolbarAction.MENU.id }
            } else bottomActions
            if (visibleActions.isEmpty()) return
            val bar: @Composable BoxScope.() -> Unit = {
                ActionBar(
                    mediaItems = mediaItems,
                    showQueueState = showQueueState,
                    showLyricsState = showLyricsState,
                    playerActions = visibleActions,
                )
            }
            var region by remember { mutableStateOf<Rect?>(null) }
            val surface = rememberPlayerBackdropRegionColor(region, useLocalSample = true)
            val modifier = if (glassSettings.enabled)
                Modifier.align(if (isLandscape) Alignment.BottomEnd else Alignment.BottomCenter) else Modifier
            Box(modifier.onGloballyPositioned { region = it.boundsInWindow() }) {
                SurfaceForegroundContent(surface) { bar() }
            }
        }


        Box(Modifier.fillMaxSize()
            .onSizeChanged { backdropViewportSize = it }
            .then(if (captureQueueUnderlay) Modifier.layerBackdrop(queuePlayerBackdrop) else Modifier)
        ) {
        if (isLandscape) {
         Box{
             if (showFullScreenArtworkPager) {
                   val fling = flingBehaviorFor(pagerStateFS, sensitivity = playerSwipeSensitivity)
                   pagerStateFS.AttachPlayerArtworkPager(
                       player,
                       playerUpdateTrigger,
                       appRunningInBackground,
                       playerContentVisible,
                   )

                 HorizontalPager(
                     state = pagerStateFS,
                     beyondViewportPageCount = 1,
                     flingBehavior = fling,
                     userScrollEnabled = !(albumCoverRotation && (isShowingLyrics || showthumbnail)),
                     modifier = Modifier
                 ) {

                     val isSettledCover = it == pagerStateFS.settledPage
                     val rotateFullscreenCover = albumCoverRotation && (isShowingLyrics || showthumbnail)
                     val rotation = rememberCoverSpin(
                         active = decorationsActive && isSettledCover && rotateFullscreenCover && !fullCoverPresentation,
                         playing = isActuallyPlaying,
                         rotationMillis = 30_000,
                         coastDegrees = 10f,
                     )

                     if (fullCoverPresentation) FullCoverPagerPage() else PlayerBlurredCover(
                         thumbnailUrl = coverUrl(mediaItems.getOrNull(it)).orEmpty(),
                         fullscreenPresentationMediaId = mediaItems.getOrNull(it)?.mediaId?.takeIf { _ ->
                             expectsForegroundCover && it == pagerStateFS.currentPage
                         },
                         publishArtworkColors = visiblePlayerArtwork && it == pagerStateFS.currentPage,
                         captureBackdrop = false,
                         allowOwnRotation = decorationsActive && isSettledCover && !rotateFullscreenCover,
                         blurAdjuster = blurAdjuster,
                         showThumbnail = showthumbnail,
                         noBlur = noblur,
                         isShowingLyrics = isShowingLyrics,
                         isShowingVisualizer = isShowingVisualizer,
                         modifier = Modifier.zIndex(if (it == pagerStateFS.currentPage) 1f else 0.9f)
                                            .conditional(albumCoverRotation) {
                                                graphicsLayer {
                                                    scaleX = if (isShowingLyrics || showthumbnail) (screenWidth/screenHeight) + 0.5f else 1f
                                                    scaleY = if (isShowingLyrics || showthumbnail) (screenWidth/screenHeight) + 0.5f else 1f
                                                    rotationZ = if ((it == pagerStateFS.settledPage) && (isShowingLyrics || showthumbnail)) rotation.value else 0f
                                                }
                                            }
                                            .clip(uiRoundnessShape()).combinedClickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    if (thumbnailTapEnabled && !showthumbnail) {
                                                        if (isShowingVisualizer) isShowingVisualizer = false
                                                        isShowingLyrics = !isShowingLyrics
                                                    }
                                                },
                                                onDoubleClick = {
                                                    if (!lyricsShownOnCover && !showvisthumbnail)
                                                        showthumbnail = !showthumbnail
                                                },
                                                onLongClick = {
                                                    blurAdjuster.isActive = showthumbnail || (isShowingLyrics && !isShowingVisualizer) || !noblur
                                                }
                                            )
                     )
                 }

                 PlayerFullscreenArtworkVeil(playerGradientStartY, blurredVeilEnd, blurredVeilBase)
             }

             if (!skipStandaloneCover) PlayerBlurredCover(
                 thumbnailUrl = backgroundArtwork.coverUrl.orEmpty(),
                 committedRevision = backgroundArtwork.coverRevision,
                 publishArtworkColors = visiblePlayerArtwork && !fullscreenPagerOwnsArtwork &&
                     !carouselOwnsArtwork && !thumbnailOwnsArtwork,
                 blurAdjuster = blurAdjuster,
                 showThumbnail = showthumbnail,
                 noBlur = noblur,
                 isShowingLyrics = isShowingLyrics,
                 isShowingVisualizer = isShowingVisualizer,
             )

            AnimatedPlayerBackground()
            PlayerLandscapeColumns(
                showArtworkColumn = showLandscapeArtworkColumn,
                modifier = Modifier.onSizeChanged { backdropFillSize = it }
                    .then(if (capturePlayerBackground) playerBackground.background else Modifier)
                    .then(containerModifier)
                    .drawBehind {
                        if (backgroundProgress == BackgroundProgress.Both || backgroundProgress == BackgroundProgress.Player) {
                            drawRect(
                                color = color.favoritesOverlay,
                                topLeft = Offset.Zero,
                                size = Size(
                                    width = positionAndDurationState.value.first.toFloat() /
                                            (durationState.value.absoluteValue.takeIf { it > 0 } ?: 1L) * size.width,
                                    height = size.maxDimension
                                )
                            )
                        }
                    }
                    .then(if (capturePlayerBackground) playerBackground.content else Modifier)
                    .then(playerForegroundClip)
                    .playerArtworkShadowOverflow(artworkShadowOverflow),
                artwork = {
                    if (showInlineVideo && videoSession != null) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            InlineMusicVideo(
                                videoSession,
                                artworkPadding = playerThumbnailPadding(thumbnailSizeLDp, landscape = true)
                                    .takeIf { showthumbnail },
                                viewportShape = uiRoundnessShape(),
                            ) {
                                thumbnailContent()
                            }
                        }
                    } else {
                    if (showthumbnail && (playerType == PlayerType.Essential)) {
                        PlayerLandscapeThumbnailFrame(
                            contentPadding = playerThumbnailPadding(thumbnailSizeLDp, landscape = true),
                            artworkVisible = playerCoverArtworkVisible(showthumbnail, isShowingLyrics,
                                coverOverlayPolicy.lyricsOnCover, isShowingVisualizer, coverOverlayPolicy.visualizerOnCover),
                        ) {
                            thumbnailContent()
                        }
                    }
                    if (playerType == PlayerType.Modern && (playerCoverArtworkVisible(showthumbnail, isShowingLyrics, coverOverlayPolicy.lyricsOnCover, isShowingVisualizer, coverOverlayPolicy.visualizerOnCover) || landscapeStandaloneVisualizer || landscapeModernVisualizerState.hasOverlayContent)) {
                        BoxWithConstraints(
                             contentAlignment = Alignment.Center,
                             modifier = Modifier
                                 .weight(1f)
                                 .playerArtworkOverflowViewport(artworkShadowOverflow)
                             /*modifier = Modifier
                            .weight(1f)*/
                             //.padding(vertical = 10.dp)
                         ) {
                             if (playerCoverArtworkVisible(showthumbnail, isShowingLyrics, coverOverlayPolicy.lyricsOnCover, isShowingVisualizer, coverOverlayPolicy.visualizerOnCover)) {
                                  val fling = flingBehaviorFor(pagerState, sensitivity = playerSwipeSensitivity, snapDurationMillis = CAROUSEL_SETTLE_DURATION_MS)
                                   val animatePageSpacing by animatePlayerCarouselDp(
                                       if (expandedplayer) (thumbnailSpacingL.toInt() * 0.01 * maxHeight -
                                           if (carousel) 3 * carouselSize.size.dp else 2 * carouselSize.size.dp)
                                       else 10.dp,
                                       viewportWidth = maxWidth,
                                       label = "landscapeCarouselSpacing",
                                   )
                                   val animatePadding by animatePlayerCarouselDp(
                                       if (expandedplayer) carouselSize.size.dp else playerThumbnailPadding(thumbnailSizeLDp, landscape = true),
                                       viewportWidth = maxWidth,
                                       label = "landscapeCarouselPadding",
                                   )
                                   // Animate the edge fade strength so it does not flash on expand/back.
                                   val playerCoverEdgeFade by animateFloatAsState(
                                       if (expandedplayer) thumbnailFadeEx else thumbnailFade,
                                       label = "coverEdgeFade",
                                   )
                                   val carouselGeometry = animatePlayerCarouselGeometry(
                                       width = maxWidth,
                                       height = maxHeight,
                                       padding = animatePadding,
                                       pageSpacing = animatePageSpacing,
                                       compactStack = expandedplayer && carousel,
                                   )

                                   pagerState.AttachPlayerArtworkPager(
                                       player,
                                       playerUpdateTrigger,
                                       appRunningInBackground,
                                       playerContentVisible,
                                       carouselButtons = carouselButtons,
                                       skipAnimation = isShowingLyrics,
                                   )
                                 DisposableEffect(pagerState) {
                                     carouselPagerMounted = true
                                     onDispose { carouselPagerMounted = false }
                                 }
                                 VerticalPager(
                                     state = pagerState,
                                     key = { page -> carouselPageKeys.key(queueTimeline, page) },
                                     pageSize = PageSize.Fixed(carouselGeometry.pageSize),
                                     pageSpacing = carouselGeometry.pageSpacing,
                                     contentPadding = PaddingValues(
                                         top = ((maxHeight - carouselGeometry.pageSize) / 2).coerceAtLeast(0.dp),
                                         bottom = ((maxHeight - carouselGeometry.pageSize) / 2).coerceAtLeast(0.dp),
                                     ),
                                     beyondViewportPageCount = 2,
                                     flingBehavior = fling,
                                     userScrollEnabled = expandedplayer || !disablePlayerHorizontalSwipe,
                                     modifier = Modifier
                                         .landscapeCarouselDrag(pagerState, playerSwipeSensitivity)
                                         .padding(
                                             all = (if (thumbnailType == ThumbnailType.Modern) -(10.dp) else 0.dp).coerceAtLeast(
                                                 0.dp
                                             )
                                         )
                                         .playerArtworkOverflowFade(artworkShadowOverflow, fadingedge && carousel, playerCoverEdgeFade * 0.05f)
                                         .conditional(fadingedge && !carousel) {
                                             VerticalfadingEdge2(
                                                 fade = playerCoverEdgeFade * 0.05f,
                                                 showTopActionsBar, topPadding, expandedplayer,
                                             )
                                         }
                                     ) {

                                     CarouselPage(
                                         it, carouselGeometry,
                                         spacingDialogAllowed = { true },
                                         showDragIndicator = { isDragged },
                                     )
                                 }
                             }
                            if (landscapeModernVisualizerState.hasOverlayContent) {
                                GlassOverlayHost(
                                    active = landscapeStandaloneVisualizer, settings = glassSettings,
                                    fadeDurationMillis = 500, transitionState = landscapeModernVisualizerState,
                                    modifier = Modifier
                                        .pointerInput(Unit) {
                                            detectHorizontalDragGestures(
                                                onHorizontalDrag = { _, dragAmount ->
                                                    deltaX += dragAmount
                                                },
                                                onDragStart = {
                                                    deltaX = 0f
                                                },
                                                onDragEnd = {
                                                    // Guest in a Listen Together room: the skip op reaches the central guard, which no-ops it
                                                    // and shows the throttled toast (spec-listen-together-guest-lock-hardening)
                                                    if (!disablePlayerHorizontalSwipe && playerType == PlayerType.Essential) {
                                                        if (deltaX > playerSwipeSensitivity.threshold) {
                                                            player.playPrevious()
                                                        } else if (deltaX < -playerSwipeSensitivity.threshold) {
                                                            player.playNext()
                                                        }

                                                    }

                                                }

                                            )
                                        }
                                ) {
                                    NextVisualizer(
                                        isDisplayed = landscapeStandaloneVisualizer, onCover = false, fadeHosted = true
                                    )
                                }
                            }
                        }
                    }
                    if (landscapeEssentialVisualizerState.hasOverlayContent) {
                        GlassOverlayHost(
                            active = landscapeStandaloneVisualizer, settings = glassSettings,
                            fadeDurationMillis = 500, transitionState = landscapeEssentialVisualizerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { _, dragAmount ->
                                            deltaX += dragAmount
                                        },
                                        onDragStart = {
                                            deltaX = 0f
                                        },
                                        onDragEnd = {
                                            // Guest in a Listen Together room: the skip op reaches the central guard, which no-ops it
                                            // and shows the throttled toast (spec-listen-together-guest-lock-hardening)
                                            if (!disablePlayerHorizontalSwipe && playerType == PlayerType.Essential) {
                                                if (deltaX > playerSwipeSensitivity.threshold) {
                                                    player.playPrevious()
                                                } else if (deltaX < -playerSwipeSensitivity.threshold) {
                                                    player.playNext()
                                                }

                                            }

                                        }

                                    )
                                }
                        ) {
                            NextVisualizer(
                                    isDisplayed = landscapeStandaloneVisualizer, onCover = false, fadeHosted = true
                                )
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .conditional(isShowingStandaloneLyrics) { weight(1f) }
                            .navigationBarsPadding()
                    ){
                        if (!coverOverlayPolicy.lyricsOnCover) {
                            LyricsScreen(
                                mediaId = mediaItem.mediaId,
                                isDisplayed = isShowingLyrics,
                                onDismiss = {
                                        isShowingLyrics = false
                                },
                                ensureSongInserted = { Database.insertIgnore( mediaItem ) },
                                size = 1000.dp,
                                mediaMetadataProvider = mediaItem::mediaMetadata,
                                durationProvider = player::getDuration,
                                isLandscape = isLandscape,
                                clickLyricsText = clickLyricsText,
                                onCover = false,
                                modifier = Modifier
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures(
                                            onHorizontalDrag = { _, dragAmount ->
                                                deltaX += dragAmount
                                            },
                                            onDragStart = {
                                                deltaX = 0f
                                            },
                                            onDragEnd = {
                                                // Guest in a Listen Together room: the skip op reaches the central guard, which no-ops it
                                                // and shows the throttled toast (spec-listen-together-guest-lock-hardening)
                                                if (!disablePlayerHorizontalSwipe) {
                                                    if (deltaX > playerSwipeSensitivity.threshold) {
                                                        player.playPrevious()
                                                    } else if (deltaX < -playerSwipeSensitivity.threshold) {
                                                        player.playNext()
                                                    }

                                                }

                                            }

                                        )
                                    }
                            )
                        }
                    }
                    }
                }, controls = {
                    if (activeTopSlots) {
                        TopActions(useTopPadding = false)
                    } else if (showLegacyVideoFallback && showVideoMode && toggleSession != null) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                            PlayerAudioVideoToggle(
                                toggleSession,
                                onSearchManually = openManualVideoSearch,
                                interactive = preview == null,
                            )
                        }
                    }
                    val landscapeLyricsState = rememberOverlayTransition(isShowingLyrics && !showLandscapeArtworkColumn)
                    val landscapeVisualizerState = rememberOverlayTransition(isShowingVisualizer && !showLandscapeArtworkColumn)
                    if (!showLandscapeArtworkColumn &&
                        (landscapeLyricsState.hasOverlayContent || landscapeVisualizerState.hasOverlayContent)) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            GlassOverlayHost(isShowingLyrics, glassSettings, transitionState = landscapeLyricsState) {
                                LyricsScreen(
                                    mediaId = mediaItem.mediaId,
                                    isDisplayed = isShowingLyrics,
                                    onDismiss = { isShowingLyrics = false },
                                    ensureSongInserted = { Database.insertIgnore(mediaItem) },
                                    size = 1000.dp,
                                    mediaMetadataProvider = mediaItem::mediaMetadata,
                                    durationProvider = player::getDuration,
                                    isLandscape = isLandscape,
                                    clickLyricsText = clickLyricsText,
                                    onCover = false,
                                    fadeHosted = true,
                                )
                            }
                            GlassOverlayHost(isShowingVisualizer, glassSettings, fadeDurationMillis = 500,
                                transitionState = landscapeVisualizerState) {
                                NextVisualizer(isDisplayed = isShowingVisualizer, onCover = false, fadeHosted = true)
                            }
                        }
                    }
                    val controllerItem = if (!showLandscapeArtworkColumn || isShowingLyrics || isShowingVisualizer)
                        mediaItem else controllerPreviewPage?.let(mediaItems::getOrNull) ?: mediaItem
                    Controller(
                        controllerItem,
                        Modifier.playerLandscapeControlsMargin().weight(1f),
                        isBuffering = isBuffering,
                        position = { positionAndDurationState.value.first },
                        duration = { durationState.value }
                    )
                    if (!showthumbnail || playerType == PlayerType.Modern) {
                        StatsForNerds(
                            mediaId = statsMediaId(),
                            isDisplayed = statsfornerds,
                            onDismiss = {}
                        )
                    }
                    ActionsBar()
                }
            )
         }
        } else {
           Box {
               if (showFullScreenArtworkPager) {
                     val fling = flingBehaviorFor(pagerStateFS, sensitivity = playerSwipeSensitivity)
                    val scaleAnimationFloat by animateFloatAsState(
                        if (isDraggedFS) 0.85f else 1f, label = ""
                    )
                     pagerStateFS.AttachPlayerArtworkPager(
                         player,
                         playerUpdateTrigger,
                         appRunningInBackground,
                         playerContentVisible,
                         settleDelayMillis = if (swipeAnimationNoThumbnail == SwipeAnimationNoThumbnail.Fade) 0L else 400L,
                     )
                    HorizontalPager(
                        state = pagerStateFS,
                        beyondViewportPageCount = if (swipeAnimationNoThumbnail != SwipeAnimationNoThumbnail.Circle || albumCoverRotation && (isShowingLyrics || showthumbnail)) 1 else 0,
                        flingBehavior = fling,
                        userScrollEnabled = !(albumCoverRotation && (isShowingLyrics || showthumbnail)),
                        modifier = Modifier
                            .background(colorPalette().background1)
                            .pointerInteropFilter {
                                circleOffsetY = it.y
                                false
                            }
                    ) {

                        val isSettledCover = it == pagerStateFS.settledPage
                        val rotateFullscreenCover = albumCoverRotation && (isShowingLyrics || showthumbnail)
                        val rotation = rememberCoverSpin(
                            active = decorationsActive && isSettledCover && rotateFullscreenCover && !fullCoverPresentation,
                            playing = isActuallyPlaying,
                            rotationMillis = 30_000,
                            coastDegrees = 10f,
                        )

                        Box(
                            modifier = Modifier
                                .conditional(albumCoverRotation && (isShowingLyrics || showthumbnail)) {
                                    zIndex(if (it == pagerStateFS.currentPage) 1f else 0.9f)
                                }
                                .conditional(swipeAnimationNoThumbnail == SwipeAnimationNoThumbnail.Scale && isDraggedFS) {
                                    graphicsLayer {
                                        scaleY = scaleAnimationFloat
                                        scaleX = scaleAnimationFloat
                                    }
                                }
                        ) {
                            if (fullCoverPresentation) FullCoverPagerPage() else PlayerBlurredCover(
                                thumbnailUrl = coverUrl(mediaItems.getOrNull(it)).orEmpty(),
                                fullscreenPresentationMediaId = mediaItems.getOrNull(it)?.mediaId?.takeIf { _ ->
                                    expectsForegroundCover && it == pagerStateFS.currentPage
                                },
                                publishArtworkColors = visiblePlayerArtwork && it == pagerStateFS.currentPage,
                                captureBackdrop = false,
                                allowOwnRotation = decorationsActive && isSettledCover && !rotateFullscreenCover,
                                blurAdjuster = blurAdjuster,
                                showThumbnail = showthumbnail,
                                noBlur = noblur,
                                isShowingLyrics = isShowingLyrics,
                                isShowingVisualizer = isShowingVisualizer,
                                modifier = Modifier.playerFullscreenArtworkMotion(
                                    pagerState = pagerStateFS,
                                    page = it,
                                    allowRotation = albumCoverRotation,
                                    shouldRotate = { isShowingLyrics || showthumbnail },
                                    rotation = rotation,
                                    rotatingCoverScale = (screenHeight / screenWidth) + 0.5f,
                                    swipeAnimation = swipeAnimationNoThumbnail,
                                    showThumbnail = showthumbnail,
                                    isDragged = isDraggedFS,
                                    circleOriginY = { circleOffsetY },
                                )
                                                   .clip(uiRoundnessShape())
                                                   .clip(uiRoundnessShape()).combinedClickable(
                                                       interactionSource = remember { MutableInteractionSource() },
                                                       indication = null,
                                                       onClick = {
                                                           if (thumbnailTapEnabled && !showthumbnail) {
                                                               if (isShowingVisualizer) isShowingVisualizer = false
                                                               isShowingLyrics = !isShowingLyrics
                                                           }
                                                       },
                                                       onDoubleClick = {
                                                           if (!lyricsShownOnCover && !showvisthumbnail)
                                                               showthumbnail = !showthumbnail
                                                       },
                                                       onLongClick = {
                                                           blurAdjuster.isActive = showthumbnail || (isShowingLyrics && !isShowingVisualizer) || !noblur
                                                       }
                                                   )
                            )

                            if ((swipeAnimationNoThumbnail == SwipeAnimationNoThumbnail.Scale) && isDraggedFS){
                                Column {
                                    Spacer(modifier = Modifier
                                        // Same weight-wins-on-expand rule as the portrait artwork slot.
                                        .conditional((screenWidth <= (screenHeight / 2)) && (!expandedplayer && !isShowingStandaloneLyrics)) {
                                            height(screenWidth)}
                                        .conditional((screenWidth > (screenHeight / 2)) || expandedplayer || isShowingStandaloneLyrics) {weight(1f)})

                                    Box(modifier = Modifier
                                        .conditional(!expandedplayer && !isShowingStandaloneLyrics) {weight(1f)}
                                    ) {
                                        val displayItem = mediaItems.getOrNull(it) ?: mediaItem
                                        val displayLinks = if (displayItem.mediaId == mediaItem.mediaId) currentLinks
                                            else displayItem.playerTrackLinks(linkCache)
                                        Controls(
                                            navController = navController,
                                            onCollapse = onDismiss,
                                            expandedplayer = expandedplayer,
                                            titleExpanded = titleExpanded,
                                            timelineExpanded = timelineExpanded,
                                            controlsExpanded = controlsExpanded,
                                            isShowingLyrics = isShowingLyrics,
                                            mediaItem = displayItem,
                                            links = displayLinks,
                                            shouldBePlaying = shouldBePlaying,
                                            isBuffering = isBuffering,
                                            position = { positionAndDurationState.value.first },
                                            duration = { durationState.value },
                                            modifier = Modifier
                                                .padding(vertical = 4.dp)
                                                .fillMaxWidth(),
                                            onBlurScaleChange = { blurAdjuster.strength = it },
                                            dynamicColorPalette = backgroundPalette.copy(accent = artworkAccent.value)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    PlayerFullscreenArtworkVeil(playerGradientStartY, blurredVeilEnd, blurredVeilBase)
                }

               if (!skipStandaloneCover) PlayerBlurredCover(
                   thumbnailUrl = backgroundArtwork.coverUrl.orEmpty(),
                   committedRevision = backgroundArtwork.coverRevision,
                   publishArtworkColors = visiblePlayerArtwork && !fullscreenPagerOwnsArtwork &&
                       !carouselOwnsArtwork && !thumbnailOwnsArtwork,
                   blurAdjuster = blurAdjuster,
                   showThumbnail = showthumbnail,
                   noBlur = noblur,
                   isShowingLyrics = isShowingLyrics,
                   isShowingVisualizer = isShowingVisualizer,
               )

            AnimatedPlayerBackground()
            val prioritizePortraitArtwork = playerPortraitPrioritizesArtwork(
                playerCoverArtworkVisible(showthumbnail, isShowingLyrics, coverOverlayPolicy.lyricsOnCover,
                    isShowingVisualizer, coverOverlayPolicy.visualizerOnCover), playerType)
            val weightedPortraitInformation = playerPortraitUsesWeightedInformation(
                expandedplayer, isShowingStandaloneLyrics, playerType, carousel, lyricsShownOnCover)
            PlayerGlassBudgetLayout(
                enabled = false,
                expandedCarousel = expandedplayer && carousel && playerType == PlayerType.Modern,
                reserveLowerContent = carousel && playerType == PlayerType.Modern,
                prioritizeArtwork = prioritizePortraitArtwork,
                expandInformation = weightedPortraitInformation,
                informationGap = playerPortraitInformationGap(),
                // Thumbnail's Modern style already has 8dp above and below its square.
                artworkVerticalPadding = if (playerType == PlayerType.Essential && thumbnailType == ThumbnailType.Modern)
                    16.dp else 0.dp,
                modifier = Modifier.onSizeChanged { backdropFillSize = it }
                    .then(if (capturePlayerBackground) playerBackground.background else Modifier)
                    .then(containerModifier)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    //.padding(top = 10.dp)
                    .drawBehind {
                        if (backgroundProgress == BackgroundProgress.Both || backgroundProgress == BackgroundProgress.Player) {
                            drawRect(
                                color = color.favoritesOverlay,
                                topLeft = Offset.Zero,
                                size = Size(
                                    width = positionAndDurationState.value.first.toFloat() /
                                            (durationState.value.absoluteValue.takeIf { it > 0 } ?: 1L) * size.width,
                                    height = size.maxDimension
                                )
                            )
                        }
                    }
                    .then(if (capturePlayerBackground) playerBackground.content else Modifier)
                    .then(playerForegroundClip)
                    .playerArtworkShadowOverflow(artworkShadowOverflow),


                header = {
                    TopActions(useTopPadding = topPadding)
                },
                artwork = {
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        // When expanded, weight() must win alone: with both height() and
                        // weight() applied the slot size became unpredictable and the
                        // expand/back actions felt inverted.
                        .conditional((screenWidth <= (screenHeight / 2)) && (!expandedplayer && !isShowingStandaloneLyrics)) {height(screenWidth)}
                        .conditional((screenWidth > (screenHeight / 2)) || expandedplayer || isShowingStandaloneLyrics) {weight(1f)}
                        .playerArtworkOverflowViewport(artworkShadowOverflow)
                ) {

                      if (showInlineVideo && videoSession != null) {
                          InlineMusicVideo(
                              videoSession,
                              artworkPadding = playerThumbnailPadding(thumbnailSizeDp).takeIf { showthumbnail },
                          ) {
                              thumbnailContent()
                          }
                      } else if (showthumbnail) {
                         if (playerCoverArtworkVisible(showthumbnail, isShowingLyrics, coverOverlayPolicy.lyricsOnCover, isShowingVisualizer, coverOverlayPolicy.visualizerOnCover)) {
                             if (playerType == PlayerType.Modern) {
                                   val fling = flingBehaviorFor(pagerState, sensitivity = playerSwipeSensitivity, snapDurationMillis = CAROUSEL_SETTLE_DURATION_MS)

                                   pagerState.AttachPlayerArtworkPager(
                                       player,
                                       playerUpdateTrigger,
                                       appRunningInBackground,
                                       playerContentVisible,
                                       carouselButtons = carouselButtons,
                                   )

                                 val pageSpacing = (thumbnailSpacing.toInt()*0.01*(screenHeight) - if (carousel) (3*carouselSize.size.dp) else (2*((100f - thumbnailSizeDp) * 1.5f).dp))
                                 val animatePageSpacing by animatePlayerCarouselDp(
                                     if (expandedplayer) (thumbnailSpacing.toInt()*0.01*(screenHeight) - if (carousel) (3*carouselSize.size.dp) else (2*carouselSize.size.dp)) else 10.dp,
                                      viewportWidth = maxWidth,
                                      label = "portraitCarouselSpacing",
                                 )

                                 val animatePadding by animatePlayerCarouselDp(
                                      if (expandedplayer) carouselSize.size.dp else playerThumbnailPadding(thumbnailSizeDp),
                                      viewportWidth = maxWidth,
                                      label = "portraitCarouselPadding",
                                 )
                                  // Animate the edge fade strength so it does not flash on expand/back.
                                  val playerCoverEdgeFade by animateFloatAsState(
                                      if (expandedplayer) thumbnailFadeEx else thumbnailFade,
                                      label = "coverEdgeFade",
                                  )
                                 val carouselGeometry = animatePlayerCarouselGeometry(
                                     width = maxWidth,
                                     height = maxHeight,
                                     padding = animatePadding,
                                     pageSpacing = animatePageSpacing,
                                     compactStack = expandedplayer && carousel,
                                 )
                                 if (carouselGeometry.pageSize > carouselGeometry.coverPadding * 2) {
                                 DisposableEffect(pagerState) {
                                     carouselPagerMounted = true
                                     onDispose { carouselPagerMounted = false }
                                 }
                                 VerticalPager(
                                     state = pagerState,
                                     key = { page -> carouselPageKeys.key(queueTimeline, page) },
                                     pageSize = PageSize.Fixed(carouselGeometry.pageSize),
                                     contentPadding = PaddingValues(
                                         top = ((maxHeight - carouselGeometry.pageSize)/2).coerceAtLeast(0.dp),
                                         bottom = ((maxHeight - carouselGeometry.pageSize)/2).coerceAtLeast(0.dp)
                                     ),
                                     pageSpacing = carouselGeometry.pageSpacing,
                                     beyondViewportPageCount = 2,
                                     flingBehavior = fling,
                                     userScrollEnabled = expandedplayer || !disablePlayerHorizontalSwipe,
                                     modifier = Modifier
                                         .padding(
                                             all = (if (expandedplayer) 0.dp else if (thumbnailType == ThumbnailType.Modern) -(10.dp) else 0.dp).coerceAtLeast(
                                                 0.dp
                                             )
                                         )
                                         .playerArtworkOverflowFade(artworkShadowOverflow, fadingedge && carousel, playerCoverEdgeFade * 0.05f)
                                         .conditional(fadingedge && !carousel) {
                                             VerticalfadingEdge2(fade = playerCoverEdgeFade*0.05f,showTopActionsBar,topPadding,expandedplayer)
                                         }
                                 ){

                                     CarouselPage(
                                         it, carouselGeometry,
                                         spacingDialogAllowed = { expandedplayer || fadingedge },
                                         showDragIndicator = { isDragged && expandedplayer },
                                     )
                                 }
                                 }
                             } else {
                                 thumbnailContent()
                             }
                         }
                      }

                   val standaloneOverlayModifier = Modifier
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { _, dragAmount ->
                                        deltaX += dragAmount
                                    },
                                    onDragStart = {
                                        deltaX = 0f
                                    },
                                    onDragEnd = {
                                        // Guest in a Listen Together room: the skip op reaches the central guard, which no-ops it
                                        // and shows the throttled toast (spec-listen-together-guest-lock-hardening)
                                        if (!disablePlayerHorizontalSwipe) {
                                            if (deltaX > playerSwipeSensitivity.threshold) {
                                                player.playPrevious()
                                            } else if (deltaX < -playerSwipeSensitivity.threshold){
                                                player.playNext()
                                            }

                                        }

                                    }

                                )
                            }
                   GlassOverlayHost(
                        active = isShowingStandaloneLyrics,
                        settings = glassSettings,
                        modifier = standaloneOverlayModifier,
                    ) {
                        LyricsScreen(
                            mediaId = mediaItem.mediaId,
                            isDisplayed = isShowingStandaloneLyrics,
                            onDismiss = { isShowingLyrics = false },
                            ensureSongInserted = { Database.insertIgnore(mediaItem) },
                            size = 1000.dp,
                            mediaMetadataProvider = mediaItem::mediaMetadata,
                            durationProvider = player::getDuration,
                            isLandscape = isLandscape,
                            clickLyricsText = clickLyricsText,
                            onCover = false,
                            fadeHosted = true,
                        )
                    }
                   val standaloneVisualizer = playerStandaloneVisualizerVisible(
                        showthumbnail, isShowingVisualizer, coverOverlayPolicy.visualizerOnCover)
                   GlassOverlayHost(
                        active = standaloneVisualizer,
                        settings = glassSettings,
                        modifier = standaloneOverlayModifier,
                        fadeDurationMillis = 500,
                    ) {
                        NextVisualizer(isDisplayed = standaloneVisualizer, onCover = false, fadeHosted = true)
                    }
                }

                },
                information = {
                PlayerInformationGlassBlock {
                PlayerPortraitInformationLayout(
                    artworkPrioritized = prioritizePortraitArtwork,
                    weightedControls = weightedPortraitInformation,
                    above = {
                if (!expandedplayer || !isShowingStandaloneLyrics || queueDurationExpanded) {
                    if (showTotalTimeQueue) {
                        PlayerQueueDurationInformation(totalPlayTimes, textoutline)
                    }

                    Spacer(
                        modifier = Modifier
                            .height(10.dp)
                    )
                }
                }, controls = {
                    val standaloneOverlay = isShowingStandaloneLyrics ||
                        (isShowingVisualizer && !coverOverlayPolicy.visualizerOnCover)
                    if (playerType == PlayerType.Essential || standaloneOverlay ||
                        !(swipeAnimationNoThumbnail == SwipeAnimationNoThumbnail.Scale && isDraggedFS)) {
                        val controllerItem = if (isShowingLyrics || isShowingVisualizer)
                            mediaItem else controllerPreviewPage?.let(mediaItems::getOrNull) ?: mediaItem
                        Controller(
                            controllerItem,
                            Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                            isBuffering = isBuffering,
                            position = { positionAndDurationState.value.first },
                            duration = { durationState.value }
                        )
                    }
                }, inlineStats = {
                    if ((!showthumbnail || playerType == PlayerType.Modern) &&
                        (!isShowingStandaloneLyrics || statsExpanded)
                    ) {
                        StatsForNerds(
                            mediaId = statsMediaId(),
                            isDisplayed = statsfornerds,
                            onDismiss = {},
                        )
                    }
                }, below = { ActionsBar() })
              }
                }
            )
            if (!showTopActionsBar && showLegacyVideoFallback && showVideoMode && toggleSession != null) {
                Box(
                    Modifier.align(Alignment.TopCenter)
                        .padding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues())
                        .height(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayerAudioVideoToggle(
                        toggleSession,
                        onSearchManually = openManualVideoSearch,
                        interactive = preview == null,
                    )
                }
            }
           }
        }

        }

        PlayerQueuePanel(
            navController = navController,
            showQueueState = showQueueState,
            visibleState = queuePanelVisibleState,
            fullyCoveringState = queueFullyCoveringState,
            queueLoopState = queueLoopState,
            queueType = queueType,
            backdrop = if (captureQueueUnderlay) queuePlayerBackdrop else artworkControlsSource,
            inheritedOpticsEnabled = inheritedOpticsEnabled,
            inheritedUnderlayCovered = inheritedUnderlayCovered,
            queueNavigationSurface = queueNavigationSurface,
            preview = preview != null,
            onDiscoverClick = { binder?.service?.nzikRadio?.toggleDiscover() },
            onClosePlayer = onDismiss,
        )


    }
    }
    }
}
