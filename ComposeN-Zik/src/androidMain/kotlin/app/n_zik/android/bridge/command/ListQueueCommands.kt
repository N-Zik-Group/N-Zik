package app.n_zik.android.bridge.command

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.forcePlayAtIndex
import app.n_zik.android.bridge.BridgeContract
import app.n_zik.android.bridge.BridgeJson
import app.n_zik.android.bridge.library.LibraryLists
import app.n_zik.android.bridge.library.LibraryProvider
import app.n_zik.android.bridge.library.ListRef
import app.n_zik.android.bridge.library.ListResolution
import app.n_zik.android.bridge.state.BridgeStateHub
import app.n_zik.android.core.database.Database
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.utils.Shuffler
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.player.addNextOffMain
import app.n_zik.android.utils.player.enqueueOffMain
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

private const val TAG = "BridgeListCommands"

/** What `/queue/list` does with the whole list (contract §9, since 1.10.0, `queue.fullList`). */
@Serializable
internal enum class ListAction {
    /** The phone's click on a list item: radio stopped, the whole list played from the item. */
    @SerialName("play") PLAY,

    /** The phone's shuffle button: `Shuffler.play` (its dislike filters, `maxSongsInQueue`, toasts). */
    @SerialName("shuffle") SHUFFLE,

    /** The phone's "play next": `addNextOffMain` (its dislike / duration filters). */
    @SerialName("next") NEXT,

    /** The phone's "enqueue": `enqueueOffMain` (same filters). */
    @SerialName("end") END,
}

/** Body of `POST /api/v1/queue/list` (contract §9, since 1.10.0). */
@Serializable
internal data class QueueListCommandBody(
    val list: ListRef,
    val action: ListAction,
    /** `play` only: index of the clicked item in the client's list (the resolved full list). */
    val startIndex: Int = 0,
    /** `play` only: id of the clicked item; when given, it wins over a stale [startIndex]. */
    val startTrackId: String? = null,
    val commandId: String? = null,
)

/** Outcome of a `/queue/list` command (contract §9). */
internal sealed interface ListCommandResult {
    /** `200`: handed to the phone's own queue entries; their changes arrive as deltas and toasts. */
    data class Applied(val revision: Long) : ListCommandResult

    data object BadRequest : ListCommandResult
    data object NotFound : ListCommandResult
    data object Rejected : ListCommandResult
    data object Unavailable : ListCommandResult
}

/** Applies `/queue/list` (contract §9, since 1.10.0). */
internal fun interface ListCommandExecutor {
    suspend fun execute(body: String): ListCommandResult

    companion object {
        val UNAVAILABLE = ListCommandExecutor { ListCommandResult.Unavailable }
    }
}

/**
 * The phone's own queue entries a list command goes through (decision 2026-10-09: the phone
 * applies its own filters and toasts). `false` when no player service is bound.
 */
internal interface PhoneQueue {
    /** `stopRadio` then `forcePlayAtIndex` — the phone's click on a list item. */
    suspend fun play(items: List<MediaItem>, index: Int): Boolean

    /** `Shuffler.play` — the phone's shuffle button. */
    suspend fun shuffle(items: List<MediaItem>): Boolean

    /** `addNextOffMain` — the phone's "play next". */
    suspend fun addNext(items: List<MediaItem>): Boolean

    /** `enqueueOffMain` — the phone's "enqueue". */
    suspend fun enqueue(items: List<MediaItem>): Boolean
}

/** Production [PhoneQueue]: the bound player service's binder, whose `player` is the guarded facade (AD-10). */
@OptIn(UnstableApi::class)
internal class BinderPhoneQueue(
    context: Context,
    private val binder: () -> PlayerServiceModern.Binder?,
) : PhoneQueue {
    private val appContext = context.applicationContext

    override suspend fun play(items: List<MediaItem>, index: Int): Boolean = withContext(NzikDispatchers.UI) {
        val current = binder() ?: return@withContext false
        current.stopRadio()
        current.player.forcePlayAtIndex(items, index)
        true
    }

    override suspend fun shuffle(items: List<MediaItem>): Boolean = withContext(NzikDispatchers.UI) {
        val current = binder() ?: return@withContext false
        Shuffler.play(current, items)
        true
    }

    override suspend fun addNext(items: List<MediaItem>): Boolean = withContext(NzikDispatchers.UI) {
        val current = binder() ?: return@withContext false
        current.player.addNextOffMain(items, appContext)
        true
    }

    override suspend fun enqueue(items: List<MediaItem>): Boolean = withContext(NzikDispatchers.UI) {
        val current = binder() ?: return@withContext false
        current.player.enqueueOffMain(items, appContext)
        true
    }
}

