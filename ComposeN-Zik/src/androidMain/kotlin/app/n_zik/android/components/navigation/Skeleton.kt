package app.n_zik.android.components.navigation

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.GlassRenderer
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import app.n_zik.android.components.navigation.dock.RegisterDockNavigation
import app.n_zik.android.components.navigation.dock.FloatingDockDimensions
import app.n_zik.android.components.navigation.dock.LocalFloatingDock
import app.n_zik.android.components.navigation.dock.floatingDockGeometry
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.n_zik.android.core.navigation.currentPageNavigationEntry
import app.n_zik.android.BuildConfig
import app.n_zik.android.core.navigation.RevealScaffoldOnPageSelection
import app.n_zik.android.colorPalette
import androidx.compose.ui.graphics.graphicsLayer
import app.n_zik.android.updater.models.CheckUpdateState
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.LocalPlayerServiceBinder
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Color
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.n_zik.android.components.navigation.nav.AbstractNavigationBar
import app.n_zik.android.components.navigation.nav.HorizontalNavigationBar
import app.n_zik.android.components.navigation.nav.GlassNavigationBarHeight
import app.n_zik.android.components.navigation.nav.GlassNavigationPlayerGap
import app.n_zik.android.components.navigation.nav.VerticalNavigationBar
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.checkUpdateStateKey
import app.it.fast4x.rimusic.utils.checkBetaUpdatesKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.seenChangelogsVersionKey
import app.it.fast4x.rimusic.utils.transition
import app.n_zik.android.components.dialog.updater.CheckForUpdateDialog
import app.n_zik.android.components.dialog.updater.NewUpdateAvailableDialog
import app.n_zik.android.components.dialog.updater.BuildTransitionWarningDialog
import app.n_zik.android.updater.models.MajorUpdateConfig
import app.n_zik.android.components.dialog.updater.MajorUpdateWarningDialog
import app.n_zik.android.updater.services.Updater
import app.it.fast4x.rimusic.utils.lastVersionCodeKey
import app.it.fast4x.rimusic.utils.lastBuildTypeKey
import androidx.compose.ui.platform.LocalDensity

