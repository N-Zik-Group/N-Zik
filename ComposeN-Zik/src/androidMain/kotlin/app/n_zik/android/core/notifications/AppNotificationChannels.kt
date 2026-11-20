package app.n_zik.android.core.notifications

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import app.n_zik.android.R
import app.n_zik.android.bridge.BRIDGE_SERVER_CHANNEL_ID
import app.n_zik.android.components.ui.screens.rewind.RewindReminderWorker
import app.n_zik.android.core.matching.SONG_MATCH_NOTIFICATION_CHANNEL_ID
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.listentogether.ListenTogetherClient
import app.n_zik.android.modules.AndroidModuleDownloadListener
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.updater.services.UpdateDownloadManager
import app.n_zik.android.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.readProfileIds

internal const val THEME_STORE_CREATOR_SUBMISSIONS_CHANNEL_ID = "theme_store_creator_submissions"
internal const val THEME_STORE_PUBLICATIONS_CHANNEL_ID = "theme_store_publications"
internal const val THEME_STORE_DOWNLOADS_CHANNEL_ID = "theme_store_downloads"
internal const val THEME_STORE_PREVIEW_GENERATION_CHANNEL_ID = "theme_store_preview_generation"

internal enum class AppNotificationGroup(val id: String, @StringRes val nameRes: Int) {
    ToolsAndUpdates("nzik_tools_updates", R.string.notification_group_tools_updates),
    ThemeStore("nzik_theme_store", R.string.theme_store_title),
}

