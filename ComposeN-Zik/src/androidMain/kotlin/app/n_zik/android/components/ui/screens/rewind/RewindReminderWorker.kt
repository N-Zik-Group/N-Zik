package app.n_zik.android.components.ui.screens.rewind

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.n_zik.android.MainActivity
import app.n_zik.android.R
import app.n_zik.android.utils.DataStoreUtils
import timber.log.Timber
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * End-of-month rewind reminder (frozen task GH-275): one notification a month, posted by
 * WorkManager on the 1st — even when the app is closed — telling the user that the previous
 * month's rewind is ready to open. Tapping the notification opens the deck on the finished
 * month: the content intent carries the year/month extras consumed by [MainActivity].
 *
 * The worker is self-perpetuating: every run reschedules itself for the next 1st, so a
 * single [schedule] at app startup keeps the monthly cadence for the app's whole life.
 *
 * Gating has two layers: the settings gate ([isMonthlyReminderEnabled], spec GH-275) is
 * checked when scheduling (off cancels the pending work) and again at execution (off
 * skips the post); the OS-level gate (frozen task contract) skips the notification —
 * with a log, not a failure — when `POST_NOTIFICATIONS` was denied at runtime on API 33+.
 * The channel itself ("rewind", importance LOW) is created by the application.
 */
internal class RewindReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    /**
     * Test seam: unit tests substitute this hook for the self-reschedule, which needs a live
     * WorkManager (unreachable from JVM tests). Production code never sets it.
     */
    internal var rescheduleHook: ((Context) -> Unit)? = null

    companion object {
        private const val TAG = "RewindReminder"
        private const val WORK_NAME = "RewindReminderWorker"
        internal const val CHANNEL_ID = "rewind"
        private const val NOTIFICATION_ID = 0x72657769 // 'rewi', stable across runs (autoCancel)

        /**
         * Extras carried by the notification's content intent so [MainActivity] can open the
         * deck directly on the finished month (frozen task: contentIntent vers le screen,
         * période = mois terminé).
         */
        internal const val EXTRA_DECK_YEAR = "rewind_deck_year"
        internal const val EXTRA_DECK_MONTH = "rewind_deck_month"

        // Small jitter so installs do not all hit WorkManager at the same 1st-of-month instant
        private const val JITTER_MS = 10L * 60 * 1000

        /**
         * The monthly reminder gate (spec GH-275): the reminder only runs while the master
         * switch, the monthly recap and the monthly notification toggles are all on — a type
         * off implies its notification off, even if the notification toggle is on. The yearly
         * notification toggle belongs to the yearly worker (roadmap item 2) and is not part
         * of this gate. Internal so unit tests can verify the decision without a WorkManager.
         */
        internal fun isMonthlyReminderEnabled(context: Context): Boolean =
            DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, true) &&
            DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, true) &&
            DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_NOTIF_ENABLED, true)

        /**
         * Schedules the first reminder for the next 1st of the month. KEEP avoids piling up
         * concurrent runs when the app is started several times before the work fires.
         * While the feature is off in the settings, any pending work is canceled instead so
         * a toggle takes effect without an app restart (spec GH-275).
         */
        fun schedule(context: Context) {
            if (!isMonthlyReminderEnabled(context)) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                Timber.tag(TAG).i("Rewind monthly reminder disabled in settings: canceling pending work")
                return
            }
            val request = OneTimeWorkRequestBuilder<RewindReminderWorker>()
                .setInitialDelay(
                    applyJitter(msUntilNextMonthStart(), (-JITTER_MS..JITTER_MS).random()),
                    TimeUnit.MILLISECONDS
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Milliseconds from [now] until the 1st of the next month, 00:00 in [now]'s zone.
         * Injectable for tests (spec GH-275, re-review: schedule delay untested).
         */
        internal fun msUntilNextMonthStart(now: ZonedDateTime = ZonedDateTime.now()): Long {
            val next = now.toLocalDate().plusMonths(1).withDayOfMonth(1).atStartOfDay(now.zone)
            return Duration.between(now, next).toMillis()
        }

        /** Applies the scheduling jitter [deltaMs] and clamps the result to non-negative. */
        internal fun applyJitter(delayMs: Long, deltaMs: Long): Long =
            (delayMs + deltaMs).coerceAtLeast(0)
    }

    override suspend fun doWork(): Result {
        return try {
            val finished = LocalDate.now().minusMonths(1)
            if (!isMonthlyReminderEnabled(applicationContext)) {
                // Re-read the toggles at execution time: a flip made after the work was
                // enqueued must suppress the notification (spec GH-275). The self-reschedule
                // below still runs and, with the gate off, cancels the pending work: the
                // monthly cadence resumes at the next app startup while the gate is on.
                Timber.tag(TAG).i("Rewind disabled in settings: skipping the %s reminder",
                    finished.month.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                Result.success()
            } else if (
                Build.VERSION.SDK_INT >= 33 &&
                applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                // The runtime grant is the frozen gate: denied means no notification,
                // but the worker keeps its monthly cadence for when it is granted back
                Timber.tag(TAG).i("POST_NOTIFICATIONS denied: skipping the %s rewind reminder",
                    finished.month.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                Result.success()
            } else {
                postRewindReminder(finished)
                Result.success()
            }
        } finally {
            // Self-perpetuating cadence: always schedule the next month, success or failure
            rescheduleHook?.invoke(applicationContext) ?: schedule(applicationContext)
        }
    }

    private fun postRewindReminder(finishedMonth: LocalDate) {
        val manager = NotificationManagerCompat.from(applicationContext)
        val monthName = finishedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())

        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_DECK_YEAR, finishedMonth.year)
            .putExtra(EXTRA_DECK_MONTH, finishedMonth.monthValue)
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(applicationContext.getString(R.string.rw_notif_title, monthName))
                .setContentText(applicationContext.getString(R.string.rw_notif_body))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
        )
        Timber.tag(TAG).i("Posted the %s rewind reminder", monthName)
    }
}
