package app.n_zik.android.components.settings.appearance.preview

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.n_zik.android.R
import app.n_zik.android.core.notifications.THEME_STORE_PREVIEW_GENERATION_CHANNEL_ID
import app.n_zik.android.core.notifications.ensureStartupNotificationChannel
import app.n_zik.android.utils.getActiveProfile
import timber.log.Timber

internal class ThemePreviewProgressNotification(private val context: Context) {
    private val profileId = getActiveProfile(context)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val tag = "theme-preview:${java.util.UUID.randomUUID()}"
    private var receiver: BroadcastReceiver? = null
    private var cancelIntent: PendingIntent? = null

    /** A new action identity for every operation prevents old actions cancelling retries. */
    fun start(onCancel: () -> Unit) {
        cancel()
        val action = "${context.packageName}.theme_preview.CANCEL.${java.util.UUID.randomUUID()}"
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != action || receiver !== this) return
                cancel()
                onCancel()
            }
        }
        ContextCompat.registerReceiver(context, listener, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = listener
        cancelIntent = PendingIntent.getBroadcast(
            context, 0, Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        show()
    }

    fun show(completed: Int = 0, total: Int = 0) {
        val action = cancelIntent ?: return
        val notifications = manager ?: return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val channel = ensureStartupNotificationChannel(context, THEME_STORE_PREVIEW_GENERATION_CHANNEL_ID, profileId)
        val title = context.getString(R.string.theme_store_generating_previews)

        val text = if (total == 0) context.getString(R.string.theme_store_preparing_previews)
        else context.getString(R.string.theme_store_preview_capture_progress, completed, total)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ui)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(total, completed, total == 0)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(0, context.getString(R.string.common_action_cancel), action)
            .build()
        try {
            notifications.notify(tag, 1, notification)
        } catch (error: SecurityException) {
            Timber.tag("ThemePreview").w(error, "Preview notification permission changed")
        }
    }

    fun cancel() {
        manager?.cancel(tag, 1)
        cancelIntent?.cancel()
        cancelIntent = null
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
    }
}
