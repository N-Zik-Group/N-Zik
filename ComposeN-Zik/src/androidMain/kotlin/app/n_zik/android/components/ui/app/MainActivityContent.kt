package app.n_zik.android.components.ui.app

import java.util.Locale
import kotlin.math.roundToInt

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import enums.VisualizerWhiteColorOption
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

import app.n_zik.android.LocalBottomBarOffset
import app.n_zik.android.LocalDownloadHelper
import app.n_zik.android.LocalIsShowingLyrics
import app.n_zik.android.LocalIsShowingVisualizer
import app.n_zik.android.LocalPendingMiniPlayerAction
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.LocalPlayerSheetState
import app.n_zik.android.LocalTopBarOffset
import app.n_zik.android.MainActivity
import app.n_zik.android.MainActivity.Companion.action_albums
import app.n_zik.android.MainActivity.Companion.action_library
import app.n_zik.android.MainActivity.Companion.action_search
import app.n_zik.android.MainActivity.Companion.action_songs
import app.n_zik.android.MainActivity.Companion.actions_artists
import app.n_zik.android.colorPalette
import app.n_zik.android.components.CustomBottomSheet
import app.n_zik.android.components.dialog.common.triggerSamsungStorageAdvice
import app.n_zik.android.components.dialog.settings.HomeTabsSettingsDialog
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassMenuHostState
import app.n_zik.android.components.glass.GlassModalContent
import app.n_zik.android.components.glass.GlassRenderer
import app.n_zik.android.components.glass.GlassRippleProvider
import app.n_zik.android.components.glass.GlassSceneContent
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.glass.LocalPlayerArtworkObservation
import app.n_zik.android.components.settings.common.LocalSettingsFooterHeight
import app.n_zik.android.components.settings.common.LocalSettingsPlayerRestart
import app.n_zik.android.components.settings.common.LocalSettingsPlayerRestartState
import app.n_zik.android.components.settings.common.SettingsPlayerRestartState
import app.n_zik.android.components.settings.common.SettingsFooterMiniPlayerGap
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.components.glass.rememberPlayerArtworkObservation
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.navigation.AppNavigation
import app.n_zik.android.components.navigation.dock.FloatingDockDimensions
import app.n_zik.android.components.navigation.dock.LocalCompactMiniPlayerProgressProvider
import app.n_zik.android.components.navigation.dock.LocalFloatingDock
import app.n_zik.android.components.navigation.dock.classicDockGeometry
import app.n_zik.android.components.navigation.dock.floatingDockGeometry
import app.n_zik.android.components.navigation.dock.floatingMiniStandaloneInset
import app.n_zik.android.components.navigation.dock.rememberBottomNavigationImeLift
import app.n_zik.android.components.navigation.nav.GlassNavigationBarHeight
import app.n_zik.android.components.navigation.nav.GlassNavigationPlayerGap
import app.n_zik.android.components.navigation.sideNavigationOwnsHeader
import app.n_zik.android.components.onboarding.OnboardingFlow
import app.n_zik.android.components.player.APP_HEADER_HEIGHT
import app.n_zik.android.components.player.ArtworkDisplayColors
import app.n_zik.android.components.player.MINI_PLAYER_TOP_GAP
import app.n_zik.android.components.player.MiniPlayerQueueOverlay
import app.n_zik.android.components.player.PaletteFade
import app.n_zik.android.components.player.SharedArtworkColors
import app.n_zik.android.components.player.rememberScopedArtworkColorRevision
import app.n_zik.android.components.player.TOP_NAV_BAR_HEIGHT
import app.n_zik.android.components.player.miniPlayerSideInset
import app.n_zik.android.components.player.miniPlayerTopDockPaddingPx
import app.n_zik.android.components.player.miniPlayerTopInset
import app.n_zik.android.components.player.miniPlayerTopPaddingPx
import app.n_zik.android.components.player.presentMiniplayerCollapsed
import app.n_zik.android.components.player.presentMiniplayerThenExpand
import app.n_zik.android.components.player.rememberArtworkDisplayResult
import app.n_zik.android.components.player.resolvePlayerSheetMediaPresence
import app.n_zik.android.components.player.shouldComposePlayerSheet
import app.n_zik.android.components.player.showMiniplayerIfDismissed
import app.n_zik.android.components.theme.DYNAMIC_THEME_RESPECT_BRIGHTNESS_DEFAULT
import app.n_zik.android.components.theme.DynamicAppearanceOwner
import app.n_zik.android.components.theme.ProvideAnimatedAppearance
import app.n_zik.android.components.theme.ambientArtworkPaint
import app.n_zik.android.components.theme.appearanceBackground
import app.n_zik.android.components.theme.dynamicThemeRespectBrightnessKey
import app.n_zik.android.components.theme.forPaletteMode
import app.n_zik.android.components.theme.startupAppearance
import app.n_zik.android.components.ui.screens.home.HomeTopMiniPlayerAnchorState
import app.n_zik.android.components.ui.screens.home.LocalHomeTopMiniPlayerAnchor
import app.n_zik.android.components.ui.screens.home.OPEN_SEARCH_SHORTCUT
import app.n_zik.android.components.ui.screens.home.activeHomeTabIds
import app.n_zik.android.compose.persist.LocalPersistMap
import app.n_zik.android.core.coil.resize
import app.n_zik.android.core.navigation.MiniPlayerQueueInterceptor
import app.n_zik.android.core.navigation.rememberScaffoldScrollConnection
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.enums.AnimatedGradient
import app.n_zik.android.enums.AudioQualityFormat
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.HomeScreenTabs
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.core.navigation.LocalPlaylistPickerNavigation
import app.n_zik.android.components.menu.playlist.LocalLibraryPlaylistImport
import app.n_zik.android.components.menu.playlist.LocalPlaylistAddChoice
import app.n_zik.android.components.menu.playlist.PlaylistImportAddDialog
import app.n_zik.android.components.menu.playlist.PlaylistActionsRequest
import app.n_zik.android.components.menu.playlist.rememberLibraryPlaylistImportMenu
import app.n_zik.android.core.navigation.navigateToPickedPlaylist
import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.enums.PendingMiniPlayerAction
import app.n_zik.android.enums.PipModule
import app.n_zik.android.enums.PlayerControlsColors
import app.n_zik.android.enums.PlayerPosition
import app.n_zik.android.enums.lyrics.LyricsColor
import app.n_zik.android.extensions.discord.DiscordUiState
import app.n_zik.android.extensions.pip.PipModuleContainer
import app.n_zik.android.extensions.pip.PipModuleCover
import app.n_zik.android.extensions.pip.setAutoEnterPip
import app.n_zik.android.extensions.pip.supportsPictureInPicture
import app.n_zik.android.utils.enablePictureInPictureAutoKey
import app.n_zik.android.utils.enablePictureInPictureKey
import app.n_zik.android.intents.DeliverHomeTabShortcut
import app.n_zik.android.intents.DeliverRewindTargets
import app.n_zik.android.intents.DeliverSettingsShortcut
import app.n_zik.android.isMiniplayerHiddenRoute
import app.n_zik.android.listentogether.ListenTogetherGuestGuardPlayer
import app.n_zik.android.listentogether.shouldDisablePlayerSheetDismiss
import app.n_zik.android.nativeSurfaceMix
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.diagnostics.UiFrameClockDiagnostics
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.LocalShimmerClock
import app.n_zik.android.ui.components.LocalShimmerPalette
import app.n_zik.android.ui.components.rememberShimmerClock
import app.n_zik.android.ui.components.themed.CrossfadeContainer
import app.n_zik.android.ui.saveable.TolerantAppearanceStateSaver
import app.n_zik.android.ui.saveable.TolerantBoolStateSaver
import app.n_zik.android.ui.screens.player.MiniPlayer
import app.n_zik.android.ui.screens.player.Player
import app.n_zik.android.ui.screens.player.components.LocalPlayerMusicVideo
import app.n_zik.android.ui.screens.player.components.ManualVideoSearchSheet
import app.n_zik.android.ui.screens.player.components.MusicVideoPresentation
import app.n_zik.android.ui.screens.player.components.rememberPlayerMusicVideo
import app.n_zik.android.ui.screens.player.rememberPlayerSheetState
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.utils.InitDownloader
import app.n_zik.android.utils.LocalMonetCompat
import app.n_zik.android.utils.LocalPlaybackPositionClock
import app.n_zik.android.utils.PlayerAwareInsetsTracker
import app.n_zik.android.utils.animatedGradientKey
import app.n_zik.android.utils.appNavBarPresentForRoute
import app.n_zik.android.utils.audioQualityFormatKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.currentMediaItemIdAsState
import app.n_zik.android.utils.disableClosingPlayerSwipingDownKey
import app.n_zik.android.utils.enableQuickPicksPageKey
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.homeTabsOrderKey
import app.n_zik.android.utils.intent
import app.n_zik.android.utils.keepPlayerMinimizedKey
import app.n_zik.android.utils.lyricsColorKey
import app.n_zik.android.utils.pipModuleKey
import app.n_zik.android.utils.playerControlsColorsKey
import app.n_zik.android.utils.playerPositionKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.rememberPlaybackPositionClock
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.toast.trackToastObstruction
import app.n_zik.android.utils.visualizerEnabledKey
import app.n_zik.android.utils.visualizerWhiteColorOptionKey
import app.n_zik.innertube.utils.LocalePreferenceItem
import app.n_zik.innertube.utils.LocalePreferences

