// Ported from Metrolist (GPL-3.0, https://github.com/MetrolistGroup/Metrolist).

package app.n_zik.android.listentogether

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

internal fun canonicalPlaybackQueue(
    currentTrack: TrackInfo,
    upcomingQueue: List<TrackInfo>,
): List<TrackInfo> =
    buildList {
        add(currentTrack)
        upcomingQueue
            .asSequence()
            .filter { it.id != currentTrack.id }
            .distinctBy { it.id }
            .forEach(::add)
    }

internal fun <T> upcomingQueueItems(
    queue: List<T>,
    currentIndex: Int,
): List<T> = if (currentIndex in queue.indices) queue.drop(currentIndex + 1) else emptyList()

internal class ListenTogetherGuestQueue(
    private val scope: CoroutineScope,
    private val tag: String,
) {
    private var queueSyncGeneration = 0
    private var queueMutationJob: Job? = null

    fun invalidate() {
        queueSyncGeneration++
        queueMutationJob?.cancel()
        queueMutationJob = null
    }

    private fun enqueueQueueMutation(
        generation: Int,
        mutation: suspend () -> Unit,
    ) {
        val previous = queueMutationJob
        queueMutationJob =
            scope.launch {
                previous?.join()
                if (generation != queueSyncGeneration) return@launch
                mutation()
            }
    }

    @UnstableApi
    fun replaceUpcoming(
        player: Player,
        currentTrack: TrackInfo?,
        queue: List<TrackInfo>,
    ) {
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex < 0) return
        val mediaItems =
            currentTrack
                ?.let { canonicalPlaybackQueue(it, queue).drop(1) }
                .orEmpty()
                .asSequence()
                .map { ListenTogetherPlayerBridge.trackToMediaItem(it) }
                .toList()

        ListenTogetherPlayerBridge.allowInternalSync = true
        try {
            player.shuffleModeEnabled = false
            if (player.mediaItemCount > currentIndex + 1) {
                player.removeMediaItems(currentIndex + 1, player.mediaItemCount)
            }
            if (mediaItems.isNotEmpty()) {
                player.addMediaItems(currentIndex + 1, mediaItems)
            }
        } finally {
            ListenTogetherPlayerBridge.allowInternalSync = false
        }
    }

    @UnstableApi
    fun addLegacy(track: TrackInfo, insertNext: Boolean) {
        val actionQueueGeneration = queueSyncGeneration
        enqueueQueueMutation(actionQueueGeneration) {
            if (queueSyncGeneration != actionQueueGeneration) return@enqueueQueueMutation
            // Host metadata lets stream resolution stay lazy.
            val mediaItem = ListenTogetherPlayerBridge.trackToMediaItem(track)
            val p = ListenTogetherPlayerBridge.player
            if (p == null) return@enqueueQueueMutation
            ListenTogetherPlayerBridge.allowInternalSync = true
            try {
                if (insertNext) {
                    p.addMediaItems(p.currentMediaItemIndex + 1, listOf(mediaItem))
                } else {
                    p.addMediaItem(mediaItem)
                }
            } finally {
                ListenTogetherPlayerBridge.allowInternalSync = false
            }
        }
    }

    @UnstableApi
    fun removeLegacy(removeId: String) {
        val actionQueueGeneration = queueSyncGeneration
        enqueueQueueMutation(actionQueueGeneration) {
            if (queueSyncGeneration != actionQueueGeneration) return@enqueueQueueMutation
            val p = ListenTogetherPlayerBridge.player ?: return@enqueueQueueMutation
            val startIndex = p.currentMediaItemIndex + 1
            var removeIndex = -1
            val total = p.mediaItemCount
            for (i in startIndex until total) {
                val id = p.getMediaItemAt(i).mediaId
                if (id == removeId || id.split("/").lastOrNull() == removeId) {
                    removeIndex = i
                    break
                }
            }
            if (removeIndex >= 0) {
                Timber.tag(tag).d("Guest: QUEUE_REMOVE index=$removeIndex id=$removeId")
                p.removeMediaItem(removeIndex)
            } else {
                Timber.tag(tag).w("QUEUE_REMOVE id not found in queue: $removeId")
            }
        }
    }

    @UnstableApi
    fun clearLegacy() {
        val actionQueueGeneration = queueSyncGeneration
        enqueueQueueMutation(actionQueueGeneration) {
            if (queueSyncGeneration != actionQueueGeneration) return@enqueueQueueMutation
            val p = ListenTogetherPlayerBridge.player ?: return@enqueueQueueMutation
            val currentIndex = p.currentMediaItemIndex
            val count = p.mediaItemCount
            val itemsAfter = count - (currentIndex + 1)
            if (itemsAfter > 0) {
                Timber.tag(tag).d("Guest: QUEUE_CLEAR removing $itemsAfter items after current")
                p.removeMediaItems(currentIndex + 1, count - (currentIndex + 1))
            }
        }
    }
}
