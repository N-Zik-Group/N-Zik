package app.n_zik.android.utils.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Drives virtual time through a bare [TestCoroutineScheduler] instead of `runTest`: `runTest` fails with `UncaughtExceptionsBeforeTest` when any earlier test in the same JVM leaked an uncaught coroutine exception, which would couple this test to unrelated ones. */
@OptIn(ExperimentalCoroutinesApi::class)
class PeriodicRunnerTest {

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(StandardTestDispatcher(scheduler) + Job())

    private fun advance(ms: Long) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }

    @Test
    fun `runs immediately then once per interval`() {
        val callTimes = mutableListOf<Long>()
        val job = scope.launch { runPeriodically(30_000) { callTimes += scheduler.currentTime } }

        scheduler.runCurrent()
        assertEquals(listOf(0L), callTimes, "first call must be immediate")

        advance(29_999)
        assertEquals(1, callTimes.size, "no call before the interval elapses")

        advance(1)
        assertEquals(listOf(0L, 30_000L), callTimes)

        advance(30_000)
        assertEquals(listOf(0L, 30_000L, 60_000L), callTimes)

        job.cancel()
    }

    @Test
    fun `cancelling the scope stops further calls`() {
        var calls = 0
        val job = scope.launch { runPeriodically(1_000) { calls++ } }

        scheduler.runCurrent()
        advance(1_000)
        assertEquals(2, calls)

        scope.coroutineContext[Job]!!.cancel()
        advance(10_000)

        assertEquals(2, calls, "no call may happen after cancellation")
        assertTrue(job.isCancelled)
    }

    @Test
    fun `a failing action does not kill the loop`() {
        var calls = 0
        val job = scope.launch {
            runPeriodically(1_000) {
                calls++
                if (calls == 1) error("simulated failure")
            }
        }

        scheduler.runCurrent()
        advance(1_000)

        assertEquals(2, calls, "loop must keep running after the action threw")
        job.cancel()
    }
}
