package app.n_zik.android.download.services

import app.n_zik.android.download.services.*
import app.n_zik.android.download.utils.*

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import app.n_zik.android.R
import app.n_zik.android.download.utils.MyDownloadHelper.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import app.n_zik.android.download.utils.MyDownloadHelper.batchTotal
import app.n_zik.android.download.utils.MyDownloadHelper.batchCompleted
import app.n_zik.android.download.utils.MyDownloadHelper.incrementBatchCompleted
import app.n_zik.android.download.utils.MyDownloadHelper.resetBatch
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.playback.exceptions.ExplicitContentException
import app.n_zik.android.playback.exceptions.LoginRequiredException
import app.n_zik.android.playback.exceptions.NoInternetException
import app.n_zik.android.playback.exceptions.PlayableFormatNonSupported
import app.n_zik.android.playback.exceptions.PlayableFormatNotFoundException
import app.n_zik.android.playback.exceptions.TimeoutException
import app.n_zik.android.playback.exceptions.UnknownException
import app.n_zik.android.playback.exceptions.UnplayableException
import app.n_zik.android.playback.exceptions.VideoIdMismatchException
import androidx.media3.datasource.HttpDataSource
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

private const val JOB_ID = 8888
const val FOREGROUND_NOTIFICATION_ID = 8989

