package app.n_zik.android.components.ui.screens.rescue

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.core.rescue.RescueProcess
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the main-process status row and the "Kill the app" action of
 * [RescueScreen].
 *
 * The status row polls the main process once per second. Liveness comes from
 * [RescueProcess.isMainProcessLikelyAlive]: below 31 (where
 * [app.n_zik.android.core.rescue.RescueFiles.isMainProcessRunning] is trustworthy) an
 * observed-dead process discards the pending kill flag; from 31 on (where
 * getRunningAppProcesses() is restricted to the calling process) the alive marker — refreshed
 * by the main process itself every ~5 s — decides, and the flag consumed by the kill receiver
 * (the kill landed) turns the status to "stopped" instantly. The danger-zone button re-sends
 * the kill request when the automatic one was lost — but only while the process is (still)
 * alive: once stopped it is disabled, because a request sent to a dead process would only
 * record a stale flag that the next healthy launch consumes as a self-kill.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine on the
 * JUnit 5 platform. The rule launches the real [RescueActivity]: it is declared in the
 * manifest, which Robolectric's ActivityScenario requires (an undeclared ComponentActivity
 * cannot be resolved — see robolectric PR #4736), so the production entry point composes the
 * screen and its onCreate records the kill request; each test asserts from that state.
 *
 * [statePreseeder] plants the alive marker BEFORE the activity launches (Robolectric defers
 * the activity launch until the main looper drains, so the pre-seed always precedes
 * [RescueActivity.onCreate]): every scenario starts from "the main process is alive when
 * the Rescue Center opens" (the realistic case, so onCreate records the request), and each
 * test then steers the marker stale or absent to reach the state it verifies. The plain
 * [Application] is used so the app's heavy init (DI, Room, player) is skipped. Under
 * Robolectric the main process is never listed by runningAppProcesses, i.e. the probe reports
 * "not running".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RescueScreenProcessStatusTest {

    /**
     * Plants the initial process state before the compose rule launches [RescueActivity].
     *
     * Robolectric defers the activity launch until the main looper drains, so the pre-seed
     * always precedes [RescueActivity.onCreate]: the marker is on disk when onCreate's
     * liveness check reads it, and every scenario starts from "the main process is alive
     * when the Rescue Center opens" (the realistic case, so onCreate records the kill
     * request).
     *
     * The below-31 test's pending flag is planted HERE, not in the test body: the first
     * poll already runs before the body (during the rule's before phase, together with the
     * composition), so the flag must exist before that first poll for the discard
     * assertion to mean anything. It is keyed on the test's [Config] SDK annotation, not
     * its method name — the annotation travels with the test, so a rename cannot silently
     * drop the pre-seed.
     */
    @get:Rule
    val statePreseeder = object : TestRule {
        override fun apply(base: Statement, description: Description): Statement =
            object : Statement() {
                override fun evaluate() {
                    val filesDir = ApplicationProvider.getApplicationContext<Context>().filesDir
                    RescueProcess.touchMainAliveMarker(filesDir)
                    // Method-level @Config wins, falling back to the class-level default
                    // (SDK 33): only the below-31 scenario needs the pending flag up front.
                    val sdk = description.getAnnotation(Config::class.java)
                        ?.sdk?.firstOrNull() ?: 33
                    if (sdk < 31) {
                        RescueProcess.writeKillRequestFlag(filesDir, "nonce=below-31-pending")
                    }
                    base.evaluate()
                }
            }
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<RescueActivity>()

    // Resolved in setUp(): under Robolectric the app's binary resources are bound when the
    // activity launches (the rule's before()), so an app-resource lookup in a property
    // initializer (which runs before the rule) hits a resource table without the app package.
    private lateinit var context: Context
    private lateinit var killTitle: String

    private fun aliveMarker(): File = File(context.filesDir, RescueProcess.ALIVE_MARKER_NAME)

    /**
     * Drives the compose clock forward one polling period at a time until the status row shows
     * [expectedText] — bounded, so a state that never arrives still fails the test.
     *
     * Why not a fixed advanceTimeBy + waitForIdle: the poll loop reads liveness on the real
     * [NzikDispatchers.DATA] thread, which the fake main clock and waitForIdle do not control.
     * They can return before the tick's state update lands (the update arrives when the real
     * thread hands back — slower under full-suite JVM load), which made fixed-timing
     * assertions flaky: the status row then still showed the previous state. Waiting for the
     * observed state keeps the assertion deterministic while still proving the poll
     * establishes the state within a few ticks.
     */
    private fun awaitStatus(expectedText: String) {
        composeRule.waitForIdle()
        repeat(10) {
            composeRule.mainClock.advanceTimeBy(1_500)
            composeRule.waitForIdle()
            try {
                composeRule.onNodeWithText(expectedText).assertIsDisplayed()
                return
            } catch (e: AssertionError) {
                // State not there yet: give the next polling period a chance.
            }
        }
        // Failed to observe the state within the bound: report the standard assertion error.
        composeRule.onNodeWithText(expectedText).assertIsDisplayed()
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        killTitle = context.getString(R.string.rescue_kill_app)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Status polling
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Config(sdk = [30])
    fun `below 31 an observed dead main process discards the pending flag`() {
        // The pre-seed planted a pending flag (a request recorded while the probe still saw
        // the process alive): the trustworthy probe says dead, so the poll must discard it,
        // show "stopped", and leave the kill button disabled.
        awaitStatus(context.getString(R.string.rescue_status_main_process_stopped))
        assertFalse(
            "the trustworthy probe saw the main process dead: the pending flag must be " +
                "discarded so the next healthy launch does not self-kill",
            RescueProcess.hasKillRequest(context)
        )
        composeRule.onNodeWithText(killTitle).assertIsNotEnabled()
    }

    @Test
    fun `from 31 on the status follows the pending flag`() {
        // Precondition: opening the Rescue Center already requested the kill (the pre-seeded
        // marker made onCreate record it).
        assertTrue(
            "opening the Rescue Center must leave a pending kill request",
            RescueProcess.hasKillRequest(context)
        )

        awaitStatus(context.getString(R.string.rescue_status_main_process_stopping))
        assertTrue(
            "from 31 on 'not running' is not proof of death: the flag must not be cancelled",
            RescueProcess.hasKillRequest(context)
        )
        composeRule.onNodeWithText(killTitle).assertIsEnabled()

        // Simulate the kill landing: the main process receiver consumed the flag.
        RescueProcess.consumeKillRequest(context)
        awaitStatus(context.getString(R.string.rescue_status_main_process_stopped))
    }

    @Test
    fun `from 31 on an app still alive shows stopping and an active kill button`() {
        // The pre-seeded fresh marker is the scenario itself: the main process is alive when
        // the Rescue Center opens, so the request is pending and the process still running.
        assertTrue(
            "opening with the process alive must leave a pending kill request",
            RescueProcess.hasKillRequest(context)
        )

        awaitStatus(context.getString(R.string.rescue_status_main_process_stopping))
        composeRule.onNodeWithText(killTitle).assertIsEnabled()
    }

    @Test
    fun `from 31 on an app already closed shows stopped and disables the kill button`() {
        // The pre-seed made the app "alive" at open time; steer to the reported bug: the main
        // process is already dead, so no marker is left behind at all. The poll must observe
        // the dead state, discard the pending flag, and grey out the kill button.
        aliveMarker().delete()

        awaitStatus(context.getString(R.string.rescue_status_main_process_stopped))
        composeRule.onNodeWithText(killTitle).assertIsNotEnabled()
        assertFalse(
            "a dead main process has no pending kill: the flag must be discarded so the " +
                "next healthy launch does not self-kill",
            RescueProcess.hasKillRequest(context)
        )
    }

    @Test
    fun `from 31 on a stale marker with a pending flag shows stopped and cancels the flag`() {
        // The pre-seed made the app "alive" at open time (the request was recorded); steer to
        // the died-in-flight case: the process died before its receiver could consume the
        // flag, so the marker stops being refreshed — it is still on disk, just stale (the
        // OS never deletes it).
        aliveMarker().setLastModified(System.currentTimeMillis() - 30_000)

        awaitStatus(context.getString(R.string.rescue_status_main_process_stopped))
        composeRule.onNodeWithText(killTitle).assertIsNotEnabled()
        assertFalse(
            "the orphan flag must be cancelled: a dead process has no pending kill",
            RescueProcess.hasKillRequest(context)
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Danger zone: "Kill the app"
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the kill the app action re-sends the kill request`() {
        // Let the poll establish the "alive" state first (the pre-seeded marker keeps the
        // process alive): the kill button is active only while the process is running.
        awaitStatus(context.getString(R.string.rescue_status_main_process_stopping))
        composeRule.onNodeWithText(killTitle).assertIsEnabled()

        // The request recorded by the activity's onCreate is still pending: remember its
        // nonce to prove the click below produces a NEW request (the receiver is registered
        // now, so only the re-send is counted below).
        val originalNonce = RescueProcess.flagNonce(context.filesDir)
        assertNotNull("opening with the process alive must leave a pending request", originalNonce)

        val received = mutableListOf<Intent>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                i?.let { received += it }
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(RescueProcess.ACTION_KILL_MAIN),
            Context.RECEIVER_NOT_EXPORTED
        )

        // The card opens the confirmation dialog (it sits in the danger zone, below the fold).
        composeRule.onNodeWithText(killTitle).performScrollTo()
        composeRule.onNodeWithText(killTitle).performClick()
        composeRule.waitForIdle()
        // Confirm with the platform "OK" button: matched case-insensitively because the
        // platform string capitalization is not pinned by the app.
        composeRule.onNodeWithText("ok", substring = true, ignoreCase = true).performClick()

        // The request is sent off the main thread: wait until the flag carries the new nonce.
        composeRule.waitUntil { RescueProcess.flagNonce(context.filesDir) != originalNonce }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "the re-sent request must write the safety-net flag",
            RescueProcess.hasKillRequest(context)
        )
        assertEquals("exactly one kill broadcast must be sent", 1, received.size)
        val sent = received[0]
        assertEquals(RescueProcess.ACTION_KILL_MAIN, sent.action)
        // setPackage: the broadcast can only be received by this app's own processes.
        assertEquals(context.packageName, sent.`package`)
        val nonce = sent.getStringExtra(RescueProcess.EXTRA_KILL_NONCE)
        assertTrue(
            "the broadcast must carry a non-blank per-request nonce",
            !nonce.isNullOrBlank()
        )
        assertEquals(
            "the re-request must be a NEW nonce replacing the stale pending one",
            nonce,
            RescueProcess.flagNonce(context.filesDir)
        )

        context.unregisterReceiver(receiver)
        // Clean up: the request is moot in the test.
        RescueProcess.cancelKillRequest(context)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Process guard (guardWrite)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the process guard refuses writes while alive and releases them when dead`() {
        val clearCacheTitle = context.getString(R.string.rescue_clear_cache)
        val confirmText = context.getString(R.string.rescue_confirm_clear_cache)

        // The pre-seeded fresh marker keeps the main process "alive": the guarded write
        // must be refused — the confirmation dialog never appears.
        composeRule.onNodeWithText(clearCacheTitle).performScrollTo()
        composeRule.onNodeWithText(clearCacheTitle).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(confirmText).assertDoesNotExist()

        // Steer to death: the marker is gone, so the guard releases and the same click
        // surfaces the confirmation dialog.
        aliveMarker().delete()
        composeRule.onNodeWithText(clearCacheTitle).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(confirmText).assertIsDisplayed()
    }
}
