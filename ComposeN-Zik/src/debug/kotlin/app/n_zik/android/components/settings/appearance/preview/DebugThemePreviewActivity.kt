package app.n_zik.android.components.settings.appearance.preview

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Bundle
import app.n_zik.android.R

class DebugThemePreviewActivity : ThemePreviewActivity() {
    override val automation = true

    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent.getBooleanExtra("postNotificationSuite", false)) {
            intent.removeExtra("postNotificationSuite")
            postNotificationSuite()
        }
        if (intent.getBooleanExtra("postNotificationExample", false)) {
            intent.removeExtra("postNotificationExample")
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel("debug_inbox_example", "Inbox example", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
            val openPreviews = PendingIntent.getActivity(
                this, 0, ThemePreviewActivity.intent(this), PendingIntent.FLAG_IMMUTABLE,
            )
            manager.notify("inbox-example", 1, Notification.Builder(this, channel.id)
                .setSmallIcon(R.drawable.notification2)
                .setContentTitle("New theme available")
                .setContentText("Explore the Normal and Glass previews for Artwork Gradient.")
                .setContentIntent(openPreviews)
                .addAction(Notification.Action.Builder(null, "View previews", openPreviews).build())
                .build())
        }
        super.onCreate(savedInstanceState)
    }

    private fun postNotificationSuite() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel("debug_inbox_suite", "Notification test suite", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
        val samples = listOf(
            Triple("Playback • test", "Now playing a sample track.", true),
            Triple("Music downloads • test", "Downloading 2 tracks.", true),
            Triple("Listen Together • test", "A listener asked to join your room.", false),
            Triple("App update • test", "The sample update finished downloading.", false),
            Triple("Modules • test", "The sample module failed to download.", false),
            Triple("Rewind • test", "The monthly playlist is ready.", false),
            Triple("PC Bridge • test", "The sample server is running.", true),
        )
        val openPreviews = PendingIntent.getActivity(
            this, 1, ThemePreviewActivity.intent(this), PendingIntent.FLAG_IMMUTABLE,
        )
        samples.forEachIndexed { index, (title, message, ongoing) ->
            val builder = Notification.Builder(this, channel.id)
                .setSmallIcon(R.drawable.notification2)
                .setContentTitle(title)
                .setContentText(message)
                .setCategory(if (index == 0) Notification.CATEGORY_TRANSPORT else null)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
            val labels = when (index) {
                1 -> listOf("Pause", "Cancel")
                2 -> listOf("Approve", "Reject")
                3 -> listOf("Install update")
                4 -> listOf("Retry")
                5 -> listOf("Open playlist")
                6 -> listOf("Open Bridge")
                else -> emptyList()
            }
            labels.forEach { label ->
                builder.addAction(Notification.Action.Builder(null, label, openPreviews).build())
            }
            if (index == 1) {
                builder.setProgress(100, 42, false)
                    .setStyle(Notification.InboxStyle().addLine("Track 1 • 42%").addLine("Track 2 • Waiting"))
            }
            manager.notify("inbox-suite", 100 + index, builder.build())
        }
    }
}
