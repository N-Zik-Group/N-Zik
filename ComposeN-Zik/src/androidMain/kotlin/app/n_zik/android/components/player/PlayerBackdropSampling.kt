package app.n_zik.android.components.player

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.ceil
import kotlin.math.floor

private val SampleSize = IntSize(32, 64)
private const val SampleIntervalMs = 120L
private const val DecisionSettleMs = 120L

internal val LocalPlayerBackdropSampling = compositionLocalOf<PlayerBackdropSampling?> { null }

/** Reads only the dedicated background layers; player foreground never enters this source. */
@Stable
internal class PlayerBackdropSampling(internal val initialSourceVersion: Int? = null) {
    var coordinates: LayoutCoordinates? = null
    var isActive by mutableStateOf(false)
        internal set
    var finished by mutableStateOf(false)
        internal set
    private var sample by mutableStateOf<BackdropSample?>(null)
    var commonSurface by mutableStateOf<Color?>(null)
        private set
    internal val hasDecision: Boolean get() = commonSurface != null
    private val regions = mutableMapOf<Any, Rect>()

    internal fun registerRegion(token: Any, bounds: Rect?) {
        if (bounds != null && bounds.width > 0f && bounds.height > 0f) regions[token] = bounds
        else regions.remove(token)
    }

    internal fun removeRegion(token: Any) { regions.remove(token) }

    internal fun publish(pixels: IntArray, origin: Offset, size: IntSize) {
        if (hasDecision) return
        val previous = sample
        if (previous == null || previous.origin != origin || previous.size != size ||
            !previous.pixels.contentEquals(pixels)) sample = BackdropSample(pixels, origin, size)
        val valid = regions.values.filter {
            it.left.isFinite() && it.top.isFinite() && it.right.isFinite() && it.bottom.isFinite() &&
                it.right > origin.x && it.bottom > origin.y &&
                it.left < origin.x + size.width && it.top < origin.y + size.height
        }
        if (valid.isNotEmpty()) {
            val union = Rect(valid.minOf { it.left }, valid.minOf { it.top },
                valid.maxOf { it.right }, valid.maxOf { it.bottom })
            commonSurface = colorAt(union)
            if (commonSurface != null) finished = true
        }
    }

    /** [boundsInWindow] must describe the actual text/control region, not the whole player. */
    fun colorAt(boundsInWindow: Rect): Color? {
        val current = sample ?: return null
        val size = current.size
        if (size.width <= 0 || size.height <= 0) return null
        // The retained sheet can move between prewarming and opening without changing its bitmap.
        val origin = coordinates?.takeIf { it.isAttached && it.size == size }?.positionInWindow() ?: current.origin
        val normalized = Rect(
            (boundsInWindow.left - origin.x) / size.width,
            (boundsInWindow.top - origin.y) / size.height,
            (boundsInWindow.right - origin.x) / size.width,
            (boundsInWindow.bottom - origin.y) / size.height,
        )
        return averagePlayerBackdrop(current.pixels, SampleSize, normalized)
    }
}

private data class BackdropSample(val pixels: IntArray, val origin: Offset, val size: IntSize)

private class RegionColorCache(var color: Color? = null)

/** Register real control bounds, but share one settled background decision across the player. */
@Composable
internal fun rememberPlayerBackdropRegionColor(bounds: Rect?): Color? {
    val sampling = LocalPlayerBackdropSampling.current
    val cached = remember(sampling) { RegionColorCache() }
    val token = remember(sampling) { Any() }
    DisposableEffect(sampling, token) {
        onDispose { sampling?.removeRegion(token) }
    }
    val current = sampling?.commonSurface
    SideEffect {
        sampling?.registerRegion(token, bounds)
        if (current != null) cached.color = current
    }
    return current ?: cached.color
}

