package app.n_zik.android.components.ui.screens.rewind

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import app.n_zik.android.MainActivity
import app.n_zik.android.R
import app.n_zik.android.rewindDeckTargetFromIntent
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
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Tests [RewindYearlyReminderWorker.doWork] against the frozen I/O matrix rows
 * (NOTIF_DENIED, GATE_OFF_EXECUTION, DECODE_YEARLY) and the yearly worker's acceptance
 * criteria: with `POST_NOTIFICATIONS` denied on API 33+ the reminder is skipped silently
 * (no notification, no crash) while the worker keeps its yearly cadence; with the grant,
 * the finished year's reminder is posted with a content intent carrying the year extra
 * only — the missing month extra being the sentinel of the yearly deck. The scheduling
 * helpers ([RewindYearlyReminderWorker.msUntilNextYearStart],
 * [RewindYearlyReminderWorker.applyJitter]) are pinned at their bounds.
 *
 * Robolectric provides the real framework plumbing (runtime permissions,
 * NotificationManager, PendingIntent). The self-reschedule goes through the
 * [RewindYearlyReminderWorker.rescheduleHook] seam because a live WorkManager is not
 * reachable from JVM tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RewindYearlyReminderWorkerTest {

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
            "the worker must keep its yearly cadence even when the gate skips",
            1,
            reschedules.size
        )
    }

    // The API 37 stubs deprecate Notification.priority (no non-deprecated getter exists in
    // the stubs — the field is the only accessor)
    @Test
    @Suppress("DEPRECATION")
    fun grantedPostNotificationsPostsTheFinishedYearReminder() {
        setPermissionState(granted = true, permission = Manifest.permission.POST_NOTIFICATIONS)
        val reschedules = mutableListOf<Context>()
        val worker = workerWithRescheduleHook(reschedules)

        val result = runBlocking { worker.doWork() }

        // WorkManager results carry no equals(): a Success is the only acceptable outcome
        assertTrue(result is ListenableWorker.Result.Success)
        val notifications = postedNotifications()
        assertEquals(1, notifications.size)
        val finishedYear = LocalDate.now().year - 1
        val title = notifications.first().extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        assertTrue(
            "the reminder should offer the $finishedYear recap, got: \"$title\"",
            title.contains(finishedYear.toString())
        )
        assertEquals(
            "the yearly reminder must use the yearly body, not the monthly one",
            RuntimeEnvironment.getApplication().getString(R.string.rw_yearly_notif_body),
            notifications.first().extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        val notification = notifications.first()
        assertEquals(
            "the yearly reminder must post on the shared rewind channel",
            RewindReminderWorker.CHANNEL_ID,
            notification.channelId
        )
        assertEquals(
            "the yearly reminder must stay low-priority like the monthly one",
            NotificationCompat.PRIORITY_LOW,
            notification.priority
        )
        // The framework `autoCancel` field is gone from the API 37 stubs; NotificationCompat
        // reads it back from the FLAG_AUTO_CANCEL bit the compat builder sets
        assertTrue(
            "the yearly reminder must auto-cancel on tap",
            NotificationCompat.getAutoCancel(notification)
        )
        assertEquals(1, reschedules.size)
    }

    @Test
    fun notificationContentIntentOpensTheFinishedYearDeck() {
        setPermissionState(granted = true, permission = Manifest.permission.POST_NOTIFICATIONS)
        val worker = workerWithRescheduleHook(mutableListOf())

        runBlocking { worker.doWork() }

        val pending = postedNotifications().single().contentIntent
        assertNotNull("the reminder must carry a content intent", pending)
        val contentIntent = shadowOf(pending).savedIntent
        assertEquals(MainActivity::class.java.name, contentIntent.component?.className)
        val finishedYear = LocalDate.now().year - 1
        assertEquals(
            "the content intent must target the finished year",
            finishedYear,
            contentIntent.getIntExtra(RewindReminderWorker.EXTRA_DECK_YEAR, -1)
        )
        assertEquals(
            "the yearly content intent must not carry a month extra (its absence is the yearly sentinel)",
            -1,
            contentIntent.getIntExtra(RewindReminderWorker.EXTRA_DECK_MONTH, -1)
        )
        // Producer/consumer join: the posted intent must decode through the shared contract
        // (MainActivity's rewindDeckTargetFromIntent) to the yearly target
        assertEquals(
            finishedYear to 0,
            rewindDeckTargetFromIntent(contentIntent, isRestoredInstance = false)
        )
    }

    // ---- Settings gating (frozen contract GH-275): master / yearly / yearly-notif ----

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
    fun yearlyOffSkipsTheNotificationDespiteTheNotifToggleOn() {
        setPermissionState(granted = true, permission = Manifest.permission.POST_NOTIFICATIONS)
        // Hierarchical gate: a type off implies its notification off, even with the notif toggle on
        DataStoreUtils.saveBoolean(RuntimeEnvironment.getApplication(), DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, false)
        val reschedules = mutableListOf<Context>()
        val worker = workerWithRescheduleHook(reschedules)

        val result = runBlocking { worker.doWork() }

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, postedNotifications().size)
        assertEquals(1, reschedules.size)
    }

    @Test
    fun yearlyNotifOffSkipsTheNotification() {
        setPermissionState(granted = true, permission = Manifest.permission.POST_NOTIFICATIONS)
        // The yearly recap itself stays on (its home entries remain) but its notification is off
        DataStoreUtils.saveBoolean(RuntimeEnvironment.getApplication(), DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED, false)
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
        assertTrue(RewindYearlyReminderWorker.isYearlyReminderEnabled(app))

        // Each off-toggle alone disables the gate, in which case schedule() cancels the work
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_ENABLED, false)
        assertFalse(RewindYearlyReminderWorker.isYearlyReminderEnabled(app))
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_ENABLED, true)
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, false)
        assertFalse(RewindYearlyReminderWorker.isYearlyReminderEnabled(app))
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, true)
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED, false)
        assertFalse(RewindYearlyReminderWorker.isYearlyReminderEnabled(app))

        // Re-enable: the gate is on again and the next schedule() re-enqueues the work
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED, true)
        assertTrue(RewindYearlyReminderWorker.isYearlyReminderEnabled(app))
    }

    // ---- Scheduling math: next 1st of January + jitter clamp ----

    @Test
    fun msUntilNextYearStartCountsToTheNextJanuaryFirst() {
        val utc = ZoneOffset.UTC
        // A mid-year date in 2026 counts to the 1st of January 2027, 00:00 local
        val mid2026 = ZonedDateTime.of(2026, 9, 24, 10, 30, 0, 0, utc)
        assertEquals(
            Duration.between(mid2026, ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, utc)).toMillis(),
            RewindYearlyReminderWorker.msUntilNextYearStart(mid2026)
        )
        // A year-end date counts the few remaining minutes
        val newYearsEve = ZonedDateTime.of(2026, 12, 31, 23, 59, 0, 0, utc)
        assertEquals(
            Duration.between(newYearsEve, ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, utc)).toMillis(),
            RewindYearlyReminderWorker.msUntilNextYearStart(newYearsEve)
        )
        // On the 1st of January itself (the execution day), the next target is the year after
        val januaryFirst = ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, utc)
        assertEquals(
            Duration.between(januaryFirst, ZonedDateTime.of(2028, 1, 1, 0, 0, 0, 0, utc)).toMillis(),
            RewindYearlyReminderWorker.msUntilNextYearStart(januaryFirst)
        )
        // Same, a few hours into the 1st
        val januaryFirstNoon = ZonedDateTime.of(2027, 1, 1, 12, 0, 0, 0, utc)
        assertEquals(
            Duration.between(januaryFirstNoon, ZonedDateTime.of(2028, 1, 1, 0, 0, 0, 0, utc)).toMillis(),
            RewindYearlyReminderWorker.msUntilNextYearStart(januaryFirstNoon)
        )
        // A non-UTC zone: the target is 00:00 in now's own zone (here CEST in June, CET in
        // January — the offset difference is part of the expected duration)
        val paris = ZoneId.of("Europe/Paris")
        val parisSummer = ZonedDateTime.of(2026, 6, 15, 12, 0, 0, 0, paris)
        assertEquals(
            Duration.between(parisSummer, ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, paris)).toMillis(),
            RewindYearlyReminderWorker.msUntilNextYearStart(parisSummer)
        )
    }

    @Test
    fun applyJitterClampsTheDelayToNonNegative() {
        assertEquals(15_000L, RewindYearlyReminderWorker.applyJitter(10_000L, 5_000L))
        assertEquals(0L, RewindYearlyReminderWorker.applyJitter(0L, -5_000L))
        assertEquals(0L, RewindYearlyReminderWorker.applyJitter(5_000L, -10_000L))
    }

    private fun workerWithRescheduleHook(reschedules: MutableList<Context>): RewindYearlyReminderWorker {
        val worker = RewindYearlyReminderWorker(RuntimeEnvironment.getApplication(), mockk<WorkerParameters>(relaxed = true))
        worker.rescheduleHook = { reschedules.add(it) }
        return worker
    }

    private fun postedNotifications(): List<Notification> =
        shadowOf(RuntimeEnvironment.getApplication().getSystemService(NotificationManager::class.java))
            .allNotifications
}
