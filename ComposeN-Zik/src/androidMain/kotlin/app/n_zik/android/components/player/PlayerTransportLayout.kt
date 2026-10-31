package app.n_zik.android.components.player

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import app.n_zik.android.components.glass.LocalGlassScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.inScope
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.usesGlassContour
import kotlin.math.roundToInt

/** Actual lower capsule bounds shared by the player controls and action toolbar. */
class PlayerTransportGeometry {
    var host by mutableStateOf<LayoutCoordinates?>(null)
    var toolbar by mutableStateOf<Rect?>(null)
    var actionPanel by mutableStateOf<Rect?>(null)
    var informationSurfaceTop by mutableStateOf<Float?>(null)
    var landscapeInformationTopOutset by mutableStateOf<Float?>(null)

    fun bounds(coordinates: LayoutCoordinates): Rect? = host?.takeIf { it.isAttached }
        ?.localBoundingBoxOf(coordinates, clipBounds = false)
}

val LocalPlayerTransportGeometry = staticCompositionLocalOf<PlayerTransportGeometry?> { null }

/** Fix the coordinate origin to the player, independent of the sheet's opening translation. */
@Composable
fun Modifier.playerTransportGeometryHost(): Modifier {
    val geometry = LocalPlayerTransportGeometry.current ?: return this
    return onGloballyPositioned { geometry.host = it }
}

/** Include the optional next-track row above the action toolbar in the lower collision boundary. */
@Composable
fun Modifier.playerActionPanelBounds(): Modifier {
    val geometry = LocalPlayerTransportGeometry.current ?: return this
    return onGloballyPositioned { geometry.actionPanel = geometry.bounds(it) }
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
    val settings = rememberGlassSettings(GlassScope.PlayerTransport)
    val panePadding = with(LocalDensity.current) {
        if (settings.usesGlassMaterial(GlassArea.ActionButtons) || settings.usesGlassContour(GlassArea.ActionButtons))
            10.dp.toPx() else 0f
    }
    var slotTop by remember { mutableStateOf<Float?>(null) }
    Box(Modifier.onGloballyPositioned { slotTop = geometry.bounds(it)?.top }) {
    Box(Modifier.layout { measurable, constraints ->
        val child = measurable.measure(constraints)
        val origin = information.host?.takeIf { it.isAttached }?.let { geometry.bounds(it)?.top }
        val regions = information.regions
        // Infer the order from measured content: expanded/lyrics variants can
        // intentionally ignore the user's swap preference.
        val timelineTop = regions.filterKeys { information.roles[it] == "timeline" }
            .values.minOfOrNull { it.top }?.let { it + (origin ?: 0f) }
        val swapped = timelineTop != null && slotTop != null && timelineTop > (slotTop ?: 0f)
        val above = regions.filterKeys { !swapped || information.roles[it] == "info" }
            .values.maxOfOrNull { it.bottom }?.let { it + (origin ?: 0f) + panePadding }
        val below = if (swapped) regions.filterKeys { information.roles[it] == "timeline" }
            .values.minOfOrNull { it.top }?.let { it + (origin ?: 0f) - panePadding }
            else listOfNotNull(geometry.actionPanel?.top, geometry.toolbar?.top).minOrNull()
        val offset = if (above != null && below != null && slotTop != null && origin != null) {
            val requested = (above + below - child.height) / 2f
            val lower = above
            val upper = below - child.height
            val fitted = if (upper >= lower) requested.coerceIn(lower, upper) else requested
            (fitted - (slotTop ?: 0f)).roundToInt()
        } else 0
        layout(child.width, child.height) { child.place(0, offset) }
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
