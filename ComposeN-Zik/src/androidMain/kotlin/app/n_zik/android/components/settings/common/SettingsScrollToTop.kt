package app.n_zik.android.components.settings.common

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import app.n_zik.android.ui.components.themed.FloatingActions
import app.n_zik.android.utils.ScrollingInfo
import app.n_zik.android.utils.smoothScrollToTop
import app.n_zik.android.LocalPlayerAwareWindowInsets

@Composable
internal fun BoxScope.SettingsScrollToTop(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    pinnedFooterHeightPx: Int = 0,
) {
    SettingsScrollToTop(
        state = lazyListState,
        position = { lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset },
        viewportHeight = { lazyListState.layoutInfo.viewportSize.height },
        onScrollToTop = lazyListState::smoothScrollToTop,
        modifier = modifier,
        pinnedFooterHeightPx = pinnedFooterHeightPx,
    )
}

@Composable
internal fun BoxScope.SettingsScrollToTop(
    lazyGridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
    SettingsScrollToTop(
        state = lazyGridState,
        position = { lazyGridState.firstVisibleItemIndex to lazyGridState.firstVisibleItemScrollOffset },
        viewportHeight = { lazyGridState.layoutInfo.viewportSize.height },
        onScrollToTop = lazyGridState::smoothScrollToTop,
        modifier = modifier,
    )
}

/** Uses header visibility instead of item counts because settings cards can exceed a viewport. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun BoxScope.SettingsScrollToTop(
    state: ScrollableState,
    position: () -> Pair<Int, Int>,
    viewportHeight: () -> Int,
    onScrollToTop: suspend () -> Unit,
    modifier: Modifier,
    pinnedFooterHeightPx: Int = 0,
) {
    val transition = remember(state) { MutableTransitionState<ScrollingInfo?>(ScrollingInfo()) }
    LaunchedEffect(state) {
        var (previousIndex, previousOffset) = position()
        snapshotFlow {
            val (index, offset) = position()
            Triple(index, offset, state.canScrollBackward)
        }.collect { (index, offset, canScrollBackward) ->
            val scrollingDown = index > previousIndex || (index == previousIndex && offset > previousOffset)
            val far = canScrollBackward && (index > 0 || offset > viewportHeight() / 2)
            transition.targetState = ScrollingInfo(isScrollingDown = scrollingDown, isFar = far)
            previousIndex = index
            previousOffset = offset
        }
    }
    FloatingActions(
        transitionState = transition,
        onScrollToTop = onScrollToTop,
        // The measured footer already includes player/chrome clearance; reserve it only once.
        windowInsets = LocalPlayerAwareWindowInsets.current.union(WindowInsets(bottom = pinnedFooterHeightPx)),
        modifier = modifier,
    )
}