@UnstableApi
class MyDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.download, 0
) {

    companion object {
        const val ACTION_CANCEL_ONGOING = "app.n_zik.android.download.action.CANCEL_ONGOING"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_ONGOING) {
            val downloadManager = MyDownloadHelper.getDownloadManager(this)
            val cursor = downloadManager.downloadIndex.getDownloads()
            while (cursor.moveToNext()) {
                val state = cursor.download.state
                if (state == Download.STATE_DOWNLOADING || state == Download.STATE_QUEUED || state == Download.STATE_RESTARTING || state == Download.STATE_STOPPED) {
                    DownloadService.sendRemoveDownload(this, MyDownloadService::class.java, cursor.download.request.id, false)
                }
            }
            return START_STICKY
        } else if (intent?.action == DownloadService.ACTION_PAUSE_DOWNLOADS) {
            app.n_zik.android.appContext().getSharedPreferences("download_prefs", Context.MODE_PRIVATE).edit().putBoolean("downloads_paused_state", true).apply()
        } else if (intent?.action == DownloadService.ACTION_RESUME_DOWNLOADS) {
            app.n_zik.android.appContext().getSharedPreferences("download_prefs", Context.MODE_PRIVATE).edit().putBoolean("downloads_paused_state", false).apply()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun getDownloadManager(): DownloadManager {

        // This will only happen once, because getDownloadManager is guaranteed to be called only once
        // in the life cycle of the process.
        val downloadManager: DownloadManager = MyDownloadHelper.getDownloadManager(this)
        val downloadNotificationHelper: DownloadNotificationHelper =
            MyDownloadHelper.getDownloadNotificationHelper(this)
        downloadManager.addListener(
            TerminalStateNotificationHelper(
                this,
                downloadNotificationHelper,
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
        val total = batchTotal
        val completed = batchCompleted
        val message = if (total > 0) {
            getString(R.string.download_progress, completed, total)
        } else {
            getString(R.string.download_in_progress, downloads.size)
        }

        val activeDownload = downloads.firstOrNull { it.state == Download.STATE_DOWNLOADING } ?: downloads.firstOrNull()
        val currentDownloadName = activeDownload?.request?.data?.let { Util.fromUtf8Bytes(it) }

        val isPaused = MyDownloadHelper.getDownloadManager(this).downloadsPaused

        if (!isPaused) {
            androidx.core.app.NotificationManagerCompat.from(this).cancel(FOREGROUND_NOTIFICATION_ID + 2)
        }

        val pauseResumeAction = if (isPaused) {
            val intent = Intent(this, MyDownloadService::class.java).setAction(DownloadService.ACTION_RESUME_DOWNLOADS)
            val pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(R.drawable.play, getString(R.string.snake_resume), pendingIntent)
        } else {
            val intent = Intent(this, MyDownloadService::class.java).setAction(DownloadService.ACTION_PAUSE_DOWNLOADS)
            val pendingIntent = PendingIntent.getService(this, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(R.drawable.pause, getString(R.string.notification_pause), pendingIntent)
        }

        val cancelOngoingIntent = Intent(this, MyDownloadService::class.java).setAction(ACTION_CANCEL_ONGOING)
        val cancelOngoingPendingIntent = PendingIntent.getService(this, 1, cancelOngoingIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancelAction = NotificationCompat.Action(R.drawable.close, getString(R.string.cancel), cancelOngoingPendingIntent)

        val deleteAllIntent = Intent(this, MyDownloadService::class.java).setAction(DownloadService.ACTION_REMOVE_ALL_DOWNLOADS)
        val deleteAllPendingIntent = PendingIntent.getService(this, 2, deleteAllIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val deleteAllAction = NotificationCompat.Action(R.drawable.trash, getString(R.string.delete), deleteAllPendingIntent)

        return NotificationCompat.Builder(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.download_progress)
            .setContentTitle(getString(R.string.download))
            .setContentText(currentDownloadName ?: message)
            .setSubText(message)
            .setProgress(total, completed, total == 0)
            .setOngoing(!isPaused)
            .setShowWhen(false)
            .addAction(pauseResumeAction)
            .addAction(cancelAction)
            .addAction(deleteAllAction)
            .build()    }

    /**
     * Creates and displays notifications for downloads when they complete or fail.
     *
     *     * This helper will outlive the lifespan of a single instance of [MyDownloadService].
     * It is static to avoid leaking the first [MyDownloadService] instance.
     */
    private class TerminalStateNotificationHelper(
        private val context: Context,
        private val notificationHelper: DownloadNotificationHelper,
        private val notificationId: Int
    ) : DownloadManager.Listener {
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
                
                val specificCause = generateSequence<Throwable>(currentCause) { it.cause }.firstOrNull { 
                    it is ExplicitContentException || 
                    it is UnresolvedAddressException || 
                    it is UnknownHostException || 
                    it is PlayableFormatNotFoundException || 
                    it is UnplayableException || 
                    it is LoginRequiredException || 
                    it is VideoIdMismatchException || 
                    it is PlayableFormatNonSupported || 
                    it is NoInternetException || 
                    it is TimeoutException || 
                    it is UnknownException
                }

                if (specificCause is ExplicitContentException) {
                    Toaster.w(R.string.parental_control_is_enabled)
                } else {
                    val errorMessage = if (httpCode == 403) {
                        context.getString(R.string.error_this_song_cannot_be_played_due_to_server_restrictions)
                    } else when (specificCause) {
                        is UnresolvedAddressException, is UnknownHostException -> context.getString(R.string.error_a_network_error_has_occurred)
                        is PlayableFormatNotFoundException -> context.getString(R.string.error_couldn_t_find_a_playable_audio_format)
                        is UnplayableException -> context.getString(R.string.error_the_original_video_source_of_this_song_has_been_deleted)
                        is LoginRequiredException -> context.getString(R.string.error_this_song_cannot_be_played_due_to_server_restrictions)
                        is VideoIdMismatchException -> context.getString(R.string.error_the_returned_video_id_doesn_t_match_the_requested_one)
                        is PlayableFormatNonSupported -> context.getString(R.string.error_file_unsupported_format)
                        is NoInternetException -> context.getString(R.string.error_no_internet)
                        is TimeoutException -> context.getString(R.string.error_timeout)
                        else -> context.getString(R.string.error_an_unknown_playback_error_has_occurred)
                    }
                    Toaster.e(errorMessage)
                }
            }
        }

        override fun onIdle(downloadManager: DownloadManager) {
            val downloads = downloadManager.currentDownloads
            val hasPaused = downloadManager.downloadsPaused
            val hadCompletedOrFailed = (completedCount > 0 || failedCount > 0)

            if (hadCompletedOrFailed) {
                val title = if (failedCount > 0) {
                    context.getString(R.string.download_completed_with_failed, completedCount, failedCount)
                } else {
                    context.getString(R.string.download_completed, completedCount)  
                }

                val notification = NotificationCompat.Builder(context, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(if (failedCount == 0) R.drawable.downloaded else R.drawable.alert_circle_not_filled)
                    .setContentTitle(context.getString(R.string.download))
                    .setContentText(title)
                    .setSubText(lastName)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .build()

                NotificationUtil.setNotification(context, notificationId, notification)

                completedCount = 0
                failedCount = 0
                lastName = ""
            }

            if (hasPaused && context is MyDownloadService && downloads.isNotEmpty()) {
                val notification = context.getForegroundNotification(downloads.toMutableList(), 0)
                NotificationUtil.setNotification(context, FOREGROUND_NOTIFICATION_ID + 2, notification)
            } else {
                androidx.core.app.NotificationManagerCompat.from(context).cancel(FOREGROUND_NOTIFICATION_ID + 2)
                androidx.core.app.NotificationManagerCompat.from(context).cancel(FOREGROUND_NOTIFICATION_ID)
                
                if (downloads.isEmpty()) {
                    app.n_zik.android.appContext().getSharedPreferences("download_prefs", Context.MODE_PRIVATE).edit().putBoolean("downloads_paused_state", false).apply()
                    resetBatch()
                }
            }
        }
    }

}
