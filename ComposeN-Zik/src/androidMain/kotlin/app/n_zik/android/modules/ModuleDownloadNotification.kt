package app.n_zik.android.modules

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.n_zik.android.R
import app.n_zik.android.appContext
import timber.log.Timber

/**
 * Persistent notification for module downloads - mirrors the in-app update
 * download (`UpdateDownloadManager`): ongoing progress notification with the
 * same 2%-step throttle, a Cancel action, and terminal completed/failed
 * states. Injected into [ModuleManager] so the state machine stays testable.
 *
 * Callbacks arrive on the download coroutine's dispatcher; the per-module
 * throttle state is synchronized.
 */
object AndroidModuleDownloadListener : ModuleDownloadListener {

    private const val TAG = "ModuleDownloadNotification"
    private const val CHANNEL_ID = "module_download_channel"
    // Offset from the update notification (9999) so both can coexist
    private const val NOTIFICATION_ID_OFFSET = 10_000

    private val shownPercent = HashMap<String, Int>()
    private val isUpdate = HashMap<String, Boolean>()

    override fun onDownloadStarted(def: ModuleDefinition, isUpdate: Boolean) {
        val context = appContext()
        ensureChannel(context)
        synchronized(this) {
            shownPercent[def.id] = -1
            this.isUpdate[def.id] = isUpdate
        }
        notifyProgress(context, def, 0, isUpdate)
    }

    override fun onDownloadProgress(def: ModuleDefinition, progress: Float) {
        val percent = (progress * 100).toInt()
        var updating = false
        synchronized(this) {
            val last = shownPercent[def.id] ?: -1
            // 2%-step throttle, same as UpdateDownloadManager
            if (percent <= last || percent % 2 != 0) return
            shownPercent[def.id] = percent
            updating = isUpdate[def.id] ?: false
        }
        notifyProgress(appContext(), def, percent, updating)
    }

    override fun onDownloadCompleted(def: ModuleDefinition) {
        val context = appContext()
        synchronized(this) {
            shownPercent.remove(def.id)
            isUpdate.remove(def.id)
        }
        notifyOrWarn(
            context,
            def,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.extension)
                .setContentTitle(context.getString(R.string.module_download_completed))
                .setContentText(context.getString(def.displayNameRes) + " " + def.version)
                .setOngoing(false)
                .setAutoCancel(true)
                .build(),
        )
    }

    override fun onDownloadFailed(def: ModuleDefinition, cause: Throwable) {
        // [cause] is already logged by ModuleManager - not surfaced in the notification
        val context = appContext()
        synchronized(this) {
            shownPercent.remove(def.id)
            isUpdate.remove(def.id)
        }
        notifyOrWarn(
            context,
            def,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.extension)
                .setContentTitle(context.getString(R.string.module_download_failed_notif))
                .setContentText(context.getString(def.displayNameRes))
                .setOngoing(false)
                .setAutoCancel(true)
                .build(),
        )
    }

    override fun onUninstalled(def: ModuleDefinition) {
        val context = appContext()
        ensureChannel(context)
        notifyOrWarn(
            context,
            def,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.extension)
                .setContentTitle(context.getString(R.string.module_uninstalled))
                .setContentText(context.getString(def.displayNameRes) + " " + def.version)
                .setOngoing(false)
                .setAutoCancel(true)
                .build(),
        )
    }

    override fun onDownloadCancelled(def: ModuleDefinition) {
        val context = appContext()
        synchronized(this) {
            shownPercent.remove(def.id)
            isUpdate.remove(def.id)
        }
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId(def.id)) }
            .onFailure { Timber.tag(TAG).w(it, "Could not dismiss the module download notification") }
    }

    private fun notifyProgress(context: Context, def: ModuleDefinition, percent: Int, indeterminate: Boolean) {
        notifyOrWarn(
            context,
            def,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.extension)
                .setContentTitle(
                    context.getString(if (indeterminate) R.string.module_updating else R.string.module_downloading),
                )
                .setContentText(def.version)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, percent, indeterminate)
                .addAction(0, context.getString(R.string.module_cancel), cancelPendingIntent(context, def))
                .build(),
        )
    }

    private fun cancelPendingIntent(context: Context, def: ModuleDefinition): PendingIntent {
        val intent = Intent(ModuleDownloadCancelReceiver.CANCEL_ACTION).apply {
            setPackage(context.packageName)
            putExtra(ModuleDownloadCancelReceiver.EXTRA_MODULE_ID, def.id)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId(def.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationId(moduleId: String): Int =
        NOTIFICATION_ID_OFFSET + Math.floorMod(moduleId.hashCode(), 9_000)

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.module_download_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun notifyOrWarn(context: Context, def: ModuleDefinition, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(notificationId(def.id), notification)
        } catch (e: SecurityException) {
            Timber.tag(TAG).w(e, "Missing POST_NOTIFICATIONS permission")
        }
    }
}

/**
 * Handles the Cancel action of a module download notification by cancelling
 * the in-flight download of the module named in the intent.
 */
class ModuleDownloadCancelReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ModuleDownloadCancel"
        const val CANCEL_ACTION = "app.n_zik.android.modules.CANCEL_DOWNLOAD"
        const val EXTRA_MODULE_ID = "module_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val moduleId = intent.getStringExtra(EXTRA_MODULE_ID)
        if (moduleId == null) {
            Timber.tag(TAG).w("Cancel broadcast without a module id")
            return
        }
        FfmpegModule.manager.cancelDownload(moduleId)
    }
}
