package app.n_zik.android.core.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import app.n_zik.android.components.glass.GlassMenuHostState
import app.n_zik.android.components.glass.excludingGlassOverlays
import app.n_zik.android.components.navigation.HeaderScrollTravel
import app.n_zik.android.components.navigation.SideRailScrollOrigin
import app.n_zik.android.components.navigation.chromeScrollDelta
import app.n_zik.android.components.navigation.dock.FloatingDockEnvironment
import app.n_zik.android.components.navigation.dock.FloatingDockState
import app.n_zik.android.components.navigation.dock.dockHasAttachedMini
import app.n_zik.android.components.navigation.dock.miniPlayerFoldEligible
import app.n_zik.android.components.navigation.pageSupportsChromeScroll
import app.n_zik.android.components.player.APP_HEADER_HEIGHT
import app.n_zik.android.components.ui.screens.home.HomeHeaderMotionRegistry
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.enums.PlayerPosition
import app.n_zik.android.utils.LandscapeBars
import app.n_zik.android.utils.playerPositionKey
import app.n_zik.android.utils.rememberPreference

internal class ScaffoldScrollState(
    val bars: ScaffoldBarVisibilityState,
    val connection: NestedScrollConnection,
    val dock: FloatingDockEnvironment,
    val homeHeaderMotionRegistry: HomeHeaderMotionRegistry,
    val sideRailScrollOrigin: SideRailScrollOrigin,
    val revealOnNavigation: () -> Unit,
    private val sideNavigationHeaderActive: MutableState<Boolean>,
) {
    @Composable
    fun TrackSideNavigationHeader(isActive: Boolean) {
        SideEffect { sideNavigationHeaderActive.value = isActive }
        LaunchedEffect(isActive) {
            if (isActive) {
                bars.animationJob.value?.cancel()
                bars.animationJob.value = null
                bars.topOffset.floatValue = 0f
            }
        }
    }
}

