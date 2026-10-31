package app.n_zik.android.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Action geometry is independent from its glyph and from the seat reserved by its row. */
internal data class MiniPlayerControlGeometry(val canvas: Dp, val seatWidth: Dp, val seatHeight: Dp)

internal fun miniPlayerControlSize(fold: Float, dockPresentation: Boolean): Dp =
    if (dockPresentation) 42.dp - 6.dp * fold else if (fold > .5f) 48.dp else 42.dp

internal fun miniPlayerIconGeometry(fold: Float, dockPresentation: Boolean, material: Boolean): MiniPlayerControlGeometry {
    val canvas = miniPlayerControlSize(fold, dockPresentation)
    if (material) {
        val padding = if (dockPresentation) 2.dp * (1f - fold) else if (fold > .5f) 0.dp else 2.dp
        return MiniPlayerControlGeometry(canvas, canvas + padding * 2, canvas)
    }
    val horizontal = if (dockPresentation) 2.dp + 4.dp * fold else if (fold > .5f) 12.dp else 2.dp
    val vertical = if (dockPresentation) 8.dp - 2.dp * fold else if (fold > .5f) 12.dp else 8.dp
    return MiniPlayerControlGeometry(canvas, 24.dp + horizontal * 2, 24.dp + vertical * 2)
}

/** The larger child owns input and feedback while the original row seat preserves icon centers. */
@Composable
internal fun MiniPlayerActionCanvas(
    geometry: MiniPlayerControlGeometry,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(Modifier) -> Unit,
) {
    Box(modifier.size(geometry.seatWidth, geometry.seatHeight), contentAlignment = Alignment.Center) {
        content(Modifier.requiredSize(geometry.canvas))
    }
}
