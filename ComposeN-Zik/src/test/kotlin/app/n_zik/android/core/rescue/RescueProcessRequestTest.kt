package app.n_zik.android.core.rescue

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/**
 * The broadcast side of [RescueProcess.requestKillMain] against a real (shadow) Context, which
 * the pure JUnit5 tests cannot reach: the kill broadcast must reach an intra-app receiver (the
 * one the main process registers on its background handler) with the kill action, and the
 * safety-net flag file must be written alongside so the request survives even if the broadcast
 * is lost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RescueProcessRequestTest {

    @Test
    fun `requesting the kill reaches an intra-app receiver and writes the safety-net flag`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Start from a clean state, whatever previous tests left behind.
        RescueProcess.cancelKillRequest(context)

        // The same receiver shape the main process registers (RECEIVER_NOT_EXPORTED: the kill
        // broadcast must never be visible outside the app).
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

        RescueProcess.requestKillMain(context)
        // The shadow delivers the broadcast to a handler-less receiver on the main looper.
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull("the kill broadcast must reach the app's own receiver", received)
        val sent = received!! // non-null: asserted by the assertNotNull directly above
        assertEquals(RescueProcess.ACTION_KILL_MAIN, sent.action)
        // setPackage: the broadcast can only be received by this app's own processes.
        assertEquals(context.packageName, sent.`package`)

        val flag = File(context.filesDir, RescueProcess.KILL_REQUEST_FLAG_NAME)
        assertTrue("the safety-net flag must be written with the broadcast", flag.exists())
        // The flag is a valid pending request: the main process would self-kill at startup.
        assertTrue(RescueProcess.consumeKillRequest(context))
        assertFalse("the consumed flag must be gone", flag.exists())

        context.unregisterReceiver(receiver)
    }

    @Test
    fun `a kill request can be re-sent so the failed kill can be retried`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Simulates the KILL_FAILED matrix row: the first request was consumed (or lost)
        // and the manual "Kill the app" button re-sends it.
        RescueProcess.cancelKillRequest(context)
        var received = 0
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                received++
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(RescueProcess.ACTION_KILL_MAIN),
            Context.RECEIVER_NOT_EXPORTED
        )

        RescueProcess.requestKillMain(context)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        RescueProcess.consumeKillRequest(context)
        RescueProcess.requestKillMain(context)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals("both requests must be delivered", 2, received)
        assertTrue(File(context.filesDir, RescueProcess.KILL_REQUEST_FLAG_NAME).exists())
        // Clean up: the discarded request must not leak into a next healthy launch.
        RescueProcess.cancelKillRequest(context)
        context.unregisterReceiver(receiver)
    }
}
