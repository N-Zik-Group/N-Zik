package app.n_zik.android.components.player

import androidx.compose.foundation.pager.PagerState
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import io.mockk.every
import io.mockk.mockk
import io.mockk.coEvery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CarouselButtonNavigationTest {
    @Test
    fun `a transport press commits only after its cover has settled`() = runTest {
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
        val pager = mockk<PagerState>()
        every { pager.isScrollInProgress } returns false
        every { pager.settledPage } answers { page }
        coEvery { pager.animateScrollToPage(1, any(), any()) } coAnswers {
            settled.await()
            page = 1
        }
        val commits = mutableListOf<Int>()
        val controller = CarouselButtonNavigation(player, pager, this) { commits += it }
        assertEquals(true, controller.move(forward = true) { true })
        runCurrent()
        assertEquals(emptyList<Int>(), commits)
        settled.complete(Unit)
        runCurrent()
        assertEquals(listOf(1), commits)
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
