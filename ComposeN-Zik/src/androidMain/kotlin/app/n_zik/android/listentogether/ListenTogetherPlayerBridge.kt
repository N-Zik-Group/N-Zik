package app.n_zik.android.listentogether

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.utils.coroutines.NzikDispatchers
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Playback bridge for the Listen Together feature (Metrolist port — no [PlayerConnection]
 * equivalent exists in N-Zik).
 *
 * Wraps the shared [PlayerServiceModern] through its own `bindService` (the service `onBind`
 * returns the same binder the UI uses, so no legacy file change is needed) and exposes the
 * small surface the sync manager needs:
 *  - dynamic [Player] access (the service swaps the ExoPlayer on crossfade, `player` is never
 *    cached);
 *  - playback control for sync operations (play/pause/seek/queue manipulation);
 *  - player-listener registration that survives crossfade player swaps
 *    (re-attached when [PlayerServiceModern.playerUpdateTrigger] increments);
 *  - upcoming-queue snapshots and track conversions (proto [TrackInfo] <-> [MediaItem]).
 *
 * Guests never change playback: the manager only drives this bridge with host commands, and
 * the user-facing controls are disabled via [listenTogetherGuestLock].
 */
/**
 * Guest lock shared with the player UI (MiniPlayer / player controls): `true` while the
 * current user is a guest in a Listen Together room. The [ListenTogetherManager] updates it;
 * legacy player UI files read it via `by` delegation to disable skip/queue/shuffle.
 */
val listenTogetherGuestLock = mutableStateOf(false)

object ListenTogetherPlayerBridge {

    private const val TAG = "LTPlayerBridge"

    /**
     * MediaItem extra marking an item as built for a Listen Together session
     * ([trackToMediaItem]). LT items carry the host app's (Metrolist) metadata,
     * which can differ from N-Zik's YouTube resolution, so library writes must
     * be skipped for them (see [isListenTogetherItem]).
     */
    const val LISTEN_TOGETHER_EXTRA = "listen_together"

