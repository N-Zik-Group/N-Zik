@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package app.n_zik.android.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassIndicatorSilhouette
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.rememberInlineGlassBackdrop
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.pullrefresh.rememberPullRefreshOverlayInsetProvider
import app.n_zik.android.isPitchBlackSurface

@Composable
fun AppPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val pullRefreshState = rememberPullToRefreshState()
    val colorPalette = LocalAppearance.current.colorPalette
    val pitchBlack = isPitchBlackSurface()
    val settings = rememberGlassSettings()
    var viewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val indicatorInset = rememberPullRefreshOverlayInsetProvider {
        viewportCoordinates?.takeIf { it.isAttached }?.positionInRoot()?.y ?: 0f
    }
    val density = LocalDensity.current

    PullToRefreshBox(
        state = pullRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.onPlaced { viewportCoordinates = it },
        indicator = {
            val placement = Modifier.align(Alignment.TopCenter).offset {
                IntOffset(0, with(density) { indicatorInset().roundToPx() })
            }
            val nativeColor = if (pitchBlack || !colorPalette.isDark) Color.Black else Color.White
            val area = GlassArea.PullToRefresh
            if (settings.usesGlassMaterial(area) || settings.usesGlassContour(area)) {
                StyledPullRefreshIndicator(
                    pullRefreshState, isRefreshing, settings, colorPalette.accent, nativeColor, placement,
                )
            } else {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = placement,
                    containerColor = colorPalette.accent,
                    color = nativeColor,
                )
            }
        },
        content = content
    )
}

@Composable
private fun StyledPullRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    settings: GlassSettings,
    nativeFill: Color,
    nativeColor: Color,
    modifier: Modifier,
) {
    val area = GlassArea.PullToRefresh
    val shape = PullToRefreshDefaults.indicatorShape
    val material = settings.usesGlassMaterial(area)
    val plate = if (material) {
        val backdrop = rememberInlineGlassBackdrop(LocalAppearance.current.colorPalette.background0)
        Modifier.glassMaterial(
            settings.forArea(area), shape, ownBackdrop = backdrop, filmColor = nativeFill, drawShadow = false,
        )
    } else {
        Modifier.background(nativeFill, shape).glassAreaContour(settings, area, shape)
    }
    PullToRefreshDefaults.IndicatorBox(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier.size(LoadingIndicatorDefaults.ContainerWidth, LoadingIndicatorDefaults.ContainerHeight),
        elevation = PullToRefreshDefaults.LoadingIndicatorElevation,
    ) {
        Box(Modifier.matchParentSize().then(plate), contentAlignment = Alignment.Center) {
            if (material) {
                GlassIndicatorSilhouette(settings, area, nativeColor) { glyph ->
                    PullRefreshGlyph(state, isRefreshing, nativeColor, glyph)
                }
            } else {
                PullRefreshGlyph(state, isRefreshing, nativeColor)
            }
        }
    }
}

@Composable
private fun PullRefreshGlyph(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = isRefreshing,
        modifier = modifier,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    ) { refreshing ->
        if (refreshing) {
            LoadingIndicator(color = color)
        } else {
            LoadingIndicator(
                progress = { state.distanceFraction },
                color = color,
                modifier = Modifier.drawWithContent {
                    // Keep the expressive indicator's rotation past the refresh threshold.
                    val overshoot = (state.distanceFraction - 1f).coerceAtLeast(0f)
                    rotate(-overshoot * 180f) { this@drawWithContent.drawContent() }
                },
            )
        }
    }
}
