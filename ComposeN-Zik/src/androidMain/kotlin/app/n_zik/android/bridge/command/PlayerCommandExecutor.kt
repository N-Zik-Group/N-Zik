package app.n_zik.android.bridge.command

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.findMediaItemIndexById
import app.it.fast4x.rimusic.utils.jumpPreviousKey
import app.it.fast4x.rimusic.utils.playNext
import app.it.fast4x.rimusic.utils.playPrevious
import app.it.fast4x.rimusic.utils.playbackSpeedKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.restoreGlobalVolume
import app.n_zik.android.bridge.AddPosition
import app.n_zik.android.bridge.BridgeContract
import app.n_zik.android.bridge.BridgeErrorCode
import app.n_zik.android.bridge.state.BridgeStateHub
import app.n_zik.android.bridge.state.DeltaMessage
import app.n_zik.android.bridge.state.ErrorMessage
import app.n_zik.android.bridge.state.PlayerStateReader
import app.n_zik.android.bridge.state.RepeatModeDto
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val TAG = "BridgeCommands"

/**
 * Main-thread access to the guarded player facade (`Binder.player`), re-read at every call
 * and never cached. `null` when no player service is bound.
 */
internal interface PlayerAccess {
    /** Runs [block] on the main thread with the current facade. */
    suspend fun <T> read(block: (Player?) -> T): T

    /**
     * Runs [block] on the main thread with the current facade, then samples the player at
     * once and publishes the result; no other sample interleaves between the two, and the
     * hub revision is read before any other sample can publish.
     */
    suspend fun <T> applyAndSample(block: (Player?) -> T): Sampled<T>
}

/** Result of [PlayerAccess.applyAndSample]: [deltas] published by the sample, [revision] of the hub right after it. */
internal data class Sampled<T>(val result: T, val deltas: List<DeltaMessage>, val revision: Long)

/** Phone settings a command shares with the phone's own controls. */
internal interface PlayerSettings {
    /** `jumpPreviousKey`: below this position (s) "previous" goes to the previous track; `0` = always. */
    fun jumpPreviousSeconds(): Int

    /** `playbackSpeedKey`, written like the phone's speed menu. */
    fun saveSpeed(speed: Float)
}

/** Library lookup of the tracks a queue command loads. */
internal fun interface TrackResolver {
    /** Media items of [trackIds] in the same order, or `null` when one of them is not in the library. */
    suspend fun resolve(trackIds: List<String>): List<MediaItem>?
}

internal class PreferencePlayerSettings(context: Context) : PlayerSettings {
    private val appContext = context.applicationContext

    // Same reading as the phone's previous button: an empty value means "0"
    override fun jumpPreviousSeconds(): Int =
        appContext.preferences.getString(jumpPreviousKey, "3")?.trim()?.toIntOrNull() ?: 0

    override fun saveSpeed(speed: Float) {
        appContext.preferences.edit().putFloat(playbackSpeedKey, speed).apply()
    }
}

/** Resolves track ids against the phone's `Song` table, off the main thread. */
internal object DatabaseTrackResolver : TrackResolver {
    @OptIn(UnstableApi::class)
    override suspend fun resolve(trackIds: List<String>): List<MediaItem>? = withContext(NzikDispatchers.DATA) {
        val songs = trackIds.distinct().associateWith { id -> Database.songTable.findByIdDirect(id) }
        val items = ArrayList<MediaItem>(trackIds.size)
        for (id in trackIds) {
            val song = songs[id] ?: return@withContext null
            items += song.asMediaItem
        }
        items
    }
}

/**
 * Remembers the last command that loaded a track; a player error within
 * [BridgeContract.LATE_FAILURE_WINDOW_MS] is broadcast as a WS `error` carrying its
 * `commandId` (contract §7.6), once.
 */
internal class LateFailureTracker(
    private val hub: BridgeStateHub,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Pending(val commandId: String?, val atMs: Long)

    private val lock = Any()
    private var pending: Pending? = null

    fun trackLoad(commandId: String?) {
        synchronized(lock) { pending = Pending(commandId, clock()) }
    }

    fun onPlayerError(reason: String) {
        val failed = synchronized(lock) {
            val current = pending ?: return
            pending = null
            current.takeIf { clock() - it.atMs <= BridgeContract.LATE_FAILURE_WINDOW_MS }
        } ?: return
        Timber.tag(TAG).w("Player error after a bridge command: $reason")
        hub.broadcast(ErrorMessage(BridgeErrorCode.PLAYER_REJECTED, "The track could not be played ($reason)", failed.commandId))
    }
}

