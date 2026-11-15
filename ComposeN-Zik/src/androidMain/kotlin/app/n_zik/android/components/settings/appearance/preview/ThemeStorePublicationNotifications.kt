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
import app.n_zik.android.R
import app.n_zik.android.MainActivity
import timber.log.Timber
import app.n_zik.android.components.settings.accounts.GitHubAccount

private val publicationNotificationLock = Any()

internal fun notifyThemeStoreAlerts(context: Context, library: ThemeStoreLibrary, themes: List<StoreTheme>) {
    synchronized(publicationNotificationLock) {
        notifyThemePublications(context, library, themes)
        notifyDownloadedThemeUpdates(context, library, themes)
        GitHubAccount.userId(context)?.let { notifyOwnThemePublications(context, it, themes) }
    }
}

private fun notifyDownloadedThemeUpdates(context: Context, library: ThemeStoreLibrary, themes: List<StoreTheme>) {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = "theme_store_publications"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(NotificationChannel(
            channel, context.getString(R.string.theme_store_publications), NotificationManager.IMPORTANCE_DEFAULT,
        ))
        if (manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return
    }
    for (theme in library.downloadedThemeUpdates(themes, ThemeUpdateMode.Notify)) {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(Intent.ACTION_VIEW, Uri.parse("nzik://theme/${theme.themeId}/${theme.revision}"), context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.notification2)
            .setContentTitle(context.getString(R.string.theme_store_download_update_available))
            .setContentText(theme.title)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify("theme-store-update:${theme.themeId}", 1, notification)
            library.markThemeUpdateNotified(theme)
        } catch (error: SecurityException) {
            Timber.tag("ThemeStore").w(error, "Theme update notification permission changed")
        }
    }
}

private fun notifyOwnThemePublications(context: Context, id: Long, themes: List<StoreTheme>) {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = "theme_store_publications"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(NotificationChannel(
            channel, context.getString(R.string.theme_store_publications), NotificationManager.IMPORTANCE_DEFAULT,
        ))
        if (manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return
    }
    val prefs = context.getSharedPreferences("theme_creator_publications", Context.MODE_PRIVATE)
    val initialized = prefs.getBoolean("initialized:$id", false)
    val saved = prefs.edit()
    for (theme in themes.filter { it.author.githubId == id }) {
        val key = "$id:${theme.themeId}"
        val previous = prefs.getInt(key, 0)
        if (theme.revision <= previous) continue
        if (initialized) {
            val title = if (previous == 0) R.string.theme_store_own_theme_published else R.string.theme_store_own_theme_updated
            val notification = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.notification2)
                .setContentTitle(context.getString(title))
                .setContentText(theme.title)
                .setAutoCancel(true)
                .build()
            try {
                manager.notify("creator-theme:${theme.themeId}", 1, notification)
            } catch (_: SecurityException) {
                continue
            }
        }
        saved.putInt(key, theme.revision)
    }
    saved.putBoolean("initialized:$id", true).apply()
}

private fun notifyThemePublications(context: Context, library: ThemeStoreLibrary, themes: List<StoreTheme>) {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = "theme_store_publications"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(NotificationChannel(
            channel, context.getString(R.string.theme_store_publications), NotificationManager.IMPORTANCE_DEFAULT,
        ))
        if (manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return
    }
    for (theme in library.newPublications(themes)) {
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.notification2)
            .setContentTitle(context.getString(R.string.theme_store_publication_title, theme.author.login))
            .setContentText(theme.title)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify("theme-store:${theme.themeId}", 1, notification)
            library.markPublicationNotified(theme)
        } catch (error: SecurityException) {
            Timber.tag("ThemeStore").w(error, "Publication notification permission changed")
        }
    }
}
