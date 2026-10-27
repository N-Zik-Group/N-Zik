package app.n_zik.android.components.dialog.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
    var durationMillis = DIALOG_HYBRID_DURATION_MS
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
        if (presentation.motion.value != target) {
            if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) {
                presentation.motion.snapTo(target)
            } else {
                // Keep interruption speed proportional to the remaining path.
                val duration = (presentation.durationMillis * abs(target - presentation.motion.value)).roundToInt()
                presentation.motion.animateTo(target, tween(duration, easing = LinearEasing))
            }
        }
        if (!visible) retained = false
    }
    if (visible || retained) {
        CompositionLocalProvider(LocalDialogPresentation provides presentation) { content() }
    }
}

/** Keeps the last non-null presentation payload valid throughout exit, scoped to its owner. */
@Composable
fun <T : Any> DialogPresence(target: T?, content: @Composable (T) -> Unit) {
    var retained by remember { mutableStateOf(target) }
    if (target != null) retained = target
    val latestTarget = rememberUpdatedState(target)
    DialogPresence(visible = target != null) {
        DisposableEffect(Unit) {
            onDispose { if (latestTarget.value == null) retained = null }
        }
        retained?.let { content(it) }
    }
}

internal const val DIALOG_HYBRID_DURATION_MS = 220
internal const val DIALOG_TV_DURATION_MS = 80

/** Opening runs line, TV, body; closing follows the exact reverse path. */
internal fun dialogBodyFraction(motion: Float): Float = dialogPhase(motion * DIALOG_HYBRID_DURATION_MS, 80f, 140f)

internal fun dialogTvFraction(motion: Float, folding: Boolean): Float =
    1f - dialogSmoothStep(dialogTvProgress(motion, folding) / .7f)

internal fun dialogLineFraction(motion: Float, folding: Boolean): Float =
    1f - dialogSmoothStep((dialogTvProgress(motion, folding) - .6f) / .4f)

internal fun dialogFlashAlpha(motion: Float, folding: Boolean): Float {
    val progress = dialogTvProgress(motion, folding)
    return dialogSmoothStep(progress) * (1f - dialogSmoothStep((progress - .9f) / .1f))
}

internal fun dialogTvAlpha(motion: Float, folding: Boolean): Float =
    1f - dialogSmoothStep((dialogTvProgress(motion, folding) - .9f) / .1f)

// TV mask progression adapted from Burn-My-Windows resources/shaders/tv.frag.
// SPDX-FileCopyrightText: Simon Schneegans <code@simonschneegans.de>
// SPDX-License-Identifier: GPL-3.0-or-later
// https://github.com/Schneegans/Burn-My-Windows/blob/main/resources/shaders/tv.frag
private fun dialogTvProgress(motion: Float, folding: Boolean): Float {
    val remaining = (motion * dialogDuration(folding) / DIALOG_TV_DURATION_MS).coerceIn(0f, 1f)
    return 1f - remaining * remaining
}

private fun dialogSmoothStep(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}

internal fun dialogDuration(folding: Boolean): Int =
    if (folding) DIALOG_HYBRID_DURATION_MS else DIALOG_TV_DURATION_MS

private fun dialogPhase(time: Float, start: Float, duration: Float): Float =
    FastOutSlowInEasing.transform(((time - start) / duration).coerceIn(0f, 1f))
