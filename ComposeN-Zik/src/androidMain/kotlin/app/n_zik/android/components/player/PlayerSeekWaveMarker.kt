package app.n_zik.android.components.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val PLAYER_SEEK_OPTICAL_PANE_SIZE = 36.dp

/** Preserves the resting waveform cursor and contracts it toward the pressed ball's interior. */
internal fun playerSeekWaveMarkerHeight(nativeHeight: Dp, thumb: PlayerSeekGlassThumb?): Dp {
    if (thumb == null) return nativeHeight
    val pressProgress = thumb.motion.pressProgress.coerceIn(0f, 1f)
    val interiorHeight = PLAYER_SEEK_OPTICAL_PANE_SIZE * thumb.motion.opticalScaleY - 4.dp
    val pressedHeight = interiorHeight.coerceIn(0.dp, nativeHeight)
    return nativeHeight + (pressedHeight - nativeHeight) * pressProgress
}
