package app.n_zik.android.playback.services

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSleepTimerTest {
    @Test fun `one collector observes start cancellation and a new countdown`() = runTest {
        val timer = PlaybackSleepTimer(this)
        val observed = mutableListOf<Long?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            timer.millisLeft.collect { observed.add(it) }
        }

        timer.start(5_000L, { error("Canceled timer expired") }, { error("Canceled timer shut down") })
        advanceTimeBy(1_000L)
        runCurrent()
        timer.cancel()
        timer.start(3_000L, { error("Canceled timer expired") }, { error("Canceled timer shut down") })
        advanceTimeBy(1_000L)
        runCurrent()
        timer.cancel()
        advanceTimeBy(10_000L)

        assertEquals(listOf(null, 5_000L, 4_000L, null, 3_000L, 2_000L, null), observed)
    }

    @Test fun `replacing a countdown during shutdown grace prevents stale shutdown`() = runTest {
        val timer = PlaybackSleepTimer(this)
        val events = mutableListOf<String>()
        timer.start(1_000L, { events.add("old expiry") }, { events.add("old shutdown") })
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(listOf("old expiry"), events)
        assertEquals(null, timer.millisLeft.value)

        timer.start(2_000L, { events.add("new expiry") }, { events.add("new shutdown") })
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(listOf("old expiry"), events)
        assertEquals(1_000L, timer.millisLeft.value)
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(listOf("old expiry", "new expiry", "new shutdown"), events)
        assertEquals(null, timer.millisLeft.value)
    }
}
