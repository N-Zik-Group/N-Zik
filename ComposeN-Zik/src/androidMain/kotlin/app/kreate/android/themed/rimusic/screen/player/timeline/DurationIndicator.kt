package app.kreate.android.themed.rimusic.screen.player.timeline

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.PLAYER_SHEET_HANDOVER_PROGRESS

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.PauseBetweenSongs
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.typography
import app.n_zik.android.LocalPlayerSheetState
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.DURATION_INDICATOR_HEIGHT
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
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
    position: Long,
    operation: Long.(Long) -> Long,
    valueSelector: (Long, Long) -> Long,
    comparedValue: Long,
    contentDescription: String,
    onClickLabel: String,
    onLongClickLabel: String,
    modifier: Modifier = Modifier,
    tapAdjustment: Long = 5_000L,
    doubleTapAdjustment: Long = 10_000L,
    longTapAdjustment: Long = 30_000L
) {
    fun seekTo( adjustment: Long ) {
        val adjustedPosition = position.operation( adjustment )
        val newPosition = valueSelector( adjustedPosition, comparedValue )
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
    val colorPaletteMode by rememberPreference( colorPaletteModeKey, ColorPaletteMode.Dark )
    val textOutline by rememberPreference( textoutlineKey, false )
    val isDarkTheme = isSystemInDarkTheme()

    return remember {
        derivedStateOf {
            if ( colorPaletteMode == ColorPaletteMode.Light || (colorPaletteMode == ColorPaletteMode.System && !isDarkTheme) )
                Color.White.copy( 0.5f )
            else if( !textOutline )
                Color.Transparent
            else
                Color.Black
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

@UnstableApi
@Composable
fun DurationIndicator(
    binder: PlayerServiceModern.Binder,
    scrubbingPosition: Long?,
    position: Long,
    duration: Long
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
                binder, position, Long::minus, ::maxOf, 0, stringResource(R.string.rewind), stringResource(R.string.rewind_5_seconds), stringResource(R.string.rewind_30_seconds), Modifier.rotate( 180f )
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
            val toDisplay by remember( position ) {
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
                        (positionAndDurationState.value.second - positionAndDurationState.value.first).coerceAtLeast( 0 )
                    }
                }
                val timeRemaining by timeRemainingState
                var isPaused by remember { mutableStateOf(false) }

                val pauseBetweenSongs by rememberPreference(pauseBetweenSongsKey, PauseBetweenSongs.`0`)
                if(pauseBetweenSongs != PauseBetweenSongs.`0`)
                    LaunchedEffect(timeRemaining) {
                        if(timeRemaining < 500) {
                            isPaused = true
                            binder.player.pause()
                            delay(pauseBetweenSongs.asMillis)
                            binder.player.play()
                            isPaused = false
                        }
                    }

                if(isPaused) return@Box

                val toDisplay by remember {
                    derivedStateOf { formatAsDuration(timeRemaining) }
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
                binder, position, Long::plus, ::minOf, duration, stringResource(R.string.forward), stringResource(R.string.forward_5_seconds), stringResource(R.string.forward_30_seconds)
            )
        }
    }
}



