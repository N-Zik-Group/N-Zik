package app.n_zik.android.download.services

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.R
import app.n_zik.android.core.notifications.channelId
import app.n_zik.android.download.utils.MyDownloadHelper.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import app.n_zik.android.download.utils.downloadDisplayName
import java.text.NumberFormat

/** Builds both service and paused notifications without retaining a service instance. */
@UnstableApi
internal class DownloadProgressNotification(context: Context) {
    private val context = context.applicationContext
    private val commands = ProfileDownloadCommands.capture(this.context)

    fun build(downloads: List<Download>, isPaused: Boolean, total: Int, completed: Int): Notification {
        val strings = ContextCompat.getContextForLanguage(context).resources

        // Calculate progress for active downloads
        val activeDownload = downloads.firstOrNull { it.state == Download.STATE_DOWNLOADING } ?: downloads.firstOrNull()
        val currentDownloadName = activeDownload?.request?.data?.let { downloadDisplayName(Util.fromUtf8Bytes(it)) }

        // Calculate overall progress from all active downloads
        val totalBytes = downloads.sumOf { it.contentLength }.coerceAtLeast(1)
        val downloadedBytes = downloads.sumOf { it.bytesDownloaded }
        val downloadProgress = if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt() else 0

        val message = if (total > 0) {
            strings.getString(R.string.downloads_progress, completed, total)
        } else if (downloads.isNotEmpty()) {
            val percent = NumberFormat.getPercentInstance(strings.configuration.locales[0]).format(downloadProgress / 100.0)
            val status = strings.getString(R.string.downloads_in_progress, downloads.size)
            "$status ($percent)"
        } else {
            strings.getString(R.string.downloads_in_progress, downloads.size)
        }

        if (!isPaused) {
            androidx.core.app.NotificationManagerCompat.from(context).cancel(FOREGROUND_NOTIFICATION_ID + 2)
        }

        val notification = NotificationCompat.Builder(context, channelId(DOWNLOAD_NOTIFICATION_CHANNEL_ID, getActiveProfile(context)))
        val target = commands
        if (target != null && isPaused) {
            val intent = target.intent(context, DownloadService.ACTION_RESUME_DOWNLOADS)
            val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notification.addAction(R.drawable.play, strings.getString(R.string.downloads_action_resume), pendingIntent)
        } else if (target != null) {
            val intent = target.intent(context, DownloadService.ACTION_PAUSE_DOWNLOADS)
            val pendingIntent = PendingIntent.getService(context, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notification.addAction(R.drawable.pause, strings.getString(R.string.player_notification_pause), pendingIntent)
        }

        if (target != null) {
            val cancel = target.intent(context, MyDownloadService.ACTION_CANCEL_ONGOING)
            val cancelIntent = PendingIntent.getService(context, 1, cancel, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notification.addAction(R.drawable.close, strings.getString(R.string.common_action_cancel), cancelIntent)
            val delete = target.intent(context, DownloadService.ACTION_REMOVE_ALL_DOWNLOADS)
            val deleteIntent = PendingIntent.getService(context, 2, delete, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notification.addAction(R.drawable.trash, strings.getString(R.string.common_action_delete), deleteIntent)
        }

        // Use batch progress if available, otherwise use download progress
        val progressMax = if (total > 0) total else 100
        val progressCurrent = if (total > 0) completed else downloadProgress
        val indeterminate = downloads.isEmpty()

        return notification
            .setSmallIcon(R.drawable.download_progress)
            .setContentTitle(strings.getString(R.string.downloads_title))
            .setContentText(currentDownloadName ?: message)
            .setSubText(message)
            .setProgress(progressMax, progressCurrent, indeterminate)
            .setOngoing(!isPaused)
            .setShowWhen(false)
            .build()
    }

}
