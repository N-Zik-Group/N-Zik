package app.n_zik.android.components.player

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import app.n_zik.android.components.glass.LocalGlassScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.isGlassMotionAllowed
import kotlin.math.roundToInt

/** Actual lower capsule bounds shared by the player controls and action toolbar. */
class PlayerTransportGeometry {
    var host by mutableStateOf<LayoutCoordinates?>(null)
    private var toolbarSnapshot by mutableStateOf<Rect?>(null)
    private var toolbarHostHeight = 0
    private var visibleToolbarSnapshot by mutableStateOf<Rect?>(null)
    private var visibleToolbarHostHeight = 0
    private var actionPanelSnapshot by mutableStateOf<Rect?>(null)
    private var actionPanelHostHeight = 0
    var toolbar: Rect?
        get() = toolbarSnapshot?.translate(0f, ((host?.size?.height ?: toolbarHostHeight) - toolbarHostHeight).toFloat())
        set(value) { toolbarSnapshot = value; toolbarHostHeight = host?.size?.height ?: 0 }
    var visibleToolbar: Rect?
        get() = visibleToolbarSnapshot?.translate(0f, ((host?.size?.height ?: visibleToolbarHostHeight) - visibleToolbarHostHeight).toFloat())
        set(value) { visibleToolbarSnapshot = value; visibleToolbarHostHeight = host?.size?.height ?: 0 }
    var actionPanel: Rect?
        get() = actionPanelSnapshot?.translate(0f, ((host?.size?.height ?: actionPanelHostHeight) - actionPanelHostHeight).toFloat())
        set(value) { actionPanelSnapshot = value; actionPanelHostHeight = host?.size?.height ?: 0 }
    var informationSurfaceTop by mutableStateOf<Float?>(null)
    var informationSurfaceBounds by mutableStateOf<Rect?>(null)
    var landscapeInformationTopOutset by mutableStateOf<Float?>(null)
    internal var lowerViewport: LayoutCoordinates? = null
    internal var lowerScrollOffset by mutableIntStateOf(0)

    internal fun lowerScrollTranslation(coordinates: LayoutCoordinates): Offset {
        val viewport = lowerViewport?.takeIf { it.isAttached } ?: return Offset.Zero
        var ancestor: LayoutCoordinates? = coordinates
        while (ancestor != null) {
            if (ancestor === viewport) return Offset(0f, -lowerScrollOffset.toFloat())
            ancestor = ancestor.parentLayoutCoordinates
        }
        return Offset.Zero
    }

    fun updateHost(coordinates: LayoutCoordinates) {
        if (host !== coordinates) {
            toolbar = null
            visibleToolbar = null
            actionPanel = null
            informationSurfaceTop = null
            informationSurfaceBounds = null
            landscapeInformationTopOutset = null
        }
        host = coordinates
    }

    fun bounds(coordinates: LayoutCoordinates): Rect? = host?.takeIf { it.isAttached }
        ?.localBoundingBoxOf(coordinates, clipBounds = false)
}

val LocalPlayerTransportGeometry = staticCompositionLocalOf<PlayerTransportGeometry?> { null }

/** Fix the coordinate origin to the player, independent of the sheet's opening translation. */
@Composable
fun Modifier.playerTransportGeometryHost(): Modifier {
    val geometry = LocalPlayerTransportGeometry.current ?: return this
    return onPlaced { geometry.updateHost(it) }
}

/** Include the optional next-track row above the action toolbar in the lower collision boundary. */
@Composable
fun Modifier.playerActionPanelBounds(): Modifier {
    val geometry = LocalPlayerTransportGeometry.current ?: return this
    DisposableEffect(geometry) { onDispose { geometry.actionPanel = null } }
    return onPlaced { geometry.actionPanel = geometry.bounds(it) }
}

