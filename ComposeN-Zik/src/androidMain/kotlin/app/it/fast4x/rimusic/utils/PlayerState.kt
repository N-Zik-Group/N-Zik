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
 * Issue #881 (gh-881), Phase 3.2 (R1) — the poll loop above is only the BASE stream: while
 * [active] is false it does not write, so a track change or a committed seek that happens
 * while the sheet is hidden left the cache on the previous value until the next gated tick
 * (field logs 2026-10-04: the bar stayed on the previous track's position for minutes after a
 * screen-off track change, while Metrolist/RiPlay/Kreate — which re-anchor the cache from the
 * player's OWN events — never show the freeze on the same device). The listener below writes
 * the live position on `onMediaItemTransition` and on every committed seek
 * (`onPositionDiscontinuity` with [Player.DISCONTINUITY_REASON_SEEK]), regardless of
 * [active] — the RiMusic/Kreate pattern (N-Zik's upstream), behaviourally verbatim. No latch
 * is set on the seek: a latch would reintroduce a state that can get stuck when `STATE_READY`
 * is delayed or missing — the exact failure mode Phases 2/3 had to remove. While the seek is
 * still in flight the poll loop keeps reporting the pre-seek position, which the bar never
 * shows because GetSeekBar holds the tapped target until the player converges on it
 * (pendingSeekTarget, Phase 3 Fix E + Phase 3.2 R2).
 *
 * Phase 3.2 (R3) — a one-shot re-seed whenever the visibility gate [active] flips or the
 * player instance changes (Metrolist's `LaunchedEffect(playbackState, mediaMetadata?.id)`
 * re-sync): the cache is written from the live player IMMEDIATELY, without waiting for the
 * next poll tick.
 *
 * @param active Gates ONLY the poll loop above: when false it does not write, so there is no
 * per-tick recomposition churn while the player content is hidden behind the mini-player.
 * The event writes (R1) and the one-shot re-seed (R3) are NOT gated — they still run while
 * inactive (writing an unobserved state is cheap), so a hidden consumer is re-anchored as
 * soon as it becomes visible again.
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

    // Issue #881 (gh-881), Phase 3.2 (R1): event-driven re-anchoring from the player's own
    // callbacks (RiMusic/Kreate pattern — see the function KDoc). Runs regardless of the
    // [active] gate: while the sheet is hidden the state has no visible observers, so the
    // writes cost nothing and keep the cache correct for the next time the sheet is shown.
    DisposableListener(key1) {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // RiMusic: keep the previously cached duration — the new stream's duration is
                // not known yet, and jumping the slider range mid-transition would be visible.
                state.value = currentPosition to state.value.second
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    // Committed seek: ExoPlayer updates the position before this callback, so
                    // the live position IS the seek target — re-anchor immediately.
                    state.value = currentPosition to duration
                }
            }
        }
    }

    // Issue #881 (gh-881), Phase 3.2 (R3): explicit one-shot re-sync when the sheet
    // visibility gate flips or the player instance changes (Metrolist pattern). Writing the
    // same value is a snapshot no-op, so this is safe on every gate oscillation.
    LaunchedEffect(this, active) {
        state.value = currentPosition to duration
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



