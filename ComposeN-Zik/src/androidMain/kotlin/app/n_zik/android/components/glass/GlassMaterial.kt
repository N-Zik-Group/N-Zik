// Adapted from Square / Convx Project (C) 2026, GPL-3.0; see NOTICE-square.txt.
package app.n_zik.android.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.isSimple
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.BackdropEffectScope
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.drawBackdrop
import app.n_zik.android.components.glass.backdrop.effects.blur
import app.n_zik.android.components.glass.backdrop.effects.colorControls
import app.n_zik.android.components.glass.backdrop.effects.lens
import app.n_zik.android.components.glass.backdrop.highlight.Highlight
import app.n_zik.android.components.glass.backdrop.highlight.HighlightStyle
import app.n_zik.android.components.glass.backdrop.shadow.Shadow

val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
internal val GlassRimWidth = 0.8.dp

/** Draws a fallback menu rim without laying out, masking, or moving the glass underneath. */
internal fun Modifier.glassMenuRim(shape: Shape, brush: Brush): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val width = minOf(kotlin.math.ceil(GlassRimWidth.toPx()), kotlin.math.ceil(size.minDimension / 2f))
    val half = width / 2f
    val inset = Offset(half, half)
    val borderSize = Size(size.width - width, size.height - width)
    val stroke = Stroke(width)
    val rounded = (outline as? Outline.Rounded)?.roundRect
    fun CornerRadius.inset(amount: Float) = CornerRadius((x - amount).coerceAtLeast(0f), (y - amount).coerceAtLeast(0f))
    // Match Compose.border's inset stroke and unequal-corner path geometry so
    // excluding the bottom never changes antialiasing along the other edges.
    val roundedPath = rounded?.takeUnless { it.isSimple }?.let { rect ->
        Path().apply {
            addRoundRect(rect)
            val inner = Path().apply {
                addRoundRect(RoundRect(width, width, rect.width - width, rect.height - width,
                    rect.topLeftCornerRadius.inset(width), rect.topRightCornerRadius.inset(width),
                    rect.bottomRightCornerRadius.inset(width), rect.bottomLeftCornerRadius.inset(width)))
            }
            op(this, inner, PathOperation.Difference)
        }
    }
    onDrawWithContent {
        drawContent()
        clipRect(left = -width, top = (size.height - width).coerceAtLeast(0f),
            right = size.width + width, bottom = size.height + width, clipOp = ClipOp.Difference) {
            when {
                outline is Outline.Rectangle -> drawRect(brush, inset, borderSize, style = stroke)
                roundedPath != null -> drawPath(roundedPath, brush)
                rounded != null && rounded.topLeftCornerRadius.x < half ->
                    clipRect(width, width, size.width - width, size.height - width, ClipOp.Difference) {
                        drawRoundRect(brush, cornerRadius = rounded.topLeftCornerRadius)
                    }
                rounded != null -> drawRoundRect(brush, inset, borderSize,
                    rounded.topLeftCornerRadius.inset(half), style = stroke)
                else -> drawOutline(outline, brush, style = Stroke(width * 2f))
            }
        }
    }
}

/** Retains square glass optics while leaving unsupported outlines on the tint fallback. */
internal fun glassOpticalShape(shape: Shape): CornerBasedShape? = when (shape) {
    RectangleShape -> RoundedCornerShape(0.dp)
    else -> shape as? CornerBasedShape
}

/** Square records clear panes at full resolution and ramps frost down to 30%. */
fun glassResolutionScale(blurRadiusDp: Float): Float =
    1f - (blurRadiusDp / 8f).coerceIn(0f, 1f) * 0.7f

fun glassSaturation(vibrancy: Float): Float = 1f + 0.5f * vibrancy.coerceIn(0f, 2f)

/**
 * Film has the same range on every surface, including menus and native-window fallbacks.
 */
@Suppress("UNUSED_PARAMETER") // Named callers stay compatible; roles and handoffs impose no hidden opacity floor.
internal fun glassFilmOpacity(configured: Float, overlay: Boolean, progress: Float = 1f,
    fallback: Boolean = false, dark: Boolean = true): Float {
    return configured.coerceIn(0f, 1f)
}

@Composable
fun glassSurfaceTint(settings: GlassSettings): Color {
    val palette = colorPalette()
    val brightness = LocalGlassMenuHost.current?.backdropLuminance
        ?: LocalGlassArtworkColor.current?.luminance() ?: 0f
    val base = if (!palette.isDark) Color(0xFFFAFAFA) else {
        lerp(Color(0xFF4A4A4E), Color(0xFF23232A), ((brightness - 0.42f) / 0.3f).coerceIn(0f, 1f))
    }
    return blendGlassTint(base, glassTint(settings), settings.tintStrength)
}

/** Retains source saturation and tone, including achromatic colours with no defined hue. */
internal fun blendGlassTint(base: Color, tint: Color, strength: Float): Color {
    if (!tint.isSpecified || strength <= 0f) return base
    val neutral = tint.red == tint.green && tint.green == tint.blue
    val sourceBase = if (neutral) {
        val tone = (base.red + base.green + base.blue) / 3f
        Color(tone, tone, tone, base.alpha)
    } else base
    return lerp(sourceBase, tint.copy(alpha = 1f), strength.coerceIn(0f, 1f))
}

/** Applies the shared Glass tint to a native fill without changing its coverage. */
@Composable
internal fun glassNativeSurfaceTint(settings: GlassSettings, base: Color): Color {
    if (!base.isSpecified || base.alpha == 0f) return base
    return blendGlassTint(base, glassTint(settings), settings.tintStrength).copy(alpha = base.alpha)
}

