package app.n_zik.android.components.settings.common

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import app.it.fast4x.rimusic.ui.components.themed.FloatingActions
import app.it.fast4x.rimusic.utils.ScrollingInfo
import app.it.fast4x.rimusic.utils.smoothScrollToTop
import app.n_zik.android.LocalPlayerAwareWindowInsets

/** Uses header visibility instead of item counts because settings cards can exceed a viewport. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun BoxScope.SettingsScrollToTop(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    pinnedFooterHeightPx: Int = 0,
) {
    val transition = remember(lazyListState) { MutableTransitionState<ScrollingInfo?>(ScrollingInfo()) }
    LaunchedEffect(lazyListState) {
        var previousIndex = lazyListState.firstVisibleItemIndex
        var previousOffset = lazyListState.firstVisibleItemScrollOffset
        snapshotFlow {
            Triple(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset, lazyListState.canScrollBackward)
        }.collect { (index, offset, canScrollBackward) ->
            val scrollingDown = index > previousIndex || (index == previousIndex && offset > previousOffset)
            val far = canScrollBackward && (index > 0 || offset > lazyListState.layoutInfo.viewportSize.height / 2)
            transition.targetState = ScrollingInfo(isScrollingDown = scrollingDown, isFar = far)
            previousIndex = index
            previousOffset = offset
        }
    }
    FloatingActions(
        transitionState = transition,
        onScrollToTop = lazyListState::smoothScrollToTop,
        // The measured footer already includes player/chrome clearance; reserve it only once.
        windowInsets = LocalPlayerAwareWindowInsets.current.union(WindowInsets(bottom = pinnedFooterHeightPx)),
        modifier = modifier,
    )
}