// THIS IS THE SCAFFOLD
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Skeleton(
    navController: NavController,
    tabIndex: Int = 0,
    onTabChanged: (Int) -> Unit = {},
    miniPlayer: @Composable (() -> Unit)? = null,
    navBarContent: @Composable (@Composable (Int, String, Int) -> Unit) -> Unit,
    topInsetHandledByContent: Boolean = false,
    topContentInsetProvider: (() -> Dp)? = null,
    suppressTabTransition: (Int, Int) -> Boolean = { _, _ -> false },
    content: @Composable AnimatedVisibilityScope.(Int) -> Unit
) {
    val navigationBarPosition = NavigationBarPosition.current()
    val tabTransition = transition()
    val glassSettings = rememberGlassSettings()
    val contentBackdrop = rememberLayerBackdrop()
    val captureGlass = glassSettings.enabled && glassRenderer(glassSettings) != GlassRenderer.Tint
    val useGlassNav = glassSettings.usesGlassMaterial(GlassArea.Nav)
    val isFloating = navigationBarPosition.isFloating
    val isIconOnly = NavigationBarType.IconOnly.isCurrent()
    RevealScaffoldOnPageSelection(tabIndex)
    val binder = LocalPlayerServiceBinder.current
    val isMiniPlayerActive = binder?.player?.currentMediaItem != null
    val currentInsets = LocalPlayerAwareWindowInsets.current

    val density = LocalDensity.current

    val navigationBar: AbstractNavigationBar =
        if ( navigationBarPosition.isHorizontal )
            HorizontalNavigationBar( tabIndex, onTabChanged, navController )
        else
            VerticalNavigationBar( tabIndex, onTabChanged, navController )

    navigationBar.add( navBarContent )
    
    // Phone in landscape on the Songs/Album/Artist/Library tabs: the nav bar is hidden until
    // the toggle button slides it in (and it slides out again on the next tap or scroll)
    val hideNavBar = hidePageBarsInLandscapeMobile()
    // The room the bar reserves for the lists changes at once when it is toggled: animating it
    // would recompose the whole screen every frame, and it only shows at the end of a list
    val hasNavBar = !hideNavBar && navigationBar.buttonList.size >= 2
    val topInset = topContentInsetProvider ?: rememberTopContentInsetProvider(hasNavBar)
    val usesDock = isFloating || navigationBarPosition == NavigationBarPosition.Bottom ||
        navigationBarPosition == NavigationBarPosition.Top
    val dock = LocalFloatingDock.current
    // During a NavHost transition the ViewModel owner can already point at the incoming
    // entry while the outgoing page still composes. Its saved-state owner remains page-local.
    val pageEntry = currentPageNavigationEntry()
    val dockPageKey = pageEntry?.id ?: remember(navController) { navController.currentBackStackEntry?.id }
    RegisterDockNavigation(dockPageKey, navigationBar.buttonList.size, visible = !hideNavBar) {
        CompositionLocalProvider(
            LocalGlassBackdrop provides contentBackdrop.takeIf { captureGlass },
            LocalFloatingDock provides dock?.copy(pageKey = dockPageKey, pagePresence = usesDock && hasNavBar),
        ) { if (usesDock) navigationBar.Draw() }
    }

    RegisterSideRailBody(
        pageKey = dockPageKey,
        enabled = !navigationBarPosition.isHorizontal && !isFloating,
    ) { surfaceBackdrop ->
        CompositionLocalProvider(LocalGlassBackdrop provides contentBackdrop.takeIf { captureGlass }) {
            if (hasNavBar) (navigationBar as? VerticalNavigationBar)?.DrawTabs(surfaceBackdrop)
        }
    }

    val navBarBottomPadding = Dimensions.navBarBottomPadding(isFloating)
    val miniPlayerHeight = Dimensions.miniPlayerHeight

    val modifiedInsets by remember(currentInsets, navigationBarPosition, isFloating, isIconOnly, isMiniPlayerActive, hasNavBar, navBarBottomPadding, miniPlayerHeight, useGlassNav, dock, density) {
        derivedStateOf {
            if (isFloating && dock?.enabled == true && hasNavBar) {
                val barHeight = FloatingDockDimensions.expandedHeight.dp
                // Reserve the endpoint once; reading the animation here relayouts the entire page.
                val geometry = floatingDockGeometry(0f, barHeight.value, FloatingDockDimensions.expandedMiniHeight,
                    dock.hasBottomMini, dock.state.foldTarget)
                currentInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                    .add(WindowInsets(bottom = geometry.combinedHeight.dp + navBarBottomPadding))
            } else if (isFloating) {
                val barHeight = if (!hasNavBar) 0.dp else if (useGlassNav) GlassNavigationBarHeight
                    else if (isIconOnly) Dimensions.floatingNavBarIconOnlyHeight else Dimensions.floatingNavBarHeight
                val visualGap = if (useGlassNav && hasNavBar) GlassNavigationPlayerGap else 10.dp
                val extraBottom = barHeight + navBarBottomPadding + visualGap
                currentInsets.add(WindowInsets(bottom = extraBottom))
            } else {
                val barHeight = if (!hasNavBar || !navigationBarPosition.isHorizontal) 0.dp else if (useGlassNav)
                    GlassNavigationBarHeight else Dimensions.navigationBarHeight - 10.dp
                currentInsets.add(WindowInsets(bottom = barHeight))
            }
        }
    }

    val inheritedSideRailInsets = LocalSideRailContentInsets.current
    val sideRailContentInsets = if (hasNavBar && !navigationBarPosition.isHorizontal && !isFloating) {
        WindowInsets(
            left = if (navigationBarPosition == NavigationBarPosition.Left) Dimensions.navigationRailWidth else 0.dp,
            right = if (navigationBarPosition == NavigationBarPosition.Right) Dimensions.navigationRailWidth else 0.dp,
        )
    } else inheritedSideRailInsets

    CompositionLocalProvider(
        LocalPlayerAwareWindowInsets provides if (hasNavBar) modifiedInsets.exclude(inheritedSideRailInsets).add(sideRailContentInsets) else modifiedInsets,
        LocalSideRailContentInsets provides sideRailContentInsets,
        LocalGlassBackdrop provides if (captureGlass) contentBackdrop else null,
        LocalFloatingDock provides dock?.copy(pageKey = dockPageKey, pagePresence = usesDock && hasNavBar),
    ) {

        val modifier: Modifier = Modifier

        Scaffold(
            modifier = modifier,
            containerColor = Color.Transparent,
            // AppNavigation already positions the page below the visible header/status area.
            contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
                .union(currentInsets.only(WindowInsetsSides.Bottom)),
        ) { scaffoldPadding ->
            Box(Modifier.fillMaxSize()) {
                // Main Content Area (Now fills screen behind floating UI)
                Box(
                    Modifier
                        .padding(top = scaffoldPadding.calculateTopPadding(), bottom = 0.dp)
                        .then(
                            if (!topInsetHandledByContent) {
                                Modifier.topOverlayViewport { with(density) { topInset().toPx() } }
                            } else Modifier
                        )
                        .fillMaxSize()
                ) {
                    Row(
                        Modifier
                            .background(colorPalette().background0)
                            .padding(
                                start = scaffoldPadding.calculateStartPadding(LocalLayoutDirection.current),
                                end = scaffoldPadding.calculateEndPadding(LocalLayoutDirection.current)
                            )
                            .fillMaxSize()
                    ) {
                        AnimatedContent(
                            targetState = tabIndex,
                            transitionSpec = {
                                if (suppressTabTransition(initialState, targetState)) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else tabTransition.invoke(this)
                            },
                            content = { index ->
                                // The captured content must never sample its ancestor capture.
                                CompositionLocalProvider(LocalGlassBackdrop provides null) { content(index) }
                            },
                            label = "",
                            modifier = Modifier.weight(1f).fillMaxHeight().then(
                                if (captureGlass) Modifier.graphicsLayer().layerBackdrop(contentBackdrop) else Modifier
                            )
                        )

                    }
                }

            }
        }
        NewUpdateAvailableDialog.Render(
            onNavigateToUpdater = { navController.navigate(NavRoutes.updater.name) }
        )
        CheckForUpdateDialog.Render()

        val lastVersionCode = rememberPreference(lastVersionCodeKey, 0)
        val lastBuildType = rememberPreference(lastBuildTypeKey, "")
        val seenChangelogs = rememberPreference(seenChangelogsVersionKey, "")
        val initialSeenChangelogs = remember { seenChangelogs.value }
        
        LaunchedEffect(Unit) {
            val currentBuildType = MajorUpdateConfig.getCurrentBuildType()
            
            // Check for Build Type Transition (Stable <-> Beta)
            val transition = MajorUpdateConfig.getTransitionType(lastBuildType.value)
            if (transition != null) {
                BuildTransitionWarningDialog.transitionType = transition
                BuildTransitionWarningDialog.isActive = true
            } else if (MajorUpdateConfig.shouldShowWarning(lastVersionCode.value, initialSeenChangelogs.isNotEmpty())) {
                MajorUpdateWarningDialog.isActive = true
            } else {
                val currentCode = BuildConfig.VERSION_CODE
                val lastCode = lastVersionCode.value
                // If it's not a major update and it's a fresh install or a minor update,
                // keep lastVersionCode in sync
                if (lastCode != currentCode) {
                    lastVersionCode.value = currentCode
                }
                
                // Keep build type in sync if no transition warning shown
                if (lastBuildType.value != currentBuildType) {
                    lastBuildType.value = currentBuildType
                }
            }
        }

        MajorUpdateWarningDialog.Render(onConfirm = {
            lastVersionCode.value = BuildConfig.VERSION_CODE
            lastBuildType.value = MajorUpdateConfig.getCurrentBuildType()
        })

        BuildTransitionWarningDialog.Render(onConfirm = {
            lastBuildType.value = MajorUpdateConfig.getCurrentBuildType()
            // Also sync version code when confirmed
            lastVersionCode.value = BuildConfig.VERSION_CODE
        })

        val check4UpdateState by rememberPreference( checkUpdateStateKey, CheckUpdateState.Enabled )
        val checkBetaUpdates by rememberPreference( checkBetaUpdatesKey, Updater.isBetaFamily(BuildConfig.VERSION_NAME) )
        
        // Reset update state when beta preferences change
        LaunchedEffect( checkBetaUpdates ) {
            if (NewUpdateAvailableDialog.isActive) {
                // If beta preferences changed and there's an active update dialog, recheck
                NewUpdateAvailableDialog.isCancelled = false
                // Forced: the cached build is already initialized from the
                // previous channel, so a plain check would re-evaluate the stale release

                // instead of re-fetching with the new channel
                Updater.checkForUpdate(isForced = true, checkBetaUpdates = checkBetaUpdates)
            }
        }
        
        LaunchedEffect( check4UpdateState ) {
            when( check4UpdateState ) {
                CheckUpdateState.Enabled  -> if( !NewUpdateAvailableDialog.isCancelled ) Updater.checkForUpdate(checkBetaUpdates = checkBetaUpdates)
                CheckUpdateState.Ask      -> CheckForUpdateDialog.isActive = true
                CheckUpdateState.Disabled -> { /* Does nothing */ }
            }
        }


        LaunchedEffect(Unit) {
            if (seenChangelogs.value != BuildConfig.VERSION_NAME) {
                seenChangelogs.value = BuildConfig.VERSION_NAME
            }
        }
    }
}
