package app.n_zik.android.playback.services

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Owns a temporary playback pause for the service that changes the preferred output. */
internal class AudioOutputSwitch(private val scope: CoroutineScope, private val player: () -> Player) {
    private var pendingResume: Job? = null
    private var pendingTrack: Pair<Int, String>? = null

    fun apply(changeOutput: () -> Boolean): Boolean {
        val target = player()
        val track = target.currentMediaItem?.let { target.currentMediaItemIndex to it.mediaId }
        if (!changeOutput()) return false

        val shouldResume = track != null && (target.playWhenReady || pendingTrack == track)
        pendingResume?.cancel()
        pendingResume = null
        pendingTrack = null
        if (shouldResume) {
            pendingTrack = track
            if (target.playWhenReady) target.pause()
            pendingResume = scope.launch {
                delay(1_000)
                pendingTrack = null
                pendingResume = null
                val current = player()
                val currentTrack = current.currentMediaItem?.let { current.currentMediaItemIndex to it.mediaId }
                if (currentTrack == track && !current.playWhenReady) current.play()
            }
        }
        return true
    }
}
