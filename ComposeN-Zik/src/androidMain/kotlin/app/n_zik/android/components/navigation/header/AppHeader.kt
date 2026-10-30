package app.n_zik.android.components.navigation.header

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.core.navigation.navigationChromeTween
import app.n_zik.android.core.navigation.rememberAppBackAction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import app.n_zik.android.components.glass.backdrop.Backdrop
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.ui.components.themed.Button
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.n_zik.android.R
import app.n_zik.android.LocalTopBarOffset
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import app.n_zik.android.components.player.APP_HEADER_HEIGHT
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.ui.header.CollapsingAppTitle
import app.n_zik.android.components.glass.rememberGlassSettings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.LocalContentColor
import app.n_zik.android.components.ui.screens.home.LocalSharedHomeHeaderGlass

class AppHeader(
    val navController: NavController
) {

    companion object {

        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun colors(): TopAppBarColors = TopAppBarColors(
            containerColor = colorPalette().background0,
            titleContentColor = colorPalette().text,
            scrolledContainerColor = colorPalette().background0,
            navigationIconContentColor = colorPalette().background0,
            actionIconContentColor = colorPalette().text
        )
    }

    @Composable
    fun Draw(backgroundOverride: Color? = null, statusBackgroundVisible: Boolean = false) {
        val glassSettings = rememberGlassSettings()
        val glassEnabled = glassSettings.usesGlassMaterial(GlassArea.Header)
        val onAppBack = rememberAppBackAction(navController)
        val context = LocalContext.current
        val currentEntry by navController.currentBackStackEntryAsState()
        val isHome = currentEntry?.destination?.route?.let { route ->
            route.startsWith(NavRoutes.home.name) || route == NavRoutes.queue.name
        } ?: true
        // Animate the start padding smoothly so the logo has nice spacing when home, 
        // and the back button aligns correctly when present.
        val startPadding by animateDpAsState(
            targetValue = if (isHome || glassEnabled) 12.dp else 4.dp,
            animationSpec = navigationChromeTween()
        )

        val topBarOffsetState = LocalTopBarOffset.current
        val density = LocalDensity.current
        val statusBarTopPx = WindowInsets.safeDrawing.getTop(density).toFloat()
        val headerHeightPx = with(density) { APP_HEADER_HEIGHT.toPx() }
        val headerColor = backgroundOverride ?: colorPalette().background0
        val hasTopGlassBackdrop = statusBackgroundVisible && glassSettings.usesGlassMaterial(GlassArea.Nav)
        val sharedHomeGlass = LocalSharedHomeHeaderGlass.current?.takeIf { it.owner != null }
        val standaloneGlass = glassEnabled && !hasTopGlassBackdrop && sharedHomeGlass == null

        Box(Modifier.fillMaxWidth()) {
        // Glass Top owns one continuous pane behind both this header and its nav row.
        if (statusBackgroundVisible && !hasTopGlassBackdrop && !standaloneGlass) {
            TopHeaderBackdrop(
                settings = glassSettings,
                filmColor = surfaceColor(colorPalette().background1),
                statusBarTopPx = statusBarTopPx,
                headerRowHeightPx = headerHeightPx,
                headerOffsetPx = { topBarOffsetState.value },
            )
        }
        // Slide the themed surface above the page without fading its controls. The page
        // keeps its own scroll distance while this clip excludes the system status area.
        val controls: @Composable (Backdrop?) -> Unit = { controlsBackdrop ->
        Row(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .headerScrollFade(
                    statusBarTopPx, headerHeightPx,
                    surfaceColor = headerColor.takeUnless { statusBackgroundVisible || standaloneGlass || sharedHomeGlass != null },
                    clipStatusAtRest = true,
                ) { topBarOffsetState.value }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = startPadding, end = if (glassEnabled) 12.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Restore TopAppBar's default content coloring behavior for action buttons
            CompositionLocalProvider(
                LocalContentColor provides colorPalette().text,
                LocalGlassBackdrop provides controlsBackdrop,
            ) {
                // Glass Back fades in at its anchor; only the title's reserved space expands.
                HeaderBackSlot(visible = !isHome, glass = glassEnabled) {
                    if (glassEnabled) {
                        Box(Modifier.padding(end = 12.dp)) {
                            HeaderIcon(R.drawable.chevron_back, colorPalette().favoritesIcon, onClick = onAppBack)
                        }
                    } else Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(uiRoundnessShape())
                            .glassAreaContour(glassSettings, GlassArea.Header, uiRoundnessShape())
                            .clickable(onClick = onAppBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            R.drawable.chevron_back,
                            colorPalette().favoritesIcon,
                            0.dp,
                            24.dp
                        ).Draw()
                    }
                }
    
                // Logo + Title — shifts right smoothly as back button expands.
                // Takes the full remaining width (weight, fill) so its extras can

                // collapse behind the logo instead of compressing the action bar, which
                // stays pinned to the right edge.
                CollapsingAppTitle(navController, context, Modifier.weight(1f))

                // Action icons (search, settings…)
                ActionBar(navController)
            }
        }
        }
        if (sharedHomeGlass != null) {
            controls(sharedHomeGlass.surface)
        } else if (standaloneGlass) {
            StandaloneHeaderGlass(
                settings = glassSettings,
                filmColor = headerColor,
                pageBackdrop = LocalGlassBackdrop.current,
                statusBarTopPx = statusBarTopPx,
                headerHeightPx = headerHeightPx,
                headerOffsetPx = { topBarOffsetState.value },
                content = controls,
            )
        } else {
            controls(rememberHeaderControlsBackdrop(glassEnabled, hasTopGlassBackdrop, headerColor))
        }
        }
    }
}
