package app.n_zik.android.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.isLandscape
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassMaterialContainerScope
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
    val regions = mutableStateMapOf<Any, Rect>()
    val coordinates = mutableMapOf<Any, LayoutCoordinates>()
    val horizontalInsets = mutableMapOf<Any, Float>()
    val roles = mutableMapOf<Any, Any?>()
    fun update() {
        val origin = host?.takeIf { it.isAttached } ?: return
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

/** One passive plate follows the actual metadata/time regions without changing their measurement.
 * A user's swapped timeline may place transport between those regions; its placement stays intact.
 */
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
    val x = with(LocalDensity.current) { 10.dp.toPx() }
    val y = with(LocalDensity.current) { PlayerInformationVerticalPadding.toPx() }
    val bounds = scope.regions.values.reduceOrNull { a, b -> Rect(
        minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
        ?.let {
            // Keep the plate and centered duration capsule aligned with the native
            // information/timeline rows, even when the icon rail overflows only left.
            val center = scope.regions.filterKeys { key -> key in scope.horizontalInsets }
                .values.maxByOrNull { region -> region.width }?.center?.x ?: it.center.x
            val requestedRadius = maxOf(center - it.left, it.right - center) + x
            val viewport = scope.viewport
            val availableRadius = viewport?.let { area -> minOf(center - area.left, area.right - center) }
                ?.coerceAtLeast(0f) ?: requestedRadius
            val radius = minOf(requestedRadius, availableRadius)
            val rail = if (landscape) scope.toolbarBounds else null
            Rect(rail?.left ?: (center - radius), it.top - y,
                rail?.right ?: (center + radius), it.bottom + y)
        }
    val surfaceTop = if (material || contour) scope.host?.takeIf { it.isAttached }
        ?.let { geometry?.bounds(it)?.top }?.let { top -> bounds?.top?.plus(top) } else null
    SideEffect {
        geometry?.informationSurfaceTop = surfaceTop
        val visibleTop = if (material || contour) bounds?.top else scope.regions.values.minOfOrNull { it.top }
        geometry?.landscapeInformationTopOutset = if (landscape)
            visibleTop?.let { (-it).coerceAtLeast(0f) } else 0f
    }
    DisposableEffect(geometry) {
        onDispose {
            geometry?.informationSurfaceTop = null
            geometry?.landscapeInformationTopOutset = null
        }
    }
    // The export records only this passive material, never the children or player controls.
    GlassMaterialContainerScope(Modifier, includeParent = true, material = if (material && bounds != null) {
        { exported -> Modifier.glassMaterial(settings.forArea(GlassArea.ActionButtons), shape,
            ownBackdrop = parent, separateWindow = parent == null, exportedBackdrop = exported) }
    } else null) { surface ->
        CompositionLocalProvider(LocalPlayerInformationBounds provides scope) {
            Box(modifier.onGloballyPositioned {
                scope.host = it; scope.viewportHost = geometry?.host; scope.update()
            }) {
                if (landscape) {
                    // Measure the same outer rail as the lower toolbar without remeasuring its content.
                    Box(Modifier.matchParentSize().playerActionToolbarWidth(landscape = true)
                        .onGloballyPositioned { scope.toolbarHost = it; scope.update() })
                }
                if ((material || contour) && bounds != null) {
                    Box(Modifier.matchParentSize().layout { measurable, constraints ->
                        val pane = measurable.measure(Constraints.fixed(
                            bounds.width.roundToInt().coerceAtLeast(0), bounds.height.roundToInt().coerceAtLeast(0)))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            pane.place(bounds.left.roundToInt(), bounds.top.roundToInt())
                        }
                    }.testTag("player.informationBlock.surface")
                        .then(if (material) surface else Modifier.glassAreaContour(settings, GlassArea.ActionButtons, shape)))
                }
                content()
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
    val region = Modifier.testTag("player.informationRegion.$key").onGloballyPositioned { child ->
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
    return onGloballyPositioned { child ->
        scope.coordinates[token] = child
        scope.roles[token] = role
        scope.update()
    }
}
