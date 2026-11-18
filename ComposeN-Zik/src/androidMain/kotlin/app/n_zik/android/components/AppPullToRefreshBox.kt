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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassIndicatorSilhouette
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.IconContrast
import app.n_zik.android.components.glass.contrastAgainst
import app.n_zik.android.components.glass.effectiveSurfaceColor
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassFilmColor
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.glassPaneForeground
import app.n_zik.android.components.glass.glassSurfaceDarkness
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.rememberInlineGlassBackdrop
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.pullrefresh.rememberPullRefreshOverlayInsetProvider
import app.n_zik.android.components.theme.LocalNativeSurfaceMix
import app.n_zik.android.components.theme.paletteDarkness
import app.n_zik.android.isPitchBlackSurface

internal fun pullRefreshNativeInk(fill: Color, darkness: Float, blackCoverage: Float): Color {
    val lightInkFraction = darkness * (1f - blackCoverage)
    val preferred = lerp(Color.Black, Color.White, lightInkFraction)
    return glassPaneForeground(preferred, fill, IconContrast, lightInkFraction)
}

@Composable
fun AppPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    useLiveGlassBackdrop: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val pullRefreshState = rememberPullToRefreshState()
    val colorPalette = LocalAppearance.current.colorPalette
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
            val blackCoverage = LocalNativeSurfaceMix.current?.let { it.pitchBlack + it.pureBlack }
                ?: if (isPitchBlackSurface()) 1f else 0f
            val nativeInk = pullRefreshNativeInk(colorPalette.accent, paletteDarkness(), blackCoverage)
            StyledPullRefreshIndicator(
                pullRefreshState, isRefreshing, settings, colorPalette.accent, nativeInk,
                placement, useLiveGlassBackdrop,
            )
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
    nativeInk: Color,
    modifier: Modifier,
    useLiveGlassBackdrop: Boolean,
) {
    val area = GlassArea.PullToRefresh
    val shape = PullToRefreshDefaults.indicatorShape
    val material = settings.usesGlassMaterial(area)
    val page = LocalAppearance.current.colorPalette.background0
    val areaSettings = settings.forArea(area)
    val liveGlass = material && useLiveGlassBackdrop
    val fill = if (liveGlass) glassFilmColor(areaSettings, nativeFill, menu = false) else nativeFill
    val surface = effectiveSurfaceColor(page, fill)
    val glyphColor = if (liveGlass) glassPaneForeground(
        Color.Unspecified, surface, IconContrast, glassSurfaceDarkness(areaSettings),
    ) else nativeInk
    val edgeAlpha = ((IconContrast - contrastAgainst(glyphColor, surface)) / .65f).coerceIn(0f, 1f)
    val plate = if (liveGlass) {
        val backdrop = rememberInlineGlassBackdrop(page)
        Modifier.glassMaterial(
            areaSettings, shape, ownBackdrop = backdrop, filmColor = nativeFill, drawShadow = false,
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
            val outline = pullRefreshOutline(edgeAlpha)
            Box(outline) {
                if (material) {
                    GlassIndicatorSilhouette(settings, area, glyphColor) { glyph ->
                        PullRefreshGlyph(state, isRefreshing, glyphColor, glyph)
                    }
                } else {
                    PullRefreshGlyph(state, isRefreshing, glyphColor)
                }
            }
        }
    }
}

/** The two edge tones remain legible as the glyph passes through its plate color. */
@Composable
private fun pullRefreshOutline(alpha: Float): Modifier {
    if (alpha == 0f) return Modifier
    val edge = remember { Paint() }
    return Modifier.drawWithContent {
        val bounds = Rect(Offset.Zero, size)
        edge.alpha = alpha
        edge.colorFilter = ColorFilter.tint(Color.Black)
        drawContext.canvas.saveLayer(bounds, edge)
        scale(1.13f) { this@drawWithContent.drawContent() }
        drawContext.canvas.restore()
        edge.colorFilter = ColorFilter.tint(Color.White)
        drawContext.canvas.saveLayer(bounds, edge)
        scale(1.06f) { this@drawWithContent.drawContent() }
        drawContext.canvas.restore()
        drawContent()
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
