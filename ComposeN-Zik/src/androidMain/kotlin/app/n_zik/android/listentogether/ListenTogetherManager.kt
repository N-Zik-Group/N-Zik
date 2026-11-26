// Ported from Metrolist (GPL-3.0, https://github.com/MetrolistGroup/Metrolist).
// The guestNeedsTrackReconcile predicate rejects null targets before guarded assertions.
package app.n_zik.android.listentogether

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

import app.n_zik.android.R
import app.n_zik.android.appTextResources
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.coroutines.NzikDispatchers

private const val ACTIVE_PLAYBACK_SYNC_TOLERANCE_MS = 2_000L

internal fun shouldSeekDuringActivePlayback(
    positionDifferenceMs: Long,
    playbackReady: Boolean,
): Boolean = playbackReady && positionDifferenceMs > ACTIVE_PLAYBACK_SYNC_TOLERANCE_MS

/**
 * Manager that bridges the Listen Together WebSocket client with the music player.
 * Handles syncing playback actions between connected users.
 *
 * Instantiated with [ListenTogetherClient] + application context (no DI framework in N-Zik);
 * see [app.n_zik.android.MainApplication].
 */
class ListenTogetherManager(
    private val client: ListenTogetherClient,
    private val context: Context,
) {
    companion object {
        private const val TAG = "ListenTogetherManager"

        /** Persisted "Sync volume" setting key (guests apply the host's volume); defaults ON. */
        const val PREF_SYNC_HOST_VOLUME = "listen_together_sync_volume"

        /**
         * Single instance (Metrolist `getInstance`/`setInstance` pattern — N-Zik has no DI
         * framework). Created in [app.n_zik.android.MainApplication]; read from the room
         * screen and the settings card.
         */
        private var instance: ListenTogetherManager? = null

        fun getInstance(): ListenTogetherManager? = instance

        internal fun setInstance(manager: ListenTogetherManager) {
            instance = manager
        }
    }

    init {
        setInstance(this)
    }

    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)

    private var eventCollectorJob: Job? = null
    private var guestLockJob: Job? = null

    /**
     * Host volume sync toggle (settings card + room menu); guests apply the host volume
     * only when enabled. Seeded from the persisted preference (default ON = pre-fix
     * behavior) so the user's choice survives an app restart, and written back on every
     * change through [setSyncHostVolumeEnabled] — both UI call sites go through it.
     */
    val syncHostVolumeEnabled =
        MutableStateFlow(readSyncHostVolumePreference())

    internal fun refreshSyncHostVolumePreference() {
        syncHostVolumeEnabled.value = readSyncHostVolumePreference()
    }

    private fun readSyncHostVolumePreference(): Boolean =
        DataStoreUtils.getBoolean(context, PREF_SYNC_HOST_VOLUME, true)

    fun setSyncHostVolumeEnabled(enabled: Boolean) {
        syncHostVolumeEnabled.value = enabled
        DataStoreUtils.saveBoolean(context, PREF_SYNC_HOST_VOLUME, enabled)
        Timber.tag(TAG).i("Sync host volume -> $enabled")
    }

    private var lastRole: RoomRole = RoomRole.NONE

    // Whether we're currently syncing (to prevent feedback loops)
    @Volatile
    private var isSyncing = false

    /**
     * The guest's local play/pause choice — `true` when the guest chose PAUSE (their own tap),
     * fed exclusively by the guarded facade ([ListenTogetherGuestGuardPlayer.onGuestPlayPause] →
     * [guestPlayPause]): host sync and service internals mutate the raw player directly and
     * never set this flag (spec-listen-together-guest-lock-hardening, "pause is kept across
     * host skips"). While set, host PLAY / track changes sync the guest's position but must
     * not resume the guest; the flag clears when the guest presses play or the room ends.
     */
    @Volatile
    private var guestLocalPause = false

    // Track last sync action time for debouncing (prevents excessive seeking/pausing)
    private var lastSyncActionTime: Long = 0L

    // Track ID being buffered
    private var bufferingTrackId: String? = null

    // Track active sync job to cancel it if a better update arrives
    private var activeSyncJob: Job? = null
    private val driftCorrection = ListenTogetherDriftCorrection(
        scope = scope,
        client = client,
        isHost = { isHost },
        localTrackId = ::localTrackId,
    )

    private var lastAppliedRevision = 0L
    private val guestQueue = ListenTogetherGuestQueue(scope, TAG)
    private var applyNextHostSnapshot = false

    // Generation ID for track changes - incremented on each new track change
    // Used to prevent old coroutines from overwriting newer track loads
    private var currentTrackGeneration: Int = 0

    // Pending sync to apply after buffering completes for guest
    private var pendingSyncState: SyncStatePayload? = null

    // Track if a buffer-complete arrived before the pending sync was ready
    private var bufferCompleteReceivedForTrack: String? = null

    // Expose client state
    val connectionState: StateFlow<ConnectionState> = client.connectionState
    val roomState: StateFlow<RoomState?> = client.roomState
    val role: StateFlow<RoomRole> = client.role
    val userId: StateFlow<String?> = client.userId
    val pendingJoinRequests: StateFlow<List<JoinRequestPayload>> = client.pendingJoinRequests
    val bufferingUsers: StateFlow<List<String>> = client.bufferingUsers
    val logs: StateFlow<List<LogEntry>> = client.logs
    val events = client.events
    val blockedUsernames: StateFlow<Set<String>> = client.blockedUsernames
    val pendingSuggestions: StateFlow<List<SuggestionReceivedPayload>> = client.pendingSuggestions

    val isInRoom: Boolean get() = client.isInRoom
    val isHost: Boolean get() = client.isHost
    val hasPersistedSession: Boolean get() = client.hasPersistedSession

    /** Local player track id (video-id normalized), or null when nothing is loaded. */
    private fun localTrackId(): String? =
        ListenTogetherPlayerBridge.player?.currentMediaItem?.mediaId
            ?.let { id -> id.split("/").lastOrNull() ?: id }

    private val hostPlayback = ListenTogetherHostPlayback(
        scope = scope,
        client = client,
        isSyncing = { isSyncing },
        localTrackId = ::localTrackId,
        syncHostVolumeEnabled = { syncHostVolumeEnabled.value },
    )
    /**
     * Initialize event collection. Should be called once at app start (main thread).
     * Binds the player bridge, wires up event/role/guest-lock collectors, and
     * auto-reconnects to a fresh persisted session once loading completes.
     */
    fun initialize() {
        Timber.tag(TAG).d("Initializing ListenTogetherManager")

        // Bind the shared player service (the manager drives playback only through the bridge)
        ListenTogetherPlayerBridge.ensureBound(context)

        eventCollectorJob?.cancel()
        eventCollectorJob =
            scope.launch {
                client.events.collect { event ->
                    try {
                        Timber.tag(TAG).d("Received event: $event")
                        handleEvent(event)
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error handling event: $event")
                    }
                }
            }

        // Role change listener
        scope.launch {
            role.collect { newRole ->
                try {
                    val previousRole = lastRole
                    lastRole = newRole

                    val wasHost = previousRole == RoomRole.HOST
                    // The local-pause intent is scoped to one guest stint: an in-room role
                    // change (host handover) ends it, so a stale flag cannot keep a new guest
//
                    // sync-paused across the handover.
                    if (isInRoom && newRole != previousRole) guestLocalPause = false
                    if (newRole == RoomRole.HOST && !wasHost) {
                        if (isInRoom) {
                            Timber.tag(TAG).d("Role changed to HOST, starting sync services")
                            hostPlayback.startQueueSyncObservation()
                            hostPlayback.startHeartbeat()
                            hostPlayback.registerListener("Failed to add player listener on role change")
                        }
                    } else if (newRole != RoomRole.HOST && wasHost) {
                        Timber.tag(TAG).d("Role changed from HOST, stopping sync services")
                        hostPlayback.stopQueueSyncObservation()
                        hostPlayback.stopHeartbeat()
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error in role change handler")
                }
            }
        }

        // Guest lock for the player UI: guests never change playback
        // (predicate centralized in [isGuestLockedForPlayback], tested in
//
        // GuestLockForPlaybackTest)
        guestLockJob?.cancel()
        guestLockJob =
            scope.launch {
                combine(role, roomState) { roomRole, state ->
                    isGuestLockedForPlayback(state != null, roomRole == RoomRole.HOST)
                }
                    .distinctUntilChanged()
                    .collect { locked ->
                        listenTogetherGuestLock.value = locked
                    }
            }

        // Auto-reconnect to a fresh persisted session (~10 min grace window) once the
        // client finishes loading disk state; otherwise the client sits idle until the
//
        // user opens the room screen.
        scope.launch {
            client.persistedSessionLoaded.collect { loaded ->
                if (!loaded) return@collect
                if (client.hasPersistedSession && !client.isInRoom) {
                    Timber.tag(TAG).d("Auto-reconnecting to persisted session")
                    client.connect()
                }
            }
        }
    }

    private fun handleEvent(event: ListenTogetherEvent) {
        when (event) {
            is ListenTogetherEvent.Connected -> {
                Timber.tag(TAG).d("Connected to server with userId: ${event.userId}")
            }

            is ListenTogetherEvent.RoomCreated -> {
                Timber.tag(TAG).d("Room created: ${event.roomCode}")
                try {
                    hostPlayback.registerListener(
                        failureMessage = "Failed to add player listener on room create",
                        successMessage = "Added player listener as host",
                    )
                    hostPlayback.publishRoomCreated()
                    hostPlayback.startQueueSyncObservation()
                    hostPlayback.startHeartbeat()
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error handling RoomCreated event")
                }
            }

            is ListenTogetherEvent.JoinApproved -> {
                Timber.tag(TAG).d("Join approved for room: ${event.roomCode}")
                applyJoinedRoomState(event.state, bypassBuffer = false)
            }

            is ListenTogetherEvent.PlaybackSync -> {
                Timber.tag(TAG).d("PlaybackSync received: ${event.action.action}")
                // Guests handle all sync actions. Host should also apply queue ops.
                val actionType = event.action.action
                val isQueueOp =
                    actionType == PlaybackActions.QUEUE_ADD ||
                        actionType == PlaybackActions.QUEUE_REMOVE ||
                        actionType == PlaybackActions.QUEUE_CLEAR
                if (!isHost || isQueueOp) {
                    handlePlaybackSync(event.action)
                }
            }

            is ListenTogetherEvent.UserJoined -> {
                Timber.tag(TAG).d("[SYNC] User joined: ${event.username}")
            }

            is ListenTogetherEvent.BufferWait -> {
                Timber.tag(TAG).d("BufferWait: waiting for ${event.waitingFor.size} users")
            }

            is ListenTogetherEvent.BufferComplete -> {
                Timber.tag(TAG).d("BufferComplete for track: ${event.trackId}")
                if (!isHost && bufferingTrackId == event.trackId) {
                    bufferCompleteReceivedForTrack = event.trackId
                    applyPendingSyncIfReady()
                }
            }

            is ListenTogetherEvent.SyncStateReceived -> {
                Timber
                    .tag(
                        TAG,
                    ).d(
                        "SyncStateReceived: playing=${event.state.isPlaying}, pos=${event.state.position}, track=${event.state.currentTrackOrNull?.id}",
                    )
                if (!isHost || applyNextHostSnapshot) {
                    val forceFullState = applyNextHostSnapshot
                    applyNextHostSnapshot = false
                    handleSyncState(event.state, forceFullState)
                }
            }

            is ListenTogetherEvent.Kicked -> {
                Timber.tag(TAG).d("Kicked from room: ${event.reason}")
                val reason = event.reason.trim().takeUnless { it.isEmpty() || it == "Removed by host" }?.let {
                    if (it == LISTEN_TOGETHER_BLOCKED_REASON) {
                        appTextResources().getString(R.string.listen_together_user_blocked_by_host)
                    } else {
                        it
                    }
                }
                if (reason != null) {
                    Toaster.e(R.string.listen_together_kicked_reason, reason)
                } else {
                    Toaster.e(R.string.listen_together_kicked)
                }
                cleanup()
            }

            is ListenTogetherEvent.Disconnected -> {
                Timber.tag(TAG).d("Disconnected from server")
                // Don't cleanup on disconnect - we might reconnect
                // cleanup() is called when leaving room intentionally or when kicked
            }

            is ListenTogetherEvent.Reconnecting -> {
                Timber.tag(TAG).d("Reconnecting: attempt ${event.attempt}/${event.maxAttempts}")
            }

            is ListenTogetherEvent.Reconnected -> {
                Timber.tag(TAG).d("Reconnected to room: ${event.roomCode}, isHost: ${event.isHost}")
                try {
                    hostPlayback.registerListener(
                        failureMessage = "Failed to re-add player listener after reconnect",
                        successMessage = "Re-added player listener after reconnect",
                    )
                    if (event.isHost) {
                        hostPlayback.publishReconnectedHost(event.state)
                    } else {
                        // Guest: ALWAYS sync to host's state after reconnection
                        Timber.tag(TAG).d("Reconnected as guest, syncing to host's current state")
                        applyJoinedRoomState(event.state, bypassBuffer = true)
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error handling Reconnected event")
                }
                // Recovery feedback for mid-session drops (silent reconnect is otherwise invisible
                // when the user is on the player screen)
                Toaster.i(R.string.listen_together_reconnected)
            }

            is ListenTogetherEvent.UserReconnected -> {
                Timber.tag(TAG).d("User reconnected: ${event.username}")
                // No action needed - reconnected user already synced via reconnect state
            }

            is ListenTogetherEvent.UserDisconnected -> {
                Timber.tag(TAG).d("User temporarily disconnected: ${event.username}")
                // User might reconnect, no action needed
            }

            is ListenTogetherEvent.HostChanged -> {
                Timber.tag(TAG).d("Host changed: new host is ${event.newHostName} (${event.newHostId})")
                val wasHost = isHost
                val nowIsHost = event.newHostId == userId.value

                if (wasHost && !nowIsHost) {
                    // Lost host role
                    Timber.tag(TAG).d("Local user lost host role")
                    hostPlayback.stopQueueSyncObservation()
                    hostPlayback.stopHeartbeat()
                    hostPlayback.unregisterListener()
                } else if (!wasHost && nowIsHost) {
                    // Gained host role
                    Timber.tag(TAG).d("Local user gained host role")

                    hostPlayback.registerListener(
                        failureMessage = "Failed to add player listener on host transfer",
                        successMessage = "Added player listener as new host",
                    )
                    hostPlayback.startQueueSyncObservation()
                    hostPlayback.startHeartbeat()
                    hostPlayback.publishTransferredHost()
                }
                // Make the role change visible even when the user is not on the room screen
                if (nowIsHost) {
                    Toaster.i(R.string.listen_together_you_are_host)
                } else {
                    Toaster.i(R.string.listen_together_new_host, event.newHostName)
                }
            }

            is ListenTogetherEvent.SuggestionApproved -> {
                Toaster.i(R.string.listen_together_suggestion_approved)
            }

            is ListenTogetherEvent.SuggestionRejected -> {
                val reason = event.reason.trim().takeUnless { it.isEmpty() || it == "Rejected by host" }
                if (reason != null) {
                    Toaster.i(R.string.listen_together_suggestion_rejected_reason, reason)
                } else {
                    Toaster.i(R.string.listen_together_suggestion_rejected)
                }
            }

            is ListenTogetherEvent.ConnectionError -> {
                Timber.tag(TAG).e("Connection error: ${event.error}")
                // The screen only toasts connection failures while a create/join is in
                // flight; a mid-session drop (user on the player screen) needs feedback too.
                val wasInRoom = isInRoom
                cleanup()
                if (wasInRoom) Toaster.e(R.string.listen_together_connection_lost)
            }

            else -> { /* Other events handled by UI */ }
        }
    }

    private fun cleanup() {
        listenTogetherGuestLock.value = false
        guestLocalPause = false
        hostPlayback.unregisterListener()
        hostPlayback.stopQueueSyncObservation()
        hostPlayback.stopHeartbeat()
        driftCorrection.cancel()
        hostPlayback.resetPublishedTrack()
        bufferingTrackId = null
        isSyncing = false
        bufferCompleteReceivedForTrack = null
        lastRole = RoomRole.NONE
        lastSyncActionTime = 0L // Reset sync debouncing
        lastAppliedRevision = 0L
        applyNextHostSnapshot = false
        guestQueue.invalidate()
        ++currentTrackGeneration // Increment to invalidate any pending track-change coroutines
    }

    private fun applyHostVolumeIfNeeded(volume: Float?) {
        if (!syncHostVolumeEnabled.value || isHost || !isInRoom) return
        val target = volume?.coerceIn(0f, 1f) ?: return
        // N-Zik has no service-level volume flow; set it directly on the ExoPlayer.
        ListenTogetherPlayerBridge.player?.volume = target
    }

    @UnstableApi
    private fun applyCanonicalUpcomingQueue(
        queue: List<TrackInfo>,
    ) {
        guestQueue.invalidate()
        val player = ListenTogetherPlayerBridge.player ?: return

        val state = roomState.value
        val currentTrack = state?.currentTrackOrNull
        if (currentTrack != null && localTrackId() != currentTrack.id) {
            applyPlaybackState(
                currentTrack = currentTrack,
                isPlaying = state.isPlaying,
                position = state.position,
                queue = queue,
                effectiveAtServerTime = state.lastUpdate,
                bypassBuffer = true,
            )
            return
        }

        guestQueue.replaceUpcoming(player, currentTrack, queue)
    }

    private fun applyPendingSyncIfReady() {
        val pending = pendingSyncState ?: return
        val pendingTrackId = pending.currentTrackOrNull?.id ?: bufferingTrackId ?: return
        val completeForTrack = bufferCompleteReceivedForTrack

        if (completeForTrack != pendingTrackId) return

        val player = ListenTogetherPlayerBridge.player ?: return

        Timber.tag(TAG).d("Applying pending sync: track=$pendingTrackId, pos=${pending.position}, play=${pending.isPlaying}")
        isSyncing = true

        val targetPos =
            client.positionAtServerTime(
                pending.position,
                pending.lastUpdate.takeIf { it > 0L },
                pending.isPlaying,
            )
        val posDiff = kotlin.math.abs(player.currentPosition - targetPos)
        // A local guest pause that landed after the pending state was captured (during the
        // buffer wait) must not be resurrected by the stale host isPlaying — the local pause
//
        // survives the buffer completion (audit finding, spec-listen-together-guest-lock-hardening).
        val willPlay = pending.isPlaying && !guestLocalPause

        if (posDiff > SOFT_SYNC_THRESHOLD_MS) {
            Timber
                .tag(
                    TAG,
                ).d("Applying pending sync: seeking ${player.currentPosition} -> $targetPos (diff ${posDiff}ms)")
            ListenTogetherPlayerBridge.seekTo(targetPos)
        }

        // Apply play/pause state only if it needs to change
        if (willPlay && !player.playWhenReady) {
            Timber.tag(TAG).d("Applying pending sync: starting playback")
            ListenTogetherPlayerBridge.play()
        } else if (!willPlay && player.playWhenReady) {
            Timber.tag(TAG).d("Applying pending sync: pausing playback")
            ListenTogetherPlayerBridge.pause()
        }
        if (willPlay) {
            driftCorrection.start(pendingTrackId, pending.position, pending.lastUpdate.takeIf { it > 0L })
        } else {
            driftCorrection.cancel()
        }

        scope.launch {
            delay(200)
            isSyncing = false
        }

        bufferingTrackId = null
        pendingSyncState = null
        bufferCompleteReceivedForTrack = null
    }

    /**
     * True when the host attached a [PlaybackActionPayload.trackId] and the local player
     * is not on that item (or has no item). Prevents PLAY/PAUSE/SEEK from adjusting the wrong song.
     */
    private fun guestNeedsTrackReconcile(
        actionTrackId: String?,
        localMediaId: String?,
    ): Boolean {
        val expected = actionTrackId?.takeIf { it.isNotEmpty() } ?: return false
        return localMediaId == null || localMediaId != expected
    }

    /**
     * Reload guest playback from [roomState] when it matches [expectedTrackId], else ask the server.
     */
    private fun reconcileGuestToHostTrack(
        expectedTrackId: String,
        wantPlaying: Boolean,
        positionMs: Long,
    ) {
        val snapshot = roomState.value
        val serverTrack = snapshot?.currentTrackOrNull
        val queue = snapshot?.queue?.takeIf { it.isNotEmpty() }
        if (serverTrack?.id == expectedTrackId) {
            Timber
                .tag(TAG)
                .w("Guest: wrong local track — reloading ${serverTrack.title} from room state")
            applyPlaybackState(
                currentTrack = serverTrack,
                isPlaying = wantPlaying,
                position = positionMs,
                queue = queue,
                bypassBuffer = true,
            )
        } else {
            Timber
                .tag(TAG)
                .w(
                    "Guest: track mismatch (PLAY/PAUSE expected id=$expectedTrackId, room has ${serverTrack?.id}) — requestSync",
                )
            client.requestSync()
        }
    }

    @UnstableApi
    private fun handlePlaybackSync(action: PlaybackActionPayload) {
        if (action.revision > 0L && action.revision < lastAppliedRevision) {
            Timber.tag(TAG).d("Ignoring stale playback revision ${action.revision} < $lastAppliedRevision")
            return
        }
        lastAppliedRevision = maxOf(lastAppliedRevision, action.revision)
        val player = ListenTogetherPlayerBridge.player
        if (player == null) {
            Timber.tag(TAG).w("Cannot sync playback - no player")
            return
        }
        val actionPosition = action.positionOrNull
        val actionTrackId = action.trackIdOrNull
        val actionTrackInfo = action.trackInfoOrNull
        val actionServerTime = action.serverTimeOrNull

        Timber.tag(TAG).d("Handling playback sync: ${action.action}, position: $actionPosition")

        isSyncing = true

        try {
            when (action.action) {
                PlaybackActions.PLAY -> {
                    val basePos = actionPosition ?: 0L
                    val now = SystemClock.elapsedRealtime()
                    val adjustedPos = client.positionAtServerTime(basePos, actionServerTime, isPlaying = true)

                    Timber.tag(TAG).d("Guest: PLAY at position $adjustedPos, currently playing=${player.playWhenReady}")

                    val playTarget = actionTrackId
                    if (bufferingTrackId != null &&
                        playTarget != null &&
                        playTarget != bufferingTrackId
                    ) {
                        Timber
                            .tag(TAG)
                            .w("Guest: PLAY targets $playTarget but was buffering $bufferingTrackId — switching")
                        pendingSyncState = null
                        bufferCompleteReceivedForTrack = null
                        bufferingTrackId = null
                        reconcileGuestToHostTrack(
                            playTarget,
                            // Locally-paused guest: load the host track but stay paused (the guest's
                            // play tap will resync + resume).
                            wantPlaying = !guestLocalPause,
                            positionMs = adjustedPos,
                        )
                        lastSyncActionTime = now
                        return
                    }

                    if (bufferingTrackId != null) {
                        pendingSyncState =
                            (
                                pendingSyncState ?: SyncStatePayload(
                                    currentTrack = roomState.value?.currentTrackOrNull,
                                    isPlaying = !guestLocalPause,
                                    position = basePos,
                                    lastUpdate = actionServerTime ?: 0L,
                                    revision = action.revision,
                                )
                            ).copy(
                                isPlaying = !guestLocalPause,
                                position = basePos,
                                lastUpdate = actionServerTime ?: 0L,
                                revision = action.revision,
                            )
                        applyPendingSyncIfReady()
                        return
                    }

                    if (guestNeedsTrackReconcile(playTarget, localTrackId())) {
                        reconcileGuestToHostTrack(
                            playTarget!!,
                            wantPlaying = !guestLocalPause,
                            positionMs = adjustedPos,
                        )
                        lastSyncActionTime = now
                        return
                    }

                    val posDiff = kotlin.math.abs(player.currentPosition - adjustedPos)
                    val alreadyPlaying = player.playWhenReady
                    if (alreadyPlaying) {
                        // PLAY is also the host heartbeat, so ordinary drift must not interrupt playback.
                        if (shouldSeekDuringActivePlayback(posDiff, player.playbackState == Player.STATE_READY)) {
                            driftCorrection.cancel()
                            Timber.tag(TAG).d("Guest: hard sync ${player.currentPosition} -> $adjustedPos (diff ${posDiff}ms)")
                            ListenTogetherPlayerBridge.seekTo(adjustedPos)
                        }
                    } else {
                        driftCorrection.cancel()
                        if (posDiff > SOFT_SYNC_THRESHOLD_MS) {
                            ListenTogetherPlayerBridge.seekTo(adjustedPos)
                        }
                        if (guestLocalPause) {
                            // Locally-paused guest: the host's PLAY (skip / resume / heartbeat) must
                            // not resume them — the position is synced above so the guest's play
//
                            // tap resumes at the host position (spec-listen-together-guest-lock-hardening).
                            Timber.tag(TAG).d("Guest: keeping local pause across host PLAY (position synced only)")
                        } else {
                            ListenTogetherPlayerBridge.play()
                        }
                    }
                    lastSyncActionTime = now
                }

                PlaybackActions.PAUSE -> {
                    val pos = actionPosition ?: 0L
                    val now = SystemClock.elapsedRealtime()
                    driftCorrection.cancel()

                    Timber.tag(TAG).d("Guest: PAUSE at position $pos, currently playing=${player.playWhenReady}")

                    val pauseTarget = actionTrackId
                    if (bufferingTrackId != null &&
                        pauseTarget != null &&
                        pauseTarget != bufferingTrackId
                    ) {
                        pendingSyncState = null
                        bufferCompleteReceivedForTrack = null
                        bufferingTrackId = null
                        reconcileGuestToHostTrack(pauseTarget, wantPlaying = false, positionMs = pos)
                        lastSyncActionTime = now
                        return
                    }

                    if (bufferingTrackId != null) {
                        pendingSyncState =
                            (
                                pendingSyncState ?: SyncStatePayload(
                                    currentTrack = roomState.value?.currentTrackOrNull,
                                    isPlaying = false,
                                    position = pos,
                                    lastUpdate = actionServerTime ?: 0L,
                                    revision = action.revision,
                                )
                            ).copy(
                                isPlaying = false,
                                position = pos,
                                lastUpdate = actionServerTime ?: 0L,
                                revision = action.revision,
                            )
                        applyPendingSyncIfReady()
                        return
                    }

                    if (guestNeedsTrackReconcile(pauseTarget, localTrackId())) {
                        reconcileGuestToHostTrack(pauseTarget!!, wantPlaying = false, positionMs = pos)
                        lastSyncActionTime = now
                        return
                    }

                    val posDiff = kotlin.math.abs(player.currentPosition - pos)
                    if (player.playWhenReady) {
                        Timber.tag(TAG).d("Guest: Pausing playback")
                        ListenTogetherPlayerBridge.pause()
                    }

                    if (posDiff > SOFT_SYNC_THRESHOLD_MS) {
                        Timber.tag(TAG).d("Guest: PAUSE seeking ${player.currentPosition} -> $pos (diff ${posDiff}ms)")
                        ListenTogetherPlayerBridge.seekTo(pos)
                    }
                    lastSyncActionTime = now
                }

                PlaybackActions.SEEK -> {
                    val pos = actionPosition ?: 0L
                    val now = SystemClock.elapsedRealtime()
                    val playing = roomState.value?.isPlaying == true
                    val adjustedPos = client.positionAtServerTime(pos, actionServerTime, playing)

                    val seekTarget = actionTrackId
                    if (bufferingTrackId != null && (seekTarget == null || seekTarget == bufferingTrackId)) {
                        pendingSyncState =
                            (pendingSyncState ?: SyncStatePayload(
                                currentTrack = roomState.value?.currentTrackOrNull,
                                // Locally-paused guest stays paused (same override as the
                                // handleSyncState buffering branch and the PLAY branch).
                                isPlaying = playing && !guestLocalPause,
                                position = pos,
                                lastUpdate = actionServerTime ?: 0L,
                                revision = action.revision,
                            )).copy(
                                position = pos,
                                lastUpdate = actionServerTime ?: 0L,
                                revision = action.revision,
                            )
                        return
                    }
                    if (guestNeedsTrackReconcile(seekTarget, localTrackId())) {
                        if (seekTarget != null) {
                            reconcileGuestToHostTrack(
                                seekTarget,
                                wantPlaying = player.playWhenReady,
                                positionMs = adjustedPos,
                            )
                        } else {
                            client.requestSync()
                        }
                        lastSyncActionTime = now
                        return
                    }

                    driftCorrection.cancel()
                    if (kotlin.math.abs(player.currentPosition - adjustedPos) > SOFT_SYNC_THRESHOLD_MS) {
                        ListenTogetherPlayerBridge.seekTo(adjustedPos)
                    }
                    if (playing) {
                        driftCorrection.start(seekTarget, pos, actionServerTime)
                    }
                    lastSyncActionTime = now
                }

                PlaybackActions.CHANGE_TRACK -> {
                    driftCorrection.cancel()
                    actionTrackInfo?.let { track ->
                        Timber.tag(TAG).d("Guest: CHANGE_TRACK to ${track.title}, queue size=${action.queue?.size}")

                        // Reset sync debounce timer on track change - this is a fresh sync cycle
                        lastSyncActionTime = 0L

                        // If we have a queue, use it! This is the "smart" sync path.
                        if (action.revision > 0L || !action.queue.isNullOrEmpty()) {
                            applyPlaybackState(
                                currentTrack = track,
                                isPlaying = false, // Will be updated by subsequent PLAY or pending sync
                                position = 0,
                                queue = action.queue.orEmpty(),
                            )
                        } else {
                            // Fallback (no queue provided): load the single track
                            bufferingTrackId = track.id
                            syncToTrack(track, false, 0)
                        }
                    }
                }

                PlaybackActions.SKIP_NEXT -> {
                    Timber.tag(TAG).d("Guest: SKIP_NEXT")
                    ListenTogetherPlayerBridge.seekToNext()
                }

                PlaybackActions.SKIP_PREV -> {
                    Timber.tag(TAG).d("Guest: SKIP_PREV")
                    ListenTogetherPlayerBridge.seekToPrevious()
                }

                PlaybackActions.QUEUE_ADD -> {
                    val track = actionTrackInfo
                    if (action.revision > 0L) {
                        applyCanonicalUpcomingQueue(action.queue.orEmpty())
                    } else if (track == null) {
                        Timber.tag(TAG).w("QUEUE_ADD missing trackInfo")
                    } else {
                        Timber.tag(TAG).d("Guest: QUEUE_ADD ${track.title}, insertNext=${action.insertNext == true}")
                        guestQueue.addLegacy(track, action.insertNext == true)
                    }
                }

                PlaybackActions.QUEUE_REMOVE -> {
                    val removeId = actionTrackId
                    if (action.revision > 0L) {
                        applyCanonicalUpcomingQueue(action.queue.orEmpty())
                    } else if (removeId.isNullOrEmpty()) {
                        Timber.tag(TAG).w("QUEUE_REMOVE missing trackId")
                    } else {
                        guestQueue.removeLegacy(removeId)
                    }
                }

                PlaybackActions.QUEUE_CLEAR -> {
                    if (action.revision > 0L) {
                        applyCanonicalUpcomingQueue(action.queue.orEmpty())
                    } else {
                        guestQueue.clearLegacy()
                    }
                }

                PlaybackActions.SET_VOLUME -> {
                    applyHostVolumeIfNeeded(action.volumeOrNull)
                }

                PlaybackActions.SYNC_QUEUE -> {
                    guestQueue.invalidate()
                    val queue = action.queue
                    if (queue != null) {
                        Timber.tag(TAG).d("Guest: SYNC_QUEUE size=${queue.size}")
                        if (queue.isEmpty()) {
                            // The host cleaned its player (mini-player dismiss): mirror the clean on
                            // the guest — clear the queue and pause, so the guest's mini-player
//
                            // disappears too (spec-listen-together-guest-lock-hardening).
                            Timber.tag(TAG).d("Guest: SYNC_QUEUE empty — host cleaned the player, clearing + pausing")
                            activeSyncJob?.cancel()
                            // Reset the sync state machine exactly like cleanup() and every other
                            // transition (review finding): without this, a stale pending sync /
//
                            // buffer-complete flag would let applyPendingSyncIfReady fire early
                            // with the pre-clean position if the host restarts the same track.
                            pendingSyncState = null
                            bufferingTrackId = null
                            bufferCompleteReceivedForTrack = null
                            driftCorrection.cancel()
                            ListenTogetherPlayerBridge.allowInternalSync = true
                            try {
                                ListenTogetherPlayerBridge.clearMediaItemsForSync()
                                ListenTogetherPlayerBridge.pause()
                            } finally {
                                ListenTogetherPlayerBridge.allowInternalSync = false
                            }
                            return
                        }
                        if (action.revision > 0L) {
                            applyCanonicalUpcomingQueue(queue)
                            return
                        }
                        // Cancel any pending "smart" sync in favor of this authoritative queue
                        activeSyncJob?.cancel()

                        val p = ListenTogetherPlayerBridge.player ?: return

                        val mediaItems =
                            queue.map { track ->
                                ListenTogetherPlayerBridge.trackToMediaItem(track)
                            }

                        val currentId = localTrackId()
                        var newIndex = -1
                        if (currentId != null) {
                            newIndex = mediaItems.indexOfFirst { it.mediaId == currentId }
                        }

                        val currentPos = p.currentPosition
                        val wasPlaying = p.isPlaying

                        ListenTogetherPlayerBridge.allowInternalSync = true
                        try {
                            if (newIndex != -1) {
                                p.setMediaItems(mediaItems, newIndex, currentPos)
                            } else {
                                val hostCurrentId = roomState.value?.currentTrackOrNull?.id
                                val hostIdx =
                                    if (hostCurrentId != null) {
                                        mediaItems.indexOfFirst { it.mediaId == hostCurrentId }
                                    } else {
                                        -1
                                    }
                                val hostPos = roomState.value?.position?.coerceAtLeast(0L) ?: 0L
                                if (hostIdx >= 0) {
                                    Timber
                                        .tag(TAG)
                                        .d("SYNC_QUEUE: aligning to host current index=$hostIdx from room state")
                                    p.setMediaItems(mediaItems, hostIdx, hostPos)
                                } else {
                                    Timber
                                        .tag(TAG)
                                        .w("SYNC_QUEUE: host current track not in queue; defaulting to start")
                                    p.setMediaItems(mediaItems, 0, 0L)
                                }
                            }
                        } finally {
                            ListenTogetherPlayerBridge.allowInternalSync = false
                        }

                        if (wasPlaying && !p.isPlaying) {
                            ListenTogetherPlayerBridge.play()
                        }
                    }
                }
            }
        } finally {
            // Minimal delay to prevent feedback loops
            scope.launch {
                delay(200)
                isSyncing = false
            }
        }
    }

    private fun applyJoinedRoomState(state: RoomState, bypassBuffer: Boolean) {
        lastAppliedRevision = state.revision
        applyPlaybackState(
            currentTrack = state.currentTrackOrNull,
            isPlaying = state.isPlaying,
            position = state.position,
            queue = state.queue,
            effectiveAtServerTime = state.lastUpdate,
            bypassBuffer = bypassBuffer,
        )
        applyHostVolumeIfNeeded(state.volume)
    }

    private fun handleSyncState(
        state: SyncStatePayload,
        forceFullState: Boolean = false,
    ) {
        if (state.revision > 0L && state.revision < lastAppliedRevision) {
            Timber.tag(TAG).d("Ignoring stale sync revision ${state.revision} < $lastAppliedRevision")
            return
        }
        lastAppliedRevision = maxOf(lastAppliedRevision, state.revision)
        val currentTrack = state.currentTrackOrNull
        Timber.tag(TAG).d("handleSyncState: playing=${state.isPlaying}, pos=${state.position}, track=${currentTrack?.id}")
        if (!forceFullState && currentTrack != null && bufferingTrackId == currentTrack.id) {
            // A locally-paused guest must not be resumed by the host's streaming state while
            // the new track is still buffering: the raw host `isPlaying` would otherwise
//
            // replace the pending state and `applyPendingSyncIfReady` would play once the
            // buffer completes (user report: "il change de buffer pour charger, mais du coup
//
            // il repart en lecture"). The play/pause decision stays overridden until the
            // guest presses play (spec-listen-together-guest-lock-hardening).
            pendingSyncState = state.copy(isPlaying = state.isPlaying && !guestLocalPause)
            applyPendingSyncIfReady()
            applyHostVolumeIfNeeded(state.volume)
            return
        }
        val localTrackId = localTrackId()
        if (!forceFullState && currentTrack != null && localTrackId == currentTrack.id && bufferingTrackId == null) {
            handlePlaybackSync(
                PlaybackActionPayload(
                    action = if (state.isPlaying) PlaybackActions.PLAY else PlaybackActions.PAUSE,
                    trackId = currentTrack.id,
                    position = state.position,
                    serverTime = state.lastUpdate,
                    revision = state.revision,
                ),
            )
            applyHostVolumeIfNeeded(state.volume)
            return
        }
        applyPlaybackState(
            currentTrack = currentTrack,
            isPlaying = state.isPlaying,
            position = state.position,
            queue = state.queue,
            effectiveAtServerTime = state.lastUpdate,
            bypassBuffer = true, // Manual sync: bypass buffer
        )
        applyHostVolumeIfNeeded(state.volume)
    }

    @UnstableApi
    private fun applyPlaybackState(
        currentTrack: TrackInfo?,
        isPlaying: Boolean,
        position: Long,
        queue: List<TrackInfo>?,
        effectiveAtServerTime: Long? = null,
        bypassBuffer: Boolean = false,
    ) {
        guestQueue.invalidate()
        if (ListenTogetherPlayerBridge.player == null) {
            Timber.tag(TAG).w("Cannot apply playback state - no player")
            return
        }
        driftCorrection.cancel()
        // A locally-paused guest (their own pause tap, tracked in [guestLocalPause]) must stay
        // paused even when the server state says the host is playing: the position is still
//
        // synced, but the guest is never resumed until they press play themselves
        // (spec-listen-together-guest-lock-hardening). The host's real state still drives the
//
        // position projection below — only the play/pause decision is overridden.
        val effectivePlaying = isPlaying && !guestLocalPause
        val initialPosition = client.positionAtServerTime(position, effectiveAtServerTime, isPlaying)

        Timber
            .tag(
                TAG,
            ).d("Applying playback state: track=${currentTrack?.id}, pos=$position, queue=${queue?.size}, bypassBuffer=$bypassBuffer")

        // Cancel any pending sync job
        activeSyncJob?.cancel()

        // If no track, just pause and clear/set queue
        if (currentTrack == null) {
            Timber.tag(TAG).d("No track in state, pausing")
            val generation = ++currentTrackGeneration
            scope.launch {
                // Verify we're still on the same track generation (no newer track change arrived)
                if (currentTrackGeneration != generation) {
                    Timber.tag(TAG).d("Skipping stale track generation: $generation vs current $currentTrackGeneration")
                    return@launch
                }

                if (ListenTogetherPlayerBridge.player == null) return@launch
                isSyncing = true
                ListenTogetherPlayerBridge.allowInternalSync = true
                try {
                    val mediaItems = queue?.takeIf { it.isNotEmpty() }?.map { ListenTogetherPlayerBridge.trackToMediaItem(it) }
                    if (mediaItems != null) {
                        ListenTogetherPlayerBridge.setMediaItemsForSync(mediaItems)
                    } else {
                        ListenTogetherPlayerBridge.clearMediaItemsForSync()
                    }
                    ListenTogetherPlayerBridge.pause()
                } finally {
                    ListenTogetherPlayerBridge.allowInternalSync = false
                    isSyncing = false
                }
            }
            return
        }

        bufferingTrackId = currentTrack.id
        val generation = ++currentTrackGeneration

        scope.launch {
            // Verify we're still on the same track generation (no newer track change arrived)
            if (currentTrackGeneration != generation) {
                Timber
                    .tag(
                        TAG,
                    ).d("Skipping stale track generation: $generation vs current $currentTrackGeneration (track ${currentTrack.id})")
                return@launch
            }

            if (ListenTogetherPlayerBridge.player == null) return@launch
            isSyncing = true
            ListenTogetherPlayerBridge.allowInternalSync = true

            try {
                // Re-verify generation before applying media items (critical section)
                if (currentTrackGeneration != generation) {
                    Timber.tag(TAG).d("Stale generation detected before setMediaItems: $generation vs $currentTrackGeneration")
                    return@launch
                }

                // Apply queue/media
                val mediaItems =
                    canonicalPlaybackQueue(currentTrack, queue.orEmpty())
                        .map { ListenTogetherPlayerBridge.trackToMediaItem(it) }
                ListenTogetherPlayerBridge.setMediaItemsForSync(mediaItems, 0, initialPosition)

                ListenTogetherPlayerBridge.seekTo(initialPosition)

                if (bypassBuffer) {
                    // Manual sync/reconnect: apply play/pause immediately, no buffer protocol
                    Timber.tag(TAG).d("Bypass buffer: immediately applying play=$isPlaying at pos=$position")

                    // Bounded readiness wait — play/pause is applied either way
                    // (ExoPlayer queues playWhenReady until the stream is ready);
//
                    // dropping the play on a not-ready outcome left guests stuck
                    // paused after a reconnect when the stream took longer than
//
                    // the budget to load (user report).
                    val readyWait = runBypassReadyWait(
                        isReady = { ListenTogetherPlayerBridge.player?.playbackState == Player.STATE_READY },
                    )
                    if (readyWait.ready) {
                        Timber.tag(TAG).d("Player ready after ${readyWait.waitedMs}ms, seeking to corrected position")
                    } else {
                        Timber
                            .tag(
                                TAG,
                            ).w("Player not ready after ${readyWait.waitedMs}ms during bypass sync — play/pause will be queued until the stream is ready")
                    }
                    val readyPosition = client.positionAtServerTime(position, effectiveAtServerTime, isPlaying)
                    Timber.tag(TAG).d("Bypass: seeking to $readyPosition")
                    ListenTogetherPlayerBridge.seekTo(readyPosition)
                    if (effectivePlaying) {
                        ListenTogetherPlayerBridge.play()
                        driftCorrection.start(currentTrack.id, position, effectiveAtServerTime)
                        Timber.tag(TAG).d("Bypass: PLAY issued")
                    } else {
                        ListenTogetherPlayerBridge.pause()
                        Timber.tag(TAG).d("Bypass: PAUSE issued (guestLocalPause=$guestLocalPause)")
                    }

                    // Clear sync state
                    pendingSyncState = null
                    bufferingTrackId = null
                    bufferCompleteReceivedForTrack = null
                } else {
                    // Normal sync: pause, store pending, send buffer_ready.
                    // effectivePlaying keeps a locally-paused guest paused after the buffer
//
                    // completes even when the host is playing.
                    ListenTogetherPlayerBridge.pause()
                    pendingSyncState =
                        SyncStatePayload(
                            currentTrack = currentTrack,
                            isPlaying = effectivePlaying,
                            position = position,
                            lastUpdate = effectiveAtServerTime ?: 0L,
                        )
                    applyPendingSyncIfReady()
                    client.sendBufferReady(currentTrack.id)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error applying playback state")
            } finally {
                ListenTogetherPlayerBridge.allowInternalSync = false
                delay(200)
                isSyncing = false
            }
        }
    }

    /** Load a single track for guest sync without a queue (fallback path): builds the [MediaItem] through the existing stream pipeline, loads it, waits for STATE_READY, pauses, stores the pending sync and signals buffer-ready to the server. */
    @UnstableApi
    private fun syncToTrack(
        track: TrackInfo,
        shouldPlay: Boolean,
        position: Long,
    ) {
        Timber.tag(TAG).d("syncToTrack: ${track.title}, play: $shouldPlay, pos: $position")

        // Track which buffer-complete we expect for this load
        bufferingTrackId = track.id
        val generation = currentTrackGeneration

        activeSyncJob?.cancel()
        activeSyncJob =
            scope.launch {
                try {
                    // Check if a newer track change arrived - skip this load if stale
                    if (currentTrackGeneration != generation) {
                        Timber
                            .tag(
                                TAG,
                            ).d("Skipping stale syncToTrack for ${track.id} (generation $generation vs $currentTrackGeneration)")
                        isSyncing = false
                        return@launch
                    }

                    if (ListenTogetherPlayerBridge.player == null) {
                        isSyncing = false
                        return@launch
                    }
                    isSyncing = true
                    ListenTogetherPlayerBridge.allowInternalSync = true
                    try {
                        val mediaItem = ListenTogetherPlayerBridge.trackToMediaItem(track)
                        ListenTogetherPlayerBridge.setMediaItemsForSync(listOf(mediaItem), 0, 0L)
                        ListenTogetherPlayerBridge.seekTo(0L)

                        // Wait for player to be ready - up to 2 seconds (40 * 50ms)
                        var waitCount = 0
                        while (waitCount < 40) {
                            // Check generation again while waiting
                            if (currentTrackGeneration != generation) {
                                Timber
                                    .tag(
                                        TAG,
                                    ).d("Generation changed while waiting for player ready - aborting sync for ${track.id}")
                                return@launch
                            }
                            val player = ListenTogetherPlayerBridge.player
                            if (player == null) return@launch
                            if (player.playbackState == Player.STATE_READY) {
                                Timber.tag(TAG).d("Player ready after ${waitCount * 50}ms")
                                break
                            }
                            delay(50)
                            waitCount++
                        }

                        // Do NOT seek here; defer the exact seek until after the server signals buffer-complete
                        // Ensure paused state before signaling ready
                        ListenTogetherPlayerBridge.pause()

                        // Store pending sync (guest will apply seek + play/pause after BufferComplete)
                        pendingSyncState =
                            SyncStatePayload(
                                currentTrack = track,
                                isPlaying = shouldPlay,
                                position = position,
                                lastUpdate = 0L,
                            )

                        // Apply immediately if buffer-complete already arrived
                        applyPendingSyncIfReady()

                        // Signal we're ready to play
                        client.sendBufferReady(track.id)
                        Timber
                            .tag(
                                TAG,
                            ).d("Sent buffer ready for ${track.id}, pending sync stored: pos=$position, play=$shouldPlay")

                        // Minimal delay before accepting sync commands
                        delay(100)
                    } finally {
                        ListenTogetherPlayerBridge.allowInternalSync = false
                        isSyncing = false
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error syncing to track")
                    ListenTogetherPlayerBridge.allowInternalSync = false
                    isSyncing = false
                }
            }
    }

    // Public API for host actions

    /**
     * Connect to the Listen Together server
     */
    fun connect() {
        Timber.tag(TAG).d("Connecting to server")
        client.connect()
    }

    /**
     * Disconnect from the server
     */
    fun disconnect() {
        Timber.tag(TAG).d("Disconnecting from server")
        cleanup()
        client.disconnect()
    }

    /**
     * Create a new room
     */
    fun createRoom(username: String) {
        Timber.tag(TAG).d("Creating room")
        client.createRoom(username)
    }

    /**
     * Join an existing room
     */
    fun joinRoom(
        roomCode: String,
        username: String,
    ) {
        Timber.tag(TAG).d("Joining room")
        client.joinRoom(roomCode, username)
    }

    /**
     * Leave the current room
     */
    fun leaveRoom() {
        Timber.tag(TAG).d("Leaving room")
        cleanup()
        client.leaveRoom()
    }

    /**
     * Approve a join request
     */
    fun approveJoin(userId: String) = client.approveJoin(userId)

    /**
     * Reject a join request
     */
    fun rejectJoin(
        userId: String,
        reason: String? = null,
    ) = client.rejectJoin(userId, reason)

    /**
     * Kick a user
     */
    fun kickUser(
        userId: String,
        reason: String? = null,
    ) = client.kickUser(userId, reason)

    /**
     * Block a user permanently (internal list)
     */
    fun blockUser(username: String) = client.blockUser(username)

    /**
     * Unblock a previously blocked user
     */
    fun unblockUser(username: String) = client.unblockUser(username)

    /**
     * Transfer host role to another user
     */
    fun transferHost(newHostId: String) = client.transferHost(newHostId)

    /**
     * Guest play/pause intent, wired by the player service from
     * [ListenTogetherGuestGuardPlayer.onGuestPlayPause] (only the guest's own taps reach the
     * guarded facade — host sync mutates the raw player directly). Play: the guest resyncs to
     * the host's position ([requestSync]). Pause: the local pause is remembered so host PLAY /
     * track changes do not resume the guest until they press play again
     * (spec-listen-together-guest-lock-hardening).
     */
    fun guestPlayPause(playWhenReady: Boolean) {
        if (isHost || !isInRoom) return
        guestLocalPause = !playWhenReady
        Timber.tag(TAG).d("Guest play/pause intent: playWhenReady=$playWhenReady, guestLocalPause=$guestLocalPause")
        if (playWhenReady) requestSync()
    }

    /** Request sync state from server (for guests to re-sync); call this when a guest presses play/pause to sync with host. */
    fun requestSync() {
        if (!isInRoom || isHost) {
            Timber.tag(TAG).d("requestSync: not applicable (isInRoom=$isInRoom, isHost=$isHost)")
            return
        }
        Timber.tag(TAG).d("Requesting sync from server")
        client.requestSync()
    }

    /**
     * Clear logs
     */
    fun clearLogs() = client.clearLogs()

    // Suggestions API

    /**
     * Suggest the given track to the host (guest only)
     */
    fun suggestTrack(track: TrackInfo) = client.suggestTrack(track)

    /**
     * Approve a suggestion (host only)
     */
    fun approveSuggestion(suggestionId: String) {
        if (!isHost) return
        // Send approval; server will insert-next and broadcast once
        client.approveSuggestion(suggestionId)
    }

    /**
     * Reject a suggestion (host only)
     */
    fun rejectSuggestion(
        suggestionId: String,
        reason: String? = null,
    ) = client.rejectSuggestion(suggestionId, reason)

    /**
     * Force reconnection to server (for manual recovery)
     */
    fun forceReconnect() {
        Timber.tag(TAG).d("Forcing reconnection")
        client.forceReconnect()
    }

    /**
     * Get persisted room code if available
     */
    fun getPersistedRoomCode(): String? = client.getPersistedRoomCode()

    /**
     * Get current session age
     */
    fun getSessionAge(): Long = client.getSessionAge()
}
