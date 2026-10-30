package app.n_zik.android.components.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/** Rail clearance for padding inside scroll owners; never padding on the page viewport. */
internal val LocalSideRailContentInsets = staticCompositionLocalOf { WindowInsets(0, 0, 0, 0) }

/** Places a rail above the page without changing the page's viewport or backdrop width. */
@Composable
internal fun SideNavigationOverlay(
    onRight: Boolean,
    railWidthPx: Int,
    topInsetPx: () -> Int,
    bottomInsetPx: () -> Int,
    leftInsetPx: () -> Int,
    rightInsetPx: () -> Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val top = topInsetPx().coerceIn(0, height)
        val bottom = bottomInsetPx().coerceIn(0, height - top)
        val left = leftInsetPx().coerceIn(0, width)
        val right = rightInsetPx().coerceIn(0, width - left)
        val railWidth = railWidthPx.coerceIn(0, width - left - right)
        val rail = measurables.single().measure(Constraints(
            minWidth = railWidth,
            maxWidth = railWidth,
            minHeight = height - top - bottom,
            maxHeight = height - top - bottom,
        ))
        layout(width, height) {
            rail.place(
                x = if (onRight) width - right - rail.width else left,
                y = top + (height - top - bottom - rail.height) / 2,
            )
        }
    }
}
