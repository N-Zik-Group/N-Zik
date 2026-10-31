package app.n_zik.android.playback.services

/** Tracks successful effect-session announcements; callers serialize access on the player thread. */
internal class AudioEffectSessionController(
    private val sender: (opened: Boolean, sessionId: Int) -> Unit
) {
    private var announcedSessionId: Int? = null

    fun sync(sessionId: Int, shouldOpen: Boolean) {
        val desiredSessionId = sessionId.takeIf { shouldOpen && it > 0 }
        if (desiredSessionId == announcedSessionId) return

        close()
        if (desiredSessionId != null) {
            sender(true, desiredSessionId)
            announcedSessionId = desiredSessionId
        }
    }

    fun close() {
        val sessionId = announcedSessionId ?: return
        sender(false, sessionId)
        announcedSessionId = null
    }
}
