package app.n_zik.android.components.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import app.n_zik.android.components.player.LocalPlayerTransportGeometry
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.player.PlayerInformationVerticalPadding

/** Shared edge reserve for lyrics and visualizer utilities, independent of the material mode. */
internal fun overlayControlsBottomInset(showThumbnail: Boolean, circularThumbnail: Boolean): Dp = when {
    !showThumbnail -> 0.dp
    circularThumbnail -> 16.dp
    else -> 0.dp
}

/** Reserve the same visible six-dp pane margin for native Lyrics and Visualizer seats. */
internal fun overlayCornerInset(seatSize: Dp): Dp = 6.dp + (40.dp - seatSize) / 2

/** Keeps centered page controls independent of the trailing settings pane. */
@Composable
internal fun GlassOverlayControls(
    showThumbnail: Boolean,
    circularThumbnail: Boolean,
    modifier: Modifier = Modifier,
    cornerSeatSize: Dp = 44.dp,
    centerContent: @Composable RowScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit,
) {
    Layout(
        modifier = modifier.fillMaxWidth().padding(start = overlayCornerInset(cornerSeatSize), end = overlayCornerInset(cornerSeatSize),
            bottom = overlayControlsBottomInset(showThumbnail, circularThumbnail)).height(50.dp),
        content = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically, content = centerContent)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically, content = trailingContent)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val trailing = measurables[1].measure(loose)
        val gap = 8.dp.roundToPx()
        val available = (constraints.maxWidth - trailing.width - gap).coerceAtLeast(0)
        val center = measurables[0].measure(loose.copy(maxWidth = available))
        val rightLimit = (available - center.width).coerceAtLeast(0)
        val centerX = ((constraints.maxWidth - center.width) / 2).coerceIn(0, rightLimit)
        layout(constraints.maxWidth, constraints.maxHeight) {
            center.placeRelative(centerX, (constraints.maxHeight - center.height) / 2)
            trailing.placeRelative(constraints.maxWidth - trailing.width,
                (constraints.maxHeight - trailing.height) / 2)
        }
    }
}

/** Native canvases retain their bounds; Glass leaves one plate inset plus a visible gap. */
internal fun overlayCanvasBottomReserve(settings: GlassSettings): Dp =
    if (settings.enabled) PlayerInformationVerticalPadding * 2 else 0.dp

/** Reduce the complete visible overlay inside its stable host, including background and utilities. */
@Composable
internal fun Modifier.glassOverlayCanvasViewport(settings: GlassSettings, showThumbnail: Boolean = false): Modifier {
    val geometry = LocalPlayerTransportGeometry.current
    val density = LocalDensity.current
    var sourceBottom by remember(geometry) { mutableStateOf<Float?>(null) }
    if (!settings.enabled) return this
    val measuredReserve = geometry?.informationSurfaceTop?.let { top ->
        sourceBottom?.let { bottom -> with(density) { (bottom - top).toDp() } + PlayerInformationVerticalPadding }
    }
    val fallback = if (showThumbnail) 0.dp else overlayCanvasBottomReserve(settings)
    val reserve = maxOf(0.dp, measuredReserve ?: fallback)
    return onGloballyPositioned { sourceBottom = geometry?.bounds(it)?.bottom }
        .padding(bottom = reserve).clipToBounds()
}

/** A coverless overlay participates in host measurement even when it is the only child. */
internal fun Modifier.glassOverlayHostViewport(settings: GlassSettings): Modifier =
    if (settings.enabled) fillMaxSize() else this

/** Inactive overlays install neither a full-size layout nor a pointer-input surface over the cover. */
@Composable
internal fun GlassOverlayHost(
    active: Boolean,
    settings: GlassSettings,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (active) Box(modifier.glassOverlayHostViewport(settings), content = content)
}

/** Coverless canvases are rectangular; only overlays drawn on artwork inherit its crop. */
internal fun overlayViewportShape(showThumbnail: Boolean, thumbnailShape: Shape): Shape =
    if (showThumbnail) thumbnailShape else RectangleShape
