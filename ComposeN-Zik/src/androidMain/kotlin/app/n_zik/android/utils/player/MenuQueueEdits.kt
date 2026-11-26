package app.n_zik.android.utils.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.utils.cleaned
import app.n_zik.android.utils.enqueue
import app.n_zik.android.utils.launchForcePlayAtIndex
import app.n_zik.android.utils.normalizedSongId

/** Tracks menu requests across player swaps while allowing an explicit clear to invalidate them. */
@UnstableApi
internal class MenuQueueEdits(
    private val currentPlayer: () -> Player?,
    private val setShuffleOrder: (IntArray) -> Unit,
) {
    private var generation = 0L

    fun invalidate() {
        generation++
    }

    fun capture(): Request? {
        currentPlayer() ?: return null
        val capturedGeneration = generation
        return Request(
            currentPlayer = { if (generation == capturedGeneration) currentPlayer() else null },
            setShuffleOrder = setShuffleOrder,
        )
    }

    class Request(
        private val currentPlayer: () -> Player?,
        private val setShuffleOrder: (IntArray) -> Unit,
    ) {
        suspend fun queue(
            mediaItems: List<MediaItem>,
            context: Context,
            playNext: Boolean,
        ): Boolean = withFilteredQueueItems(mediaItems, context, currentPlayer) { player, items ->
            when {
                playNext -> player.addNextFromMenu(items, setShuffleOrder)
                player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED ->
                    player.launchForcePlayAtIndex(items, 0, canApplyQueue = { currentPlayer() === player })
                else -> player.enqueue(items)
            }
        }
    }
}

@UnstableApi
private fun Player.addNextFromMenu(items: List<MediaItem>, setShuffleOrder: (IntArray) -> Unit) {
    val currentIndex = currentMediaItemIndex
    val queueSize = mediaItemCount
    val cleanedItems = items.map(MediaItem::cleaned)
    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED || currentIndex !in 0 until queueSize) {
        setMediaItems(cleanedItems)
        if (playbackState == Player.STATE_IDLE) prepare()
        play()
        return
    }

    val shuffledIndices = if (shuffleModeEnabled) {
        val timeline = currentTimeline
        buildList(queueSize) {
            var index = timeline.getFirstWindowIndex(true)
            while (index != C.INDEX_UNSET) {
                add(index)
                index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
            }
        }
    } else emptyList()
    val edit = planMenuPlayNext(
        currentIndex = currentIndex,
        queueIds = List(queueSize) { getMediaItemAt(it).normalizedSongId },
        nextIds = items.map(MediaItem::normalizedSongId),
        shuffledIndices = shuffledIndices,
    )
    edit.removals.forEach(::removeMediaItem)
    addMediaItems(edit.insertionIndex, cleanedItems)
    edit.shuffleOrder?.let(setShuffleOrder)
}

internal data class MenuPlayNextPlan(
    val removals: List<Int>,
    val insertionIndex: Int,
    val shuffleOrder: IntArray?,
)

/** Replaces one queued occurrence per requested item without touching the current occurrence. */
@UnstableApi
internal fun planMenuPlayNext(
    currentIndex: Int,
    queueIds: List<String>,
    nextIds: List<String>,
    shuffledIndices: List<Int>,
): MenuPlayNextPlan {
    val otherOccurrences = queueIds.indices.filterNot { it == currentIndex }
        .groupBy { queueIds[it] }
        .mapValues { (_, indices) -> ArrayDeque(indices) }
    val removed = nextIds.mapNotNull { otherOccurrences[it]?.removeFirstOrNull() }.toSet()
    val insertionIndex = currentIndex + 1 - removed.count { it < currentIndex }
    val shuffleOrder = shuffledIndices.takeIf { it.size == queueIds.size }?.let { order ->
        var remainingIndex = 0
        val newIndices = IntArray(queueIds.size) { index ->
            if (index in removed) C.INDEX_UNSET
            else remainingIndex++.let { if (it < insertionIndex) it else it + nextIds.size }
        }
        buildList {
            order.forEach { oldIndex ->
                val index = newIndices[oldIndex]
                if (index != C.INDEX_UNSET) add(index)
                if (oldIndex == currentIndex) addAll(insertionIndex until insertionIndex + nextIds.size)
            }
        }.toIntArray()
    }
    return MenuPlayNextPlan(removed.sortedDescending(), insertionIndex, shuffleOrder)
}
