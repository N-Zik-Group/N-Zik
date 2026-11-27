package app.n_zik.android.core.rewind

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.concurrent.TimeUnit
import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import timber.log.Timber
import app.n_zik.android.MainActivity
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.RewindReminderWorker
import app.n_zik.android.core.backup.awaitLibraryReadyForWork
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.notifications.channelId
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.privacy.JournalPermission
import app.n_zik.android.privacy.PermissionEventKind
import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.PermissionUsageJournal
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.getActiveProfile

private const val JITTER_MS = 10L * 60 * 1000

internal fun addRewindScheduleJitter(delayMs: Long, deltaMs: Long): Long =
    (delayMs + deltaMs).coerceAtLeast(0)

internal enum class RewindPlaylistSchedule(
    val tag: String,
    private val legacyName: String,
    private val workerClass: Class<out CoroutineWorker>,
    private val enabledKey: String,
    private val notificationKey: String,
    private val notificationId: Int,
    private val pendingIntentCode: Int,
) {
    Monthly(
        "RewindMonthlyPlaylist",
        "RewindMonthlyPlaylistWorker",
        RewindMonthlyPlaylistWorker::class.java,
        DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED,
        DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_NOTIF_ENABLED,
        0x7265706C,
        3,
    ),
    Yearly(
        "RewindYearlyPlaylist",
        "RewindYearlyPlaylistWorker",
        RewindYearlyPlaylistWorker::class.java,
        DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED,
        DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_NOTIF_ENABLED,
        0x72657079,
        4,
    );

    fun isEnabled(context: Context): Boolean =
        DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, true) &&
            DataStoreUtils.getBoolean(context, enabledKey, true)

    fun isNotificationEnabled(context: Context): Boolean =
        isEnabled(context) && DataStoreUtils.getBoolean(context, notificationKey, true)

    fun schedule(context: Context, owner: ProfileStorageIdentity? = ProfileWorkOwner.capture(context)) {
        if (owner == null || !ProfileWorkOwner.isBound(context, owner)) return
        val work = WorkManager.getInstance(context)
        val workName = ProfileWorkOwner.name(legacyName, owner)
        work.cancelUniqueWork(legacyName)
        if (!isEnabled(context)) {
            work.cancelUniqueWork(workName)
            Timber.tag(tag).i("Rewind playlist disabled: canceling pending work")
            return
        }
        val request = OneTimeWorkRequest.Builder(workerClass)
            .setInputData(ProfileWorkOwner.input(owner))
            .setInitialDelay(
                addRewindScheduleJitter(delayUntilNextRun(ZonedDateTime.now()), (0L..JITTER_MS).random()),
                TimeUnit.MILLISECONDS,
            )
            .build()
        work.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
    }

    fun delayUntilNextRun(now: ZonedDateTime): Long {
        val today = now.toLocalDate()
        val next = when (this) {
            Monthly -> today.plusMonths(1).withDayOfMonth(1)
            Yearly -> today.plusYears(1).withDayOfYear(1)
        }
        return Duration.between(now, next.atStartOfDay(now.zone)).toMillis()
    }

    fun finishedPeriod(now: LocalDate): LocalDate = when (this) {
        Monthly -> now.minusMonths(1)
        Yearly -> now.minusYears(1)
    }

    fun playlistName(finished: LocalDate): String = when (this) {
        Monthly -> RewindPlaylists.monthlyName(finished.year, finished.monthValue)
        Yearly -> RewindPlaylists.yearlyName(finished.year)
    }

    fun notifyCreated(context: Context, finished: LocalDate, playlistId: Long?) {
        if (!isNotificationEnabled(context)) {
            Timber.tag(tag).i("Playlist notification toggle off: skipping the notification")
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.tag(tag).i("POST_NOTIFICATIONS denied: playlist created without a notification")
            return
        }

        val resources = ContextCompat.getContextForLanguage(context).resources
        val label = when (this) {
            Monthly -> finished.month.getDisplayName(TextStyle.FULL, resources.configuration.locales[0])
            Yearly -> finished.year.toString()
        }
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        playlistId?.let { intent.putExtra(RewindPlaylists.EXTRA_REWIND_PLAYLIST_ID, it) }
        val pending = PendingIntent.getActivity(
            context, pendingIntentCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(context).notify(
            notificationId,
            NotificationCompat.Builder(context, channelId(RewindReminderWorker.CHANNEL_ID, getActiveProfile(context)))
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(resources.getString(R.string.rewind_playlist_notification_title, label))
                .setContentText(resources.getString(R.string.rewind_playlist_notification_description))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build(),
        )
        if (this == Yearly) {
            PermissionUsageJournal.record(
                context, PermissionFeature.REWIND, JournalPermission.NOTIFICATIONS, PermissionEventKind.USED,
            )
        }
        Timber.tag(tag).i("Posted the \"$label\" rewind playlist notification")
    }
}

internal abstract class ScheduledRewindPlaylistWorker(
    context: Context,
    params: WorkerParameters,
    private val cadence: RewindPlaylistSchedule,
) : CoroutineWorker(context, params) {
    internal var rescheduleHook: ((Context) -> Unit)? = null

    final override suspend fun doWork(): Result {
        val owner = ProfileWorkOwner.read(inputData) ?: return Result.success()
        val context = applicationContext
        fun stillBound() = ProfileWorkOwner.isBound(context, owner)
        if (!context.awaitLibraryReadyForWork()) return Result.retry()
        if (!ProfileWorkOwner.exists(context, owner)) return Result.success()
        if (!stillBound()) return Result.retry()

        try {
            val finished = cadence.finishedPeriod(LocalDate.now())
            val name = cadence.playlistName(finished)
            if (!cadence.isEnabled(context)) {
                Timber.tag(cadence.tag).i("Rewind playlist disabled in settings: skipping $name")
                return Result.success()
            }
            if (Database.playlistTable.findByName(name).first() != null) {
                Timber.tag(cadence.tag).i("Rewind playlist already exists: skipping $name")
                return Result.success()
            }
            val window = RewindPlaylists.windowFor(name)
            if (window == null) {
                Timber.tag(cadence.tag).w("Could not derive the listening window of $name")
                return Result.success()
            }
            val count = generateRewindPlaylist(name, window.first, window.second, GenerateMode.CreateIfMissing, ::stillBound)
            if (count > 0 && stillBound()) {
                val playlistId = Database.playlistTable.findByBrowseId(RewindPlaylists.generatedSourceId(name)).first()?.id
                if (stillBound()) cadence.notifyCreated(context, finished, playlistId)
            }
            return Result.success()
        } finally {
            if (stillBound()) {
                rescheduleHook?.invoke(context) ?: cadence.schedule(context, owner)
            }
        }
    }
}
