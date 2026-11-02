package app.n_zik.android.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.isLandscape
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.inScope
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.searchBubbleShape
import kotlin.math.roundToInt

/** Vertical breathing room shared by the information plate and overlay canvas clearance. */
val PlayerInformationVerticalPadding = 10.dp

internal class PlayerInformationBounds {
    var host: LayoutCoordinates? = null
    var viewportHost: LayoutCoordinates? = null
    var viewport by mutableStateOf<Rect?>(null)
    var toolbarHost: LayoutCoordinates? = null
    var toolbarBounds by mutableStateOf<Rect?>(null)
    var seekBarCoordinates: LayoutCoordinates? = null
    var seekBar by mutableStateOf<Rect?>(null)
    var durationCoordinates: LayoutCoordinates? = null
    var duration by mutableStateOf<Rect?>(null)
    val regions = mutableStateMapOf<Any, Rect>()
    val coordinates = mutableMapOf<Any, LayoutCoordinates>()
    val horizontalInsets = mutableMapOf<Any, Float>()
    val roles = mutableMapOf<Any, Any?>()
    var landscape: Boolean = false
    var horizontalPadding: Float = 0f
    var verticalPadding: Float = 0f

    fun surfaceBounds(): Rect? {
        val union = regions.values.reduceOrNull { a, b -> Rect(
            minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
            ?: return null
        val center = regions.filterKeys { it in horizontalInsets }.values.maxByOrNull { it.width }
            ?.center?.x ?: union.center.x
        val requestedRadius = maxOf(center - union.left, union.right - center) + horizontalPadding
        val availableRadius = viewport?.let { minOf(center - it.left, it.right - center).coerceAtLeast(0f) }
            ?: requestedRadius
        val radius = minOf(requestedRadius, availableRadius)
        val rail = if (landscape) toolbarBounds else null
        return Rect(rail?.left ?: (center - radius), union.top - verticalPadding,
            rail?.right ?: (center + radius), union.bottom + verticalPadding)
    }
    fun update() {
        val origin = host?.takeIf { it.isAttached } ?: return
        seekBar = seekBarCoordinates?.takeIf { it.isAttached }?.let {
            origin.localBoundingBoxOf(it, clipBounds = false)
        }
        duration = durationCoordinates?.takeIf { it.isAttached }?.let {
            origin.localBoundingBoxOf(it, clipBounds = false)
        }
        viewport = origin.localBoundingBoxOf(viewportHost?.takeIf { it.isAttached } ?: origin.findRootCoordinates(), clipBounds = false)
        toolbarBounds = toolbarHost?.takeIf { it.isAttached }?.let {
            origin.localBoundingBoxOf(it, clipBounds = false)
        }
        coordinates.forEach { (key, child) ->
            if (child.isAttached) {
                val rect = origin.localBoundingBoxOf(child, clipBounds = false)
                val inset = (horizontalInsets[key] ?: 0f).coerceAtMost(rect.width / 2f)
                regions[key] = Rect(rect.left + inset, rect.top, rect.right - inset, rect.bottom)
            } else regions.remove(key)
        }
    }
}

internal val LocalPlayerInformationBounds = staticCompositionLocalOf<PlayerInformationBounds?> { null }
private val LocalPlayerInformationRole = staticCompositionLocalOf<Any?> { null }

/** Exposes the track row separately from the durations underneath it. */
@Composable
fun Modifier.playerSeekBarBounds(): Modifier {
    val scope = LocalPlayerInformationBounds.current ?: return this
    DisposableEffect(scope) {
        onDispose { scope.seekBarCoordinates = null; scope.seekBar = null }
    }
    return testTag("player.seekBar.track").onPlaced {
        scope.seekBarCoordinates = it
        scope.update()
    }
}

/** Records the duration row whose text is vertically centered, without changing its measurement. */
@Composable
fun PlayerDurationBounds(content: @Composable () -> Unit) {
    val scope = LocalPlayerInformationBounds.current
    DisposableEffect(scope) {
        onDispose { scope?.durationCoordinates = null; scope?.duration = null }
    }
    Box(Modifier.testTag("player.timeline.duration").onPlaced {
        scope?.durationCoordinates = it
        scope?.update()
    }) { content() }
}

/** A passive plate follows metadata and time regions without changing their measurement. */
@Composable
fun PlayerInformationGlassBlock(
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerHeader),
    landscape: Boolean = isLandscape,
    content: @Composable () -> Unit,
) {
    val settings = settings.inScope(GlassScope.PlayerHeader)
    if (LocalPlayerInformationBounds.current != null) {
        content()
        return
    }
    val material = settings.usesGlassMaterial(GlassArea.ActionButtons)
    val contour = settings.usesGlassContour(GlassArea.ActionButtons)
    val scope = remember { PlayerInformationBounds() }
    val geometry = LocalPlayerTransportGeometry.current
    val shape = searchBubbleShape()
    val parent = LocalGlassContainerBackdrop.current
    scope.landscape = landscape
    scope.horizontalPadding = with(LocalDensity.current) { 10.dp.toPx() }
    scope.verticalPadding = with(LocalDensity.current) { PlayerInformationVerticalPadding.toPx() }
    DisposableEffect(geometry) {
        onDispose {
            geometry?.informationSurfaceTop = null
            geometry?.informationSurfaceBounds = null
            geometry?.landscapeInformationTopOutset = null
        }
    }
    // Every player pane samples the same original scene. Exporting this plate to its
    // children would apply film and blur twice to the time, information rail and heart.
    val surface = if (material) Modifier.glassMaterial(settings.forArea(GlassArea.ActionButtons), shape,
        ownBackdrop = parent, separateWindow = parent == null) else Modifier
    CompositionLocalProvider(LocalPlayerInformationBounds provides scope) {
        Layout(modifier = modifier.onPlaced {
            scope.host = it; scope.viewportHost = geometry?.host; scope.update()
        }, content = {
            Box {
                if (landscape) {
                    // Measure the same outer rail as the lower toolbar without remeasuring its content.
                    Box(Modifier.matchParentSize().playerActionToolbarWidth(landscape = true)
                        .onPlaced { scope.toolbarHost = it; scope.update() })
                }
                content()
            }
            if (material || contour) Box(Modifier.testTag("player.informationBlock.surface")
                .then(if (material) surface else if (contour)
                    Modifier.glassAreaContour(settings, GlassArea.ActionButtons, shape) else Modifier))
        }) { children, constraints ->
            val body = children[0].measure(constraints)
            val lookahead = isLookingAhead
            layout(body.width, body.height) {
                // Content placement publishes current coordinates before the passive pane
                // is measured. A resize never reuses the previous frame's plate geometry.
                body.placeRelative(0, 0)
                if (!lookahead) scope.update()
                val bounds = if (lookahead) null else scope.surfaceBounds()
                val origin = scope.host?.takeIf { it.isAttached }?.let { geometry?.bounds(it) }
                val surfaceBounds = if (material || contour) bounds?.let { rect ->
                    origin?.let { rect.translate(it.left, it.top) }
                } else null
                val visibleTop = if (material || contour) bounds?.top else scope.regions.values.minOfOrNull { it.top }
                if (!lookahead) {
                    geometry?.informationSurfaceTop = surfaceBounds?.top
                    geometry?.informationSurfaceBounds = surfaceBounds
                    geometry?.landscapeInformationTopOutset = if (landscape)
                        visibleTop?.let { (-it).coerceAtLeast(0f) } else 0f
                }
                children.getOrNull(1)?.let { surface ->
                    val pane = surface.measure(Constraints.fixed(
                        bounds?.width?.roundToInt()?.coerceAtLeast(0) ?: 0,
                        bounds?.height?.roundToInt()?.coerceAtLeast(0) ?: 0))
                    pane.place(bounds?.left?.roundToInt() ?: 0, bounds?.top?.roundToInt() ?: 0, zIndex = -1f)
                }
            }
        }
    }
}

/** Register only visible content; disposing a hidden section removes its previous geometry. */
@Composable
fun PlayerInformationGlassRegion(
    key: Any,
    vertical: Boolean = false,
    horizontalContentInset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val scope = LocalPlayerInformationBounds.current
    val token = remember(key) { Any() }
    val inset = with(LocalDensity.current) { horizontalContentInset.toPx() }
    DisposableEffect(scope, token) {
        onDispose {
            scope?.coordinates?.remove(token)
            scope?.horizontalInsets?.remove(token)
            scope?.regions?.remove(token)
            scope?.roles?.remove(token)
        }
    }
    val region = Modifier.testTag("player.informationRegion.$key").onPlaced { child ->
        scope?.let {
            it.coordinates[token] = child
            it.horizontalInsets[token] = inset
            it.roles[token] = key
            it.update()
        }
    }
    // GetSeekBar emits a track Row, its 8dp spacer, then DurationIndicator.
    // Preserve their original parent Column flow; a Box would stack them at the same origin.
    CompositionLocalProvider(LocalPlayerInformationRole provides key) {
        if (vertical) Column(region) { content() } else Box(region) { content() }
    }
}

/** Include the actual passive capsule's overflow when measuring the outer pane, without layout padding. */
@Composable
internal fun Modifier.playerInformationGlassVisualBounds(): Modifier {
    val scope = LocalPlayerInformationBounds.current ?: return this
    val token = remember { Any() }
    val role = LocalPlayerInformationRole.current
    DisposableEffect(scope, token) {
        onDispose { scope.coordinates.remove(token); scope.regions.remove(token); scope.roles.remove(token) }
    }
    return onPlaced { child ->
        scope.coordinates[token] = child
        scope.roles[token] = role
        scope.update()
    }
}
