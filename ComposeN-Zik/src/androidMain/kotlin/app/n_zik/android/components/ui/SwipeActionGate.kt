package app.n_zik.android.components.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.flow.collectLatest

/** Material may validate the same dismiss target repeatedly while a pointer gesture is active. */
class SwipeActionGate {
    internal var gestureActive by mutableStateOf(false)
        private set
    private var pointerSessionPending = false
    private var triggered = false

    internal fun beginGesture() {
        gestureActive = true
        pointerSessionPending = true
        triggered = false
    }

    internal fun endGesture() {
        gestureActive = false
    }

    internal fun finishSettling() {
        if (!gestureActive) pointerSessionPending = false
    }

    /** Accessibility/programmatic actions remain available when there is no pointer gesture. */
    fun run(action: () -> Unit) {
        if (!pointerSessionPending || !triggered) {
            triggered = true
            action()
        }
    }
}

/** Hold the completed pointer action through release validation and the row's return animation. */
@Composable
fun ObserveSwipeActionSettling(gate: SwipeActionGate, readOffset: () -> Float) {
    LaunchedEffect(gate) {
        snapshotFlow { gate.gestureActive to readOffset() }.collectLatest { (active, offset) ->
            if (!active && offset == 0f) {
                withFrameNanos { }
                if (!gate.gestureActive && readOffset() == 0f) gate.finishSettling()
            }
        }
    }
}

/** Observes without consuming so scroll, cancellation and reorder keep their existing ownership. */
fun Modifier.observeSwipeActionGesture(gate: SwipeActionGate): Modifier = pointerInput(gate) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        gate.beginGesture()
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
            } while (event.changes.any { it.pressed })
        } finally {
            gate.endGesture()
        }
    }
}
