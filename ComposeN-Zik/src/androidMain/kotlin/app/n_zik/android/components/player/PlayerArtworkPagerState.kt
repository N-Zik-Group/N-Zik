package app.n_zik.android.components.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import kotlin.math.absoluteValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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

internal suspend fun PagerState.animatePlayerArtworkScrollToPage(
    page: Int,
    animationSpec: AnimationSpec<Float>,
    currentPlayingPage: () -> Int,
) = coroutineScope {
    val startingPage = settledPage
    val clock = coroutineContext[MonotonicFrameClock]
    // A user scroll can cancel its competing mutation without cancelling the
    // synchronization effect that owns the target.
    val scroll = async {
        if (clock == null) {
            animateScrollToPage(page, animationSpec = animationSpec)
        } else {
            withContext(PlayerArtworkFrameClock(clock)) {
                animateScrollToPage(page, animationSpec = animationSpec)
            }
        }
    }
    try {
        scroll.await()
    } catch (cancelled: CancellationException) {
        ensureActive()
        if (startingPage == page || currentPlayingPage() != page) throw cancelled
        fun isCenteredAndIdle() = !isScrollInProgress && currentPageOffsetFraction.absoluteValue <= 0.001f
        snapshotFlow { isCenteredAndIdle() }.first { idle ->
            if (idle) {
                // Drag and fling are separate mutations. Let the fling take
                // ownership before treating a centered release as settled.
                clock?.withFrameNanos { }
            }
            idle && isCenteredAndIdle()
        }
        if (settledPage == startingPage && currentPlayingPage() == page) {
            // A gesture that settles on a different page already owns the
            // playback handoff. Returning to its origin leaves Next in force.
            scrollToPage(page)
        }
    }
}

/** Keep late frames from spending the remaining travel in one step of a button-driven scroll. */
internal class PlayerArtworkFrameClock(private val upstream: MonotonicFrameClock) : MonotonicFrameClock {
    private var lastFrameNanos = 0L
    private var animationTimeNanos: Long? = null

    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
        upstream.withFrameNanos { frameNanos ->
            val time = animationTimeNanos?.let { previousTime ->
                previousTime + (frameNanos - lastFrameNanos).coerceIn(0L, MAX_FRAME_STEP_NANOS)
            } ?: frameNanos
            lastFrameNanos = frameNanos
            animationTimeNanos = time
            onFrame(time)
        }

    private companion object {
        const val MAX_FRAME_STEP_NANOS = 1_000_000_000L / 30
    }
}
