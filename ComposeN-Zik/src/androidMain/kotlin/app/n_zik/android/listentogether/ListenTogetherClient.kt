// Metrolist Project (C) 2026, licensed under GPL-3.0. See git history for contributors.
// Room invariants or local guards establish the asserted values before use.

package app.n_zik.android.listentogether

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import com.google.protobuf.MessageLite
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber

import app.n_zik.android.R
import app.n_zik.android.appTextResources
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.core.notifications.ensureStartupNotificationChannel
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getActiveProfile

/** Shared Listen Together WebSocket client installed by the application. */
class ListenTogetherClient internal constructor(
    private val context: Context,
    private val socketFactory: WebSocket.Factory,
) {
        constructor(context: Context) : this(
            context,
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(60, TimeUnit.SECONDS) // Match server ping interval
                .build(),
        )

        companion object {
            private const val TAG = "ListenTogether"
            private val DEFAULT_SERVER_URL = ListenTogetherServers.defaultServerUrl
            private const val MAX_RECONNECT_ATTEMPTS = 15 // Increased from 5 to 15
            private const val INITIAL_RECONNECT_DELAY_MS = 1000L // Start at 1 second
            private const val MAX_RECONNECT_DELAY_MS = 120000L // Cap at 2 minutes
            private const val PING_INTERVAL_MS = 25000L
            private const val INITIAL_PING_INTERVAL_MS = 250L
            private const val MAX_LOG_ENTRIES = 500
            private const val SESSION_GRACE_PERIOD_MS = 10 * 60 * 1000L // 10 minutes

            // Preference keys (SharedPreferences via DataStoreUtils)
            const val PREF_SERVER_URL = "listen_together_server_url"
            const val PREF_SESSION_TOKEN = "listen_together_session_token"
            const val PREF_ROOM_CODE = "listen_together_room_code"
            const val PREF_USER_ID = "listen_together_user_id"
            const val PREF_IS_HOST = "listen_together_is_host"
            const val PREF_SESSION_TIMESTAMP = "listen_together_session_timestamp"
            const val PREF_AUTO_APPROVAL = "listen_together_auto_approval"
            const val PREF_AUTO_APPROVE_SUGGESTIONS = "listen_together_auto_approve_suggestions"
            const val PREF_BLOCKED_USERS = "listen_together_blocked_users"
            const val PREF_HISTORY = "listen_together_history"

            // Notification constants
            // internal: also the base ID of the per-profile channel set (ProfileNotificationChannels)
            internal const val NOTIFICATION_CHANNEL_ID = "listen_together_channel"
            const val ACTION_APPROVE_JOIN = "app.n_zik.android.LISTEN_TOGETHER_APPROVE_JOIN"
            const val ACTION_REJECT_JOIN = "app.n_zik.android.LISTEN_TOGETHER_REJECT_JOIN"
            const val ACTION_APPROVE_SUGGESTION = "app.n_zik.android.LISTEN_TOGETHER_APPROVE_SUGGESTION"
            const val ACTION_REJECT_SUGGESTION = "app.n_zik.android.LISTEN_TOGETHER_REJECT_SUGGESTION"
            const val EXTRA_USER_ID = "extra_user_id"
            const val EXTRA_SUGGESTION_ID = "extra_suggestion_id"
            const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

            @Volatile
            private var instance: ListenTogetherClient? = null

            fun getInstance(): ListenTogetherClient? = instance

            fun setInstance(client: ListenTogetherClient) {
                instance = client
            }
        }

        // Initialize scope early before init block since it's used in observeNetworkChanges()
        private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)

        // State flows - initialized before init block to avoid NullPointerException when accessing log()
        private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
        val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

        private val _roomState = MutableStateFlow<RoomState?>(null)
        val roomState: StateFlow<RoomState?> = _roomState.asStateFlow()

        private val _role = MutableStateFlow(RoomRole.NONE)
        val role: StateFlow<RoomRole> = _role.asStateFlow()

        private val _userId = MutableStateFlow<String?>(null)
        val userId: StateFlow<String?> = _userId.asStateFlow()

        private val _pendingJoinRequests = MutableStateFlow<List<JoinRequestPayload>>(emptyList())
        val pendingJoinRequests: StateFlow<List<JoinRequestPayload>> = _pendingJoinRequests.asStateFlow()

        private val _bufferingUsers = MutableStateFlow<List<String>>(emptyList())
        val bufferingUsers: StateFlow<List<String>> = _bufferingUsers.asStateFlow()

        // Suggestions: pending items visible to host
        private val _pendingSuggestions = MutableStateFlow<List<SuggestionReceivedPayload>>(emptyList())
        val pendingSuggestions: StateFlow<List<SuggestionReceivedPayload>> = _pendingSuggestions.asStateFlow()

        // Blocked usernames (internal list for privacy)
        private val _blockedUsernames = MutableStateFlow<Set<String>>(emptySet())
        val blockedUsernames: StateFlow<Set<String>> = _blockedUsernames.asStateFlow()

        private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
        val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

        // Signals that the async persisted-session load in [init] has finished, so the
        // manager can auto-reconnect to a fresh session (grace window) right after it.
        private val _persistedSessionLoaded = MutableStateFlow(false)
        val persistedSessionLoaded: StateFlow<Boolean> = _persistedSessionLoaded.asStateFlow()

        // Event flow
        private val _events = MutableSharedFlow<ListenTogetherEvent>()
        val events: SharedFlow<ListenTogetherEvent> = _events.asSharedFlow()
        private val eventQueue = Channel<ListenTogetherEvent>(Channel.UNLIMITED)

        // The startup coroutine reads preferences immediately after launch.
        private val sessionPreferences = ListenTogetherSessionPreferences(context)

        private var restoreSession = true

        private fun emitEvent(event: ListenTogetherEvent) {
            if (eventQueue.trySend(event).isFailure) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_failed_to_queue_listen_together_event, event::class.simpleName)
            }
        }

        /**
         * Observe network changes to trigger reconnections
         */
        private fun observeNetworkChanges() {
            scope.launch {
                try {
                    NetworkQualityHelper.observeConnection(context).collect { available: Boolean ->
                        val previous = isNetworkAvailable
                        isNetworkAvailable = available

                        if (available && !previous) {
                            log(LogLevel.INFO, R.string.listen_together_log_event_network_restored_checking_if_reconnection_needed)
                            // Reset attempts when network is restored to allow a fresh set of retries
                            if (_connectionState.value == ConnectionState.ERROR ||
                                _connectionState.value == ConnectionState.DISCONNECTED
                            ) {
                                if (sessionCredential != null || _roomState.value != null || pendingAction != null) {
                                    log(LogLevel.INFO, R.string.listen_together_log_event_network_restored_triggering_reconnection)
                                    reconnectAttempts = 0 // Reset attempts for a fresh start
                                    connect()
                                }
                            }
                        } else if (!available && previous) {
                            log(LogLevel.WARNING, R.string.listen_together_log_event_network_lost)
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error observing network changes")
                }
            }
        }

        /**
         * Load persisted session information from storage
         */
        private fun loadPersistedSession() {
            try {
                restorePersistedSession()
            } catch (e: Exception) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_failed_to_load_persisted_session, e.message)
            }

            // Also load blocked usernames
            loadBlockedUsernames()

            // Migrate old server URL to new one
            migrateServerUrl()

            // Signal that persisted-session loading is complete so the manager can
            // start its auto-reconnect.
            _persistedSessionLoaded.value = true
        }

        @Synchronized
        private fun restorePersistedSession() {
            if (!restoreSession) {
                log(LogLevel.INFO, R.string.listen_together_log_event_skipping_persisted_session_restore, logDetail(R.string.listen_together_log_detail_restore_superseded))
                return
            }
            val session = sessionPreferences.read()
            if (session.token.isEmpty()) return
            val age = System.currentTimeMillis() - session.timestamp
            if (session.roomCode.isEmpty() || age >= SESSION_GRACE_PERIOD_MS) {
                log(LogLevel.WARNING, R.string.listen_together_log_event_session_expired, logDetail(R.string.listen_together_log_detail_session_age, age))
                clearPersistedSession()
                return
            }
            // Older records contain no evidence tying their token to the current setting.
            val serverUrl = session.serverUrl ?: return
            sessionCredential = ListenTogetherSessionCredential(session.token, serverUrl)
            storedRoomCode = session.roomCode
            _userId.value = session.userId.ifEmpty { null }
            wasHost = session.isHost
            sessionStartTime = session.timestamp
            log(LogLevel.INFO, R.string.listen_together_log_event_loaded_persisted_session, logDetail(R.string.listen_together_log_detail_room_and_host, session.roomCode, session.isHost))
        }

        /**
         * Load blocked usernames from storage
         */
        private fun loadBlockedUsernames() {
            try {
                _blockedUsernames.value = sessionPreferences.readBlockedUsernames()
            } catch (e: Exception) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_failed_to_load_blocked_usernames, e.message)
                _blockedUsernames.value = emptySet()
            }
        }

        /**
         * Save blocked usernames to storage
         */
        private fun saveBlockedUsernames() {
            try {
                sessionPreferences.saveBlockedUsernames(_blockedUsernames.value)
            } catch (e: Exception) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_failed_to_save_blocked_usernames, e.message)
            }
        }

        /**
         * Migrate old server URL to new one if needed
         */
        private fun migrateServerUrl() {
            try {
                val configuredUrl = DataStoreUtils.getString(context, PREF_SERVER_URL, DEFAULT_SERVER_URL)
                val normalizedUrl = ListenTogetherServers.normalizeUrl(configuredUrl)

                if (normalizedUrl != configuredUrl && ListenTogetherServers.connectionUrl(normalizedUrl) != null) {
                    log(LogLevel.INFO, R.string.listen_together_log_event_migrating_server_url, logDetail(R.string.listen_together_log_detail_url_migration, configuredUrl, normalizedUrl))
                    if (DataStoreUtils.getString(context, PREF_SERVER_URL, DEFAULT_SERVER_URL) == configuredUrl) {
                        DataStoreUtils.saveString(context, PREF_SERVER_URL, normalizedUrl)
                    }
                }
            } catch (e: Exception) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_failed_to_migrate_server_url, e.message)
            }
        }

        /**
         * Save current session information to persistent storage
         */
        private fun savePersistedSession() {
            try {
                val credential = sessionCredential ?: return
                sessionPreferences.save(
                    token = credential.token,
                    roomCode = storedRoomCode.orEmpty(),
                    userId = _userId.value.orEmpty(),
                    isHost = wasHost,
                    timestamp = sessionStartTime,
                    serverUrl = credential.serverUrl,
                )
            } catch (e: Exception) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_failed_to_save_persisted_session, e.message)
            }
        }

        /**
         * Clear persisted session information
         */
        private fun clearPersistedSession() {
            try {
                sessionPreferences.clear()
            } catch (e: Exception) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_failed_to_clear_persisted_session, e.message)
            }
        }

        // Message codec - uses Protobuf with compression enabled
        private val codec = MessageCodec(true)
        private val serverClock = ServerClock(SystemClock::elapsedRealtime)
        private val pingSequence = AtomicLong(0L)
        private val lastPlaybackRevision = AtomicLong(0L)

        @Volatile
        private var webSocket: WebSocket? = null
        private var connectionRequestUrl: HttpUrl? = null
        private var connectedServerUrl: HttpUrl? = null
        private var pingJob: Job? = null
        private var reconnectAttempts = 0

        // Session info for reconnection
        @Volatile
        private var sessionCredential: ListenTogetherSessionCredential? = null
        private var storedUsername: String? = null
        private var storedRoomCode: String? = null
        private var wasHost: Boolean = false
        private var sessionStartTime: Long = 0

        // Pending actions to execute when connected
        @Volatile
        private var pendingAction: PendingAction? = null
        private var pendingActionServerUrl: HttpUrl? = null

        // Wake lock to keep connection alive when in a room
        private var wakeLock: PowerManager.WakeLock? = null

        private val roomNotifications = ListenTogetherNotifications(context)

        // Network connectivity monitoring
        private var isNetworkAvailable =
            try {
                NetworkQualityHelper.isNetworkConnected(context)
            } catch (e: Exception) {
                true
            }

        init {
            setInstance(this)
            ensureNotificationChannel()
            scope.launch {
                for (event in eventQueue) {
                    _events.emit(event)
                }
            }
            scope.launch {
                loadPersistedSession()
                observeNetworkChanges()
            }
        }

        /** Stable User-Agent for the Listen Together server: every build variant (debug / foss / dev) reports the release package name, so a server-side UA allowlist only needs the single entry `com.nevar.nzik`. */
        private fun userAgent(): String =
            context.packageName
                .removeSuffix(".debug")
                .removeSuffix(".foss")
                .removeSuffix(".dev")

        private fun getServerUrl(): HttpUrl? {
            val configuredUrl = DataStoreUtils.getString(context, PREF_SERVER_URL, DEFAULT_SERVER_URL)
            return ListenTogetherServers.connectionUrl(configuredUrl)
        }

        /**
         * Calculate exponential backoff delay with jitter
         */
        private fun calculateBackoffDelay(attempt: Int): Long {
            val exponentialDelay = INITIAL_RECONNECT_DELAY_MS * (2 shl (minOf(attempt - 1, 4)))
            val cappedDelay = minOf(exponentialDelay, MAX_RECONNECT_DELAY_MS)
            // Add 0-20% jitter to prevent thundering herd
            val jitter = (cappedDelay * 0.2 * Math.random()).toLong()
            return cappedDelay + jitter
        }

        private fun logReason(reason: String?): LogDetail? = when (reason) {
            LISTEN_TOGETHER_JOIN_BLOCKED_REASON, LISTEN_TOGETHER_BLOCKED_REASON ->
                logDetail(R.string.listen_together_user_blocked_by_host)
            "Rejected by host", "Removed by host" -> null
            else -> reason?.let { raw -> LogDetail { raw } }
        }

        private fun logDetail(@StringRes resource: Int, vararg args: Any?): LogDetail =
            LogDetail { resources -> resources.getString(resource, *args) }

        private fun log(
            level: LogLevel,
            @StringRes messageRes: Int,
            details: String? = null,
        ) = log(level, messageRes, details?.let { raw -> LogDetail { raw } })

        private fun log(
            level: LogLevel,
            @StringRes messageRes: Int,
            detail: LogDetail?,
        ) {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
            val entry = LogEntry(timestamp, level, messageRes, detail)

            _logs.value = (_logs.value + entry).takeLast(MAX_LOG_ENTRIES)

            val resources = appTextResources()
            val message = resources.getString(messageRes)
            val details = detail?.resolve(resources)
            when (level) {
                LogLevel.ERROR -> Timber.tag(TAG).e("$message ${details ?: ""}")
                LogLevel.WARNING -> Timber.tag(TAG).w("$message ${details ?: ""}")
                LogLevel.DEBUG -> Timber.tag(TAG).d("$message ${details ?: ""}")
                LogLevel.INFO -> Timber.tag(TAG).i("$message ${details ?: ""}")
            }
        }

        fun clearLogs() {
            _logs.value = emptyList()
        }

        /**
         * Connect to the Listen Together server
         */
        @Synchronized
        fun connect() {
            if (_connectionState.value == ConnectionState.CONNECTED ||
                _connectionState.value == ConnectionState.CONNECTING
            ) {
                log(LogLevel.WARNING, R.string.listen_together_log_event_already_connected_or_connecting)
                return
            }

            val serverUrl = sessionCredential?.serverUrl ?: pendingActionServerUrl ?: getServerUrl()
            if (serverUrl == null) {
                reportConnectionError(R.string.listen_together_invalid_server_address)
                return
            }

            connectionRequestUrl = serverUrl
            serverClock.reset()
            log(LogLevel.INFO, R.string.listen_together_log_event_connecting_to_server, serverUrl.redact())

            val request =
                Request
                    .Builder()
                    .url(serverUrl)
                    .header("User-Agent", userAgent())
                    .build()

            _connectionState.value = ConnectionState.CONNECTING
            webSocket =
                socketFactory.newWebSocket(
                    request,
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) = synchronized(this@ListenTogetherClient) {
                            if (webSocket !== this@ListenTogetherClient.webSocket) return@synchronized
                            val serverUrl = response.request.url
                            val credential = sessionCredential
                            val resumeToken = credential?.tokenFor(serverUrl)
                            if (credential != null && resumeToken == null) {
                                reportConnectionError(R.string.listen_together_session_server_changed)
                                return@synchronized
                            }
                            connectedServerUrl = serverUrl
                            log(LogLevel.INFO, R.string.listen_together_log_event_connected_to_server)
                            _connectionState.value = ConnectionState.CONNECTED
                            reconnectAttempts = 0
                            startPingJob()

                            if (resumeToken != null) {
                                log(LogLevel.INFO, R.string.listen_together_log_event_attempting_to_reconnect_to_previous_session, logDetail(R.string.listen_together_log_detail_room, storedRoomCode))
                                sendMessage(MessageTypes.RECONNECT, ReconnectPayload(resumeToken), webSocket)
                            } else {
                                // Execute any pending action
                                executePendingAction()
                            }
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            bytes: okio.ByteString,
                        ) = synchronized(this@ListenTogetherClient) {
                            if (webSocket !== this@ListenTogetherClient.webSocket) return@synchronized
                            // Handle binary protobuf messages
                            handleMessage(bytes.toByteArray(), connectedServerUrl ?: return@synchronized)
                        }

                        override fun onClosing(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) = synchronized(this@ListenTogetherClient) {
                            if (webSocket !== this@ListenTogetherClient.webSocket) return@synchronized
                            log(LogLevel.INFO, R.string.listen_together_log_event_server_closing_connection, logDetail(R.string.listen_together_log_detail_close_code_reason, code, reason))
                            webSocket.close(1000, null)
                            Unit
                        }

                        override fun onClosed(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) = synchronized(this@ListenTogetherClient) {
                            if (webSocket !== this@ListenTogetherClient.webSocket) return@synchronized
                            clearConnection()
                            log(LogLevel.INFO, R.string.listen_together_log_event_connection_closed, logDetail(R.string.listen_together_log_detail_close_code_reason, code, reason))
                            handleDisconnect()
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) = synchronized(this@ListenTogetherClient) {
                            if (webSocket !== this@ListenTogetherClient.webSocket) return@synchronized
                            clearConnection()
                            log(LogLevel.ERROR, R.string.listen_together_log_event_connection_failure, t.message)
                            handleConnectionFailure(t)
                        }
                    },
                )
        }

        private fun reportConnectionError(@StringRes reason: Int) {
            clearConnection()?.cancel()
            pingJob?.cancel()
            pingJob = null
            pendingAction = null
            pendingActionServerUrl = null
            _connectionState.value = ConnectionState.ERROR
            val message = appTextResources().getString(reason)
            log(LogLevel.ERROR, R.string.listen_together_log_event_connection_failure, message)
            emitEvent(ListenTogetherEvent.ConnectionError(message, isServerConfigurationError = true))
        }

        private fun clearConnection(): WebSocket? {
            val socket = webSocket
            webSocket = null
            connectionRequestUrl = null
            connectedServerUrl = null
            return socket
        }

        private fun executePendingAction() {
            val action = pendingAction ?: return
            pendingAction = null
            pendingActionServerUrl = null

            sendRoomAction(action, queued = true)
        }

        private fun sendRoomAction(action: PendingAction, queued: Boolean) {
            when (action) {
                is PendingAction.CreateRoom -> {
                    if (queued) log(LogLevel.INFO, R.string.listen_together_log_event_executing_pending_create_room, action.username)
                    sendMessage(MessageTypes.CREATE_ROOM, CreateRoomPayload(action.username))
                }
                is PendingAction.JoinRoom -> {
                    if (queued) log(
                        LogLevel.INFO,
                        R.string.listen_together_log_event_executing_pending_join_room,
                        logDetail(R.string.listen_together_log_detail_pending_join, action.roomCode, action.username),
                    )
                    sendMessage(MessageTypes.JOIN_ROOM, JoinRoomPayload(action.roomCode.uppercase(), action.username))
                }
            }
        }

        /**
         * Disconnect from the server
         */
        @Synchronized
        fun disconnect() {
            log(LogLevel.INFO, R.string.listen_together_log_event_disconnecting_from_server)
            releaseWakeLock() // Release wake lock when disconnecting
            pingJob?.cancel()
            pingJob = null
            serverClock.reset()
            lastPlaybackRevision.set(0L)
            clearConnection()?.close(1000, "User disconnected")
            _connectionState.value = ConnectionState.DISCONNECTED

            // Clear session and state on explicit disconnect
            restoreSession = false
            sessionCredential = null
            storedRoomCode = null
            storedUsername = null
            pendingAction = null
            pendingActionServerUrl = null
            _roomState.value = null
            _role.value = RoomRole.NONE
            _userId.value = null
            _pendingJoinRequests.value = emptyList()
            _bufferingUsers.value = emptyList()

            // Clear from persistent storage
            clearPersistedSession()
            reconnectAttempts = 0

            emitEvent(ListenTogetherEvent.Disconnected)
        }

        private fun startPingJob() {
            pingJob?.cancel()
            pingJob =
                scope.launch {
                    repeat(3) {
                        sendTimeSyncPing()
                        delay(INITIAL_PING_INTERVAL_MS)
                    }
                    while (true) {
                        // Refresh the WakeLock on every ping cycle so it never expires while the
                        // connection is active. Without this, the 10-minute timeout can lapse during
//
                        // long sessions with the screen off, allowing the CPU to throttle and
                        // causing the WebSocket to degrade, resulting in choppy audio.
                        acquireWakeLock()
                        sendTimeSyncPing()
                        delay(PING_INTERVAL_MS)
                    }
                }
        }

        private fun sendTimeSyncPing() {
            sendMessage(
                MessageTypes.PING,
                PingPayload(
                    clientTime = SystemClock.elapsedRealtime(),
                    sequence = pingSequence.incrementAndGet(),
                ),
            )
        }

        internal fun serverTimeNow(): Long? = serverClock.now()

        internal fun positionAtServerTime(
            position: Long,
            effectiveAtServerTime: Long?,
            isPlaying: Boolean,
        ): Long = serverClock.positionAt(position, effectiveAtServerTime, isPlaying)

        private fun acceptPlaybackRevision(revision: Long): Boolean {
            if (revision <= 0L) return true
            while (true) {
                val current = lastPlaybackRevision.get()
                if (revision < current) return false
                if (revision == current || lastPlaybackRevision.compareAndSet(current, revision)) return true
            }
        }

        @Suppress("DEPRECATION")
        private fun acquireWakeLock() {
            if (wakeLock == null) {
                val powerManager = context.getSystemService<PowerManager>()
                wakeLock =
                    powerManager?.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "NZik:ListenTogether",
                    )
            }
            // Always release before acquiring so that the timeout is reset on each call.
            // This is safe because the ping job calls acquireWakeLock() every PING_INTERVAL_MS
