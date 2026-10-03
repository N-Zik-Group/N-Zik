package app.it.fast4x.rimusic.utils

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.listentogether.ListenTogetherGuestGuardPlayer
import app.n_zik.android.listentogether.rememberListenTogetherGuestLock
import app.kreate.android.themed.rimusic.screen.player.timeline.DurationIndicator
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.n_zik.android.components.player.PENDING_SEEK_POLL_INTERVAL_MS
import app.n_zik.android.components.player.shouldReleasePendingSeekPosition
import app.n_zik.android.components.player.skipBasePosition
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.PauseBetweenSongs
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import app.it.fast4x.rimusic.models.ui.UiMedia
import app.n_zik.android.typography
import app.n_zik.android.extensions.audiobar.views.ProgressPercentage
import app.it.fast4x.rimusic.ui.components.SeekBar
import app.n_zik.android.extensions.audiobar.views.SeekBarVisualizer
import app.it.fast4x.rimusic.ui.components.SeekBarColored
import app.it.fast4x.rimusic.ui.components.SeekBarCustom
import app.it.fast4x.rimusic.ui.components.SeekBarThin
import app.it.fast4x.rimusic.ui.components.SeekBarWaved
import app.it.fast4x.rimusic.ui.styling.collapsedPlayerProgressBar
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.extensions.audiobar.views.SeekBarStaticAudioWaves

const val DURATION_INDICATOR_HEIGHT = 20

