package app.it.fast4x.rimusic.utils

import android.app.Notification
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Covers spec-gh-606-threading-g5.md I/O & edge-case matrix rows 3 and 4
 * (SCREEN_OFF cancels the 30 s tick; onDestroy stops it) plus the restart
 * behavior of the coroutine-based tick loop in [InvincibleService].
 *
 * Virtual time: [Dispatchers.setMain] swaps the delegate of the stable test
 * main dispatcher behind [NzikDispatchers.UI] (== Dispatchers.Main), so the
 * tick loop's `delay(30_000)` is driven by [TestCoroutineScheduler.advanceTimeBy]
 * instead of real time.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's
 * junit-vintage-engine on the JUnit 5 platform — do not convert the annotations
 * to JUnit 5, the Robolectric runner only works with JUnit 4.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InvincibleServiceTest {

    private val scheduler = TestCoroutineScheduler()

    private var startForegroundCalls = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        startForegroundCalls = 0
        mockkStatic(ServiceCompat::class)
        every { ServiceCompat.startForeground(any(), any(), any(), any()) } answers {
            startForegroundCalls++
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `screenOff cancels the 30s tick and no further tick runs after it`() {
        val service = createStartedService()
        service.startInvincibility()

        scheduler.runCurrent() // tick coroutine starts, suspends in delay(30_000)
        scheduler.advanceTimeBy(30_001) // first tick runs
        assertEquals(1, startForegroundCalls)

        // SCREEN_OFF: cancels the pending tick (equivalent of the former
        // removeCallbacks) and performs the one-shot startForeground.
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        // Robolectric (PAUSED looper mode) delivers the broadcast to the
        // registered receiver through the main looper — flush it before

        // asserting on the one-shot startForeground.
        ShadowLooper.idleMainLooper()
        assertEquals(2, startForegroundCalls)

        // Matrix row 3: advancing virtual time past several 30 s intervals
        // produces no additional tick (no additional startForeground).
        scheduler.advanceTimeBy(5L * 30_001)
        assertEquals(2, startForegroundCalls)
    }

    @Test
    fun `double screenOn leaves a single tick chain`() {
        val service = createStartedService()
        service.startInvincibility()

        scheduler.runCurrent() // initial tick coroutine suspends in delay(30_000)

        // Two SCREEN_ON broadcasts in a row: each cancels the previous tick job and
        // relaunches runTickLoop (which ticks immediately) — only the last chain survives.
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        ShadowLooper.idleMainLooper()
        scheduler.runCurrent() // the relaunched loop starts, ticks, suspends
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        ShadowLooper.idleMainLooper()
        scheduler.runCurrent() // the second relaunch starts, ticks, suspends

        assertEquals(2, startForegroundCalls) // one immediate tick per SCREEN_ON

        // Advancing virtual time past several 30 s intervals must produce the
        // single-cadence count (one tick per interval), not a doubled chain.
        scheduler.advanceTimeBy(30_001)
        assertEquals(3, startForegroundCalls)
        scheduler.advanceTimeBy(60_002)
        assertEquals(5, startForegroundCalls)
    }

    @Test
    fun `onDestroy stops the tick and no more startForeground runs after it`() {
        val service = createStartedService()
        service.startInvincibility()

        scheduler.runCurrent()
        scheduler.advanceTimeBy(30_001) // one tick
        assertEquals(1, startForegroundCalls)

        service.onDestroy() // Invincibility.stop(): tick job cancelled, receiver unregistered

        // Matrix row 4: advancing virtual time past several 30 s intervals
        // produces no further startForeground.
        scheduler.advanceTimeBy(5L * 30_001)
        assertEquals(1, startForegroundCalls)
    }

    @Test
    fun `tick scope is reusable - restarting after stop launches a new tick`() {
        val service = createStartedService()
        service.startInvincibility()

        scheduler.runCurrent()
        scheduler.advanceTimeBy(30_001)
        assertEquals(1, startForegroundCalls)

        service.stopInvincibility() // stop() cancels only the tick job, not the scope
        scheduler.advanceTimeBy(5L * 30_001)
        assertEquals(1, startForegroundCalls)

        service.startInvincibility() // start() again on the same, never-cancelled scope
        scheduler.runCurrent()
        scheduler.advanceTimeBy(30_001)
        assertEquals(2, startForegroundCalls)
    }

    private fun createStartedService(): TestInvincibleService {
        val service = Robolectric.buildService(TestInvincibleService::class.java).create().get()
        // SDK 33: isAllowedToStartForegroundServices requires the
        // battery-optimization exemption (isAtLeastAndroid12 is true).
        val powerManager = service.getSystemService(PowerManager::class.java)
        requireNotNull(powerManager) { "PowerManager is always available under Robolectric" }
        shadowOf(powerManager).setIgnoringBatteryOptimizations(service.packageName, true)
        // onUnbind() creates the Invincibility instance (the tick loop owner).
        service.onUnbind(null)
        return service
    }

    private class TestInvincibleService : InvincibleService() {
        override val isInvincibilityEnabled: Boolean get() = true
        override val notificationId: Int = 42
        override fun shouldBeInvincible(): Boolean = true
        override fun notification(): Notification = NotificationCompat.Builder(this).build()

        fun startInvincibility() = makeInvincible(true)
        fun stopInvincibility() = makeInvincible(false)
    }
}