/**
 * Applies the commands of contract §9 through the guarded player facade only (AD-10), one
 * at a time. A refusal known in advance is answered without touching the player: the facade
 * ignores blocked calls silently, so the Listen Together guest lock is read first. After
 * each command the player is sampled at once: the deltas published tell whether it changed
 * something; a playback still buffering counts as a change whose delta is still to come.
 */
@OptIn(UnstableApi::class)
internal class PlayerCommandExecutor(
    private val player: PlayerAccess,
    private val hub: BridgeStateHub,
    private val lateFailures: LateFailureTracker,
    private val settings: PlayerSettings,
    private val tracks: TrackResolver = DatabaseTrackResolver,
    /** Listen Together guest lock, read on the main thread at every command. */
    private val guestLocked: () -> Boolean,
) : BridgeCommandExecutor {

    private sealed interface Outcome {
        data class Refused(val result: Refusal) : Outcome

        /** [awaitingDelta]: the change shows later (buffering, asynchronous queue load). */
        data class Done(val awaitingDelta: Boolean = false) : Outcome
    }

    private enum class Refusal { UNAVAILABLE, REJECTED, MISMATCH }

    private val mutex = Mutex()

    override suspend fun execute(command: BridgeCommand): CommandResult = mutex.withLock {
        val action = command.action
        val trackIds = when (action) {
            is PlayerAction.QueuePlay -> action.trackIds
            is PlayerAction.QueueAdd -> action.trackIds.distinct()
            else -> null
        }
        var items = emptyList<MediaItem>()
        if (trackIds != null) {
            // Cheap refusals first, then the library lookup, before anything is applied
            val refusal = player.read { current -> precheck(current, action) }
            if (refusal != null) return@withLock refusal.toResult(hub.currentRevision)
            items = tracks.resolve(trackIds) ?: return@withLock CommandResult.NotFound
        }
        val (outcome, deltas, revision) = player.applyAndSample { current ->
            precheck(current, action)?.let { return@applyAndSample Outcome.Refused(it) }
            // precheck returned null: the facade is there
            current?.let { apply(it, command, items) } ?: Outcome.Refused(Refusal.UNAVAILABLE)
        }
        when (outcome) {
            is Outcome.Refused -> outcome.result.toResult(revision)
            is Outcome.Done -> when {
                deltas.isNotEmpty() -> CommandResult.Applied(changed = true, revision = revision)
                outcome.awaitingDelta -> CommandResult.Applied(changed = true, revision = revision + 1)
                else -> CommandResult.Applied(changed = false, revision = revision)
            }
        }.also { Timber.tag(TAG).d("${action::class.simpleName} -> ${it::class.simpleName}") }
    }

    private fun Refusal.toResult(revision: Long): CommandResult = when (this) {
        Refusal.UNAVAILABLE -> CommandResult.Unavailable
        Refusal.REJECTED -> CommandResult.Rejected
        Refusal.MISMATCH -> CommandResult.QueueMismatch(revision)
    }

    private fun precheck(current: Player?, action: PlayerAction): Refusal? = when {
        current == null -> Refusal.UNAVAILABLE
        action.guarded && guestLocked() -> Refusal.REJECTED
        else -> null
    }

    private fun apply(p: Player, command: BridgeCommand, items: List<MediaItem>): Outcome {
        val commandId = command.commandId
        return when (val action = command.action) {
            PlayerAction.Play -> {
                val idle = p.playbackState == Player.STATE_IDLE
                if (idle) p.prepare()
                p.play()
                val awaiting = awaitingPlayback(p)
                // Loading (from idle) or buffering: a failure can still follow
                if (idle || awaiting) lateFailures.trackLoad(commandId)
                Outcome.Done(awaiting)
            }
            PlayerAction.Pause -> {
                p.pause()
                Outcome.Done()
            }
            is PlayerAction.Seek -> {
                if (p.mediaItemCount == 0) return Outcome.Refused(Refusal.MISMATCH)
                val duration = p.duration
                val target = if (duration != C.TIME_UNSET && duration > 0) action.positionMs.coerceAtMost(duration) else action.positionMs
                p.seekTo(target)
                Outcome.Done()
            }
            PlayerAction.Next -> {
                p.playNext()
                lateFailures.trackLoad(commandId)
                Outcome.Done(awaitingPlayback(p))
            }
            PlayerAction.Previous -> {
                // Same rule as the phone's previous button (Modern controls)
                val jumpSeconds = settings.jumpPreviousSeconds()
                if (!p.hasPreviousMediaItem() || (jumpSeconds != 0 && p.currentPosition > jumpSeconds * 1_000L)) {
                    p.seekTo(0)
                    Outcome.Done()
                } else {
                    p.playPrevious()
                    lateFailures.trackLoad(commandId)
                    Outcome.Done(awaitingPlayback(p))
                }
            }
            is PlayerAction.Speed -> {
                // Pitch unchanged, and persisted like the phone's speed menu
                p.playbackParameters = PlaybackParameters(action.speed, p.playbackParameters.pitch)
                settings.saveSpeed(action.speed)
                Outcome.Done()
            }
            is PlayerAction.Repeat -> {
                p.repeatMode = when (action.mode) {
                    RepeatModeDto.OFF -> Player.REPEAT_MODE_OFF
                    RepeatModeDto.ONE -> Player.REPEAT_MODE_ONE
                    RepeatModeDto.ALL -> Player.REPEAT_MODE_ALL
                }
                Outcome.Done()
            }
            is PlayerAction.Shuffle -> {
                p.shuffleModeEnabled = action.enabled
                Outcome.Done()
            }
            is PlayerAction.QueuePlay -> {
                p.setMediaItems(items, action.startIndex, action.positionMs)
                p.prepare()
                p.restoreGlobalVolume()
                p.playWhenReady = true
                lateFailures.trackLoad(commandId)
                Outcome.Done(awaitingPlayback(p))
            }
            is PlayerAction.QueueAdd -> addToQueue(p, action.position, items, commandId)
            is PlayerAction.QueueRemove -> {
                val window = PlayerStateReader.windowIndexOf(p, action.index, action.trackId)
                    ?: return Outcome.Refused(Refusal.MISMATCH)
                val removesCurrent = window == p.currentMediaItemIndex
                p.removeMediaItem(window)
                if (removesCurrent && p.playWhenReady && p.mediaItemCount > 0) lateFailures.trackLoad(commandId)
                Outcome.Done()
            }
            is PlayerAction.QueueMove -> {
                // Decision 2026-09-30: a move is only defined in the natural order
                if (p.shuffleModeEnabled) return Outcome.Refused(Refusal.REJECTED)
                val from = PlayerStateReader.windowIndexOf(p, action.fromIndex, action.trackId)
                if (from == null || action.toIndex !in 0 until p.mediaItemCount) return Outcome.Refused(Refusal.MISMATCH)
                p.moveMediaItem(from, action.toIndex)
                Outcome.Done()
            }
            is PlayerAction.QueueJump -> {
                val window = PlayerStateReader.windowIndexOf(p, action.index, action.trackId)
                    ?: return Outcome.Refused(Refusal.MISMATCH)
                p.seekTo(window, 0L)
                p.prepare()
                p.restoreGlobalVolume()
                p.playWhenReady = true
                lateFailures.trackLoad(commandId)
                Outcome.Done(awaitingPlayback(p))
            }
            PlayerAction.QueueClear -> {
                p.stop()
                p.clearMediaItems()
                Outcome.Done()
            }
        }
    }

    /**
     * `/queue/add` through the phone's own helpers, without filters (decision 2026-09-30): a
     * track already queued is moved instead of duplicated, and a stopped player starts. At the
     * end of a running queue, a queued track (the current one included) is moved there, so
     * playback goes on; the others are enqueued in order.
     */
    private fun addToQueue(p: Player, position: AddPosition, items: List<MediaItem>, commandId: String?): Outcome {
        val stopped = p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED
        when (position) {
            AddPosition.NEXT -> p.addNext(items)
            AddPosition.END -> if (stopped) {
                p.enqueue(items)
            } else {
                items.forEach { item ->
                    val index = p.findMediaItemIndexById(item.mediaId)
                    if (index >= 0) p.moveMediaItem(index, p.mediaItemCount - 1) else p.enqueue(item)
                }
            }
        }
        if (stopped) lateFailures.trackLoad(commandId)
        // From a stopped player, `enqueue` loads the queue asynchronously: its delta comes later
        return Outcome.Done(awaitingDelta = (stopped && position == AddPosition.END) || awaitingPlayback(p))
    }

    /** Playback requested but not started yet (buffering): its `playbackChanged` is still to come. */
    private fun awaitingPlayback(p: Player): Boolean =
        p.playWhenReady && !p.isPlaying && p.mediaItemCount > 0 && p.playbackState == Player.STATE_BUFFERING
}
