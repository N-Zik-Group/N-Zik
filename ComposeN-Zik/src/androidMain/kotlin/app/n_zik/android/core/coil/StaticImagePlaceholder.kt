package app.n_zik.android.core.coil

import androidx.annotation.DrawableRes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.navigationSurfaceColor
import app.n_zik.android.components.glass.IconContrast
import app.n_zik.android.components.glass.contrastAdjustedAccent
import app.n_zik.android.components.glass.contrastAgainst
import app.n_zik.android.components.glass.effectiveSurfaceColor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import coil3.compose.AsyncImagePainter
import coil3.compose.CrossfadePainter

/** Immutable frozen frames of the same expressive polygon family as the Material loader. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val staticImagePlaceholderVectors: List<ImageVector> by lazy {
    val polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons
    var scale = 1f
    for (polygon in polygons) {
        val bounds = polygon.calculateBounds()
        val maxBounds = polygon.calculateMaxBounds()
        scale = min(scale, max(
            (bounds[2] - bounds[0]) / (maxBounds[2] - maxBounds[0]),
            (bounds[3] - bounds[1]) / (maxBounds[3] - maxBounds[1]),
        ))
    }
    scale *= LoadingIndicatorDefaults.IndicatorSize.value /
        min(LoadingIndicatorDefaults.ContainerWidth.value, LoadingIndicatorDefaults.ContainerHeight.value)
    polygons.mapIndexed { index, source ->
        val polygon = source.normalized()
        val bounds = polygon.calculateBounds(approximate = false)
        val centerX = (bounds[0] + bounds[2]) / 2f
        val centerY = (bounds[1] + bounds[3]) / 2f
        // Preserve the former shared placeholder's rotation, neutral fill and intrinsic geometry.
        fun x(y: Float) = 12f - (y - centerY) * 24f * scale
        fun y(x: Float) = 12f + (x - centerX) * 24f * scale
        ImageVector.Builder("StaticImagePlaceholder$index", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color(0xff666666))) {
                val first = polygon.cubics.first()
                moveTo(x(first.anchor0Y), y(first.anchor0X))
                polygon.cubics.forEach { cubic ->
                    curveTo(x(cubic.control0Y), y(cubic.control0X),
                        x(cubic.control1Y), y(cubic.control1X),
                        x(cubic.anchor1Y), y(cubic.anchor1X))
                }
                close()
            }
        }.build()
    }
}

/** The identity contains source/revision values, never a freshly rebuilt Coil request. */
@Composable
internal fun rememberStaticImagePlaceholderVector(
    loadIdentity: Any?,
    chooseIndex: (Int) -> Int = { Random.nextInt(it) },
): ImageVector = remember(loadIdentity) {
    staticImagePlaceholderVectors[chooseIndex(staticImagePlaceholderVectors.size)]
}

@Composable
internal fun rememberImageLoadingPainter(
    loadIdentity: Any?,
    @DrawableRes placeholder: Int? = null,
    backgroundColor: Color? = null,
    glyphColor: Color? = null,
    glyphOutlineAlpha: Float = 0f,
): Painter = if (placeholder != null) {
    painterResource(placeholder)
} else {
    val source = rememberVectorPainter(rememberStaticImagePlaceholderVector(loadIdentity))
    if (backgroundColor == null) source else remember(source, backgroundColor, glyphColor, glyphOutlineAlpha) {
        ImagePlaceholderBackgroundPainter(source, backgroundColor, glyphColor, glyphOutlineAlpha)
    }
}

/** Custom resources and non-image glyphs keep their own background policy. */
@Composable
internal fun rememberThemedImageLoadingPainter(
    loadIdentity: Any?,
    @DrawableRes placeholder: Int? = null,
): Painter = if (placeholder != null) {
    rememberImageLoadingPainter(loadIdentity, placeholder)
} else {
    val palette = colorPalette()
    val navigation = navigationSurfaceColor()
    val (background, glyph) = remember(navigation, palette.background0, palette.accent) {
        val plate = effectiveSurfaceColor(palette.background0.copy(alpha = 1f), navigation)
        plate to resolveImagePlaceholderInk(palette.accent, plate)
    }
    rememberImageLoadingPainter(loadIdentity, backgroundColor = background,
        glyphColor = glyph.fill, glyphOutlineAlpha = glyph.outlineAlpha)
}

