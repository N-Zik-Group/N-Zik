package app.it.fast4x.rimusic.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.n_zik.android.BuildConfig
import app.n_zik.android.LocalPlayerSheetState
import app.n_zik.android.colorPalette
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import app.it.fast4x.rimusic.enums.CheckUpdateState
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.PlayerPosition
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.LocalPlayerServiceBinder
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Color
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.ui.components.navigation.nav.AbstractNavigationBar
import app.it.fast4x.rimusic.ui.components.navigation.nav.HorizontalNavigationBar
import app.it.fast4x.rimusic.ui.components.navigation.nav.VerticalNavigationBar
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.checkUpdateStateKey
import app.it.fast4x.rimusic.utils.checkBetaUpdatesKey
import app.it.fast4x.rimusic.utils.playerPositionKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.seenChangelogsVersionKey
import app.it.fast4x.rimusic.utils.transition
import app.it.fast4x.rimusic.enums.UiType
import app.n_zik.android.components.dialog.updater.CheckForUpdateDialog
import app.n_zik.android.components.dialog.updater.NewUpdateAvailableDialog
import app.n_zik.android.components.dialog.updater.BuildTransitionWarningDialog
import app.n_zik.android.updater.models.MajorUpdateConfig
import app.n_zik.android.components.dialog.updater.MajorUpdateWarningDialog
import app.n_zik.android.updater.services.Updater
import app.it.fast4x.rimusic.utils.lastVersionCodeKey
import app.it.fast4x.rimusic.utils.lastBuildTypeKey
import app.n_zik.android.LocalBottomBarOffset

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
    content: @Composable AnimatedVisibilityScope.(Int) -> Unit
) {
    val navigationBarPosition = NavigationBarPosition.current()
    val isFloating = navigationBarPosition.isFloating
    val isIconOnly = NavigationBarType.IconOnly.isCurrent()
    val binder = LocalPlayerServiceBinder.current
    val isMiniPlayerActive = binder?.player?.currentMediaItem != null
    val currentInsets = LocalPlayerAwareWindowInsets.current

    val bottomBarOffsetState = LocalBottomBarOffset.current

    val navigationBar: AbstractNavigationBar =
        if ( navigationBarPosition.isHorizontal )
            HorizontalNavigationBar( tabIndex, onTabChanged, navController )
        else
            VerticalNavigationBar( tabIndex, onTabChanged, navController )

    navigationBar.add( navBarContent )
    
    val hasNavBar = navigationBar.buttonList.size >= 2

    val navBarBottomPadding = Dimensions.navBarBottomPadding(isFloating)
    val miniPlayerHeight = Dimensions.miniPlayerHeight

    val modifiedInsets by remember(currentInsets, isFloating, isIconOnly, isMiniPlayerActive, hasNavBar, navBarBottomPadding, miniPlayerHeight) {
        derivedStateOf {
            if (isFloating) {
                val barHeight = if (hasNavBar) (if (isIconOnly) Dimensions.floatingNavBarIconOnlyHeight else Dimensions.floatingNavBarHeight) else 0.dp
                val visualGap = 10.dp
                val extraBottom = barHeight + navBarBottomPadding + visualGap
                currentInsets.add(WindowInsets(bottom = extraBottom))
            } else {
                val barHeight = if (hasNavBar) (Dimensions.navigationBarHeight - 10.dp) else 0.dp
                currentInsets.add(WindowInsets(bottom = barHeight))
            }
        }
    }

    CompositionLocalProvider(LocalPlayerAwareWindowInsets provides modifiedInsets) {

        val appHeader: @Composable () -> Unit = {
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                if ( NavigationBarPosition.Top.isCurrent() )
                    navigationBar.Draw()
            }
        }

        val modifier: Modifier = Modifier

        Scaffold(
            modifier = modifier,
            containerColor = Color.Transparent,
            topBar = appHeader,
            contentWindowInsets = currentInsets,
            bottomBar = {
                if ( NavigationBarPosition.Bottom.isCurrent() ) {
                    if (!isFloating) {
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(0, bottomBarOffsetState.value.roundToInt()) }
                                .fillMaxWidth()
                        ) {
                            navigationBar.Draw()
                        }
                    } else {
                        navigationBar.Draw()
                    }
                }
            }
        ) { scaffoldPadding ->
            Box(Modifier.fillMaxSize()) {
                // Main Content Area (Now fills screen behind floating UI)
                Box(
                    Modifier
                        .padding(
                            top = scaffoldPadding.calculateTopPadding(),
                            bottom = 0.dp
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
                        if( NavigationBarPosition.Left.isCurrent() )
                            navigationBar.Draw()

                        AnimatedContent(
                            targetState = tabIndex,
                            transitionSpec = transition(),
                            content = content,
                            label = "",
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )

                        if( NavigationBarPosition.Right.isCurrent() )
                            navigationBar.Draw()
                    }
                }

                // Floating UI Overlay (Sync with screen bottom, NOT scaffold content)
                Box(Modifier.fillMaxSize()
                    .offset { IntOffset(0, bottomBarOffsetState.value.roundToInt()) }
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                    if ( isFloating ) {
                        Box(
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            navigationBar.Draw()
                        }
                    }

                    val playerPosition by rememberPreference(playerPositionKey, PlayerPosition.Bottom)
                    val playerAlignment =
                        if (playerPosition == PlayerPosition.Top)
                            Alignment.TopCenter
                        else
                            Alignment.BottomCenter

                    val playerPaddingBottom = if (playerPosition == PlayerPosition.Bottom) {
                        if (isFloating) {
                            if (hasNavBar) {
                                val barHeight = if (isIconOnly) Dimensions.floatingNavBarIconOnlyHeight else Dimensions.floatingNavBarHeight
                                barHeight + navBarBottomPadding + 4.dp
                            } else {
                                navBarBottomPadding
                            }
                        } else {
                            if (hasNavBar && NavigationBarPosition.Bottom.isCurrent()) {
                                Dimensions.standardNavBarHeight + navBarBottomPadding + 4.dp
                            } else {
                                navBarBottomPadding + 5.dp
                            }
                        }
                    } else 5.dp

                    // MiniPlayer is now rendered in CustomBottomSheet (MainActivity)
                    // No need to render it here
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

        // Function to extract the version suffix
        fun extractVersionSuffix(versionStr: String): String {
            val parts = versionStr.removePrefix("v").split("-")
            return if (parts.size > 1) parts[1] else ""
        }

        val check4UpdateState by rememberPreference( checkUpdateStateKey, CheckUpdateState.Enabled )
        val checkBetaUpdates by rememberPreference( checkBetaUpdatesKey, extractVersionSuffix(BuildConfig.VERSION_NAME) == "beta" )
        
        // Reset update state when beta preferences change
        LaunchedEffect( checkBetaUpdates ) {
            if (NewUpdateAvailableDialog.isActive) {
                // If beta preferences changed and there's an active update dialog, recheck
                NewUpdateAvailableDialog.isCancelled = false
                Updater.checkForUpdate(checkBetaUpdates = checkBetaUpdates)
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




