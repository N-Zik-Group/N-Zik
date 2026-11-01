package app.n_zik.android.playback.utils

import androidx.media3.common.Player
import app.it.fast4x.rimusic.utils.playNext
import app.it.fast4x.rimusic.utils.playPrevious

/** Check before the entire seek/prepare/resume chain, including stale notification taps. */
fun Player.playNextIfAvailable(): Boolean {
    if (!isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) return false
    playNext()
    return true
}

fun Player.playPreviousIfAvailable(): Boolean {
    if (!isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) return false
    playPrevious()
    return true
}

/** The UI Previous command can restart the current item even at the queue boundary. */
fun Player.seekPreviousIfAvailable(): Boolean {
    if (!isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)) return false
    seekToPrevious()
    return true
}
