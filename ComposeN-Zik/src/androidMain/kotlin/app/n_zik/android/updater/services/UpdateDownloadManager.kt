package app.n_zik.android.updater.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import app.n_zik.android.R
import app.n_zik.android.core.network.client.NetworkClientFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import okhttp3.CacheControl

/**
 * Manages APK downloads for in-app updates.
 *
 * Uses OkHttp (via [NetworkClientFactory]) to respect proxy settings.
 * Exposes download state via [downloadState] StateFlow for UI observation.
 */
object UpdateDownloadManager {

    sealed class DownloadState {
        data object Idle : DownloadState()
        data object Starting : DownloadState()
        data class Downloading(val progress: Float) : DownloadState()
        data class Completed(val filePath: String) : DownloadState()
        data class Failed(val error: String) : DownloadState()
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    var downloadingVersion: String? = null
        private set

    private var downloadJob: Job? = null
    private var activeCall: Call? = null

    private const val UPDATE_CHANNEL_ID = "update_channel"
    private const val UPDATE_NOTIFICATION_ID = 9999

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UPDATE_CHANNEL_ID,
                context.getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Starts downloading the APK from the given URL.
     * Progress is reported through [downloadState].
     */
    fun startDownload(context: Context, apkUrl: String, version: String) {
        // Cancel any existing download
        cancelDownload(context)

        downloadingVersion = version
        _downloadState.value = DownloadState.Starting

        ensureNotificationChannel(context)
        val notificationManager = NotificationManagerCompat.from(context)
        val notificationBuilder = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.update)
            .setContentTitle(context.getString(R.string.downloading_update))
            .setContentText(version)
            .setOngoing(true)
            .setProgress(100, 0, false)
            .setOnlyAlertOnce(true)

        try {
            notificationManager.notify(UPDATE_NOTIFICATION_ID, notificationBuilder.build())
        } catch (e: SecurityException) {
            Timber.tag("UpdateDownloadManager").w(e, "Missing POST_NOTIFICATIONS permission")
        }

        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            var outputStream: FileOutputStream? = null
            var outputFile: File? = null
            try {
                val request = Request.Builder()
                    .url(apkUrl)
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .build()

                val call = NetworkClientFactory.getClient().newCall(request)
                activeCall = call
                val response = call.execute()

                if (!response.isSuccessful) {
                    try { notificationManager.cancel(UPDATE_NOTIFICATION_ID) } catch (e: Exception) { Timber.tag("UpdateDownloadManager").w(e, "Failed to cancel notification") }
                    _downloadState.value = DownloadState.Failed(
                        context.getString(R.string.server_error, response.code.toString(), response.message)
                    )
                    return@launch
                }

                val body = response.body ?: run {
                    try { notificationManager.cancel(UPDATE_NOTIFICATION_ID) } catch (e: Exception) { Timber.tag("UpdateDownloadManager").w(e, "Failed to cancel notification") }
                    _downloadState.value = DownloadState.Failed(context.getString(R.string.empty_response_body))
                    return@launch
                }

                val contentLength = body.contentLength()
                val inputStream = body.byteStream()

                // Create download directory
                val downloadDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "nzik_updates"
                )
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }

