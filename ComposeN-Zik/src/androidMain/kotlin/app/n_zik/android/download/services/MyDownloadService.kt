package app.n_zik.android.download.services

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.core.notifications.channelId
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.download.utils.MyDownloadHelper.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import app.n_zik.android.download.utils.MyDownloadHelper.batchTotal
import app.n_zik.android.download.utils.MyDownloadHelper.batchCompleted
import app.n_zik.android.download.utils.MyDownloadHelper.incrementBatchCompleted
import app.n_zik.android.download.utils.MyDownloadHelper.resetBatch
import app.kreate.android.me.knighthat.utils.Toaster
import app.it.fast4x.rimusic.utils.getActiveProfile
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
import androidx.media3.datasource.HttpDataSource
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

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
    R.string.download, 0
) {

    companion object {
        const val ACTION_CANCEL_ONGOING = "app.n_zik.android.download.action.CANCEL_ONGOING"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_ONGOING) {
            val downloadManager = MyDownloadHelper.getDownloadManager(this)
            downloadManager.downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) {
                    val state = cursor.download.state
                    if (state == Download.STATE_DOWNLOADING || state == Download.STATE_QUEUED || state == Download.STATE_RESTARTING || state == Download.STATE_STOPPED) {
                        DownloadService.sendRemoveDownload(this, MyDownloadService::class.java, cursor.download.request.id, false)
                    }
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
                val title = if (failedCount > 0) {
                    context.getString(R.string.download_completed_with_failed, completedCount, failedCount)
                } else {
                    context.getString(R.string.download_completed, completedCount)
                }

                val notification = NotificationCompat.Builder(context, channelId(DOWNLOAD_NOTIFICATION_CHANNEL_ID, getActiveProfile(context)))
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

            if (hasPaused && downloads.isNotEmpty()) {
                val notification = progressNotification.build(downloads, true, batchTotal, batchCompleted)
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
    if (httpCode == 403) R.string.error_this_song_cannot_be_played_due_to_server_restrictions
    else when (specificCause) {
        is UnresolvedAddressException, is UnknownHostException -> R.string.error_a_network_error_has_occurred
        is PlayableFormatNotFoundException -> R.string.error_couldn_t_find_a_playable_audio_format
        is UnplayableException -> R.string.error_the_original_video_source_of_this_song_has_been_deleted
        is LoginRequiredException -> R.string.error_this_song_cannot_be_played_due_to_server_restrictions
        is VideoIdMismatchException -> R.string.error_the_returned_video_id_doesn_t_match_the_requested_one
        is PlayableFormatNonSupported -> R.string.error_file_unsupported_format
        is NoInternetException, is StreamResolutionOfflineException -> R.string.error_no_internet
        is TimeoutException -> R.string.error_timeout
        is InterruptedIOException, is SocketException -> R.string.error_a_network_error_has_occurred
        // P11 (review, 2026-10-23): download-domain fallback — the playback-domain
        // unknown-error string was misleading for a failed download.
        else -> R.string.download_failed
    }
