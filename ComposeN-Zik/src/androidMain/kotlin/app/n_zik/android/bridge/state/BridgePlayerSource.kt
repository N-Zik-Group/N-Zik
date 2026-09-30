package app.n_zik.android.bridge.state

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.bridge.BridgeContract
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val TAG = "BridgePlayerSource"

/** `isLiked` / `isDownloaded` of one track, resolved off the main thread. */
private data class TrackFlags(val isLiked: Boolean, val isDownloaded: Boolean)

/**
 * Feeds a [BridgeStateHub] with the phone's player state, read-only. Binds
 * [PlayerServiceModern] WITHOUT creating it (the app behaves exactly as before; with no
 * player the empty state is published and the connection happens as soon as the service
 * starts), listens to the guarded facade `Binder.player` on the main thread — re-read at
 * every use and re-attached on `playerUpdateTrigger` (crossfade swap) — and samples the
 * player on each relevant event and every [resampleIntervalMs] (fresh heartbeat position).
 *
 * `isLiked` / `isDownloaded` are resolved off the main thread once per queue change and
 * kept until the next one: a like alone produces no delta (contract §1.1).
 */
internal class BridgePlayerSource(
    context: Context,
    private val hub: BridgeStateHub,
    private val clock: () -> Long = System::currentTimeMillis,
    private val resampleIntervalMs: Long = BridgeContract.HEARTBEAT_INTERVAL_MS,
) {
    private val appContext = context.applicationContext

    // Every field below is only touched on the main thread (UI dispatcher, player callbacks)
    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)
    private val sampleRequests = Channel<Unit>(Channel.CONFLATED)
    private var connection: ServiceConnection? = null
    private var binder: PlayerServiceModern.Binder? = null
    private var attachedPlayer: Player? = null
    private var swapJob: Job? = null
    private var pendingSeek = false
    private var pendingTransition = false
    private var resolvedIds: List<String>? = null
    private var flags: Map<String, TrackFlags> = emptyMap()

    private val listener = object : Player.Listener {
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) pendingSeek = true
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Only a repeat-one restart keeps the same track: other transitions show in the track id
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) pendingTransition = true
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(*SAMPLED_EVENTS)) requestSample()
        }
    }

    fun start() {
        scope.launch { bind() }
        scope.launch {
            for (request in sampleRequests) {
                // One failed sample must not stop the publishing for the rest of the run
                try {
                    sampleOnce()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Could not publish a player sample")
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(resampleIntervalMs)
                requestSample()
            }
        }
    }

    /** Detaches from the player and unbinds (on the main thread), then stops every job. Callable from any thread. */
    fun stop() {
        scope.launch(NonCancellable) {
            detachListener()
            swapJob?.cancel()
            connection?.let { conn ->
                runCatching { appContext.unbindService(conn) }
                    .onFailure { Timber.tag(TAG).w(it, "Failed to unbind the player service") }
            }
            connection = null
            binder = null
            scope.cancel()
        }
    }

    private fun bind() {
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val playerBinder = service as? PlayerServiceModern.Binder ?: run {
                    Timber.tag(TAG).w("Unexpected binder from the player service")
                    return
                }
                Timber.tag(TAG).i("Player service connected")
                binder = playerBinder
                swapJob?.cancel()
                // StateFlow: collecting also attaches right away to the current player
                swapJob = scope.launch { playerBinder.playerUpdateTrigger.collect { attachListener() } }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Timber.tag(TAG).i("Player service disconnected, publishing the empty state")
                swapJob?.cancel()
                detachListener()
                binder = null
                requestSample()
            }
        }
        // No BIND_AUTO_CREATE: the bridge never starts the player service by itself
        val bound = runCatching {
            appContext.bindService(Intent(appContext, PlayerServiceModern::class.java), conn, 0)
        }.onFailure { Timber.tag(TAG).e(it, "Could not bind the player service") }.getOrDefault(false)
        if (bound) connection = conn else Timber.tag(TAG).w("Player service binding refused")
        requestSample()
    }

    private fun attachListener() {
        val player = runCatching { binder?.player }.getOrNull()
        if (player !== attachedPlayer) {
            detachListener()
            player?.addListener(listener)
            attachedPlayer = player
            if (player != null) Timber.tag(TAG).d("Listening to the player facade")
        }
        requestSample()
    }

    private fun detachListener() {
        attachedPlayer?.let { previous ->
            runCatching { previous.removeListener(listener) }
                .onFailure { Timber.tag(TAG).w(it, "Failed to remove the bridge player listener") }
        }
        attachedPlayer = null
    }

    private fun requestSample() {
        sampleRequests.trySend(Unit)
    }

    private suspend fun sampleOnce() {
        val player = runCatching { binder?.player }.getOrNull()
        if (player == null) {
            resolvedIds = null
            hub.submit(PlayerSample.EMPTY)
            return
        }
        // A failed read keeps the pending seek / transition for the next sample
        val read = runCatching { PlayerStateReader.read(player, clock()) }
            .onFailure { Timber.tag(TAG).w(it, "Could not read the player state") }
            .getOrNull() ?: return
        val seek = pendingSeek
        val transition = pendingTransition
        pendingSeek = false
        pendingTransition = false
        val ids = read.items.map { it.trackId }
        if (ids != resolvedIds) {
            flags = withContext(NzikDispatchers.DATA) { resolveFlags(ids.distinct()) }
            resolvedIds = ids
        }
        val queue = read.items.map { item ->
            val itemFlags = flags[item.trackId]
            TrackMapping.track(
                mediaId = item.mediaId,
                title = item.title,
                artist = item.artist,
                hasArtwork = item.hasArtwork,
                durationText = item.durationText,
                isLiked = itemFlags?.isLiked == true,
                isDownloaded = itemFlags?.isDownloaded == true,
            )
        }
        hub.submit(read.sample.copy(queue = queue), seek = seek, transition = transition)
    }

    private fun resolveFlags(trackIds: List<String>): Map<String, TrackFlags> {
        val downloads = MyDownloadHelper.downloads.value
        return trackIds.associateWith { id ->
            val liked = runCatching { Database.songTable.isLikedDirect(id) }
                .onFailure { Timber.tag(TAG).w(it, "Could not read the like state of a track") }
                .getOrDefault(false)
            TrackFlags(isLiked = liked, isDownloaded = downloads[id]?.state == Download.STATE_COMPLETED)
        }
    }

    private companion object {
        val SAMPLED_EVENTS = intArrayOf(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_IS_PLAYING_CHANGED,
            Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
            Player.EVENT_REPEAT_MODE_CHANGED,
            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            Player.EVENT_POSITION_DISCONTINUITY,
        )
    }
}
