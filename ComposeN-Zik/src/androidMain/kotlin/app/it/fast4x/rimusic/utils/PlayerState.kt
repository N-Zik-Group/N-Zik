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
import androidx.compose.runtime.withFrameNanos
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.n_zik.android.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
 * Polls the player position once per frame into [state].
 *
 * @param active When false the poller stays suspended from writing, so
 * consumers are not invalidated every frame (the player content is always
 * composed but may be hidden behind the mini-player). The first frame after
 * [active] becomes true again re-syncs the position.
 */
@Composable
fun Player.positionAndDurationState(key1: Any? = null, active: Boolean = true): State<Pair<Long, Long>> {
    val state = remember(key1) {
        mutableStateOf(currentPosition to duration)
    }
    val activeRef = rememberUpdatedState(active)

    LaunchedEffect(this, key1) {
        var isSeeking = false
        var needsUpdate = false

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isSeeking = false
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Do not modify the state directly in the callback
                // The polling coroutine will take care of it.
                needsUpdate = true
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    isSeeking = true
                    needsUpdate = true
                }
            }
        }

        addListener(listener)

        val pollJob = launch {
            while (isActive) {
                withFrameNanos { }
                if (activeRef.value && (!isSeeking || needsUpdate)) {
                    state.value = currentPosition to duration
                    needsUpdate = false
                }
            }
        }

        try {
            suspendCancellableCoroutine<Nothing> { }
        } finally {
            pollJob.cancel()
            removeListener(listener)
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



