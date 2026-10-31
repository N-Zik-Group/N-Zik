package app.n_zik.android.components.navigation.header

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Shared opacity clock for the header and its stationary Android status cover. */
internal fun headerScrollOpacity(statusBarTopPx: Float, hideRangePx: Float, offsetPx: Float): Float {
    if (hideRangePx <= 0f) return 1f
    return (1f + offsetPx / hideRangePx).coerceIn(0f, 1f)
}

/** Translate and fade without recomposition, clearing input when fully hidden. */
internal fun Modifier.headerScrollFade(
    statusBarTopPx: Float,
    hideRangePx: Float,
    surfaceColor: Color? = null,
    clipStatusAtRest: Boolean = false,
    motionVisualOutset: Dp = 0.dp,
    offsetPx: () -> Float,
): Modifier = graphicsLayer {
    val offset = offsetPx()
    translationY = offset
    alpha = headerScrollOpacity(statusBarTopPx, hideRangePx, offset)
    val visibility = if (hideRangePx > 0f) (1f + offset / hideRangePx).coerceIn(0f, 1f) else 1f
    // Keep moving glyphs below Android icons throughout the fade.
    // An empty outline also removes input at the hidden endpoint.
    val top = when {
        visibility == 0f -> size.height
        offset < 0f || clipStatusAtRest -> statusBarTopPx - offset
        else -> 0f
    }
    // Keep the status/hidden mask, but let held Glass controls draw past the chrome's
    // other edges. Outsets also preserve that drawing during offscreen alpha fades.
    val overflow = if (visibility > 0f) motionVisualOutset.toPx() else 0f
    shape = HeaderVisibleShape(top, overflow)
    outsets = if (visibility > 0f) LayerOutsets(
        left = motionVisualOutset, right = motionVisualOutset, bottom = motionVisualOutset,
    ) else LayerOutsets.Zero
    clip = true
}.drawWithContent {
    // Keep the exact configured color and film throughout movement; the shared layer
    // opacity supplies the same fade as the status cover without a second color change.
    surfaceColor?.let { color ->
        drawRect(color)
    }
    drawContent()
}

private data class HeaderVisibleShape(val top: Float, val overflow: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(-overflow, top.coerceIn(0f, size.height),
            size.width + overflow, size.height + overflow))
}
