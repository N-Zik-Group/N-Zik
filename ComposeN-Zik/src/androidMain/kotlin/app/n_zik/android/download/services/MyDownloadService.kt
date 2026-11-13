package app.n_zik.android.download.services

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.awaitLibraryReady
import app.n_zik.android.core.backup.LibraryStartupException
import app.n_zik.android.core.backup.isLibraryReady
import app.n_zik.android.core.notifications.channelId
import app.n_zik.android.core.notifications.ensureStartupNotificationChannel
import app.n_zik.android.core.profiles.LegacyMediaAccess
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.playback.services.StreamingCacheReaders
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.download.utils.MyDownloadHelper.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import app.n_zik.android.download.utils.MyDownloadHelper.batchTotal
import app.n_zik.android.download.utils.MyDownloadHelper.batchCompleted
import app.n_zik.android.download.utils.MyDownloadHelper.incrementBatchCompleted
import app.n_zik.android.download.utils.MyDownloadHelper.resetBatch
import app.n_zik.android.download.utils.downloadDisplayName
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.playback.exceptions.ExplicitContentException
import app.n_zik.android.playback.exceptions.LoginRequiredException
import app.n_zik.android.playback.exceptions.NoInternetException
import app.n_zik.android.playback.exceptions.PlayableFormatNonSupported
import app.n_zik.android.playback.exceptions.PlayableFormatNotFoundException
import app.n_zik.android.playback.exceptions.StreamResolutionOfflineException
import app.n_zik.android.playback.exceptions.TimeoutException
import app.n_zik.android.playback.exceptions.UnknownException
import app.n_zik.android.playback.exceptions.UnplayableException
import app.n_zik.android.playback.exceptions.VideoIdMismatchException
import app.n_zik.android.utils.coroutines.NzikDispatchers
import androidx.media3.datasource.HttpDataSource
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

private const val JOB_ID = 8888
const val FOREGROUND_NOTIFICATION_ID = 8989

