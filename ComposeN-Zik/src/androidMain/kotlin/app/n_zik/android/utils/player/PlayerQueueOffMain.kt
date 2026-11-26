package app.n_zik.android.utils.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.listentogether.ListenTogetherGuestGuardPlayer
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.utils.addNext
import app.n_zik.android.utils.enqueue
import app.n_zik.android.utils.excludeMediaItems
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext

@UnstableApi
suspend fun Player.addNextOffMain(mediaItems: List<MediaItem>, context: Context): Boolean =
    queueMediaItemsOffMain(mediaItems, context, playNext = true) { this }

@UnstableApi
suspend fun Player.enqueueOffMain(mediaItems: List<MediaItem>, context: Context): Boolean =
    queueMediaItemsOffMain(mediaItems, context, playNext = false) { this }

/** Filters off the caller thread, then resolves the current player immediately before the queue edit. */
@UnstableApi
internal suspend fun queueMediaItemsOffMain(
    mediaItems: List<MediaItem>,
    context: Context,
    playNext: Boolean,
    currentPlayer: () -> Player?,
): Boolean = withFilteredQueueItems(mediaItems, context, currentPlayer) { player, filteredMediaItems ->
    if (playNext) player.addNext(filteredMediaItems, context = null)
    else player.enqueue(filteredMediaItems, context = null)
}

internal suspend fun withFilteredQueueItems(
    mediaItems: List<MediaItem>,
    context: Context,
    currentPlayer: () -> Player?,
    applyEdit: (Player, List<MediaItem>) -> Unit,
): Boolean {
    if (mediaItems.isEmpty()) return false
    val filterReceiver = currentPlayer() ?: return false
    val filteredMediaItems = withContext(NzikDispatchers.DATA) {
        filterReceiver.excludeMediaItems(mediaItems, context, reportAllExcluded = true)
    }
    if (filteredMediaItems.isEmpty()) return false
    if (listenTogetherGuestLock.value) {
        ListenTogetherGuestGuardPlayer.reportUiBlockedOp(context)
        return false
    }
    val player = currentPlayer() ?: return false
    applyEdit(player, filteredMediaItems)
    return true
}
