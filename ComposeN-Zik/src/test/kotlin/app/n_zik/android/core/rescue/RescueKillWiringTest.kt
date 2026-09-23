package app.n_zik.android.core.rescue

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.MainApplication
import app.n_zik.android.components.ui.screens.rescue.RescueActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess

/**
 * Wiring tests for the main-process side of [RescueProcess], which the pure flag tests
 * ([RescueProcessTest]) and the broadcast test ([RescueProcessRequestTest]) cannot reach:
 *
 * - startup self-kill: a pending kill-request flag consumed in [MainApplication.onCreate]
 *   ends the process BEFORE any heavy initialization;
 * - hot-kill receiver nonce verification: a broadcast whose nonce matches the pending flag
 *   ends the process, a foreign or missing nonce leaves the pending flag intact (API < 33
 *   sender verification: the receiver is exported there);
 * - opening the Rescue Center ([RescueActivity.onCreate]) requests the kill (broadcast +
 *   safety-net flag).
 *
 * Robolectric's [ShadowProcess] only records [Process.killProcess] calls — it never kills
 * the test JVM, so `wasKilled` is the observation point.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RescueKillWiringTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Drains the background looper the kill receiver is registered on. */
    private fun idleKillHandler() {
        Shadows.shadowOf(RescueProcess.KILL_REQUEST_HANDLER.looper).idle()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Startup self-kill (safety-net flag consumed before heavy init)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `startup with a pending kill flag ends the main process before heavy init`() {
        ShadowProcess.clearKilledProcesses()
        // isMainProcess() (SDK 28+) reads the shadowed process name.
        ShadowProcess.setProcessName(context.packageName)
        // A pending request from a previous :rescue session: the broadcast was lost (the main
        // process was already dead when the Rescue Center opened).
        RescueProcess.writeKillRequestFlag(context.filesDir, "nonce=test-rescue pid=1 at=2")

        // Robolectric pins a plain Application (robolectric.properties), so MainApplication is
        // never created by the runtime. A bare instance has no base context, and
        // isMainProcess() reads the package name through it; attachBaseContext is protected
        // (the framework calls it from android.app), so attach it via the same plain call.
        val app = MainApplication()
        ContextWrapper::class.java
            .getDeclaredMethod("attachBaseContext", Context::class.java)
            .apply { isAccessible = true }
            .invoke(app, context)
        app.onCreate()

        assertTrue(
            "a pending kill request must end the main process at startup",
            ShadowProcess.wasKilled(Process.myPid())
        )
        assertFalse(
            "the flag must be consumed before the kill (no kill loop on the next launch)",
            RescueProcess.hasKillRequest(context)
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Hot-kill receiver: nonce verification
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the kill receiver ends the process when the broadcast nonce matches the flag`() {
        ShadowProcess.clearKilledProcesses()

        RescueProcess.registerKillMainReceiver(context)
        RescueProcess.requestKillMain(context)
        idleKillHandler()

        assertTrue(
            "a matching kill request must end the main process",
            ShadowProcess.wasKilled(Process.myPid())
        )
        assertFalse(
            "the flag must be consumed before the kill (no kill loop on the next launch)",
            RescueProcess.hasKillRequest(context)
        )
    }

    @Test
    fun `a kill broadcast with a foreign nonce is ignored and the pending flag stays`() {
        ShadowProcess.clearKilledProcesses()
        RescueProcess.writeKillRequestFlag(context.filesDir, "nonce=legit-nonce-1 rescue pid=1 at=2")
        RescueProcess.registerKillMainReceiver(context)

        // API < 33 the receiver is exported: a third-party app can broadcast the action but
        // cannot know the per-request nonce, which only the flag and the broadcast share.
        context.sendBroadcast(
            Intent(RescueProcess.ACTION_KILL_MAIN)
                .setPackage(context.packageName)
                .putExtra(RescueProcess.EXTRA_KILL_NONCE, "attacker-nonce")
        )
        idleKillHandler()

        assertFalse(
            "a foreign nonce must not trigger the kill",
            ShadowProcess.wasKilled(Process.myPid())
        )
        assertTrue(
            "the pending request must stay intact for a real re-request",
            RescueProcess.hasKillRequest(context)
        )

        // Clean up: the still-pending request must not leak into a next healthy launch.
        RescueProcess.cancelKillRequest(context)
    }

    @Test
    fun `a kill broadcast without a nonce is ignored and the pending flag stays`() {
        ShadowProcess.clearKilledProcesses()
        RescueProcess.writeKillRequestFlag(context.filesDir, "nonce=legit-nonce-2 rescue pid=1 at=2")
        RescueProcess.registerKillMainReceiver(context)

        context.sendBroadcast(
            Intent(RescueProcess.ACTION_KILL_MAIN)
                .setPackage(context.packageName)
        )
        idleKillHandler()

        assertFalse(
            "a nonce-less broadcast must not trigger the kill",
            ShadowProcess.wasKilled(Process.myPid())
        )
        assertTrue(
            "the pending request must stay intact for a real re-request",
            RescueProcess.hasKillRequest(context)
        )

        // Clean up: the still-pending request must not leak into a next healthy launch.
        RescueProcess.cancelKillRequest(context)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Rescue Center entry point
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `opening the rescue center requests the main process kill`() {
        // Clean state, whatever previous tests left behind.
        RescueProcess.cancelKillRequest(context)

        var received: Intent? = null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                received = i
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(RescueProcess.ACTION_KILL_MAIN),
            Context.RECEIVER_NOT_EXPORTED
        )

        // onCreate only: the kill request is sent before the UI is composed.
        Robolectric.buildActivity(RescueActivity::class.java).create()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull("opening the Rescue Center must broadcast the kill request", received)
        val sent = received!! // non-null: asserted by the assertNotNull directly above
        assertEquals(RescueProcess.ACTION_KILL_MAIN, sent.action)
        // setPackage: the broadcast can only be received by this app's own processes.
        assertEquals(context.packageName, sent.`package`)
        assertTrue(
            "the safety-net flag must be written with the request",
            RescueProcess.hasKillRequest(context)
        )

        context.unregisterReceiver(receiver)
        // Clean up: the request is moot in the test.
        RescueProcess.cancelKillRequest(context)
    }
}
