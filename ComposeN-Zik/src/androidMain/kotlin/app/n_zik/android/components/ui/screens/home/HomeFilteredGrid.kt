package app.n_zik.android.components.ui.screens.home

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.n_zik.android.compose.persist.persist

@Composable
internal fun <T> rememberHomeFilteredGrid(
    slot: String,
    state: LazyGridState,
    ready: Boolean,
    rows: List<T>,
    key: (T) -> Any,
): List<T>? {
    var displayed by persist<List<T>>("home/filtered-grid/$slot")
    val scrollAnchor = persist("home/filtered-grid/$slot/scroll", HomeFilterScrollAnchor()).value
    LaunchedEffect(state, ready, rows) {
        if (!ready || displayed == rows) return@LaunchedEffect
        val index = state.firstVisibleItemIndex
        scrollAnchor.update(
            displayed?.isNotEmpty() == true, rows, key,
            index, state.firstVisibleItemScrollOffset,
            state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.key,
            state::requestScrollToItem,
        )
        displayed = rows
    }
    return displayed
}

internal class HomeFilterScrollAnchor {
    private data class Position(val index: Int, val offset: Int, val key: Any?)
    private var beforeEmpty: Position? = null

    fun <T> update(
        hadRows: Boolean,
        rows: List<T>,
        key: (T) -> Any,
        index: Int,
        offset: Int,
        firstKey: Any?,
        scrollTo: (Int, Int) -> Unit,
    ) {
        if (rows.isEmpty()) {
            if (hadRows) beforeEmpty = Position(index, offset, firstKey)
            return
        }

        val previous = beforeEmpty
        beforeEmpty = null
        if (previous == null) {
            // Compose otherwise follows the item's key, even when the viewport is at the top.
            if (index == 0 && offset == 0) scrollTo(0, 0)
            return
        }
        val matchingIndex = if (previous.index == 0 && previous.offset == 0) 0 else {
            rows.indexOfFirst { key(it) == previous.key }
        }
        scrollTo(matchingIndex.takeIf { it >= 0 } ?: previous.index.coerceAtMost(rows.lastIndex), previous.offset)
    }
}
