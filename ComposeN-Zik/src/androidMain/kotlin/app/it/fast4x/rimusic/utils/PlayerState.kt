package app.it.fast4x.rimusic.utils

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.n_zik.android.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import app.kreate.android.me.knighthat.utils.Toaster

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

/**
 * Issue #881 (gh-881), Phase 3.1: position poll period of [positionAndDurationState].
 * 100 ms keeps the bar/label smooth (RiPlay, which never shows the frozen bar on the same
 * device, polls every 200 ms) without a per-frame request.
 */
internal const val POSITION_POLL_INTERVAL_MS = 100L

/**
 * Polls the player position every [POSITION_POLL_INTERVAL_MS] into the returned state.
 *
 * Issue #881 (gh-881), Phase 3.1 — aligned on RiPlay's `rememberPlayerPositionAndDurationState`:
 * a plain time-based loop that ALWAYS reads the live `currentPosition`/`duration`. The former
 * frame-clock poller (`withFrameNanos`) only wrote when an `isSeeking` flag (set on a SEEK
 * discontinuity, cleared on a `STATE_READY` callback) and a `needsUpdate` flag agreed; field
 * logs 2026-10-04 (FURY, OPPO / Android 15) show the bar frozen on the PREVIOUS track's position
 * after a screen-off track change, every skip tap re-seeking to that stale value. With no
 * latch there is no state to get stuck in; the "snap back to the old position" the seeking
 * latch used to hide is covered by GetSeekBar's held `pendingSeekTarget` (Phase 3 Fix E).
 *
 * @param active When false the poller does not write, so consumers are not invalidated
 * while the player content is hidden behind the mini-player. The next tick after [active]
 * becomes true again re-syncs the position.
 */
@Composable
fun Player.positionAndDurationState(key1: Any? = null, active: Boolean = true): State<Pair<Long, Long>> {
    val state = remember(key1) {
        mutableStateOf(currentPosition to duration)
    }
    val activeRef = rememberUpdatedState(active)

    LaunchedEffect(this, key1) {
        while (isActive) {
            if (activeRef.value) {
                val sample = currentPosition to duration
                if (sample != state.value) state.value = sample
            }
            delay(POSITION_POLL_INTERVAL_MS)
        }
    }

    return state
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
            Toaster.w( R.string.info_not_find_application_audio )
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



