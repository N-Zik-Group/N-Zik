package app.n_zik.android.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.PlayerTimelineType

/** The selected native track keeps its height while the player resolves its duration. */
@Composable
fun PlayerTimelineSlot(
    type: PlayerTimelineType,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.height(playerTimelineSlotHeight(type)), contentAlignment = Alignment.Center) {
        content()
    }
}

internal fun playerTimelineSlotHeight(type: PlayerTimelineType): Dp = when (type) {
    PlayerTimelineType.AudioWaves -> 40.dp
    PlayerTimelineType.VisualizerBar -> 50.dp
    PlayerTimelineType.PinBar, PlayerTimelineType.BodiedBar -> 15.dp
    PlayerTimelineType.Default, PlayerTimelineType.Wavy,
    PlayerTimelineType.ThinBar, PlayerTimelineType.ColoredBar -> 6.dp
}