@Composable
private fun glassRimTint(settings: GlassSettings): Color {
    return blendGlassTint(Color.White, glassTint(settings), settings.tintStrength)
}

/** Shared Square material for the preview and all production surfaces. */
@Composable
fun Modifier.glassMaterial(
    settings: GlassSettings,
    shape: Shape,
    separateWindow: Boolean = false,
    menu: Boolean = false,
    applyEdgeEffects: Boolean = true,
    blurRadiusDp: Float = settings.blur,
    highlightAlpha: Float = 0.3f,
    ownBackdrop: Backdrop? = null,
    backdropScale: Float? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    onDrawTint: (DrawScope.() -> Unit)? = null,
    menuFilmProgress: Float = 1f,
    filmColor: Color? = null,
    drawFilm: Boolean = true,
    drawShadow: Boolean = true,
    exportedBackdrop: LayerBackdrop? = null,
): Modifier {
    if (!settings.enabled) return this
    val backdrop = ownBackdrop ?: LocalGlassBackdrop.current
    val renderer = if (separateWindow || backdrop == null) GlassRenderer.Tint else glassRenderer(settings)
    val baseFilm = glassSurfaceTint(settings)
    val nativeFilm = filmColor?.let { glassNativeSurfaceTint(settings, it) }
    val film = if (menu && nativeFilm != null) lerp(baseFilm, nativeFilm, menuFilmProgress.coerceIn(0f, 1f))
        else nativeFilm ?: baseFilm
    val rimColor = glassRimTint(settings)
    val rimAlpha = (highlightAlpha * settings.rim / 0.55f).coerceIn(0f, 1f)
    val cornerShape = glassOpticalShape(shape)
    val menuOpacity = menuFilmProgress.coerceIn(0f, 1f)
    val dark = colorPalette().isDark
    val fallback = renderer == GlassRenderer.Tint || cornerShape == null || backdrop == null
    val opacity = glassFilmOpacity(settings.film, menu, menuOpacity, fallback, dark)
    if (renderer == GlassRenderer.Tint || cornerShape == null || backdrop == null) {
        return then(if (layerBlock != null) Modifier.graphicsLayer(layerBlock) else Modifier)
            .clip(shape).background(if (drawFilm) film.copy(alpha = film.alpha * opacity) else Color.Transparent)
            .then(if (onDrawTint != null) Modifier.drawBehind { onDrawTint.invoke(this) } else Modifier)
            .then(if (menu) Modifier.glassMenuRim(shape, SolidColor(rimColor.copy(alpha = rimAlpha)))
                else Modifier.border(GlassRimWidth, rimColor.copy(alpha = rimAlpha), shape))
    }
    val density = LocalDensity.current
    val blurBudget = playerOverlayBlurBudget(
        radiusDp = blurRadiusDp,
        resolution = (backdropScale ?: glassResolutionScale(blurRadiusDp)).coerceIn(.05f, 1f),
        playerActive = LocalGlassPlayerOverlayActive.current,
        menu = menu,
        queue = LocalQueueGlassBackdrop.current != null,
    )
    val blurDp = blurBudget.radiusDp
    val resolution = blurBudget.resolution
    val blurPx = with(density) { blurDp.dp.toPx() } * resolution
    val heightPx = with(density) { (settings.depth * 48f).dp.toPx() } * resolution
    val amountPx = with(density) { (settings.amount * 48f).dp.toPx() } * resolution
    val saturation = glassSaturation(settings.vibrancy)
    val shapeBlock: () -> Shape = remember(cornerShape) { { cornerShape } }
    val effects: BackdropEffectScope.() -> Unit = remember(
        saturation, blurPx, heightPx, amountPx, renderer, applyEdgeEffects,
        settings.depthEffect, settings.dispersion,
    ) {
        {
            if (saturation != 1f) colorControls(saturation = saturation)
            if (blurPx > 0f) blur(blurPx)
            if (renderer == GlassRenderer.Refraction && applyEdgeEffects && size.minDimension > 24f) {
                val reach = size.minDimension * 0.45f
                lens(
                    refractionHeight = heightPx.coerceAtMost(reach),
                    refractionAmount = amountPx.coerceAtMost(reach),
                    depthEffect = settings.depthEffect,
                    dispersion = if (settings.dispersion) 1f else 0f,
                )
            }
        }
    }
    val highlight: (() -> Highlight?)? = remember(applyEdgeEffects, rimColor, rimAlpha, menu) {
        if (applyEdgeEffects && rimAlpha > 0f) ({ Highlight(width = GlassRimWidth,
            style = HighlightStyle.Default(color = rimColor.copy(alpha = rimAlpha), angle = 45f),
            excludeBottom = menu) }) else null
    }
    val shadow: (() -> Shadow?)? = remember(applyEdgeEffects, drawShadow) {
        if (applyEdgeEffects && drawShadow) ({ Shadow.Default }) else null
    }
    val surface: DrawScope.() -> Unit = remember(film, opacity, onDrawTint, drawFilm) {
        {
            if (drawFilm && opacity > 0f) drawRect(film.copy(alpha = film.alpha * opacity))
            onDrawTint?.invoke(this)
        }
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = shapeBlock,
        effects = effects,
        highlight = highlight,
        shadow = shadow,
        onDrawSurface = surface,
        backdropScale = resolution,
        layerBlock = layerBlock,
        exportedBackdrop = exportedBackdrop,
    )
}
