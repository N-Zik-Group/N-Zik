package app.kreate.android.themed.rimusic.screen.player.timeline

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.PLAYER_SHEET_HANDOVER_PROGRESS
import app.n_zik.android.components.player.durationOutlineColorOf

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.colorPalette
import app.n_zik.android.listentogether.rememberListenTogetherGuestLock
import app.it.fast4x.rimusic.enums.PauseBetweenSongs
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.diagnostics.PLAYBACK_DIAG_TAG
import timber.log.Timber
import app.n_zik.android.typography
import app.n_zik.android.LocalPlayerSheetState
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.DURATION_INDICATOR_HEIGHT
import app.it.fast4x.rimusic.utils.formatAsDuration
import app.it.fast4x.rimusic.utils.pauseBetweenSongsKey
import app.it.fast4x.rimusic.utils.positionAndDurationState
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.showRemainingSongTimeKey
import app.it.fast4x.rimusic.utils.textoutlineKey
import kotlinx.coroutines.delay
import app.it.fast4x.rimusic.utils.showSkipTimeButtonsKey

/**
 * Adjust timeline based on provided values.
 *
 * @param operation of [Long] to get desired position (either [Long.plus] or [Long.minus])
 * @param valueSelector takes a value between position and [comparedValue] to ensure position isn't outside of allowed range
 * @param comparedValue either end of position, this value sets the limit for calculated position
 */
@UnstableApi
@Composable
private fun RowScope.SkipTimeButton(
    binder: PlayerServiceModern.Binder,
    // Issue #881 (gh-881), Phase 3.1: base and bound are read at TAP time — a composed value
    // can be stale when the player UI stops recomposing (screen-off track change, field logs
    // 2026-10-04); [composedPosition] is only kept for the SKIP_TAP diagnostic line.
    position: () -> Long,
    composedPosition: Long,
    operation: Long.(Long) -> Long,
    valueSelector: (Long, Long) -> Long,
    comparedValue: () -> Long,
    contentDescription: String,
    onClickLabel: String,
    onLongClickLabel: String,
    modifier: Modifier = Modifier,
    tapAdjustment: Long = 5_000L,
    doubleTapAdjustment: Long = 10_000L,
    longTapAdjustment: Long = 30_000L,
    onSeekIssued: (Long) -> Unit = {}
) {
    // Issue #881 (gh-881), Phase 3 Fix E: a guest's seek is vetoed by the guarded facade —
    // publishing the target would hold the label on a position the player never reaches.
    val ltGuestLocked = rememberListenTogetherGuestLock()
    fun seekTo( adjustment: Long ) {
        val base = position()
        val adjustedPosition = base.operation( adjustment )
        val newPosition = valueSelector( adjustedPosition, comparedValue() )
        Timber.tag( PLAYBACK_DIAG_TAG ).d(
            "SKIP_TAP adj=%d base=%d composed=%d target=%d",
            adjustedPosition - base, base, composedPosition, newPosition
        )
        // Issue #881 (gh-881), Phase 3 Fix E (review patch): while the stream is still loading
        // `duration` is `C.TIME_UNSET` (Long.MIN_VALUE) — the forward button then computes
        // `minOf(x, Long.MIN_VALUE)` = Long.MIN_VALUE, which would hold a garbage target on
        // the label (up to the 10 s release timeout) and feed it to `seekTo`. Rewind clamps
        // at 0, so only the forward tap can ever produce a negative target here.
        if ( newPosition < 0 ) return
        if ( !ltGuestLocked ) onSeekIssued( newPosition )
        binder.player.seekTo( newPosition )
    }

    Icon(
        painter = painterResource( R.drawable.play_forward ),
        tint = colorPalette().favoritesIcon,
        contentDescription = contentDescription,
        modifier = modifier.size( DURATION_INDICATOR_HEIGHT.dp )
                           .align( Alignment.CenterVertically )
                           .clip(uiRoundnessShape()).combinedClickable(
                               interactionSource = remember { MutableInteractionSource() },
                               indication = null,
                               role = Role.Button,
                               onClickLabel = onClickLabel,
                               onClick = { seekTo(tapAdjustment) },
                               onDoubleClick = { seekTo(doubleTapAdjustment) },
                               onLongClickLabel = onLongClickLabel,
                               onLongClick = { seekTo(longTapAdjustment) }
                           )
    )
}

@Composable
private fun outlineColorState(): State<Color> {
    val textOutline by rememberPreference( textoutlineKey, false )
    // Read the palette in the composable scope: `colorPalette()` is @Composable and the
    // derivedStateOf lambda below is not a composable context
    val palette = colorPalette()

    // The decision is pinned by the pure helper (spec-achromatic-ramp-luminance-cap, loopback 2,
    // VG-3): keyed on the effective tone, and the text-outline opt-out now applies in light
    // mode too (the legacy light-mode branch ignored it).
    return remember {
        derivedStateOf {
            durationOutlineColorOf( textOutline, palette )
        }
    }
}

