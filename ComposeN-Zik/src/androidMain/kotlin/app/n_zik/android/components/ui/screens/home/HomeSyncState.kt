package app.n_zik.android.components.ui.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.core.notifications.channelId
import app.n_zik.android.MainActivity
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.PlaylistPreview

object HomeSyncState {
    var isSyncingArtists by mutableStateOf(false)
    var artistSyncProgress by mutableStateOf(0f)
    var artistSyncCurrentName by mutableStateOf("")
    var artistSyncCurrentIndex by mutableStateOf(0)
    var artistSyncTotal by mutableStateOf(0)
    var artistSyncFailed by mutableStateOf(0)
    var failedArtistsList by mutableStateOf<List<Artist>>(emptyList())

    var isSyncingAlbums by mutableStateOf(false)
    var albumSyncProgress by mutableStateOf(0f)
    var albumSyncCurrentName by mutableStateOf("")
    var albumSyncCurrentIndex by mutableStateOf(0)
    var albumSyncTotal by mutableStateOf(0)
    var albumSyncFailed by mutableStateOf(0)
    var failedAlbumsList by mutableStateOf<List<Album>>(emptyList())

    var isSyncingPlaylists by mutableStateOf(false)
    var playlistSyncProgress by mutableStateOf(0f)
    var playlistSyncCurrentName by mutableStateOf("")
    var playlistSyncCurrentIndex by mutableStateOf(0)
    var playlistSyncTotal by mutableStateOf(0)
    var playlistSyncFailed by mutableStateOf(0)
    var failedPlaylistsList by mutableStateOf<List<PlaylistPreview>>(emptyList())

    fun showSyncNotification(
        title: String,
        message: String,
        notificationId: Int = 1001,
        isOngoing: Boolean = false,
        maxProgress: Int = 0,
        currentProgress: Int = 0
    ) {
        val notificationManager = appContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(appContext(), MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext(),
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(appContext(), channelId(HomeSyncService.SYNC_NOTIFICATION_CHANNEL_ID, getActiveProfile(appContext())))
            .setSmallIcon(R.drawable.sync) // Using sync icon or default
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(!isOngoing)
            .setOngoing(isOngoing)
            .setContentIntent(pendingIntent)
            
        if (isOngoing && maxProgress > 0) {
            builder.setProgress(maxProgress, currentProgress, false)
        } else if (isOngoing) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(0, 0, false)
        }
            
        notificationManager.notify(notificationId, builder.build())
    }
    
    fun clearSyncNotification(notificationId: Int) {
        val notificationManager = appContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }
}