                outputFile = File(downloadDir, "nzik-update-$version.apk")
                outputStream = FileOutputStream(outputFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead: Long = 0
                var lastProgressPercent = -1

                _downloadState.value = DownloadState.Downloading(0f)

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    // Check if the coroutine was cancelled between reads
                    ensureActive()

                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    if (contentLength > 0) {
                        val progress = totalBytesRead.toFloat() / contentLength.toFloat()
                        _downloadState.value = DownloadState.Downloading(progress.coerceIn(0f, 1f))
                        
                        val progressPercent = (progress * 100).toInt()
                        if (progressPercent != lastProgressPercent && progressPercent % 2 == 0) {
                            lastProgressPercent = progressPercent
                            notificationBuilder.setProgress(100, progressPercent, false)
                            try { notificationManager.notify(UPDATE_NOTIFICATION_ID, notificationBuilder.build()) } catch (e: Exception) { Timber.tag("UpdateDownloadManager").w(e, "Failed to update progress notification") }
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                outputStream = null
                inputStream.close()
                activeCall = null

                val installIntent = getInstallIntent(context, outputFile)
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, installIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val completedNotification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
                    .setSmallIcon(R.drawable.update)
                    .setContentTitle(context.getString(R.string.update_download_completed))
                    .setContentText(context.getString(R.string.tap_to_install_update))
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()
                try {
                    notificationManager.cancel(UPDATE_NOTIFICATION_ID)
                    notificationManager.notify(UPDATE_NOTIFICATION_ID, completedNotification)
                } catch (e: Exception) {
                    Timber.tag("UpdateDownloadManager").e(e, "Failed to show completed notification")
                }

                _downloadState.value = DownloadState.Completed(outputFile.absolutePath)

            } catch (e: CancellationException) {
                // Download was cancelled - clean up silently
                try { notificationManager.cancel(UPDATE_NOTIFICATION_ID) } catch (ex: Exception) { Timber.tag("UpdateDownloadManager").w(ex, "Failed to cancel notification on cancellation") }
                outputStream?.runCatching { close() }
                activeCall = null
                cleanupTempFiles(context)
                _downloadState.value = DownloadState.Idle
            } catch (e: IOException) {
                // OkHttp throws IOException when a Call is cancelled
                outputStream?.runCatching { close() }
                activeCall = null
                if (downloadJob?.isCancelled == true) {
                    try { notificationManager.cancel(UPDATE_NOTIFICATION_ID) } catch (ex: Exception) { Timber.tag("UpdateDownloadManager").w(ex, "Failed to cancel notification on job cancel") }
                    cleanupTempFiles(context)
                    _downloadState.value = DownloadState.Idle
                } else {
                    Timber.tag("UpdateDownloadManager").e(e, "Update download failed")
                    val failedNotification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
                        .setSmallIcon(R.drawable.update)
                        .setContentTitle(context.getString(R.string.update_download_failed))
                        .setContentText(e.message ?: context.getString(R.string.download_failed))
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .build()
                    try { notificationManager.notify(UPDATE_NOTIFICATION_ID, failedNotification) } catch (ex: Exception) { Timber.tag("UpdateDownloadManager").e(ex, "Failed to show failed notification") }
                    
                    _downloadState.value = DownloadState.Failed(
                        e.message ?: context.getString(R.string.download_failed)
                    )
                }
            } catch (e: Exception) {
                outputStream?.runCatching { close() }
                activeCall = null
                cleanupTempFiles(context)
                Timber.tag("UpdateDownloadManager").e(e, "Update download failed")
                val failedNotification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
                    .setSmallIcon(R.drawable.update)
                    .setContentTitle(context.getString(R.string.update_download_failed))
                    .setContentText(e.message ?: context.getString(R.string.download_failed))
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .build()
                try { notificationManager.notify(UPDATE_NOTIFICATION_ID, failedNotification) } catch (ex: Exception) { Timber.tag("UpdateDownloadManager").e(ex, "Failed to show failed notification") }
                
                _downloadState.value = DownloadState.Failed(
                    e.message ?: context.getString(R.string.download_failed)
                )
            }
        }
    }

    /**
     * Cancels an ongoing download and cleans up temporary files.
     */
    fun cancelDownload(context: Context) {
        // Cancel the OkHttp call first - this unblocks inputStream.read() immediately
        activeCall?.cancel()
        activeCall = null
        downloadJob?.cancel()
        downloadJob = null
        cleanupTempFiles(context)
        downloadingVersion = null
        _downloadState.value = DownloadState.Idle
    }

    /**
     * Resets the state back to Idle without cancelling.
     */
    fun resetState() {
        downloadingVersion = null
        _downloadState.value = DownloadState.Idle
    }

    private fun getInstallIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Triggers the Android package installer for the downloaded APK.
     */
    fun installApk(context: Context, filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Timber.tag("UpdateDownloadManager").w("APK file not found at $filePath, skipping install")
                _downloadState.value = DownloadState.Idle
                return
            }

            val installIntent = getInstallIntent(context, file)
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Timber.tag("UpdateDownloadManager").e(e, "Failed to launch APK installer")
            _downloadState.value = DownloadState.Failed(
                context.getString(R.string.failed_to_install, e.message ?: "")
            )
        }
    }

    /**
     * Clears all downloaded APKs from the cache directory.
     */
    fun clearCache(context: Context) {
        cleanupTempFiles(context)
        downloadingVersion = null
        _downloadState.value = DownloadState.Idle
    }

    private fun cleanupTempFiles(context: Context) {
        try {
            val downloadDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "nzik_updates"
            )
            if (downloadDir.exists()) {
                downloadDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            Timber.tag("UpdateDownloadManager").e(e, "Failed to cleanup temp files")
        }
    }
}

