package app.n_zik.android.components.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.PlayerTimelineType

internal val PLAYER_SEEK_OPTICAL_PANE_SIZE = 36.dp

/** The optical pane stays centered on the native waveform cursor without resizing it. */
internal fun playerSeekWaveMarkerHeight(
    nativeHeight: Dp,
    @Suppress("UNUSED_PARAMETER") thumb: PlayerSeekGlassThumb?,
): Dp = nativeHeight

/** The live visualizer has a taller amplitude field, but shares the audio waveform's resting cursor. */
internal fun playerSeekVisualizerMarkerHeight(nativeHeight: Dp): Dp =
    minOf(nativeHeight, playerTimelineSlotHeight(PlayerTimelineType.AudioWaves))