internal data class NotificationChannelSpec(
    val baseId: String,
    @StringRes val nameRes: Int,
    val importance: Int,
    @StringRes val descRes: Int,
    val badgeOff: Boolean = false,
    val appGroup: AppNotificationGroup? = null,
) {
    @RequiresApi(Build.VERSION_CODES.O)
    fun pendingRegistration(
        existing: NotificationChannel?,
        strings: Resources,
        groupId: String,
        id: String = baseId,
        name: String = strings.getString(nameRes),
    ): NotificationChannel? {
        if (existing?.group != null) return null
        // Re-submit an ungrouped channel with its existing user settings.
        val channel = existing ?: NotificationChannel(id, name, importance).apply {
            description = strings.getString(descRes)
            if (badgeOff) setShowBadge(false)
        }
        channel.group = groupId
        return channel
    }
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun NotificationManager.ensureChannelGroup(id: String, name: String) {
    if (notificationChannelGroups.firstOrNull { it.id == id }?.name?.toString() != name) {
        createNotificationChannelGroup(NotificationChannelGroup(id, name))
    }
}

internal val PROFILE_CHANNEL_SPECS = listOf(
    NotificationChannelSpec(
        PlayerServiceModern.NotificationChannelId,
        R.string.notification_channel_playback,
        NotificationManager.IMPORTANCE_LOW,
        descRes = R.string.notification_channel_playback_description,
        badgeOff = true,
    ),
    NotificationChannelSpec(
        PlayerServiceModern.SleepTimerNotificationChannelId,
        R.string.player_sleep_timer_title,
        NotificationManager.IMPORTANCE_DEFAULT,
        descRes = R.string.notification_channel_sleep_timer_description,
        badgeOff = true,
    ),
    NotificationChannelSpec(
        MyDownloadHelper.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
        R.string.notification_channel_audio_downloads,
        NotificationManager.IMPORTANCE_LOW,
        descRes = R.string.notification_channel_audio_downloads_description,
        badgeOff = true,
    ),
    NotificationChannelSpec(
        RewindReminderWorker.CHANNEL_ID,
        R.string.rewind_channel,
        NotificationManager.IMPORTANCE_LOW,
        descRes = R.string.notification_channel_rewind_description,
        badgeOff = true,
    ),
    NotificationChannelSpec(
        ListenTogetherClient.NOTIFICATION_CHANNEL_ID,
        R.string.listen_together_notification_channel_name,
        NotificationManager.IMPORTANCE_HIGH,
        descRes = R.string.listen_together_notification_channel_description,
    ),
    NotificationChannelSpec(
        THEME_STORE_CREATOR_SUBMISSIONS_CHANNEL_ID,
        R.string.notification_channel_theme_submissions,
        NotificationManager.IMPORTANCE_DEFAULT,
        descRes = R.string.notification_channel_theme_submissions_description,
        appGroup = AppNotificationGroup.ThemeStore,
    ),
    NotificationChannelSpec(
        THEME_STORE_PUBLICATIONS_CHANNEL_ID,
        R.string.notification_channel_theme_publications,
        NotificationManager.IMPORTANCE_DEFAULT,
        descRes = R.string.notification_channel_theme_publications_description,
        appGroup = AppNotificationGroup.ThemeStore,
    ),
    NotificationChannelSpec(
        THEME_STORE_DOWNLOADS_CHANNEL_ID,
        R.string.notification_channel_theme_downloads,
        NotificationManager.IMPORTANCE_LOW,
        descRes = R.string.notification_channel_theme_downloads_description,
        appGroup = AppNotificationGroup.ThemeStore,
    ),
    NotificationChannelSpec(
        THEME_STORE_PREVIEW_GENERATION_CHANNEL_ID,
        R.string.notification_channel_theme_previews,
        NotificationManager.IMPORTANCE_LOW,
        descRes = R.string.notification_channel_theme_previews_description,
        appGroup = AppNotificationGroup.ThemeStore,
    ),
)

internal val APP_WIDE_CHANNEL_SPECS = listOf(
    NotificationChannelSpec(
        SONG_MATCH_NOTIFICATION_CHANNEL_ID,
        R.string.song_matching_progress_title,
        NotificationManager.IMPORTANCE_LOW,
        descRes = R.string.notification_channel_song_matching_description,
        badgeOff = true,
        appGroup = AppNotificationGroup.ToolsAndUpdates,
    ),
    NotificationChannelSpec(
        BRIDGE_SERVER_CHANNEL_ID,
        R.string.bridge_server,
        NotificationManager.IMPORTANCE_LOW,
        descRes = R.string.notification_channel_pc_server_description,
        appGroup = AppNotificationGroup.ToolsAndUpdates,
    ),
    NotificationChannelSpec(
        UpdateDownloadManager.UPDATE_CHANNEL_ID,
        R.string.updates_channel_name,
        NotificationManager.IMPORTANCE_DEFAULT,
        descRes = R.string.notification_channel_app_updates_description,
        appGroup = AppNotificationGroup.ToolsAndUpdates,
    ),
    NotificationChannelSpec(
        AndroidModuleDownloadListener.CHANNEL_ID,
        R.string.modules_download_channel,
        NotificationManager.IMPORTANCE_DEFAULT,
        descRes = R.string.notification_channel_module_downloads_description,
        appGroup = AppNotificationGroup.ToolsAndUpdates,
    ),
)

internal fun installAppNotificationChannels(context: Context, strings: Resources) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    APP_WIDE_CHANNEL_SPECS.mapNotNull { it.appGroup }.distinct().forEach { group ->
        manager.ensureChannelGroup(group.id, strings.getString(group.nameRes))
    }
    val existing = manager.notificationChannels.associateBy { it.id }
    val pending = APP_WIDE_CHANNEL_SPECS.mapNotNull { spec ->
        spec.pendingRegistration(existing[spec.baseId], strings, requireNotNull(spec.appGroup).id)
    }
    if (pending.isNotEmpty()) manager.createNotificationChannels(pending)
    (listOf(DEFAULT_PROFILE_ID) + context.readProfileIds() + getActiveProfile(context)).distinct().forEach { profileId ->
        registerProfileChannels(context, manager, strings, profileId, existing)
    }
    refreshNotificationChannelLabels(context, strings, manager)
}

/** Keeps notification senders working if they run before full application initialization. */
internal fun ensureAppNotificationChannel(context: Context, id: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val existing = manager.getNotificationChannel(id)
    if (existing?.group != null) return
    val spec = APP_WIDE_CHANNEL_SPECS.first { it.baseId == id }
    val strings = ContextCompat.getContextForLanguage(context).resources
    val group = requireNotNull(spec.appGroup)
    manager.ensureChannelGroup(group.id, strings.getString(group.nameRes))
    spec.pendingRegistration(existing, strings, group.id)?.let(manager::createNotificationChannel)
}
