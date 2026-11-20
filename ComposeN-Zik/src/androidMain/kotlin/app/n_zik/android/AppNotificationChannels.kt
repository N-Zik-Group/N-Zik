package app.n_zik.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import app.n_zik.android.core.notifications.refreshNotificationChannelLabels
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.components.ui.screens.rewind.RewindReminderWorker

internal fun installAppNotificationChannels(context: Context, strings: Resources) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        // Channel for music player
        val playerChannel = NotificationChannel(
            PlayerServiceModern.NotificationChannelId,
            strings.getString(R.string.player_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = strings.getString(R.string.player_title)
            setShowBadge(false)
        }

        // Channel for sleep timer
        val sleepTimerChannel = NotificationChannel(
            PlayerServiceModern.SleepTimerNotificationChannelId,
            strings.getString(R.string.player_sleep_timer_title),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = strings.getString(R.string.player_sleep_timer_title)
            setShowBadge(false)
        }

        // Channel for downloads
        val downloadChannel = NotificationChannel(
            MyDownloadHelper.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
            strings.getString(R.string.downloads_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = strings.getString(R.string.downloads_title)
            setShowBadge(false)
        }

        // Channel for the monthly rewind reminder
        val rewindChannel = NotificationChannel(
            RewindReminderWorker.CHANNEL_ID,
            strings.getString(R.string.rewind_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = strings.getString(R.string.rewind_channel)
            setShowBadge(false)
        }

        notificationManager.createNotificationChannels(
            listOf(playerChannel, sleepTimerChannel, downloadChannel, rewindChannel)
        )
        refreshNotificationChannelLabels(context, strings, notificationManager)
    }
}
