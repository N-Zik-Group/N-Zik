package app.n_zik.android.components.glass

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop

/** Follows the actual sheet position in both directions, including interrupted drags. */
internal fun miniPlayerGlassBodyAlpha(progress: Float): Float =
    1f - ((if (progress.isFinite()) progress else 0f) / MINI_PLAYER_GLASS_BODY_FADE_END).coerceIn(0f, 1f)

private const val MINI_PLAYER_GLASS_BODY_FADE_END = .12f

/** A transparent material must not reveal the swipe action's native fill while at rest. */
internal fun miniPlayerSwipeBackgroundColor(settings: GlassSettings, revealed: Boolean, original: Color): Color =
    if (settings.usesGlassMaterial(GlassArea.MiniPlayer) && !revealed) Color.Transparent else original

/** Apply only to the empty body layer so controls keep their own material. */
internal fun Modifier.glassMiniPlayerBodyVisibility(settings: GlassSettings, progress: () -> Float): Modifier =
    if (settings.usesGlassMaterial(GlassArea.MiniPlayer)) graphicsLayer {
        alpha = miniPlayerGlassBodyAlpha(progress())
    } else this

/** Mixes material-only exports; foreground artwork and controls never enter this source. */
@Composable
internal fun rememberMiniPlayerControlBackdrop(
    parent: Backdrop?,
    body: Backdrop,
    progress: () -> Float,
): Backdrop = remember(parent, body, progress) {
    if (parent == null) body else object : Backdrop {
        override val isCoordinatesDependent = true

        override fun DrawScope.drawBackdrop(
            density: Density,
            coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?,
        ) {
            with(parent) { drawBackdrop(density, coordinates, layerBlock) }
            val opacity = miniPlayerGlassBodyAlpha(progress())
            if (opacity > 0f) {
                drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = opacity })
                with(body) { drawBackdrop(density, coordinates, layerBlock) }
                drawContext.canvas.restore()
            }
        }
    }
}

/** Applied to the native branch only: the material branch already owns its sole contour. */
@Composable
fun Modifier.glassAreaContour(settings: GlassSettings, area: GlassArea, shape: Shape): Modifier {
    if (!settings.usesGlassContour(area)) return this
    val tint = glassTint(settings)
    return if (area == GlassArea.Menus || area == GlassArea.Dropdowns) {
        glassMenuRim(shape, glassEdgeLightBrush(settings, tint))
    } else glassEdgeRim(settings, shape, tint)
}

/** Keeps the native mini player fill and shadow whenever only its contour is selected. */
@Composable
fun Modifier.glassMiniPlayerBackground(
    settings: GlassSettings,
    shape: Shape,
    backdrop: Backdrop?,
    originalColor: Color,
    progressColor: Color = Color.Transparent,
    progressFraction: () -> Float = { 0f },
    exportedBackdrop: LayerBackdrop? = null,
    motionOptimized: Boolean = false,
): Modifier {
    if (!settings.usesGlassMaterial(GlassArea.MiniPlayer)) {
        return background(originalColor).drawBehind {
            drawRect(progressColor, size = Size(progressFraction() * size.width, size.maxDimension))
        }.glassAreaContour(settings, GlassArea.MiniPlayer, shape)
    }
    val baseFilm = glassNativeSurfaceTint(settings, originalColor)
    val progressFilm = glassNativeSurfaceTint(settings, progressColor.compositeOver(originalColor))
    val opacity = settings.film.coerceIn(0f, 1f)
    // A denser played film stays readable even when the global tint merges both source colours.
    val progressOpacity = opacity + .8f * opacity * (1f - opacity)
    // Both regions share one capture, optical surface and contour; only their film changes.
    return glassMaterial(
        settings.forArea(GlassArea.MiniPlayer), shape, ownBackdrop = backdrop,
        highlightAlpha = .3f, drawFilm = false, exportedBackdrop = exportedBackdrop,
        applyEdgeEffects = !motionOptimized,
        backdropScale = if (motionOptimized) .5f else null,
        drawShadow = !motionOptimized,
        onDrawTint = {
            val fraction = progressFraction().let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
            val split = size.width * fraction
            clipRect(right = split) { drawRect(progressFilm.copy(alpha = progressFilm.alpha * progressOpacity)) }
            clipRect(left = split) { drawRect(baseFilm.copy(alpha = baseFilm.alpha * opacity)) }
        },
    )
}