@UnstableApi
class MyDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    // The channel this service actually posts on (per-profile — both notification builders
    // below use it): DownloadService.onCreate (re)creates exactly this channel at start, so
    // the base ID here would resurrect the base `download_channel` under every profile.
    // (`this` is not usable in a super-constructor call — appContext() is already set by
    // the time any service can be constructed.)
    channelId(DOWNLOAD_NOTIFICATION_CHANNEL_ID, getActiveProfile(appContext())),
    R.string.downloads_title, 0
) {

    companion object {
        const val ACTION_CANCEL_ONGOING = "app.n_zik.android.download.action.CANCEL_ONGOING"
        // Media3's scheduler uses this private action without setting KEY_FOREGROUND.
        private const val SCHEDULED_RESTART = "androidx.media3.exoplayer.downloadService.action.RESTART"
    }

    private data class PendingStart(val intent: Intent?, val flags: Int, val id: Int)

    private val pendingStarts = ArrayDeque<PendingStart>()
    private var startupJob: Job? = null
    private var downloadManagerStarted = false
    private var waitingForeground = false
    private var commands: ProfileDownloadCommands? = null
    private var legacyStorageProtected = false
    private var dispatchedStart = false

    override fun onCreate() {
        if (isLibraryReady()) {
            initializeDownloadManager()
            return
        }
        startupJob = NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            try {
                awaitLibraryReady()
            } catch (error: LibraryStartupException) {
                Timber.tag("MyDownloadService").e(error, "Library is unavailable for downloads")
                stopSelf()
                return@launch
            }
            initializeDownloadManager()
            while (pendingStarts.isNotEmpty()) {
                val pending = pendingStarts.removeFirst()
                onStartCommand(pending.intent, pending.flags, pending.id)
            }
        }
    }

    private fun initializeDownloadManager() {
        commands = ProfileDownloadCommands.capture(this)
        legacyStorageProtected = LegacyMediaAccess.isProtected(this, getActiveProfile(this), ProfileDataItem.DOWNLOADS)
        if (legacyStorageProtected) {
            stopSelf()
            return
        }
        super.onCreate()
        downloadManagerStarted = true
        if (commands?.isPaused(this) == true) MyDownloadHelper.getDownloadManager(this).pauseDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (legacyStorageProtected) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (intent?.data == null && (intent?.action == ACTION_ADD_DOWNLOAD || intent?.action == ACTION_REMOVE_DOWNLOAD)) {
            // Unowned starts, including previously redelivered ones, cannot be attributed.
            return rejectStart(startId)
        }
        if (!downloadManagerStarted) {
            if (!waitingForeground &&
                (intent?.getBooleanExtra(KEY_FOREGROUND, false) == true || intent?.action == SCHEDULED_RESTART)
            ) {
                val channel = ensureStartupNotificationChannel(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID, getActiveProfile(this))
                val strings = ContextCompat.getContextForLanguage(this).resources
                startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    NotificationCompat.Builder(this, channel)
                        .setSmallIcon(R.drawable.ic_launcher_monochrome)
                        .setContentTitle(strings.getString(R.string.downloads_title))
                        .setContentText(strings.getString(R.string.backup_startup_preparing_library))
                        .setProgress(0, 0, true)
                        .setOngoing(true)
                        .build(),
                )
                waitingForeground = true
            }
            pendingStarts.addLast(PendingStart(intent, flags, startId))
            return if (intent == null) START_STICKY else START_REDELIVER_INTENT
        }
        when (intent?.action) {
            ACTION_CANCEL_ONGOING, ACTION_PAUSE_DOWNLOADS, ACTION_RESUME_DOWNLOADS, ACTION_REMOVE_ALL_DOWNLOADS,
            ACTION_ADD_DOWNLOAD, ACTION_REMOVE_DOWNLOAD, ACTION_SET_STOP_REASON, ACTION_SET_REQUIREMENTS -> {
                val target = commands
                if (target == null || !target.matches(intent) || target != ProfileDownloadCommands.capture(this)) {
                    return rejectStart(startId)
                }
                if (intent.intersectsCleanup()) return rejectStart(startId)
                when (intent.action) {
                    ACTION_CANCEL_ONGOING -> {
                        cancelOngoingDownloads()
                        return START_STICKY
                    }
                    ACTION_PAUSE_DOWNLOADS -> target.setPaused(this, true)
                    ACTION_RESUME_DOWNLOADS -> target.setPaused(this, false)
                }
            }
        }
        dispatchedStart = true
        return super.onStartCommand(intent, flags, startId)
    }

    private fun rejectStart(startId: Int): Int {
        if (!dispatchedStart) stopSelfResult(startId)
        return START_NOT_STICKY
    }

    private fun Intent.intersectsCleanup(): Boolean {
        val cache = MyDownloadHelper.boundDownloadCache() ?: return false
        return when (action) {
            ACTION_ADD_DOWNLOAD -> {
                @Suppress("DEPRECATION")
                val request = getParcelableExtra<DownloadRequest>(KEY_DOWNLOAD_REQUEST)
                request != null && (
                    StreamingCacheReaders.isReserved(cache, request.id) ||
                        StreamingCacheReaders.isReserved(cache, request.customCacheKey ?: request.id)
                    )
            }
            ACTION_REMOVE_DOWNLOAD, ACTION_SET_STOP_REASON ->
                StreamingCacheReaders.isReserved(cache, getStringExtra(KEY_CONTENT_ID))
            ACTION_CANCEL_ONGOING, ACTION_REMOVE_ALL_DOWNLOADS, ACTION_SET_REQUIREMENTS ->
                StreamingCacheReaders.isReserved(cache, null)
            else -> false
        }
    }

    internal fun cancelOngoingDownloads() {
        val downloadManager = MyDownloadHelper.getDownloadManager(this)
        downloadManager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                val state = cursor.download.state
                if (state == Download.STATE_DOWNLOADING || state == Download.STATE_QUEUED || state == Download.STATE_RESTARTING || state == Download.STATE_STOPPED) {
                    downloadManager.removeDownload(cursor.download.request.id)
                }
            }
        }
    }

    override fun onDestroy() {
        startupJob?.cancel()
        pendingStarts.clear()
        // DownloadService dereferences its manager helper, which is only attached by its onCreate.
        if (downloadManagerStarted) super.onDestroy()
    }

    override fun getDownloadManager(): DownloadManager {

        // This will only happen once, because getDownloadManager is guaranteed to be called only once
        // in the life cycle of the process.
        val downloadManager: DownloadManager = MyDownloadHelper.getDownloadManager(this)
        downloadManager.addListener(
            TerminalStateNotificationHelper(
                applicationContext,
                FOREGROUND_NOTIFICATION_ID + 1
            )
        )
        return downloadManager
    }

    override fun getScheduler(): PlatformScheduler? {
        return if(Util.SDK_INT >= 21) PlatformScheduler(this, JOB_ID) else null
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return DownloadProgressNotification(applicationContext).build(
            downloads,
            MyDownloadHelper.getDownloadManager(applicationContext).downloadsPaused,
            batchTotal,
            batchCompleted
        )
    }

    /**
     * Creates and displays notifications for downloads when they complete or fail.
     *
     * This helper will outlive the lifespan of a single instance of [MyDownloadService].
     * It is static to avoid leaking the first [MyDownloadService] instance.
     */
    internal class TerminalStateNotificationHelper(
        context: Context,
        private val notificationId: Int
    ) : DownloadManager.Listener {
        private val context = context.applicationContext
        private val progressNotification = DownloadProgressNotification(this.context)
        private var completedCount = 0
        private var failedCount = 0
        private var lastName = ""

        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            if (download.state == Download.STATE_COMPLETED) {
                completedCount++
                incrementBatchCompleted()
                WaveformExtractor.deleteWaveform(context, download.request.id)
                lastName = Util.fromUtf8Bytes(download.request.data)
            } else if (download.state == Download.STATE_REMOVING) {
                WaveformExtractor.deleteWaveform(context, download.request.id)
            } else if (download.state == Download.STATE_FAILED) {
                failedCount++
                lastName = Util.fromUtf8Bytes(download.request.data)

                val currentCause = finalException
                var rootCause: Throwable? = currentCause
                var httpCode: Int? = null
                while (rootCause != null) {
                    if (rootCause is HttpDataSource.InvalidResponseCodeException) {
                        httpCode = rootCause.responseCode
                    }
                    rootCause = rootCause.cause
                }

                val specificCause = findSpecificDownloadCause(currentCause)

                if (specificCause is ExplicitContentException) {
                    Toaster.w(R.string.parental_control_is_enabled)
                } else {
                    Toaster.e(downloadFailureToastRes(httpCode, specificCause))
                }
            }
        }

        override fun onIdle(downloadManager: DownloadManager) {
            val downloads = downloadManager.currentDownloads
            val hasPaused = downloadManager.downloadsPaused
            val hadCompletedOrFailed = (completedCount > 0 || failedCount > 0)

            if (hadCompletedOrFailed) {
                val strings = ContextCompat.getContextForLanguage(context).resources
                val title = if (failedCount > 0) {
                    strings.getString(R.string.downloads_completed_with_failed, completedCount, failedCount)
                } else {
                    strings.getString(R.string.downloads_completed, completedCount)
                }

                val notification = NotificationCompat.Builder(context, channelId(DOWNLOAD_NOTIFICATION_CHANNEL_ID, getActiveProfile(context)))
                    .setSmallIcon(if (failedCount == 0) R.drawable.downloaded else R.drawable.alert_circle_not_filled)
                    .setContentTitle(strings.getString(R.string.downloads_title))
                    .setContentText(title)
                    .setSubText(downloadDisplayName(lastName))
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .build()

                NotificationUtil.setNotification(context, notificationId, notification)

                completedCount = 0
                failedCount = 0
                lastName = ""
            }

            if (hasPaused && downloads.isNotEmpty()) {
                val notification = progressNotification.build(downloads, true, batchTotal, batchCompleted)
                NotificationUtil.setNotification(context, FOREGROUND_NOTIFICATION_ID + 2, notification)
            } else {
                androidx.core.app.NotificationManagerCompat.from(context).cancel(FOREGROUND_NOTIFICATION_ID + 2)
                androidx.core.app.NotificationManagerCompat.from(context).cancel(FOREGROUND_NOTIFICATION_ID)

                if (downloads.isEmpty()) resetBatch()
            }
        }
    }

}

