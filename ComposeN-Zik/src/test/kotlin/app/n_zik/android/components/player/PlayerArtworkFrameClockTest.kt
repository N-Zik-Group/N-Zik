package app.n_zik.android.components.player

import androidx.compose.runtime.MonotonicFrameClock
import androidx.media3.common.Timeline
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class PlayerArtworkFrameClockTest {
    @Test
    fun `carousel keys follow surviving window occurrences after removal and reorder`() {
        fun timeline(vararg uids: String): Timeline = mockk {
            every { windowCount } returns uids.size
            every { getWindow(any(), any()) } answers {
                secondArg<Timeline.Window>().apply { uid = uids[firstArg<Int>()] }
            }
        }
        val keys = PlayerArtworkPageKeys()
        val initial = timeline("copy-one", "middle", "copy-two")
        val first = keys.key(initial, 0)
        val second = keys.key(initial, 2)
        assertNotEquals(first, second)
        assertEquals(second, keys.key(timeline("copy-two", "middle"), 0))
    }

    @Test
    fun `regular frames retain their original timing`() = runTest {
        val times = listOf(1_000_000_000L, 1_016_666_667L, 1_033_333_334L, 1_066_666_667L)
        val clock = PlayerArtworkFrameClock(TestClock(times))

        assertEquals(times, times.map { clock.withFrameNanos { it } })
    }

    @Test
    fun `long stalls advance at most one step and do not accrue catch-up time`() = runTest {
        val times = listOf(1_000_000_000L, 1_200_000_000L, 1_216_666_667L, 1_500_000_000L)
        val clock = PlayerArtworkFrameClock(TestClock(times))

        assertEquals(
            listOf(1_000_000_000L, 1_033_333_333L, 1_050_000_000L, 1_083_333_333L),
            times.map { clock.withFrameNanos { it } },
        )
    }

    private class TestClock(times: List<Long>) : MonotonicFrameClock {
        private val remaining = times.iterator()

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(remaining.next())
    }
}