internal data class ImagePlaceholderInk(val fill: Color, val outlineAlpha: Float = 0f)

/** Keep the plate and shape stable while the accent follows the displayed palette. */
internal fun resolveImagePlaceholderInk(accent: Color, background: Color): ImagePlaceholderInk {
    val minimum = IconContrast + .05f // Keep a small margin for native pixel quantization.
    val originalDeficit = minimum - contrastAgainst(accent, background)
    if (originalDeficit <= 0f) return ImagePlaceholderInk(accent)
    val opaque = accent.copy(alpha = 1f)
    val deficit = minimum - contrastAgainst(opaque, background)
    val backgroundLuminance = background.luminance()
    val adjusted = when {
        deficit <= 0f -> opaque
        backgroundLuminance <= .15f -> contrastAdjustedAccent(opaque, background, minimum, 1f)
        backgroundLuminance >= .21f -> contrastAdjustedAccent(opaque, background, minimum, 0f)
        else -> {
            lerp(contrastAdjustedAccent(opaque, background, minimum, 1f),
                contrastAdjustedAccent(opaque, background, minimum, 0f),
                smoothInkWeight((backgroundLuminance - .15f) / .06f))
        }
    }
    val promoted = lerp(accent, opaque, smoothInkWeight(originalDeficit / .2f))
    val fill = lerp(promoted, adjusted, smoothInkWeight(deficit / .2f))
    // Both edges stay available while a smoothly blended fill crosses the plate color.
    val outline = ((minimum - contrastAgainst(fill, background)) / .65f).coerceIn(0f, 1f)
    return ImagePlaceholderInk(fill, outline)
}

private fun smoothInkWeight(weight: Float): Float {
    val clamped = weight.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}

/** Each rendering sink owns its measured viewport, even when its source painter is shared. */
@Composable
internal fun rememberImageLoadingViewportPainter(source: Painter): Painter =
    remember(source) { ImageLoadingViewportPainter(source) }

@Composable
internal fun rememberFramedImageLoadingPainter(loadIdentity: Any?, @DrawableRes placeholder: Int? = null): Painter =
    rememberImageLoadingViewportPainter(rememberThemedImageLoadingPainter(loadIdentity, placeholder))

/** Share the viewport of a centered image with its loading painter. */
internal fun Modifier.imageLoadingViewport(painter: Painter): Modifier =
    if (painter is ImageLoadingViewportPainter) onSizeChanged { painter.viewport = it.toSize() } else this

private class ImageLoadingViewport(val size: Size, val origin: Offset, val scaledSize: Size) {
    fun boundsAt(childSize: Size): Rect = Rect(
        origin + Offset((childSize.width - scaledSize.width) / 2f, (childSize.height - scaledSize.height) / 2f),
        size,
    )
}

private class ImageLoadingViewportPainter(val source: Painter) : Painter() {
    var viewport = Size.Unspecified
    private var loading: Painter? = null
    private var alpha = 1f
    private var filter: ColorFilter? = null
    override val intrinsicSize get() = source.intrinsicSize
    override fun applyAlpha(alpha: Float): Boolean { this.alpha = alpha; return true }
    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean { filter = colorFilter; return true }

    override fun DrawScope.onDraw() {
        val state = (source as? AsyncImagePainter)?.state?.value
        if (state is AsyncImagePainter.State.Loading) loading = state.painter
        // Scope inside Image's clipping layer: its independent redraws bypass outer draw modifiers.
        // Coil also keeps the outgoing painter private after emitting State.Success.
        if (viewport.width.isFinite() && viewport.height.isFinite()) {
            val position = Alignment.Center.align(size.roundToIntSize(), viewport.roundToIntSize(), layoutDirection)
            val frame = ImageLoadingViewport(viewport, Offset(-position.x.toFloat(), -position.y.toFloat()), size)
            withImageLoadingViewport(loading, frame) {
                withImageLoadingViewport(source, frame) {
                    with(source) { draw(size, alpha, filter) }
                }
            }
        } else with(source) { draw(size, alpha, filter) }
    }
}

