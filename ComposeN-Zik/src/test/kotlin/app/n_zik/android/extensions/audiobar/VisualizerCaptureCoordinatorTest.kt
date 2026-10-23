package app.n_zik.android.extensions.audiobar

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import utils.VisualizerHelper
import utils.VisualizerSnapshot

/**
 * Covers issue #606's per-sessionId capture loop (ARCHITECTURE-SPINE AD-2): every collector of
 * the same sessionId must share exactly one [VisualizerHelper.captureNow] producer, and that
 * producer must stop [VisualizerCaptureCoordinator.subscriptionTimeoutMillis] after its last
 * collector goes away (`SharingStarted.WhileSubscribed`).
 *
 * [VisualizerHelper.captureNow] is mocked (via mockk's companion-object support) so this test
 * never touches a real native `Visualizer`. `captureIntervalMillis`/`subscriptionTimeoutMillis`
 * are shrunk for the duration of this test so it doesn't need to wait out the production
 * 16ms/2000ms values in real time -- the coordinator's loop runs on a real background
 * dispatcher (`NzikDispatchers.VISUALIZER`), so a `TestDispatcher`/virtual time would not
 * control it.
 */
class VisualizerCaptureCoordinatorTest {

    private val callCount = AtomicInteger(0)

    @Before
    fun setUp() {
        // Defensive: a scope.launch job from a previous test can still be running (against that
        // test's already-torn-down mocks) once this test's setup begins. Clear the singleton's

        // state before wiring up new mocks so nothing stray from the previous test observes them.
        VisualizerCaptureCoordinator.resetForTests()
        mockkObject(VisualizerHelper.Companion)
        every { VisualizerHelper.captureNow(any()) } answers {
            // A fresh instance every call: matches production's "always a new copy" contract
            // and lets StateFlow's conflation actually observe each capture as a distinct value.
            VisualizerSnapshot(byteArrayOf(1), byteArrayOf(2), callCount.incrementAndGet())
        }
        VisualizerCaptureCoordinator.captureIntervalMillis = 10L
        VisualizerCaptureCoordinator.subscriptionTimeoutMillis = 80L
    }

    @After
    fun tearDown() {
        // Cancel this test's capture-loop/keep-alive jobs before unmocking so none of them can
        // observe the real (unmocked) VisualizerHelper.captureNow() after this test ends.
        VisualizerCaptureCoordinator.resetForTests()
        unmockkObject(VisualizerHelper.Companion)
        VisualizerCaptureCoordinator.captureIntervalMillis = 16L
        VisualizerCaptureCoordinator.subscriptionTimeoutMillis = 2000L
    }

    @Test
    fun `snapshotFlow returns the exact same shared instance for repeated calls on one sessionId`() {
        val sessionId = 9001

        val first = VisualizerCaptureCoordinator.snapshotFlow(sessionId)
        val second = VisualizerCaptureCoordinator.snapshotFlow(sessionId)

        assertSame(
            "two consumers of the same sessionId (e.g. SeekBarVisualizer + VisualizerView, " +
                "the latter indirectly through VisualizerHelper.snapshotProvider) must be backed " +
                "by the exact same StateFlow, never a second independent capture loop",
            first,
            second,
        )
    }

    @Test
    fun `different sessionIds get independent StateFlow instances (session switch mid-playback)`() {
        val flowA = VisualizerCaptureCoordinator.snapshotFlow(9010)
        val flowB = VisualizerCaptureCoordinator.snapshotFlow(9011)

        assertTrue(
            "switching audioSessionId must not reuse the previous session's capture loop/flow",
            flowA !== flowB,
        )
    }

    @Test
    fun `two simultaneous collectors never double the capture rate`() = runBlocking {
        val sessionId = 9002
        val flow = VisualizerCaptureCoordinator.snapshotFlow(sessionId)

        val jobA = launch { flow.collect {} }
        val jobB = launch { flow.collect {} }
        delay(150)
        jobA.cancel()
        jobB.cancel()

        val calls = callCount.get()
        // ~150ms / 10ms interval =~ 15 captures for ONE shared loop. If each collector ran its
        // own loop (the #606 bug), we'd see roughly double that. Bounds are generous to absorb

        // scheduling jitter while still catching an actual duplication regression.
        assertTrue("expected some captures to have happened, got $calls", calls in 1..25)
    }

    @Test
    fun `capture loop stops after WhileSubscribed timeout and restarts for a new collector`() = runBlocking {
        val sessionId = 9003
        val flow = VisualizerCaptureCoordinator.snapshotFlow(sessionId)

        val job = launch { flow.collect {} }
        delay(50)
        job.cancel()

        delay(VisualizerCaptureCoordinator.subscriptionTimeoutMillis + 150)
        val countAfterStop = callCount.get()

        delay(100)
        assertTrue(
            "no collector => captureNow() must stop firing once the WhileSubscribed timeout elapses " +
                "(before=$countAfterStop, after=${callCount.get()})",
            callCount.get() == countAfterStop,
        )

        val secondJob = launch { flow.collect {} }
        delay(100)
        secondJob.cancel()
        assertTrue(
            "a fresh collector must restart the capture loop for that sessionId",
            callCount.get() > countAfterStop,
        )
    }

    @Test
    fun `currentSnapshot alone (no real Flow collector) keeps the capture loop alive while polled`() = runBlocking {
        val sessionId = 9004

        // VisualizerView never calls collect{} -- it only ever peeks .value through
        // VisualizerHelper.getFft()/getWave(), every onDraw() frame. Simulate that: repeated

        // currentSnapshot() calls with no collect{} anywhere.
        repeat(10) {
            VisualizerCaptureCoordinator.currentSnapshot(sessionId)
            delay(20)
        }

        assertTrue(
            "polling currentSnapshot() alone must start and keep the capture loop running " +
                "(regression: .value reads don't count as WhileSubscribed collectors on their own)",
            callCount.get() > 1,
        )
    }

    @Test
    fun `currentSnapshot's keep-alive stops the loop once polling stops`() = runBlocking {
        val sessionId = 9005

        VisualizerCaptureCoordinator.currentSnapshot(sessionId)
        delay(50)
        val countWhilePolling = callCount.get()

        // Stop polling entirely and wait past the keep-alive + WhileSubscribed timeouts.
        delay(VisualizerCaptureCoordinator.subscriptionTimeoutMillis * 2 + 150)
        val countAfterIdle = callCount.get()

        delay(100)
        assertTrue(
            "no more polling => the proxy collector must stop, letting WhileSubscribed stop the loop too",
            callCount.get() == countAfterIdle,
        )
        assertTrue(
            "sanity: captures should have actually happened while polling was active",
            countWhilePolling > 0 && countAfterIdle >= countWhilePolling,
        )
    }

    @Test
    fun `capture loop survives captureNow throwing once and keeps emitting afterward`() = runBlocking {
        val sessionId = 9006
        val hasThrown = AtomicBoolean(false)
        every { VisualizerHelper.captureNow(any()) } answers {
            if (hasThrown.compareAndSet(false, true)) {
                throw RuntimeException("simulated native Visualizer failure")
            }
            VisualizerSnapshot(byteArrayOf(1), byteArrayOf(2), callCount.incrementAndGet())
        }

        val flow = VisualizerCaptureCoordinator.snapshotFlow(sessionId)
        val job = launch { flow.collect {} }
        delay(150)
        job.cancel()

        assertTrue(
            "a single captureNow() exception must not permanently kill the shared StateFlow's " +
                "capture coroutine -- it should log and keep emitting on the next tick",
            callCount.get() > 0,
        )
    }
}