    // Main-thread scope on the app's dispatcher system (no Main.immediate equivalent in
    // NzikDispatchers; one extra looper hop is irrelevant for the jobs launched here).
    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)

    private var service: PlayerServiceModern? = null
    private var serviceConnection: ServiceConnection? = null
    private var boundContext: Context? = null
    private var playerTriggerJob: Job? = null

    /** Set while the manager applies host state internally (suppresses echo on the host side). */
    @Volatile
    var allowInternalSync: Boolean = false

    private val _upcomingQueue = MutableStateFlow<List<TrackInfo>>(emptyList())
    val upcomingQueue: StateFlow<List<TrackInfo>> = _upcomingQueue.asStateFlow()

    /**
     * Listener registrations kept so they can be re-attached to the fresh ExoPlayer after a
     * crossfade player swap (the service swaps `player` and bumps `playerUpdateTrigger`).
     */
    private val registeredListeners = mutableMapOf<Player.Listener, Boolean>()

    /**
     * Video id of the current Listen Together item, or null — maintained on the main thread
     * only (see [updateCurrentListenTogetherVideoId]). The stream pipeline reads it from the
     * ExoPlayer IO thread via [isListenTogetherVideo]; touching the player there makes Media3
     * throw "Player is accessed on the wrong thread" and the load fails in a retry loop.
     */
    @Volatile
    private var currentListenTogetherVideoId: String? = null

    private val bridgePlayerListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            refreshUpcomingQueue()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateCurrentListenTogetherVideoId()
            refreshUpcomingQueue()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) refreshUpcomingQueue()
        }
    }

    // ── Service binding ────────────────────────────────────────────────────────────────

    /** Idempotent lazy bind; must be called from the main thread (bindService requires it). */
    fun ensureBound(context: Context) {
        if (service != null || serviceConnection != null) return
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName, ibinder: IBinder) {
                service = (ibinder as PlayerServiceModern.Binder).service
                Timber.tag(TAG).d("Bound to PlayerServiceModern")
                // Attach the queue-tracking listener and any listener registered before bind.
                attachBridgeListener()
                reattachAllListeners()
                refreshUpcomingQueue()
            }

            override fun onServiceDisconnected(name: android.content.ComponentName) {
                // The service is not being destroyed (we hold a binding); just wait for rebind.
                service = null
                Timber.tag(TAG).w("Player service connection lost, waiting for rebind")
            }
        }
        serviceConnection = connection
        val intent = Intent(context, PlayerServiceModern::class.java)
        if (context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            boundContext = context
        } else {
            serviceConnection = null
            Timber.tag(TAG).e("Failed to bind PlayerServiceModern for Listen Together")
        }
        observePlayerSwaps()
    }

    private fun observePlayerSwaps() {
        playerTriggerJob?.cancel()
        playerTriggerJob = scope.launch {
            service?.let { svc ->
                svc.playerUpdateTrigger.collect {
                    // Fresh ExoPlayer after crossfade: re-attach every registered listener.
                    // A deferred head trim targets the previous player instance — drop it.
                    cancelPendingHeadTrim()
                    Timber.tag(TAG).d("Player swapped, re-attaching Listen Together listeners")
                    reattachAllListeners()
                    refreshUpcomingQueue()
                }
            }
        }
    }

    /** The shared ExoPlayer — always read dynamically, never cache the instance. */
    val player: Player?
        get() = service?.player

    // ── Player listener registration (survives crossfade swaps) ────────────────────────

    fun registerPlayerListener(listener: Player.Listener) {
        registeredListeners[listener] = true
        try {
            player?.addListener(listener)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to add player listener")
        }
        Timber.tag(TAG).d("Registered player listener: ${listener::class.simpleName}")
    }

    fun unregisterPlayerListener(listener: Player.Listener) {
        registeredListeners[listener] = false
        try {
            player?.removeListener(listener)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to remove player listener")
        }
        Timber.tag(TAG).d("Unregistered player listener: ${listener::class.simpleName}")
    }

    private fun attachBridgeListener() {
        try {
            player?.addListener(bridgePlayerListener)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to add bridge player listener")
        }
        // Seed the LT marker cache for the item already current (bind after a restart,
        // or a crossfade swap) — the listener alone would wait for the next transition.
        updateCurrentListenTogetherVideoId()
    }

    private fun reattachAllListeners() {
        val current = player ?: return
        attachBridgeListener()
        for ((listener, isRegistered) in registeredListeners) {
            if (!isRegistered) continue
            try {
                current.removeListener(listener)
                current.addListener(listener)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to re-attach player listener after swap")
            }
        }
    }

    // ── Playback control (sync operations only) ─────────────────────────────────────────

    fun play() {
        val p = player ?: return
        // A fresh (IDLE) player never starts on play() alone — see
        // [shouldPreparePlayerAfterSync]. Defensive: covers raw setMediaItems paths
        // (SYNC_QUEUE handler) that bypass setMediaItemsForSync.
        if (shouldPreparePlayerAfterSync(p.playbackState, p.mediaItemCount)) {
            p.prepare()
        }
        p.play()
    }

    fun pause() {
        player?.pause()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekToNext() {
        player?.seekToNext()
    }

    fun seekToPrevious() {
        player?.seekToPrevious()
    }

    /**
     * Sync the player queue to the host state, starting playback from [startIndex] at
     * [startPositionMs].
     *
     * Prefers an in-place update over a full timeline replace: `setMediaItems` stops the
     * player (IDLE) and drops the loaded stream; on a guest the host re-broadcasts the whole
     * queue on every track change, so a full replace made the player sheet visibly
     * close/reopen on each host track change (user-reported). Instead the target is located
     * in the loaded queue (or appended), the player seeks to it, and the queue is realigned
     * around it — the playback session and the media item survive the sync.
     *
     * Mutation order is a Media3 constraint (bug 2026-09-26, "timeline cassée"): a seek to
     * another window is applied *after* this call returns, and removing a window the player
     * is transitioning through (or the item it is playing) cancels that transition — the
     * player was left IDLE with a dead timeline and never recovered. So the target is
     * sought first, then only items *after* the target are adjusted, and the played head
     * (which includes the item that was playing) is trimmed only once the transition has
     * completed ([scheduleHeadTrim]).
     */
    @UnstableApi
    fun setMediaItemsForSync(mediaItems: List<MediaItem>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        val player = player ?: return
        player.shuffleModeEnabled = false
        val hostQueue = mediaItems.subList(startIndex, mediaItems.size)
        val plan = computeInPlaceQueueSyncPlan(
            loadedIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
            hostQueueIds = hostQueue.map { it.mediaId },
        )
        if (plan == null) {
            cancelPendingHeadTrim()
            player.clearMediaItems()
            return
        }
        if (plan.appendCount > 0) {
            player.addMediaItems(hostQueue)
        }
        player.seekTo(plan.targetIndex, startPositionMs.coerceAtLeast(0L))
        if (!plan.tailMatches) {
            // Replace the stale tail — only items after the target, so the pending window
            // seek to the target is unaffected.
            for (i in tailRemovalIndices(player.mediaItemCount, plan.targetIndex)) {
                player.removeMediaItem(i)
            }
            val hostTail = hostQueue.subList(1, hostQueue.size)
            if (hostTail.isNotEmpty()) {
                player.addMediaItems(hostTail)
            }
        }
        if (plan.headToRemove > 0) {
            if (canTrimHeadImmediately(plan.targetIndex, player.currentMediaItemIndex)) {
                // Target is already the current item: no window transition in flight, the
                // head (items before the current one) can be trimmed right away.
                trimPlayedHead(plan.headToRemove)
            } else {
                // Defer: trimming now would remove the item the player is transitioning
                // through and cancel the transition.
                scheduleHeadTrim(plan.headToRemove, hostQueue.first().mediaId)
            }
        } else {
            cancelPendingHeadTrim()
        }
        // Media3 never moves an IDLE player out of IDLE automatically (verified against the
        // media3 1.10.1 sources: setMediaItems/addMediaItems on a fresh player leave it IDLE
        // and play() only flips playWhenReady). After a process restart the first sync loaded
        // the host queue but never prepared the player, so the guest stayed stuck IDLE and the
        // player "did nothing" (user-reported, 2026-09-26). prepare() starts loading from the
        // pending seek above; it is a no-op for every other state.
        if (shouldPreparePlayerAfterSync(player.playbackState, player.mediaItemCount)) {
            player.prepare()
            Timber.tag(TAG).i("Queue sync: player was IDLE with loaded queue - issuing prepare()")
        }
        Timber.tag(TAG).d(
            "Queue sync applied in place: target=${hostQueue.first().mediaId} @ ${plan.targetIndex}, " +
                "loaded=${player.mediaItemCount} items, headTrim=${plan.headToRemove}",
        )
    }

    fun clearMediaItemsForSync() {
        cancelPendingHeadTrim()
        player?.clearMediaItems()
        currentListenTogetherVideoId = null
    }

    // ── Deferred played-head trim (see [setMediaItemsForSync]) ──────────────────────────

    /** Fallback delay for the head trim when no transition callback is expected to fire. */
    private const val HEAD_TRIM_FALLBACK_MS = 2000L

    private var headTrimListener: Player.Listener? = null
    private var headTrimJob: Job? = null

    /**
     * Trims the played head once the player has transitioned to the item with [targetMediaId]
     * (one-shot [Player.Listener.onMediaItemTransition]), with a [HEAD_TRIM_FALLBACK_MS]
     * fallback for the case where no transition fires (target already current).
     */
    private fun scheduleHeadTrim(headToRemove: Int, targetMediaId: String) {
        cancelPendingHeadTrim()
        val player = player ?: return
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (headTrimListener !== this) return // replaced by a newer sync
                cancelPendingHeadTrim()
                if (mediaItem?.mediaId == targetMediaId) {
                    trimPlayedHead(headToRemove)
                }
            }
        }
        headTrimListener = listener
        try {
            player.addListener(listener)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to add head-trim player listener")
            headTrimListener = null
        }
        headTrimJob = scope.launch {
            delay(HEAD_TRIM_FALLBACK_MS)
            if (headTrimListener === listener) {
                cancelPendingHeadTrim()
                if (player.currentMediaItem?.mediaId == targetMediaId) {
                    trimPlayedHead(headToRemove)
                }
            }
        }
    }

    /** Drops the played head, highest index first; never touches the current item. */
    private fun trimPlayedHead(headToRemove: Int) {
        val player = player ?: return
        val current = player.currentMediaItemIndex
        val toRemove = headToRemove.coerceAtMost(current.coerceAtLeast(0))
        for (i in toRemove - 1 downTo 0) {
            player.removeMediaItem(i)
        }
        if (toRemove > 0) {
            Timber.tag(TAG).d("Trimmed $toRemove played item(s) from the queue head")
        }
    }

    private fun cancelPendingHeadTrim() {
        headTrimListener?.let { listener ->
            try {
                player?.removeListener(listener)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to remove head-trim player listener")
            }
        }
        headTrimListener = null
        headTrimJob?.cancel()
        headTrimJob = null
    }

    // ── Track conversions ──────────────────────────────────────────────────────────────

    /**
     * Video id of a media item (media ids may carry a path prefix — same convention as
     * `MediaItem.asSong`).
     */
    private fun videoIdOf(mediaId: String): String = mediaId.split("/").lastOrNull() ?: mediaId

    /** Current track as a proto [TrackInfo], or null when nothing is loaded. */
    fun currentTrackInfo(): TrackInfo? {
        val player = player ?: return null
        val item = player.currentMediaItem ?: return null
        val metadata = item.mediaMetadata
        return TrackInfo(
            id = videoIdOf(item.mediaId),
            title = metadata.title?.toString() ?: "Unknown",
            artist = metadata.artist?.toString() ?: "Unknown",
            album = metadata.albumTitle?.toString().orEmpty(),
            duration = player.duration.takeIf { it > 0 } ?: 0L,
            thumbnail = metadata.artworkUri?.toString().orEmpty(),
        )
    }

    /**
     * True when [item] was built for a Listen Together session ([trackToMediaItem]).
     * LT items carry the host app's (Metrolist) metadata — placeholder bylines
     * (e.g. a literal "Titre"), title-as-album, comma-joined channel bylines —
     * which would pollute the library with garbage Artist/Album rows if written.
     */
    fun isListenTogetherItem(item: MediaItem?): Boolean =
        item?.mediaMetadata?.extras?.getBoolean(LISTEN_TOGETHER_EXTRA, false) == true

    /**
     * Decision for the local-DB history write in the player service's
     * `onPlaybackStatsReady`: the app-wide pause switch always wins; Listen
     * Together items are recorded only when the user enabled the LT history
     * option (their metadata belongs to the host app, so at most a browseId-only
     * placeholder Song row is written for the Event foreign key).
     */
    fun shouldRecordLocalHistory(
        isListenTogetherItem: Boolean,
        pauseListenHistory: Boolean,
        listenTogetherHistoryEnabled: Boolean,
    ): Boolean =
        !pauseListenHistory && (!isListenTogetherItem || listenTogetherHistoryEnabled)

    /**
     * Decision for the stream library-metadata flow
     * (`StreamResolver.upsertSongInfo`: check the track against YouTube, then
     * upsert the verified data).
     *
     * Regular streams always run the flow. Listen Together streams only run it
     * when the user enabled the "Local history" option for LT — with the option
     * off, LT streams stay fully library-agnostic (no Song/Artist/Album rows
     * created by playback), mirroring [shouldRecordLocalHistory].
     */
    fun shouldRunStreamInfoUpsert(
        isListenTogether: Boolean,
        listenTogetherHistoryEnabled: Boolean,
    ): Boolean =
        !isListenTogether || listenTogetherHistoryEnabled

    /**
     * Decision for [Player.prepare] after a queue sync / before playback.
     *
     * Media3 never moves an IDLE player out of IDLE automatically (verified against the
     * media3 1.10.1 sources: `setMediaSourcesInternal` masks the state — "never move out
     * of IDLE automatically" — and `BasePlayer.play()` only calls setPlayWhenReady). After
     * a process restart the guest's first Listen Together sync loaded the host queue but
     * never prepared the player, so it stayed IDLE forever and "did nothing"
     * (user-reported, 2026-09-26). [Player.prepare] is a no-op when the player is not IDLE
     * (its public guard), and the ENDED state self-recovers from the sync seek (seeking
     * while ENDED forces BUFFERING), so the guard is only needed for IDLE with items.
     */
    fun shouldPreparePlayerAfterSync(playbackState: Int, mediaItemCount: Int): Boolean =
        playbackState == Player.STATE_IDLE && mediaItemCount > 0

    /**
     * True when the item currently loaded for [videoId] is a Listen Together item.
     * The stream pipeline uses this to skip library writes for host metadata —
     * per-item (not per-room) on purpose: the host queue may keep playing after
     * the room session ends and cleanup() does not clear the timeline.
     *
     * Thread-safe to call from the ExoPlayer IO thread: reads the main-thread-maintained
     * cache [currentListenTogetherVideoId] instead of the player (Media3 forbids player
     * access outside its own thread).
     */
    fun isListenTogetherVideo(videoId: String): Boolean =
        currentListenTogetherVideoId != null && currentListenTogetherVideoId == videoId

    /**
     * Refreshes [currentListenTogetherVideoId] from the player's current item.
     * Must only run on the main thread (player callbacks and sync operations).
     */
    private fun updateCurrentListenTogetherVideoId() {
        currentListenTogetherVideoId = listenTogetherVideoIdOf(player?.currentMediaItem)
    }

    /**
     * Video id to cache as the current Listen Together video for [item]: the (prefix-stripped)
     * video id when [item] is a Listen Together item, null otherwise. Pure — safe from any thread.
     */
    internal fun listenTogetherVideoIdOf(item: MediaItem?): String? =
        item?.takeIf { isListenTogetherItem(it) }?.let { videoIdOf(it.mediaId) }

    /** Upcoming queue (timeline windows after the current item) as proto [TrackInfo] list. */
    fun refreshUpcomingQueue() {
        val player = player ?: return
        if (player.mediaItemCount == 0) {
            _upcomingQueue.value = emptyList()
            return
        }
        val window = Timeline.Window()
        val timeline = player.currentTimeline
        val tracks = buildList {
            for (i in (player.currentMediaItemIndex + 1) until minOf(player.mediaItemCount, timeline.windowCount)) {
                val track = timeline.getWindow(i, window).toTrackInfo()
                if (track.id.isNotEmpty()) add(track)
            }
        }
        _upcomingQueue.value = tracks
    }

    /**
     * Build a playable [MediaItem] for a proto [TrackInfo] through the existing stream
     * pipeline: raw video id as media id + uri (the service's `ResolvingDataSource` extracts
     * the video id and resolves the stream, exactly like `Song.asMediaItem` for YouTube ids).
     */
    @UnstableApi
    fun trackToMediaItem(track: TrackInfo): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        if (track.album.isNotEmpty()) metadataBuilder.setAlbumTitle(track.album)
        if (track.thumbnail.isNotEmpty()) {
            try {
                metadataBuilder.setArtworkUri(track.thumbnail.toUri())
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Invalid thumbnail URI for ${track.id}")
            }
        }
        val extras = android.os.Bundle()
        // Mark the item as a Listen Together item so the playback pipeline skips
        // library writes for it (the host app's metadata must not reach the DB).
        extras.putBoolean(LISTEN_TOGETHER_EXTRA, true)
        val durationMs = track.duration
        if (durationMs > 0) {
            val seconds = durationMs / 1000
            extras.putString("durationText", "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}")
        }
        metadataBuilder.setExtras(extras)
        return MediaItem.Builder()
            .setMediaMetadata(metadataBuilder.build())
            .setMediaId(track.id)
            .setUri(track.id.toUri())
            .setCustomCacheKey(track.id)
            .build()
    }

    /**
     * Upcoming queue windows after the current item, converted to proto [TrackInfo]
     * (Metrolist `Timeline.Window.toTrackInfo()` equivalent).
     */
    private fun Timeline.Window.toTrackInfo(): TrackInfo {
        val mediaItem = mediaItem ?: return TrackInfo("unknown", "Unknown", "Unknown", "", 0L, "")
        val metadata = mediaItem.mediaMetadata
        val durationMs = if (durationUs > 0) durationUs / 1000 else 0L
        return TrackInfo(
            id = videoIdOf(mediaItem.mediaId),
            title = metadata.title?.toString() ?: "Unknown",
            artist = metadata.artist?.toString() ?: "Unknown",
            album = metadata.albumTitle?.toString().orEmpty(),
            duration = durationMs,
            thumbnail = metadata.artworkUri?.toString().orEmpty(),
        )
    }

    fun release() {
        playerTriggerJob?.cancel()
        playerTriggerJob = null
        registeredListeners.clear()
        boundContext?.let { ctx ->
            val connection = serviceConnection
            if (connection != null) {
                try {
                    ctx.unbindService(connection)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to unbind player service")
                }
            }
        }
        service = null
        serviceConnection = null
        boundContext = null
        _upcomingQueue.value = emptyList()
    }
}
