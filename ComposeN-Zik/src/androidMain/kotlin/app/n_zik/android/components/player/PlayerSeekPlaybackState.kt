package app.n_zik.android.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player

/** Observes playback callbacks independently of the timeline's position polling. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun rememberPlayerSeekIsPlaying(player: Player): Boolean {
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        // Re-read after registration in case playback changed before the effect ran.
        isPlaying = player.isPlaying
        onDispose { player.removeListener(listener) }
    }
    return isPlaying
}
