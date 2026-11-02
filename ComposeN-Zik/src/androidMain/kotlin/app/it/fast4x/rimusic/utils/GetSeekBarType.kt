package app.it.fast4x.rimusic.utils

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import app.it.fast4x.rimusic.models.ui.UiMedia
import app.it.fast4x.rimusic.ui.components.SeekBar
import app.it.fast4x.rimusic.ui.components.SeekBarColored
import app.it.fast4x.rimusic.ui.components.SeekBarCustom
import app.it.fast4x.rimusic.ui.components.SeekBarThin
import app.it.fast4x.rimusic.ui.components.SeekBarWaved
import app.it.fast4x.rimusic.ui.styling.collapsedPlayerProgressBar
import app.kreate.android.themed.rimusic.screen.player.timeline.DurationIndicator
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.n_zik.android.components.player.PENDING_SEEK_POLL_INTERVAL_MS
import app.n_zik.android.components.player.PlayerDurationBounds
import app.n_zik.android.components.player.PlayerSeekGlass
import app.n_zik.android.components.player.PlayerTimelineSlot
import app.n_zik.android.components.player.playerSeekBarBounds
import app.n_zik.android.components.player.playerSeekGestureCleanup
import app.n_zik.android.components.player.rememberCurrentSeekEndCallback
import app.n_zik.android.components.player.rememberCurrentSeekValueCallback
import app.n_zik.android.components.player.rememberPlayerSeekIsPlaying
import app.n_zik.android.components.player.shouldReleasePendingSeekPosition
import app.n_zik.android.components.player.skipBasePosition
import app.n_zik.android.extensions.audiobar.views.ProgressPercentage
import app.n_zik.android.extensions.audiobar.views.SeekBarStaticAudioWaves
import app.n_zik.android.extensions.audiobar.views.SeekBarVisualizer
import app.n_zik.android.listentogether.ListenTogetherGuestGuardPlayer
import app.n_zik.android.listentogether.rememberListenTogetherGuestLock
import app.n_zik.android.uiRoundnessShape
import kotlinx.coroutines.delay

const val DURATION_INDICATOR_HEIGHT = 20

