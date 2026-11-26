package app.n_zik.android.playback.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Crossfade and the Listen Together host already own the track transition. */
internal fun trackBoundaryPauseMs(configuredMs: Long, crossfadeEnabled: Boolean, guestLocked: Boolean): Long =
    if (crossfadeEnabled || guestLocked) 0 else configuredMs

/** Keeps the pending resume with the playback service, across UI and screen changes. */
internal class TrackBoundaryPause(private val scope: CoroutineScope) {
    private var pendingResume: Job? = null

    fun schedule(delayMs: Long, resume: () -> Unit) {
        cancel()
        pendingResume = scope.launch {
            delay(delayMs)
            pendingResume = null
            resume()
        }
    }

    fun cancel() {
        pendingResume?.cancel()
        pendingResume = null
    }
}
