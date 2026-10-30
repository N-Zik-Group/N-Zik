package app.n_zik.android.components.navigation.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.roundToInt

/** Square's fading control width: the node survives the fold while its occupied width changes. */
@Composable
fun FloatingMiniActionSlot(fraction: Float, enabled: Boolean = true, leadingSpace: Dp = 0.dp, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val visible = fraction.coerceIn(0f, 1f)
    val interactive = visible >= .5f
    Layout(
        modifier = Modifier.then(if (visible < 1f) Modifier.clipToBounds() else Modifier).graphicsLayer {
            alpha = visible
            // Fading controls need no offscreen bitmap clipped to their shrinking slot.
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }
            .then(if (interactive) Modifier else Modifier.clearAndSetSemantics {})
            .pointerInput(interactive) {
                if (!interactive) awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            },
        content = { Box(Modifier.padding(start = leadingSpace)) { content() } },
    ) { children, constraints ->
        val child = children.single().measure(constraints.copy(minWidth = 0))
        layout((child.width * visible).roundToInt(), child.height) {
            if (visible > 0f) child.placeRelative(0, 0)
        }
    }
}