@OptIn(UnstableApi::class)
@Composable
fun GetSeekBar(
    position: () -> Long,
    duration: () -> Long,
    mediaId: String,
    media: UiMedia,
    shouldBePlaying: Boolean,
    isBuffering: Boolean
    ) {
    val binder = LocalPlayerServiceBinder.current
    binder?.player ?: return
    val playerTimelineType by rememberPreference(playerTimelineTypeKey, PlayerTimelineType.Wavy)
    var scrubbingPosition by remember(mediaId) {
        mutableStateOf<Long?>(null)
    }
    // Issue #881 (gh-881), Phase 3 Fix E: the tapped / skip-button seek target held on the bar
    // until the player COMMITS the seek. Without it the bar snaps back to the stale player
    // position on every seek (player reports the pre-seek position until the seek commits),
    // the user-visible "l'ancienne position brièvement puis téléport" (field report 2026-10-03).
    // Cleared by the release effect below (convergence / safety timeout) or by the next drag.
    var pendingSeekTarget by remember(mediaId) {
        mutableStateOf<Long?>(null)
    }
    // Issue #881 (gh-881), Phase 3.1: when the target was issued — lets the skip buttons
    // decide AT TAP TIME whether the held target is still the right base (see skipBasePosition).
    var pendingSeekIssuedAtMs by remember(mediaId) {
        mutableLongStateOf(0L)
    }
    fun holdSeekTarget(target: Long) {
        pendingSeekTarget = target
        pendingSeekIssuedAtMs = SystemClock.elapsedRealtime()
    }
    var transparentbar by rememberPreference(transparentbarKey, true)
    val scope = rememberCoroutineScope()
    // Listen Together guest lock (spec-listen-together-guest-lock-hardening): a guest in a room
    // cannot seek — the bar is fully INERT: the scrubber position is never captured (no visual
    // jump) and no seek op is issued; every interaction attempt shows the throttled "the host
    // controls playback" toast. The bar keeps full opacity and size — .alpha() must NOT be
    // applied to this subtree: alpha < 1 promotes content to a compositing layer sized to the
    // layout bounds and implicitly clips everything drawn outside them, which cut the Wavy bar's
    // overflow drawing (15px-stroke wave + 15-20dp scrubber pill around a 6dp box) and made it
    // look "crushed" (user feedback 2026-09-29, confirmed by the Modifier.alpha docs).
    val ltGuestLocked = rememberListenTogetherGuestLock()
    val ltContext = LocalContext.current

    // Issue #881 (gh-881), Phase 3 Fix E: release the held target once the player converges on
    // it (see shouldReleasePendingSeekPosition) — or after the safety timeout if the seek never
    // commits (error state). Keyed on the target: a new tap/drag restarts the effect with the
    // new value, and remember(mediaId) drops it on track changes.
    // The convergence source is the SAME `position()` the bar renders from, i.e. the app's
    // 100 ms poll cache (positionAndDurationState, Phase 3.1): it freezes only while the sheet
    // content is inactive (active=false) — in that case the hold simply survives until the
    // 10 s safety timeout, which is the intended behavior on a screen the user is not looking
    // at (review finding: the coupling is documented, not accidental).
    LaunchedEffect(pendingSeekTarget) {
        val target = pendingSeekTarget ?: return@LaunchedEffect
        val startedAtMs = SystemClock.elapsedRealtime()
        while (pendingSeekTarget == target) {
            if (shouldReleasePendingSeekPosition(
                    target,
                    position(),
                    SystemClock.elapsedRealtime() - startedAtMs,
                )
            ) {
                pendingSeekTarget = null
                break
            }
            delay(PENDING_SEEK_POLL_INTERVAL_MS)
        }
    }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
    ) {

        if (duration() == C.TIME_UNSET) {
            if (shouldBePlaying) {
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

        if (playerTimelineType != PlayerTimelineType.Default
            && playerTimelineType != PlayerTimelineType.Wavy
            && playerTimelineType != PlayerTimelineType.AudioWaves
            && playerTimelineType != PlayerTimelineType.ThinBar
            && playerTimelineType != PlayerTimelineType.ColoredBar
            && playerTimelineType != PlayerTimelineType.VisualizerBar
            && playerTimelineType != PlayerTimelineType.BodiedBar
        )
            SeekBarCustom(
                type = playerTimelineType,
                value = scrubbingPosition ?: pendingSeekTarget ?: position(),
                minimumValue = 0,
                maximumValue = duration(),
                onDragStart = {
                    // Guest lock: the seek bar is inert — capture nothing (no scrubber jump);
                    // the interaction attempt is explained by the throttled blocked toast.
                    if (ltGuestLocked) {
                        ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                    } else {
                        // Issue #881 (gh-881), Phase 3 Fix E: a new drag takes over the display —
                        // drop any held seek target.
                        pendingSeekTarget = null
                        scrubbingPosition = it
                    }
                },
                onDrag = { delta ->
                    scrubbingPosition = if (duration() != C.TIME_UNSET) {
                        scrubbingPosition?.plus(delta)?.coerceIn(0, duration())
                    } else {
                        null
                    }
                },
                onDragEnd = {
                    // Issue #881 (gh-881), Phase 3 Fix E (review patch): the guest lock may
                    // have engaged DURING the drag — re-check it so a seek the guarded facade
                    // vetoes is not held on the bar (parity with the skip-button path, which
                    // guards onSeekIssued).
                    if (ltGuestLocked) {
                        ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                    } else {
                        // Hold the tapped position on the bar until the player commits the seek
                        // (pendingSeekTarget release effect) instead of snapping back to the
                        // stale player position.
                        scrubbingPosition?.let {
                            holdSeekTarget(it)
                            binder.player.seekTo(it)
                        }
                    }
                    scrubbingPosition = null
                },
                color = colorPalette().collapsedPlayerProgressBar,
                backgroundColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                shape = uiRoundnessShape(),
                //modifier = Modifier.pulsatingEffect(currentValue = scrubbingPosition?.toFloat() ?: position().toFloat(), isVisible = true)
            )

        if (playerTimelineType == PlayerTimelineType.Default)
            SeekBar(
                value = scrubbingPosition ?: pendingSeekTarget ?: position(),
                minimumValue = 0,
                maximumValue = duration(),
                onDragStart = {
                    // Guest lock: the seek bar is inert — capture nothing (no scrubber jump);
                    // the interaction attempt is explained by the throttled blocked toast.
                    if (ltGuestLocked) {
                        ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                    } else {
                        // Issue #881 (gh-881), Phase 3 Fix E: a new drag takes over the display —
                        // drop any held seek target.
                        pendingSeekTarget = null
                        scrubbingPosition = it
                    }
                },
                onDrag = { delta ->
                    scrubbingPosition = if (duration() != C.TIME_UNSET) {
                        scrubbingPosition?.plus(delta)?.coerceIn(0, duration())
                    } else {
                        null
                    }
                },
                onDragEnd = {
                    // Issue #881 (gh-881), Phase 3 Fix E (review patch): the guest lock may
                    // have engaged DURING the drag — re-check it so a seek the guarded facade
                    // vetoes is not held on the bar (parity with the skip-button path, which
                    // guards onSeekIssued).
                    if (ltGuestLocked) {
                        ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                    } else {
                        // Hold the tapped position on the bar until the player commits the seek
                        // (pendingSeekTarget release effect) instead of snapping back to the
                        // stale player position.
                        scrubbingPosition?.let {
                            holdSeekTarget(it)
                            binder.player.seekTo(it)
                        }
                    }
                    scrubbingPosition = null
                },
                color = colorPalette().collapsedPlayerProgressBar,
                backgroundColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                shape = uiRoundnessShape(),
                //modifier = Modifier.pulsatingEffect(currentValue = scrubbingPosition?.toFloat() ?: position().toFloat(), isVisible = true)
            )

        if (playerTimelineType == PlayerTimelineType.ThinBar)
            SeekBarThin(
                value = scrubbingPosition ?: pendingSeekTarget ?: position(),
                minimumValue = 0,
                maximumValue = duration(),
                onDragStart = {
                    // Guest lock: the seek bar is inert — capture nothing (no scrubber jump);
                    // the interaction attempt is explained by the throttled blocked toast.
                    if (ltGuestLocked) {
                        ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                    } else {
                        // Issue #881 (gh-881), Phase 3 Fix E: a new drag takes over the display —
                        // drop any held seek target.
                        pendingSeekTarget = null
                        scrubbingPosition = it
                    }
                },
                onDrag = { delta ->
                    scrubbingPosition = if (duration() != C.TIME_UNSET) {
                        scrubbingPosition?.plus(delta)?.coerceIn(0, duration())
                    } else {
                        null
                    }
                },
                onDragEnd = {
                    // Issue #881 (gh-881), Phase 3 Fix E (review patch): the guest lock may
                    // have engaged DURING the drag — re-check it so a seek the guarded facade
                    // vetoes is not held on the bar (parity with the skip-button path, which
                    // guards onSeekIssued).
                    if (ltGuestLocked) {
                        ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                    } else {
                        // Hold the tapped position on the bar until the player commits the seek
                        // (pendingSeekTarget release effect) instead of snapping back to the
                        // stale player position.
                        scrubbingPosition?.let {
                            holdSeekTarget(it)
                            binder.player.seekTo(it)
                        }
                    }
                    scrubbingPosition = null
                },
                color = colorPalette().collapsedPlayerProgressBar,
                backgroundColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                shape = uiRoundnessShape(),
                //modifier = Modifier.pulsatingEffect(currentValue = scrubbingPosition?.toFloat() ?: position().toFloat(), isVisible = true)
            )

        if (playerTimelineType == PlayerTimelineType.Wavy) {
            SeekBarWaved(
                position = { (scrubbingPosition ?: pendingSeekTarget)?.toFloat() ?: position().toFloat() },
                range = 0f..media.duration.toFloat(),
                onSeekStarted = {
                    // Guest lock: the seek bar is inert — capture nothing (no scrubber jump);
                    // the interaction attempt is explained by the throttled blocked toast.
                    if (ltGuestLocked) {
                        ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                    } else {
                        // Issue #881 (gh-881), Phase 3 Fix E: a new drag takes over the display —
                        // drop any held seek target.
                        pendingSeekTarget = null
                        scrubbingPosition = it.toLong()
                    }
                },
                onSeek = { delta ->
                    scrubbingPosition = if (duration() != C.TIME_UNSET) {
                        scrubbingPosition?.plus(delta)?.coerceIn(0F, duration().toFloat())
                            ?.toLong()
                    } else {
                        null
                    }
                },
                onSeekFinished = {
                    // Issue #881 (gh-881), Phase 3 Fix E (review patch): the guest lock may
                    // have engaged DURING the drag — re-check it so a seek the guarded facade
                    // vetoes is not held on the bar (parity with the skip-button path).
                    if (ltGuestLocked) {
                        ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                    } else {
                        // Hold the tapped position on the bar until the player commits the seek
                        // (pendingSeekTarget release effect).
                        scrubbingPosition?.let {
                            holdSeekTarget(it)
                            binder.player.seekTo(it)
                        }
                    }
                    scrubbingPosition = null
                },
                color = colorPalette().collapsedPlayerProgressBar,
                isActive = binder.player.isPlaying,
                backgroundColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                shape = uiRoundnessShape(),
            )
        }

        if (playerTimelineType == PlayerTimelineType.VisualizerBar) {
            SeekBarVisualizer(
                audioSessionIdProvider = { try { binder.player.audioSessionId } catch (e: Exception) { null } },
                isPlaying = binder.player.isPlaying,
                progressPercentage = {
                    // Issue #881 (gh-881), Phase 3 Fix E: show the held target until the
                    // player commits the seek.
                    val held = pendingSeekTarget ?: position()
                    ProgressPercentage.safeValue((held.toFloat() / duration().toFloat()).coerceIn(0f, 1f))
                },
                playedColor = colorPalette().accent,
                notPlayedColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                waveInteraction = {
                    // Guest lock: the seek bar is inert — no seek is issued; the interaction
                    // attempt is explained by the throttled blocked toast.
                    if (ltGuestLocked) {
                        ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                    } else {
                        // Issue #881 (gh-881), Phase 3 Fix E (review patch): local val instead
                        // of re-reading the delegated state with `!!` (AGENTS.md no-!! rule).
                        val target = (it.value * duration().toFloat()).toLong()
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
                uiMedia = media,
                position = scrubbingPosition ?: pendingSeekTarget ?: position(),
                duration = duration(),
                isPlaying = binder.player.isPlaying,
                onPositionChange = {
                    // Guest lock: the seek bar is inert — capture nothing; the (throttled)
                    // blocked toast explains the interaction attempt.
                    if (ltGuestLocked) {
                        ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                    } else {
                        // Issue #881 (gh-881), Phase 3 Fix E: a new drag takes over the display —
                        // drop any held seek target.
                        pendingSeekTarget = null
                        scrubbingPosition = it
                    }
                },
                onPositionChangeFinished = {
                    // Issue #881 (gh-881), Phase 3 Fix E (review patch): the guest lock may
                    // have engaged DURING the drag — re-check it so a seek the guarded facade
                    // vetoes is not held on the bar (parity with the skip-button path).
                    if (ltGuestLocked) {
                        ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                    } else {
                        // Hold the tapped position on the bar until the player commits the seek
                        // (pendingSeekTarget release effect).
                        scrubbingPosition?.let {
                            holdSeekTarget(it)
                            binder.player.seekTo(it)
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
                value = scrubbingPosition ?: pendingSeekTarget ?: position(),
                minimumValue = 0,
                maximumValue = duration(),
                onDragStart = {
                    // Guest lock: the seek bar is inert — capture nothing (no scrubber jump);
                    // the interaction attempt is explained by the throttled blocked toast.
                    if (ltGuestLocked) {
                        ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                    } else {
                        // Issue #881 (gh-881), Phase 3 Fix E: a new drag takes over the display —
                        // drop any held seek target.
                        pendingSeekTarget = null
                        scrubbingPosition = it
                    }
                },
                onDrag = { delta ->
                    scrubbingPosition = if (duration() != C.TIME_UNSET) {
                        scrubbingPosition?.plus(delta)?.coerceIn(0, duration())
                    } else {
                        null
                    }
                },
                onDragEnd = {
                    // Issue #881 (gh-881), Phase 3 Fix E (review patch): the guest lock may
                    // have engaged DURING the drag — re-check it so a seek the guarded facade
                    // vetoes is not held on the bar (parity with the skip-button path, which
                    // guards onSeekIssued).
                    if (ltGuestLocked) {
                        ListenTogetherGuestGuardPlayer.reportUiBlockedOp(ltContext)
                    } else {
                        // Hold the tapped position on the bar until the player commits the seek
                        // (pendingSeekTarget release effect) instead of snapping back to the
                        // stale player position.
                        scrubbingPosition?.let {
                            holdSeekTarget(it)
                            binder.player.seekTo(it)
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

    Spacer( modifier = Modifier.height( 8.dp ) )

    DurationIndicator(
        binder,
        scrubbingPosition ?: pendingSeekTarget,
        // Issue #881 (gh-881), Phase 3 Fix E (review patch): the skip buttons must compute
        // their adjustment from the HELD target, not the stale player position — otherwise a
        // consecutive tap lands from the old position while the label shows the target.
        scrubbingPosition ?: pendingSeekTarget ?: position(),
        duration(),
        // Issue #881 (gh-881), Phase 3 Fix E: skip-button seeks hold their target on the label
        // until the player commits it (same pendingSeekTarget state as the bar taps).
        onSeekIssued = { holdSeekTarget(it) },
        // Issue #881 (gh-881), Phase 3.1: the skip base is read LIVE at tap time — the composed
        // position above froze on the previous track after a screen-off track change (field
        // logs 2026-10-04), so every tap re-seeked to the same stale value ± 5/30 s.
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





