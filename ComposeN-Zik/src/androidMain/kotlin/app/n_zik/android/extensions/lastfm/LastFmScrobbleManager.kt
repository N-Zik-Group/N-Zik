package app.n_zik.android.extensions.lastfm

import android.content.Context
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.utils.albumTitleOrDb
import app.n_zik.android.utils.artistTextOrDb
import app.n_zik.android.utils.titleOrDb
import it.fast4x.lastfm.LastFm
import it.fast4x.lastfm.models.LastFmApiException
import kotlinx.coroutines.CoroutineScope
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.min

/**
 * Last.fm scrobble behavior, read from encrypted preferences.
 * Defaults match the previously hard-coded (574) behavior.
 */
internal data class LastFmScrobbleConfig(
    val nowPlayingEnabled: Boolean = true,
    val scrobbleEnabled: Boolean = true,
    val minTrackDurationMs: Long = 30_000L,
    val scrobbleThresholdPercent: Int = 50,
    val maxScrobbleDelayMs: Long = 50_000L
)

/**
 * Scrobble manager for LastFM (Metrolist semantics, now configurable):
 * - Now Playing is sent when an eligible online track starts, if the option is enabled.
 * - The scrobble fires after min(threshold % of duration, max delay), timestamped at track
 *   start, if the option is enabled.
 * - Pausing freezes the remaining delay, resuming continues it; a new track cancels the
 *   pending job.
 * - Setting changes apply at runtime via [onConfigChanged] without recreating the manager
 *   or re-sending Now Playing.
 */
class LastFmScrobbleManager(
    private val context: Context,
    private val externalScope: CoroutineScope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val TAG = "LastFmScrobble"
        // Last.fm official docs: 9 = Invalid session key - please re-authenticate
        private const val SESSION_EXPIRED_CODE = 9

        /**
         * Scrobble delay in ms: min(threshold % of track duration, max delay).
         */
        internal fun computeScrobbleDelayMs(
            durationMs: Long,
            config: LastFmScrobbleConfig = LastFmScrobbleConfig()
        ): Long {
            if (durationMs <= 0L) return 0L
            val ratioDelayMs = durationMs * config.scrobbleThresholdPercent / 100L
            return min(ratioDelayMs, config.maxScrobbleDelayMs)
        }
    }

    private enum class Phase { Idle, Active, Paused, Done }

    // Dedicated Main scope for the session-expired toast: the service's prefs
    // listener destroys this manager as soon as the session key is removed,
