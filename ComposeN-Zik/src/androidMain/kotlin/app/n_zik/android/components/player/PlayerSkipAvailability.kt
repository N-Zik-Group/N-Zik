package app.n_zik.android.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.Player

data class PlayerSkipAvailability(val next: Boolean, val previousItem: Boolean, val previous: Boolean)

internal fun Player.skipAvailability() = PlayerSkipAvailability(
    next = isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
    previousItem = isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM),
    previous = isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS),
)

/** Track timeline, repeat and shuffle changes through the player's command events. */
@Composable
fun rememberPlayerSkipAvailability(player: Player): State<PlayerSkipAvailability> {
    val state = remember(player) { mutableStateOf(player.skipAvailability()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                state.value = player.skipAvailability()
            }
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                state.value = player.skipAvailability()
            }
        }
        player.addListener(listener)
        state.value = player.skipAvailability()
        onDispose { player.removeListener(listener) }
    }
    return state
}
