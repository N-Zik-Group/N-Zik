package app.n_zik.innertube.invidious

import app.n_zik.innertube.utils.InnertubeLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Before this fix, useUnofficialInstances's setter created a brand-new CoroutineScope(Dispatchers.IO)
 * on every toggle, never tracked or cancellable. Now it reuses a single SupervisorJob-backed scope,
 * so a fetch failure from one toggle can't take out a later one.
 */
class InvidiousScopeTest {

    @Test
    fun `a failing fetch does not cancel the shared scope`() = runBlocking {
        val rootJob = requireNotNull(Invidious.scope.coroutineContext[Job]) { "scope must have a root Job" }
        val handler = CoroutineExceptionHandler { _, _ -> }
        val failing = Invidious.scope.launch(handler) { error("simulated fetchInstances failure") }
        failing.join()
        assertTrue(failing.isCancelled, "failing child must have run and failed")
        assertFalse(rootJob.isCancelled, "scope must not be cancelled by a failing child")
    }

    @Test
    fun `a later fetch still runs after a sibling failure`() = runBlocking {
        val handler = CoroutineExceptionHandler { _, _ -> }
        val failing = Invidious.scope.launch(handler) { error("simulated fetchInstances failure") }
        failing.join()
        val ran = CountDownLatch(1)
        Invidious.scope.launch { ran.countDown() }
        assertTrue(ran.await(10, TimeUnit.SECONDS), "later launch must still run after a sibling failure")
    }

    @Test
    fun `a failure is logged by the scope handler and siblings survive`() = runTest {
        var captured: Throwable? = null
        val listener = InnertubeLogger.Listener { tag, level, _, throwable ->
            if (tag == "InvidiousScope" && level == InnertubeLogger.Level.ERROR) {
                captured = throwable
            }
        }
        InnertubeLogger.addListener(listener)
        try {
            val scope = CoroutineScope(
                SupervisorJob() +
                    Invidious.scopeExceptionHandler +
                    UnconfinedTestDispatcher()
            )
            val siblingRan = CountDownLatch(1)

            val failing = scope.launch { error("simulated scope coroutine failure") }
            failing.join()
            assertTrue(failing.isCancelled, "failing child must have run and failed")

            scope.launch { siblingRan.countDown() }
            assertTrue(siblingRan.await(10, TimeUnit.SECONDS), "sibling must still run after a sibling failure")

            assertTrue(captured is IllegalStateException, "scope handler must log the original throwable")
            scope.cancel()
        } finally {
            InnertubeLogger.removeListener(listener)
        }
    }
}
