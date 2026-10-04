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
 * Issue #881 (gh-881), Phase 3.2 (R1) — in addition to the poll loop, the cache is re-anchored
 * from the player's OWN events (RiMusic/Kreate pattern — N-Zik's upstream, behaviourally
 * verbatim): `onMediaItemTransition` writes the live position keeping the previously cached
 * duration (the new stream's duration is not known yet, and jumping the slider range
 * mid-transition would be visible), and a committed seek
 * (`onPositionDiscontinuity` with [Player.DISCONTINUITY_REASON_SEEK]) writes the live position
 * and the live duration immediately. No latch is set on the seek: a latch would reintroduce
 * a state that can get stuck when `STATE_READY` is delayed or missing — the exact failure
 * mode Phases 2/3 had to remove. While the seek is still in flight the poll loop keeps
 * reporting the pre-seek position, which the bar never shows because GetSeekBar holds the
 * tapped target until the player converges on it (pendingSeekTarget, Phase 3 Fix E + R2).
 *
 * Issue #881 (gh-881), Phase 3.3 — the poll loop is UNCONDITIONAL: there is no sheet-visibility
 * gate. The gate (a N-Zik-only optimization; every healthy reference polls regardless of sheet
 * visibility — RiPlay unconditionally, Kreate/RiMusic ungated, Metrolist gated only on
 * `isPlaying`) kept the cache on the last event value whenever gate and visible UI disagreed:
 * field logs 2026-10-04 (FURY, OPPO / Android 15) show the bar frozen on the last seek target
 * for up to 5 s after screen off/on + background cycles, on an activity that was continuously
 * resumed, while skip taps still sought correctly from the live position. The write-if-changed
 * guard in the loop below bounds the cost: while paused the position is static, so there is no
 * state write and no recomposition; while playing with the sheet hidden the hidden subtrees
 * recompose at up to 10 Hz — the accepted trade-off (the healthy references run per-call-site
 * pollers without this gate and the same device shows no measurable cost, while the gated
 * cache produced the field-verified freeze). The poll's duration write is guarded the same way
 * Metrolist guards its poll: while a stream is resolving the player's duration is
 * `C.TIME_UNSET`, and writing it would clobber the duration the R1 transition write kept —
 * the guard holds the cached duration until the player reports a real one. The cache re-seeds
 * on composition and on every [key1] or player-instance change because the poll effect
 * restarts there and its first iteration writes the live value immediately (this replaces
 * Phase 3.2's one-shot gate-flip re-seed, R3, which only existed to cover the gate).
 */
@Composable
fun Player.positionAndDurationState(key1: Any? = null): State<Pair<Long, Long>> {
    val state = remember(key1) {
        mutableStateOf(currentPosition to duration)
    }

    // Issue #881 (gh-881), Phase 3.3: unconditional poll — see the function KDoc for the
    // gate-removal rationale. Restarts on composition, [key1] change and player-instance swap;
    // the first iteration writes the live value immediately, so no separate re-seed effect
    // is needed.
    LaunchedEffect(this, key1) {
        while (isActive) {
            // Metrolist's guard: while a stream is resolving the player's duration is
            // C.TIME_UNSET — keep the cached duration instead of clobbering it (that is also
            // what the R1 transition write preserves).
            val sample = currentPosition to (duration.takeIf { it > 0 } ?: state.value.second)
            if (sample != state.value) state.value = sample
            delay(POSITION_POLL_INTERVAL_MS)
        }
    }

    // Issue #881 (gh-881), Phase 3.2 (R1): event-driven re-anchoring from the player's own
    // callbacks (RiMusic/Kreate pattern — see the function KDoc): a sub-poll-tick re-anchor
    // on track changes and committed seeks, on top of the loop above.
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



