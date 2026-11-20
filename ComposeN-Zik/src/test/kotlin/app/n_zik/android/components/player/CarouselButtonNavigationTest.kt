package app.n_zik.android.components.player

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.MonotonicFrameClock
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import io.mockk.every
import io.mockk.mockk
import io.mockk.coEvery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CarouselButtonNavigationTest {
    @Test
    fun `a transport press commits only after its cover has settled`() = runTest {
        checkTransportSettle(visibleAtSettle = true)
    }

    @Test
    fun `a transport press restores the playing cover when the player closes`() = runTest {
        checkTransportSettle(visibleAtSettle = false)
    }

    @Test
    fun `an interrupted invalid press cannot commit through the gesture observer`() = runTest {
        checkTransportSettle(visibleAtSettle = false, gestureTakesOver = true)
    }

    private fun TestScope.checkTransportSettle(visibleAtSettle: Boolean, gestureTakesOver: Boolean = false) {
        val timeline = mockk<Timeline>()
        every { timeline.windowCount } returns 2
        every { timeline.getWindow(any(), any()) } answers {
            secondArg<Timeline.Window>().apply { uid = firstArg<Int>() }
        }
        val player = mockk<Player>()
        every { player.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM) } returns true
        every { player.currentMediaItemIndex } returns 0
        every { player.nextMediaItemIndex } returns 1
        every { player.currentTimeline } returns timeline
        val settled = CompletableDeferred<Unit>()
        var page = 0
        var scrolling = false
        val pager = mockk<PagerState>()
        every { pager.isScrollInProgress } answers { scrolling }
        every { pager.settledPage } answers { page }
        every { pager.pageCount } returns 2
        coEvery { pager.scrollToPage(0, any()) } coAnswers { page = 0 }
        coEvery { pager.animateScrollToPage(1, any(), any()) } coAnswers {
            settled.await()
            page = 1
        }
        val commits = mutableListOf<Int>()
        val nextFrame = CompletableDeferred<Long>()
        val frameClock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(nextFrame.await())
        }
        val controller = CarouselButtonNavigation(player, pager, CoroutineScope(coroutineContext + frameClock)) { commits += it }
        var visible = true
        assertEquals(true, controller.move(forward = true) { visible })
        runCurrent()
        assertEquals(emptyList<Int>(), commits)
        settled.complete(Unit)
        runCurrent()
        assertEquals(1, page)
        assertEquals(emptyList<Int>(), commits)
        visible = visibleAtSettle
        scrolling = gestureTakesOver
        nextFrame.complete(16_666_667)
        runCurrent()
        assertEquals(if (visibleAtSettle) listOf(1) else emptyList(), commits)
        assertEquals(if (visibleAtSettle || gestureTakesOver) 1 else 0, page)
        if (gestureTakesOver) assertEquals(false, controller.allowGestureCommit(1))
    }

    @Test
    fun `queued taps follow shuffle direction and do not repeat the same item in repeat one`() {
        val timeline = mockk<Timeline>()
        every { timeline.getNextWindowIndex(4, Player.REPEAT_MODE_OFF, true) } returns 7
        every { timeline.getPreviousWindowIndex(7, Player.REPEAT_MODE_OFF, true) } returns 4
        assertEquals(7, carouselQueuedTarget(timeline, 4, true, Player.REPEAT_MODE_ONE, true))
        assertEquals(4, carouselQueuedTarget(timeline, 7, false, Player.REPEAT_MODE_ONE, true))
    }

    @Test
    fun `queued taps preserve repeat all and stop at the end in repeat off`() {
        val timeline = mockk<Timeline>()
        every { timeline.getNextWindowIndex(3, Player.REPEAT_MODE_ALL, false) } returns 0
        every { timeline.getNextWindowIndex(3, Player.REPEAT_MODE_OFF, false) } returns C.INDEX_UNSET
        assertEquals(0, carouselQueuedTarget(timeline, 3, true, Player.REPEAT_MODE_ALL, false))
        assertEquals(C.INDEX_UNSET, carouselQueuedTarget(timeline, 3, true, Player.REPEAT_MODE_OFF, false))
    }
}
