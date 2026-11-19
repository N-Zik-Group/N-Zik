package app.n_zik.android.components.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import app.n_zik.android.components.ui.FullPageLoader
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

@Composable
internal fun HomeStartupLoader(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        FullPageLoader()
    }
}

@Composable
internal fun HomeEntranceLayer(
    contentAlpha: State<Float>,
    showStartupLoader: Boolean,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha.value }) {
            content()
        }
        if (showStartupLoader) {
            HomeStartupLoader(Modifier.graphicsLayer { alpha = 1f - contentAlpha.value })
        }
    }
}
