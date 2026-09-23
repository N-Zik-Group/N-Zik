package app.n_zik.android.components.ui.screens.rescue

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.core.rescue.RescueProcess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the main-process status row and the "Kill the app" action of
 * [RescueScreen].
 *
 * The status row polls the main process once per second: below 31 (where the
 * [app.n_zik.android.core.rescue.RescueFiles.isMainProcessRunning] probe is trustworthy) an
 * observed-dead process discards the pending kill flag; from 31 on (where
 * getRunningAppProcesses() is restricted to the calling process) the status follows the flag
 * itself — it turns to "stopped" only once the kill receiver has consumed it. The danger-zone
 * button re-sends the kill request (broadcast + safety-net flag) when the automatic one was
 * lost.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine on the
 * JUnit 5 platform. The rule launches the real [RescueActivity]: it is declared in the
 * manifest, which Robolectric's ActivityScenario requires (an undeclared ComponentActivity
 * cannot be resolved — see robolectric PR #4736), so the production entry point composes the
 * screen and its onCreate requests the kill; each test asserts from that state. The plain
 * [Application] is used so the app's heavy init (DI, Room, player) is skipped. Under
 * Robolectric the main process is never listed by runningAppProcesses, i.e. the probe reports
 * "not running".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RescueScreenProcessStatusTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<RescueActivity>()

    // Resolved in setUp(): under Robolectric the app's binary resources are bound when the
    // activity launches (the rule's before()), so an app-resource lookup in a property
    // initializer (which runs before the rule) hits a resource table without the app package.
    private lateinit var context: Context
    private lateinit var killTitle: String

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
        // Opening the Rescue Center wrote the pending flag (its presence is asserted by the
        // wiring tests and by the SDK 33 test below, whose poll never discards it). The first
        // poll may already have run while the rule launched the activity, so the flag cannot
        // be asserted up front here — the discard below is what this test verifies: if the
        // poll never ran, or ran without discarding, the flag would still be present.
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()

        assertFalse(
            "the trustworthy probe saw the main process dead: the pending flag must be " +
                "discarded so the next healthy launch does not self-kill",
            RescueProcess.hasKillRequest(context)
        )
    }

    @Test
    fun `from 31 on the status follows the pending flag`() {
        // Precondition: opening the Rescue Center already requested the kill.
        assertTrue(
            "opening the Rescue Center must leave a pending kill request",
            RescueProcess.hasKillRequest(context)
        )

        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()

        assertTrue(
            "from 31 on 'not running' is not proof of death: the flag must not be cancelled",
            RescueProcess.hasKillRequest(context)
        )
        composeRule
            .onNodeWithText(context.getString(R.string.rescue_status_main_process_stopping))
            .assertIsDisplayed()

        // Simulate the kill landing: the main process receiver consumed the flag.
        RescueProcess.consumeKillRequest(context)
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(context.getString(R.string.rescue_status_main_process_stopped))
            .assertIsDisplayed()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Danger zone: "Kill the app"
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the kill the app action re-sends the kill request`() {
        // Clean state: discard the request fired by the activity's onCreate (it was sent
        // before this receiver existed, so it is not counted below).
        RescueProcess.cancelKillRequest(context)

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

        composeRule.waitForIdle()

        // The card opens the confirmation dialog (it sits in the danger zone, below the fold).
        composeRule.onNodeWithText(killTitle).performScrollTo()
        composeRule.onNodeWithText(killTitle).performClick()
        composeRule.waitForIdle()
        // Confirm with the platform "OK" button: matched case-insensitively because the
        // platform string capitalization is not pinned by the app.
        composeRule.onNodeWithText("ok", substring = true, ignoreCase = true).performClick()

        // The request is sent off the main thread: wait until the flag is on disk.
        composeRule.waitUntil { RescueProcess.hasKillRequest(context) }
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

        context.unregisterReceiver(receiver)
        // Clean up: the request is moot in the test.
        RescueProcess.cancelKillRequest(context)
    }
}
