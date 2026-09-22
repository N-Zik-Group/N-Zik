package app.n_zik.android.extensions.discord

import android.content.Context
import androidx.media3.common.MediaItem
import app.n_zik.android.core.network.utils.isNetworkAvailable
import app.n_zik.android.R
import app.n_zik.android.utils.albumTitleOrDb
import app.n_zik.android.utils.artistTextOrDb
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.utils.isExplicit
import app.kreate.android.me.knighthat.utils.Toaster
import com.metrolist.music.discordrpc.DiscordRpc
import com.metrolist.music.discordrpc.DiscordRpcConnection
import com.metrolist.music.discordrpc.entities.Timestamps
import com.metrolist.music.discordrpc.ActivityType
import kotlinx.coroutines.CoroutineScope
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber
import app.n_zik.android.core.network.client.NetworkClientFactory
import java.io.IOException
import com.metrolist.music.discordrpc.SuperProperties
import android.os.Build
import java.util.Locale


class DiscordPresenceManager(
    private val context: Context,
    private val getToken: () -> String?,
    private val getAdvancedSettings: () -> DiscordAdvancedSettings = { DiscordAdvancedSettings.read(context) },
    private val externalScope: CoroutineScope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA),
    private val connectionFactory: (String) -> DiscordRpcConnection = { defaultConnection(it) },
    private val tokenValidator: (suspend (String) -> Boolean?)? = null
) {
    companion object {
        /**
         * Debounce delay for presence updates.
         * When the user skips songs rapidly, each skip resets this timer.
         * Only after 5 seconds of stability does the update actually fire.
         * This prevents RPC spam, wrong-song flashes, and false "paused" states.
         */
        private const val DEBOUNCE_DELAY_MS = 5000L

        /** Item 8: refresh tick while playing (animated Discord progress bar + network re-arm). */
        private const val REFRESH_INTERVAL_MS = 5000L

        /** Item 7: 60 s of pause with no event → clear the activity (connection kept). */
        private const val PAUSE_CLEAR_DELAY_MS = 60_000L

        /** Item 7: 10 min with no event → close the RPC connection entirely. */
        private const val IDLE_CLOSE_DELAY_MS = 600_000L

        /** Drift tolerance for the cached start/end timestamps. */
        private const val DRIFT_TOLERANCE_MS = 2500L

        /** Default RPC connection (production); tests inject their own factory. */
        internal fun defaultConnection(token: String): DiscordRpcConnection = DiscordRpcConnection(
            token = token,
            os = "Android",
            browser = "Discord Android",
            device = Build.DEVICE,
            userAgent = SuperProperties.userAgent,
            superPropertiesBase64 = SuperProperties.superPropertiesBase64
        )
    }

    private val tag = "DiscordPresence"
    private var rpc: DiscordRpcConnection? = null
    private var lastToken: String? = null
    private var lastMediaItem: MediaItem? = null
    private var lastPosition: Long = 0L
    private var lastDuration: Long = 0L
    private var lastPlaybackSpeed: Float = 1f
    private var lastIsPlaying = false
    private var lastGetCurrentPosition: (() -> Long)? = null
    private var lastIsPlayingProvider: (() -> Boolean)? = null
    private var isStopped = false
    private val discordScope = externalScope
    private var refreshJob: Job? = null
    private var debounceJob: Job? = null
    private var reconnectWatchJob: Job? = null
    private var terminalWatchJob: Job? = null
    private var pauseClearJob: Job? = null
    private var idleCloseJob: Job? = null

    /**
     * Item 7: the idle 10-min timer closed the connection, which is terminal inside the
     * module (closed = true). The next event must create a fresh DiscordRpcConnection —
     * same pattern as the token change.
     */
    private var connectionNeedsRecreation = false

    // Lazy: the OkHttp client is only needed by validateToken. Building it eagerly in
    // the constructor makes JVM unit tests (no Robolectric) hit android.util.Log
    // "not mocked" during OkHttp platform detection.
    private val client by lazy { NetworkClientFactory.getClientWithTimeout(10L, 10L) }

    /**
     * Watches the gateway's reconnection budget: once it gives up after the maximum
     * number of automatic retries, the RPC stays offline (a manual reconnect or a new
     * token/media event re-creates the connection). Surface that to the user.
     */
    private fun watchReconnectAbandonment(connection: DiscordRpcConnection) {
        reconnectWatchJob?.cancel()
        reconnectWatchJob = discordScope.launch {
            connection.reconnectAbandoned.collect { abandoned ->
                if (abandoned) {
                    Timber.tag(tag).w("Discord RPC gave up reconnecting (max attempts reached)")
                    // Item 9: durable error, not only a toast (settings banner).
                    DiscordRpcErrorState.set(DiscordRpcError.RECONNECT_FAILED)
                    withContext(NzikDispatchers.UI) {
                        Toaster.e(R.string.discord_rpc_reconnect_failed)
                    }
                }
            }
        }
    }

    /**
     * Item 9: watches the gateway's terminal close code (4004 = invalid token). The
     * gateway does not reconnect after a 4004, so the error is surfaced durably for
     * the settings banner (the toast is not enough: it disappears in seconds).
     */
    private fun watchTerminalClose(connection: DiscordRpcConnection) {
        terminalWatchJob?.cancel()
        terminalWatchJob = discordScope.launch {
            connection.terminalCloseCode.collect { code ->
                if (code == 4004) {
                    Timber.tag(tag).e("Gateway closed with 4004 (invalid token) — surfacing durable error")
                    DiscordRpcErrorState.set(DiscordRpcError.INVALID_TOKEN)
                } else {
                    // connect() resets the flow to null (a fresh attempt) — the moment the
                    // terminal error no longer applies (item 9: clear on the new attempt /
                    // session established). A failed retry re-sets it on the next 4004.
                    DiscordRpcErrorState.clear()
                }
            }
        }
    }

    /**
     * Item 9: clears the durable error when a fresh connection is created for a new
     * token (or after the idle close) — a (re)established session is the moment the
     * banner can safely disappear.
     */
    private fun onConnectionRecreated() {
        DiscordRpcErrorState.clear()
    }

    private fun getSmallImageUrl(): String {
        return "https://raw.githubusercontent.com/N-Zik-Group/N-Zik/main/assets/discord/fallback_app.png?v=2"
    }

    private fun getLargeImageFallback(): String {
        return "https://raw.githubusercontent.com/N-Zik-Group/N-Zik/main/assets/discord/fallback_album.png?v=2"
    }

    /**
     * Validate the token
     */
    internal suspend fun validateToken(token: String): Boolean? = withContext(NzikDispatchers.DATA) {
        if (!context.isNetworkAvailable) return@withContext null
        val request = Request.Builder()
            .url("https://discord.com/api/v9/users/@me")
            .header("Authorization", token)
            .get()
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }.getOrElse { exception ->
            if (exception.message?.contains("429") == true || exception.message?.contains("Too Many Requests") == true) {
                Timber.tag(tag).d("Rate limited by Discord API during token validation")
                null // Treat as network error to retry later
            } else {
                Timber.tag(tag).e(exception, "Error validating token: ${exception.message}")
                if (exception is IOException) {
                    null
                } else {
                    false
                }
            }
        }
    }

    /**
     * Entry point for every player event (media change, play/pause, seek/skip).
     *
     * [playbackSpeed] is the effective playback speed (item 5): the timestamps are
     * adjusted by it and the details carry a " [1.50x]"-style suffix when it is not 1.0.
     * [getCurrentPosition] / [isPlayingProvider] are live providers (item 8): the
     * refresh tick reads them every ~5 s so the Discord progress bar animates.
     */
    fun onPlayingStateChanged(
        mediaItem: MediaItem?,
        isPlaying: Boolean,
        position: Long = 0L,
        duration: Long = 0L,
        now: Long = System.currentTimeMillis(),
        playbackSpeed: Float = 1f,
        getCurrentPosition: (() -> Long)? = null,
        isPlayingProvider: (() -> Boolean)? = null
    ) {
        if (isStopped) return

        // Update the player state BEFORE any conditional return below: a transient
        // token/network condition must never leave lastMediaItem stale, otherwise later
        // events (pause clear, re-sync) would act on an outdated player state.
        lastMediaItem = mediaItem
        lastPosition = position
        lastDuration = duration
        lastPlaybackSpeed = speedOrOne(playbackSpeed)
        lastIsPlaying = isPlaying
        lastGetCurrentPosition = getCurrentPosition
        lastIsPlayingProvider = isPlayingProvider

        // Item 8: the refresh tick keeps running even while the network is down — each
        // tick re-checks isNetworkAvailable and re-arms the send on return (state is
        // kept in lastMediaItem/lastPosition). It only runs while actively playing;
        // a pause or onStop stops it.
        if (mediaItem != null && isPlaying) {
            startRefreshLoop()
        } else {
            stopRefreshLoop()
        }

        val token = getToken() ?: return
        if (token.isEmpty()) return

        if (!context.isNetworkAvailable) {
            return
        }

        if (token != lastToken || connectionNeedsRecreation) {
            connectionNeedsRecreation = false
            rpc?.closeDirect()
            val connection = connectionFactory(token)
            rpc = connection
            lastToken = token
            // Item 10: a new account must never inherit the previous account's uploaded
            // asset mappings (fork addition — the cache is process-wide).
            connection.clearArtworkCache()
            watchReconnectAbandonment(connection)
            watchTerminalClose(connection)
            onConnectionRecreated()
        }

        if (mediaItem == null) {
            // No media item = no music at all → clear the activity.
            debounceJob?.cancel()
            discordScope.launch { rpc?.clearActivity() }
            // Item 7: any event re-arms both inactivity timers.
            armInactivityTimers()
            return
        }

        // Item 7: any media event re-arms both inactivity timers (60 s pause clear,
        // 10 min idle close).
        armInactivityTimers()

        // Cancel any pending debounced update — a new event supersedes it.
        debounceJob?.cancel()

        // Debounce: wait DEBOUNCE_DELAY_MS before actually sending the presence update.
        // If another event arrives before the delay elapses, this job is cancelled
        // and a new one starts. This prevents RPC spam during rapid skipping.
        debounceJob = discordScope.launch {
            delay(DEBOUNCE_DELAY_MS)
            if (isStopped) return@launch
            // Media item exists → always show music status (playing or paused)
            if (isPlaying) {
                sendPlayingPresence(mediaItem, position, duration, lastPlaybackSpeed)
            } else {
                sendPausedPresence(duration, now, position)
            }
        }
    }

    private var cachedPausedTimestamp: Long = 0L

    /**
     * Send the "Paused" presence with the frozen time.
     * Item 6: in advanced mode the details line is rendered from the dedicated pause
     * template (default: "⏸︎ Paused: {song.name}" = current behavior).
     */
    private fun sendPausedPresence(duration: Long, now: Long, pausedPosition: Long) {
        if (isStopped) return
        val mediaItem = lastMediaItem ?: return
        var frozenTimestamp = now - pausedPosition

        val mediaId = mediaItem.mediaId
        if (cachedMediaId == mediaId && wasPaused) {
            // Keep the previous frozen timestamp to avoid UI resets
            frozenTimestamp = cachedPausedTimestamp
        } else {
            cachedMediaId = mediaId
            cachedPausedTimestamp = frozenTimestamp
            wasPaused = true
        }

        val settings = getAdvancedSettings()
        // PW-3: the pause presence is optional — when disabled, no paused update is
        // sent (the last presence stays as-is; the 60 s pause-clear timer still applies).
        if (!settings.pausePresenceEnabled) {
            Timber.tag(tag).d("Pause presence disabled — skipping the paused update")
            return
        }
        val info = mediaInfo(mediaItem)
        // Name/state/buttons follow the current mode; only the details line is
        // templatized for the paused state (the frozen progress bar is kept).
        val str = discordStrings()
        val content = DiscordActivityBuilder.buildForPlaying(info, settings, str)
            .copy(details = DiscordActivityBuilder.buildPausedLine(info, settings, str))

        discordScope.launch {
            if (isStopped) return@launch
            sendActivity(
                content = content,
                start = frozenTimestamp,
                end = frozenTimestamp,
                mediaItem = mediaItem,
                settings = settings
            )
        }
    }

    /**
     * Send a custom discord activity.
     *
     * Item 6: [content] is pre-rendered (normal identity or advanced templates); the
     * selected activity type is resolved here, the status is fixed to "online"
     * (the user-status selector was removed) and `since = 0` is carried by every
     * presence update (Discord manages the "online since" itself — we never reset it).
     */
    private suspend fun sendActivity(
        content: DiscordPresenceContent,
        start: Long,
        end: Long,
        mediaItem: MediaItem?,
        settings: DiscordAdvancedSettings
    ) {
        if (isStopped) return
        val token = getToken() ?: return
        if (token.isEmpty()) return

        if (token != lastToken || connectionNeedsRecreation) {
            val validate = tokenValidator ?: { validateToken(it) }
            when (validate(token)) {
                false -> {
                    Timber.tag(tag).e("Invalid token, stopping presence updates")
                    // Item 9: durable error (settings banner), not only a toast.
                    DiscordRpcErrorState.set(DiscordRpcError.INVALID_TOKEN)
                    withContext(NzikDispatchers.UI) {
                        Toaster.e(R.string.discord_token_text_invalid)
                    }
                    return
                }
                null -> {
                    Timber.tag(tag).w("Network error while updating presence, skipping.")
                    return
                }
                true -> { /* Token is valid, continue */ }
            }

            connectionNeedsRecreation = false
            rpc?.closeDirect()
            val connection = connectionFactory(token)
            rpc = connection
            lastToken = token
            connection.clearArtworkCache()
            watchReconnectAbandonment(connection)
            watchTerminalClose(connection)
            onConnectionRecreated()
        }

        val rawUri = mediaItem?.mediaMetadata?.artworkUri?.toString()
        // Per-section visibility (advanced mode only — normal mode keeps its frozen
        // identity). A disabled section is sent as null: the module omits the field
        // from the JSON entirely (no empty line, no asset, no progress bar).
        val advanced = settings.advancedMode
        val largeImageUrl = if (advanced && !settings.showArtwork) {
            null
        } else if (rawUri != null && rawUri.startsWith("http")) {
            rawUri
        } else {
            getLargeImageFallback()
        }
        val smallImageUrl = if (advanced && !settings.showSmallImage) null else getSmallImageUrl()
        // Image tooltips: advanced mode can override each one with a template (empty =
        // the built-in default); `{app.version}` resolves to the app version name.
        val info = mediaItem?.let { mediaInfo(it) } ?: DiscordMediaInfo("", "", null, "")
        val str = discordStrings()
        val renderImageText: (String) -> String = { template ->
            DiscordTemplateRenderer.render(
                template, info.title, info.artist, info.albumName, info.songId, str.unknownAlbum, str.appVersion,
            )
        }
        val largeTextValue = largeImageUrl?.let {
            val defaultText = if (content.state.isNotBlank()) "${content.details} - ${content.state}" else content.details
            val text = if (advanced && settings.largeImageTextTemplate.isNotBlank()) {
                renderImageText(settings.largeImageTextTemplate)
            } else {
                // Built-in default: the album value when the app knows it (metadata or DB),
                // otherwise the previous "details - state" combination.
                info.albumName?.takeIf { it.isNotBlank() } ?: defaultText
            }
            text.takeIf { it.isNotBlank() }
        }
        val smallTextValue = smallImageUrl?.let {
            val text = if (advanced && settings.smallImageTextTemplate.isNotBlank()) {
                renderImageText(settings.smallImageTextTemplate)
            } else {
                "v${str.appVersion}"
            }
            text.takeIf { it.isNotBlank() }
        }
        val timestampsValue = if (advanced && !settings.showTimestamps) {
            null
        } else {
            Timestamps(
                start = start,
                end = if (end > 0L) end else null
            )
        }

        runCatching {
            rpc?.setActivity(
                applicationId = DiscordRpc.APPLICATION_ID,
                name = content.name,
                details = content.details.takeIf { it.isNotBlank() },
                state = content.state.takeIf { it.isNotBlank() },
                type = resolveActivityType(settings),
                timestamps = timestampsValue,
                largeImage = largeImageUrl,
                smallImage = smallImageUrl,
                largeText = largeTextValue,
                smallText = smallTextValue,
                buttons = content.buttons,
                status = DISCORD_STATUS_ONLINE,
                since = 0L
            )
        }.onFailure {
            Timber.tag(tag).w("Error setting Discord activity: ${it.message}")
        }
    }

    /**
     * Item 6: selected activity type (default = listening = current behavior).
     */
    private fun resolveActivityType(settings: DiscordAdvancedSettings): ActivityType = when (settings.activityType) {
        0 -> ActivityType.PLAYING
        3 -> ActivityType.WATCHING
        5 -> ActivityType.COMPETING
        else -> ActivityType.LISTENING
    }

    private fun speedOrOne(speed: Float): Float = if (speed > 0f) speed else 1f

    /**
     * Item 6: re-syncs the live presence when an advanced setting changes (upstream
     * parity notifySettingsChanged) — the module's 500 ms limit prevents spam.
     */
    fun onAdvancedSettingsChanged() {
        if (isStopped) return
        Timber.tag(tag).d("Advanced settings changed — re-syncing presence")
        armInactivityTimers()
        lastMediaItem?.let { media ->
            if (lastIsPlaying) {
                discordScope.launch {
                    sendPlayingPresence(media, lastPosition, lastDuration, lastPlaybackSpeed)
                }
            } else {
                sendPausedPresence(lastDuration, System.currentTimeMillis(), lastPosition)
            }
        }
    }

    /**
     * Item 5: re-sends the playing presence after a playback speed change so the
     * timestamps and the " [1.50x]" suffix reflect the new speed. The service caller
     * guarantees ~1 s delay + playWhenReady && STATE_READY (upstream parity MS L2884-2897).
     */
    fun onPlaybackSpeedChanged(speed: Float) {
        if (isStopped) return
        lastPlaybackSpeed = speedOrOne(speed)
        lastMediaItem?.let { media ->
            if (lastIsPlaying && context.isNetworkAvailable) {
                Timber.tag(tag).d("Re-sending presence after speed change (${lastPlaybackSpeed}x)")
                sendPlayingPresence(media, lastGetCurrentPosition?.invoke() ?: lastPosition, lastDuration, lastPlaybackSpeed)
            }
        }
    }

    /**
     * Close the discord presence (STOP)
     */
    fun onStop() {
        isStopped = true
        debounceJob?.cancel()
        refreshJob?.cancel()
        reconnectWatchJob?.cancel()
        terminalWatchJob?.cancel()
        pauseClearJob?.cancel()
        idleCloseJob?.cancel()
        rpc?.closeDirect()
        discordScope.cancel()
    }

    /**
     * Item 8: while the media is playing, re-sends the playing presence every ~5 s
     * through the live providers, so Discord's progress bar animates. Each tick
     * re-checks isNetworkAvailable (a down network just skips the tick; the next tick,
     * within 5 s, re-arms the send). Stopped by a pause or onStop.
     */
    private fun startRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = discordScope.launch {
            while (isActive && !isStopped) {
                delay(REFRESH_INTERVAL_MS)
                if (isStopped) break
                // A pause (or media removed) stops the loop — the presence is frozen.
                // Callers must pass the live providers: without isPlayingProvider the
                // tick cannot tell the playback state, so the loop stops here (warned).
                val isPlayingProvider = lastIsPlayingProvider
                if (isPlayingProvider == null) {
                    Timber.tag(tag).w("Refresh tick: no live isPlaying provider — stopping the refresh loop")
                    break
                }
                if (!isPlayingProvider.invoke()) break
                val media = lastMediaItem ?: break
                lastPosition = lastGetCurrentPosition?.invoke() ?: lastPosition
                // Activity keeps the connection alive: re-arm the 10-min idle close.
                armIdleCloseTimer()
                if (!context.isNetworkAvailable) {
                    Timber.tag(tag).w("Network unavailable — refresh tick skipped, re-armed on the next tick")
                    continue
                }
                Timber.tag(tag).d("Refresh tick: re-sending playing presence (position=${lastPosition}ms, speed=${lastPlaybackSpeed})")
                sendPlayingPresence(media, lastPosition, lastDuration, lastPlaybackSpeed)
            }
        }
    }

    private fun stopRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /**
     * Item 7: re-arms both inactivity timers from any event.
     */
    private fun armInactivityTimers() {
        if (isStopped) return
        armPauseClearTimer()
        armIdleCloseTimer()
    }

    /**
     * Item 7: 60 s of pause with no event → clear the activity (the connection is
     * kept — a resume is cheap). Only the stale playing presence left after a pause
     * with the pause presence DISABLED is clearable: with the pause presence enabled
     * the paused presence IS the active state (stays as long as the player is
     * paused), and the auto-clear itself is an advanced option (default on).
     */
    private fun armPauseClearTimer() {
        pauseClearJob?.cancel()
        pauseClearJob = discordScope.launch {
            delay(PAUSE_CLEAR_DELAY_MS)
            if (isStopped) return@launch
            if (lastMediaItem != null && !lastIsPlaying) {
                val settings = getAdvancedSettings()
                if (settings.pausePresenceEnabled || !settings.pauseClearEnabled) {
                    Timber.tag(tag).i("60 s paused — the presence stays (pause presence on / auto-clear off)")
                } else {
                    Timber.tag(tag).i("60 s paused with pause presence disabled — clearing activity (connection kept)")
                    rpc?.clearActivity()
                }
            }
        }
    }

    /**
     * Item 7: 10 min with no event → close the RPC connection entirely. The module
     * close is terminal (closed = true): the next event re-creates the connection via
     * [connectionNeedsRecreation]. Exception: an active paused presence (pause
     * presence enabled) keeps the activity on Discord, so the timer re-arms instead
     * of closing — the connection stays open with the paused state.
     */
    private fun armIdleCloseTimer() {
        idleCloseJob?.cancel()
        idleCloseJob = discordScope.launch {
            delay(IDLE_CLOSE_DELAY_MS)
            if (isStopped) return@launch
            val settings = getAdvancedSettings()
            // The idle close is an advanced option (default on): disabled → the
            // connection stays open (re-arm keeps watching the state / toggles).
            if (!settings.idleCloseEnabled) {
                armIdleCloseTimer()
                return@launch
            }
            // An active paused presence (enabled) keeps the activity on Discord — the
            // connection must stay open with it: re-arm instead of closing.
            if (lastMediaItem != null && !lastIsPlaying && settings.pausePresenceEnabled) {
                Timber.tag(tag).i("10 min idle with an active pause presence — re-arming the idle close")
                armIdleCloseTimer()
                return@launch
            }
            Timber.tag(tag).i("10 min with no event — closing the RPC connection (idle)")
            rpc?.closeDirect()
            connectionNeedsRecreation = true
        }
    }

    /**
     * Get the version name of the app
     */
    fun getVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private var cachedMediaId: String? = null
    private var cachedStartTime: Long = 0L
    private var cachedEndTime: Long = 0L
    private var cachedSpeed: Float = -1f
    private var wasPaused: Boolean = false

    /**
     * Item 5 (upstream parity MS L3604-3610): both timestamp bounds adjusted by the
     * playback speed — start = now - position/speed, end = now + (duration-position)/speed.
     * The details carry a " [%.2fx]" suffix when the speed is not 1.0. The drift cache
     * is invalidated by a speed change (or a song change / resume).
     */
    private fun sendPlayingPresence(mediaItem: MediaItem, position: Long, duration: Long, speed: Float) {
        val safeSpeed = speedOrOne(speed)
        val currentTime = System.currentTimeMillis()
        val adjustedPosition = (position / safeSpeed).toLong()
        var calculatedStartTime = currentTime - adjustedPosition
        val adjustedRemaining = if (duration > 0L) {
            ((duration - position) / safeSpeed).toLong().coerceAtLeast(0L)
        } else {
            null
        }
        var end = adjustedRemaining?.let { currentTime + it } ?: 0L

        val mediaId = mediaItem.mediaId
        if (cachedMediaId == mediaId && !wasPaused && cachedSpeed == safeSpeed) {
            // Allow up to 2.5 seconds of drift to account for execution delays
            if (kotlin.math.abs(calculatedStartTime - cachedStartTime) < DRIFT_TOLERANCE_MS) {
                calculatedStartTime = cachedStartTime
                end = cachedEndTime
            } else {
                cachedStartTime = calculatedStartTime
                cachedEndTime = end
            }
        } else {
            cachedMediaId = mediaId
            cachedStartTime = calculatedStartTime
            cachedEndTime = end
            cachedSpeed = safeSpeed
            wasPaused = false
        }

        val cleanTitle = cleanPrefix(mediaItem.mediaMetadata.title?.toString() ?: "").takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_title)
        // The explicit marker, exactly like the notification and the app widgets:
        // the marker is prepended when the app's MediaItem.isExplicit flags the track.
        val rawTitle = if (mediaItem.isExplicit) "\uD83C\uDD74 $cleanTitle" else cleanTitle
        // Item 5: suffix only when the speed differs from 1.0 (locale-fixed format).
        val title = if (safeSpeed != 1.0f) {
            "$rawTitle [${String.format(Locale.US, "%.2fx", safeSpeed)}]"
        } else {
            rawTitle
        }
        val settings = getAdvancedSettings()
        val info = DiscordMediaInfo(
            title = title,
            artist = mediaItem.artistTextOrDb().takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_artist),
            // Album resolved from metadata then DB (same path as LastFM/lyrics) — a
            // streaming MediaItem usually carries no albumTitle. Null → the renderer
            // applies the localized "Unknown Album" for {album.name}.
            albumName = mediaItem.albumTitleOrDb().takeIf { it.isNotBlank() && it != "null" },
            songId = mediaId,
        )
        val content = DiscordActivityBuilder.buildForPlaying(info, settings, discordStrings())
        discordScope.launch {
            if (isStopped) return@launch
            sendActivity(
                content = content,
                start = calculatedStartTime,
                end = end,
                mediaItem = mediaItem,
                settings = settings
            )
        }
    }

    /**
     * Media info for template rendering (title may carry the speed suffix).
     */
    private fun mediaInfo(mediaItem: MediaItem): DiscordMediaInfo {
        val cleanTitle = cleanPrefix(mediaItem.mediaMetadata.title?.toString() ?: "").takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_title)
        return DiscordMediaInfo(
            // Same explicit marker as the activity title above (the app's
            // MediaItem.isExplicit, like the notification).
            title = if (mediaItem.isExplicit) "\uD83C\uDD74 $cleanTitle" else cleanTitle,
            artist = mediaItem.artistTextOrDb().takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_artist),
            // Album resolved from metadata then DB (see sendPlayingPresence) — the image
            // tooltips render from this info, so they stay consistent with the activity lines.
            albumName = mediaItem.albumTitleOrDb().takeIf { it.isNotBlank() && it != "null" },
            songId = mediaItem.mediaId,
        )
    }

    /**
     * Localized strings for the presence content (item 6): resolved from strings.xml so
     * no user-facing text is hardcoded in the builder (translatable per locale).
     */
    private fun discordStrings() = DiscordStrings(
        nameFallback = context.getString(R.string.discord_presence_name),
        buttonGetNZik = context.getString(R.string.discord_presence_button_get_nzik),
        buttonListenYtmusic = context.getString(R.string.discord_presence_button_listen_ytmusic),
        pausedLineDefault = context.getString(R.string.discord_presence_pause_default),
        unknownAlbum = context.getString(R.string.discord_template_unknown_album),
        appVersion = getVersionName(context),
    )
}
