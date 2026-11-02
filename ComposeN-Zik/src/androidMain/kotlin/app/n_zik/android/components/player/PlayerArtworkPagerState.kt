package app.n_zik.android.components.player

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key

/**
 * Portrait measures its pager in LookaheadScope; landscape uses normal measurement.
 * PagerState retains whether it has seen lookahead, so reusing that state in the
 * landscape tree would treat every subsequent measure as an approach-only pass.
 * Recreate the measurement state at that boundary, retaining the playing song.
 */
@Composable
fun rememberPlayerArtworkPagerState(
    landscape: Boolean,
    playingIndex: Int,
    pageCount: () -> Int,
): PagerState = key(landscape) {
    rememberPagerState(initialPage = playingIndex.coerceIn(0, (pageCount() - 1).coerceAtLeast(0)),
        pageCount = pageCount)
}
