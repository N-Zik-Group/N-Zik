package app.n_zik.android.components.dialog.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** Local lifetime and motion for one window; a frame consumes it before composing children. */
internal class DialogPresentation(val requested: State<Boolean>) {
    val motion = Animatable(0f)
    val durationMillis = DIALOG_MOTION_DURATION_MS
    val interactive: Boolean get() = requested.value
}

internal val LocalDialogPresentation = staticCompositionLocalOf<DialogPresentation?> { null }

/** Whether controls in the current window may accept an action. */
val dialogInputEnabled: Boolean
    @Composable get() = (LocalDialogPresentation.current ?: LocalDialogInput.current)?.interactive ?: true

internal val LocalDialogInput = staticCompositionLocalOf<DialogPresentation?> { null }

/** Retains a locally owned dialog until its bounded exit finishes. Owner disposal is immediate. */
@Composable
fun DialogPresence(visible: Boolean, inherit: Boolean = false, content: @Composable () -> Unit) {
    if (inherit && LocalDialogPresentation.current != null) {
        content()
        return
    }
    val requested = rememberUpdatedState(visible)
    val presentation = remember { DialogPresentation(requested) }
    var retained by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) retained = true
        val target = if (visible) 1f else 0f
        animateDialogMotion(presentation.motion, target)
        if (!visible) retained = false
    }
    if (visible || retained) {
        CompositionLocalProvider(LocalDialogPresentation provides presentation) { content() }
    }
}

/** Replaces a payload at the invisible midpoint, keeping the same modal window throughout. */
@Composable
fun <T : Any> DialogPresence(target: T?, content: @Composable (T) -> Unit) {
    var retained by remember { mutableStateOf(target) }
    val requested = rememberUpdatedState(target != null && target == retained)
    val presentation = remember { DialogPresentation(requested) }
    LaunchedEffect(target) {
        if (retained != null && retained != target) {
            animateDialogMotion(presentation.motion, 0f)
        }
        retained = target
        if (target != null) animateDialogMotion(presentation.motion, 1f)
    }
    retained?.let { payload ->
        CompositionLocalProvider(LocalDialogPresentation provides presentation) { content(payload) }
    }
}

internal const val DIALOG_MOTION_DURATION_MS = 220

internal suspend fun animateDialogMotion(motion: Animatable<Float, *>, target: Float) {
    if (motion.value == target) return
    if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) {
        motion.snapTo(target)
    } else {
        // Keep interruption speed proportional to the remaining path.
        val duration = (DIALOG_MOTION_DURATION_MS * abs(target - motion.value)).roundToInt()
        motion.animateTo(target, tween(duration, easing = LinearEasing))
    }
}

/** One reversible fade and uniform scale keep every section visible throughout the transition. */
internal fun dialogFadeAlpha(motion: Float): Float =
    FastOutSlowInEasing.transform(motion.coerceIn(0f, 1f))

internal fun dialogScale(motion: Float): Float = .96f + .04f * dialogFadeAlpha(motion)
