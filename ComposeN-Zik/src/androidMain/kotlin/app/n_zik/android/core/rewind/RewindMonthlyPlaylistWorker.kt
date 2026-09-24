package app.n_zik.android.core.rewind

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
import app.n_zik.android.components.ui.screens.rewind.RewindReminderWorker
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.DataStoreUtils
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Monthly rewind playlist worker (spec 2): on the 1st of the month, creates
 * `rewind-monthly:YYYYMM` from the finished month's most-played songs — if it does not
 * exist yet — and posts a notification when the notification toggle allows it.
 *
 * Pattern copied line-for-line from [RewindReminderWorker] (same package-structure rules),
 * with two deliberate differences (frozen spec 2):
 * - the creation gate is the dedicated `KEY_REWIND_MONTHLY_PLAYLIST_ENABLED` toggle —
 *   deliberately decoupled from the master switch and the deck type toggles;
 * - `POST_NOTIFICATIONS` denied on API 33+ does NOT skip the creation: the playlist is
 *   still created, the notification is the optional part (unlike the reminder workers,
 *   whose only payload is the notification).
 *
 * The worker is self-perpetuating: every run reschedules itself for the next 1st, so a
 * single [schedule] at app startup keeps the monthly cadence for the app's whole life.
 * The notification shares the application-created "rewind" channel (no new channel).
 */
internal class RewindMonthlyPlaylistWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    /**
     * Test seam: unit tests substitute this hook for the self-reschedule, which needs a live
     * WorkManager (unreachable from JVM tests). Production code never sets it.
     */
    internal var rescheduleHook: ((Context) -> Unit)? = null

    companion object {
        private const val TAG = "RewindMonthlyPlaylist"
        private const val WORK_NAME = "RewindMonthlyPlaylistWorker"
        private const val NOTIFICATION_ID = 0x7265706C // 'repl', stable across runs (autoCancel)

        // Positive-only jitter so installs do not all hit WorkManager at the same
        // 1st-of-month instant: it can only push the firing later (up to +10 min), never
        // before the 1st — an early fire would compute the finished month one month too far back
        private const val JITTER_MS = 10L * 60 * 1000

        /**
         * The monthly playlist creation gate (spec 1 contract): only the dedicated
         * creation toggle. Internal so unit tests can verify the decision without a
         * WorkManager.
         */
        internal fun isMonthlyPlaylistEnabled(context: Context): Boolean =
            DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, true)

        /**
         * The monthly playlist notification gate: posts only while both the notification
         * toggle and the creation toggle are on — a type off implies its notification
         * off, even if the notification toggle is on.
         */
        internal fun isMonthlyPlaylistNotificationEnabled(context: Context): Boolean =
            isMonthlyPlaylistEnabled(context) &&
            DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_NOTIF_ENABLED, true)

        /**
         * Schedules the first creation for the next 1st of the month. KEEP avoids piling up
         * concurrent runs when the app is started several times before the work fires.
         * While the feature is off in the settings, any pending work is canceled instead so
         * a toggle takes effect without an app restart.
         */
        fun schedule(context: Context) {
            if (!isMonthlyPlaylistEnabled(context)) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                Timber.tag(TAG).i("Rewind monthly playlist disabled in settings: canceling pending work")
                return
            }
            val request = OneTimeWorkRequestBuilder<RewindMonthlyPlaylistWorker>()
                .setInitialDelay(
                    applyJitter(msUntilNextMonthStart(), (0L..JITTER_MS).random()),
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
         * Injectable for tests.
         */
        internal fun msUntilNextMonthStart(now: ZonedDateTime = ZonedDateTime.now()): Long {
            val next = now.toLocalDate().plusMonths(1).withDayOfMonth(1).atStartOfDay(now.zone)
            return Duration.between(now, next).toMillis()
        }

        /** Applies the positive-only scheduling jitter [deltaMs] and clamps the result to non-negative as a safety net. */
        internal fun applyJitter(delayMs: Long, deltaMs: Long): Long =
            (delayMs + deltaMs).coerceAtLeast(0)
    }

    override suspend fun doWork(): Result {
        return try {
            // Computed at execution, not scheduling: the finished month is always
            // "now minus one" when the work fires (a WorkManager delay is never exact).
            val finished = LocalDate.now().minusMonths(1)
            val name = RewindPlaylists.monthlyName(finished.year, finished.monthValue)
            if (!isMonthlyPlaylistEnabled(applicationContext)) {
                // Re-read the toggle at execution time: a flip made after the work was
                // enqueued must skip the creation. The self-reschedule below still runs
                // and, with the gate off, cancels the pending work: the cadence resumes
                // at the next app startup while the gate is on.
                Timber.tag(TAG).i("Rewind monthly playlist disabled in settings: skipping $name")
                Result.success()
            } else if (Database.playlistTable.findByName(name).first() != null) {
                // Frozen "already exists" behavior: a total skip (no write, no
                // notification). A playlist deleted by the user is never recreated by
                // this worker — it runs once per month, only the deck pill brings it back.
                Timber.tag(TAG).i("Rewind playlist \"$name\" already exists: total skip (no write, no notification)")
                Result.success()
            } else {
                val window = RewindPlaylists.windowFor(name)
                if (window == null) {
                    Timber.tag(TAG).w("Could not derive the listening window of \"$name\": skipping")
                    Result.success()
                } else {
                    val count = generateRewindPlaylist(name, window.first, window.second, GenerateMode.CreateIfMissing)
                    if (count > 0) {
                        postCreatedNotification(finished.month.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                    }
                    Result.success()
                }
            }
        } finally {
            // Self-perpetuating cadence: always schedule the next month, success or failure
            rescheduleHook?.invoke(applicationContext) ?: schedule(applicationContext)
        }
    }

    private fun postCreatedNotification(finishedMonthName: String) {
        if (!isMonthlyPlaylistNotificationEnabled(applicationContext)) {
            Timber.tag(TAG).i("Playlist notification toggle off: skipping the notification")
            return
        }
        if (
            Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // The runtime grant is the frozen gate for the notification only: the playlist
            // was created regardless (the creation is the purpose, the notification is
            // optional — frozen task WORKER_NOTIF_DENIED).
            Timber.tag(TAG).i("POST_NOTIFICATIONS denied: \"$finishedMonthName\" playlist created without a notification")
            return
        }

        // The content intent opens the app without extras: the user navigates to the
        // Playlists tab (frozen spec 2 — the playlist notification is not the deck).
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        NotificationManagerCompat.from(applicationContext).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, RewindReminderWorker.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(applicationContext.getString(R.string.rw_playlist_notif_title, finishedMonthName))
                .setContentText(applicationContext.getString(R.string.rw_playlist_notif_body))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
        )
        Timber.tag(TAG).i("Posted the \"$finishedMonthName\" rewind playlist notification")
    }
}
