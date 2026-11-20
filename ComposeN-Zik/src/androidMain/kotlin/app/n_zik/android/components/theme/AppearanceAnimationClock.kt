package app.n_zik.android.components.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

private val LocalAppearanceAnimationClock = staticCompositionLocalOf<BroadcastFrameClock?> { null }

/** One demand-driven frame pump for appearance and player animations in this scene. */
@Composable
internal fun ProvideAppearanceAnimationClock(content: @Composable () -> Unit) {
    if (LocalAppearanceAnimationClock.current != null) {
        content()
        return
    }

    val requests = remember { Channel<Unit>(Channel.CONFLATED) }
    val clock = remember { BroadcastFrameClock { requests.trySend(Unit) } }
    LaunchedEffect(clock) {
        while (true) {
            requests.receive()
            // Effects can be cancelled between requesting and awaiting an upstream frame.
            if (clock.hasAwaiters) withFrameNanos(clock::sendFrame)
        }
    }
    CompositionLocalProvider(LocalAppearanceAnimationClock provides clock, content = content)
}

/** Each effect keeps its cancellation and duration, and receives the same presentation frame. */
@Composable
internal fun LaunchedAppearanceAnimationEffect(
    vararg keys: Any?,
    block: suspend CoroutineScope.() -> Unit,
) {
    val clock = LocalAppearanceAnimationClock.current
    LaunchedEffect(clock, *keys) {
        if (clock == null) block() else withContext(clock, block)
    }
}

internal suspend fun animateAppearanceFraction(durationMillis: Int, onFrame: (Float) -> Unit) {
    animate(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
    ) { value, _ -> onFrame(value) }
}
