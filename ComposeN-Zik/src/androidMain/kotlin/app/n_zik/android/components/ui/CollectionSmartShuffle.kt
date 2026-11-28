package app.n_zik.android.components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.navigation.NavController
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.search.matchesCollectionSearch
import app.n_zik.android.utils.forcePlayAtIndex
import app.n_zik.android.R
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.enums.RecommendationsNumber
import app.n_zik.android.enums.MaxSongs
import app.n_zik.android.hasExplicitPrefix
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.playback.utils.asSmartShuffleRecommendation
import app.n_zik.android.playback.utils.spreadSmartShuffleRecommendations
import app.n_zik.android.playback.utils.smartShuffleInsertionPositions
import kotlin.random.Random
import app.n_zik.android.typography
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.components.tab.SmartShuffle
import app.n_zik.android.components.tab.showSmartShuffleResult
import app.n_zik.android.ui.screens.localplaylist.loadLocalPlaylistRecommendations
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.durationTextToMillis
import app.n_zik.android.utils.excludeMediaItems
import app.n_zik.android.utils.maxSongsInQueueKey
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.recommendationsNumberKey
import app.n_zik.android.utils.rememberPreference
import kotlinx.coroutines.withContext

internal class CollectionSmartShuffle(
    val available: Boolean,
    val active: Boolean,
    val loading: Boolean,
    val recommendations: List<Song>,
    val action: MenuIcon,
    val deactivate: () -> Unit,
    private val maxQueueSize: Int,
) {
    val extraCount: Int get() = recommendations.size
    val extraDuration: Long get() = recommendations.sumOf { durationTextToMillis(it.durationText.orEmpty()) }

    private fun additions(items: List<MediaItem>): List<MediaItem> {
        if (items.isEmpty() || recommendations.isEmpty()) return emptyList()
        val ids = items.mapTo(HashSet(), MediaItem::mediaId)
        return recommendations.filterNot { it.id in ids }.map { it.asMediaItem.asSmartShuffleRecommendation(true) }
    }

    fun mix(items: List<MediaItem>): List<MediaItem> {
        val additions = additions(items)
        return if (additions.isEmpty()) items else spreadSmartShuffleRecommendations(items + additions)
    }

    fun mixPlayback(items: List<MediaItem>, selectedIndex: Int): Pair<List<MediaItem>, Int> {
        val selected = items.getOrNull(selectedIndex) ?: return items to selectedIndex
        val additions = additions(items).take((maxQueueSize - 1).coerceAtLeast(0))
        if (additions.isEmpty()) return items to selectedIndex
        val sourceSlots = maxQueueSize - additions.size
        val first = selectedIndex.coerceAtMost((items.size - sourceSlots).coerceAtLeast(0))
        val source = items.subList(first, (first + sourceSlots).coerceAtMost(items.size))
        val mixed = spreadSmartShuffleRecommendations(source + additions)
        return mixed to mixed.indexOfFirst { it === selected }
    }
}

