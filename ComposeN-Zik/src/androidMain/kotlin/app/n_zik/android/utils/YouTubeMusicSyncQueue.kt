package app.n_zik.android.utils

import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import timber.log.Timber

sealed class SyncOperation {
    data object FullSync : SyncOperation()
    data class SinglePlaylist(val browseId: String, val playlistId: Long) : SyncOperation()
    data object CleanupDuplicates : SyncOperation()

    // Pushes (App → YTM)
    data class PushLikedSongs(val force: Boolean = false) : SyncOperation()
    data class PushAlbumBookmarks(val force: Boolean = false) : SyncOperation()
    data class PushArtistFollows(val force: Boolean = false) : SyncOperation()
    data class PushPlaylists(val force: Boolean = false) : SyncOperation()
    data class PushEpisodes(val force: Boolean = false) : SyncOperation()
}

data class SyncItemStatus(
    val state: String = "idle",
    val detail: SyncStatusDetail? = null
)

data class SyncStatus(
    val isRunning: Boolean = false,
    val currentOperation: String = "",
    val likedSongs: SyncItemStatus = SyncItemStatus(),
    val librarySongs: SyncItemStatus = SyncItemStatus(),
    val uploadedSongs: SyncItemStatus = SyncItemStatus(),
    val likedAlbums: SyncItemStatus = SyncItemStatus(),
    val uploadedAlbums: SyncItemStatus = SyncItemStatus(),
    val artists: SyncItemStatus = SyncItemStatus(),
    val playlists: SyncItemStatus = SyncItemStatus(),
    val episodes: SyncItemStatus = SyncItemStatus(),
    val pushLikedSongs: SyncItemStatus = SyncItemStatus(),
    val pushAlbumBookmarks: SyncItemStatus = SyncItemStatus(),
    val pushArtistFollows: SyncItemStatus = SyncItemStatus(),
    val pushPlaylists: SyncItemStatus = SyncItemStatus(),
    val pushEpisodes: SyncItemStatus = SyncItemStatus()
)

private val _syncStatus = MutableStateFlow(SyncStatus())
val syncStatus: StateFlow<SyncStatus> = _syncStatus

private var syncChannel = Channel<SyncOperation>(Channel.BUFFERED)
private val syncScope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
@Volatile private var processingJob: kotlinx.coroutines.Job? = null
private val syncMutex = Mutex()
private val startLock = Any()
internal class SyncDetailCapture : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SyncDetailCapture>

    var detail: SyncStatusDetail? = null
}

// Coalescing: track queued operation keys to prevent duplicates
private val queuedOperationKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

private fun SyncOperation.coalescingKey(): String? = when (this) {
    is SyncOperation.FullSync -> "full"
    is SyncOperation.SinglePlaylist -> "playlist:${browseId}"
    is SyncOperation.CleanupDuplicates -> "cleanup"
    is SyncOperation.PushLikedSongs -> "push:likedSongs"
    is SyncOperation.PushAlbumBookmarks -> "push:albumBookmarks"
    is SyncOperation.PushArtistFollows -> "push:artistFollows"
    is SyncOperation.PushPlaylists -> "push:playlists"
    is SyncOperation.PushEpisodes -> "push:episodes"
}

private suspend fun <T> withRetry(
    maxRetries: Int = 3,
    initialDelay: Long = 1000L,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(maxRetries - 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag("SyncYTMusicUtils").w("Retry ${attempt + 1}/$maxRetries after ${currentDelay}ms: ${e.message}")
            kotlinx.coroutines.delay(currentDelay)
            currentDelay *= 2
        }
    }
    return block()
}

private fun isCoveredByFullSync(operation: SyncOperation): Boolean = when (operation) {
    is SyncOperation.FullSync, is SyncOperation.CleanupDuplicates -> false
    is SyncOperation.SinglePlaylist -> "full" in queuedOperationKeys
    else -> false
}

