package app.n_zik.android.download.services

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.R
import app.n_zik.android.core.notifications.channelId
import app.n_zik.android.download.utils.MyDownloadHelper.DOWNLOAD_NOTIFICATION_CHANNEL_ID

/** Builds both service and paused notifications without retaining a service instance. */
@UnstableApi
internal class DownloadProgressNotification(context: Context) {
    private val context = context.applicationContext

    fun build(downloads: List<Download>, isPaused: Boolean, total: Int, completed: Int): Notification {

        // Calculate progress for active downloads
        val activeDownload = downloads.firstOrNull { it.state == Download.STATE_DOWNLOADING } ?: downloads.firstOrNull()
        val currentDownloadName = activeDownload?.request?.data?.let { Util.fromUtf8Bytes(it) }

        // Calculate overall progress from all active downloads
        val totalBytes = downloads.sumOf { it.contentLength }.coerceAtLeast(1)
        val downloadedBytes = downloads.sumOf { it.bytesDownloaded }
        val downloadProgress = if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt() else 0

        val message = if (total > 0) {
            context.getString(R.string.download_progress, completed, total)
        } else if (downloads.isNotEmpty()) {
            context.getString(R.string.download_in_progress, downloads.size) + " ($downloadProgress%)"
        } else {
            context.getString(R.string.download_in_progress, downloads.size)
        }

        if (!isPaused) {
            androidx.core.app.NotificationManagerCompat.from(context).cancel(FOREGROUND_NOTIFICATION_ID + 2)
        }

        val pauseResumeAction = if (isPaused) {
            val intent = Intent(context, MyDownloadService::class.java).setAction(DownloadService.ACTION_RESUME_DOWNLOADS)
            val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(R.drawable.play, context.getString(R.string.snake_resume), pendingIntent)
        } else {
            val intent = Intent(context, MyDownloadService::class.java).setAction(DownloadService.ACTION_PAUSE_DOWNLOADS)
            val pendingIntent = PendingIntent.getService(context, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(R.drawable.pause, context.getString(R.string.notification_pause), pendingIntent)
        }

        val cancelOngoingIntent = Intent(context, MyDownloadService::class.java).setAction(MyDownloadService.ACTION_CANCEL_ONGOING)
        val cancelOngoingPendingIntent = PendingIntent.getService(context, 1, cancelOngoingIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancelAction = NotificationCompat.Action(R.drawable.close, context.getString(R.string.cancel), cancelOngoingPendingIntent)

        val deleteAllIntent = Intent(context, MyDownloadService::class.java).setAction(DownloadService.ACTION_REMOVE_ALL_DOWNLOADS)
        val deleteAllPendingIntent = PendingIntent.getService(context, 2, deleteAllIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val deleteAllAction = NotificationCompat.Action(R.drawable.trash, context.getString(R.string.delete), deleteAllPendingIntent)

        // Use batch progress if available, otherwise use download progress
        val progressMax = if (total > 0) total else 100
        val progressCurrent = if (total > 0) completed else downloadProgress
        val indeterminate = downloads.isEmpty()

        return NotificationCompat.Builder(context, channelId(DOWNLOAD_NOTIFICATION_CHANNEL_ID, getActiveProfile(context)))
            .setSmallIcon(R.drawable.download_progress)
            .setContentTitle(context.getString(R.string.download))
            .setContentText(currentDownloadName ?: message)
            .setSubText(message)
            .setProgress(progressMax, progressCurrent, indeterminate)
            .setOngoing(!isPaused)
            .setShowWhen(false)
            .addAction(pauseResumeAction)
            .addAction(cancelAction)
            .addAction(deleteAllAction)
            .build()
    }

}