//
            // (25 s), ensuring the lock is refreshed well before the 10-minute window elapses.
            // Without the release-and-reacquire pattern the first acquire() sets the countdown
//
            // and subsequent calls while isHeld is true are no-ops, so the lock would still
//
            // expire after 10 minutes of continuous screen-off sessions.
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock?.acquire(10 * 60 * 1000L)
            log(LogLevel.DEBUG, R.string.listen_together_log_event_wake_lock_acquired)
        }

        private fun releaseWakeLock() {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                log(LogLevel.DEBUG, R.string.listen_together_log_event_wake_lock_released)
            }
        }

        private fun ensureNotificationChannel() {
            try {
                ensureStartupNotificationChannel(context, NOTIFICATION_CHANNEL_ID, getActiveProfile(context))
            } catch (e: Exception) {
                log(LogLevel.WARNING, R.string.listen_together_log_event_failed_to_create_notification_channel, e.message)
            }
        }

        private fun handleDisconnect() {
            pingJob?.cancel()
            pingJob = null
            serverClock.reset()

            // Don't clear room state - we might reconnect
            // Only update connection state
            _connectionState.value = ConnectionState.DISCONNECTED
            _pendingJoinRequests.value = emptyList()
            _bufferingUsers.value = emptyList()

            // If we have a session, try to reconnect
            if (sessionCredential != null && _roomState.value != null) {
                log(LogLevel.INFO, R.string.listen_together_log_event_connection_lost_will_attempt_to_reconnect)
                handleConnectionFailure(Exception("Connection lost"))
            } else {
                emitEvent(ListenTogetherEvent.Disconnected)
            }
        }

        private fun handleConnectionFailure(t: Throwable) {
            pingJob?.cancel()
            pingJob = null

            // Always try to reconnect if we have a session token or pending action
            val shouldReconnect = sessionCredential != null || _roomState.value != null || pendingAction != null

            if (!isNetworkAvailable) {
                log(LogLevel.WARNING, R.string.listen_together_log_event_connection_failure_waiting_for_network, t.message)
                _connectionState.value = ConnectionState.DISCONNECTED
                return
            }

            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS && shouldReconnect) {
                reconnectAttempts++
                _connectionState.value = ConnectionState.RECONNECTING

                val delayMs = calculateBackoffDelay(reconnectAttempts)
                val delaySeconds = delayMs / 1000

                log(
                    LogLevel.INFO,
                    R.string.listen_together_log_event_attempting_reconnect,
                    logDetail(R.string.listen_together_log_detail_reconnect_attempt, reconnectAttempts, MAX_RECONNECT_ATTEMPTS, delaySeconds, t.message),
                )

                emitEvent(ListenTogetherEvent.Reconnecting(reconnectAttempts, MAX_RECONNECT_ATTEMPTS))
                scope.launch {
                    delay(delayMs)

                    // Check if we're still supposed to be reconnecting
                    if (_connectionState.value == ConnectionState.RECONNECTING || _connectionState.value == ConnectionState.DISCONNECTED) {
                        log(LogLevel.INFO, R.string.listen_together_log_event_reconnecting_after_backoff, logDetail(R.string.listen_together_log_detail_reconnect_delay, delaySeconds))
                        connect()
                    }
                }
            } else {
                _connectionState.value = ConnectionState.ERROR

                // If we had a session, notify user but keep session data for manual retry
                if (sessionCredential != null) {
                    log(
                        LogLevel.ERROR,
                        R.string.listen_together_log_event_reconnection_failed,
                        logDetail(R.string.listen_together_log_detail_reconnect_manual),
                    )
                    emitEvent(
                        ListenTogetherEvent.ConnectionError(
                            "Connection failed after $MAX_RECONNECT_ATTEMPTS attempts. ${t.message ?: "Unknown error"}",
                        ),
                    )
                } else {
                    // No session, so clear everything
                    pendingAction = null
                    pendingActionServerUrl = null
                    storedRoomCode = null
                    storedUsername = null
                    _roomState.value = null
                    _role.value = RoomRole.NONE
                    clearPersistedSession()

                    emitEvent(ListenTogetherEvent.ConnectionError(t.message ?: "Unknown error"))
                }
            }
        }

        private fun handleMessage(data: ByteArray, serverUrl: HttpUrl) {
            log(LogLevel.DEBUG, R.string.listen_together_log_event_received_message, logDetail(R.string.listen_together_log_detail_message_bytes, data.size))

            try {
                // Decode message using Protobuf
                val (msgType, payloadBytes) = codec.decode(data)

                when (msgType) {
                    MessageTypes.ROOM_CREATED -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? RoomCreatedPayload ?: return
                        _userId.value = payload.userId
                        _role.value = RoomRole.HOST
                        lastPlaybackRevision.set(0L)
                        sessionCredential = ListenTogetherSessionCredential(payload.sessionToken, serverUrl)
                        storedRoomCode = payload.roomCode
                        wasHost = true
                        sessionStartTime = System.currentTimeMillis()

                        _roomState.value =
                            RoomState(
                                roomCode = payload.roomCode,
                                hostId = payload.userId,
                                users = listOf(UserInfo(payload.userId, storedUsername ?: "", true)),
                                isPlaying = false,
                                position = 0,
                                lastUpdate = System.currentTimeMillis(),
                                volume = 1f,
                            )

                        // Save session to persistent storage
                        savePersistedSession()

                        acquireWakeLock() // Keep connection alive while in room
                        log(LogLevel.INFO, R.string.listen_together_log_event_room_created, logDetail(R.string.listen_together_log_detail_room_code, payload.roomCode))
                        emitEvent(ListenTogetherEvent.RoomCreated(payload.roomCode, payload.userId))
                        // App custom toast (info variant, app pattern) for room creation so the host sees it regardless of UI
                        Toaster.i(R.string.listen_together_room_created, payload.roomCode)
                    }

                    MessageTypes.JOIN_REQUEST -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? JoinRequestPayload ?: return

                        // Check if user is blocked
                        if (isUserBlocked(payload.username)) {
                            log(LogLevel.INFO, R.string.listen_together_log_event_join_request_from_blocked_user_ignored, logDetail(R.string.listen_together_log_detail_user, payload.username))
                            // Silently reject blocked users
                            rejectJoin(payload.userId, LISTEN_TOGETHER_JOIN_BLOCKED_REASON)
                            return
                        }

                        _pendingJoinRequests.value += payload
                        log(LogLevel.INFO, R.string.listen_together_log_event_join_request_received, logDetail(R.string.listen_together_log_detail_user, payload.username))

                        // Check if auto-approval is enabled
                        val autoApprovalEnabled = DataStoreUtils.getBoolean(context, PREF_AUTO_APPROVAL, false)

                        if (_role.value == RoomRole.HOST) {
                            if (autoApprovalEnabled) {
                                // Automatically approve the join request
                                log(LogLevel.INFO, R.string.listen_together_log_event_auto_approving_join_request, logDetail(R.string.listen_together_log_detail_user, payload.username))
                                approveJoin(payload.userId)
                            } else {
                                // Notify host with Approve/Reject actions
                                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    roomNotifications.showJoinRequest(payload)
                                }
                            }
                        }
                        emitEvent(ListenTogetherEvent.JoinRequestReceived(payload.userId, payload.username))
                    }

                    MessageTypes.JOIN_APPROVED -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? JoinApprovedPayload ?: return
                        _userId.value = payload.userId
                        _role.value = RoomRole.GUEST
                        sessionCredential = ListenTogetherSessionCredential(payload.sessionToken, serverUrl)
                        storedRoomCode = payload.roomCode
                        wasHost = false
                        sessionStartTime = System.currentTimeMillis()

                        _roomState.value = payload.state
                        lastPlaybackRevision.set(payload.state.revision)

                        // Save session to persistent storage
                        savePersistedSession()

                        acquireWakeLock() // Keep connection alive while in room
                        log(LogLevel.INFO, R.string.listen_together_log_event_joined_room, logDetail(R.string.listen_together_log_detail_room_code, payload.roomCode))
                        emitEvent(ListenTogetherEvent.JoinApproved(payload.roomCode, payload.userId, payload.state))
                    }

                    MessageTypes.JOIN_REJECTED -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? JoinRejectedPayload ?: return
                        log(LogLevel.WARNING, R.string.listen_together_log_event_join_rejected, logReason(payload.reason))
                        emitEvent(ListenTogetherEvent.JoinRejected(payload.reason))
                    }

                    MessageTypes.USER_JOINED -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? UserJoinedPayload ?: return
                        _roomState.value =
                            _roomState.value?.copy(
                                users = _roomState.value!!.users + UserInfo(payload.userId, payload.username, false),
                            )
                        _pendingJoinRequests.value = _pendingJoinRequests.value.filter { it.userId != payload.userId }

                        roomNotifications.cancelJoinRequest(payload.userId)

                        log(LogLevel.INFO, R.string.listen_together_log_event_user_joined, payload.username)
                        emitEvent(ListenTogetherEvent.UserJoined(payload.userId, payload.username))
                    }

                    MessageTypes.USER_LEFT -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? UserLeftPayload ?: return
                        _roomState.value =
                            _roomState.value?.copy(
                                users = _roomState.value!!.users.filter { it.userId != payload.userId },
                            )
                        log(LogLevel.INFO, R.string.listen_together_log_event_user_left, payload.username)
                        emitEvent(ListenTogetherEvent.UserLeft(payload.userId, payload.username))
                    }

                    MessageTypes.HOST_CHANGED -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? HostChangedPayload ?: return
                        _roomState.value =
                            _roomState.value?.copy(
                                hostId = payload.newHostId,
                                users =
                                    _roomState.value!!.users.map {
                                        it.copy(isHost = it.userId == payload.newHostId)
                                    },
                            )
                        if (payload.newHostId == _userId.value) {
                            _role.value = RoomRole.HOST
                        } else if (_role.value == RoomRole.HOST) {
                            // Lost host role
                            _role.value = RoomRole.GUEST
                        }
                        log(LogLevel.INFO, R.string.listen_together_log_event_host_changed, logDetail(R.string.listen_together_log_detail_new_host, payload.newHostName))
                        emitEvent(ListenTogetherEvent.HostChanged(payload.newHostId, payload.newHostName))
                    }

                    MessageTypes.KICKED -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? KickedPayload ?: return
                        log(LogLevel.WARNING, R.string.listen_together_log_event_kicked_from_room, logReason(payload.reason))
                        releaseWakeLock() // Release wake lock when kicked
                        sessionCredential = null
                        clearPersistedSession()
                        _roomState.value = null
                        _role.value = RoomRole.NONE
                        lastPlaybackRevision.set(0L)
                        emitEvent(ListenTogetherEvent.Kicked(payload.reason))
                    }

                    MessageTypes.SYNC_PLAYBACK -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? PlaybackActionPayload ?: return
                        if (!acceptPlaybackRevision(payload.revision)) {
                            log(LogLevel.DEBUG, R.string.listen_together_log_event_discarding_stale_playback_event, logDetail(R.string.listen_together_log_detail_revision, payload.revision))
                            return
                        }
                        log(LogLevel.DEBUG, R.string.listen_together_log_event_playback_sync, logDetail(R.string.listen_together_log_detail_action, payload.action))

                        _roomState.applyPlaybackAction(payload)

                        emitEvent(ListenTogetherEvent.PlaybackSync(payload))
                    }

                    MessageTypes.BUFFER_WAIT -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? BufferWaitPayload ?: return
                        _bufferingUsers.value = payload.waitingFor
                        log(LogLevel.DEBUG, R.string.listen_together_log_event_waiting_for_buffering, logDetail(R.string.listen_together_log_detail_waiting_users, payload.waitingFor.size))
                        emitEvent(ListenTogetherEvent.BufferWait(payload.trackId, payload.waitingFor))
                    }

                    MessageTypes.BUFFER_COMPLETE -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? BufferCompletePayload ?: return
                        _bufferingUsers.value = emptyList()
                        log(LogLevel.INFO, R.string.listen_together_log_event_all_users_buffered, logDetail(R.string.listen_together_log_detail_track, payload.trackId))
                        emitEvent(ListenTogetherEvent.BufferComplete(payload.trackId))
                    }

                    MessageTypes.SYNC_STATE -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? SyncStatePayload ?: return
                        if (!acceptPlaybackRevision(payload.revision)) {
                            log(LogLevel.DEBUG, R.string.listen_together_log_event_discarding_stale_sync_state, logDetail(R.string.listen_together_log_detail_revision, payload.revision))
                            return
                        }
                        _roomState.applyPlaybackSnapshot(payload)
                        log(LogLevel.INFO, R.string.listen_together_log_event_sync_state_received, logDetail(R.string.listen_together_log_detail_playback_state, payload.isPlaying, payload.position))
                        emitEvent(ListenTogetherEvent.SyncStateReceived(payload))
                    }

                    MessageTypes.SUGGESTION_RECEIVED -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? SuggestionReceivedPayload ?: return
                        // Only host should receive suggestions
                        if (_role.value == RoomRole.HOST) {
                            // Check if user is blocked
                            if (isUserBlocked(payload.fromUsername)) {
                                log(LogLevel.INFO, R.string.listen_together_log_event_suggestion_from_blocked_user_ignored, logDetail(R.string.listen_together_log_detail_user, payload.fromUsername))
                                return
                            }

                            log(LogLevel.INFO, R.string.listen_together_log_event_suggestion_received, logDetail(R.string.listen_together_log_detail_suggestion_from, payload.fromUsername, payload.trackInfo.title))

                            // Check if auto-approval of suggestions is enabled
                            val autoApproveSuggestionsEnabled = DataStoreUtils.getBoolean(context, PREF_AUTO_APPROVE_SUGGESTIONS, false)

                            if (autoApproveSuggestionsEnabled) {
                                // Automatically approve the suggestion
                                log(LogLevel.INFO, R.string.listen_together_log_event_auto_approving_suggestion, logDetail(R.string.listen_together_log_detail_suggestion_from, payload.fromUsername, payload.trackInfo.title))
                                approveSuggestion(payload.suggestionId)
                            } else {
                                // Add to pending list and show notification
                                _pendingSuggestions.value += payload
                                // Notify the host with actionable notification
                                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    roomNotifications.showSuggestion(payload)
                                }
                            }
                        }
                    }

                    MessageTypes.SUGGESTION_APPROVED -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? SuggestionApprovedPayload ?: return
                        log(LogLevel.INFO, R.string.listen_together_log_event_suggestion_approved, payload.trackInfo.title)

                        roomNotifications.cancelSuggestion(payload.suggestionId)

                        // Notify the guest whose suggestion was accepted
                        if (_role.value == RoomRole.GUEST) {
                            emitEvent(ListenTogetherEvent.SuggestionApproved)
                        }
                    }

                    MessageTypes.SUGGESTION_REJECTED -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? SuggestionRejectedPayload ?: return
                        log(LogLevel.WARNING, R.string.listen_together_log_event_suggestion_rejected, logReason(payload.reasonOrNull))

                        roomNotifications.cancelSuggestion(payload.suggestionId)

                        // Notify the guest whose suggestion was declined
                        if (_role.value == RoomRole.GUEST) {
                            emitEvent(ListenTogetherEvent.SuggestionRejected(payload.reasonOrNull.orEmpty()))
                        }
                    }

                    MessageTypes.ERROR -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? ErrorPayload ?: return
                        log(LogLevel.ERROR, R.string.listen_together_log_event_server_error, logDetail(R.string.listen_together_log_detail_code_message, payload.code, payload.message))

                        // Handle specific error cases
                        when (payload.code) {
                            "session_not_found" -> {
                                // Clear stale room state immediately - server no longer recognizes the session
                                _roomState.value = null
                                _role.value = RoomRole.NONE
                                _pendingJoinRequests.value = emptyList()
                                _bufferingUsers.value = emptyList()

                                // Session expired on server, try to rejoin the room
                                val roomCode = storedRoomCode
                                val username = storedUsername
                                if (roomCode != null && username != null && !wasHost) {
                                    log(
                                        LogLevel.WARNING,
                                        R.string.listen_together_log_event_session_expired_on_server,
                                        logDetail(R.string.listen_together_log_detail_rejoin_room, storedRoomCode),
                                    )
                                    // Try rejoining as a guest
                                    scope.launch {
                                        delay(500) // Small delay before rejoin attempt
                                        synchronized(this@ListenTogetherClient) {
                                            if (connectedServerUrl == serverUrl && sessionCredential?.serverUrl == serverUrl) {
                                                submitRoomAction(PendingAction.JoinRoom(roomCode, username), username, serverUrl)
                                            }
                                        }
                                    }
                                } else if (storedRoomCode != null && storedUsername != null) {
                                    // Host session expired - would need to create new room
                                    log(
                                        LogLevel.WARNING,
                                        R.string.listen_together_log_event_host_session_expired,
                                        logDetail(R.string.listen_together_log_detail_rejoin_manual, storedRoomCode),
                                    )
                                    clearPersistedSession()
                                    sessionCredential = null
                                } else {
                                    clearPersistedSession()
                                    sessionCredential = null
                                }
                            }

                            else -> {}
                        }

                        emitEvent(ListenTogetherEvent.ServerError(payload.code, payload.message))
                    }

                    MessageTypes.PONG -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? PongPayload
                        if (payload != null) {
                            val firstSample =
                                serverClock.recordPong(
                                    payload.clientTime,
                                    payload.serverReceiveTime,
                                    payload.serverSendTime,
                                )
                            if (firstSample && _role.value == RoomRole.GUEST) requestSync()
                        }
                        log(LogLevel.DEBUG, R.string.listen_together_log_event_pong_received)
                    }

                    MessageTypes.RECONNECTED -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? ReconnectedPayload ?: return
                        if (sessionCredential?.serverUrl != serverUrl) return
                        _userId.value = payload.userId
                        _role.value = if (payload.isHost) RoomRole.HOST else RoomRole.GUEST
                        _roomState.value = payload.state
                        lastPlaybackRevision.set(payload.state.revision)

                        // Update persisted session info
                        wasHost = payload.isHost
                        sessionStartTime = System.currentTimeMillis()
                        savePersistedSession()

                        // Reset reconnection attempts on successful reconnection
                        reconnectAttempts = 0

                        acquireWakeLock() // Re-acquire wake lock after reconnection
                        log(
                            LogLevel.INFO,
                            R.string.listen_together_log_event_successfully_reconnected_to_room,
                            logDetail(R.string.listen_together_log_detail_reconnected_session, payload.roomCode, payload.isHost, reconnectAttempts),
                        )
                        emitEvent(ListenTogetherEvent.Reconnected(payload.roomCode, payload.userId, payload.state, payload.isHost))
                    }

                    MessageTypes.USER_RECONNECTED -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? UserReconnectedPayload ?: return
                        // Mark user as connected in the room state
                        _roomState.value =
                            _roomState.value?.copy(
                                users =
                                    _roomState.value!!.users.map { user ->
                                        if (user.userId == payload.userId) user.copy(isConnected = true) else user
                                    },
                            )
                        log(LogLevel.INFO, R.string.listen_together_log_event_user_reconnected, payload.username)
                        emitEvent(ListenTogetherEvent.UserReconnected(payload.userId, payload.username))
                    }

                    MessageTypes.USER_DISCONNECTED -> {
                        val payload = codec.decodePayload(msgType, payloadBytes) as? UserDisconnectedPayload ?: return
                        // Mark user as disconnected in the room state
                        _roomState.value =
                            _roomState.value?.copy(
                                users =
                                    _roomState.value!!.users.map { user ->
                                        if (user.userId == payload.userId) user.copy(isConnected = false) else user
                                    },
                            )
                        log(LogLevel.INFO, R.string.listen_together_log_event_user_temporarily_disconnected, payload.username)
                        emitEvent(ListenTogetherEvent.UserDisconnected(payload.userId, payload.username))
                    }

                    else -> {
                        log(LogLevel.WARNING, R.string.listen_together_log_event_unknown_message_type, msgType)
                    }
                }
            } catch (e: Exception) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_error_parsing_message, e.message)
            }
        }

        private fun sendMessage(
            type: String,
            payload: MessageLite?,
            socket: WebSocket? = webSocket,
        ): Boolean = try {
            val data = codec.encode(type, payload)
            log(LogLevel.DEBUG, R.string.listen_together_log_event_sending_message, logDetail(R.string.listen_together_log_detail_protobuf_type, type))

            val success = socket?.send(okio.ByteString.of(*data)) == true
            if (!success) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_failed_to_send_message, type)
            }
            success
        } catch (e: Exception) {
            log(LogLevel.ERROR, R.string.listen_together_log_event_error_encoding_message, logDetail(R.string.listen_together_log_detail_type_error, type, e.message))
            false
        }

        private fun sendMessageNoPayload(type: String) {
            sendMessage(type, null)
        }

        // Public API methods

        /** Creates a room when the connection can send the request. */
        fun createRoom(username: String) = submitRoomAction(PendingAction.CreateRoom(username), username)

        /** Joins a room when the connection can send the request. */
        fun joinRoom(roomCode: String, username: String) =
            submitRoomAction(PendingAction.JoinRoom(roomCode, username), username)

        @Synchronized
        private fun submitRoomAction(action: PendingAction, username: String, serverUrl: HttpUrl? = getServerUrl()) {
            if (serverUrl == null) {
                reportConnectionError(R.string.listen_together_invalid_server_address)
                return
            }
            restoreSession = false
            lastPlaybackRevision.set(0L)
            // The new action must win over a pending persisted-session restore.
            clearPersistedSession()
            sessionCredential = null
            storedRoomCode = null
            wasHost = false
            storedUsername = username

            if (_connectionState.value == ConnectionState.CONNECTED &&
                (connectionRequestUrl == serverUrl || connectedServerUrl == serverUrl)
            ) {
                sendRoomAction(action, queued = false)
                return
            }
            val waitingMessage = when (action) {
                is PendingAction.CreateRoom -> R.string.listen_together_log_event_not_connected_queueing_create_room_action
                is PendingAction.JoinRoom -> R.string.listen_together_log_event_not_connected_queueing_join_room_action
            }
            log(LogLevel.INFO, waitingMessage)
            pendingAction = action
            pendingActionServerUrl = serverUrl
            if (webSocket != null && connectionRequestUrl != serverUrl) {
                clearConnection()?.close(1000, "Server selection changed")
                pingJob?.cancel()
                pingJob = null
                _connectionState.value = ConnectionState.DISCONNECTED
            }
            if (_connectionState.value == ConnectionState.DISCONNECTED || _connectionState.value == ConnectionState.ERROR) {
                connect()
            }
        }

        /**
         * Leave the current room
         */
        @Synchronized
        fun leaveRoom() {
            sendMessageNoPayload(MessageTypes.LEAVE_ROOM)

            // Clear session info on intentional leave
            restoreSession = false
            sessionCredential = null
            storedRoomCode = null
            storedUsername = null
            pendingAction = null
            pendingActionServerUrl = null
            _roomState.value = null
            _role.value = RoomRole.NONE
            _userId.value = null
            _pendingJoinRequests.value = emptyList()
            _bufferingUsers.value = emptyList()
            lastPlaybackRevision.set(0L)

            // Clear from persistent storage
            clearPersistedSession()

            releaseWakeLock()
        }

        /**
         * Approve a join request (host only)
         */
        fun approveJoin(userId: String) {
            if (_role.value != RoomRole.HOST) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_cannot_approve_join, logDetail(R.string.listen_together_log_detail_not_host))
                return
            }
            sendMessage(MessageTypes.APPROVE_JOIN, ApproveJoinPayload(userId))

            roomNotifications.cancelJoinRequest(userId)
        }

        /**
         * Reject a join request (host only)
         */
        fun rejectJoin(
            userId: String,
            reason: String? = null,
        ) {
            if (_role.value != RoomRole.HOST) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_cannot_reject_join, logDetail(R.string.listen_together_log_detail_not_host))
                return
            }
            sendMessage(MessageTypes.REJECT_JOIN, RejectJoinPayload(userId, reason))
            _pendingJoinRequests.value = _pendingJoinRequests.value.filter { it.userId != userId }

            roomNotifications.cancelJoinRequest(userId)
        }

        /**
         * Kick a user from the room (host only)
         */
        fun kickUser(
            userId: String,
            reason: String? = null,
        ) {
            if (_role.value != RoomRole.HOST) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_cannot_kick_user, logDetail(R.string.listen_together_log_detail_not_host))
                return
            }
            sendMessage(MessageTypes.KICK_USER, KickUserPayload(userId, reason))
        }

        /**
         * Transfer host role to another user (host only)
         */
        fun transferHost(newHostId: String) {
            if (_role.value != RoomRole.HOST) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_cannot_transfer_host, logDetail(R.string.listen_together_log_detail_not_host))
                return
            }
            sendMessage(MessageTypes.TRANSFER_HOST, TransferHostPayload(newHostId))
        }

        /**
         * Send a playback action (host only)
         */
        fun sendPlaybackAction(
            action: String,
            trackId: String? = null,
            position: Long? = null,
            trackInfo: TrackInfo? = null,
            insertNext: Boolean? = null,
            queue: List<TrackInfo>? = null,
            queueTitle: String? = null,
            volume: Float? = null,
        ) {
            if (_role.value != RoomRole.HOST) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_cannot_control_playback, logDetail(R.string.listen_together_log_detail_not_host))
                return
            }
            sendMessage(
                MessageTypes.PLAYBACK_ACTION,
                PlaybackActionPayload(
                    action,
                    trackId,
                    position,
                    trackInfo,
                    insertNext,
                    queue,
                    queueTitle,
                    volume,
                    capturedAtServerTime =
                        serverTimeNow().takeIf {
                            position != null &&
                                (action == PlaybackActions.PLAY || action == PlaybackActions.PAUSE || action == PlaybackActions.SEEK)
                        },
                ),
            )
        }

        /**
         * Signal that buffering is complete for the current track
         */
        fun sendBufferReady(trackId: String) {
            val sanitizedTrackId = trackId.trim()
            if (sanitizedTrackId.isEmpty()) {
                log(LogLevel.WARNING, R.string.listen_together_log_event_skipping_buffer_ready, logDetail(R.string.listen_together_log_detail_blank_track_id))
                return
            }
            sendMessage(MessageTypes.BUFFER_READY, BufferReadyPayload(sanitizedTrackId))
        }

        /**
         * Suggest a track to the host (guest only)
         */
        fun suggestTrack(trackInfo: TrackInfo): Boolean {
            if (!isInRoom) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_cannot_suggest_track, logDetail(R.string.listen_together_log_detail_not_in_room))
                return false
            }
            if (_role.value == RoomRole.HOST) {
                log(LogLevel.WARNING, R.string.listen_together_log_event_host_should_not_suggest_tracks)
                return false
            }
            if (_connectionState.value != ConnectionState.CONNECTED) return false
            val sent = sendMessage(MessageTypes.SUGGEST_TRACK, SuggestTrackPayload(trackInfo))
            if (sent) Toaster.i(R.string.listen_together_suggestion_sent)
            return sent
        }

        /**
         * Approve a suggestion (host only)
         */
        fun approveSuggestion(suggestionId: String) {
            if (_role.value != RoomRole.HOST) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_cannot_approve_suggestion, logDetail(R.string.listen_together_log_detail_not_host))
                return
            }
            sendMessage(MessageTypes.APPROVE_SUGGESTION, ApproveSuggestionPayload(suggestionId))
            // Remove locally from pending list
            _pendingSuggestions.value = _pendingSuggestions.value.filter { it.suggestionId != suggestionId }

            roomNotifications.cancelSuggestion(suggestionId)
        }

        /**
         * Reject a suggestion (host only)
         */
        fun rejectSuggestion(
            suggestionId: String,
            reason: String? = null,
        ) {
            if (_role.value != RoomRole.HOST) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_cannot_reject_suggestion, logDetail(R.string.listen_together_log_detail_not_host))
                return
            }
            sendMessage(MessageTypes.REJECT_SUGGESTION, RejectSuggestionPayload(suggestionId, reason))
            _pendingSuggestions.value = _pendingSuggestions.value.filter { it.suggestionId != suggestionId }

            roomNotifications.cancelSuggestion(suggestionId)
        }

        /**
         * Request current playback state from server (for guest re-sync)
         */
        fun requestSync() {
            if (_roomState.value == null) {
                log(LogLevel.ERROR, R.string.listen_together_log_event_cannot_request_sync, logDetail(R.string.listen_together_log_detail_not_in_room))
                return
            }
            log(LogLevel.INFO, R.string.listen_together_log_event_requesting_sync_state_from_server)
            sendMessageNoPayload(MessageTypes.REQUEST_SYNC)
        }

        /**
         * Block a user permanently (internal list). Prevents their join requests and suggestions from appearing.
         */
        fun blockUser(username: String) {
            val updated = _blockedUsernames.value.toMutableSet()
            updated.add(username)
            _blockedUsernames.value = updated

            // Filter out blocked users from pending requests and suggestions
            _pendingJoinRequests.value =
                _pendingJoinRequests.value
                    .filter { it.username !in _blockedUsernames.value }
            _pendingSuggestions.value =
                _pendingSuggestions.value
                    .filter { it.fromUsername !in _blockedUsernames.value }

            // Save to storage
            scope.launch {
                saveBlockedUsernames()
            }

            log(LogLevel.INFO, R.string.listen_together_log_event_user_blocked, username)
        }

        /**
         * Unblock a previously blocked user
         */
        fun unblockUser(username: String) {
            val updated = _blockedUsernames.value.toMutableSet()
            updated.remove(username)
            _blockedUsernames.value = updated

            // Save to storage
            scope.launch {
                saveBlockedUsernames()
            }

            log(LogLevel.INFO, R.string.listen_together_log_event_user_unblocked, username)
        }

        /**
         * Check if a user is blocked
         */
        fun isUserBlocked(username: String): Boolean = username in _blockedUsernames.value

        /**
         * Check if currently in a room
         */
        val isInRoom: Boolean
            get() = _roomState.value != null

        /**
         * Check if current user is host
         */
        val isHost: Boolean
            get() = _role.value == RoomRole.HOST

        /**
         * Force reconnection to server (useful for manual recovery)
         */
        @Synchronized
        fun forceReconnect() {
            log(LogLevel.INFO, R.string.listen_together_log_event_forcing_reconnection_to_server)
            reconnectAttempts = 0 // Reset attempts to retry from start
            pingJob?.cancel()
            pingJob = null

            if (webSocket != null) {
                try {
                    clearConnection()?.close(1000, "Forcing reconnection")
                } catch (e: Exception) {
                    log(LogLevel.DEBUG, R.string.listen_together_log_event_error_closing_websocket, e.message)
                }
            }

            _connectionState.value = ConnectionState.DISCONNECTED

            // Attempt connection with reset backoff
            scope.launch {
                delay(500)
                connect()
            }
        }

        /**
         * Check if there's a persisted session available for recovery
         */
        val hasPersistedSession: Boolean
            get() = sessionCredential != null

        /**
         * Get the persisted room code if available
         */
        fun getPersistedRoomCode(): String? = storedRoomCode

        /**
         * Get current session age in milliseconds
         */
        fun getSessionAge(): Long =
            if (sessionStartTime > 0) {
                System.currentTimeMillis() - sessionStartTime
            } else {
                0L
            }
    }
