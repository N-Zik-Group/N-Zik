package app.n_zik.android.components.player

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.contrastAgainst
import app.n_zik.android.components.glass.effectiveSurfaceColor
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
private const val MaxSampleAttempts = 8

internal val LocalPlayerBackdropSampling = compositionLocalOf<PlayerBackdropSampling?> { null }
internal val LocalPlayerArtworkUnderControls = compositionLocalOf { false }

/** Reads only the dedicated background layers; player foreground never enters this source. */
@Stable
internal class PlayerBackdropSampling(
    internal val initialSourceVersion: Int? = null,
    private val previousSurface: Color? = null,
    private val provisionalSurface: State<Color?>? = null,
    internal val retainSettledContrast: Boolean = false,
) {
    var coordinates: LayoutCoordinates? = null
    var isActive by mutableStateOf(false)
        internal set
    var finished by mutableStateOf(false)
        internal set
    private var sample by mutableStateOf<BackdropSample?>(null)
    internal var measurementVersion by mutableIntStateOf(0)
        private set
    var commonSurface by mutableStateOf<Color?>(null)
        private set
    internal val visibleEstimate: Color? get() = provisionalSurface?.value
    val hasProvisionalSurface: Boolean get() = visibleEstimate != null
    val foregroundSurface: Color?
        get() = commonSurface ?: visibleEstimate ?:
            previousSurface.takeIf { !finished || retainSettledContrast }
    internal val hasDecision: Boolean get() = commonSurface != null
    private val regions = mutableMapOf<Any, Rect>()

    internal fun registerRegion(token: Any, bounds: Rect?) {
        if (bounds != null && bounds.width > 0f && bounds.height > 0f) regions[token] = bounds
        else regions.remove(token)
    }

    internal fun removeRegion(token: Any) { regions.remove(token) }

    internal fun publish(pixels: IntArray, origin: Offset, size: IntSize,
        capturedEstimate: Color? = null) {
        if (hasDecision) return
        val previous = sample
        val candidate = BackdropSample(pixels, origin, size, capturedEstimate)
        val valid = regions.values.filter {
            it.left.isFinite() && it.top.isFinite() && it.right.isFinite() && it.bottom.isFinite() &&
                it.right > origin.x && it.bottom > origin.y &&
                it.left < origin.x + size.width && it.top < origin.y + size.height
        }
        val surface = if (valid.isEmpty()) null else {
            val union = Rect(valid.minOf { it.left }, valid.minOf { it.top },
                valid.maxOf { it.right }, valid.maxOf { it.bottom })
            colorAt(candidate, union)
        }
        if (previous == null || previous.origin != origin || previous.size != size ||
            previous.capturedEstimate != capturedEstimate || !previous.pixels.contentEquals(pixels)) {
            sample = candidate
            measurementVersion++
        }
        if (surface != null) {
            commonSurface = surface
            finished = true
        }
    }

    internal fun regionalSample(color: Color?, film: Color? = null): PlayerBackdropRegionSample? =
        color?.let { PlayerBackdropRegionSample(it, sample?.capturedEstimate?.withFilm(film)) }

    internal fun displayedRegionSurface(
        measured: PlayerBackdropRegionSample?,
        retained: PlayerBackdropRegionSample?,
        film: Color? = null,
    ): Color? {
        if (measured == null && retained == null) return null
        if (measured != null && hasDecision) return measured.color
        val estimate = visibleEstimate?.withFilm(film)
        if (measured != null) return measured.surfaceAt(estimate)
        if (!finished || hasDecision || retainSettledContrast || estimate != null) return retained?.surfaceAt(estimate)
        return null
    }

    /** [boundsInWindow] must describe the actual text/control region, not the whole player. */
    fun colorAt(boundsInWindow: Rect, textFilm: Color? = null): Color? {
        if (finished && !hasDecision) return null
        val current = sample ?: return null
        return colorAt(current, boundsInWindow, textFilm)
    }

    private fun colorAt(current: BackdropSample, boundsInWindow: Rect, textFilm: Color? = null): Color? {
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
        return averagePlayerBackdrop(current.pixels, SampleSize, normalized, textFilm)
    }
}

