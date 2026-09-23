package app.n_zik.android.components.ui.screens.rewind

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import app.n_zik.android.MainActivity
import app.n_zik.android.utils.DataStoreUtils
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowInstrumentation
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Tests [RewindReminderWorker.doWork] against the frozen matrix row
 * NOTIF_SANS_PERMISSION and the worker acceptance criteria: with
 * `POST_NOTIFICATIONS` denied on API 33+ the reminder is skipped silently (no
 * notification, no crash) while the worker keeps its monthly cadence; with the
 * grant, the previous month's reminder is posted.
 *
 * Robolectric provides the real framework plumbing (runtime permissions,
 * NotificationManager, PendingIntent). The self-reschedule goes through the
 * [RewindReminderWorker.rescheduleHook] seam because a live WorkManager is not
 * reachable from JVM tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RewindReminderWorkerTest {

    private val shadowInstrumentation: ShadowInstrumentation
        get() = shadowOf(ShadowInstrumentation.getInstrumentation())

    /**
     * Robolectric 4.17 keeps the runtime permission state inside [ShadowInstrumentation], but its
     * `grantPermissions`/`denyPermissions` methods are package-private there — the test reaches
     * them through reflection.
     */
    private fun setPermissionState(granted: Boolean, permission: String) {
        val method = ShadowInstrumentation::class.java.declaredMethods
            .first { it.name == (if (granted) "grantPermissions" else "denyPermissions") && it.parameterCount == 1 }
        method.isAccessible = true
        method.invoke(shadowInstrumentation, arrayOf(permission))
    }

    @Test
    fun deniedPostNotificationsSkipsTheNotificationButKeepsTheCadence() {
        val reschedules = mutableListOf<Context>()
        val worker = workerWithRescheduleHook(reschedules)
        setPermissionState(granted = false, permission = Manifest.permission.POST_NOTIFICATIONS)

        val result = runBlocking { worker.doWork() }

        // WorkManager results carry no equals(): a Success is the only acceptable outcome
        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals("a denied grant must not produce a notification", 0, postedNotifications().size)
        assertEquals(
            "the worker must keep its monthly cadence even when the gate skips",
            1,
            reschedules.size
        )
    }

    @Test
    fun grantedPostNotificationsPostsThePreviousMonthReminder() {
        setPermissionState(granted = true, permission = Manifest.permission.POST_NOTIFICATIONS)
        val reschedules = mutableListOf<Context>()
        val worker = workerWithRescheduleHook(reschedules)

        val result = runBlocking { worker.doWork() }

        // WorkManager results carry no equals(): a Success is the only acceptable outcome
        assertTrue(result is ListenableWorker.Result.Success)
        val notifications = postedNotifications()
        assertEquals(1, notifications.size)
        val finishedMonth = LocalDate.now().minusMonths(1)
            .month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val title = notifications.first().extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        assertTrue("the reminder should offer the $finishedMonth recap, got: \"$title\"", title.contains(finishedMonth))
        assertEquals(1, reschedules.size)
    }

    @Test
    fun notificationContentIntentOpensTheFinishedMonthDeck() {
        setPermissionState(granted = true, permission = Manifest.permission.POST_NOTIFICATIONS)
        val worker = workerWithRescheduleHook(mutableListOf())

        runBlocking { worker.doWork() }

        val pending = postedNotifications().single().contentIntent
        assertNotNull("the reminder must carry a content intent", pending)
        val contentIntent = shadowOf(pending).savedIntent
        assertEquals(MainActivity::class.java.name, contentIntent.component?.className)
        val finished = LocalDate.now().minusMonths(1)
        assertEquals(
            "the content intent must target the finished month",
            finished.year,
            contentIntent.getIntExtra(RewindReminderWorker.EXTRA_DECK_YEAR, -1)
        )
        assertEquals(
            finished.monthValue,
            contentIntent.getIntExtra(RewindReminderWorker.EXTRA_DECK_MONTH, -1)
        )
    }

    // ---- Settings gating (spec GH-275): master / monthly / monthly-notif ----

    @Test
    fun masterOffSkipsTheNotificationButStillRunsTheRescheduleStep() {
        setPermissionState(granted = true, permission = Manifest.permission.POST_NOTIFICATIONS)
        DataStoreUtils.saveBoolean(RuntimeEnvironment.getApplication(), DataStoreUtils.KEY_REWIND_ENABLED, false)
        val reschedules = mutableListOf<Context>()
        val worker = workerWithRescheduleHook(reschedules)

        val result = runBlocking { worker.doWork() }

        // WorkManager results carry no equals(): a Success is the only acceptable outcome
        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals("master off must not produce a notification even with the grant", 0, postedNotifications().size)
        assertEquals(
            "the worker must still perform its reschedule step after a settings-gate skip",
            1,
            reschedules.size
        )
    }

    @Test
    fun monthlyOffSkipsTheNotificationDespiteTheNotifToggleOn() {
        setPermissionState(granted = true, permission = Manifest.permission.POST_NOTIFICATIONS)
        // Hierarchical gate: a type off implies its notification off, even with the notif toggle on
        DataStoreUtils.saveBoolean(RuntimeEnvironment.getApplication(), DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, false)
        val reschedules = mutableListOf<Context>()
        val worker = workerWithRescheduleHook(reschedules)

        val result = runBlocking { worker.doWork() }

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, postedNotifications().size)
        assertEquals(1, reschedules.size)
    }

    @Test
    fun monthlyNotifOffSkipsTheNotification() {
        setPermissionState(granted = true, permission = Manifest.permission.POST_NOTIFICATIONS)
        // The monthly recap itself stays on (its home entries remain) but its notification is off
        DataStoreUtils.saveBoolean(RuntimeEnvironment.getApplication(), DataStoreUtils.KEY_REWIND_MONTHLY_NOTIF_ENABLED, false)
        val reschedules = mutableListOf<Context>()
        val worker = workerWithRescheduleHook(reschedules)

        val result = runBlocking { worker.doWork() }

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, postedNotifications().size)
        assertEquals(1, reschedules.size)
    }

    @Test
    fun scheduleGateFollowsTheHierarchicalToggles() {
        val app = RuntimeEnvironment.getApplication()
        // Nothing saved: the defaults are all on, so the work is enqueued as today
        assertTrue(RewindReminderWorker.isMonthlyReminderEnabled(app))

        // Each off-toggle alone disables the gate, in which case schedule() cancels the work
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_ENABLED, false)
        assertFalse(RewindReminderWorker.isMonthlyReminderEnabled(app))
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_ENABLED, true)
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, false)
        assertFalse(RewindReminderWorker.isMonthlyReminderEnabled(app))
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, true)
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_MONTHLY_NOTIF_ENABLED, false)
        assertFalse(RewindReminderWorker.isMonthlyReminderEnabled(app))

        // Re-enable: the gate is on again and the next schedule() re-enqueues the work
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_MONTHLY_NOTIF_ENABLED, true)
        assertTrue(RewindReminderWorker.isMonthlyReminderEnabled(app))
    }

    private fun workerWithRescheduleHook(reschedules: MutableList<Context>): RewindReminderWorker {
        val worker = RewindReminderWorker(RuntimeEnvironment.getApplication(), mockk<WorkerParameters>(relaxed = true))
        worker.rescheduleHook = { reschedules.add(it) }
        return worker
    }

    private fun postedNotifications(): List<Notification> =
        shadowOf(RuntimeEnvironment.getApplication().getSystemService(NotificationManager::class.java))
            .allNotifications
}
