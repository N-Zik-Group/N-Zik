package app.n_zik.android.components.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth

/** Reserve lower content before portrait artwork, including the native expanded carousel. */
@Composable
fun PlayerGlassBudgetLayout(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    expandedCarousel: Boolean = false,
    header: @Composable ColumnScope.() -> Unit,
    artwork: @Composable ColumnScope.() -> Unit,
    information: @Composable ColumnScope.() -> Unit,
) {
    if (!enabled && !expandedCarousel) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            header()
            artwork()
            information()
        }
        return
    }
    Layout(modifier = modifier, content = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = header)
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = artwork)
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = information)
    }) { children, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val top = children[0].measure(loose)
        // Measure the real optional queue/stats/actions and the controls' natural content.
        // This avoids a square cover squeezing the transport into a smaller weighted slot.
        val lower = children[2].measure(loose.copy(maxHeight = Constraints.Infinity))
        val remaining = (constraints.maxHeight - top.height - lower.height).coerceAtLeast(0)
        val cover = children[1].measure(loose.copy(maxHeight = remaining))
        val width = constraints.constrainWidth(maxOf(top.width, cover.width, lower.width))
        // The sheet offers a loose maximum height. Keep the background and lower
        // actions across that whole viewport, even when a fixed cover leaves spare room.
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight
            else constraints.constrainHeight(top.height + cover.height + lower.height)
        layout(width, height) {
            top.placeRelative((width - top.width) / 2, 0)
            cover.placeRelative((width - cover.width) / 2, top.height)
            lower.placeRelative((width - lower.width) / 2, height - lower.height)
        }
    }
}