private data class BackdropSample(
    val pixels: IntArray,
    val origin: Offset,
    val size: IntSize,
    val capturedEstimate: Color?,
)

private class PlayerBackdropSurfaceCache(var color: Color? = null)

internal data class PlayerBackdropRegionSample(val color: Color, val capturedEstimate: Color?) {
    fun surfaceAt(estimate: Color?): Color {
        val before = capturedEstimate ?: return color
        if (estimate == null || estimate == before) return color
        return Color(
            (color.red + estimate.red - before.red).coerceIn(0f, 1f),
            (color.green + estimate.green - before.green).coerceIn(0f, 1f),
            (color.blue + estimate.blue - before.blue).coerceIn(0f, 1f),
        )
    }
}

private fun Color.withFilm(film: Color?): Color = if (film == null) this else effectiveSurfaceColor(this, film)

private class PlayerBackdropRegionCache(var sample: PlayerBackdropRegionSample? = null)

/** Register real control bounds, but share one settled background decision across the player. */
@Composable
internal fun rememberPlayerBackdropRegionColor(
    bounds: Rect?,
    useLocalSample: Boolean = false,
    textFilm: Color? = null,
    fallbackToSharedSurface: Boolean = true,
): Color? {
    val sampling = LocalPlayerBackdropSampling.current
    val lastLocal = remember { PlayerBackdropRegionCache() }
    val token = remember { Any() }
    DisposableEffect(sampling, token) {
        onDispose { sampling?.removeRegion(token) }
    }
    val version = if (useLocalSample) sampling?.measurementVersion else null
    val measured = remember(sampling, bounds, version, sampling?.finished, textFilm) {
        if (version != null && version > 0 && bounds != null && sampling != null)
            sampling.regionalSample(sampling.colorAt(bounds, textFilm), textFilm) else null
    }
    SideEffect {
        sampling?.registerRegion(token, bounds)
        if (measured != null) lastLocal.sample = measured
    }
    val retained = lastLocal.sample.takeIf { useLocalSample && bounds != null }
    return sampling?.displayedRegionSurface(measured, retained, textFilm)
        ?: sampling?.foregroundSurface?.takeIf { fallbackToSharedSurface }?.withFilm(textFilm)
}

/** Measure the pixels under the individual text or transport control using the shared recording. */
@Composable
internal fun rememberPlayerControlBackdrop(
    enabled: Boolean,
    film: Color? = null,
    measureText: Boolean = false,
): Pair<Modifier, Color?> {
    if (!enabled) return Modifier to null
    val bounds = remember { mutableStateOf<Rect?>(null) }
    val modifier = remember { Modifier.onGloballyPositioned { bounds.value = it.boundsInWindow() } }
    return modifier to rememberPlayerBackdropRegionColor(
        bounds.value, useLocalSample = true, fallbackToSharedSurface = false,
        textFilm = film.takeIf { measureText },
    )
}

