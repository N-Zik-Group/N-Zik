package app.n_zik.android.components.ui.toggles

import kotlin.math.abs

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection



@Stable
internal class SwitchGestureState(checked: Boolean) {
    var dragging by mutableStateOf(false)
    var position by mutableFloatStateOf(if (checked) 1f else 0f)
}

@Composable
internal fun rememberSwitchGestureState(checked: Boolean, enabled: Boolean): SwitchGestureState {
    val state = remember { SwitchGestureState(checked) }
    LaunchedEffect(enabled) { if (!enabled) state.dragging = false }
    return state
}

/** Native toggleable owns taps and semantics; only horizontal drags commit a second gesture path. */
@Composable
internal fun Modifier.switchInput(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean,
    interactions: MutableInteractionSource,
    gesture: SwitchGestureState,
    visualPosition: Float,
    travelDistance: Dp,
): Modifier {
    if (onCheckedChange == null) return this
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val travel = with(LocalDensity.current) { travelDistance.toPx() }
    val currentPosition = rememberUpdatedState(visualPosition)
    val currentChecked = rememberUpdatedState(checked)
    val currentCallback = rememberUpdatedState(onCheckedChange)
    return this
        .minimumInteractiveComponentSize()
        .toggleable(checked, interactions, indication = null, enabled = enabled,
            role = Role.Switch, onValueChange = { currentCallback.value.invoke(it) })
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var vertical = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val offset = change.position - down.position
                    if (abs(offset.y) > viewConfiguration.touchSlop && abs(offset.y) > abs(offset.x)) vertical = true
                    // Leave vertical movement to scrolling parents, but reject a swipe as a tap.
                    if (!change.pressed && vertical) change.consume()
                } while (event.changes.any { it.pressed })
            }
        }
        .pointerInput(enabled, direction, travel, gesture) {
            if (!enabled) return@pointerInput
            try {
                detectHorizontalDragGestures(
                    onDragStart = { gesture.position = currentPosition.value; gesture.dragging = true },
                    onDragCancel = { gesture.dragging = false },
                    onDragEnd = {
                        val next = gesture.position >= .5f
                        gesture.dragging = false
                        if (next != currentChecked.value) currentCallback.value.invoke(next)
                    },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        gesture.position = (gesture.position + direction * delta / travel).coerceIn(0f, 1f)
                    },
                )
            } finally {
                // pointerInput can restart without delivering onDragCancel.
                gesture.dragging = false
            }
        }
}