/** Center the transport between its visible neighbors, retaining a stable native layout slot. */
@Composable
fun PlayerTransportCenter(content: @Composable () -> Unit) {
    val scopedContent: @Composable () -> Unit = {
        CompositionLocalProvider(LocalGlassScope provides GlassScope.PlayerTransport) { content() }
    }
    val geometry = LocalPlayerTransportGeometry.current
    val information = LocalPlayerInformationBounds.current
    if (geometry == null || information == null) {
        scopedContent()
        return
    }
    val settings = rememberGlassSettings(GlassScope.PlayerHeader)
    val motion = rememberPlayerReflowPlacement()
    val motionEnabled = isGlassMotionAllowed()
    val motionDamping = rememberGlassSettings(GlassScope.PlayerTransport).playerReflowDamping()
    val optical = settings.usesGlassMaterial(GlassArea.ActionButtons) || settings.usesGlassContour(GlassArea.ActionButtons)
    val panePadding = with(LocalDensity.current) {
        if (optical)
            10.dp.toPx() else 0f
    }
    val slotCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(Modifier.onPlaced { slotCoordinates[0] = it }) {
    Box(Modifier.layout { measurable, constraints ->
        val child = measurable.measure(constraints)
        val lookahead = isLookingAhead
        layout(child.width, child.height) placement@{
        if (lookahead) {
            child.place(0, 0)
            return@placement
        }
        information.update()
        val slotTop = slotCoordinates[0]?.takeIf { it.isAttached }?.let { geometry.bounds(it)?.top }
        val origin = information.host?.takeIf { it.isAttached }?.let { geometry.bounds(it)?.top }
        val regions = information.regions
        // Infer the order from measured content: expanded/lyrics variants can
        // intentionally ignore the user's swap preference.
        val timelineTop = regions.filterKeys { information.roles[it] == "timeline" }
            .values.minOfOrNull { it.top }?.let { it + (origin ?: 0f) }
        val swapped = timelineTop != null && slotTop != null && timelineTop > slotTop
        val above = regions.filterKeys { !swapped || information.roles[it] == "info" }
            .values.maxOfOrNull { it.bottom }?.let {
                it + (origin ?: 0f) + if (swapped) panePadding else 0f
            }
        val below = if (swapped) regions.filterKeys { information.roles[it] == "timeline" }
            .values.minOfOrNull { it.top }?.let { it + (origin ?: 0f) - panePadding }
            else listOfNotNull(geometry.actionPanel?.top, (geometry.visibleToolbar ?: geometry.toolbar)?.top).minOrNull()
        val offset = if (above != null && below != null && slotTop != null && origin != null) {
            val durationCenter = information.duration?.center?.y?.plus(origin)
            // The information plate is the visible upper neighbor in Glass. Native
            // controls instead balance against the centered time labels.
            val anchorAbove = if (!swapped) {
                if (optical) information.surfaceBounds()?.bottom?.plus(origin) ?: above
                else durationCenter ?: above
            } else above
            val anchorBelow = if (swapped && !optical) durationCenter ?: below else below
            val requested = (anchorAbove + anchorBelow - child.height) / 2f
            val lower = if (optical && !swapped)
                information.surfaceBounds()?.bottom?.plus(origin) ?: above else above
            val upper = below - child.height
            val fitted = if (upper >= lower) requested.coerceIn(lower, upper) else upper
            val viewport = geometry.host?.size
            val scrollTranslation = slotCoordinates[0]?.let(geometry::lowerScrollTranslation) ?: Offset.Zero
            val actual = if (viewport != null) motion.position(viewport, Offset(0f, fitted), motionEnabled,
                motionDamping, scrollTranslation).y
                else fitted
            // A taller neighbor can enter the old spring position before it reaches its new target.
            val safeActual = if (upper >= lower) actual.coerceIn(lower, upper) else upper
            (safeActual - slotTop).roundToInt()
        } else 0
        child.place(0, offset)
        }
    }) { scopedContent() }
    }
}

/** Let native transport fit independently of the user's narrower timeline width. */
fun Modifier.playerTransportLayout(timelineHorizontalInset: Dp): Modifier =
    testTag("player.transport.row").layout { measurable, constraints ->
        // Timeline width is a user preference; transport must still fit its native
        // buttons and passive glass envelopes. Widen only a cramped slot, centered
        // within its original parent, and keep 10dp free at each viewport edge.
        val maxOutset = (timelineHorizontalInset - 10.dp).coerceAtLeast(0.dp).roundToPx()
        val nativeWidth = measurable.minIntrinsicWidth(constraints.maxHeight)
        val requiredWidth = nativeWidth + 20.dp.roundToPx()
        val width = maxOf(constraints.maxWidth, requiredWidth)
            .coerceAtMost(constraints.maxWidth + 2 * maxOutset)
        val child = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(constraints.maxWidth, child.height) {
            child.placeRelative((constraints.maxWidth - child.width) / 2, 0)
        }
    }
