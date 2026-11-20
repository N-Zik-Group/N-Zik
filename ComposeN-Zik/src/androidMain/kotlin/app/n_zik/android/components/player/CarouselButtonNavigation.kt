package app.n_zik.android.components.player

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import app.n_zik.android.components.player.slide.CAROUSEL_SETTLE_DURATION_MS
import app.n_zik.android.components.player.slide.playerPagerSettleAnimation
import app.n_zik.android.utils.playAtIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

internal val LocalCarouselButtonNavigation = staticCompositionLocalOf<((Boolean) -> Boolean)?> { null }

/** A transport press first moves the artwork, then hands off playback like a settled drag. */
internal class CarouselButtonNavigation(
    private val player: Player,
    private val pager: PagerState,
    private val scope: CoroutineScope,
    private val commitPlayback: (Int) -> Unit = { player.playAtIndex(it) },
) {
    private data class Move(val origin: Int, val originUid: Any, val target: Int, val targetUid: Any)

    private val window = Timeline.Window()
    private var pending by mutableStateOf<Move?>(null)
    private var rejectedPage: Int? = null
    private var job: Job? = null
    val isMoving: Boolean get() = pending != null

    fun cancel() {
        pending = null
        job?.cancel()
    }

    fun allowGestureCommit(page: Int): Boolean {
        val rejected = page == rejectedPage
        rejectedPage = null
        return !rejected
    }

    fun move(forward: Boolean, stillAvailable: () -> Boolean): Boolean {
        if (!stillAvailable() || !player.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)) return false
        val timeline = player.currentTimeline
        val origin = player.currentMediaItemIndex
        val originUid = uidAt(timeline, origin) ?: return false
        val validPending = pending?.takeIf {
            it.origin == origin && it.originUid == originUid && it.targetUid == uidAt(timeline, it.target)
        }
        if (pending != null && validPending == null) cancel()
        if (validPending == null && (pager.isScrollInProgress || pager.settledPage != origin)) return false

        val target = if (validPending == null) {
            if (forward) player.nextMediaItemIndex else player.previousMediaItemIndex
        } else {
            carouselQueuedTarget(timeline, validPending.target, forward, player.repeatMode, player.shuffleModeEnabled)
        }
        val targetUid = uidAt(timeline, target) ?: return validPending != null
        // The pager shortcuts distant jumps. Keep shuffle or wraparound jumps on the direct path.
        if (validPending == null && abs(target - origin) != 1) return false
        val move = Move(origin, originUid, target, targetUid)
        rejectedPage = null
        job?.cancel()
        pending = move
        job = scope.launch {
            try {
                pager.animateScrollToPage(target, animationSpec = playerPagerSettleAnimation(CAROUSEL_SETTLE_DURATION_MS))
                // Separate the final pager mutation and the track callbacks by a frame boundary.
                coroutineContext[MonotonicFrameClock]?.withFrameNanos { }
                if (pending !== move) return@launch
                val currentTimeline = player.currentTimeline
                val validMove = stillAvailable() && player.currentMediaItemIndex == origin &&
                    uidAt(currentTimeline, origin) == originUid && uidAt(currentTimeline, target) == targetUid &&
                    player.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                if (pager.isScrollInProgress) {
                    if (!validMove) rejectedPage = target
                    return@launch
                }
                if (validMove && target != origin && pager.settledPage == target) {
                    commitPlayback(target)
                } else if (pager.settledPage == target && player.currentMediaItemIndex != target) {
                    val playingPage = player.currentMediaItemIndex
                    if (playingPage in 0 until pager.pageCount) {
                        pager.scrollToPage(playingPage)
                    } else {
                        // The settle observer must not replay a rejected transport move.
                        rejectedPage = target
                    }
                }
            } finally {
                if (pending === move) pending = null
            }
        }
        return true
    }

    private fun uidAt(timeline: Timeline, index: Int): Any? =
        if (index != C.INDEX_UNSET && index in 0 until timeline.windowCount) timeline.getWindow(index, window).uid else null
}

internal fun carouselQueuedTarget(
    timeline: Timeline,
    page: Int,
    forward: Boolean,
    repeatMode: Int,
    shuffle: Boolean,
): Int {
    val navigationRepeat = if (repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else repeatMode
    return if (forward) timeline.getNextWindowIndex(page, navigationRepeat, shuffle)
    else timeline.getPreviousWindowIndex(page, navigationRepeat, shuffle)
}
