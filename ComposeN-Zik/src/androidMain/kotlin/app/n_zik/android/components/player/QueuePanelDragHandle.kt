package app.n_zik.android.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import app.n_zik.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Shares the drag endpoints and their accessibility actions between the two queue panels. */
@Composable
internal fun Modifier.queuePanelDragHandle(
    heightFraction: Animatable<Float, AnimationVector1D>,
    maxFraction: Float,
    screenHeightPx: Int,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
): Modifier {
    val dismissQueue by rememberUpdatedState(onDismiss)
    val label = stringResource(R.string.player_mini_button_queue)
    val restingFraction = 0.65f
    suspend fun resize(fraction: Float) {
        heightFraction.animateTo(fraction, spring(dampingRatio = 0.8f, stiffness = 300f))
    }
    return semantics {
        contentDescription = label
        dismiss { dismissQueue(); true }
        if (maxFraction > restingFraction) {
            if (heightFraction.targetValue > restingFraction) {
                collapse { scope.launch { resize(restingFraction) }; true }
            } else {
                expand { scope.launch { resize(maxFraction) }; true }
            }
        }
    }.pointerInput(heightFraction, maxFraction, screenHeightPx, scope) {
        detectVerticalDragGestures(
            onDragEnd = {
                scope.launch {
                    val currentFraction = heightFraction.value
                    when {
                        currentFraction > 0.85f -> resize(maxFraction)
                        currentFraction < 0.4f -> dismissQueue()
                        else -> resize(restingFraction)
                    }
                }
            },
            onVerticalDrag = { _, dragAmount ->
                scope.launch {
                    val fraction = (heightFraction.value - dragAmount / screenHeightPx)
                        .coerceIn(0.1f, maxFraction)
                    heightFraction.snapTo(fraction)
                }
            },
        )
    }
}
