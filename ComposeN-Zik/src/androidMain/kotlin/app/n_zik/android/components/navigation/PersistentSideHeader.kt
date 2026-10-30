package app.n_zik.android.components.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.navigation.header.SideAppHeaderControls
import app.n_zik.android.components.navigation.nav.SideNavigationSurface

/** The panel and header stay outside NavHost; only the current entry supplies tab content. */
@Composable
internal fun PersistentSideHeader(
    navController: NavController,
    position: NavigationBarPosition,
    pageKey: () -> String? = { null },
    expectsTabs: () -> Boolean = { false },
    registration: (String?) -> SideRailState.Registration? = { null },
) {
    val settings = rememberGlassSettings()
    val surfaceBackdrop = rememberLayerBackdrop()
    PersistentSideRailFrame(position) {
        Box(Modifier.fillMaxSize().sideRailScrollOrigin()) {
            SideNavigationSurface(settings, Modifier.matchParentSize(), surfaceBackdrop)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp, bottom = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SideAppHeaderControls(navController)
                SideRailBodySubcompose(pageKey, expectsTabs, registration, surfaceBackdrop)
            }
        }
    }
}

/** The mini-player already clears the rail horizontally, so playback cannot shorten it. */
@Composable
internal fun PersistentSideRailFrame(
    position: NavigationBarPosition,
    safeInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    SideNavigationOverlay(
        onRight = position == NavigationBarPosition.Right,
        railWidthPx = with(density) { Dimensions.navigationRailWidth.roundToPx() },
        topInsetPx = { safeInsets.getTop(density) },
        bottomInsetPx = { safeInsets.getBottom(density) },
        leftInsetPx = { safeInsets.getLeft(density, direction) },
        rightInsetPx = { safeInsets.getRight(density, direction) },
        modifier = Modifier.fillMaxSize(),
        content = content,
    )
}
