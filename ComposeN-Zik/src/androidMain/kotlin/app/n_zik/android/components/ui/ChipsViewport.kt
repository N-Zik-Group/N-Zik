package app.n_zik.android.components.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.offset

/** Lets a chip scroller use its parent's horizontal gutter without moving other content. */
fun Modifier.chipsViewport(horizontalGutter: Dp): Modifier = layout { measurable, constraints ->
    val gutter = horizontalGutter.roundToPx()
    val child = measurable.measure(constraints.offset(horizontal = gutter * 2))
    layout((child.width - gutter * 2).coerceAtLeast(0), child.height) {
        child.placeRelative(-gutter, 0)
    }
}
