package app.n_zik.android.utils

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.n_zik.android.components.player.MiniPlayerProgressSample
import app.n_zik.android.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import app.n_zik.android.core.utils.Toaster

@Composable
fun Player.DisposableListener(
    key1: Any? = null,
    listenerProvider: () -> Player.Listener
) {
    DisposableEffect(this, key1) {
        val listener = listenerProvider()
        addListener(listener)
        onDispose {
            removeListener(listener)
        }
    }
}

internal const val POSITION_POLL_INTERVAL_MS = 100L

internal class PlaybackPositionClock(val player: Player) {
    private val samples = mutableStateOf(MiniPlayerProgressSample(player.currentPosition, player.duration, player.isPlaying))
    private val retainedDuration = mutableStateOf(samples.value.durationMs)
    private var sampledAtMs = SystemClock.elapsedRealtime()
    private var speed = player.playbackParameters?.speed ?: 1f

    val sample: State<MiniPlayerProgressSample> = samples
    val timelineDuration: State<Long> = retainedDuration

    fun publish(discontinuity: Boolean = false, holdTimelineDuration: Boolean = false, committedSeek: Boolean = false) {
        val rawDuration = player.duration
        sampledAtMs = SystemClock.elapsedRealtime()
        speed = player.playbackParameters?.speed ?: 1f
        samples.value = MiniPlayerProgressSample(
            player.currentPosition, rawDuration, player.isPlaying,
            samples.value.discontinuity + if (discontinuity) 1 else 0,
        )
        if (committedSeek || (!holdTimelineDuration && rawDuration > 0)) retainedDuration.value = rawDuration
    }

    fun estimatedPositionMs(): Long {
        val current = samples.value
        if (!current.isPlaying) return current.positionMs
        val elapsed = (SystemClock.elapsedRealtime() - sampledAtMs).coerceIn(0, POSITION_POLL_INTERVAL_MS)
        val position = current.positionMs + (elapsed * speed).toLong()
        return current.durationMs.takeIf { it > 0 }?.let { position.coerceAtMost(it) } ?: position
    }
}

internal val LocalPlaybackPositionClock = staticCompositionLocalOf<PlaybackPositionClock?> { null }

@Composable
internal fun Player.playbackPositionClock(key1: Any? = null): PlaybackPositionClock =
    LocalPlaybackPositionClock.current?.takeIf { it.player === this } ?: rememberPlaybackPositionClock(key1)

/** Live position and timeline duration shared by both players. Keep the last known duration
 * during stream resolution and re-anchor immediately on a committed seek (#881). */
@Composable
fun Player.positionAndDurationState(key1: Any? = null): State<Pair<Long, Long>> {
    val clock = playbackPositionClock(key1)
    return remember(clock) {
        derivedStateOf { clock.sample.value.positionMs to clock.timelineDuration.value }
    }
}

@Composable
internal fun Player.rememberPlaybackPositionClock(key1: Any? = null): PlaybackPositionClock {
    val clock = remember(this, key1) { PlaybackPositionClock(this) }

    // The #881 timeline must follow playback even while the sheet is collapsed.
    LaunchedEffect(this, key1) {
        while (isActive) {
            clock.publish()
            delay(POSITION_POLL_INTERVAL_MS)
        }
    }

    DisposableListener(clock) {
        object : Player.Listener {
            private var publishedPosition = false

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                clock.publish(discontinuity = true, holdTimelineDuration = true)
                publishedPosition = true
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    clock.publish(discontinuity = true, committedSeek = true)
                    publishedPosition = true
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                val jumped = events.contains(Player.EVENT_POSITION_DISCONTINUITY)
                if (!publishedPosition && (jumped || events.containsAny(
                        Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                    ))
                ) clock.publish(discontinuity = jumped, holdTimelineDuration = true)
                publishedPosition = false
            }
        }
    }

    return clock
}

@Composable
fun rememberEqualizerLauncher(
    audioSessionId: () -> Int?,
    contentType: Int = AudioEffect.CONTENT_TYPE_MUSIC
): State<() -> Unit> {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    return rememberUpdatedState {
        try {
            launcher.launch(
                Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                    replaceExtras(EqualizerIntentBundleAccessor.bundle {
                        audioSessionId()?.let { audioSession = it }
                        packageName = context.packageName
                        this.contentType = contentType
                    })
                }
            )
        } catch (e: ActivityNotFoundException) {
            Toaster.w( R.string.audio_equalizer_application_unavailable_message )
        }
    }
}
@Composable
fun Player.currentMediaItemIdAsState(key1: Any? = null): State<String?> {
    val state = remember(key1) {
        mutableStateOf(currentMediaItem?.mediaId)
    }

    DisposableListener(key1) {
        object : Player.Listener {
            private var publishedPosition = false

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                state.value = mediaItem?.mediaId
            }
        }
    }

    return state
}

@Composable
fun Player.playbackStateState(key1: Any? = null): State<Int> {
    val state = remember(key1) {
        mutableStateOf(playbackState)
    }

    DisposableListener(key1) {
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                state.value = playbackState
            }
        }
    }

    return state
}



