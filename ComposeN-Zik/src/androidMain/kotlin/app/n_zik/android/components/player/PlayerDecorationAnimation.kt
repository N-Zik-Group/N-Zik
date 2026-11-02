package app.n_zik.android.components.player

import android.graphics.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Decorative work follows visibility, independently of playback and its progress polling. */
@Composable
fun rememberPlayerDecorationsActive(visible: Boolean): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return visible && started
}

/** Each active run starts with a fresh timestamp; hidden wall time never becomes motion. */
internal suspend fun playerDecorationFrames(onFrame: (Long) -> Unit) {
    var previous = withFrameNanos { it }
    while (true) {
        val current = withFrameNanos { it }
        onFrame((current - previous).coerceAtLeast(0L))
        previous = current
    }
}

@Composable
internal fun rememberPlayerDecorationElapsedNanos(active: Boolean): State<Long> {
    val elapsed = remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        if (active) playerDecorationFrames { elapsed.longValue += it }
    }
    return elapsed
}

/** Same linear rotations as the fluid background, retaining the last angle while inactive. */
@Composable
fun animatePlayerBrushRotation(
    shader: Shader,
    size: Size,
    duration: Int,
    clockwise: Boolean,
    active: Boolean,
): State<ShaderBrush> {
    val elapsed = rememberPlayerDecorationElapsedNanos(active)
    return remember(shader, size, duration, clockwise) {
        val matrix = Matrix()
        val brush = ShaderBrush(shader)
        object : State<ShaderBrush> {
            override val value: ShaderBrush
                get() {
                    // Read in the caller's draw/composition scope so a retained brush still
                    // invalidates on time changes without allocating a brush/matrix each frame.
                    val phase = (elapsed.value % (duration * 1_000_000L)).toFloat() /
                        (duration * 1_000_000L).toFloat()
                    matrix.reset()
                    matrix.postRotate(phase * 360f * if (clockwise) 1f else -1f,
                        size.width / 2f, size.height / 2f)
                    shader.setLocalMatrix(matrix)
                    return brush
                }
        }
    }
}
