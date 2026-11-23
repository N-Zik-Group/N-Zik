// Ported from Metrolist (GPL-3.0, https://github.com/MetrolistGroup/Metrolist).
package app.n_zik.android.listentogether

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

private const val TAG = "ListenTogetherManager"

internal class ListenTogetherHostPlayback(
    private val scope: CoroutineScope,
    private val client: ListenTogetherClient,
    private val isSyncing: () -> Boolean,
    private val localTrackId: () -> String?,
    private val syncHostVolumeEnabled: () -> Boolean,
) {
    private val isHost: Boolean get() = client.isHost
    private val isInRoom: Boolean get() = client.isInRoom
    private var playerListenerRegistered = false
    private var queueObserverJob: Job? = null
    private var heartbeatJob: Job? = null
    private var lastSyncedVolume: Float? = null
    private var lastSyncedIsPlaying: Boolean? = null
    private var lastSyncedTrackId: String? = null

    private val playerListener =
        object : Player.Listener {
            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                try {
                    if (isSyncing() || !isHost || !isInRoom) return

                    if (ListenTogetherPlayerBridge.allowInternalSync) return
                    val player = ListenTogetherPlayerBridge.player ?: return

                    Timber.tag(TAG).d("Play state changed: $playWhenReady (reason: $reason)")

                    // ALWAYS ensure track is synced before play/pause
                    val currentTrackId = localTrackId()
                    if (currentTrackId != null && currentTrackId != lastSyncedTrackId) {
                        Timber
                            .tag(TAG)
                            .d("[SYNC] Sending track change before play state: track = $currentTrackId")
                        ListenTogetherPlayerBridge.currentTrackInfo()?.let { track ->
                            sendTrackChangeInternal(track)
                            lastSyncedTrackId = currentTrackId
                            // Reset play state since server resets IsPlaying on track change
                            lastSyncedIsPlaying = false
                        }
                        if (playWhenReady) {
                            Timber.tag(TAG).d("[SYNC] Host is playing, sending PLAY after track change")
                            lastSyncedIsPlaying = true
                            client.sendPlaybackAction(
                                PlaybackActions.PLAY,
                                trackId = currentTrackId,
                                position = player.currentPosition,
                            )
                        }
                        return
                    }

                    // Only send play/pause if track is already synced
                    sendPlayState(playWhenReady, player)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error in onPlayWhenReadyChanged")
                }
            }

            private fun sendPlayState(
                playWhenReady: Boolean,
                player: Player,
            ) {
                try {
                    val position = player.currentPosition
                    val currentTrackId = localTrackId()

                    if (playWhenReady) {
                        Timber.tag(TAG).d("Host sending PLAY at position $position (track: $currentTrackId)")
                        client.sendPlaybackAction(PlaybackActions.PLAY, trackId = currentTrackId, position = position)
                        lastSyncedIsPlaying = true
                    } else if (!playWhenReady && (lastSyncedIsPlaying == true)) {
                        Timber.tag(TAG).d("Host sending PAUSE at position $position (track: $currentTrackId)")
                        client.sendPlaybackAction(PlaybackActions.PAUSE, trackId = currentTrackId, position = position)
                        lastSyncedIsPlaying = false
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error in sendPlayState")
                }
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                try {
                    if (isSyncing() || !isHost || !isInRoom) return
                    if (mediaItem == null) {
                        // Host cleared the player (mini-player dismiss clean): propagate the clean
                        // to the guests — an empty SYNC_QUEUE makes them clear their queue and
                        // stop, mirroring the host (spec-listen-together-guest-lock-hardening).
                        // Sent immediately because the debounced queue observer would race the
                        // host's stopService.
                        if (!ListenTogetherPlayerBridge.allowInternalSync) {
                            Timber.tag(TAG).d("Host: player queue cleared, sending empty SYNC_QUEUE to guests")
                            client.sendPlaybackAction(PlaybackActions.SYNC_QUEUE, queue = emptyList())
                        }
                        return
                    }

                    if (ListenTogetherPlayerBridge.allowInternalSync) return
                    val player = ListenTogetherPlayerBridge.player ?: return

                    val trackId = localTrackId()
                    if (trackId == null || trackId == lastSyncedTrackId) return

                    // Send track change with the current track info
                    ListenTogetherPlayerBridge.currentTrackInfo()?.let { track ->
                        lastSyncedTrackId = trackId
                        // Reset play state tracking since server resets IsPlaying on track change
                        lastSyncedIsPlaying = false

                        Timber.tag(TAG).d("Host sending track change: ${track.title}")
                        sendTrackChangeInternal(track)

                        val isPlaying = player.playWhenReady
                        if (isPlaying) {
                            Timber.tag(TAG).d("Host is playing during track change, sending PLAY")
                            lastSyncedIsPlaying = true
                            client.sendPlaybackAction(
                                PlaybackActions.PLAY,
                                trackId = trackId,
                                position = player.currentPosition,
                            )
                        }
                    } ?: Timber
                        .tag(TAG)
                        .w("onMediaItemTransition: track info not ready for $trackId; lastSyncedTrackId unchanged")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error in onMediaItemTransition")
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                try {
                    if (isSyncing() || !isHost || !isInRoom) return
                    if (ListenTogetherPlayerBridge.allowInternalSync) return

                    // Only send seek if it was a user-initiated seek (also covers "restart song":
                    // a host seek-0 is captured here, so no separate restart hook is needed)
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        val trackId = localTrackId()
                        Timber.tag(TAG).d("Host sending SEEK to ${newPosition.positionMs} (track: $trackId)")
                        client.sendPlaybackAction(PlaybackActions.SEEK, trackId = trackId, position = newPosition.positionMs)
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error in onPositionDiscontinuity")
                }
            }
        }

    fun registerListener(failureMessage: String, successMessage: String? = null) {
        if (ListenTogetherPlayerBridge.player == null || playerListenerRegistered) return
        try {
            ListenTogetherPlayerBridge.registerPlayerListener(playerListener)
            playerListenerRegistered = true
            if (successMessage != null) Timber.tag(TAG).d(successMessage)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, failureMessage)
        }
    }

    fun unregisterListener() {
        if (playerListenerRegistered) {
            ListenTogetherPlayerBridge.unregisterPlayerListener(playerListener)
            playerListenerRegistered = false
        }
    }

    fun resetPublishedTrack() {
        lastSyncedIsPlaying = null
        lastSyncedTrackId = null
    }

    fun publishRoomCreated() {
        // Initialize sync state
        val player = ListenTogetherPlayerBridge.player
        lastSyncedIsPlaying = player?.playWhenReady
        lastSyncedTrackId = localTrackId()

        // If there's already a track loaded, send it to the server
        ListenTogetherPlayerBridge.currentTrackInfo()?.let { track ->
            Timber.tag(TAG).d("Room created with existing track: ${track.title}")
            // Send track change so server has the current track info
            sendTrackChangeInternal(track)
            // WebSocket sends from one client are ordered, so PLAY can follow immediately.
            val isPlaying = player?.playWhenReady == true
            if (isPlaying) {
                lastSyncedIsPlaying = true
                Timber.tag(TAG).d("Host already playing on room create, sending PLAY")
                client.sendPlaybackAction(
                    PlaybackActions.PLAY,
                    trackId = track.id,
                    position = player?.currentPosition ?: 0L,
                )
            }
        }
    }

    fun publishReconnectedHost(state: RoomState) {
        // Host: only send sync if necessary
        val player = ListenTogetherPlayerBridge.player
        lastSyncedIsPlaying = player?.playWhenReady
        lastSyncedTrackId = localTrackId()

        val currentTrack = ListenTogetherPlayerBridge.currentTrackInfo()
        if (currentTrack != null) {
            // Reconnect only republishes track metadata when the server has a different track.
            val serverTrackId = state.currentTrackOrNull?.id
            if (serverTrackId != currentTrack.id) {
                Timber
                    .tag(
                        TAG,
                    ).d(
                        "Reconnected as host, server track ($serverTrackId) differs from local (${currentTrack.id}), syncing",
                    )
                sendTrackChangeInternal(currentTrack)
            } else {
                Timber.tag(TAG).d("Reconnected as host, server already has current track $serverTrackId")
            }

            if (player?.playWhenReady == true) {
                val pos = player.currentPosition
                val trackId = currentTrack.id
                Timber.tag(TAG).d("Reconnected host is playing, sending PLAY at $pos (track: $trackId)")
                client.sendPlaybackAction(PlaybackActions.PLAY, trackId = trackId, position = pos)
            }
        }
    }

    fun publishTransferredHost() {
        // Send current player state to guests
        val player = ListenTogetherPlayerBridge.player
        val track = ListenTogetherPlayerBridge.currentTrackInfo()
        if (track != null) {
            Timber.tag(TAG).d("New host sending current track: ${track.title}")
            sendTrackChangeInternal(track)

            if (player?.playWhenReady == true) {
                Timber.tag(TAG).d("New host is playing, sending PLAY")
                client.sendPlaybackAction(
                    PlaybackActions.PLAY,
                    trackId = track.id,
                    position = player.currentPosition,
                )
            }
        }
    }

    /**
     * Internal track change - bypasses isSyncing check for initial state sync
     */
    private fun sendTrackChangeInternal(track: TrackInfo) {
        if (!isHost) return

        // Use a default duration of 3 minutes if duration is 0 or negative
        val durationMs = if (track.duration > 0) track.duration else 180000L
        val trackInfo =
            if (durationMs == track.duration) {
                track
            } else {
                TrackInfo(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    duration = durationMs,
                    thumbnail = track.thumbnail,
                    suggestedBy = track.suggestedBy,
                )
            }

        Timber.tag(TAG).d("Sending track change: ${trackInfo.title}, duration: $durationMs")

        // Also grab current queue to send along with track change
        val currentQueue =
            try {
                ListenTogetherPlayerBridge.upcomingQueue.value
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to get current queue")
                null
            }

        client.sendPlaybackAction(
            PlaybackActions.CHANGE_TRACK,
            trackInfo = trackInfo,
            queue = currentQueue,
        )
    }

    fun startQueueSyncObservation() {
        if (queueObserverJob?.isActive == true) return

        Timber.tag(TAG).d("Starting queue sync observation")
        queueObserverJob =
            scope.launch {
                ListenTogetherPlayerBridge.upcomingQueue
                    .collectLatest { tracks ->
                        if (!isHost || !isInRoom || isSyncing()) return@collectLatest

                        delay(500) // Debounce rapid playlist manipulations

                        Timber.tag(TAG).d("Sending SYNC_QUEUE with ${tracks.size} items")
                        client.sendPlaybackAction(
                            PlaybackActions.SYNC_QUEUE,
                            queue = tracks,
                        )
                    }
            }
    }

    fun stopQueueSyncObservation() {
        queueObserverJob?.cancel()
        queueObserverJob = null
    }

    fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob =
            scope.launch {
                while (heartbeatJob?.isActive == true && isInRoom && isHost) {
                    delay(8000L)
                    val player = ListenTogetherPlayerBridge.player
                    if (player != null && player.playWhenReady && player.playbackState == Player.STATE_READY) {
                        val pos = player.currentPosition
                        val beatTrackId = localTrackId()
                        Timber.tag(TAG).d("Host heartbeat: sending PLAY at pos $pos track=$beatTrackId")
                        client.sendPlaybackAction(
                            PlaybackActions.PLAY,
                            trackId = beatTrackId,
                            position = pos,
                        )
                    }
                    // Volume sync: N-Zik has no service-level volume flow, so the host polls
                    // the ExoPlayer volume each heartbeat and broadcasts significant changes.
                    if (player != null && syncHostVolumeEnabled()) {
                        val normalized = player.volume.coerceIn(0f, 1f)
                        val last = lastSyncedVolume
                        if (last == null || kotlin.math.abs(last - normalized) >= 0.01f) {
                            lastSyncedVolume = normalized
                            client.sendPlaybackAction(PlaybackActions.SET_VOLUME, volume = normalized)
                        }
                    }
                }
            }
        Timber.tag(TAG).d("Host heartbeat started (8s interval)")
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        lastSyncedVolume = null
        Timber.tag(TAG).d("Host heartbeat stopped")
    }
}
