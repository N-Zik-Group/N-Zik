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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess
import java.io.File
import java.time.Duration

/**
 * Wiring tests for the main-process side of [RescueProcess], which the pure flag tests
 * ([RescueProcessTest]) and the broadcast test ([RescueProcessRequestTest]) cannot reach:
 *
 * - startup self-kill: a pending kill-request flag consumed in [MainApplication.onCreate]
 *   ends the process BEFORE any heavy initialization — but a flag older than the max age
 *   (a leftover from an older app version) is discarded without killing;
 * - hot-kill receiver nonce verification: a broadcast whose nonce matches the pending flag
 *   ends the process, a foreign or missing nonce leaves the pending flag intact (API < 33
 *   sender verification: the receiver is exported there); a consumed kill also invalidates
 *   the alive marker, so a re-opened Rescue Center records no new request for the dead
 *   process;
 * - opening the Rescue Center ([RescueActivity.onCreate]) records the kill request (broadcast
 *   + safety-net flag) ONLY when the main process is (likely) alive — the alive marker from
 *   31 on, the trustworthy probe below — and records nothing when it is already dead; the
 *   `:rescue` process never refreshes the alive marker itself;
 * - the alive marker updater ([RescueProcess.startAliveMarkerUpdater]) touches the marker on
 *   start and keeps re-touching it on the refresh cadence;
 * - the liveness decision table ([RescueProcess.isMainProcessLikelyAlive]): probe below 31,
 *   alive marker from 31 on.
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

    @Test
    fun `startup with a stale kill flag does not kill and discards the flag`() {
        ShadowProcess.clearKilledProcesses()
        ShadowProcess.setProcessName(context.packageName)
        // Leftover from an older app version, which recorded the flag on every Rescue open
        // even while the process was alive: planted now, then aged past the max age.
        val flag = RescueProcess.writeKillRequestFlag(context.filesDir, "nonce=test-stale-1 rescue pid=1 at=2")
        flag.setLastModified(System.currentTimeMillis() - RescueProcess.KILL_REQUEST_MAX_AGE_MS - 60_000)

        val app = MainApplication()
        ContextWrapper::class.java
            .getDeclaredMethod("attachBaseContext", Context::class.java)
            .apply { isAccessible = true }
            .invoke(app, context)
        app.onCreate()

        assertFalse(
            "a stale leftover flag must not self-kill the first launch after an upgrade",
            ShadowProcess.wasKilled(Process.myPid())
        )
        assertFalse(
            "the stale flag must be discarded so it cannot linger either",
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
    fun `a consumed kill invalidates the alive marker before the process dies`() {
        ShadowProcess.clearKilledProcesses()

        // The main process is alive: its updater keeps the marker fresh.
        RescueProcess.registerKillMainReceiver(context)
        RescueProcess.touchMainAliveMarker(context)
        assertTrue(
            "precondition: the marker must read fresh before the kill",
            RescueProcess.isMainAliveMarkerFresh(context)
        )

        RescueProcess.requestKillMain(context)
        idleKillHandler()

        assertTrue(
            "a matching kill request must end the main process",
            ShadowProcess.wasKilled(Process.myPid())
        )
        assertFalse(
            "the marker must read stale after the kill: a re-opened Rescue Center in the " +
                "staleness window must record no new request for an already-dead process",
            RescueProcess.isMainAliveMarkerFresh(context)
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
        File(context.filesDir, RescueProcess.ALIVE_MARKER_NAME).delete()

        // The main process is alive when the Rescue Center opens: its background updater
        // keeps the alive marker fresh, so the conditional onCreate records the request.
        RescueProcess.touchMainAliveMarker(context)
        // Invariant: the `:rescue` process never refreshes the alive marker. Capture the
        // mtime and assert it is unchanged after onCreate.
        val markerMtimeBefore =
            File(context.filesDir, RescueProcess.ALIVE_MARKER_NAME).lastModified()

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
        assertEquals(
            "the `:rescue` process must never refresh the alive marker",
            markerMtimeBefore,
            File(context.filesDir, RescueProcess.ALIVE_MARKER_NAME).lastModified()
        )

        context.unregisterReceiver(receiver)
        // Clean up: the request is moot in the test.
        RescueProcess.cancelKillRequest(context)
    }

    @Test
    fun `opening the rescue center with the app already closed records no kill request`() {
        // Clean state, whatever previous tests left behind.
        RescueProcess.cancelKillRequest(context)
        File(context.filesDir, RescueProcess.ALIVE_MARKER_NAME).delete()

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

        // No marker, no updater: the main process is dead (e.g. force-stopped), so the
        // alive check is false and onCreate must record nothing — a stale flag would make
        // the next healthy launch end itself at startup.
        Robolectric.buildActivity(RescueActivity::class.java).create()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNull(
            "no kill broadcast may be sent for an already-dead process",
            received
        )
        assertFalse(
            "no kill flag may be recorded: the next healthy launch must not self-kill",
            RescueProcess.hasKillRequest(context)
        )

        context.unregisterReceiver(receiver)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Liveness decision table (isMainProcessLikelyAlive)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `from 31 on liveness follows the alive marker, not the probe`() {
        // From 31 on getRunningAppProcesses() is restricted to the calling process: Robolectric's
        // probe never lists the main process, so only the marker may answer "alive".
        assertFalse(
            "no marker: the main process is not reported alive",
            RescueProcess.isMainProcessLikelyAlive(context)
        )

        RescueProcess.touchMainAliveMarker(context)
        assertTrue(
            "a fresh marker: the main process is reported alive despite the dead probe",
            RescueProcess.isMainProcessLikelyAlive(context)
        )

        File(context.filesDir, RescueProcess.ALIVE_MARKER_NAME).delete()
    }

    @Test
    @Config(sdk = [30])
    fun `below 31 liveness follows the trustworthy probe, not the marker`() {
        // Below 31 the probe is authoritative: the alive marker is not even consulted, and
        // Robolectric's probe never lists the main process as running.
        RescueProcess.touchMainAliveMarker(context)

        assertFalse(
            "below 31 the probe decides, and it reports the main process dead",
            RescueProcess.isMainProcessLikelyAlive(context)
        )

        File(context.filesDir, RescueProcess.ALIVE_MARKER_NAME).delete()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Alive marker updater (main-process background loop)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the alive marker updater touches the marker and keeps re-touching it`() {
        val marker = File(context.filesDir, RescueProcess.ALIVE_MARKER_NAME)
        marker.delete()

        // The first tick is posted immediately on the background looper: drain it to run.
        RescueProcess.startAliveMarkerUpdater(context)
        Shadows.shadowOf(RescueProcess.ALIVE_MARKER_HANDLER.looper).idle()

        assertTrue(
            "the immediate first tick must create the alive marker",
            marker.exists()
        )
        assertTrue(
            "a just-touched marker must read fresh",
            RescueProcess.isMainAliveMarkerFresh(context)
        )

        // The loop must re-touch the marker instead of stopping after the first tick:
        // advance the same shadow looper past the refresh interval.
        val firstTouch = marker.lastModified()
        Shadows.shadowOf(RescueProcess.ALIVE_MARKER_HANDLER.looper)
            .idleFor(Duration.ofMillis(RescueProcess.ALIVE_MARKER_REFRESH_MS + 1_000))
        assertTrue(
            "the updater must re-touch the marker after the refresh interval (it never stops)",
            marker.lastModified() > firstTouch
        )
    }
}