/**
 * S5/A5b (offline gate, 2026-10-22): the first "known" exception in [finalException]'s
 * cause chain at any depth that determines a failed download's toast — incl. the offline
 * fast-fail marker and the raw network failures (socket timeouts/resets) of A5b.
 */
internal fun findSpecificDownloadCause(finalException: Exception?): Throwable? =
    generateSequence<Throwable>(finalException) { it.cause }.firstOrNull {
        it is ExplicitContentException ||
        it is UnresolvedAddressException ||
        it is UnknownHostException ||
        it is PlayableFormatNotFoundException ||
        it is UnplayableException ||
        it is LoginRequiredException ||
        it is VideoIdMismatchException ||
        it is PlayableFormatNonSupported ||
        it is NoInternetException ||
        it is StreamResolutionOfflineException ||
        it is TimeoutException ||
        it is UnknownException ||
        it is InterruptedIOException ||
        it is SocketException
    }

/** S5/A5b (offline gate, 2026-10-22): the toast resource for a failed download — network-down causes (offline fast-fail marker, raw socket timeouts/resets, DNS) map to their specific string; the fallback is download-domain (`download_failed`, P11 review 2026-10-23). */
internal fun downloadFailureToastRes(httpCode: Int?, specificCause: Throwable?): Int =
    if (httpCode == 403) R.string.downloads_error_server_restriction
    else when (specificCause) {
        is UnresolvedAddressException, is UnknownHostException -> R.string.downloads_error_network
        is PlayableFormatNotFoundException -> R.string.player_error_no_playable_format
        is UnplayableException -> R.string.player_error_source_deleted
        is LoginRequiredException -> R.string.downloads_error_server_restriction
        is VideoIdMismatchException -> R.string.player_error_video_id_mismatch
        is PlayableFormatNonSupported -> R.string.player_error_unsupported_format
        is NoInternetException, is StreamResolutionOfflineException -> R.string.common_error_no_internet
        is TimeoutException -> R.string.common_error_connection_timeout
        is InterruptedIOException, is SocketException -> R.string.downloads_error_network
        // P11 (review, 2026-10-23): download-domain fallback — the playback-domain
        // unknown-error string was misleading for a failed download.
        else -> R.string.downloads_failed
    }