/** Media items of the tracks of a resolved list, in order; tracks gone from the database are skipped. */
internal fun interface ListItemResolver {
    suspend fun resolve(trackIds: List<String>): List<Pair<String, MediaItem>>
}

/**
 * Resolves the list against the phone's `Song` table, off the main thread, in batched `IN` queries
 * ([CHUNK] ids each, under SQLite's bound-parameter limit), then back in the list's order.
 */
@OptIn(UnstableApi::class)
internal object DatabaseListItemResolver : ListItemResolver {
    private const val CHUNK = 900

    override suspend fun resolve(trackIds: List<String>): List<Pair<String, MediaItem>> = withContext(NzikDispatchers.DATA) {
        val songs = trackIds.distinct().chunked(CHUNK)
            .flatMap { Database.songTable.findByIdsDirect(it) }
            .associateBy { it.id }
        orderResolved(trackIds) { id -> songs[id]?.asMediaItem }
    }
}

/** [trackIds] in order, each with its resolved item; ids that do not resolve are skipped. */
internal fun <T> orderResolved(trackIds: List<String>, lookup: (String) -> T?): List<Pair<String, T>> =
    trackIds.mapNotNull { id -> lookup(id)?.let { id to it } }

/**
 * `/queue/list` (contract §9, since 1.10.0): the phone resolves the WHOLE list named by the
 * client ([LibraryLists], the same reads as the paged list routes) and hands it to its own
 * queue entries ([PhoneQueue]), which apply its filters, its `maxSongsInQueue` cap and its
 * toasts. The answer never waits for the queue: `changed` is always `false` and the result
 * shows through the usual deltas (and the `toast` message, `ui.toasts`).
 */
internal class ListQueueExecutor(
    private val library: LibraryProvider,
    private val queue: PhoneQueue,
    private val hub: BridgeStateHub,
    private val guestLocked: suspend () -> Boolean,
    private val items: ListItemResolver = DatabaseListItemResolver,
) : ListCommandExecutor {
    private val mutex = Mutex()

    override suspend fun execute(body: String): ListCommandResult = mutex.withLock {
        val command = parse(body) ?: return@withLock ListCommandResult.BadRequest
        if (guestLocked()) return@withLock ListCommandResult.Rejected
        val tracks = when (val resolved = LibraryLists.resolve(library, command.list)) {
            is ListResolution.Found -> resolved.tracks
            ListResolution.Invalid -> return@withLock ListCommandResult.BadRequest
            ListResolution.Unknown -> return@withLock ListCommandResult.NotFound
        }
        val resolved = items.resolve(tracks.map { it.id })
        val mediaItems = resolved.map { it.second }
        val applied = when (command.action) {
            ListAction.PLAY -> {
                val start = startIndexOf(command, resolved.map { it.first }) ?: return@withLock ListCommandResult.NotFound
                queue.play(mediaItems, start)
            }
            // An empty list is the phone's own case: Shuffler answers with its no_song_to_shuffle toast
            ListAction.SHUFFLE -> queue.shuffle(mediaItems)
            ListAction.NEXT -> mediaItems.isEmpty() || queue.addNext(mediaItems)
            ListAction.END -> mediaItems.isEmpty() || queue.enqueue(mediaItems)
        }
        Timber.tag(TAG).d("${command.action} on ${command.list.kind} (${mediaItems.size} tracks) -> $applied")
        if (applied) ListCommandResult.Applied(hub.currentRevision) else ListCommandResult.Unavailable
    }

    companion object {
        /** Decodes and validates a body; `null` → `400`. */
        fun parse(body: String): QueueListCommandBody? {
            val json = runCatching { BridgeJson.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
            val command = runCatching { BridgeJson.decodeFromJsonElement(QueueListCommandBody.serializer(), json) }.getOrNull()
                ?: return null
            if (command.commandId != null && command.commandId.length > BridgeContract.COMMAND_ID_MAX_LENGTH) return null
            if (command.startIndex < 0) return null
            return command
        }

        /** [QueueListCommandBody.startTrackId] when it is in the list, else a valid [QueueListCommandBody.startIndex]. */
        fun startIndexOf(command: QueueListCommandBody, ids: List<String>): Int? {
            command.startTrackId?.let { id -> ids.indexOf(id).takeIf { it >= 0 }?.let { return it } }
            return command.startIndex.takeIf { it in ids.indices && command.startTrackId == null }
        }
    }
}

/** Answer of `GET /api/v1/library/locate` (contract §10.4, since 1.10.0): `-1` when absent. */
@Serializable
internal data class LocateResponse(val index: Int, val total: Int)
