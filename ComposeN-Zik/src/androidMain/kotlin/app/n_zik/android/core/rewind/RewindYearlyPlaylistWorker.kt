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
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Yearly rewind playlist worker (spec 2): on the 1st of January, creates
 * `rewind-yearly:YYYY` from the finished year's most-played songs — if it does not
 * exist yet — and posts a notification when the notification toggle allows it.
 *
 * Same pattern and same deliberate differences as [RewindMonthlyPlaylistWorker]: the
 * dedicated `KEY_REWIND_YEARLY_PLAYLIST_ENABLED` creation gate (decoupled from the
 * master switch and the deck toggles) and creation-on-despite-denied-POST_NOTIFICATIONS
 * (the notification is the optional part). Shares the application-created "rewind"
 * notification channel (no new channel).
 */
internal class RewindYearlyPlaylistWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    /**
     * Test seam: unit tests substitute this hook for the self-reschedule, which needs a live
     * WorkManager (unreachable from JVM tests). Production code never sets it.
     */
    internal var rescheduleHook: ((Context) -> Unit)? = null

    companion object {
        private const val TAG = "RewindYearlyPlaylist"
        private const val WORK_NAME = "RewindYearlyPlaylistWorker"
        private const val NOTIFICATION_ID = 0x72657079 // 'repy', stable across runs (autoCancel)

        // Positive-only jitter so installs do not all hit WorkManager at the same
        // 1st-of-January instant: it can only push the firing later (up to +10 min), never
        // before the 1st — an early fire would compute the finished year one year too far back
        private const val JITTER_MS = 10L * 60 * 1000

        /**
         * The yearly playlist creation gate (spec 1 contract): only the dedicated
         * creation toggle. Internal so unit tests can verify the decision without a
         * WorkManager.
         */
        internal fun isYearlyPlaylistEnabled(context: Context): Boolean =
            DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, true)

        /**
         * The yearly playlist notification gate: posts only while both the notification
         * toggle and the creation toggle are on — a type off implies its notification
         * off, even if the notification toggle is on.
         */
        internal fun isYearlyPlaylistNotificationEnabled(context: Context): Boolean =
            isYearlyPlaylistEnabled(context) &&
            DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_NOTIF_ENABLED, true)

        /**
         * Schedules the first creation for the next 1st of January. KEEP avoids piling up
         * concurrent runs when the app is started several times before the work fires.
         * While the feature is off in the settings, any pending work is canceled instead so
         * a toggle takes effect without an app restart.
         */
        fun schedule(context: Context) {
            if (!isYearlyPlaylistEnabled(context)) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                Timber.tag(TAG).i("Rewind yearly playlist disabled in settings: canceling pending work")
                return
            }
            val request = OneTimeWorkRequestBuilder<RewindYearlyPlaylistWorker>()
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
         * always strictly in the future, even on the 1st of January itself. Injectable
         * for tests.
         */
        internal fun msUntilNextYearStart(now: ZonedDateTime = ZonedDateTime.now()): Long {
            val next = now.toLocalDate().plusYears(1).withMonth(1).withDayOfMonth(1).atStartOfDay(now.zone)
            return Duration.between(now, next).toMillis()
        }

        /** Applies the positive-only scheduling jitter [deltaMs] and clamps the result to non-negative as a safety net. */
        internal fun applyJitter(delayMs: Long, deltaMs: Long): Long =
            (delayMs + deltaMs).coerceAtLeast(0)
    }

    override suspend fun doWork(): Result {
        return try {
            // Computed at execution, not scheduling: the work fires on the 1st of January
            // of year Y, so the finished year is Y-1 — the same "now minus one" approach
            // the monthly worker uses for its finished month.
            val finishedYear = LocalDate.now().year - 1
            val name = RewindPlaylists.yearlyName(finishedYear)
            if (!isYearlyPlaylistEnabled(applicationContext)) {
                // Re-read the toggle at execution time: a flip made after the work was
                // enqueued must skip the creation. The self-reschedule below still runs
                // and, with the gate off, cancels the pending work: the cadence resumes
                // at the next app startup while the gate is on.
                Timber.tag(TAG).i("Rewind yearly playlist disabled in settings: skipping $name")
                Result.success()
            } else if (Database.playlistTable.findByName(name).first() != null) {
                // Frozen "already exists" behavior: a total skip (no write, no
                // notification). A playlist deleted by the user is never recreated by
                // this worker — it runs once per year, only the deck pill brings it back.
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
                        postCreatedNotification(finishedYear.toString())
                    }
                    Result.success()
                }
            }
        } finally {
            // Self-perpetuating cadence: always schedule the next year, success or failure
            rescheduleHook?.invoke(applicationContext) ?: schedule(applicationContext)
        }
    }

    private fun postCreatedNotification(finishedYear: String) {
        if (!isYearlyPlaylistNotificationEnabled(applicationContext)) {
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
            Timber.tag(TAG).i("POST_NOTIFICATIONS denied: \"$finishedYear\" playlist created without a notification")
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
                .setContentTitle(applicationContext.getString(R.string.rw_playlist_notif_title, finishedYear))
                .setContentText(applicationContext.getString(R.string.rw_playlist_notif_body))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
        )
        Timber.tag(TAG).i("Posted the \"$finishedYear\" rewind playlist notification")
    }
}
