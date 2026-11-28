package app.n_zik.android.components.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import app.n_zik.android.components.tab.SmartShuffle
import app.n_zik.android.components.tab.showSmartShuffleResult
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.enums.MaxSongs
import app.n_zik.android.enums.RecommendationsNumber
import app.n_zik.android.hasExplicitPrefix
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.utils.asSmartShuffleRecommendation
import app.n_zik.android.playback.utils.isSmartShuffleRecommendation
import app.n_zik.android.playback.utils.smartShuffleInsertionPositions
import app.n_zik.android.playback.utils.smartShuffleQueueOwner
import app.n_zik.android.ui.screens.localplaylist.loadLocalPlaylistRecommendations
import app.n_zik.android.ui.screens.player.QueueToolBarState
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.maxSongsInQueueKey
import app.n_zik.android.utils.normalizedSongId
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.player.withFilteredQueueItems
import app.n_zik.android.utils.podcastBrowseId
import app.n_zik.android.utils.recommendationsNumberKey
import app.n_zik.android.utils.rememberPreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val queueRecommendationOwner = "player-queue"
internal val LocalQueueSmartShuffle = compositionLocalOf<QueueSmartShuffle?> { null }

internal class QueueSmartShuffle(
    val active: Boolean,
    val loading: Boolean,
    val count: Int,
    val enabled: Boolean,
    val toggle: () -> Unit,
) {
    @Composable
    fun toolbarAction(selectionAllowsAction: Boolean) = SmartShuffle(
        isRecommendationEnabled = { active },
        isRecommendationsLoading = { loading },
        onToggleRecommendation = toggle,
        isActionAvailable = { enabled && selectionAllowsAction },
    )
}

internal val MediaItem?.isQueuePodcast: Boolean
    get() = this != null && (podcastBrowseId != null ||
        mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)

internal fun MediaItem.canSeedQueueRecommendations() =
    !isLocal && !isSmartShuffleRecommendation && !isQueuePodcast

internal fun <T> insertQueueRecommendations(upcoming: List<T>, additions: List<T>): List<T> {
    val byGap = smartShuffleInsertionPositions(upcoming.size, additions).entries.groupBy({ it.value }, { it.key })
    return buildList(upcoming.size + additions.size) {
        upcoming.forEachIndexed { index, item ->
            addAll(byGap[index].orEmpty())
            add(item)
        }
        addAll(byGap[upcoming.size].orEmpty())
    }
}

private fun Timeline.shuffledQueueIndices(): List<Int> = buildList(windowCount) {
    var index = getFirstWindowIndex(true)
    while (index != C.INDEX_UNSET && size < windowCount) {
        add(index)
        index = getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
    }
}

private fun removeShuffledQueueRecommendations(
    player: Player,
    order: List<Int>,
    removed: Set<Int>,
    setOrder: (IntArray) -> Unit,
) {
    if (removed.isEmpty()) return
    val newIndex = IntArray(order.size)
    var kept = 0
    for (index in newIndex.indices) {
        if (index !in removed) newIndex[index] = kept++
    }
    val descending = removed.sortedDescending()
    var end = descending.first() + 1
    var start = end - 1
    for (index in descending.drop(1)) {
        if (index == start - 1) start = index else {
            player.removeMediaItems(start, end)
            start = index
            end = index + 1
        }
    }
    player.removeMediaItems(start, end)
    setOrder(order.filterNot(removed::contains).map { newIndex[it] }.toIntArray())
}

private data class QueueRecommendationState(val hasSeed: Boolean, val lastOwnedIndex: Int, val count: Int, val size: Int, val shuffledPosition: IntArray?) {
    fun ownsUpcoming(current: Int) = lastOwnedIndex > (shuffledPosition?.getOrNull(current) ?: current)
}

private fun Timeline.queueRecommendationState(shuffle: Boolean): QueueRecommendationState {
    var hasSeed = false
    var lastOwnedIndex = -1
    var count = 0
    val window = Timeline.Window()
    for (index in 0 until windowCount) {
        val item = getWindow(index, window).mediaItem
        if (item.isSmartShuffleRecommendation) {
            count++
            if (item.smartShuffleQueueOwner == queueRecommendationOwner) lastOwnedIndex = index
        } else if (!hasSeed && item.canSeedQueueRecommendations()) {
            hasSeed = true
        }
    }
    val positions = if (shuffle && lastOwnedIndex >= 0) {
        IntArray(windowCount).also { positions ->
            shuffledQueueIndices().forEachIndexed { position, index ->
                positions[index] = position
                if (getWindow(index, window).mediaItem.smartShuffleQueueOwner == queueRecommendationOwner) {
                    lastOwnedIndex = position
                }
            }
        }
    } else null
    return QueueRecommendationState(hasSeed, lastOwnedIndex, count, windowCount, positions)
}

