package app.n_zik.android.playback.services

/**
 * Pure (Android-free) rule behind the persistent-queue restore path in [PlayerServiceModern].
 *
 * Why: `prepare()` is what moves the player out of IDLE, and media3 shows the media
 * notification whenever the timeline is non-empty and the player is not IDLE — preparing the
 * restored queue while auto-resume is off produced a media notification with no music
 * playing (and a pre-buffer with network traffic). The restored queue is prepared
 * (pre-buffered) only when the service resumes playback on start; otherwise the player stays
 * IDLE, the queue stays visible in the app, and the first play recovers the IDLE state on
 * every surface (session commands via media3's `Util.handlePlayButtonAction`, the in-app
 * play through the `AudioUtils.fadeInEffect` guard — full rationale in [PlayerServiceModern]).
 * Plain boolean logic, unit-testable off device.
 */
object RestoredQueueRules {

    /**
     * Whether the persistent queue restored at service start must be prepared.
     *
     * @param resumePlaybackOnStart true when the "resume playback on start"
     *        setting is enabled (the restored queue is played immediately)
     * @return true only when playback resumes on start, so the player may
     *         leave IDLE right after the restore; false keeps the player
     *         IDLE (no media notification, no pre-buffer)
     */
    fun shouldPrepareRestoredQueue(resumePlaybackOnStart: Boolean): Boolean = resumePlaybackOnStart
}
