package app.n_zik.android.components.navigation

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.glassNativeSurfaceTint
import app.n_zik.android.surfaceColor
import app.n_zik.android.LocalBottomBarOffset
import app.n_zik.android.components.navigation.dock.LocalFloatingDock
import app.n_zik.android.components.navigation.dock.PersistentHorizontalNavigation
import app.n_zik.android.components.player.APP_HEADER_HEIGHT
import app.n_zik.android.components.player.TOP_NAV_BAR_HEIGHT


import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.GlassRenderer
import app.n_zik.android.components.glass.glassRenderer
import androidx.compose.runtime.DisposableEffect
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassArtworkColor
import app.n_zik.android.components.glass.GlassMenuHostState
import app.n_zik.android.components.glass.GlassOverlayHost
import app.n_zik.android.components.glass.GlassDialogOverlayHost
import app.n_zik.android.components.glass.GlassModalContent
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.rememberGlassArtworkColor
import app.n_zik.android.LocalPlayerServiceBinder
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

import app.n_zik.android.core.navigation.ScreenTransitions
import app.n_zik.android.core.navigation.LocalSettingsBackNavigation
import app.n_zik.android.core.navigation.SettingsBackNavigation
import app.n_zik.android.components.search.SearchResultsViewport
import app.n_zik.android.components.theme.appearanceBackground


import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import app.n_zik.android.components.navigation.header.AppHeader
import app.n_zik.android.components.navigation.header.LocalHeaderProfileFace
import app.n_zik.android.components.navigation.header.rememberHeaderProfileFace
import app.n_zik.android.components.navigation.header.LocalSideHeaderMenuTrigger
import app.n_zik.android.components.glass.GlassMenuTriggerState
import app.n_zik.android.components.navigation.header.LocalSideHeaderSearchProgress
import app.n_zik.android.components.navigation.header.headerSearchJoinProgress
import app.n_zik.android.core.navigation.currentSearchShortcutRoute
import app.n_zik.android.core.navigation.shouldShowSearchShortcut
import app.kreate.android.themed.rimusic.screen.artist.ArtistAlbums
import app.kreate.android.themed.rimusic.screen.artist.ArtistPlaylists
import app.kreate.android.themed.rimusic.screen.artist.ArtistVideos
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.StatisticsType
import app.it.fast4x.rimusic.enums.TransitionEffect
import app.n_zik.android.components.ui.screens.easter.EasterScreen
import app.n_zik.android.easter.EasterEgg
import app.it.fast4x.rimusic.models.Mood
import app.n_zik.android.components.glass.CustomModalBottomSheet
import app.n_zik.android.components.musicbrainz.insights.AlbumInsightsScreen
import app.n_zik.android.components.musicbrainz.insights.ArtistInsightsScreen
import app.n_zik.android.components.ui.screens.album.AlbumScreen
import app.n_zik.android.components.ui.screens.artist.ArtistScreen
import app.it.fast4x.rimusic.ui.screens.history.HistoryScreen
import app.n_zik.android.components.ui.screens.home.HomeScreen
import app.n_zik.android.extensions.discord.DiscordUiState
import app.n_zik.android.components.ui.screens.home.LocalHomeTopMiniPlayerAnchor
import app.n_zik.android.components.ui.screens.home.LocalSharedHomeHeaderGlass
import app.n_zik.android.components.ui.screens.home.rememberSharedHomeHeaderGlass
import app.n_zik.android.components.ui.screens.home.usesSharedHomeHeaderGlass
import app.n_zik.android.components.ui.screens.listentogether.ListenTogetherScreen
import app.n_zik.android.components.ui.screens.bridge.BridgeServerScreen
import app.it.fast4x.rimusic.ui.screens.localplaylist.LocalPlaylistScreen
import app.it.fast4x.rimusic.ui.screens.mood.MoodScreen
import app.it.fast4x.rimusic.ui.screens.mood.MoodsPageScreen
import app.it.fast4x.rimusic.ui.screens.newreleases.NewreleasesScreen
import app.it.fast4x.rimusic.ui.screens.player.Queue
import app.it.fast4x.rimusic.ui.screens.playlist.PlaylistScreen
import app.it.fast4x.rimusic.ui.screens.podcast.PodcastScreen
import app.it.fast4x.rimusic.ui.screens.search.SearchScreen
import app.it.fast4x.rimusic.ui.screens.searchresult.SearchResultScreen
import app.n_zik.android.components.ui.screens.settings.SettingsScreen
import app.it.fast4x.rimusic.ui.screens.statistics.StatisticsScreen
import app.n_zik.android.components.ui.screens.rewind.RewindHomeScreen
import app.n_zik.android.components.ui.screens.rewind.RewindScreen
import app.it.fast4x.rimusic.utils.clearPreference
import app.it.fast4x.rimusic.utils.homeScreenTabIndexKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.transitionEffectKey
import app.it.fast4x.rimusic.utils.disableNavigationBackStackKey
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import timber.log.Timber
import app.n_zik.android.updater.ui.UpdateScreen
import androidx.compose.runtime.remember
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import app.n_zik.android.LocalTopBarOffset
import app.n_zik.android.uiRoundnessShape
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.core.spring
import app.n_zik.android.components.ui.screens.profiles.ProfileScreen
import app.it.fast4x.rimusic.ui.styling.Dimensions

