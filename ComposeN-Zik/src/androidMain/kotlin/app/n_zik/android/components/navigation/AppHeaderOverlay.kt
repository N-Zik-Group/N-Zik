package app.n_zik.android.components.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

internal fun appHeaderContentTopPx(
    headerHeightPx: Int,
    scrollOffsetPx: Int,
    statusBarTopPx: Int,
    retainStatusInset: Boolean,
): Int {
    if (retainStatusInset) return (headerHeightPx + scrollOffsetPx).coerceAtLeast(statusBarTopPx)
    // Scroll hides the header row, not its status-bar padding. Release that padding on
    // the same clock so a hidden header cannot leave an empty strip above the page.
    val rowHeightPx = (headerHeightPx - statusBarTopPx).coerceAtLeast(1)
    val visibleFraction = (1f + scrollOffsetPx.toFloat() / rowHeightPx).coerceIn(0f, 1f)
    return (headerHeightPx * visibleFraction).toInt()
}

internal data class AppHeaderScrollStep(val offsetPx: Float, val consumedY: Float)

/** Match consumed input to page travel, including status padding released without Top tabs. */
internal fun appHeaderScrollStep(
    previousOffsetPx: Float,
    availableY: Float,
    headerRowHeightPx: Float,
    statusBarTopPx: Float,
    retainStatusInset: Boolean,
): AppHeaderScrollStep {
    if (headerRowHeightPx <= 0f) return AppHeaderScrollStep(previousOffsetPx, 0f)
    val pageTravelPerHeaderPixel = if (retainStatusInset) 1f
        else (headerRowHeightPx + statusBarTopPx.coerceAtLeast(0f)) / headerRowHeightPx
    val offset = (previousOffsetPx + availableY / pageTravelPerHeaderPixel)
        .coerceIn(-headerRowHeightPx, 0f)
    return AppHeaderScrollStep(offset, (offset - previousOffsetPx) * pageTravelPerHeaderPixel)
}

/** Shared root viewport placement; inset is sampled in layout, never by the navigation graph. */
internal fun Modifier.appHeaderViewport(topInsetPx: () -> Int): Modifier = layout { measurable, constraints ->
    val inset = topInsetPx().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.copy(
        minHeight = (constraints.minHeight - inset).coerceAtLeast(0),
        maxHeight = (constraints.maxHeight - inset).coerceAtLeast(0),
    ))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, inset) }
}

/** The capture modifier includes both siblings; only the header's own bounds receive its input. */
@Composable
internal fun AppHeaderOverlay(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    navigation: @Composable () -> Unit = {},
    topContentCover: @Composable () -> Unit = {},
    headerClipBottomPx: (() -> Int)? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        content()
        topContentCover()
        navigation()
        Box(Modifier.align(Alignment.TopStart).then(
            if (headerClipBottomPx == null) Modifier else Modifier.graphicsLayer {
                shape = HeaderOverlayClipShape(headerClipBottomPx().toFloat())
                clip = true
            }
        )) { header() }
    }
}

/** The moving header may not paint or receive touches inside the stationary Top tabs. */
private data class HeaderOverlayClipShape(val bottom: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(0f, 0f, size.width, bottom.coerceIn(0f, size.height)))
}