//
    // which cancels externalScope before a toast launched on it would run.
    private val toastScope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)

    @Volatile
    private var config: LastFmScrobbleConfig = LastFmScrobbleConfig()
    @Volatile
    private var trackJob: Job? = null
    @Volatile
    private var jobGeneration = 0L
    @Volatile
    private var phase = Phase.Idle
    @Volatile
    private var trackedMediaId: String? = null
    @Volatile
    private var trackedItem: MediaItem? = null
    @Volatile
    private var trackedDurationMs: Long = 0L
    @Volatile
    private var remainingMs: Long = 0L
    @Volatile
    private var timerStartedAt: Long = 0L
    @Volatile
    private var trackStartedAtEpochSeconds: Long = 0L
    @Volatile
    private var isCurrentlyPlaying = false
    @Volatile
    private var destroyed = false

    private var cachedArtist: String? = null
    private var cachedTitle: String? = null
    private var cachedAlbum: String? = null

    init {
        config = readConfig()
    }

    /**
     * Reads the current scrobble settings from encrypted preferences.
     */
    private fun readConfig(): LastFmScrobbleConfig {
        val prefs = context.encryptedPreferences
        return LastFmScrobbleConfig(
            nowPlayingEnabled = prefs.getBoolean(isLastfmNowPlayingEnabledKey, true),
            scrobbleEnabled = prefs.getBoolean(isLastfmScrobbleEnabledKey, true),
            minTrackDurationMs = prefs.getInt(lastfmMinTrackDurationSecondsKey, 30).toLong() * 1000L,
            scrobbleThresholdPercent = prefs.getInt(lastfmScrobbleThresholdPercentKey, 50),
            maxScrobbleDelayMs = prefs.getInt(lastfmMaxScrobbleDelaySecondsKey, 50).toLong() * 1000L
        )
    }

    /**
     * Called on media item transition (new track or queue cleared).
     */
    @Synchronized
    fun onTrackTransition(mediaItem: MediaItem?, duration: Long) {
        if (destroyed) return
        cancelTrackJob()
        resetState()
        trackedMediaId = mediaItem?.mediaId
        trackedItem = mediaItem
        trackedDurationMs = duration
        if (mediaItem == null || mediaItem.isLocal) return
        if (duration <= config.minTrackDurationMs) return
        startTrack(mediaItem, duration, resume = false)
    }

    /**
     * Called on play/pause state changes.
     */
    @Synchronized
    fun onPlayingStateChanged(isPlaying: Boolean, mediaItem: MediaItem?, duration: Long) {
        if (destroyed) return
        if (mediaItem == null) {
            cancelTrackJob()
            resetState()
            trackedMediaId = null
            trackedDurationMs = 0L
            return
        }
        if (mediaItem.mediaId != trackedMediaId) return

        isCurrentlyPlaying = isPlaying

        if (isPlaying) {
            when (phase) {
                Phase.Idle -> {
                    // Duration was unknown at transition time; start now that it is known
                    if (mediaItem.isLocal) return
                    if (duration > config.minTrackDurationMs) startTrack(mediaItem, duration, resume = false)
                }
                Phase.Active -> {
                    // Same item still playing: no-op, ExoPlayer can fire duplicate callbacks
                }
                Phase.Paused -> {
                    if (remainingMs > 0L) {
                        startTrack(mediaItem, duration, resume = true)
                    } else {
                        phase = Phase.Done
                    }
                }
                Phase.Done -> {
                    // No pending scrobble (scrobbled, disabled, or track ineligible)
                }
            }
        } else {
            if (phase == Phase.Active) pauseTimer()
        }
    }

    /**
     * Re-reads the settings after a sub-option change. If a scrobble is
     * pending and the track is still eligible, the pending timer is re-armed
     * from now (Active) or recomputed (Paused) without re-sending Now Playing
     * or recreating the manager. If the track is no longer eligible or
     * scrobbling is disabled, the pending scrobble is cancelled. An Idle
     * track that became eligible while playing is started: Now Playing is
     * sent when enabled, even with scrobbling disabled.
     */
    @Synchronized
    fun onConfigChanged() {
        if (destroyed) return
        config = readConfig()
        if (trackedMediaId == null || trackedDurationMs <= config.minTrackDurationMs) {
            // Track no longer eligible: cancel the pending scrobble, do not re-arm
            cancelTrackJob()
            remainingMs = 0L
            timerStartedAt = 0L
            trackStartedAtEpochSeconds = 0L
            if (phase == Phase.Active || phase == Phase.Paused) phase = Phase.Done
            return
        }
        if (!config.scrobbleEnabled && (phase == Phase.Active || phase == Phase.Paused)) {
            // Scrobbling disabled while a timer is pending: cancel it.
            // Idle/Done fall through: an eligible Idle track may still send
//
            // Now Playing (startTrack sends NP only, then stops).
            cancelTrackJob()
            remainingMs = 0L
            timerStartedAt = 0L
            trackStartedAtEpochSeconds = 0L
            phase = Phase.Done
            return
        }
        when (phase) {
            Phase.Active -> {
                cancelTrackJob()
                remainingMs = computeScrobbleDelayMs(trackedDurationMs, config)
                timerStartedAt = clock()
                // resume = true: re-arm the timer only, never re-send Now Playing
                val item = trackedItem
                if (item != null) launchTrackJob(item, resume = true)
            }
            Phase.Paused -> {
                // Timer frozen: recompute the remaining delay only
                remainingMs = computeScrobbleDelayMs(trackedDurationMs, config)
            }
            Phase.Idle -> {
                // Track was not started (below the previous minimum, or unknown duration):
                // start it now that it is eligible and playing.
                val item = trackedItem
                if (isCurrentlyPlaying && item != null && !item.isLocal) {
                    startTrack(item, trackedDurationMs, resume = false)
                }
            }
            Phase.Done -> {
                // No pending scrobble (already scrobbled, or scrobbling disabled at track start)
            }
        }
    }

    /**
     * Cancels the pending job and releases the scope.
     * The dedicated toastScope is intentionally NOT cancelled: a session
     * expired in flight must still be able to toast the re-login prompt.
     */
    @Synchronized
    fun destroy() {
        if (destroyed) return
        destroyed = true
        cancelTrackJob()
        resetState()
        trackedMediaId = null
        externalScope.cancel()
    }

    private fun resetState() {
        phase = Phase.Idle
        remainingMs = 0L
        timerStartedAt = 0L
        trackStartedAtEpochSeconds = 0L
        isCurrentlyPlaying = false
        trackedItem = null
        trackedDurationMs = 0L
        cachedArtist = null
        cachedTitle = null
        cachedAlbum = null
    }

    private fun startTrack(mediaItem: MediaItem, duration: Long, resume: Boolean) {
        // On resume, player.duration can be C.TIME_UNSET (-1) right after a
        // recovery seek while the stream is still loading: keep the known duration.
        if (duration > 0L) trackedDurationMs = duration
        if (!resume) {
            trackStartedAtEpochSeconds = clock() / 1000L
            remainingMs = computeScrobbleDelayMs(duration, config)
            // Start the clock before the job launches so a pause during the
            // Now Playing call still deducts elapsed time in pauseTimer()
            timerStartedAt = clock()
        }
        phase = Phase.Active
        launchTrackJob(mediaItem, resume)
    }

    /** Cancels the pending job and bumps the generation counter so a stale job woken up by a cancellation still observes itself as outdated and aborts before sending anything. */
    private fun cancelTrackJob() {
        jobGeneration++
        trackJob?.cancel()
        trackJob = null
    }

    private fun launchTrackJob(mediaItem: MediaItem, resume: Boolean) {
        val mediaId = mediaItem.mediaId
        jobGeneration++
        val gen = jobGeneration
        trackJob = externalScope.launch {
            val artist = cachedArtist ?: mediaItem.artistTextOrDb()
            val title = cachedTitle ?: mediaItem.titleOrDb()
            val album = cachedAlbum ?: mediaItem.albumTitleOrDb()
            if (artist.isBlank() || title.isBlank()) {
                // Empty metadata (no metadata + no DB row): nothing to send
                phase = Phase.Done
                return@launch
            }
            cachedArtist = artist
            cachedTitle = title
            cachedAlbum = album
            if (gen != jobGeneration || mediaId != trackedMediaId || destroyed) return@launch

            if (!resume && config.nowPlayingEnabled) {
                LastFm.updateNowPlaying(artist, title, album, durationSeconds()).onFailure { e ->
                    Timber.tag(TAG).w(e, "Now Playing failed for '$title' by '$artist'")
                    handleFailure(e)
                }
                if (gen != jobGeneration || mediaId != trackedMediaId || destroyed) return@launch
            }

            if (!config.scrobbleEnabled) {
                // Scrobbling disabled: no timer, no scrobble
                phase = Phase.Done
                return@launch
            }

            if (resume) timerStartedAt = clock()
            delay(remainingMs)
            if (gen != jobGeneration || mediaId != trackedMediaId || destroyed) return@launch
            phase = Phase.Done
            sendScrobble(artist, title, album)
        }
    }

    private fun pauseTimer() {
        cancelTrackJob()
        if (timerStartedAt > 0L) {
            remainingMs -= clock() - timerStartedAt
            if (remainingMs < 0L) remainingMs = 0L
            timerStartedAt = 0L
        }
        phase = Phase.Paused
    }

    private fun sendScrobble(artist: String, title: String, album: String?) {
        externalScope.launch {
            LastFm.scrobble(artist, title, trackStartedAtEpochSeconds, album, durationSeconds())
                .onSuccess {
                    Timber.tag(TAG).d("Scrobbled '$title' by '$artist'")
                }
                .onFailure { e ->
                    Timber.tag(TAG).e(e, "Scrobble failed for '$title' by '$artist'")
                    handleFailure(e)
                }
        }
    }

    /**
     * Track duration in seconds, 0 when unknown (never sent to the API).
     */
    private fun durationSeconds(): Long =
        if (trackedDurationMs > 0L) trackedDurationMs / 1000L else 0L

    private fun handleFailure(e: Throwable) {
        if (e is LastFmApiException && e.errorCode == SESSION_EXPIRED_CODE) {
            onSessionExpired()
        }
    }

    private fun onSessionExpired() {
        runCatching {
            context.encryptedPreferences.edit {
                remove(lastfmSessionKey)
                remove(lastfmUsernameKey)
            }
            LastFm.sessionKey = null
        }.onFailure {
            Timber.tag(TAG).e(it, "Failed to clear expired LastFM session")
        }
        toastScope.launch {
            Toaster.e(R.string.lastfm_session_expired)
        }
    }
}
