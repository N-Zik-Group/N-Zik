package app.n_zik.android.components.player

import android.graphics.BlurMaskFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.internal.InverseLayerScope

/** Shares passive artwork recordings between a clipped pager and its player backdrop. */
class PlayerArtworkShadowOverflow internal constructor() {
    internal var coordinates by mutableStateOf<LayoutCoordinates?>(null)
    internal var hostCoordinates by mutableStateOf<LayoutCoordinates?>(null)
    internal var fade by mutableStateOf(0f)
    internal val shadows = mutableStateMapOf<Any, () -> ArtworkShadowFrame?>()
}

internal data class ArtworkShadowFrame(
    val coordinates: LayoutCoordinates,
    val shape: Shape,
    val alpha: Float,
    val zIndex: Float,
    val layer: GraphicsLayer,
    val shadow: Boolean,
    val imageOverflow: Boolean,
    val shadowCache: ArtworkOverflowShadowCache,
)

internal class ArtworkOverflowShadowCache {
    var imageVersion = 0
    private var shape: Shape? = null
    private var size = Size.Zero
    private var density = 0f
    private var layoutDirection: LayoutDirection? = null
    private var cachedOutline: Outline? = null
    val mask = Path()
    val paint = Paint()
    val alphaPaint = Paint()

    fun outline(frame: ArtworkShadowFrame, scope: DrawScope): Outline {
        val newSize = Size(frame.coordinates.size.width.toFloat(), frame.coordinates.size.height.toFloat())
        val previous = cachedOutline
        if (previous != null && shape == frame.shape && size == newSize &&
            density == scope.density && layoutDirection == scope.layoutDirection) return previous
        shape = frame.shape
        size = newSize
        density = scope.density
        layoutDirection = scope.layoutDirection
        return frame.shape.createOutline(newSize, scope.layoutDirection, scope).also { outline ->
            cachedOutline = outline
            mask.reset()
            when (outline) {
                is Outline.Rectangle -> mask.addRect(outline.rect)
                is Outline.Rounded -> mask.addRoundRect(outline.roundRect)
                is Outline.Generic -> mask.addPath(outline.path)
            }
            paint.asFrameworkPaint().maskFilter = BlurMaskFilter(8f * density, BlurMaskFilter.Blur.NORMAL)
        }
    }
}

/** Keeps a separate passive paint surface without changing artwork or gesture bounds. */
@Composable
fun rememberPlayerArtworkShadowOverflow(): PlayerArtworkShadowOverflow = remember { PlayerArtworkShadowOverflow() }

/** Samples only passive artwork replay over the existing player background, never its controls. */
@Composable
fun rememberPlayerArtworkControlsBackdrop(
    parent: Backdrop,
    overflow: PlayerArtworkShadowOverflow,
    enabled: Boolean,
): Backdrop = if (!enabled) parent else remember(parent, overflow) {
    object : Backdrop {
        private val inverse = InverseLayerScope()
        private val maskPaint = Paint()
        override val isCoordinatesDependent = true
        override val contentVersion: Int?
            get() {
                val parentVersion = parent.contentVersion
                var version = parentVersion ?: 0
                val host = overflow.hostCoordinates?.takeIf { it.isAttached }
                val pager = overflow.coordinates?.takeIf { it.isAttached }
                version = 31 * version + overflow.fade.hashCode()
                if (host != null && pager != null) {
                    version = 31 * version + host.size.hashCode()
                    version = 31 * version + host.positionInWindow().hashCode()
                    version = 31 * version + host.localBoundingBoxOf(pager, clipBounds = false).hashCode()
                    overflow.shadows.values.forEach { readFrame ->
                        val frame = readFrame() ?: return@forEach
                        version = 31 * version + host.localBoundingBoxOf(frame.coordinates, clipBounds = false).hashCode()
                        version = 31 * version + frame.alpha.hashCode()
                        version = 31 * version + frame.zIndex.hashCode()
                        version = 31 * version + frame.shape.hashCode()
                        version = 31 * version + frame.shadow.hashCode()
                        version = 31 * version + frame.imageOverflow.hashCode()
                        version = 31 * version + frame.shadowCache.imageVersion
                        version = 31 * version + System.identityHashCode(frame.layer)
                    }
                }
                return if (parentVersion == null) null else version
            }

        override fun DrawScope.drawBackdrop(
            density: Density,
            coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?,
        ) {
            with(parent) { drawBackdrop(density, coordinates, layerBlock) }
            val target = coordinates?.takeIf { it.isAttached } ?: return
            val host = overflow.hostCoordinates?.takeIf { it.isAttached } ?: return
            val pager = overflow.coordinates?.takeIf { it.isAttached } ?: return
            withTransform({
                if (layerBlock != null) with(inverse.apply { reset() }) { inverseTransform(density, layerBlock) }
                val offset = host.localPositionOf(target)
                translate(-offset.x, -offset.y)
            }) {
                drawPlayerArtworkOverflow(overflow, host, pager, maskPaint)
            }
        }
    }
}