fun NavHostController.navigateClean(route: String, context: Context) {
    val disableBackStack = context.preferences.getBoolean(disableNavigationBackStackKey, false)
    if (disableBackStack) {
        navigate(route) {
            popUpTo(NavRoutes.home.name) { saveState = false }
            launchSingleTop = true
        }
    } else {
        navigate(route)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@androidx.annotation.OptIn()
@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class,
    ExperimentalTextApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalMaterial3Api::class,
)
@UnstableApi
@Composable
fun AppNavigation(
    navController: NavHostController,
    miniPlayer: @Composable () -> Unit = {},
    openTabFromShortcut: Int,
    hasTopNavBarVisible: Boolean = false,
    hasSideNavBarVisible: Boolean = false,
    hasPageNavBar: Boolean = false,
) {
    val sideRail = remember(navController) { SideRailState() }
    val headerProfileFace = rememberHeaderProfileFace()
    val sideHeaderMenuTrigger = remember(navController) { GlassMenuTriggerState() }
    val navigationPosition = NavigationBarPosition.current()
    val horizontalDock = LocalFloatingDock.current
    val horizontalBottomOffset = LocalBottomBarOffset.current
    val sideHeaderSearchProgress = headerSearchJoinProgress(
        shouldShowSearchShortcut(currentSearchShortcutRoute(navController))
    )
    val glassSettings = rememberGlassSettings()
    val parentGlassHost = LocalGlassMenuHost.current
    val glassMenuHost = parentGlassHost ?: remember { GlassMenuHostState() }
    val pageBackdrop = rememberLayerBackdrop()
    val overlayBackdrop = rememberLayerBackdrop()
    val captureGlass = glassSettings.enabled && glassRenderer(glassSettings) != GlassRenderer.Tint
    val captureNavigationOverlay = captureGlass && parentGlassHost?.sceneBackdrop == null
    SideEffect {
        glassMenuHost.backdrop = if (captureGlass) pageBackdrop else null
        glassMenuHost.overlayBackdrop = if (captureNavigationOverlay) overlayBackdrop else null
    }
    DisposableEffect(glassMenuHost, pageBackdrop, overlayBackdrop) {
        onDispose {
            if (glassMenuHost.backdrop === pageBackdrop) glassMenuHost.backdrop = null
            if (glassMenuHost.overlayBackdrop === overlayBackdrop) glassMenuHost.overlayBackdrop = null
        }
    }
    val binder = LocalPlayerServiceBinder.current
    val playerGeneration by binder?.playerUpdateTrigger?.collectAsStateWithLifecycle(0)
        ?: remember { mutableStateOf(0) }
    // Crossfade replaces the player facade without replacing the service binder.
    val player = remember(binder, playerGeneration) { binder?.player }
    val artworkColor = rememberGlassArtworkColor(
        player, glassSettings.enabled || glassMenuHost.standaloneSettings, colorPalette().isDark,
    )
    val artworkLuminance by animateFloatAsState(
        targetValue = artworkColor?.luminance() ?: 0f,
        animationSpec = tween(600), label = "glassBackdropLuminance",
    )
    SideEffect {
        glassMenuHost.artworkColor = artworkColor
        glassMenuHost.backdropLuminance = artworkLuminance
    }
    val transitionEffect by rememberPreference(transitionEffectKey, TransitionEffect.Fade)

    @Composable
    fun modalBottomSheetPage(content: @Composable () -> Unit) {
        var showSheet by rememberSaveable { mutableStateOf(true) }
        
        CustomModalBottomSheet(
            showSheet = showSheet,
            onDismissRequest = {
                if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED)
                    navController.popBackStack()
            },
            containerColor = Color.Transparent,
            modifier = Modifier.statusBarsPadding(),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            shape = uiRoundnessShape()
        ) {
            content()
        }
    }

    // Clearing homeScreenTabIndex in opening app.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        clearPreference(context, homeScreenTabIndexKey)
    }

    val transitions = remember(transitionEffect) { ScreenTransitions(transitionEffect) }

    val disableBackStack by rememberPreference(disableNavigationBackStackKey, false)
    val currentEntry by navController.currentBackStackEntryAsState()
    val settingsBackNavigation = remember(navController) {
        SettingsBackNavigation { currentEntry?.destination?.route }
    }
    val isHome = currentEntry?.destination?.route?.startsWith(NavRoutes.home.name) ?: true
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val currentHomeTab by DiscordUiState.currentHomeTab.collectAsStateWithLifecycle()
    val isQuickPicks = isHome && (currentHomeTab == null || currentHomeTab == "quickpicks")
    val sharedHomeGlass = rememberSharedHomeHeaderGlass(
        enabled = usesSharedHomeHeaderGlass(isHome, currentHomeTab, navigationPosition, glassSettings),
        pageKey = NavRoutes.home.name,
        topNavigation = navigationPosition == NavigationBarPosition.Top,
    )
    val isSettings = currentEntry?.destination?.route?.startsWith(NavRoutes.settings.name) == true
    val isRewind = currentEntry?.destination?.route?.let { route ->
        route == NavRoutes.rewind.name || route.startsWith("${NavRoutes.rewind.name}?")
    } ?: false

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val lifecycleOwner = LocalLifecycleOwner.current

    val backCallback = remember(navController, settingsBackNavigation) {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (!settingsBackNavigation.handleBack()) {
                    navController.popBackStack(NavRoutes.home.name, inclusive = false)
                }
            }
        }
    }
    
    val interceptBackToHome = disableBackStack && !isHome && !settingsBackNavigation.hasNestedBackStack
    backCallback.isEnabled = interceptBackToHome

    LaunchedEffect(currentEntry, interceptBackToHome) {
        if (interceptBackToHome) {
            backCallback.remove()
            backDispatcher?.addCallback(lifecycleOwner, backCallback)
        } else {
            backCallback.remove()
        }
    }

    DisposableEffect(lifecycleOwner, backDispatcher) {
        onDispose { backCallback.remove() }
    }

    // Phone in landscape on the Songs/Album/Artist/Library tabs: the app header is hidden until
    // the toggle button slides it in (and it slides out again on the next tap or scroll)
    val isBarlessScreen = app.it.fast4x.rimusic.utils.isLandscapeBarlessScreen() && !hasSideNavBarVisible
    val hideTopBar = app.it.fast4x.rimusic.utils.hideBarsInLandscapeMobile() && !hasSideNavBarVisible
    // Kept as State objects and only read while laying out: the slide moves the content every
    // frame, and reading it here in composition would recompose the whole nav graph each frame
    val headerProgress = animateFloatAsState(
        targetValue = if (hideTopBar) 0f else 1f,
        animationSpec = tween(app.it.fast4x.rimusic.utils.LANDSCAPE_BARS_ANIMATION_MS, easing = FastOutSlowInEasing),
        label = "landscapeHeaderProgress"
    )
    // Rewind is a full-screen deck: the header slides up out of the screen on a jelly spring
    // (and slides back in with the same bounce when leaving the deck)
    val rewindHeaderProgress = animateFloatAsState(
        targetValue = if (isRewind) 0f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "rewindHeaderProgress"
    )
    val isBarlessScreenState = rememberUpdatedState(isBarlessScreen)
    // Home chrome uses the same scroll offset in landscape as in portrait. The
    // landscape toggle still controls the navigation tabs, not a second header clock.
    val toggleDrivesHeader = remember {
        derivedStateOf { isBarlessScreenState.value }
    }

    val density = LocalDensity.current
    val statusBarTopPx = WindowInsets.safeDrawing.getTop(density)
    val statusBarLeftPx = WindowInsets.safeDrawing.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
    val statusBarRightPx = WindowInsets.safeDrawing.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
    val headerRowHeightPx = with(density) { (if (hasSideNavBarVisible) 0.dp else APP_HEADER_HEIGHT).toPx() }
    val headerHeightPx = with(density) { (if (hasSideNavBarVisible) 0.dp else APP_HEADER_HEIGHT).roundToPx() } + statusBarTopPx
    val navBaseColor = surfaceColor(colorPalette().background1)
    val navStatusColor = if (glassSettings.usesGlassMaterial(GlassArea.Nav)) {
        val tinted = glassNativeSurfaceTint(glassSettings, navBaseColor)
        tinted.copy(alpha = tinted.alpha * glassSettings.film.coerceIn(0f, 1f))
    } else navBaseColor
    val homeTopMiniAnchor = LocalHomeTopMiniPlayerAnchor.current
    val scrollTopBarOffset = LocalTopBarOffset.current
    val topBarOffsetState = remember(headerHeightPx, scrollTopBarOffset, hasSideNavBarVisible) {
        derivedStateOf {
            if (hasSideNavBarVisible) 0f
            else if (toggleDrivesHeader.value) -(1f - headerProgress.value) * headerHeightPx
            // Rewind slide is additive: 0 on every normal screen, -headerHeight on the deck
            else scrollTopBarOffset.value - (1f - rewindHeaderProgress.value) * headerHeightPx
        }
    }

    CompositionLocalProvider(
        LocalSideRail provides sideRail,
        LocalHeaderProfileFace provides headerProfileFace,
        LocalSideHeaderMenuTrigger provides sideHeaderMenuTrigger,
        LocalSideHeaderSearchProgress provides sideHeaderSearchProgress,
        LocalSettingsBackNavigation provides settingsBackNavigation,
        LocalSharedHomeHeaderGlass provides sharedHomeGlass,
        LocalGlassArtworkColor provides artworkColor,
        LocalGlassMenuHost provides glassMenuHost,
        app.n_zik.android.components.ui.screens.home.LocalHomeTopCover provides (isBarlessScreen || hasSideNavBarVisible),
        app.n_zik.android.components.ui.screens.home.LocalHomeLandscapeTopCover provides isBarlessScreen,
        app.n_zik.android.components.ui.screens.home.LocalHomeTopDockFoldProgress provides {
            if (hasTopNavBarVisible) horizontalDock?.navigationProgress ?: 0f else 0f
        },
        LocalAppHeaderInitialContentInset provides {
            with(density) {
                appHeaderContentInsetPx(
                    headerRowHeightPx, statusBarTopPx.toFloat(), isBarlessScreen, 0f,
                ).toDp()
            }
        },
        LocalAppHeaderContentInset provides {
            with(density) {
                appHeaderContentInsetPx(
                    headerRowHeightPx, statusBarTopPx.toFloat(), isBarlessScreen, headerProgress.value,
                ).toDp()
            }
        },
        LocalAppHeaderOverlayInset provides {
            with(density) {
                appHeaderOverlayTopPx(headerRowHeightPx, statusBarTopPx.toFloat(), topBarOffsetState.value).toDp()
            }
        },
    ) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    GlassModalContent(glassMenuHost, manageFocus = parentGlassHost == null, focusSessionKey = currentEntry?.id) {
    AppHeaderOverlay(
        // The page-only capture cannot cover the header: a fully expanded menu would
        // sample transparent pixels over the header and expose its unblurred icons.
        // Keep this outer capture separate so header glass still samples pageBackdrop.
        // The transparent header also needs the root appearance background inside
        // the capture, or its gaps expose an abrupt optical boundary above the page.
        modifier = Modifier.fillMaxSize()
            .then(if (captureNavigationOverlay) Modifier.layerBackdrop(
                overlayBackdrop,
                frozen = { !glassMenuHost.hasOverlays },
            ).appearanceBackground() else Modifier)
            .topNavigationStatusFill(
                visible = hasTopNavBarVisible && !glassSettings.usesGlassMaterial(GlassArea.Nav),
                statusBarTopPx = statusBarTopPx.toFloat(),
                horizontalStartInsetPx = statusBarLeftPx.toFloat(),
                horizontalEndInsetPx = statusBarRightPx.toFloat(),
                headerHeightPx = headerRowHeightPx,
                color = navStatusColor,
                headerColor = if (hasSideNavBarVisible || glassSettings.usesGlassMaterial(GlassArea.Header) ||
                    (hasTopNavBarVisible && glassSettings.usesGlassMaterial(GlassArea.Nav)) ||
                    (isHome && isLandscape)) null
                    else if (hasTopNavBarVisible) navStatusColor else colorPalette().background0,
                headerOffsetPx = { topBarOffsetState.value },
            ),
        headerClipBottomPx = if (hasTopNavBarVisible) {
            { appHeaderOverlayTopPx(headerRowHeightPx, statusBarTopPx.toFloat(), topBarOffsetState.value).toInt() }
        } else null,
        navigation = {
    if (navigationPosition.isHorizontal || navigationPosition.isFloating) CompositionLocalProvider(
        LocalGlassBackdrop provides pageBackdrop.takeIf { captureGlass },
    ) {
        PersistentHorizontalNavigation(
            position = navigationPosition,
            pageKey = { currentEntry?.id },
            expectsTabs = { hasPageNavBar },
            registration = { horizontalDock?.state?.registration(it) },
            hidden = hideTopBar,
            topPx = { appHeaderOverlayTopPx(headerRowHeightPx, statusBarTopPx.toFloat(), topBarOffsetState.value).toInt() },
            bottomOffsetPx = { horizontalBottomOffset.value },
        )
    }
        },
        // Expanded Top chrome covers the fixed mini-player gap. The compact dock
        // keeps its own surface and lets page content show around the capsule.
        topContentCover = {
            if (isHome && !isQuickPicks && hasTopNavBarVisible && !glassSettings.usesGlassMaterial(GlassArea.Nav)) {
                Box(Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .layout { measurable, constraints ->
                    val navigationBottom = appHeaderOverlayTopPx(
                        headerRowHeightPx, statusBarTopPx.toFloat(), topBarOffsetState.value,
                    ) + TOP_NAV_BAR_HEIGHT.toPx()
                    val miniBottom = homeTopMiniAnchor?.headerTopInset()?.toPx() ?: 0f
                    val coverHeight = maxOf(navigationBottom, miniBottom).toInt()
                        .coerceIn(0, constraints.maxHeight)
                    val cover = measurable.measure(Constraints.fixed(constraints.maxWidth, coverHeight))
                    layout(cover.width, cover.height) { cover.place(0, 0) }
                }.graphicsLayer {
                    alpha = 1f - (horizontalDock?.navigationProgress ?: 0f).coerceIn(0f, 1f)
                }.background(colorPalette().background0))
            }
        },
        // Side rails own the controls; horizontal headers keep their layout-only scroll motion.
        header = {
            if (!hasSideNavBarVisible) CompositionLocalProvider(LocalTopBarOffset provides topBarOffsetState, LocalGlassBackdrop provides pageBackdrop.takeIf { captureGlass }) {
                AppHeader(navController).Draw(
                    backgroundOverride = navStatusColor.takeIf { hasTopNavBarVisible },
                    statusBackgroundVisible = hasTopNavBarVisible,
                )
            }
        },
    ) {
    SearchResultsViewport {
    NavHost(
        modifier = Modifier.fillMaxSize()
            .then(if (captureGlass) Modifier.graphicsLayer().layerBackdrop(pageBackdrop) else Modifier),
        navController = navController,
        startDestination = NavRoutes.home.name,
        enterTransition = transitions.enterTransition,
        exitTransition = transitions.exitTransition,
        popEnterTransition = transitions.popEnterTransition,
        popExitTransition = transitions.popExitTransition,
        predictivePopEnterTransition = transitions.predictivePopEnterTransition,
        predictivePopExitTransition = transitions.predictivePopExitTransition,
    ) {
        val navigateToPlaylist =
            { browseId: String -> navController.navigateClean("${NavRoutes.playlist.name}/$browseId", context) }

        chromeComposable(route = NavRoutes.home.name) {
            HomeScreen(
                navController = navController,
                onPlaylistUrl = navigateToPlaylist,
                miniPlayer = miniPlayer,
                openTabFromShortcut = openTabFromShortcut
            )
        }

        chromeComposable(route = NavRoutes.easterOne.name) {
            EasterScreen(navController = navController, egg = EasterEgg.PACMAN)
        }

        chromeComposable(route = NavRoutes.easterTwo.name) {
            EasterScreen(navController = navController, egg = EasterEgg.SNAKE)
        }

        chromeComposable(route = NavRoutes.queue.name) {
            modalBottomSheetPage {
                Queue(
                    navController = navController,
                    onDismiss = {},
                    onDiscoverClick = {}
                )
            }
        }

        chromeComposable(
            route = "${NavRoutes.artist.name}/{id}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id") ?: ""
            ArtistScreen(
                navController = navController,
                browseId = id,
                miniPlayer = miniPlayer,
            )
        }

        chromeComposable(
            route = "${NavRoutes.album.name}/{id}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id") ?: ""
            AlbumScreen(
                navController = navController,
                browseId = id,
                miniPlayer = miniPlayer,
            )
        }

        chromeComposable(
            route = "artistInsights/{id}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id") ?: ""
            ArtistInsightsScreen(
                navController = navController,
                artistId = id
            )
        }

        chromeComposable(
            route = "albumInsights/{id}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id") ?: ""
            AlbumInsightsScreen(
                navController = navController,
                albumId = id
            )
        }

        chromeComposable(
            route = "${NavRoutes.playlist.name}/{id}?params={params}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = {
                        type = NavType.StringType
                    }
                ),
                navArgument(
                    name = "params",
                    builder = {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id") ?: ""
            val params = navBackStackEntry.arguments?.getString( "params" )
            PlaylistScreen(
                navController = navController,
                browseId = id,
                params = params,
                miniPlayer = miniPlayer,
            )
        }

        chromeComposable(
            route = "${NavRoutes.podcast.name}/{id}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id") ?: ""
            PodcastScreen(
                navController = navController,
                browseId = id,
                params = null,
                miniPlayer = miniPlayer,
            )
        }

        chromeComposable(
            route = "${NavRoutes.settings.name}?tab={tab}&focus={focus}",
            arguments = listOf(
                navArgument(
                    name = "tab",
                    builder = {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                ),
                navArgument(
                    name = "focus",
                    builder = {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            )
        ) { navBackStackEntry ->
            val tab = navBackStackEntry.arguments?.getInt("tab", 0) ?: 0
            val focus = navBackStackEntry.arguments?.getString("focus", "") ?: ""

            SettingsScreen(
                navController = navController,
                miniPlayer = miniPlayer,
                initialTab = tab,
                focus = focus,
            )
        }

        chromeComposable(route = NavRoutes.statistics.name) {
            StatisticsScreen(
                navController = navController,
                statisticsType = StatisticsType.Today,
                miniPlayer = miniPlayer,
            )
        }

        // Rewind home: yearly/monthly entry point to the deck
        chromeComposable(route = NavRoutes.rewindHome.name) {
            RewindHomeScreen(
                navController = navController,
                miniPlayer = miniPlayer,
            )
        }

        // Listen Together room screen (spec-listen-together)
        chromeComposable(route = NavRoutes.listenTogether.name) {
            ListenTogetherScreen(
                navController = navController,
                miniPlayer = miniPlayer,
            )
        }

        // Local PC bridge server page
        chromeComposable(route = NavRoutes.bridgeServer.name) {
            BridgeServerScreen(
                navController = navController,
                miniPlayer = miniPlayer,
            )
        }

        chromeComposable(
            route = "${NavRoutes.rewind.name}?year={year}&month={month}&scope={scope}",
            arguments = listOf(
                navArgument("year") { type = NavType.IntType; defaultValue = 0 },
                navArgument("month") { type = NavType.IntType; defaultValue = -1 },
                navArgument("scope") { type = NavType.StringType; defaultValue = "" },
            )
        ) { backStackEntry ->
            val year = backStackEntry.arguments?.getInt("year", 0) ?: 0
            val month = backStackEntry.arguments?.getInt("month", -1) ?: -1
            val scope = backStackEntry.arguments?.getString("scope", "") ?: ""
            RewindScreen(
                navController = navController,
                miniPlayer = miniPlayer,
                rewindYear = if (year > 0) year else null,
                rewindMonth = if (month > 0) month else null,
                rewindScope = scope,
            )
        }

        chromeComposable(route = NavRoutes.profiles.name) {
            ProfileScreen(
                navController = navController,
                miniPlayer = miniPlayer,
            )
        }

        chromeComposable(route = NavRoutes.history.name) {
            HistoryScreen(
                navController = navController,
                miniPlayer = miniPlayer,

                )
        }

        chromeComposable(
            route = "${NavRoutes.search.name}?text={text}",
            arguments = listOf(
                navArgument(
                    name = "text",
                    builder = {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            )
        ) { navBackStackEntry ->
            val text = navBackStackEntry.arguments?.getString("text") ?: ""

            SearchScreen(
                navController = navController,
                miniPlayer = miniPlayer,
                initialTextInput = text,
                onViewPlaylist = {},
                onSearch = { query ->
                    Timber.tag("AppNavigation").d("onSearch: $query")

                    navController.navigate(
                        route = "${NavRoutes.searchResults.name}/${Uri.encode( query )}",
                    )
                    // Search history is recorded per screen by the search tabs
                    // themselves (SearchHistoryStore), not here
                },
            )
        }

        chromeComposable(
            route = "${NavRoutes.searchResults.name}/{query}",
            arguments = listOf(
                navArgument(
                    name = "query",
                    builder = { type = NavType.StringType }
                )
            )
        ) { navBackStackEntry ->
            val query = navBackStackEntry.arguments?.getString("query") ?: ""

            SearchResultScreen(
                navController = navController,
                miniPlayer = miniPlayer,
                query = query,
                onSearchAgain = {}
            )
        }

        chromeComposable(
            route = "${NavRoutes.localPlaylist.name}/{id}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.LongType }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getLong("id") ?: 0L

            LocalPlaylistScreen(
                navController = navController,
                playlistId = id,
                miniPlayer = miniPlayer
            )
        }

        chromeComposable(
            route = NavRoutes.mood.name,
        ) { navBackStackEntry ->
            val mood: Mood? = navController.previousBackStackEntry?.savedStateHandle?.get("mood")
            if (mood != null) {
                MoodScreen(
                    navController = navController,
                    mood = mood,
                    miniPlayer = miniPlayer,
                )
            }
        }

        chromeComposable(
            route = NavRoutes.moodsPage.name
        ) { navBackStackEntry ->
            MoodsPageScreen(
                navController = navController,
                miniPlayer = miniPlayer
            )
        }

        chromeComposable(
            route = NavRoutes.newAlbums.name
        ) { navBackStackEntry ->
            NewreleasesScreen(
                navController = navController,
                miniPlayer = miniPlayer,
            )
        }

        chromeComposable(
            route = "${NavRoutes.artistAlbums.name}/{id}?params={params}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                ),
                navArgument(
                    name = "params",
                    builder = {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id").orEmpty()
            val params = navBackStackEntry.arguments?.getString("params").orEmpty()

            ArtistAlbums( navController, id, params, miniPlayer )
        }

        chromeComposable(
            route = "${NavRoutes.artistVideos.name}/{id}?params={params}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                ),
                navArgument(
                    name = "params",
                    builder = {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id").orEmpty()
            val params = navBackStackEntry.arguments?.getString("params").orEmpty()

            ArtistVideos( navController, id, params, miniPlayer )
        }

        chromeComposable(
            route = "${NavRoutes.artistPlaylists.name}/{id}?params={params}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                ),
                navArgument(
                    name = "params",
                    builder = {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id").orEmpty()
            val params = navBackStackEntry.arguments?.getString("params").orEmpty()

            ArtistPlaylists( navController, id, params, miniPlayer )
        }

        chromeComposable(route = NavRoutes.updater.name) {
            UpdateScreen(navController = navController)
        }
    }
    } // end SearchResultsViewport
    }
    if (!navigationPosition.isHorizontal && !navigationPosition.isFloating) CompositionLocalProvider(
        LocalGlassBackdrop provides pageBackdrop.takeIf { captureGlass },
    ) {
        PersistentSideHeader(navController, navigationPosition,
            pageKey = { currentEntry?.id }, expectsTabs = { hasPageNavBar },
            registration = sideRail::registration)
    }
    if (parentGlassHost == null) {
        GlassDialogOverlayHost(glassMenuHost)
        GlassOverlayHost(glassMenuHost)
    }
    }
    }
    }
}