@Composable
private fun OutlinedText( text: String, outlineColor: Color ) {
    // Main text
    BasicText(
        text = text,
        style = typography().xxs.semiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )

    // Outline
    BasicText(
        text = text,
        style = typography().xxs
                            .semiBold
                            .merge(
                                TextStyle(
                                    drawStyle = Stroke(width = 1.0f, join = StrokeJoin.Round),
                                    color = outlineColor
                                )
                            ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Remaining song time to display. While the stream is loading the player may report an
 * unknown duration (C.DURATION_UNSET = Long.MIN_VALUE, observed on Listen Together host
 * track changes); `duration - position` would overflow signed 64-bit and flash a huge
 * garbage value (2026-09-26). Returns -1 ("unknown", displayed as "--:--") when the
 * duration is not positive, otherwise the clamped remaining time.
 */
internal fun timeRemainingOf(duration: Long, position: Long): Long =
    if (duration <= 0) -1L else (duration - position).coerceAtLeast( 0 )

@UnstableApi
@Composable
fun DurationIndicator(
    binder: PlayerServiceModern.Binder,
    scrubbingPosition: Long?,
    position: Long,
    duration: Long,
    // Issue #881 (gh-881), Phase 3 Fix E: invoked with the target whenever a skip button
    // issues a seek, so the label can hold that position until the player commits it
    // (GetSeekBar publishes its pendingSeekTarget state through it).
    onSeekIssued: (Long) -> Unit = {},
    // Issue #881 (gh-881), Phase 3.1: live skip base evaluated at tap time (GetSeekBar reads
    // the pending target / player position); defaults to the composed [position].
    seekBasePosition: () -> Long = { position }
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding( horizontal = 10.dp )
                           .fillMaxWidth()
    ) {
        val showSkipTimeButtons by rememberPreference( showSkipTimeButtonsKey, true )
        if (showSkipTimeButtons) {
            SkipTimeButton(
                binder, seekBasePosition, position, Long::minus, ::maxOf, { 0L }, stringResource(R.string.rewind), stringResource(R.string.rewind_5_seconds), stringResource(R.string.rewind_30_seconds), Modifier.rotate( 180f ), onSeekIssued = onSeekIssued
            )

            Spacer( Modifier.width( 5.dp ) )
        }

        /**
         * Current implement of [rememberPreference] creates new [MutableState]
         * each time the function is called. To prevent creation of multiple instances,
         * this variable is placed in parent class and passed to each [OutlinedText].
         *
         * When it's updated, all [OutlinedText] are updated as well.
         */
        val outlineColor by outlineColorState()

        // Scrubbing position
        Box(
            modifier = Modifier.weight( 1f )
                               .height( DURATION_INDICATOR_HEIGHT.dp ),
            contentAlignment = Alignment.CenterStart
        ) {
            // Issue #881 (gh-881), Phase 3 Fix E (review patch): the held seek target arrives
            // through the `scrubbingPosition` parameter — it must be in the remember key, or
            // the label keeps showing the stale player position while the bar holds the target
            // (the captured parameter is a plain value, so only a key change re-derives it).
            val toDisplay by remember( scrubbingPosition, position ) {
                derivedStateOf { formatAsDuration( scrubbingPosition ?: position ) }
            }
            OutlinedText( toDisplay, outlineColor )
        }

        // Remaining duration
        val showRemainingSongTime by rememberPreference( showRemainingSongTimeKey, true )
        if( showRemainingSongTime ) {
            Box(
                modifier = Modifier.weight( 1f )
                                   .height( DURATION_INDICATOR_HEIGHT.dp ),
                contentAlignment = Alignment.Center
            ) {
                // The full player stays composed while hidden behind the
                // mini-player; only poll position when its content is visible
                // (0.45f = CustomBottomSheet hand-over threshold)
                val positionAndDurationState =
                    binder.player.positionAndDurationState(active = LocalPlayerSheetState.current.progress > PLAYER_SHEET_HANDOVER_PROGRESS)
                val timeRemainingState = remember {
                    derivedStateOf {
                        timeRemainingOf(
                            positionAndDurationState.value.second,
                            positionAndDurationState.value.first,
                        )
                    }
                }
                val timeRemaining by timeRemainingState
                var isPaused by remember { mutableStateOf(false) }

                val pauseBetweenSongs by rememberPreference(pauseBetweenSongsKey, PauseBetweenSongs.`0`)
                // Guest lock (spec-listen-together-guest-lock-hardening): the automatic
                // pause→delay→play runs through the guarded facade, where it would register
                // spurious guest play/pause intents (pause sets guestLocalPause, play fires a
                // requestSync the guest never caused; a backgrounded app mid-delay can leave
                // the flag stuck). In a room the host drives track transitions, so skip it.
                if(pauseBetweenSongs != PauseBetweenSongs.`0` && !rememberListenTogetherGuestLock())
                    LaunchedEffect(timeRemaining) {
                        if(timeRemaining >= 0 && timeRemaining < 500) {
                            isPaused = true
                            binder.player.pause()
                            delay(pauseBetweenSongs.asMillis)
                            binder.player.play()
                            isPaused = false
                        }
                    }

                if(isPaused) return@Box

                val toDisplay by remember {
                    derivedStateOf { if (timeRemaining < 0) "--:--" else formatAsDuration(timeRemaining) }
                }
                OutlinedText( toDisplay, outlineColor )
            }
        }

        // Song's duration
        Box(
            modifier = Modifier.weight( 1f )
                               .height( DURATION_INDICATOR_HEIGHT.dp ),
            contentAlignment = Alignment.CenterEnd
        ) {
            val toDisplay = remember( duration ) {
                if( duration <= 0 ) "--:--" else formatAsDuration( duration )
            }
            OutlinedText( toDisplay, outlineColor )
        }

        if (showSkipTimeButtons) {
            Spacer( Modifier.width( 5.dp ) )

            SkipTimeButton(
                binder, seekBasePosition, position, Long::plus, ::minOf, { binder.player.duration }, stringResource(R.string.forward), stringResource(R.string.forward_5_seconds), stringResource(R.string.forward_30_seconds), onSeekIssued = onSeekIssued
            )
        }
    }
}



