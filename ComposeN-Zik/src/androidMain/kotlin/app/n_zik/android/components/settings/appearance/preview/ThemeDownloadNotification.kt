package app.n_zik.android.components.settings.appearance.preview

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.n_zik.android.MainActivity
import app.n_zik.android.R
import timber.log.Timber

internal class ThemeDownloadNotification(private val context: Context, private val theme: StoreTheme) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val tag = "theme-download:${theme.themeId}:${theme.revision}"

    fun show(result: Boolean? = null) {
        val notifications = manager ?: return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val channel = "theme_store_downloads"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(NotificationChannel(
                channel, context.getString(R.string.theme_store_downloaded), NotificationManager.IMPORTANCE_LOW,
            ))
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(Intent.ACTION_VIEW, Uri.parse("nzik://theme/${theme.themeId}/${theme.revision}"), context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val title = when (result) {
            null -> R.string.theme_store_downloading
            true -> R.string.theme_store_download_saved
            false -> R.string.theme_store_download_failed
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(if (result == true) R.drawable.downloaded else R.drawable.download_progress)
            .setContentTitle(context.getString(title))
            .setContentText(theme.title)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(result == null)
            .setAutoCancel(result != null)
        if (result == null) builder.setProgress(0, 0, true)
        else builder.addAction(R.drawable.ui, context.getString(R.string.theme_store_details), open)
        try {
            notifications.notify(tag, 1, builder.build())
        } catch (error: SecurityException) {
            Timber.tag("ThemeStore").w(error, "Theme download notification permission changed")
        }
    }

    fun cancel() {
        manager?.cancel(tag, 1)
    }
}
