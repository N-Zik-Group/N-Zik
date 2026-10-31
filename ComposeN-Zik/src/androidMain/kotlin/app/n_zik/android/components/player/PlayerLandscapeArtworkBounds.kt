package app.n_zik.android.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.constrainHeight
import kotlin.math.roundToInt

/** Fits the artwork and pager viewport above the toolbar's bottom without moving the controls. */
@Composable
fun Modifier.playerLandscapeArtworkBounds(): Modifier {
    val geometry = LocalPlayerTransportGeometry.current ?: return this
    var top by remember(geometry) { mutableStateOf<Float?>(null) }
    return onGloballyPositioned { top = geometry.bounds(it)?.top }
        .layout { measurable, constraints ->
            val bottom = geometry.toolbar?.bottom
            val available = if (constraints.hasBoundedHeight && bottom != null && top != null)
                (bottom - (top ?: 0f)).roundToInt().coerceIn(0, constraints.maxHeight)
            else constraints.maxHeight
            val child = measurable.measure(constraints.copy(
                minHeight = minOf(constraints.minHeight, available), maxHeight = available))
            // Keep the original row footprint; only artwork receives the reduced height budget.
            layout(child.width, constraints.constrainHeight(child.height)) { child.placeRelative(0, 0) }
        }
        .testTag("player.landscapeArtwork.viewport")
}
