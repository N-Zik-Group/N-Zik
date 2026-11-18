package app.n_zik.android.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.media3.common.Player
import app.n_zik.android.utils.DisposableListener
import app.n_zik.android.utils.POSITION_POLL_INTERVAL_MS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

private fun Player.miniPlayerProgressSample(discontinuity: Int) =
    MiniPlayerProgressSample(currentPosition, duration, isPlaying, discontinuity)

/** Interpolates playback samples without reading animated state in the mini-player composition. */
@Composable
internal fun rememberMiniPlayerProgress(player: Player, generation: Any?): () -> Float {
    val samples = remember(player, generation) { MutableStateFlow(player.miniPlayerProgressSample(0)) }
    val progress = remember(samples) { Animatable(samples.value.fraction) }
    player.DisposableListener(samples) {
        object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                val changesPosition = events.containsAny(
                    Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_ITEM_TRANSITION,
                )
                if (changesPosition || events.containsAny(
                        Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_TIMELINE_CHANGED,
                    )
                ) {
                    val revision = samples.value.discontinuity + if (changesPosition) 1 else 0
                    samples.value = player.miniPlayerProgressSample(revision)
                }
            }
        }
    }
    LaunchedEffect(samples) {
        launch {
            while (isActive) {
                samples.value = player.miniPlayerProgressSample(samples.value.discontinuity)
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
        var previous = samples.value
        samples.collectLatest { sample ->
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
