package app.n_zik.android.playback.services

/** Tracks active fade time using timestamps from one monotonic clock. */
internal class CrossfadeTimeline(startedAtMs: Long, playing: Boolean) {
    private var activeSinceMs: Long? = startedAtMs.takeIf { playing }
    private var accumulatedMs = 0L

    fun setPlaying(playing: Boolean, nowMs: Long) {
        if (playing) {
            if (activeSinceMs == null) activeSinceMs = nowMs
        } else {
            activeSinceMs?.let { accumulatedMs += (nowMs - it).coerceAtLeast(0L) }
            activeSinceMs = null
        }
    }

    fun elapsedMs(nowMs: Long): Long =
        accumulatedMs + (activeSinceMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L)
}
