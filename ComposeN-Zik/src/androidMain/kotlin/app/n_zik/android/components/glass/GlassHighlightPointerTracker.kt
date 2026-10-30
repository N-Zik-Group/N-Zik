package app.n_zik.android.components.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/** Observes final positions without taking click, long-click, or scroll ownership. */
internal fun Modifier.trackGlassHighlightPointers(tracker: GlassHighlightPointerTracker): Modifier =
    onGloballyPositioned { tracker.coordinates = it }.pointerInput(tracker) {
        try {
            awaitPointerEventScope {
                while (true) tracker.observe(awaitPointerEvent(PointerEventPass.Final))
            }
        } finally {
            tracker.cancelPointerInput()
        }
    }

/** Routes provider-local positions only to indications with an active native press. */
internal class GlassHighlightPointerTracker {
    var coordinates: LayoutCoordinates? = null
    private val activeNodes = mutableSetOf<GlassHighlightNode>()
    private var pointerId: PointerId? = null

    fun add(node: GlassHighlightNode) {
        activeNodes.add(node)
    }

    fun remove(node: GlassHighlightNode) {
        activeNodes.remove(node)
    }

    fun observe(event: PointerEvent) {
        if (pointerId == null) pointerId = event.changes.firstOrNull { it.pressed }?.id
        val change = event.changes.firstOrNull { it.id == pointerId }
        val coordinates = coordinates
        // A native long click consumes movement while its press remains active.
        if (change?.pressed == true && coordinates != null) {
            activeNodes.forEach { it.movePointer(change.position, coordinates) }
        }
        if (event.changes.none { it.pressed }) pointerId = null
    }

    fun cancelPointerInput() {
        pointerId = null
    }
}
