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
import java.util.concurrent.TimeUnit

/**
 * Year-end rewind reminder (roadmap item 2, last point of the frozen contract GH-275): one
 * notification a year, posted by WorkManager on the 1st of January — even when the app is
 * closed — telling the user that the finished year's rewind is ready to open. The firing is
 * spread by a positive jitter of up to 10 minutes AFTER the 1st (never before, so the
 * execution always sees the new year). Tapping the notification opens the yearly deck on the
 * finished year: the content intent carries the year extra only (no month extra), which is
 * the sentinel the shared content-intent decode in [MainActivity] reads as a year-only target.
 *
 * The worker is self-perpetuating: every run reschedules itself for the next 1st of January, so
 * a single [schedule] at app startup keeps the yearly cadence for the app's whole life.
 *
 * Gating has two layers: the settings gate ([isYearlyReminderEnabled], frozen contract GH-275)
 * is checked when scheduling (off cancels the pending work) and again at execution (off
 * skips the post); the OS-level gate skips the notification — with a log, not a failure —
 * when `POST_NOTIFICATIONS` was denied at runtime on API 33+.
 * The channel itself ("rewind", importance LOW) is shared with the monthly worker and created
 * by the application.
 */
internal class RewindYearlyReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    /**
     * Test seam: unit tests substitute this hook for the self-reschedule, which needs a live
     * WorkManager (unreachable from JVM tests). Production code never sets it.
     */
    internal var rescheduleHook: ((Context) -> Unit)? = null

    companion object {
        private const val TAG = "RewindYearlyReminder"
        private const val WORK_NAME = "RewindYearlyReminderWorker"
        // Distinct from the monthly worker's 0x72657769 ('rewi'): both notifications coexist
        // and autoCancel independently
        private const val NOTIFICATION_ID = 0x7265776C // 'rewl', stable across runs (autoCancel)

        // Positive-only jitter (up to 10 minutes AFTER the 1st of January, never before) so
        // installs do not all hit WorkManager at the same 1st-of-January instant without a
        // single work firing before the new year starts
        private const val JITTER_MS = 10L * 60 * 1000

        /**
         * The yearly reminder gate (frozen contract GH-275): the reminder only runs while the
         * master switch, the yearly recap and the yearly notification toggles are all on — a
         * type off implies its notification off, even if the notification toggle is on. The
         * monthly notification toggle belongs to the monthly worker and is not part of this
         * gate. Internal so unit tests can verify the decision without a WorkManager.
         */
        internal fun isYearlyReminderEnabled(context: Context): Boolean =
            DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, true) &&
            DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, true) &&
            DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED, true)

        /**
         * Schedules the first reminder for the next 1st of January, with a positive jitter
         * that can only push the firing up to 10 minutes after the 1st — never before, so
         * `doWork` always observes the new year. KEEP avoids piling up concurrent runs when
         * the app is started several times before the work fires. While the feature is off
         * in the settings, any pending work is canceled instead so a toggle takes effect
         * without an app restart (frozen contract GH-275).
         */
        fun schedule(context: Context) {
            if (!isYearlyReminderEnabled(context)) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                Timber.tag(TAG).i("Rewind yearly reminder disabled in settings: canceling pending work")
                return
            }
            val request = OneTimeWorkRequestBuilder<RewindYearlyReminderWorker>()
                .setInitialDelay(
                    applyJitter(msUntilNextYearStart(), (0L..JITTER_MS).random()),
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
         * Milliseconds from [now] until the 1st of the next year, 00:00 in [now]'s zone —
         * always strictly in the future, even on the 1st of January itself. Injectable for
         * tests.
         */
        internal fun msUntilNextYearStart(now: ZonedDateTime = ZonedDateTime.now()): Long {
            val next = now.toLocalDate().plusYears(1).withMonth(1).withDayOfMonth(1).atStartOfDay(now.zone)
            return Duration.between(now, next).toMillis()
        }

        /**
         * Applies the positive-only scheduling jitter [deltaMs] (the firing is spread up to
         * 10 minutes after the 1st of January, never before) and clamps the result to
         * non-negative as a safety net.
         */
        internal fun applyJitter(delayMs: Long, deltaMs: Long): Long =
            (delayMs + deltaMs).coerceAtLeast(0)
    }

    override suspend fun doWork(): Result {
        return try {
            // Computed at execution, not scheduling: the work fires on the 1st of January of
            // year Y, so the finished year is Y-1 — the same "now minus one" approach the
            // monthly worker uses for its finished month (a one-year delay is subject to
            // WorkManager's realities, so scheduling-time math would be stale).
            val finishedYear = LocalDate.now().year - 1
            if (!isYearlyReminderEnabled(applicationContext)) {
                // Re-read the toggles at execution time: a flip made after the work was
                // enqueued must suppress the notification (frozen contract GH-275). The
                // self-reschedule below still runs and, with the gate off, cancels the
                // pending work: the yearly cadence resumes at the next app startup while
                // the gate is on.
                Timber.tag(TAG).i("Rewind disabled in settings: skipping the %s yearly reminder", finishedYear)
                Result.success()
            } else if (
                Build.VERSION.SDK_INT >= 33 &&
                applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                // The runtime grant is the frozen gate: denied means no notification,
                // but the worker keeps its yearly cadence for when it is granted back
                Timber.tag(TAG).i("POST_NOTIFICATIONS denied: skipping the %s yearly rewind reminder", finishedYear)
                Result.success()
            } else {
                postRewindReminder(finishedYear)
                Result.success()
            }
        } finally {
            // Self-perpetuating cadence: always schedule the next year, success or failure
            rescheduleHook?.invoke(applicationContext) ?: schedule(applicationContext)
        }
    }

    private fun postRewindReminder(finishedYear: Int) {
        val manager = NotificationManagerCompat.from(applicationContext)

        // Year extra only (the shared contract key [RewindReminderWorker.EXTRA_DECK_YEAR]) —
        // the missing month extra is the sentinel that the shared decode in [MainActivity]
        // reads as "open the yearly deck"; the monthly notification always carries a month
        // 1..12, so the two extras sets never confuse each other.
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(RewindReminderWorker.EXTRA_DECK_YEAR, finishedYear)
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, RewindReminderWorker.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(applicationContext.getString(R.string.rw_notif_title, finishedYear.toString()))
                .setContentText(applicationContext.getString(R.string.rw_yearly_notif_body))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
        )
        Timber.tag(TAG).i("Posted the %s yearly rewind reminder", finishedYear)
    }
}
