package app.n_zik.android.utils.coroutines

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Covers the hardened fire-and-forget scope factory [NzikDispatchers.fireAndForget] (issue #606, Goal G5): dispatcher preservation, sibling survival after an unhandled failure, the Timber exception handler, and the caller-provided Job contract of the CoroutineContext overload. */
class NzikDispatchersFireAndForgetTest {

    private val captured = mutableListOf<Throwable>()

    private val captureTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (tag == "FireAndForget") t?.let { captured += it }
        }
    }

    @BeforeEach
    fun setUp() {
        captured.clear()
        Timber.plant(captureTree)
    }

    @AfterEach
    fun tearDown() {
        Timber.uproot(captureTree)
    }

    @Test
    fun `dispatcher overload keeps the exact dispatcher requested`() {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "faf-dispatcher-test") }
        try {
            val dispatcher = executor.asCoroutineDispatcher()
            val threadName = AtomicReference<String>()
            val latch = CountDownLatch(1)
            NzikDispatchers.fireAndForget(dispatcher).launch {
                threadName.set(Thread.currentThread().name)
                latch.countDown()
            }
            assertTrue(latch.await(5, TimeUnit.SECONDS), "scope launch did not run in time")
            // startsWith: kotlinx.coroutines debug mode appends " @coroutine#N" to thread names
            // (same convention as NzikDispatchersTest).
            assertTrue(
                threadName.get().startsWith("faf-dispatcher-test"),
                "expected faf-dispatcher-test but was ${threadName.get()}",
            )
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun `dispatcher overload installs a fresh active Job as the scope root`() {
        val scope = NzikDispatchers.fireAndForget(UnconfinedTestDispatcher())
        // Supervisor semantics (child failure must not cancel the root) are covered by the
        // sibling-survival test below; here we only lock down the root Job's presence/state.
        val root = requireNotNull(scope.coroutineContext[Job]) { "fireAndForget must expose a root Job" }
        assertTrue(root.isActive, "fresh scope must not be cancelled")
    }

    @Test
    fun `unhandled exception is logged and does not cancel siblings or the scope`() = runBlocking {
        val siblingRan = AtomicBoolean(false)
        val scope = NzikDispatchers.fireAndForget(UnconfinedTestDispatcher())

        scope.launch { throw IllegalStateException("boom") }
        scope.launch { siblingRan.set(true) }

        assertTrue(siblingRan.get(), "a failure in one coroutine must not cancel its siblings")
        val root = requireNotNull(scope.coroutineContext[Job])
        assertTrue(root.isActive, "an unhandled failure must not cancel the scope's root Job")
        assertEquals(1, captured.size, "exactly one Timber error entry expected")
        assertEquals("boom", captured.single().message)
    }

    @Test
    fun `scope wires a CoroutineExceptionHandler so failures never go uncaught`() {
        val scope = NzikDispatchers.fireAndForget(UnconfinedTestDispatcher())
        assertNotNull(scope.coroutineContext[CoroutineExceptionHandler],
            "fireAndForget must install a CoroutineExceptionHandler on the scope")
    }

    @Test
    fun `context overload keeps a caller-provided Job as the cancellation root`() = runBlocking {
        val job = SupervisorJob()
        val scope = NzikDispatchers.fireAndForget(UnconfinedTestDispatcher() + job)

        assertSame(job, scope.coroutineContext[Job], "caller-provided Job must stay the cancellation root")
        assertNotNull(scope.coroutineContext[CoroutineExceptionHandler],
            "the context overload must install the same CoroutineExceptionHandler as the dispatcher overload")

        scope.cancel()
        assertFalse(job.isActive, "cancelling the scope must cancel the caller-provided Job")
    }

    @Test
    fun `context overload keeps a caller-provided plain Job as the cancellation root`() = runBlocking {
        val job = Job()
        val scope = NzikDispatchers.fireAndForget(UnconfinedTestDispatcher() + job)

        // assertSame also proves the caller's Job was not replaced by the helper's own root.
        assertSame(job, scope.coroutineContext[Job], "a plain caller-provided Job must stay the cancellation root")

        scope.cancel()
        assertFalse(job.isActive, "cancelling the scope must cancel the caller-provided Job")
    }
}