@Composable
internal fun rememberCollectionSmartShuffle(
    key: Any,
    source: List<Song> = emptyList(),
    enabled: Boolean,
    actionEnabled: Boolean = true,
    initiallyActive: Boolean = false,
    fetchRecommendations: Boolean = true,
    loadSource: (suspend () -> List<Song>)? = null,
): CollectionSmartShuffle {
    val context = LocalContext.current
    val connected by NetworkQualityHelper.isNetworkAvailableComposable(context)
    val offline by OfflineMode.rememberEnabled()
    val amount by rememberPreference(recommendationsNumberKey, RecommendationsNumber.Adaptive)
    val maxQueueSize by rememberPreference(maxSongsInQueueKey, MaxSongs.Unlimited)
    val parentalControl by rememberPreference(parentalControlEnabledKey, false)
    val player = LocalPlayerServiceBinder.current?.player
    val seedIds = remember(source) { source.asSequence().filterNot(Song::isLocal).map(Song::id).toSet() }
    val available = enabled && (!fetchRecommendations || (!offline && connected && (seedIds.isNotEmpty() || loadSource != null)))
    var active by remember(key) { mutableStateOf(initiallyActive) }
    var loading by remember(key) { mutableStateOf(false) }
    var reportResult by remember(key) { mutableStateOf(false) }
    var suggestions by remember(key) { mutableStateOf(emptyList<Song>()) }
    LaunchedEffect(key, available, active, seedIds, source.size, amount, parentalControl, fetchRecommendations, player) {
        suggestions = emptyList()
        if (!available || !active || !fetchRecommendations) {
            loading = false
            if (!available) {
                if (reportResult) showSmartShuffleResult(0)
                reportResult = false
                active = false
            }
            return@LaunchedEffect
        }
        loading = true
        try {
            suggestions = withContext(NzikDispatchers.DATA) {
                val matches = loadLocalPlaylistRecommendations(loadSource?.invoke() ?: source, amount) {
                    !parentalControl || !it.title.hasExplicitPrefix()
                }.keys.toList()
                if (player == null || matches.isEmpty()) matches else {
                    val allowedIds = player.excludeMediaItems(matches.map(Song::asMediaItem), context)
                        .mapTo(HashSet(), MediaItem::mediaId)
                    matches.filter { it.id in allowedIds }
                }
            }
            if (reportResult) {
                reportResult = false
                showSmartShuffleResult(suggestions.size)
            }
        } finally {
            loading = false
        }
    }
    val action = SmartShuffle(
        isRecommendationEnabled = { active },
        isRecommendationsLoading = { loading },
        onToggleRecommendation = {
            active = !active
            reportResult = active
        },
        isActionAvailable = { available && actionEnabled },
    )
    return CollectionSmartShuffle(
        available, active && available, loading, suggestions.takeIf { active && available }.orEmpty(), action,
        deactivate = { active = false },
        maxQueueSize = maxQueueSize.toInt(),
    )
}

@Composable
internal fun CollectionSmartShuffleCount(active: Boolean, loading: Boolean, count: Int, modifier: Modifier = Modifier) {
    AnimatedVisibility(active, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.smart_shuffle),
                contentDescription = stringResource(R.string.recommendations_action_smart_shuffle),
                tint = colorPalette().textSecondary,
                modifier = Modifier.size(12.dp),
            )
            if (loading) InlineCircularLoader(Modifier.size(12.dp), color = colorPalette().textSecondary)
            else if (count > 0) {
                BasicText(
                    count.toString(), style = typography().xxxs.semiBold.copy(color = colorPalette().textSecondary),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal data class CollectionTrackRow<out T>(
    val original: IndexedValue<T>? = null,
    val recommendation: Song? = null,
) {
    fun key(trackKey: (T) -> String): String =
        recommendation?.let { "smart-shuffle:" + it.id } ?: trackKey(requireNotNull(original).value)
}

internal fun <T> collectionTrackRows(source: List<T>, recommendations: List<Song>): List<CollectionTrackRow<T>> {
    val placements = smartShuffleInsertionPositions(source.size, recommendations, Random(recommendations.hashCode()))
        .entries.groupBy({ it.value }, { it.key })
    return buildList(source.size + recommendations.size) {
        source.forEachIndexed { index, item ->
            placements[index].orEmpty().forEach { add(CollectionTrackRow(recommendation = it)) }
            add(CollectionTrackRow(original = IndexedValue(index, item)))
        }
        placements[source.size].orEmpty().forEach { add(CollectionTrackRow(recommendation = it)) }
    }
}

internal fun <T> CollectionSmartShuffle.trackRows(source: List<T>, query: String): List<CollectionTrackRow<T>> =
    collectionTrackRows(source, recommendations.filter { query.isBlank() || it.matchesCollectionSearch(query) })

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CollectionRecommendationSong(
    song: Song,
    smartShuffle: CollectionSmartShuffle,
    navController: NavController,
    selectionActive: Boolean,
    modifier: Modifier = Modifier,
    playbackTracks: () -> List<MediaItem>,
) {
    val binder = LocalPlayerServiceBinder.current
    val recommendation = remember(song) { song.asMediaItem.asSmartShuffleRecommendation(true) }
    SongItem(
        song = song,
        navController = navController,
        isRecommended = true,
        modifier = modifier,
        sourceMediaItem = { recommendation },
        onClick = {
            if (!selectionActive) {
                val sources = playbackTracks() + recommendation
                val (tracks, start) = smartShuffle.mixPlayback(sources, sources.lastIndex)
                if (start >= 0) {
                    binder?.stopRadio()
                    binder?.player?.forcePlayAtIndex(tracks, start)
                }
            }
        },
    )
}