/** Normalized region sampling is independent of Android rendering and easy to exercise. */
internal fun averagePlayerBackdrop(pixels: IntArray, size: IntSize, region: Rect, textFilm: Color? = null): Color? {
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
    val textSurfaces = textFilm?.let { ArrayList<Pair<Float, Color>>(total) }
    for (y in top until bottom) for (x in left until right) {
        val pixel = pixels[y * size.width + x]
        // Rounded clipping can leave a few transparent edge pixels in an otherwise usable region.
        if ((pixel ushr 24) < 250) continue
        red += (pixel ushr 16) and 255
        green += (pixel ushr 8) and 255
        blue += pixel and 255
        count++
        if (textSurfaces != null && textFilm != null) {
            val surface = effectiveSurfaceColor(Color(pixel), textFilm)
            textSurfaces.add(surface.luminance() to surface)
        }
    }
    // Sparse or mostly translucent recordings cannot describe the displayed background.
    if (count == 0 || count * 10 < total * 9) return null
    if (textSurfaces != null) {
        // Choose the ink that stays legible across a larger part of a mottled background.
        // Ignore isolated pixels at the two ends so a sliver of artwork cannot flip the whole line.
        textSurfaces.sortBy { it.first }
        val dark = textSurfaces[(count - 1) / 4].second
        val light = textSurfaces[(count - 1) * 3 / 4].second
        return if (contrastAgainst(Color.White, light) >= contrastAgainst(Color.Black, dark)) dark else light
    }
    return Color((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
}

@Composable
internal fun rememberPlayerBackdropSampling(
    backdrop: Backdrop,
    active: Boolean,
    decisionKey: Any = Unit,
    renderedBackgroundReady: Boolean? = null,
    provisionalSurface: State<Color?>? = null,
    retainSettledContrast: Boolean = false,
): PlayerBackdropSampling {
    val lastSurface = remember(backdrop) { PlayerBackdropSurfaceCache() }
    val latestEstimate = rememberUpdatedState(provisionalSurface)
    val visibleEstimate = remember { derivedStateOf { latestEstimate.value?.value } }
    val state = remember(backdrop, decisionKey, retainSettledContrast) {
        PlayerBackdropSampling(backdrop.contentVersion, lastSurface.color, visibleEstimate, retainSettledContrast)
    }
    val activeState by rememberUpdatedState(state.takeIf { active && renderedBackgroundReady != false })
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val sampledSurface = state.commonSurface
    SideEffect {
        state.isActive = active
        sampledSurface?.let { lastSurface.color = it }
    }
    LaunchedEffect(state, active, density, layoutDirection, renderedBackgroundReady) {
        if (state.hasDecision || activeState !== state) return@LaunchedEffect
        // A failed hidden prewarm may retry when the player becomes visible again.
        state.finished = false
        // Allow the scene to draw the new frame before reading its small backdrop.
        delay(32L)
        withContext(NzikDispatchers.UI) capture@ {
            var warned = false
            var attempts = 0
            while (isActive && activeState === state && !state.hasDecision && attempts < MaxSampleAttempts) {
                // Bound idle polls too: a hidden retained player may never redraw its source.
                attempts++
                val coordinates = state.coordinates?.takeIf { it.isAttached }
                if (coordinates != null && coordinates.size.width > 0 && coordinates.size.height > 0) {
                    val size = coordinates.size
                    val origin = coordinates.positionInWindow()
                    val version = backdrop.contentVersion
                    // Never decide from the previous track's retained background recording.
                    val recordingReady = renderedBackgroundReady == true || version == null || version != state.initialSourceVersion
                    if (recordingReady) {
                        try {
                            val capturedEstimate = state.visibleEstimate
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
                            if (activeState !== state) return@capture
                            val image = layer.toImageBitmap().asAndroidBitmap()
                            val pixels = withContext(NzikDispatchers.MEDIA) {
                                if (activeState !== state) return@withContext null
                                // Reading a hardware bitmap requires a software copy and can wait for the GPU.
                                val hardware = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                    image.config == Bitmap.Config.HARDWARE
                                val readable = if (hardware)
                                    checkNotNull(image.copy(Bitmap.Config.ARGB_8888, false)) else image
                                val pixels = IntArray(SampleSize.width * SampleSize.height)
                                try {
                                    readable.getPixels(pixels, 0, SampleSize.width, 0, 0, SampleSize.width, SampleSize.height)
                                } finally {
                                    if (readable !== image) readable.recycle()
                                }
                                pixels
                            }
                            if (activeState !== state || pixels == null) return@capture
                            Snapshot.withMutableSnapshot {
                                state.publish(pixels, origin, size, capturedEstimate = capturedEstimate)
                            }
                            if (state.hasDecision) Timber.tag("PlayerBackdropSampling").d(
                                "Background sample ready: %dx%d, opaque=%d/%d", size.width, size.height,
                                pixels.count { (it ushr 24) >= 250 }, pixels.size,
                            )
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
            if (activeState === state && isActive) state.finished = true
        }
    }
    return state
}