/** Normalized region sampling is independent of Android rendering and easy to exercise. */
internal fun averagePlayerBackdrop(pixels: IntArray, size: IntSize, region: Rect): Color? {
    if (size.width <= 0 || size.height <= 0 || pixels.size != size.width * size.height ||
        !region.left.isFinite() || !region.top.isFinite() || !region.right.isFinite() || !region.bottom.isFinite() ||
        region.width <= 0f || region.height <= 0f || region.right <= 0f || region.bottom <= 0f ||
        region.left >= 1f || region.top >= 1f) return null
    val left = floor(region.left.coerceIn(0f, 1f) * size.width).toInt().coerceAtMost(size.width - 1)
    val top = floor(region.top.coerceIn(0f, 1f) * size.height).toInt().coerceAtMost(size.height - 1)
    val right = ceil(region.right.coerceIn(0f, 1f) * size.width).toInt().coerceIn(left + 1, size.width)
    val bottom = ceil(region.bottom.coerceIn(0f, 1f) * size.height).toInt().coerceIn(top + 1, size.height)
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0
    val total = (right - left) * (bottom - top)
    for (y in top until bottom) for (x in left until right) {
        val pixel = pixels[y * size.width + x]
        // Rounded clipping can leave a few transparent edge pixels in an otherwise usable region.
        if ((pixel ushr 24) < 250) continue
        red += (pixel ushr 16) and 255
        green += (pixel ushr 8) and 255
        blue += pixel and 255
        count++
    }
    // Sparse or mostly translucent recordings cannot describe the displayed background.
    return if (count == 0 || count * 10 < total * 9) null
        else Color((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
}

@Composable
internal fun rememberPlayerBackdropSampling(
    backdrop: Backdrop,
    active: Boolean,
    decisionKey: Any = Unit,
    backgroundTransitionMs: Int = 0,
): PlayerBackdropSampling {
    val state = remember(backdrop, decisionKey) { PlayerBackdropSampling(backdrop.contentVersion) }
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    SideEffect { state.isActive = active }
    LaunchedEffect(state, active, density, layoutDirection, backgroundTransitionMs) {
        if (!active || state.hasDecision) return@LaunchedEffect
        // A failed hidden prewarm may retry when the player becomes visible again.
        state.finished = false
        // Let the existing cover-color fade finish before freezing its new background.
        delay(maxOf(DecisionSettleMs, backgroundTransitionMs.toLong() + 50L))
        withContext(NzikDispatchers.UI) {
            var warned = false
            var attempts = 0
            while (isActive && !state.hasDecision && attempts < 8) {
                // Bound idle polls too: a hidden retained player may never redraw its source.
                attempts++
                val coordinates = state.coordinates?.takeIf { it.isAttached }
                if (coordinates != null && coordinates.size.width > 0 && coordinates.size.height > 0) {
                    val size = coordinates.size
                    val origin = coordinates.positionInWindow()
                    val version = backdrop.contentVersion
                    // Never decide from the previous track's retained background recording.
                    if (version == null || version != state.initialSourceVersion) {
                        try {
                            layer.record(density, layoutDirection, SampleSize) {
                                // Preserve full-scene draw dimensions while rasterizing only a tiny GPU target.
                                val previousDrawSize = drawContext.size
                                drawContext.size = Size(size.width.toFloat(), size.height.toFloat())
                                try {
                                    scale(SampleSize.width.toFloat() / size.width,
                                        SampleSize.height.toFloat() / size.height, Offset.Zero) {
                                        with(backdrop) { drawBackdrop(density, coordinates) }
                                    }
                                } finally { drawContext.size = previousDrawSize }
                            }
                            val image = layer.toImageBitmap().asAndroidBitmap()
                            // Hardware-backed results require a software copy before reading pixels.
                            val software = image.copy(Bitmap.Config.ARGB_8888, false)
                            try {
                                val pixels = IntArray(SampleSize.width * SampleSize.height)
                                software.getPixels(pixels, 0, SampleSize.width, 0, 0, SampleSize.width, SampleSize.height)
                                state.publish(pixels, origin, size)
                                if (state.hasDecision) Timber.tag("PlayerBackdropSampling").d(
                                    "Background sample ready: %dx%d, opaque=%d/%d", size.width, size.height,
                                    pixels.count { (it ushr 24) >= 250 }, pixels.size,
                                )
                            } finally { software.recycle() }
                            warned = false
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: RuntimeException) {
                            if (!warned) Timber.tag("PlayerBackdropSampling").w(failure, "Background sample unavailable")
                            warned = true
                        }
                    }
                }
                delay(SampleIntervalMs)
            }
            if (isActive) state.finished = true
        }
    }
    return state
}
