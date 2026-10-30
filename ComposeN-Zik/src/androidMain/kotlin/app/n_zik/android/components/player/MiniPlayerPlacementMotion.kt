package app.n_zik.android.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import app.n_zik.android.core.navigation.navigationChromeTween

/** Animates discrete placement changes while preserving every live scroll delta. */
internal class MiniPlayerPlacementMotion {
    private var previousKey: Any? = null
    private var lastPosition: Float? = null
    private var correction = 0f

    fun position(key: Any?, target: Float, progress: Float): Float {
        if (lastPosition == null || previousKey != key) {
            correction = (lastPosition ?: target) - target
            previousKey = key
        }
        return (target + correction * (1f - progress.coerceIn(0f, 1f))).also {
            lastPosition = it
        }
    }
}

/** Read the returned provider only during placement or drawing. */
@Composable
internal fun rememberMiniPlayerPlacementMotion(placementKey: Any?): (Float) -> Float {
    val motion = remember { MiniPlayerPlacementMotion() }
    val progress = remember(placementKey) { Animatable(0f) }
    LaunchedEffect(progress) {
        progress.animateTo(1f, navigationChromeTween())
    }
    return remember(motion, placementKey, progress) {
        { target -> motion.position(placementKey, target, progress.value) }
    }
}
