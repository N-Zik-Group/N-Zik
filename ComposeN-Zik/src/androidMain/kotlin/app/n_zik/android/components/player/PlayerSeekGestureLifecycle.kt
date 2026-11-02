package app.n_zik.android.components.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Clears abandoned scrub display state after native handlers have processed the terminal event. */
@Composable
internal fun Modifier.playerSeekGestureCleanup(mediaId: String, onFinished: () -> Unit): Modifier {
    val finish by rememberUpdatedState(onFinished)
    return pointerInput(mediaId) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                } while (event.changes.any { it.pressed })
            } finally {
                finish()
            }
        }
    }
}

/** Keeps callbacks current within a media and range boundary, ignoring detached native handlers. */
@Composable
internal fun <T> rememberCurrentSeekValueCallback(
    isCurrentGesture: () -> Boolean,
    callback: (T) -> Unit,
): (T) -> Unit {
    val current by rememberUpdatedState(callback)
    return remember { { value -> if (isCurrentGesture()) current(value) } }
}

@Composable
internal fun rememberCurrentSeekEndCallback(
    isCurrentGesture: () -> Boolean,
    callback: () -> Unit,
): () -> Unit {
    val current by rememberUpdatedState(callback)
    return remember { { if (isCurrentGesture()) current() } }
}
