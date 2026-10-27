package app.n_zik.android.components.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay

/** Keeps the initial service grace period from replaying when returning to Home. */
@Composable
internal fun rememberHomeEntranceReady(serviceKey: Any?): State<Boolean> {
    val ready = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(serviceKey) {
        if (!ready.value) {
            delay(100)
            ready.value = true
        }
    }
    return ready
}

/** Replays the visual entrance on remount without restarting service readiness. */
@Composable
internal fun rememberHomeEntranceAlpha(ready: Boolean, animationsEnabled: Boolean): State<Float> {
    val alpha = remember { Animatable(if (ready && !animationsEnabled) 1f else 0f) }
    LaunchedEffect(ready, animationsEnabled) {
        if (!ready) alpha.snapTo(0f)
        else if (animationsEnabled) alpha.animateTo(1f, tween(300))
        else alpha.snapTo(1f)
    }
    return alpha.asState()
}
