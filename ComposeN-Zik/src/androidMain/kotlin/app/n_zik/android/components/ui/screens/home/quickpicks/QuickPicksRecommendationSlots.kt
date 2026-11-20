package app.n_zik.android.components.ui.screens.home.quickpicks

import app.n_zik.android.models.Song
import kotlin.math.ceil
import kotlin.random.Random

internal const val QUICK_PICKS_GRID_ROWS = 3

internal fun visibleQuickPicksSlots(viewportWidth: Float, itemWidth: Float): Int {
    val columns = viewportWidth / itemWidth
    return (if (columns.isFinite()) ceil(columns).toInt().coerceAtLeast(1) else 1) * QUICK_PICKS_GRID_ROWS
}

internal fun fillQuickPicksRecommendationSlots(
    previous: List<Song?>,
    history: List<Song>,
    online: List<Song>,
    historyPending: Boolean,
    onlinePending: Boolean,
    keepFirstHistorySong: Boolean,
    seed: Int,
    initialSlots: Int,
): List<Song?> {
    val historyIds = history.mapTo(HashSet(), Song::id)
    // Keep the history metadata when both sources offer the same title.
    val remaining = (history + online).distinctBy(Song::id).associateByTo(LinkedHashMap(), Song::id)
    val slots = MutableList(initialSlots) { index ->
        previous.getOrNull(index)?.let { remaining.remove(it.id) }
    }
    val random = Random(seed)
    val firstHistory = history.firstOrNull()?.takeIf { keepFirstHistorySong }
        ?.let { remaining.remove(it.id) }
    val (historyPool, onlinePool) = remaining.values.shuffled(random)
        .partition { it.id in historyIds }
    val availableHistory = ArrayDeque(listOfNotNull(firstHistory) + historyPool)
    val availableOnline = ArrayDeque(onlinePool)
    val preferHistoryFirst = keepFirstHistorySong || online.isEmpty() && !onlinePending
    for (index in slots.indices) {
        if (slots[index] != null) continue
        val preferHistory = (index % 2 == 0) == preferHistoryFirst
        slots[index] = if (preferHistory) availableHistory.removeFirstOrNull()
        else availableOnline.removeFirstOrNull()
    }
    for (index in slots.indices) {
        if (slots[index] != null) continue
        val preferHistory = (index % 2 == 0) == preferHistoryFirst
        if (preferHistory && !historyPending) slots[index] = availableOnline.removeFirstOrNull()
        if (!preferHistory && !onlinePending) slots[index] = availableHistory.removeFirstOrNull()
    }

    // Keep overflow rows in place as later sources fill the first two columns.
    val overflow = (availableHistory + availableOnline).associateByTo(LinkedHashMap(), Song::id)
    val tail = previous.drop(initialSlots)
        .mapNotNull { it?.let { song -> overflow.remove(song.id) } }
    val remainingHistory = availableHistory.filter { it.id in overflow }.iterator()
    val remainingOnline = availableOnline.filter { it.id in overflow }.iterator()
    val mixed = buildList {
        while (remainingHistory.hasNext() || remainingOnline.hasNext()) {
            if (remainingOnline.hasNext()) add(remainingOnline.next())
            if (remainingHistory.hasNext()) add(remainingHistory.next())
        }
    }
    val firstColumns = if (historyPending || onlinePending) slots else slots.filterNotNull()
    return firstColumns + tail + mixed
}
