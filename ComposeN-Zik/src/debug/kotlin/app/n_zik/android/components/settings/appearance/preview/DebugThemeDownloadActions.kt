package app.n_zik.android.components.settings.appearance.preview

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.n_zik.android.R

class DebugThemeDownloadActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val title = intent.getStringExtra("theme") ?: "Artwork Gradient"
        when (intent.action) {
            "pause" -> post(context, title, paused = true)
            "resume" -> post(context, title)
            "cancel" -> manager.cancel(Tag, ProgressId)
            "update" -> {
                manager.cancel(Tag, 0)
                post(context, title)
            }
        }
    }

    companion object {
        private const val Tag = "theme-store-example"
        private const val ProgressId = 2

        internal fun action(context: Context, command: String, theme: String = "Artwork Gradient"): PendingIntent =
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, DebugThemeDownloadActions::class.java).setAction(command).putExtra("theme", theme),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        internal fun post(context: Context, theme: String, paused: Boolean = false) {
            val builder = Notification.Builder(context, "debug_theme_store_examples")
                .setSmallIcon(R.drawable.download_progress)
                .setContentTitle(if (paused) "Download paused • demo" else "Downloading theme • demo")
                .setContentText("$theme · 42%")
                .setProgress(100, 42, false)
                .setOngoing(!paused)
                .addAction(
                    if (paused) R.drawable.play else R.drawable.pause,
                    context.getString(if (paused) R.string.downloads_action_resume else R.string.player_notification_pause),
                    action(context, if (paused) "resume" else "pause", theme),
                )
                .addAction(R.drawable.close, context.getString(R.string.common_action_cancel), action(context, "cancel", theme))
            context.getSystemService(NotificationManager::class.java).notify(Tag, ProgressId, builder.build())
        }
    }
}