@Composable
internal fun rememberQueueSmartShuffle(binder: PlayerServiceModern.Binder?): QueueSmartShuffle? {
    val player = binder?.player ?: return null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connection by NetworkQualityHelper.isNetworkAvailableComposable(context)
    val offline by OfflineMode.rememberEnabled()
    val guestLocked by listenTogetherGuestLock
    val parentalControl by rememberPreference(parentalControlEnabledKey, false)
    val amount by rememberPreference(recommendationsNumberKey, RecommendationsNumber.Adaptive)
    val maxQueueSize by rememberPreference(maxSongsInQueueKey, MaxSongs.Unlimited)
    var state by remember(player) { mutableStateOf(player.currentTimeline.queueRecommendationState(player.shuffleModeEnabled)) }
    var currentIndex by remember(player) { mutableStateOf(player.currentMediaItemIndex) }
    var podcast by remember(player) { mutableStateOf(player.currentMediaItem.isQueuePodcast) }
    var request by remember(player) { mutableStateOf<Job?>(null) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                    currentIndex = player.currentMediaItemIndex
                    podcast = player.currentMediaItem.isQueuePodcast
                    if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                        state = player.currentTimeline.queueRecommendationState(player.shuffleModeEnabled)
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            request?.cancel()
        }
    }
    val canRequest = !offline && connection && state.hasSeed
    val active = state.ownsUpcoming(currentIndex)
    val loading = request != null
    if (podcast || guestLocked || (!canRequest && !active && !loading)) return null
    val enabled = !QueueToolBarState.selectionActive && !QueueToolBarState.dragInProgress && (active || state.size < maxQueueSize.toInt())
    return remember(player, active, loading, state.count, canRequest, enabled) {
        QueueSmartShuffle(active, loading, state.count, enabled) toggle@{
            if (binder.player !== player || listenTogetherGuestLock.value || QueueToolBarState.selectionActive || QueueToolBarState.dragInProgress || player.currentMediaItem.isQueuePodcast || request != null) return@toggle
            if (state.ownsUpcoming(player.currentMediaItemIndex)) {
                if (player.shuffleModeEnabled) {
                    val order = player.currentTimeline.shuffledQueueIndices()
                    val upcoming = order.drop(order.indexOf(player.currentMediaItemIndex) + 1)
                    val removed = upcoming.filterTo(HashSet()) {
                        player.getMediaItemAt(it).smartShuffleQueueOwner == queueRecommendationOwner
                    }
                    removeShuffledQueueRecommendations(player, order, removed) { updatedOrder ->
                        binder.setQueueSmartShuffleOrder(player, updatedOrder)
                    }
                    state = player.currentTimeline.queueRecommendationState(true)
                    return@toggle
                }
                val start = player.currentMediaItemIndex + 1
                val count = player.mediaItemCount
                val remaining = (start until count).map(player::getMediaItemAt)
                    .filterNot { it.smartShuffleQueueOwner == queueRecommendationOwner }
                if (remaining.size < count - start) player.replaceMediaItems(start, count, remaining)
                state = player.currentTimeline.queueRecommendationState(player.shuffleModeEnabled)
                return@toggle
            }
            if (!canRequest) return@toggle
            val timeline = player.currentTimeline
            if (timeline.isEmpty || player.mediaItemCount >= maxQueueSize.toInt()) return@toggle
            val anchor = timeline.getWindow(0, Timeline.Window()).uid
            val seeds = (0 until player.mediaItemCount).asSequence().map(player::getMediaItemAt)
                .filter(MediaItem::canSeedQueueRecommendations).map(MediaItem::asSong).distinctBy { it.id }.toList()
            if (seeds.isEmpty()) return@toggle
            request = scope.launch {
                try {
                    val recommended = withContext(NzikDispatchers.DATA) {
                        loadLocalPlaylistRecommendations(seeds, amount) {
                            !parentalControl || !it.title.hasExplicitPrefix()
                        }.keys.map { it.asMediaItem.asSmartShuffleRecommendation(true, queueRecommendationOwner) }
                    }
                    var addedCount = 0
                    withFilteredQueueItems(recommended, context, { binder.player.takeIf { it === player } }) { livePlayer, eligible ->
                        val liveTimeline = livePlayer.currentTimeline
                        if (offline || QueueToolBarState.selectionActive || QueueToolBarState.dragInProgress || livePlayer.currentMediaItem.isQueuePodcast || liveTimeline.isEmpty ||
                            liveTimeline.getWindow(0, Timeline.Window()).uid != anchor) return@withFilteredQueueItems
                        val current = livePlayer.currentMediaItemIndex
                        val count = livePlayer.mediaItemCount
                        if (current !in 0 until count) return@withFilteredQueueItems
                        val ids = (0 until count).mapTo(HashSet()) { livePlayer.getMediaItemAt(it).normalizedSongId }
                        val additions = eligible.filterNot { it.normalizedSongId in ids }
                            .take((maxQueueSize.toInt() - count).coerceAtLeast(0))
                        if (additions.isEmpty()) return@withFilteredQueueItems
                        if (livePlayer.shuffleModeEnabled) {
                            val order = liveTimeline.shuffledQueueIndices()
                            val split = order.indexOf(current) + 1
                            if (split == 0) return@withFilteredQueueItems
                            val mixed = insertQueueRecommendations(order.drop(split), additions.indices.map { count + it })
                            livePlayer.addMediaItems(additions)
                            binder.setQueueSmartShuffleOrder(livePlayer, (order.take(split) + mixed).toIntArray())
                        } else {
                            val start = current + 1
                            val upcoming = (start until count).map(livePlayer::getMediaItemAt)
                            // Publish a single edit while keeping the playing window.
                            livePlayer.replaceMediaItems(start, count, insertQueueRecommendations(upcoming, additions))
                        }
                        addedCount = (livePlayer.mediaItemCount - count).coerceAtLeast(0)
                        state = livePlayer.currentTimeline.queueRecommendationState(livePlayer.shuffleModeEnabled)
                    }
                    showSmartShuffleResult(addedCount)
                } finally {
                    request = null
                }
            }
        }
    }
}