private fun withImageLoadingViewport(painter: Painter?, frame: ImageLoadingViewport, draw: () -> Unit) {
    when (painter) {
        is ImagePlaceholderBackgroundPainter -> withPlaceholderViewport(painter, frame, draw)
        is ImageLoadingViewportPainter -> withImageLoadingViewport(painter.source, frame, draw)
        is AsyncImagePainter -> withImageLoadingViewport(painter.state.value.painter, frame, draw)
        is CrossfadePainter -> withImageLoadingViewport(painter.start, frame) {
            withImageLoadingViewport(painter.end, frame, draw)
        }
        else -> draw()
    }
}

private inline fun withPlaceholderViewport(
    painter: ImagePlaceholderBackgroundPainter,
    frame: ImageLoadingViewport,
    draw: () -> Unit,
) {
    val previous = painter.viewport
    painter.viewport = frame
    try {
        draw()
    } finally {
        painter.viewport = previous
    }
}

/** Covers Fit margins without changing intrinsic geometry or observing state in composition. */
internal fun Modifier.imageLoadingBackground(
    painter: Painter,
    isLoading: () -> Boolean = { true },
): Modifier {
    val source = (painter as? ImageLoadingViewportPainter)?.source ?: painter
    return imageLoadingViewport(painter).then(if (source is ImagePlaceholderBackgroundPainter) {
        Modifier.drawBehind { if (isLoading()) drawRect(source.background) }
    } else Modifier)
}

/** The background belongs only to the loading frame, never the decoded artwork. */
private class ImagePlaceholderBackgroundPainter(
    private val source: Painter,
    val background: Color,
    glyphColor: Color?,
    private val outlineAlpha: Float,
) : Painter() {
    var viewport: ImageLoadingViewport? = null
    private val glyphFilter = glyphColor?.let { ColorFilter.tint(it) }
    private val layerPaint by lazy { Paint() }
    private var alpha = DefaultAlpha
    private var colorFilter: ColorFilter? = null
    override val intrinsicSize get() = source.intrinsicSize

    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        this.colorFilter = colorFilter
        return true
    }

    override fun DrawScope.onDraw() {
        // Coil centers the outgoing placeholder inside the crossfade's own draw bounds.
        val frame = viewport?.boundsAt(size) ?: Rect(Offset.Zero, size)
        val side = min(size.minDimension, frame.size.minDimension)
        if (alpha == DefaultAlpha && colorFilter == null) {
            drawFrame(frame, side)
        } else {
            // Compose the glyph and plate together before applying the crossfade or caller alpha.
            layerPaint.alpha = alpha
            layerPaint.colorFilter = colorFilter
            drawContext.canvas.withSaveLayer(frame, layerPaint) { drawFrame(frame, side) }
        }
    }

    private fun DrawScope.drawFrame(frame: Rect, side: Float) {
        drawRect(background, topLeft = frame.topLeft, size = frame.size)
        if (side > 0f) translate((size.width - side) / 2f, (size.height - side) / 2f) {
            val glyphSize = Size(side, side)
            if (outlineAlpha > 0f) {
                val center = Offset(side / 2f, side / 2f)
                scale(1.14f, pivot = center) {
                    with(source) { draw(glyphSize, alpha = outlineAlpha, colorFilter = DarkPlaceholderEdge) }
                }
                scale(1.07f, pivot = center) {
                    with(source) { draw(glyphSize, alpha = outlineAlpha, colorFilter = LightPlaceholderEdge) }
                }
            }
            with(source) { draw(glyphSize, colorFilter = glyphFilter) }
        }
    }
}

private val DarkPlaceholderEdge = ColorFilter.tint(Color.Black)
private val LightPlaceholderEdge = ColorFilter.tint(Color.White)