@Composable
@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("UnusedBoxWithConstraintsScope")
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(
    ExperimentalTextApi::class,
    ExperimentalFoundationApi::class, ExperimentalAnimationApi::class,
    ExperimentalMaterial3Api::class
)
internal fun MainActivity.MainActivityContent(launchedFromNotification: Boolean) {
    // Issue #881 (gh-881), Phase 3.1: lifecycle + first-frame probe (frozen UI after wake).
    UiFrameClockDiagnostics()
    val restartSettingsPlayer = remember(this) { this::restartSettingsPlayer }
    val settingsProfile = getActiveProfile(this)
    val settingsPlayerRestartState = rememberSaveable(settingsProfile, saver = SettingsPlayerRestartState.saver(settingsProfile)) {
        SettingsPlayerRestartState()
    }
    val selectedPaletteName by rememberPreference(colorPaletteNameKey, ColorPaletteName.Dynamic)

    val pictureInPictureEnabled by rememberPreference(enablePictureInPictureKey, false)
    val automaticPictureInPictureEnabled by rememberPreference(enablePictureInPictureAutoKey, false)
    LaunchedEffect(pictureInPictureEnabled, automaticPictureInPictureEnabled) {
        if (supportsPictureInPicture(this@MainActivityContent)) {
            runCatching {
                setAutoEnterPip(pictureInPictureEnabled && automaticPictureInPictureEnabled)
            }.onFailure { Timber.tag("PictureInPicture").w(it, "Unable to update automatic PiP") }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val isSystemInDarkTheme by rememberUpdatedState(isSystemInDarkTheme())
    val navController = rememberNavController()
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            DiscordUiState.currentRoute.value = destination.route
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }
    // Issue #881 (gh-881): tolerant saver — a positional saveable shift (foreign payload)
    // must restore to the default instead of throwing (spec M5).
    var showQueueOverlay by rememberSaveable(saver = TolerantBoolStateSaver("main.showQueueOverlay")) { mutableStateOf(false) }
    val queueInterceptor = remember(navController) {
        MiniPlayerQueueInterceptor(navController) { showQueueOverlay = true }
    }
    DisposableEffect(navController) {
        queueInterceptor.attach()
        onDispose { queueInterceptor.detach() }
    }
    // Issue #881 (gh-881): tolerant saver (spec M5).
    val pendingMiniPlayerAction = remember { mutableStateOf<PendingMiniPlayerAction?>(null) }
    // Issue #881 (gh-881): tolerant savers (spec M5).
    val isShowingLyrics = rememberSaveable(saver = TolerantBoolStateSaver("main.isShowingLyrics")) { mutableStateOf(false) }
    val isShowingVisualizer = rememberSaveable(saver = TolerantBoolStateSaver("main.isShowingVisualizer")) { mutableStateOf(false) }
    var animatedGradient by rememberPreference(animatedGradientKey, AnimatedGradient.M3EMorphingCover)


    LocalePreferences.preference =
        LocalePreferenceItem(
            hl = Locale.getDefault().language,
            gl = Locale.getDefault().country
        )

    preferences.getEnum(audioQualityFormatKey, AudioQualityFormat.Auto)

    fun computeAppearance(): Appearance = configuredAppearance(isSystemInDarkTheme)

    // The saved palette contains only accent + darkness. Reconcile it with the
    // current theme before the first frame, including after activity recreation.
    var appearance by rememberSaveable(
        saver = TolerantAppearanceStateSaver(
            fallback = ::computeAppearance,
            slot = "main.appearance",
            normalizeRestored = { restored ->
                startupAppearance(
                    restored = restored,
                    configured = computeAppearance(),
                    paletteName = preferences.getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic),
                    paletteMode = preferences.getEnum(colorPaletteModeKey, ColorPaletteMode.Dark),
                    systemIsDark = isSystemInDarkTheme,
                    respectThemeBrightness = preferences.getBoolean(
                        dynamicThemeRespectBrightnessKey,
                        DYNAMIC_THEME_RESPECT_BRIGHTNESS_DEFAULT,
                    ),
                )
            },
        ),
    ) {
        mutableStateOf(computeAppearance())
    }

    var fadeFromAppearance by remember { mutableStateOf<Appearance?>(null) }
    var dynamicArtworkColors by remember(preferences) { mutableStateOf<ArtworkDisplayColors?>(null) }
    val glassMenuHost = remember { GlassMenuHostState() }

    fun appearanceSurfaceMix(palette: ColorPalette) = nativeSurfaceMix(
        preferences.getEnum(colorPaletteModeKey, ColorPaletteMode.Dark),
        preferences.getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic),
        palette,
    )

    var surfaceMix by remember { mutableStateOf(appearanceSurfaceMix(appearance.colorPalette)) }

    fun updateAppearance(newAppearance: Appearance, animateGlobal: Boolean = false) {
        val target = newAppearance.forPaletteMode(preferences.getEnum(colorPaletteModeKey, ColorPaletteMode.Dark))
        val targetSurfaceMix = appearanceSurfaceMix(target.colorPalette)
        if (target == appearance && targetSurfaceMix == surfaceMix) return
        fadeFromAppearance = if (animateGlobal) appearance else null
        appearance = target
        surfaceMix = targetSurfaceMix
    }

    val dynamicAppearanceOwner = remember(preferences, coroutineScope) {
        DynamicAppearanceOwner(
            preferences = preferences,
            scope = coroutineScope,
            systemIsDark = { isSystemInDarkTheme },
            loadPalette = { _, _ -> null },
            loadArtwork = { url ->
                app.n_zik.android.components.player.SharedArtworkColors.load(this@MainActivityContent, url)
            },
            publishArtwork = { colors, animate ->
                if (dynamicArtworkColors != colors) {
                    if (animate && fadeFromAppearance == null) fadeFromAppearance = appearance
                    dynamicArtworkColors = colors
                }
            },
            appearance = { appearance },
            publish = { next, animate -> updateAppearance(next, animate) },
            // Compose updates the bars from the displayed page color below.
            systemBars = {},
            saveWidget = { palette ->
                savePaletteForWidget(palette)
                val service = binder
                if (service != null) service.refreshArtworkWidgets()
                else coroutineScope.launch(NzikDispatchers.DATA) {
                    app.n_zik.android.widget.NZikWidgetManager.updateIdleWidgets(this@MainActivityContent)
                }
            },
        )
    }
    DisposableEffect(dynamicAppearanceOwner) {
        dynamicAppearanceOwner.start()
        onDispose { dynamicAppearanceOwner.close() }
    }

    val playerUpdateTrigger by binder?.playerUpdateTrigger?.collectAsStateWithLifecycle(0) ?: remember { mutableStateOf(0) }
    val playerArtwork = rememberPlayerArtworkObservation(binder?.player, playerUpdateTrigger)
    val latestPlayerArtwork by rememberUpdatedState(playerArtwork)
    fun setDynamicPalette(
        player: Player?,
        animateTheme: Boolean = false,
        shouldAnimate: () -> Boolean = { animateTheme },
    ) {
        val observation = latestPlayerArtwork
        if (observation.player !== player) return
        val artwork = observation.state.value
        val artworkUri = artwork?.uri
        dynamicAppearanceOwner.refresh(artworkUri, animateTheme,
            awaitArtworkMetadata = artwork?.mediaId != null, shouldAnimate = shouldAnimate)
    }

    val artworkColorGeneration by SharedArtworkColors.generation.collectAsStateWithLifecycle(context = NzikDispatchers.DATA)
    val observedArtwork = playerArtwork.state.value
    val artworkProfile = ImageCacheFactory.rememberArtworkProfile()
    val sourceRevision = ImageCacheFactory.rememberArtworkRevision(observedArtwork?.uri, artworkProfile)
    val colorRevision = rememberScopedArtworkColorRevision(observedArtwork?.uri, artworkProfile)
    val artworkRevisions = Triple(artworkColorGeneration, sourceRevision, colorRevision)
    var requestedArtworkRevisions by remember(playerArtwork, observedArtwork, artworkProfile) {
        mutableStateOf(artworkRevisions)
    }
    LaunchedEffect(playerArtwork, observedArtwork, artworkRevisions) {
        val player = playerArtwork.player ?: return@LaunchedEffect
        if (requestedArtworkRevisions == artworkRevisions) return@LaunchedEffect
        requestedArtworkRevisions = artworkRevisions
        setDynamicPalette(player, animateTheme = true)
    }

    // Issue #881 (gh-881): tolerant saver (spec M5).
    var hasInitializedAppearance by rememberSaveable(saver = TolerantBoolStateSaver("main.hasInitializedAppearance")) { mutableStateOf(false) }

    LaunchedEffect(isSystemInDarkTheme) {
        if (!hasInitializedAppearance) {
            hasInitializedAppearance = true
            // The binder may already have extracted/restored the dynamic accent.
            // Do not replace it with the startup fallback just because this effect runs later.
            val paletteName = preferences.getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic)
            updateAppearance(if (paletteName == ColorPaletteName.Dynamic) appearance else computeAppearance())
            return@LaunchedEffect
        }
        val colorPaletteName = preferences.getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic)
        if (colorPaletteName == ColorPaletteName.Dynamic) {
            binder?.player?.let { player ->
                setDynamicPalette(player, animateTheme = true)
            }
        } else {
            val computed = computeAppearance()
            if (appearance.colorPalette != computed.colorPalette) {
                updateAppearance(computed, animateGlobal = true)
            }
        }
    }

    DisposableEffect(binder) {

        val listener = mainActivityPreferenceChanges(
            coroutineScope = coroutineScope,
            systemIsDark = { isSystemInDarkTheme },
            appearance = { appearance },
            setAppearance = { appearance = it },
            updateAppearance = ::updateAppearance,
            refreshDynamicPalette = { setDynamicPalette(binder?.player, animateTheme = true) },
            cancelDynamicAppearance = dynamicAppearanceOwner::cancel,
        )

        with(preferences) {
            registerOnSharedPreferenceChangeListener(listener)

            val colorPaletteName =
                getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic)
            if (colorPaletteName == ColorPaletteName.Dynamic) {
                // A disconnected service has unknown artwork, not an empty queue.
                binder?.player?.let { player ->
                    setDynamicPalette(player)
                }
            }

            onDispose {
                        unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }

    val shimmerClock = rememberShimmerClock()

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Phone in landscape on the Songs/Album/Artist/Library tabs: app header and nav bar
    // are hidden until the toggle button reveals them, so scroll-hide does not apply there
    val dockNavigationPosition = NavigationBarPosition.current()
    val hasSideNavigation = sideNavigationOwnsHeader(dockNavigationPosition)
    val isLandscapeBarless = app.n_zik.android.utils.isLandscapeBarlessScreen() && !hasSideNavigation
    val areBarsHidden = app.n_zik.android.utils.hideBarsInLandscapeMobile() && !hasSideNavigation

    // Every time such a screen is entered (or left) the bars start put away again
    LaunchedEffect(isLandscapeBarless) {
        app.n_zik.android.utils.LandscapeBars.hide()
    }
    // Top navigation remains pinned while the header scrolls out.
    val topNavHeight = if (rememberGlassSettings().usesGlassMaterial(GlassArea.Nav))
        GlassNavigationBarHeight else TOP_NAV_BAR_HEIGHT
    val topNavBarPx = if (NavigationBarPosition.Top.isCurrent()) {
        with(LocalDensity.current) { topNavHeight.roundToPx() }
    } else 0

    val isPlayerSheetExpanded = remember { mutableStateOf(false) }
    val safeDrawingInsets = WindowInsets.safeDrawing
    val dockMediaIdState = binder?.player?.currentMediaItemIdAsState()
        ?: remember { mutableStateOf<String?>(null) }
    val dockMediaId by dockMediaIdState
    val scaffoldScroll = rememberScaffoldScrollConnection(
        navController = navController,
        coroutineScope = coroutineScope,
        isLandscapeBarless = isLandscapeBarless,
        isLandscape = isLandscape,
        safeDrawingInsets = safeDrawingInsets,
        dockNavigationPosition = dockNavigationPosition,
        areBarsHidden = areBarsHidden,
        dockMediaIdState = dockMediaIdState,
        isPlayerSheetExpanded = isPlayerSheetExpanded,
        showQueueOverlay = showQueueOverlay,
        glassMenuHost = glassMenuHost,
    )
    val topBarOffsetState = scaffoldScroll.bars.topOffset
    val bottomBarOffsetState = scaffoldScroll.bars.bottomOffset
    var topBarOffset by topBarOffsetState
    var bottomBarOffset by bottomBarOffsetState
    val offsetAnimationJob = scaffoldScroll.bars.animationJob
    val floatingDock = scaffoldScroll.dock
    val floatingDockEnabled = floatingDock.enabled
    val floatingDockProgress = floatingDock.progress
    val classicDock = dockNavigationPosition == NavigationBarPosition.Bottom
    val topDock = dockNavigationPosition == NavigationBarPosition.Top

    // The player would keep the hidden offset because its scroll guard blocks reveal.
    fun restoreHiddenBars() {
        if (topBarOffset == 0f && bottomBarOffset == 0f) return
        scaffoldScroll.bars.visible.value = true
        offsetAnimationJob.value?.cancel()
        offsetAnimationJob.value = coroutineScope.launch {
            // Match the player's auto-expansion delay.
            launch { androidx.compose.animation.core.Animatable(topBarOffset).animateTo(0f, androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { topBarOffset = value } }
            launch { androidx.compose.animation.core.Animatable(bottomBarOffset).animateTo(0f, androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { bottomBarOffset = value } }
        }
        Timber.tag("MainActivity").d("restoreHiddenBars: restoring hidden bars before player auto-launch")
    }

    val ambientCoverControls by rememberPreference(playerControlsColorsKey, PlayerControlsColors.Monochrome)
    val ambientLyricsColor by rememberPreference(lyricsColorKey, LyricsColor.White)
    val ambientVisualizerEnabled by rememberPreference(visualizerEnabledKey, false)
    val ambientVisualizerColor by rememberPreference(visualizerWhiteColorOptionKey, VisualizerWhiteColorOption.White)
    val usesDynamicArtwork = selectedPaletteName == ColorPaletteName.Dynamic
    val needsFixedArtwork = !usesDynamicArtwork && (rememberGlassSettings().enabled ||
        glassMenuHost.standaloneSettings || ambientCoverControls == PlayerControlsColors.Cover ||
        ambientLyricsColor == LyricsColor.Cover ||
        ambientVisualizerEnabled && ambientVisualizerColor == VisualizerWhiteColorOption.Cover)
    var fixedArtworkColors by remember(preferences) { mutableStateOf<ArtworkDisplayColors?>(null) }
    if (needsFixedArtwork) {
        val artwork = playerArtwork.state.value
        val result = rememberArtworkDisplayResult(artwork?.uri, appearance.colorPalette.isDark,
            awaitArtworkMetadata = artwork?.mediaId != null)
        if (result.resolved) SideEffect {
            if (fixedArtworkColors != result.colors) {
                if (!isPlayerSheetExpanded.value && fadeFromAppearance == null) fadeFromAppearance = appearance
                fixedArtworkColors = result.colors
            }
        }
    }
    val artworkColors = if (usesDynamicArtwork) dynamicArtworkColors else fixedArtworkColors.takeIf { needsFixedArtwork }

    ProvideAnimatedAppearance(
        target = appearance,
        fadeFrom = fadeFromAppearance,
        onFadeComplete = { fadeFromAppearance = null },
        targetNativeSurfaceMix = surfaceMix,
        targetAmbientArtwork = ambientArtworkPaint(artworkColors, appearance.colorPalette.accent),
    ) {
        PageSystemBarAppearance(glassMenuHost)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .appearanceBackground()
                .nestedScroll(scaffoldScroll.connection)
        ) {


        val density = LocalDensity.current
        val windowsInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        val bottomDp = with(density) { windowsInsets.getBottom(density).toDp() }

        // Calculate player padding before initializing playerSheetState
        val isFloatingNavBar = NavigationBarPosition.BottomFloating.isCurrent()
        val isIconOnlyNav = app.n_zik.android.enums.NavigationBarType.IconOnly.isCurrent()
        val navBarBottomPad = Dimensions.navBarBottomPadding(isFloatingNavBar)
        val bottomNavigationImeLift = rememberBottomNavigationImeLift(dockNavigationPosition)
        // Route-aware: only screens that actually render the app nav bar reserve its
        // height under the mini-player, so it drops to the screen edge on bar-less pages.
        // Sourced from the nav controller rather than DiscordUiState so layout never
        // depends on the RPC feature's destination listener
        val playerEntry = navController.currentBackStackEntryAsState().value
        val settingsFooterHeights = remember { mutableStateMapOf<String, Int>() }
        val reportSettingsFooterHeight = remember(settingsFooterHeights) {
            { entryId: String, heightPx: Int ->
                if (heightPx > 0) settingsFooterHeights[entryId] = heightPx
                else settingsFooterHeights.remove(entryId)
                Unit
            }
        }
        val playerRoute = playerEntry?.destination?.route
        // Home draws one nav bar button per active tab and only shows the bar with two
        // or more, so pass the real tab count: the same preferences HomeScreen reads,
        // kept reactive so a tab toggled in settings moves the mini-player live.
        val quickPicksTabEnabled by rememberPreference(enableQuickPicksPageKey, true)
        val songsTabEnabled by rememberPreference("hometab_songs_enabled", true)
        val artistsTabEnabled by rememberPreference("hometab_artists_enabled", true)
        val albumsTabEnabled by rememberPreference("hometab_albums_enabled", true)
        val playlistsTabEnabled by rememberPreference("hometab_playlists_enabled", true)
        val homeTabsOrderSerialized by rememberPreference(homeTabsOrderKey, "")
        val activeHomeTabCount = remember(homeTabsOrderSerialized, quickPicksTabEnabled, songsTabEnabled, artistsTabEnabled, albumsTabEnabled, playlistsTabEnabled) {
            activeHomeTabIds(
                HomeTabsSettingsDialog.parseOrder(homeTabsOrderSerialized),
                quickPicksTabEnabled,
                songsTabEnabled,
                artistsTabEnabled,
                albumsTabEnabled,
                playlistsTabEnabled,
            ).size
        }
        val hasNavBar = !areBarsHidden &&
            (floatingDock.state.registeredPresence(floatingDock.pageKey)
                ?: appNavBarPresentForRoute(
                    playerRoute,
                    activeHomeTabCount,
                    destinationId = if (playerRoute == "${NavRoutes.artist.name}/{id}") {
                        playerEntry?.arguments?.getString("id")
                    } else null,
                ))
        val pageDock = floatingDock.copy(pagePresence = hasNavBar)
        val dockPresence = androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (hasNavBar) 1f else 0f,
            animationSpec = tween(320, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)),
            label = "miniDockPresence",
        )

        val playerGlassSettings = rememberGlassSettings()
        val glassNavigation = playerGlassSettings.usesGlassMaterial(GlassArea.Nav)
        val hasSideNavBarVisible = sideNavigationOwnsHeader(dockNavigationPosition)
        scaffoldScroll.TrackSideNavigationHeader(hasSideNavBarVisible)
        val appHeaderHeight = if (hasSideNavBarVisible) 0.dp else APP_HEADER_HEIGHT
        val playerPos by rememberPreference(playerPositionKey, PlayerPosition.Bottom)
        val targetPlayerPadBottom = if (playerPos == PlayerPosition.Bottom) {
            if (isFloatingNavBar) {
                if (hasNavBar) {
                    val barHeight = if (glassNavigation) GlassNavigationBarHeight
                        else if (isIconOnlyNav) Dimensions.floatingNavBarIconOnlyHeight else Dimensions.floatingNavBarHeight
                    barHeight + navBarBottomPad + if (glassNavigation) GlassNavigationPlayerGap else 4.dp
                } else {
                    navBarBottomPad
                }
            } else {
                if (hasNavBar && NavigationBarPosition.Bottom.isCurrent()) {
                    if (glassNavigation) GlassNavigationBarHeight + navBarBottomPad + GlassNavigationPlayerGap
                    else Dimensions.standardNavBarHeight + navBarBottomPad + 4.dp
                } else {
                    navBarBottomPad + if (isLandscape) 16.dp else 5.dp
                }
            }
        } else 5.dp
        // CustomBottomSheet animates the complete anchor, including top/bottom switches.
        val playerPadBottom = rememberUpdatedState(targetPlayerPadBottom)

        // Top anchor: the mini-player sits under the app header (and the top nav bar).
        // The provider is read at draw time so the scroll-hide offset moves it without
        // recomposing this screen on every frame.
        val isTopPlayer = playerPos == PlayerPosition.Top
        val settingsFooterHeight = playerEntry?.id?.let { settingsFooterHeights[it] }
        val settingsFooterClearance = if (!isTopPlayer && settingsFooterHeight != null) {
            with(density) { settingsFooterHeight.toDp() } + SettingsFooterMiniPlayerGap
        } else 0.dp
        val dockExpandedNavHeight = FloatingDockDimensions.expandedHeight.dp
        val dockSafeWidth = maxWidth - with(density) {
            (safeDrawingInsets.getLeft(density, LayoutDirection.Ltr) +
                safeDrawingInsets.getRight(density, LayoutDirection.Ltr)).toDp()
        }
        val dockExpandedMiniHeight = FloatingDockDimensions.expandedMiniHeight.dp
        val classicNavHeight = if (glassNavigation) GlassNavigationBarHeight else Dimensions.navigationBarHeight - 10.dp
        val dockGeometry = {
            if (classicDock || topDock) classicDockGeometry(dockSafeWidth.value,
                if (topDock) with(density) { topNavBarPx.toDp().value } else classicNavHeight.value,
                dockExpandedMiniHeight.value, floatingDock.hasAttachedMini, floatingDockProgress.value,
                expandedGap = if (glassNavigation) GlassNavigationPlayerGap.value else FloatingDockDimensions.componentGap)
            else floatingDockGeometry(dockSafeWidth.value, dockExpandedNavHeight.value,
                dockExpandedMiniHeight.value, floatingDock.hasBottomMini, floatingDockProgress.value,
                hasSearch = false)
        }
        val statusBarTopPx = safeDrawingInsets.getTop(density)
        val targetPlayerTopInsetPx = with(density) {
            miniPlayerTopInset(
                statusBarTop = statusBarTopPx.toDp(),
                barsHidden = areBarsHidden,
                hasTopNavBar = NavigationBarPosition.Top.isCurrent() && hasNavBar,
                headerHeight = appHeaderHeight,
                topNavBarHeight = topNavBarPx.toDp(),
                gap = if (hasSideNavBarVisible) 0.dp else app.n_zik.android.components.player.MINI_PLAYER_TOP_GAP,
            ).toPx()
        }
        val playerTopInsetPx = rememberUpdatedState(targetPlayerTopInsetPx)
        val collapsedPlayerHeight = Dimensions.collapsedPlayer
        // Side system bars (status bar at the left, nav bar at the right in landscape,
        // cutout) and a navigation rail on a side: the collapsed mini-player narrows to
        // stay clear of them. Both are put away with the other bars on the bar-less
        // landscape screens.
        val railWidth = if (areBarsHidden) 0.dp else Dimensions.navigationRailWidth
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val leftSafeInset = with(density) { safeDrawingInsets.getLeft(density, LayoutDirection.Ltr).toDp() }
        val rightSafeInset = with(density) { safeDrawingInsets.getRight(density, LayoutDirection.Ltr).toDp() }
        val playerSafeStartInset = if (isRtl) rightSafeInset else leftSafeInset
        val playerSafeEndInset = if (isRtl) leftSafeInset else rightSafeInset
        val playerStartInset = miniPlayerSideInset(
            railWidth = if ((if (isRtl) NavigationBarPosition.Right else NavigationBarPosition.Left).isCurrent()) railWidth else 0.dp,
            safeInset = playerSafeStartInset,
        )
        val playerEndInset = miniPlayerSideInset(
            railWidth = if ((if (isRtl) NavigationBarPosition.Left else NavigationBarPosition.Right).isCurrent()) railWidth else 0.dp,
            safeInset = playerSafeEndInset,
        )
        // Only the header leaves; the top navigation row remains below the status bar.
        val headerHideRangePx = with(density) { appHeaderHeight.toPx() }
        val retainTopMiniBelowNavigation = !areBarsHidden && NavigationBarPosition.Top.isCurrent() && hasNavBar
        val basePlayerTopPadding: (() -> Dp)? = if (isTopPlayer) {
            {
                with(density) {
                    if (retainTopMiniBelowNavigation && floatingDock.hasAttachedMini) {
                        miniPlayerTopDockPaddingPx(
                            insetPx = playerTopInsetPx.value,
                            scrollOffsetPx = topBarOffsetState.value,
                            headerHeightPx = headerHideRangePx,
                            navHeightPx = topNavBarPx.toFloat(),
                            miniHeightPx = dockGeometry().miniHeight.dp.toPx(),
                            gapPx = MINI_PLAYER_TOP_GAP.toPx(),
                            fold = floatingDockProgress.value * dockPresence.value,
                        ).toDp()
                    } else miniPlayerTopPaddingPx(
                        insetPx = playerTopInsetPx.value,
                        scrollOffsetPx = topBarOffsetState.value,
                        hideRangePx = headerHideRangePx,
                        collapsedHeightPx = collapsedPlayerHeight.toPx(),
                        retainedTopInsetPx = (playerTopInsetPx.value - headerHideRangePx)
                            .coerceAtLeast(statusBarTopPx.toFloat()),
                        barsHidden = areBarsHidden,
                    ).toDp()
                }
            }
        } else null

        val homeTopMiniBase = rememberUpdatedState(
            if (dockMediaId != null) basePlayerTopPadding else null
        )
        val homeTopMiniGap = rememberUpdatedState(
            if (areBarsHidden || hasSideNavBarVisible) 0.dp else MINI_PLAYER_TOP_GAP
        )
        val miniBeforeHomeHeaderHeight = rememberUpdatedState<(() -> Dp)?>(
            if (dockMediaId != null && app.n_zik.android.components.ui.screens.home.miniPrecedesHomeHeader(
                    retainTopMiniBelowNavigation, areBarsHidden,
                )
            ) {
                { dockGeometry().miniHeight.dp }
            } else null
        )
        // Read the fold clock where the header uses the height, outside the root composition.
        val miniBeforeHomeHeader = remember {
            derivedStateOf { miniBeforeHomeHeaderHeight.value?.invoke() }
        }
        val homeTopMiniAnchor = remember {
            HomeTopMiniPlayerAnchorState(homeTopMiniBase, homeTopMiniGap, miniBeforeHomeHeader)
        }
        val isHomeDestination = playerEntry?.destination?.route?.startsWith(NavRoutes.home.name) == true
        val homeMiniPlacementKey by remember(homeTopMiniAnchor) {
            derivedStateOf { homeTopMiniAnchor.placementKey() }
        }
        val sideHomeMiniGap = if (hasSideNavBarVisible) MINI_PLAYER_TOP_GAP else 0.dp
        val homeMiniTopInset = remember(homeTopMiniAnchor, homeMiniPlacementKey, sideHomeMiniGap) {
            homeTopMiniAnchor.miniTopInsetProvider(panelGap = sideHomeMiniGap)
        }
        val playerTopPadding: (() -> Dp)? = basePlayerTopPadding?.let { base ->
            {
                if (isHomeDestination && !retainTopMiniBelowNavigation && !areBarsHidden)
                    homeMiniTopInset() ?: base()
                else base()
            }
        }
        val playerPlacementKey = listOf(
            playerEntry?.id, playerPos, dockNavigationPosition, isLandscape,
            areBarsHidden, hasNavBar, glassNavigation,
            playerGlassSettings.usesGlassMaterial(GlassArea.MiniPlayer), maxWidth, maxHeight,
            targetPlayerPadBottom, targetPlayerTopInsetPx,
            homeMiniPlacementKey.takeIf {
                isHomeDestination && isTopPlayer && !retainTopMiniBelowNavigation && !areBarsHidden
            },
        )

        val playerSheetState = rememberPlayerSheetState(
            dismissedBound = 0.dp,
            collapsedBound = Dimensions.collapsedPlayer + bottomDp,
            expandedBound = maxHeight,
        )

        // Keep the scroll-hide guard in sync with the sheet's real on-screen state.
        LaunchedEffect(playerSheetState) {
            snapshotFlow { playerSheetState.progress > 0.01f }
                .distinctUntilChanged()
                .collect { isPlayerSheetExpanded.value = it }
        }

        // NOT keyed on playerSheetState.value: that changes every drag frame,
        // which used to recreate the derived state and re-provide
        // LocalPlayerAwareWindowInsets per frame, recomposing every screen
        // that consumes the insets while the mini-player is dragged.
        // The tracker returns the same WindowInsets instance while the
        // clamped bottom is stable, so no consumer is invalidated per frame.
        val playerAwareWindowInsets by remember(bottomDp, playerSheetState.collapsedBound) {
            val insetsTracker = PlayerAwareInsetsTracker(
                baseInsets = windowsInsets.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                ),
                lowerBound = bottomDp,
                upperBound = playerSheetState.collapsedBound,
            )
            derivedStateOf { insetsTracker.resolve(playerSheetState.value) }
        }

        val openTabFromShortcut = when (shortcutIntentAction) {
            action_songs -> HomeScreenTabs.Songs.index
            action_albums -> HomeScreenTabs.Albums.index
            actions_artists -> HomeScreenTabs.Artists.index
            action_library -> HomeScreenTabs.Playlists.index
            action_search -> OPEN_SEARCH_SHORTCUT
            else -> -1
        }
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        DeliverHomeTabShortcut(navController, shortcutIntentAction, openTabFromShortcut, libraryIntentDelivery, currentRoute) {
            shortcutIntentAction = null
        }
        DeliverRewindTargets(
            lifecycle = lifecycle,
            navController = navController,
            deck = rewindDeckTarget,
            playlist = rewindPlaylistTarget,
            delivery = libraryIntentDelivery,
            navigationReady = onboardingStep == null,
            onDeckDelivered = { rewindDeckTarget = null },
            onPlaylistDelivered = { rewindPlaylistTarget = null },
        )

        // "Now playing" notification tap — WARM start (spec-notification-click-opens-player):
        // present the mini-player (if the sheet is dismissed) then deploy the full
        // player, restoring the hidden bars first — the same pacing as the existing
        // launchedFromNotification path (presentMiniplayerThenExpand). Consumed from an
        // effect, like the rewind targets above, and gated on onboarding the same way:
        // while onboarding is up the NavHost is not composed, so the target is held
        // until the flow completes and the effect re-runs. The token was already
        // consumed off the intent in onNewIntent (one-shot contract).
        LaunchedEffect(openPlayerFromNotificationWarm, libraryIntentDelivery, onboardingStep == null) {
            if (onboardingStep != null) return@LaunchedEffect
            if (openPlayerFromNotificationWarm) {
                openPlayerFromNotificationWarm = false
                // Anti-flicker: a sheet already above the expanded bound is left alone
                // (a tap while the full player is open must not cause a visible
                // collapse→expand).
                if (playerSheetState.value < playerSheetState.expandedBound) {
                    // On the composable's rememberCoroutineScope(), NOT this effect's
                    // scope: the flag reset above changes the effect key → recomposition
                    // cancels the effect scope, which would kill the delayed
                    // launch { delay(800); expandSoft() } child before it runs
                    // (device bug: the mini bar appeared, the expansion never did).
                    coroutineScope.presentMiniplayerThenExpand(playerSheetState, onPresent = { restoreHiddenBars() })
                }
            }
        }

        DeliverSettingsShortcut(
            lifecycle, navController, NavRoutes.profiles, openProfilesShortcut, libraryIntentDelivery, onboardingStep == null,
        ) { openProfilesShortcut = false }
        DeliverSettingsShortcut(
            lifecycle, navController, NavRoutes.updater, openUpdaterDebugShortcut, libraryIntentDelivery, onboardingStep == null,
        ) { openUpdaterDebugShortcut = false }

        // One-time Samsung storage advice (spec-samsung-storage-advice-dialog):
        // the effect re-runs only when its key (onboardingStep == null) changes;
        // the Samsung/shown/location gate is evaluated inside
        // triggerSamsungStorageAdvice (pure Samsung check before any I/O), and the
        // shown flag is persisted when the dialog is shown, so a re-run is a no-op
        // and keeping system storage never re-triggers it.
        LaunchedEffect(onboardingStep == null) {
            if (onboardingStep != null) return@LaunchedEffect
            triggerSamsungStorageAdvice(applicationContext)
        }

                val offlineMode by OfflineMode.rememberEnabled()
                val positionClock = binder?.takeUnless { pipState.value }?.let { service ->
                    val generation by service.playerUpdateTrigger.collectAsStateWithLifecycle()
                    service.player.rememberPlaybackPositionClock(generation)
                }
                val videoSession = rememberPlayerMusicVideo(
                    audio = binder?.player,
                    collapsed = playerSheetState.isCollapsed || playerSheetState.isDismissed,
                    inPip = pipState.value,
                    enabled = !offlineMode,
                )
                Box(Modifier.fillMaxSize()) {
                CrossfadeContainer(state = pipState.value) { isCurrentInPip ->
                    Timber.tag("MainActivity").d("pipState ${pipState.value} CrossfadeContainer isCurrentInPip $isCurrentInPip ")
                    val pipModule by rememberPreference(pipModuleKey, PipModule.Cover)
            if (isCurrentInPip && videoSession.selected) {
                MusicVideoPresentation(videoSession, binder?.player, inPip = true)
            } else if (isCurrentInPip) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                ) {
                    when (pipModule) {
                        PipModule.Cover -> {
                            PipModuleContainer {
                                PipModuleCover(
                                    url = binder?.player?.currentMediaItem?.mediaMetadata?.artworkUri
                                        ?.toString()
                                        ?.resize(1000, 1000)
                                        .orEmpty()
                                )
                            }
                        }

                    }

                }

            } else {
                    var playlistActions by remember { mutableStateOf<PlaylistActionsRequest?>(null) }
                    CompositionLocalProvider(LocalGlassMenuHost provides glassMenuHost) {
                    val queueSmartShuffle = app.n_zik.android.components.ui.screens.player.rememberQueueSmartShuffle(binder)
                    val importMenu = rememberLibraryPlaylistImportMenu(offlineMode)
                    CompositionLocalProvider(
                    LocalLibraryPlaylistImport provides importMenu,
                    app.n_zik.android.components.ui.screens.player.LocalQueueSmartShuffle provides queueSmartShuffle,
                    LocalPlaylistAddChoice provides { playlistActions = it },
                    LocalIndication provides ripple(bounded = true),
                    app.n_zik.android.components.glass.LocalGlassPlayerOverlayActive provides
                        (!playerSheetState.isDismissed && !playerSheetState.isCollapsed),
                    LocalRippleConfiguration provides LocalAppearance.current.colorPalette.text.let { color ->
                        remember(color) { RippleConfiguration(color = color) }
                    },
                    LocalShimmerClock provides shimmerClock,
                    LocalShimmerPalette provides LocalAppearance.current.colorPalette,
                    LocalPlayerServiceBinder provides binder,
                    LocalSettingsPlayerRestart provides restartSettingsPlayer,
                    LocalSettingsPlayerRestartState provides settingsPlayerRestartState,
                    LocalPlaybackPositionClock provides positionClock,
                    LocalPlayerArtworkObservation provides playerArtwork,
                    LocalPlayerMusicVideo provides videoSession.takeUnless { pipState.value },
                    LocalFloatingDock provides pageDock,
                    app.n_zik.android.components.navigation.LocalSideRailScrollOrigin provides scaffoldScroll.sideRailScrollOrigin,
                    LocalHomeTopMiniPlayerAnchor provides homeTopMiniAnchor,
                    app.n_zik.android.components.ui.screens.home.LocalHomeHeaderMotionRegistry provides scaffoldScroll.homeHeaderMotionRegistry,
                    LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                    LocalSettingsFooterHeight provides reportSettingsFooterHeight,
                    LocalDownloadHelper provides downloadHelper,
                    LocalPlayerSheetState provides playerSheetState,
                    LocalMonetCompat provides monet,
                    LocalPersistMap provides persistMap,
                    LocalPendingMiniPlayerAction provides pendingMiniPlayerAction,
                    app.n_zik.android.core.navigation.LocalMiniPlayerQueueRequest provides
                        { showQueueOverlay = true },
                    LocalPlaylistPickerNavigation provides { id, onDevice ->
                        navigateToPickedPlaylist(navController, id, onDevice)
                    },
                    LocalIsShowingLyrics provides isShowingLyrics,
                    LocalIsShowingVisualizer provides isShowingVisualizer,
                    LocalTopBarOffset provides topBarOffsetState,
                    LocalBottomBarOffset provides bottomBarOffsetState,
                    app.n_zik.android.core.navigation.LocalRevealScaffold provides scaffoldScroll.revealOnNavigation
                    //LocalInternetConnected provides internetConnected
                ) {
                    GlassRippleProvider {
                    val sceneGlassSettings = rememberGlassSettings()
                    var toastNeedsLiveBackdrop by remember { mutableStateOf(false) }
                    var miniQueueMounted by remember { mutableStateOf(false) }
                    val miniQueueVisible = showQueueOverlay || miniQueueMounted
                    val sheetPlayer = binder?.player
                    val currentMediaId = sheetPlayer?.currentMediaItemIdAsState(key1 = sheetPlayer)?.value
                    GlassSceneContent(glassMenuHost,
                        enabled = sceneGlassSettings.enabled && glassRenderer(sceneGlassSettings) != GlassRenderer.Tint,
                        needsLiveBackdrop = toastNeedsLiveBackdrop || miniQueueVisible,
                        refreshKey = listOf(
                            currentMediaId, appearance.colorPalette,
                            navController.currentBackStackEntryAsState().value?.id,
                            playerSheetState.isDismissed, playerSheetState.isCollapsed,
                            playerSheetState.isExpanded,
                            isShowingLyrics.value, isShowingVisualizer.value,
                        ),
                        modifier = Modifier.appearanceBackground()) {
                    GlassModalContent(glassMenuHost,
                        focusSessionKey = navController.currentBackStackEntryAsState().value?.id) {
                    app.n_zik.android.components.player.MiniPlayerQueueScene(queueVisible = miniQueueVisible, scene = {
                    OnboardingFlow(onboardingStep, onAdvance = ::advanceOnboarding) {
                        app.n_zik.android.components.player.PlayerCoveredPage(
                            covered = playerSheetState.isExpanded,
                            reduceGlassEffects = playerSheetState.isAboveCollapsed,
                        ) {
                            AppNavigation(
                                navController = navController,
                                miniPlayer = {},
                                hasTopNavBarVisible = NavigationBarPosition.Top.isCurrent() && hasNavBar,
                                hasSideNavBarVisible = hasSideNavBarVisible,
                                hasPageNavBar = hasNavBar,
                                openTabFromShortcut = openTabFromShortcut.takeIf {
                                    currentRoute?.startsWith(NavRoutes.home.name) == true
                                } ?: -1,
                                shortcutDelivery = libraryIntentDelivery,
                                onShortcutConsumed = { delivery ->
                                    if (libraryIntentDelivery == delivery) shortcutIntentAction = null
                                },
                            )
                        }
                    }

                    val disableClosingPlayerSwipingDown by rememberPreference(disableClosingPlayerSwipingDownKey, false)
                    // Listen Together guest lock (spec-listen-together-guest-lock-hardening,
                    // matrix row MINIPLAYER_DISMISS_GUEST): the dismiss-swipe's onDismiss is
                    // destructive for the room — it clears the queue synced from the host,
                    // stops the radio and stops the player service. While a guest is locked,
                    // the gesture itself is disabled, so the room cannot be "cleaned" from
                    // a guest device.
                    val ltGuestLocked by app.n_zik.android.listentogether.listenTogetherGuestLock
                    checkIfAppIsRunningInBackground()

                    var mediaPresent by remember {
                        mutableStateOf(sheetPlayer?.currentMediaItem != null)
                    }

                    LaunchedEffect(sheetPlayer, currentMediaId, playerSheetState, settingsPlayerRestartInProgress) {
                        if (settingsPlayerRestartInProgress) return@LaunchedEffect
                        val presence = resolvePlayerSheetMediaPresence {
                            binder?.player?.takeIf { it === sheetPlayer }?.let { it.currentMediaItem != null }
                        } ?: return@LaunchedEffect
                        mediaPresent = presence
                        if (presence) {
                            playerSheetState.showMiniplayerIfDismissed()
                        } else {
                            if (!playerSheetState.isDismissed) {
                                playerSheetState.dismiss()
                            }
                            showQueueOverlay = false
                        }
                    }

                    // Full-screen routes have their own mini-player transition.
                    val isMiniplayerHiddenRoute = isMiniplayerHiddenRoute(currentRoute)
                    val miniplayerHideProgress = animateFloatAsState(
                        targetValue = if (isMiniplayerHiddenRoute) 1f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "miniplayerHideDismiss"
                    )

                    // Keep the card mounted through the slide to the dismissed anchor.
                    if (shouldComposePlayerSheet(mediaPresent, playerSheetState.isDismissed)) {
                        val artworkInMotion = remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .graphicsLayer {
                                    translationY = miniplayerHideProgress.value * 160.dp.toPx()
                                    alpha = (1f - miniplayerHideProgress.value).coerceIn(0f, 1f)
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                }
                        ) {
                                CustomBottomSheet(
                                    state = playerSheetState,
                                    modifier = Modifier.fillMaxWidth(),
                                    onDismiss = {
                                        binder?.onExplicitQueueClear()
                                        binder?.player?.clearMediaItems()
                                        runCatching {
                                            this@MainActivityContent.stopService(this@MainActivityContent.intent<app.n_zik.android.playback.services.PlayerServiceModern>())
                                        }
                                    },
                                    bottomPadding = {
                                        val dockPadding = if (floatingDockEnabled && !topDock && !isTopPlayer)
                                            navBarBottomPad + dockGeometry().miniBottomOffset.dp * dockPresence.value
                                        else playerPadBottom.value
                                        maxOf(dockPadding, settingsFooterClearance) + with(density) {
                                            if (isTopPlayer) 0.dp else bottomNavigationImeLift().toDp()
                                        }
                                    },
                                    topPadding = playerTopPadding,
                                    placementKey = playerPlacementKey,
                                    verticalOffset = {
                                        with(density) {
                                            if (isTopPlayer) 0.dp else bottomBarOffsetState.value.roundToInt().toDp()
                                        }
                                    },
                                    collapsedStartInset = playerStartInset,
                                    collapsedEndInset = playerEndInset,
                                    collapsedContentHeight = Dimensions.collapsedPlayer,
                                    collapsedHeightProvider = { dockGeometry().miniHeight.dp },
                                    collapsedStartInsetProvider = {
                                        maxOf(playerStartInset, playerSafeStartInset -
                                            16.dp * (1f - floatingDockProgress.value)) +
                                            floatingMiniStandaloneInset(dockSafeWidth.value, floatingDockProgress.value, isLandscape).dp.let { standalone ->
                                                if (!floatingDockEnabled || !floatingDock.hasAttachedMini) standalone else standalone +
                                                    (dockGeometry().miniSideInset.dp - standalone) * dockPresence.value
                                            }
                                    },
                                    collapsedEndInsetProvider = {
                                        maxOf(playerEndInset, playerSafeEndInset -
                                            16.dp * (1f - floatingDockProgress.value)) +
                                            floatingMiniStandaloneInset(dockSafeWidth.value, floatingDockProgress.value, isLandscape).dp.let { standalone ->
                                                if (!floatingDockEnabled || !floatingDock.hasAttachedMini) standalone else standalone +
                                                    (dockGeometry().miniEndInset.dp - standalone) * dockPresence.value
                                            }
                                    },
                                    collapsedHorizontalPaddingProvider = { 16.dp * (1f - floatingDockProgress.value) },
                                    disableDismiss = shouldDisablePlayerSheetDismiss(disableClosingPlayerSwipingDown, ltGuestLocked),
                                    // Guest lock: a blocked "clean the player" attempt is explained by the
                                    // shared throttled toast (the destructive onDismiss is unreachable
                                    // while disabled). The "disable closing swiping down" setting alone
                                    // stays silent — it is the user's own choice.
                                    onDismissBlocked = {
                                        if (ltGuestLocked) ListenTogetherGuestGuardPlayer.reportUiBlockedOp(this@MainActivityContent)
                                    },
                                    collapsedContent = {
                                        val toastObstructionVisible by remember(playerSheetState, isTopPlayer) {
                                            derivedStateOf {
                                                !isTopPlayer && playerSheetState.isCollapsed && miniplayerHideProgress.value < .99f
                                            }
                                        }
                                        CompositionLocalProvider(LocalCompactMiniPlayerProgressProvider provides
                                            { floatingDockProgress.value }) {
                                        Box(Modifier.fillMaxWidth()
                                            .trackToastObstruction(
                                                enabled = toastObstructionVisible,
                                            )) {
                                        MiniPlayer(
                                            showPlayer = {
                                                playerSheetState.expandSoft()
                                            },
                                            hidePlayer = {
                                                coroutineScope.launch {
                                                    playerSheetState.hide()
                                                }
                                            },
                                            navController = navController
                                        )
                                        }
                                        }
                                    }
                                ) {
                                    PaletteFade(durationMillis = 360, artworkInMotion = artworkInMotion) {
                                        Player(navController, artworkInMotion = artworkInMotion) {
                                            coroutineScope.launch {
                                                playerSheetState.hide()
                                            }
                                        }
                                    }
                                }
                        }
                    }

                    }, overlay = {
                    MiniPlayerQueueOverlay(
                        showSheet = showQueueOverlay,
                        navController = navController,
                        onDismiss = { showQueueOverlay = false },
                        onVisibilityChange = { miniQueueMounted = it },
                    )
                    })

                    ManualVideoSearchSheet(
                        query = videoSession.manualSearchQuery.takeUnless { pipState.value },
                        onDismiss = { videoSession.manualSearchQuery = null },
                    )

                    val menuState = LocalMenuState.current
                    val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                    AppAppearanceMenu(menuState, menuSheetState)
                    val activePlaylistActions = playlistActions
                    LaunchedEffect(activePlaylistActions, menuState.isDisplayed) {
                        if (activePlaylistActions?.parentMenu != null && !menuState.isDisplayed) {
                            playlistActions = null
                        }
                    }
                    PlaylistImportAddDialog(
                        menuState = menuState,
                        visible = activePlaylistActions?.isOnSource(menuState) == true && !importMenu.isOpen,
                        canAdd = playlistActions?.openPicker != null,
                        canImport = playlistActions?.canImportFiles == true,
                        onDismiss = { playlistActions = null },
                        onImport = { importMenu.open { playlistActions = null } },
                        onAdd = {
                            val request = playlistActions
                            if (request?.parentMenu == null) playlistActions = null
                            val onReturn = if (request != null && request.parentMenu == null) {
                                { playlistActions = request }
                            } else null
                            request?.openPicker?.invoke(onReturn)
                        },
                    )

                    }
                    }
                    AppGlassOverlayScene(
                        host = glassMenuHost,
                        toastNeedsLiveBackdrop = toastNeedsLiveBackdrop,
                        onToastPresenceChanged = { toastNeedsLiveBackdrop = it },
                        windowsInsets = windowsInsets,
                    )
                    }
                    }
                    }

                }

                if (videoSession.selected && videoSession.fullscreen && !pipState.value) {
                    Box(Modifier.fillMaxSize().zIndex(1f)) {
                        CompositionLocalProvider(
                            LocalPlayerServiceBinder provides binder,
                            LocalPlaybackPositionClock provides positionClock,
                        ) {
                            MusicVideoPresentation(videoSession, binder?.player, inPip = false)
                        }
                    }
                }
                }
        }
        // Issue #881 (gh-881): tolerant saver — this slot lives in the BoxWithConstraints
        // sub-composition, the exact spot where the 15:23:55 CCE crashed (spec M5).
        var isPlayerInitialized by rememberSaveable(saver = TolerantBoolStateSaver("main.isPlayerInitialized")) { mutableStateOf(false) }
        LaunchedEffect(playerArtwork, observedArtwork, playerUpdateTrigger) {
            val player = playerArtwork.player ?: return@LaunchedEffect
            setDynamicPalette(player) { !playerSheetState.isExpanded }
        }
        DisposableEffect(binder?.player, playerUpdateTrigger) {
            val currentBinder = binder ?: return@DisposableEffect onDispose { }
            val player = currentBinder.player

            // "Now playing" notification tap — COLD start (spec-notification-click-opens-player):
            // the held tap token is validated against the CURRENT service's token (via
            // the Binder) on EVERY run of this effect, not only at init: a valid token
            // with no media yet is HELD and re-checked when media arrives (queue
            // restore / early binder attach); a stale token (task-record re-send after
            // a process death — the restarted service minted a new token) is
            // discarded, no deployment. There is no persistent marker: staleness is
            // the service's live token, not a stored value.
            val heldOpenPlayerToken = openPlayerColdToken
            var deployedNow = false
            if (heldOpenPlayerToken != null) {
                if (heldOpenPlayerToken != currentBinder.openPlayerToken) {
                    // Stale (process-death re-send: the service restarted and minted a
                    // new token) → discard, no deployment.
                    openPlayerColdToken = null
                } else if (player.currentMediaItem != null) {
                    openPlayerColdToken = null
                    // Decision 2026-10-03 (spec-notification-click-opens-player): the
                    // notification tap ALWAYS deploys the full player — the
                    // keepPlayerMinimized setting is NOT consulted on this path
                    // (a deliberate tap, distinct from the widget/historical
                    // cold start that honors the setting).
                    // Anti-flicker: a sheet already above the expanded bound is left
                    // alone (no visible collapse→expand on a tap while the full player
                    // is open).
                    if (playerSheetState.value < playerSheetState.expandedBound) {
                        coroutineScope.presentMiniplayerThenExpand(playerSheetState, onPresent = { restoreHiddenBars() })
                    }
                    deployedNow = true
                }
                // valid + no media: held — re-checked on the next playerUpdateTrigger.
            }

            if (!isPlayerInitialized) {
                isPlayerInitialized = true
                if (player.currentMediaItem != null && !deployedNow) {
                    if (launchedFromNotification) intent.replaceExtras(Bundle())
                    if (launchedFromNotification && !preferences.getBoolean(keepPlayerMinimizedKey, true)) {
                        coroutineScope.presentMiniplayerThenExpand(playerSheetState, onPresent = { restoreHiddenBars() })
                    } else {
                        playerSheetState.presentMiniplayerCollapsed()
                    }
                }
            }

            val listener = object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (settingsPlayerRestartInProgress) return
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem != null) {
                        if (mediaItem.mediaMetadata.extras?.getBoolean("isFromPersistentQueue") != true) {
                            if (playerSheetState.isExpanded) return
                            if (preferences.getBoolean(keepPlayerMinimizedKey, true)) {
                                // Collapsed position; pops with a fade when coming from the dismissed zone
                                playerSheetState.presentMiniplayerCollapsed()
                            } else {
                                coroutineScope.presentMiniplayerThenExpand(playerSheetState, onPresent = { restoreHiddenBars() })
                            }
                        }
                    }
                }
            }

            player.addListener(listener)

            onDispose {
                player.removeListener(listener)
            }
        }

        InitDownloader()

        }
    }

    DeliverPendingLibraryNavigation(navController)
}