@Composable
internal fun rememberScaffoldScrollConnection(
    navController: NavController,
    coroutineScope: CoroutineScope,
    isLandscapeBarless: Boolean,
    isLandscape: Boolean,
    safeDrawingInsets: WindowInsets,
    dockNavigationPosition: NavigationBarPosition,
    areBarsHidden: Boolean,
    dockMediaIdState: State<String?>,
    isPlayerSheetExpanded: State<Boolean>,
    showQueueOverlay: Boolean,
    glassMenuHost: GlassMenuHostState,
): ScaffoldScrollState {
    val scaffoldBars = remember { ScaffoldBarVisibilityState() }
    var topBarOffset by scaffoldBars.topOffset
    LaunchedEffect(isLandscapeBarless) {
        if (isLandscapeBarless) topBarOffset = 0f
    }
    val headerTravel = remember(scaffoldBars) { HeaderScrollTravel() }
    val homeHeaderMotionRegistry = remember(scaffoldBars, navController) {
        HomeHeaderMotionRegistry(
            scaffoldBars.topOffset,
            homeIsCurrent = { navController.currentDestination?.route?.substringBefore('?') == NavRoutes.home.name },
        ) {
            scaffoldBars.animationJob.value?.cancel()
            scaffoldBars.animationJob.value = null
        }
    }
    val offsetAnimationJob = scaffoldBars.animationJob
    val revealScaffoldOnNavigation = remember(scaffoldBars, coroutineScope, headerTravel) {
        {
            headerTravel.reset()
            scaffoldBars.revealOnNavigation(coroutineScope)
        }
    }
    RevealScaffoldOnNavigation(navController, revealScaffoldOnNavigation)

    val density = LocalDensity.current
    val chromeEntry by navController.currentBackStackEntryAsState()
    val isScrollableRoute = pageSupportsChromeScroll(chromeEntry?.destination?.route)

    // Geometry changes require an immediate reset of offsets measured in pixels.
    LaunchedEffect(isLandscape, density, safeDrawingInsets) {
        scaffoldBars.reveal()
    }

    val floatingDockState = remember { FloatingDockState() }
    val classicDock = dockNavigationPosition == NavigationBarPosition.Bottom
    val topDock = dockNavigationPosition == NavigationBarPosition.Top
    val floatingDockEnabled = (dockNavigationPosition.isFloating || classicDock || topDock) && !areBarsHidden
    val floatingDockPageKey = chromeEntry?.id
    val dockMediaId by dockMediaIdState
    val dockPlayerPosition by rememberPreference(playerPositionKey, PlayerPosition.Bottom)
    LaunchedEffect(scaffoldBars) {
        scaffoldBars.reveal()
    }
    val floatingDockClock = remember { Animatable(0f) }
    val floatingDockProgress = floatingDockClock.asState()
    val dockMotionBlocked = !miniPlayerFoldEligible(
        isPlayerSheetExpanded.value, showQueueOverlay, glassMenuHost.hasOverlays,
    )
    // Read the target outside composition while the foreground list is scrolling.
    LaunchedEffect(floatingDockState, dockMotionBlocked) {
        if (!dockMotionBlocked) {
            snapshotFlow { floatingDockState.foldTarget }.collectLatest { target ->
                floatingDockClock.animateTo(
                    target,
                    tween(320, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
                )
            }
        }
    }
    val floatingDock = remember(
        floatingDockState, floatingDockPageKey, floatingDockEnabled, classicDock, topDock,
        dockNavigationPosition, floatingDockProgress, dockMediaId, dockPlayerPosition,
    ) {
        FloatingDockEnvironment(
            state = floatingDockState,
            pageKey = floatingDockPageKey,
            enabled = floatingDockEnabled,
            progress = floatingDockProgress,
            hasBottomMini = dockMediaId != null && dockPlayerPosition == PlayerPosition.Bottom,
            classic = classicDock || topDock,
            floating = dockNavigationPosition.isFloating,
            attachedMini = dockHasAttachedMini(dockMediaId != null, dockNavigationPosition, dockPlayerPosition),
        )
    }
    LaunchedEffect(floatingDockPageKey, floatingDockEnabled, classicDock, topDock) {
        floatingDockState.expand()
        scaffoldBars.bottomOffset.floatValue = 0f
    }
    val sideNavigationHeaderActive = remember { mutableStateOf(false) }
    val sideRailScrollOrigin = remember { SideRailScrollOrigin() }
    val nestedScrollConnection = remember(
        isLandscapeBarless, isLandscape, isScrollableRoute, density, showQueueOverlay, glassMenuHost,
    ) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (sideRailScrollOrigin.take()) return Offset.Zero
                if (isPlayerSheetExpanded.value || showQueueOverlay || glassMenuHost.hasOverlays) return Offset.Zero
                val deltaY = chromeScrollDelta(
                    consumed.y, available.y,
                    topBarOffset < 0f || floatingDockState.foldTarget > 0f,
                )
                if (deltaY != 0f) floatingDockState.onScroll(deltaY, with(density) { 50.dp.toPx() })
                if (!sideNavigationHeaderActive.value && (isScrollableRoute || isLandscape)) {
                    val headerHeight = with(density) { APP_HEADER_HEIGHT.toPx() }
                    val headerDelta = headerTravel.delta(
                        consumed.y, available.y, topBarOffset, headerHeight,
                        homeHeaderMotionRegistry.hasScrollRoom(),
                    )
                    if (headerDelta != 0f) {
                        offsetAnimationJob.value?.cancel()
                        offsetAnimationJob.value = null
                        homeHeaderMotionRegistry.scrollBy(headerDelta, headerHeight)
                    }
                }
                if (deltaY != 0f && isLandscapeBarless && source == NestedScrollSource.UserInput) {
                    LandscapeBars.hide()
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (sideNavigationHeaderActive.value || isPlayerSheetExpanded.value ||
                    showQueueOverlay || glassMenuHost.hasOverlays
                ) return Velocity.Zero
                if (homeHeaderMotionRegistry.hasScrollRoom() == false) return Velocity.Zero
                val height = with(density) { APP_HEADER_HEIGHT.toPx() }
                val target = if (topBarOffset < -height / 2f) -height else 0f
                if (target == topBarOffset) return Velocity.Zero
                offsetAnimationJob.value?.cancel()
                offsetAnimationJob.value = coroutineScope.launch {
                    Animatable(topBarOffset).animateTo(target, tween(200, easing = FastOutSlowInEasing)) {
                        topBarOffset = value
                        homeHeaderMotionRegistry.sync()
                    }
                }
                return Velocity.Zero
            }
        }.excludingGlassOverlays(glassMenuHost)
    }

    return ScaffoldScrollState(
        bars = scaffoldBars,
        connection = nestedScrollConnection,
        dock = floatingDock,
        homeHeaderMotionRegistry = homeHeaderMotionRegistry,
        sideRailScrollOrigin = sideRailScrollOrigin,
        revealOnNavigation = revealScaffoldOnNavigation,
        sideNavigationHeaderActive = sideNavigationHeaderActive,
    )
}