private fun startSyncQueue() {
    synchronized(startLock) {
        if (processingJob?.isActive == true) return
        processingJob = syncScope.launch {
            for (operation in syncChannel) {
            // Remove from coalescing set
            operation.coalescingKey()?.let(queuedOperationKeys::remove)

            // Skip individual imports if FullSync is already queued/running
            if (isCoveredByFullSync(operation)) {
                Timber.tag("SyncYTMusicUtils").d("Skipping $operation — covered by FullSync")
                continue
            }

            syncMutex.lock()
            try {
                _syncStatus.update { it.copy(isRunning = true, currentOperation = operation::class.simpleName.orEmpty()) }
                executeOperation(operation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("SyncYTMusicUtils").e(e, "Sync operation failed: $operation")
            } finally {
                _syncStatus.update { it.copy(isRunning = false, currentOperation = "") }
                syncMutex.unlock()
            }
        }
    }
}
}

fun queueSync(operation: SyncOperation) {
    val key = operation.coalescingKey()
    if (key != null && !queuedOperationKeys.add(key)) {
        Timber.tag("SyncYTMusicUtils").d("Skipping duplicate sync operation: $operation")
        return // Already queued
    }
    startSyncQueue()
    val result = syncChannel.trySend(operation)
    if (result.isFailure) {
        key?.let(queuedOperationKeys::remove)
        Timber.tag("SyncYTMusicUtils").w("Failed to enqueue sync operation: $operation")
    }
}

fun cancelAllSyncs() {
    processingJob?.cancel()
    processingJob = null
    queuedOperationKeys.clear()
    syncChannel.cancel()
    syncChannel = Channel(Channel.BUFFERED)
    _syncStatus.value = SyncStatus()
    Timber.tag("SyncYTMusicUtils").d("All sync operations cancelled")
}

private suspend fun trackSyncItem(
    replace: SyncStatus.(SyncItemStatus) -> SyncStatus,
    operation: suspend () -> Boolean,
) {
    val capture = SyncDetailCapture()
    _syncStatus.update { it.replace(SyncItemStatus("syncing")) }
    try {
        val succeeded = withContext(capture) {
            withRetry {
                capture.detail = null
                operation()
            }
        }
        val state = if (succeeded) "idle" else "error"
        _syncStatus.update { it.replace(SyncItemStatus(state, capture.detail)) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val detail = capture.detail ?: SyncStatusDetail.Text(R.string.common_error_unknown)
        _syncStatus.update { it.replace(SyncItemStatus("error", detail)) }
        throw e
    }
}

private suspend fun executeOperation(operation: SyncOperation) {
    when (operation) {
        is SyncOperation.FullSync -> {
            trackSyncItem({ item -> copy(likedSongs = item) }) { importYTMLikedSongs(force = true) }
            trackSyncItem({ item -> copy(librarySongs = item) }) { importYTMLibrarySongs(force = true) }
            trackSyncItem({ item -> copy(uploadedSongs = item) }) { importYTMUploadedSongs(force = true) }
            trackSyncItem({ item -> copy(likedAlbums = item) }) { importYTMLikedAlbums(force = true) }
            trackSyncItem({ item -> copy(uploadedAlbums = item) }) { importYTMUploadedAlbums(force = true) }
            trackSyncItem({ item -> copy(artists = item) }) { importYTMSubscribedChannels(force = true) }
            trackSyncItem({ item -> copy(playlists = item) }) { importYTMPlaylists(force = true) }
            trackSyncItem({ item -> copy(episodes = item) }) { importYTMEpisodesForLater(force = true) }
            withRetry { cleanupDuplicatePlaylists() }
            setLastSyncTime()
        }
        is SyncOperation.SinglePlaylist -> {
            val playlist = Database.playlistTable.findById(operation.playlistId).first()
            if (playlist != null) {
                withRetry { ytmPrivatePlaylistSync(playlist, operation.playlistId) }
            }
        }
        is SyncOperation.CleanupDuplicates -> {
            cleanupDuplicatePlaylists()
        }

        // Push operations (App → YTM) — respect direction + individual toggles
        is SyncOperation.PushLikedSongs -> trackSyncItem({ item -> copy(pushLikedSongs = item) }) {
            pushYTMLikedSongs(force = operation.force)
        }
        is SyncOperation.PushAlbumBookmarks -> trackSyncItem({ item -> copy(pushAlbumBookmarks = item) }) {
            pushYTMAlbumBookmarks(force = operation.force)
        }
        is SyncOperation.PushArtistFollows -> trackSyncItem({ item -> copy(pushArtistFollows = item) }) {
            pushYTMArtistFollows(force = operation.force)
        }
        is SyncOperation.PushPlaylists -> trackSyncItem({ item -> copy(pushPlaylists = item) }) {
            pushYTMPlaylists(force = operation.force)
        }
        is SyncOperation.PushEpisodes -> trackSyncItem({ item -> copy(pushEpisodes = item) }) {
            pushYTMSavedEpisodes(force = operation.force)
        }
    }
}