@OptIn(UnstableApi::class)
@Composable
fun GetSeekBar(
    position: () -> Long,
    duration: () -> Long,
    mediaId: String,
    media: UiMedia,
    shouldBePlaying: Boolean,
    isBuffering: Boolean,
) {
    val binder = LocalPlayerServiceBinder.current
    binder?.player ?: return
    val playerTimelineType by rememberPreference(playerTimelineTypeKey, PlayerTimelineType.Wavy)
    val seekDuration = duration()
    // Native handlers must be recreated together; their cancelled callbacks cannot target a new track.
    val gestureBoundary = Triple(mediaId, seekDuration, playerTimelineType)
    val currentGestureBoundary by rememberUpdatedState(gestureBoundary)
    val isCurrentGesture = { currentGestureBoundary == gestureBoundary }
    var scrubbingPosition by remember(gestureBoundary) {
        mutableStateOf<Long?>(null)
    }
    var pendingSeekTarget by remember(mediaId, seekDuration) {
        mutableStateOf<Long?>(null)
    }
    var pendingSeekIssuedAtMs by remember(mediaId, seekDuration) {
        mutableLongStateOf(0L)
    }
    fun holdSeekTarget(target: Long) {
        pendingSeekTarget = target
        pendingSeekIssuedAtMs = SystemClock.elapsedRealtime()
    }
    val transparentbar by rememberPreference(transparentbarKey, true)
    val ltGuestLocked = rememberListenTogetherGuestLock()
    val ltContext = LocalContext.current

    LaunchedEffect(pendingSeekTarget) {
        val target = pendingSeekTarget ?: return@LaunchedEffect
        val startedAtMs = SystemClock.elapsedRealtime()
        while (pendingSeekTarget == target) {
            if (shouldReleasePendingSeekPosition(
                    target,
                    binder.player.currentPosition,
                    SystemClock.elapsedRealtime() - startedAtMs,
                )
            ) {
                pendingSeekTarget = null
                break
            }
            delay(PENDING_SEEK_POLL_INTERVAL_MS)
        }
    }

    PlayerTimelineSlot(
        type = playerTimelineType,
        modifier = Modifier
            .playerSeekBarBounds()
            .playerSeekGestureCleanup(mediaId) { scrubbingPosition = null }
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
    ) {
        if (seekDuration <= 0L) {
            if (shouldBePlaying && seekDuration == C.TIME_UNSET) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = colorPalette().collapsedPlayerProgressBar
                )
            } else {
                LinearProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = colorPalette().collapsedPlayerProgressBar
                )
            }
        } else {
            key(gestureBoundary) {
                PlayerSeekGlass(
                    type = playerTimelineType,
                    enabled = !ltGuestLocked,
                    fraction = {
                        val range = if (playerTimelineType == PlayerTimelineType.Wavy) media.duration else duration()
                        if (range > 0) (scrubbingPosition ?: pendingSeekTarget ?: position()).toFloat() / range else 0f
                    },
                ) { glassThumb ->
                    val isActuallyPlaying = rememberPlayerSeekIsPlaying(binder)
                    if (playerTimelineType != PlayerTimelineType.Default
                        && playerTimelineType != PlayerTimelineType.Wavy
                        && playerTimelineType != PlayerTimelineType.AudioWaves
                        && playerTimelineType != PlayerTimelineType.ThinBar
                        && playerTimelineType != PlayerTimelineType.ColoredBar
                        && playerTimelineType != PlayerTimelineType.VisualizerBar
                    )
                        SeekBarCustom(
                            glassThumb = glassThumb,
                            type = playerTimelineType,
                            value = scrubbingPosition ?: pendingSeekTarget ?: position(),
                            minimumValue = 0,
                            maximumValue = duration(),
                            onDragStart = rememberCurrentSeekValueCallback(isCurrentGesture) {
                                if (ltGuestLocked) {
                                    ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                                } else {
                                    pendingSeekTarget = null
                                    scrubbingPosition = it.coerceIn(0L, seekDuration)
                                }
                            },
                            onDrag = rememberCurrentSeekValueCallback(isCurrentGesture) { delta ->
                                scrubbingPosition = if (duration() != C.TIME_UNSET) {
                                    scrubbingPosition?.plus(delta)?.coerceIn(0, duration())
                                } else {
                                    null
                                }
                            },
                            onDragEnd = rememberCurrentSeekEndCallback(isCurrentGesture) {
                                if (ltGuestLocked) {
                                    ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                                } else {
                                    scrubbingPosition?.let {
                                        val target = it.coerceIn(0L, seekDuration)
                                        holdSeekTarget(target)
                                        binder.player.seekTo(target)
                                    }
                                }
                                scrubbingPosition = null
                            },
                            color = colorPalette().collapsedPlayerProgressBar,
                            backgroundColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                            shape = uiRoundnessShape(),
                        )

                    if (playerTimelineType == PlayerTimelineType.Default)
                        SeekBar(
                            glassThumb = glassThumb,
                            value = scrubbingPosition ?: pendingSeekTarget ?: position(),
                            minimumValue = 0,
                            maximumValue = duration(),
                            onDragStart = rememberCurrentSeekValueCallback(isCurrentGesture) {
                                if (ltGuestLocked) {
                                    ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                                } else {
                                    pendingSeekTarget = null
                                    scrubbingPosition = it.coerceIn(0L, seekDuration)
                                }
                            },
                            onDrag = rememberCurrentSeekValueCallback(isCurrentGesture) { delta ->
                                scrubbingPosition = if (duration() != C.TIME_UNSET) {
                                    scrubbingPosition?.plus(delta)?.coerceIn(0, duration())
                                } else {
                                    null
                                }
                            },
                            onDragEnd = rememberCurrentSeekEndCallback(isCurrentGesture) {
                                if (ltGuestLocked) {
                                    ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                                } else {
                                    scrubbingPosition?.let {
                                        val target = it.coerceIn(0L, seekDuration)
                                        holdSeekTarget(target)
                                        binder.player.seekTo(target)
                                    }
                                }
                                scrubbingPosition = null
                            },
                            color = colorPalette().collapsedPlayerProgressBar,
                            backgroundColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                            shape = uiRoundnessShape(),
                        )

                    if (playerTimelineType == PlayerTimelineType.ThinBar)
                        SeekBarThin(
                            glassThumb = glassThumb,
                            value = scrubbingPosition ?: pendingSeekTarget ?: position(),
                            minimumValue = 0,
                            maximumValue = duration(),
                            onDragStart = rememberCurrentSeekValueCallback(isCurrentGesture) {
                                if (ltGuestLocked) {
                                    ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                                } else {
                                    pendingSeekTarget = null
                                    scrubbingPosition = it.coerceIn(0L, seekDuration)
                                }
                            },
                            onDrag = rememberCurrentSeekValueCallback(isCurrentGesture) { delta ->
                                scrubbingPosition = if (duration() != C.TIME_UNSET) {
                                    scrubbingPosition?.plus(delta)?.coerceIn(0, duration())
                                } else {
                                    null
                                }
                            },
                            onDragEnd = rememberCurrentSeekEndCallback(isCurrentGesture) {
                                if (ltGuestLocked) {
                                    ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                                } else {
                                    scrubbingPosition?.let {
                                        val target = it.coerceIn(0L, seekDuration)
                                        holdSeekTarget(target)
                                        binder.player.seekTo(target)
                                    }
                                }
                                scrubbingPosition = null
                            },
                            color = colorPalette().collapsedPlayerProgressBar,
                            backgroundColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                            shape = uiRoundnessShape(),
                        )

                    if (playerTimelineType == PlayerTimelineType.Wavy) {
                        SeekBarWaved(
                            glassThumb = glassThumb,
                            position = { (scrubbingPosition ?: pendingSeekTarget)?.toFloat() ?: position().toFloat() },
                            range = 0f..media.duration.toFloat(),
                            onSeekStarted = rememberCurrentSeekValueCallback(isCurrentGesture) {
                                if (ltGuestLocked) {
                                    ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                                } else {
                                    pendingSeekTarget = null
                                    scrubbingPosition = it.toLong().coerceIn(0L, seekDuration)
                                }
                            },
                            onSeek = rememberCurrentSeekValueCallback(isCurrentGesture) { delta ->
                                scrubbingPosition = if (duration() != C.TIME_UNSET) {
                                    scrubbingPosition?.plus(delta)?.coerceIn(0F, duration().toFloat())
                                        ?.toLong()
                                } else {
                                    null
                                }
                            },
                            onSeekFinished = rememberCurrentSeekEndCallback(isCurrentGesture) {
                                if (ltGuestLocked) {
                                    ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                                } else {
                                    scrubbingPosition?.let {
                                        val target = it.coerceIn(0L, seekDuration)
                                        holdSeekTarget(target)
                                        binder.player.seekTo(target)
                                    }
                                }
                                scrubbingPosition = null
                            },
                            color = colorPalette().collapsedPlayerProgressBar,
                            isActive = isActuallyPlaying,
                            backgroundColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                            shape = uiRoundnessShape(),
                        )
                    }

                    if (playerTimelineType == PlayerTimelineType.VisualizerBar) {
                        SeekBarVisualizer(
                            glassThumb = glassThumb,
                            audioSessionIdProvider = { try { binder.player.audioSessionId } catch (e: Exception) { null } },
                            isPlaying = isActuallyPlaying,
                            progressPercentage = {
                                val held = pendingSeekTarget ?: position()
                                ProgressPercentage.safeValue((held.toFloat() / duration().toFloat()).coerceIn(0f, 1f))
                            },
                            playedColor = colorPalette().accent,
                            notPlayedColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                            waveInteraction = rememberCurrentSeekValueCallback(isCurrentGesture) {
                                if (ltGuestLocked) {
                                    ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                                } else {
                                    val target = (it.value * seekDuration.toFloat()).toLong().coerceIn(0L, seekDuration)
                                    holdSeekTarget(target)
                                    binder.player.seekTo(target)
                                }
                            },
                            modifier = Modifier
                                .height(50.dp)
                        )
                    }

                    if (playerTimelineType == PlayerTimelineType.AudioWaves) {
                        SeekBarStaticAudioWaves(
                            glassThumb = glassThumb,
                            uiMedia = media,
                            position = scrubbingPosition ?: pendingSeekTarget ?: position(),
                            duration = duration(),
                            isPlaying = isActuallyPlaying,
                            onPositionChange = rememberCurrentSeekValueCallback(isCurrentGesture) {
                                if (ltGuestLocked) {
                                    ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                                } else {
                                    pendingSeekTarget = null
                                    scrubbingPosition = it.coerceIn(0L, seekDuration)
                                }
                            },
                            onPositionChangeFinished = rememberCurrentSeekEndCallback(isCurrentGesture) {
                                if (ltGuestLocked) {
                                    ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                                } else {
                                    scrubbingPosition?.let {
                                        val target = it.coerceIn(0L, seekDuration)
                                        holdSeekTarget(target)
                                        binder.player.seekTo(target)
                                    }
                                }
                                scrubbingPosition = null
                            },
                            audioSessionId = { try { binder.player.audioSessionId } catch (e: Exception) { -1 } },
                            unplayedColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        )
                    }

                    if (playerTimelineType == PlayerTimelineType.ColoredBar)
                        SeekBarColored(
                            glassThumb = glassThumb,
                            value = scrubbingPosition ?: pendingSeekTarget ?: position(),
                            minimumValue = 0,
                            maximumValue = duration(),
                            onDragStart = rememberCurrentSeekValueCallback(isCurrentGesture) {
                                if (ltGuestLocked) {
                                    ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                                } else {
                                    pendingSeekTarget = null
                                    scrubbingPosition = it.coerceIn(0L, seekDuration)
                                }
                            },
                            onDrag = rememberCurrentSeekValueCallback(isCurrentGesture) { delta ->
                                scrubbingPosition = if (duration() != C.TIME_UNSET) {
                                    scrubbingPosition?.plus(delta)?.coerceIn(0, duration())
                                } else {
                                    null
                                }
                            },
                            onDragEnd = rememberCurrentSeekEndCallback(isCurrentGesture) {
                                if (ltGuestLocked) {
                                    ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                                } else {
                                    scrubbingPosition?.let {
                                        val target = it.coerceIn(0L, seekDuration)
                                        holdSeekTarget(target)
                                        binder.player.seekTo(target)
                                    }
                                }
                                scrubbingPosition = null
                            },
                            color = colorPalette().collapsedPlayerProgressBar,
                            backgroundColor = colorPalette().textSecondary,
                            shape = uiRoundnessShape()
                        )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    PlayerDurationBounds {
        DurationIndicator(
            binder,
            scrubbingPosition ?: pendingSeekTarget,
            scrubbingPosition ?: pendingSeekTarget ?: position(),
            duration(),
            onSeekIssued = { holdSeekTarget(it) },
            seekBasePosition = {
                skipBasePosition(
                    scrubbingMs = scrubbingPosition,
                    pendingTargetMs = pendingSeekTarget,
                    pendingHeldForMs = SystemClock.elapsedRealtime() - pendingSeekIssuedAtMs,
                    livePlayerPositionMs = binder.player.currentPosition,
                )
            },
        )
    }
}
