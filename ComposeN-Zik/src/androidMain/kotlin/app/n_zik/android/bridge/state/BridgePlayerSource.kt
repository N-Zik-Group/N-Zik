package app.n_zik.android.bridge.state

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.bridge.BridgeContract
import app.n_zik.android.bridge.command.PlayerAccess
import app.n_zik.android.bridge.command.Sampled
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val TAG = "BridgePlayerSource"

/** `like` / `isDownloaded` of one track, resolved off the main thread. */
private data class TrackFlags(val like: TrackLike, val isDownloaded: Boolean)

/**
 * Feeds a [BridgeStateHub] with the phone's player state, read-only. Binds
 * [PlayerServiceModern] WITHOUT creating it (the app behaves exactly as before; with no
 * player the empty state is published and the connection happens as soon as the service
 * starts), listens to the guarded facade `Binder.player` on the main thread — re-read at
 * every use and re-attached on `playerUpdateTrigger` (crossfade swap) — and samples the
 * player on each relevant event and every [resampleIntervalMs] (fresh heartbeat position).
 *
 * `like` / `isDownloaded` are re-resolved off the main thread on EVERY sample (cheap
 * indexed reads, contract §1.1): a flag change alone — a like on the phone, a download
 * finishing — reaches the companion as a `queueChanged`. The hub drops the delta when the
 * flags did not actually move.
 *
 * As a [PlayerAccess], it hands the same facade to the remote commands (contract §9) and
 * samples right after each one, serialized with the regular sampling.
 */
internal class BridgePlayerSource(
    context: Context,
    private val hub: BridgeStateHub,
    private val clock: () -> Long = System::currentTimeMillis,
    private val resampleIntervalMs: Long = BridgeContract.HEARTBEAT_INTERVAL_MS,
    /** Player errors, on the main thread: late failures of commands (contract §7.6). */
    private val onPlayerError: (String) -> Unit = {},
) : PlayerAccess {
    private val appContext = context.applicationContext

    // Every field below is only touched on the main thread (UI dispatcher, player callbacks)
    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)
    private val sampleRequests = Channel<Unit>(Channel.CONFLATED)

    /** One sample at a time: the periodic loop and the samples forced by commands never interleave. */
    private val sampleMutex = Mutex()
    private var connection: ServiceConnection? = null
    // @Volatile: the bridge's `GET /library/cache` (contract §10) reads the bound service's cache
    // size off the main thread; the binder is published only once fully built
    @Volatile private var binder: PlayerServiceModern.Binder? = null
    private var attachedPlayer: Player? = null
    private var swapJob: Job? = null
    private var pendingSeek = false
    private var pendingTransition = false
    private var flags: Map<String, TrackFlags> = emptyMap()

    private val listener = object : Player.Listener {
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) pendingSeek = true
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Only a repeat-one restart keeps the same track: other transitions show in the track id
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) pendingTransition = true
        }

        override fun onPlayerError(error: PlaybackException) {
            onPlayerError(error.errorCodeName)
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
                    sampleMutex.withLock { sampleOnce() }
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

    /**
     * The streaming cache's used space from the bound service, as the phone's own
     * `CacheSpaceIndicator` reads it; `null` while the service is not yet bound. Callable from
     * any thread (contract §10, `GET /library/cache`).
     */
    fun mediaCacheSpace(): Long? = binder?.cache?.cacheSpace

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
        val player = currentPlayer()
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

    /** The guarded facade, read fresh at every use (never cached across calls). */
    private fun currentPlayer(): Player? = runCatching { binder?.player }.getOrNull()

    override suspend fun <T> read(block: (Player?) -> T): T =
        withContext(NzikDispatchers.UI) { block(currentPlayer()) }

    override suspend fun <T> applyAndSample(block: (Player?) -> T): Sampled<T> =
        sampleMutex.withLock {
            withContext(NzikDispatchers.UI) {
                val result = block(currentPlayer())
                // The command is applied: a failed sample must not turn it into an error
                val deltas = try {
                    sampleOnce()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Could not sample the player after a command")
                    emptyList()
                }
                // Still under the sample lock: no later sample has moved the revision yet
                Sampled(result, deltas, hub.currentRevision)
            }
        }

    /** Reads the player and publishes the deltas it implies; call it holding [sampleMutex]. */
    private suspend fun sampleOnce(): List<DeltaMessage> {
        val player = currentPlayer()
        if (player == null) {
            return hub.submit(PlayerSample.EMPTY)
        }
        // A failed read keeps the pending seek / transition for the next sample
        val read = runCatching { PlayerStateReader.read(player, clock()) }
            .onFailure { Timber.tag(TAG).w(it, "Could not read the player state") }
            .getOrNull() ?: return emptyList()
        val seek = pendingSeek
        val transition = pendingTransition
        pendingSeek = false
        pendingTransition = false
        // The like / download flags can change without the queue changing (a like alone):
        // re-resolve on every sample so the companion's badges follow the phone
        val ids = read.items.map { it.trackId }
        flags = withContext(NzikDispatchers.DATA) { resolveFlags(ids.distinct()) }
        val queue = read.items.map { item ->
            val itemFlags = flags[item.trackId]
            TrackMapping.track(
                mediaId = item.mediaId,
                title = item.title,
                artist = item.artist,
                hasArtwork = item.hasArtwork,
                durationText = item.durationText,
                isLiked = itemFlags?.like == TrackLike.LIKED,
                isDownloaded = itemFlags?.isDownloaded == true,
                playerDurationMs = item.playerDurationMs,
                isExplicit = item.isExplicitExtra,
                like = itemFlags?.like,
                artworkUrl = item.artworkUrl,
            )
        }
        return hub.submit(read.sample.copy(queue = queue), seek = seek, transition = transition)
    }

    private suspend fun resolveFlags(trackIds: List<String>): Map<String, TrackFlags> {
        val downloads = MyDownloadHelper.downloads.value
        return trackIds.associateWith { id ->
            // Raw `likedAt`: a queue track absent from the database reads neutral
            val likedAt = runCatching { Database.songTable.getLikedAt(id) }
                .onFailure { Timber.tag(TAG).w(it, "Could not read the like state of a track") }
                .getOrNull()
            TrackFlags(like = TrackLike.of(likedAt), isDownloaded = downloads[id]?.state == Download.STATE_COMPLETED)
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