/** Marks the pager viewport independently of the larger, navigation-safe paint host. */
fun Modifier.playerArtworkOverflowViewport(overflow: PlayerArtworkShadowOverflow): Modifier =
    onGloballyPositioned { overflow.coordinates = it }

/** Uses the same continuous fade for the pager and its passive artwork overflow. */
@Composable
fun Modifier.playerArtworkOverflowFade(
    overflow: PlayerArtworkShadowOverflow,
    enabled: Boolean,
    fade: Float,
): Modifier {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val strength = if (enabled && fade.isFinite()) fade.coerceIn(0f, .5f) else 0f
    SideEffect { overflow.fade = strength }
    DisposableEffect(overflow) {
        onDispose { overflow.fade = 0f }
    }
    if (strength == 0f) return this
    return onGloballyPositioned { coordinates = it }
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val local = coordinates?.takeIf { it.isAttached }
            val viewport = overflow.coordinates?.takeIf { it.isAttached }
            if (local != null && viewport != null) {
                drawArtworkOverflowFade(local.localBoundingBoxOf(viewport, clipBounds = false), strength)
            }
        }
}

private fun DrawScope.drawArtworkOverflowFade(viewport: Rect, strength: Float, drawSize: Size = size) {
    if (strength <= 0f || viewport.height <= 0f) return
    // Extend the same gradient past both pager edges, where the replay takes over.
    val extension = 32.dp.toPx()
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            strength to Color.Black,
            (1f - strength) to Color.Black,
            1f to Color.Transparent,
            startY = viewport.top - extension,
            endY = viewport.bottom + extension,
        ),
        blendMode = BlendMode.DstIn,
        size = drawSize,
    )
}

/**
 * Paints overflowing images behind player content, clipped strictly to this host.
 * Apply after its background, on the common ancestor of artwork, header and information.
 * The host must exclude navigation; [playerArtworkOverflowViewport] marks the pager.
 */
@Composable
fun Modifier.playerArtworkShadowOverflow(overflow: PlayerArtworkShadowOverflow): Modifier {
    val hostModifier = onGloballyPositioned { overflow.hostCoordinates = it }
    if (overflow.shadows.isEmpty()) return hostModifier.drawWithContent { clipRect { this@drawWithContent.drawContent() } }
    val foreground = rememberGraphicsLayer()
    val maskPaint = remember { Paint() }
    return hostModifier.drawWithContent {
        // The inner Compose layer updates dirty content synchronously, then this
        // recording contains its cached display-list reference, not the whole UI.
        foreground.record { this@drawWithContent.drawContent() }
        val host = overflow.hostCoordinates?.takeIf { it.isAttached }
        val pager = overflow.coordinates?.takeIf { it.isAttached }
        if (host == null || pager == null) {
            clipRect { drawLayer(foreground) }
            return@drawWithContent
        }
        clipRect {
            drawPlayerArtworkOverflow(overflow, host, pager, maskPaint)
            // Headers, information and the selected image always remain in front.
            drawLayer(foreground)
        }
    }.graphicsLayer()
}

private fun DrawScope.drawPlayerArtworkOverflow(
    overflow: PlayerArtworkShadowOverflow,
    host: LayoutCoordinates,
    pager: LayoutCoordinates,
    maskPaint: Paint,
) {
    val hostSize = Size(host.size.width.toFloat(), host.size.height.toFloat())
    val viewport = host.localBoundingBoxOf(pager, clipBounds = false)
    val frames = overflow.shadows.values.mapNotNull { it() }.sortedBy { it.zIndex }
    fun drawOverflowBand(top: Float, bottom: Float) {
        if (bottom <= top) return
        val band = Rect(0f, top, hostSize.width, bottom)
        clipRect(band.left, band.top, band.right, band.bottom) {
            val masked = overflow.fade > 0f
            if (masked) drawContext.canvas.saveLayer(band, maskPaint)
            frames.forEach { frame ->
                if (frame.coordinates.isAttached) {
                    val bounds = host.localBoundingBoxOf(frame.coordinates, clipBounds = false)
                    if (frame.alpha > 0f && artworkShadowIntersectsViewport(bounds, viewport)) {
                        if (frame.shadow && bounds.inflate(32.dp.toPx()).overlaps(band)) {
                            if (frame.imageOverflow) drawArtworkOverflowShadow(bounds, frame)
                            else {
                                val bleed = 32.dp.toPx()
                                clipRect(top = viewport.top - bleed, bottom = viewport.bottom + bleed) {
                                    drawArtworkOverflowShadow(bounds, frame)
                                }
                            }
                        }
                        if (frame.imageOverflow && bounds.overlaps(band)) drawArtworkOverflowImage(bounds, frame)
                    }
                }
            }
            if (masked) {
                drawArtworkOverflowFade(viewport, overflow.fade, hostSize)
                drawContext.canvas.restore()
            }
        }
    }
    drawOverflowBand(0f, viewport.top.coerceIn(0f, hostSize.height))
    drawOverflowBand(viewport.bottom.coerceIn(0f, hostSize.height), hostSize.height)
}

