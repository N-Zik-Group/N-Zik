package app.n_zik.android.components.settings.appearance.preview

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.n_zik.android.R
import timber.log.Timber

internal class ThemePreviewProgressNotification(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val tag = "theme-preview:${java.util.UUID.randomUUID()}"

    fun show(completed: Int = 0, total: Int = 0) {
        val notifications = manager ?: return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val channel = "theme_store_preview_generation"
        val title = context.getString(R.string.theme_store_generating_previews)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(NotificationChannel(channel, title, NotificationManager.IMPORTANCE_LOW))
        }
        val text = if (total == 0) context.getString(R.string.theme_store_preparing_previews)
        else context.getString(R.string.theme_store_preview_capture_progress, completed, total)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ui)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(total, completed, total == 0)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
        try {
            notifications.notify(tag, 1, notification)
        } catch (error: SecurityException) {
            Timber.tag("ThemePreview").w(error, "Preview notification permission changed")
        }
    }

    fun cancel() {
        manager?.cancel(tag, 1)
    }
}
