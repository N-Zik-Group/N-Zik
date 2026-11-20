package app.n_zik.android.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.Player
import app.n_zik.android.utils.playbackPositionClock
import app.n_zik.android.utils.POSITION_POLL_INTERVAL_MS
import kotlinx.coroutines.flow.collectLatest

internal data class MiniPlayerProgressSample(
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val discontinuity: Int = 0,
) {
    val fraction: Float
        get() = if (durationMs > 0) positionMs.coerceIn(0L, durationMs).toFloat() / durationMs else 0f

    fun follows(previous: MiniPlayerProgressSample): Boolean =
        isPlaying && previous.isPlaying && durationMs > 0L && durationMs == previous.durationMs &&
            discontinuity == previous.discontinuity && positionMs > previous.positionMs
}

/** Interpolates shared playback samples without reading animation state in composition. */
@Composable
internal fun rememberMiniPlayerProgress(player: Player, generation: Any?): () -> Float {
    val samples = player.playbackPositionClock(generation).sample
    val progress = remember(samples) { Animatable(samples.value.fraction) }
    LaunchedEffect(samples) {
        var previous = samples.value
        snapshotFlow { samples.value }.collectLatest { sample ->
            val interpolate = sample.follows(previous)
            previous = sample
            if (interpolate) {
                progress.animateTo(sample.fraction, tween(POSITION_POLL_INTERVAL_MS.toInt(), easing = LinearEasing))
            } else {
                progress.snapTo(sample.fraction)
            }
        }
    }
    return remember(progress) { { progress.value } }
}