internal fun artworkShadowIntersectsViewport(bounds: Rect, viewport: Rect): Boolean =
    bounds.left.isFinite() && bounds.top.isFinite() && bounds.right.isFinite() && bounds.bottom.isFinite() &&
        bounds.width > 0f && bounds.height > 0f && bounds.overlaps(viewport)

/** Captures the original rounded image without adding any input or semantics surface. */
@Composable
internal fun Modifier.registerPlayerArtworkShadow(
    overflow: PlayerArtworkShadowOverflow?,
    shape: Shape,
    alpha: () -> Float,
    zIndex: () -> Float,
    isVisible: () -> Boolean = { true },
    shadow: Boolean = true,
    imageOverflow: Boolean = true,
): Modifier {
    if (overflow == null || (!shadow && !imageOverflow)) return this
    val owner = remember { Any() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentShape by rememberUpdatedState(shape)
    val currentAlpha by rememberUpdatedState(alpha)
    val currentZIndex by rememberUpdatedState(zIndex)
    val currentIsVisible by rememberUpdatedState(isVisible)
    val currentShadow by rememberUpdatedState(shadow)
    val currentImageOverflow by rememberUpdatedState(imageOverflow)
    val layer = rememberGraphicsLayer()
    val shadowCache = remember { ArtworkOverflowShadowCache() }
    DisposableEffect(overflow, owner) {
        overflow.shadows[owner] = {
            coordinates?.takeIf { currentIsVisible() && it.isAttached }?.let {
                ArtworkShadowFrame(it, currentShape, currentAlpha(), currentZIndex(), layer, currentShadow, currentImageOverflow, shadowCache)
            }
        }
        onDispose { overflow.shadows.remove(owner) }
    }
    return onGloballyPositioned { coordinates = it }.drawWithContent {
        if (currentImageOverflow) {
            layer.record { this@drawWithContent.drawContent() }
            shadowCache.imageVersion++
            drawLayer(layer)
        } else {
            drawContent()
        }
    }
}

private fun DrawScope.drawArtworkOverflowImage(bounds: Rect, frame: ArtworkShadowFrame) {
    val originalSize = frame.coordinates.size
    if (originalSize.width <= 0 || originalSize.height <= 0 || frame.alpha <= 0f) return
    val canvas = drawContext.canvas
    canvas.save()
    canvas.translate(bounds.left, bounds.top)
    canvas.scale(bounds.width / originalSize.width, bounds.height / originalSize.height)
    val alpha = frame.alpha.coerceIn(0f, 1f)
    if (alpha < 1f) {
        canvas.saveLayer(Rect(0f, 0f, originalSize.width.toFloat(), originalSize.height.toFloat()),
        frame.shadowCache.alphaPaint.apply { color = Color.Black.copy(alpha = alpha) })
    }
    drawLayer(frame.layer)
    if (alpha < 1f) canvas.restore()
    canvas.restore()
}

private fun DrawScope.drawArtworkOverflowShadow(bounds: Rect, frame: ArtworkShadowFrame) {
    val originalWidth = frame.coordinates.size.width.toFloat()
    if (originalWidth <= 0f || frame.alpha <= 0f) return
    val scale = bounds.width / originalWidth
    val originalHeight = frame.coordinates.size.height.toFloat()
    val outline = frame.shadowCache.outline(frame, this)
    val offset = 4.dp.toPx()
    val paint = frame.shadowCache.paint
    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.translate(bounds.left, bounds.top)
        canvas.scale(scale, scale)
        // The recorded image owns this area, including translucent rear cards.
        canvas.clipPath(frame.shadowCache.mask, ClipOp.Difference)
        val alpha = frame.alpha.coerceIn(0f, 1f)
        if (alpha < 1f) {
            val bleed = 32.dp.toPx()
            canvas.saveLayer(Rect(-bleed, -bleed, originalWidth + bleed, originalHeight + bleed),
                frame.shadowCache.alphaPaint.apply { color = Color.Black.copy(alpha = alpha) })
        }
        paint.color = Color.Black.copy(alpha = .75f)
        canvas.save()
        canvas.translate(offset, offset)
        canvas.drawOutline(outline, paint)
        canvas.restore()
        paint.color = Color.Black.copy(alpha = .5f)
        canvas.save()
        canvas.translate(-offset, -offset)
        canvas.drawOutline(outline, paint)
        canvas.restore()
        if (alpha < 1f) canvas.restore()
        canvas.restore()
    }
}
