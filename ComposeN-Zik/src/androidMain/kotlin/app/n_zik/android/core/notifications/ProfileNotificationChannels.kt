package app.n_zik.android.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.home.HomeSyncService
import app.n_zik.android.components.ui.screens.rewind.RewindReminderWorker
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.listentogether.ListenTogetherClient
import app.n_zik.android.playback.services.PlayerServiceModern
import timber.log.Timber

/**
 * Per-profile notification channels (goal ⑩ — scope Profiles, spec per-profile-notifications).
 *
 * The app's 6 profiled channels (player, sleep timer, download, sync, rewind, listen-together)
 * are resolved per profile: the base profile keeps its current channel IDs exactly (zero
 * migration, bit-for-bit identical behavior — its 5 boot channels keep being created by
 * `MainApplication.createNotificationChannels()`, the listen-together one by
 * `ListenTogetherClient.ensureNotificationChannel()` on first use), while every user profile
 * owns its 6 channels as `<current id>_<profile id>`. The "update" and "PC bridge" channels
 * are NOT profiled (app-wide features, not per-profile ones).
 */

private const val TAG = "ProfileNotificationChannels"

/** The spec of one of the 6 profiled channels (its base ID + the channel attributes). */
private data class ChannelSpec(
    val baseId: String,
    val nameRes: Int,
    val descRes: Int,
    val importance: Int,
    /** true when the base channel is created with `setShowBadge(false)`. */
    val badgeOff: Boolean,
)

// All six base IDs reference their emitters' constants directly — a single source of the
// profile channel set (HomeSyncService.SYNC_NOTIFICATION_CHANNEL_ID and
// ListenTogetherClient.NOTIFICATION_CHANNEL_ID were exposed for this; the others were already
// visible). Renaming an emitter's constant moves the profile set with it, and the
// profileChannelIds tests pin the agreement.

/** The 6 profiled channels, in the same order as the base creation in MainApplication. */
private val PROFILE_CHANNEL_SPECS = listOf(
    ChannelSpec(
        PlayerServiceModern.NotificationChannelId,
        R.string.player,
        R.string.player,
        NotificationManager.IMPORTANCE_LOW,
        badgeOff = true,
    ),
    ChannelSpec(
        PlayerServiceModern.SleepTimerNotificationChannelId,
        R.string.sleep_timer,
        R.string.sleep_timer,
        NotificationManager.IMPORTANCE_DEFAULT,
        badgeOff = true,
    ),
    ChannelSpec(
        MyDownloadHelper.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
        R.string.download,
        R.string.download,
        NotificationManager.IMPORTANCE_LOW,
        badgeOff = true,
    ),
    ChannelSpec(
        HomeSyncService.SYNC_NOTIFICATION_CHANNEL_ID,
        R.string.sync,
        R.string.sync_notifications,
        NotificationManager.IMPORTANCE_LOW,
        badgeOff = true,
    ),
    ChannelSpec(
        RewindReminderWorker.CHANNEL_ID,
        R.string.rw_channel,
        R.string.rw_channel,
        NotificationManager.IMPORTANCE_LOW,
        badgeOff = true,
    ),
    ChannelSpec(
        ListenTogetherClient.NOTIFICATION_CHANNEL_ID,
        R.string.listen_together_notification_channel_name,
        R.string.listen_together_notification_channel_desc,
        NotificationManager.IMPORTANCE_HIGH,
        badgeOff = false, // mirrors ListenTogetherClient.ensureNotificationChannel (the default badge)
    ),
)

/**
 * The channel ID [baseId] posts on for [profileId]: the base profile keeps its current ID
 * unchanged (zero migration), every other profile gets `<base id>_<profile id>` — exactly one
 * profile suffix (the base IDs already contain underscores, e.g. `default_channel_id`, so the
 * suffix never doubles).
 *
 * Pure on purpose (unit-tested without Android).
 */
fun channelId(baseId: String, profileId: String): String =
    if (profileId == DEFAULT_PROFILE_ID) baseId else "${baseId}_$profileId"

/**
 * The set of the 6 channel IDs of [profileId] — what the emitters post on and what the profile
 * switch cancels. Pure on purpose (unit-tested without Android).
 */
fun profileChannelIds(profileId: String): Set<String> =
    PROFILE_CHANNEL_SPECS.mapTo(mutableSetOf()) { channelId(it.baseId, profileId) }

/**
 * Creates (idempotently) the 6 channels of [profileId], each named with its base channel's own
 * display name. The 6 channels are separated by ID, never by name: the active profile is not
 * shown in the channel name (the channels of different profiles share their display name).
 *
 * No-op for the base profile: its channels are created by MainApplication, unchanged. Called
 * at boot for the active profile and at profile switch for the incoming profile. Re-creating an
 * existing channel is a no-op on Android (its settings are never touched).
 */
fun ensureProfileChannels(context: Context, profileId: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    if (profileId == DEFAULT_PROFILE_ID) return
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannels(
        PROFILE_CHANNEL_SPECS.map { spec ->
            NotificationChannel(
                channelId(spec.baseId, profileId),
                context.getString(spec.nameRes),
                spec.importance,
            ).apply {
                description = context.getString(spec.descRes)
                if (spec.badgeOff) setShowBadge(false)
            }
        },
    )
    Timber.tag(TAG).d("Ensured the 6 channels of profile '%s'", profileId)
}

/**
 * Cancels every ACTIVE notification posted on one of the 6 channels of [profileId] — channel
 * by channel, never by tag (robust to any notification posted on these channels, from any
 * source): the non-ongoing ones (rewind, sync, listen-together, sleep timer) survive a process
 * death, the ongoing ones are cancelled as well (their services are stopped separately).
 * Called synchronously in the profile switch, before the process exit, and in the profile
 * deletion flows. Notification channels are an API 26 (O) feature — a no-op below O.
 */
fun cancelProfileNotifications(context: Context, profileId: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val toCancel = notificationsOnChannels(notificationManager.activeNotifications, profileChannelIds(profileId))
    toCancel.forEach { sbn ->
        // Cancel by (tag, id) when the notification carries a tag, by id otherwise — a plain
        // id cancel would leave a same-id, different-tag notification in place.
        if (sbn.tag != null) notificationManager.cancel(sbn.tag, sbn.id) else notificationManager.cancel(sbn.id)
    }
    Timber.tag(TAG).d("Cancelled %d pending notification(s) of profile '%s'", toCancel.size, profileId)
}

/**
 * Purges [profileId]'s notification state — the last step of a profile deletion: cancels its
 * pending notifications ([cancelProfileNotifications]) and deletes its 6 channels, so a deleted
 * profile leaves no orphan channel (or surviving notification) behind in the system. Never
 * touches the base profile's channels (boot-managed — `MainApplication` / `ListenTogetherClient`
 * recreate them, the base is never deleted).
 */
fun deleteProfileChannels(context: Context, profileId: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    if (profileId == DEFAULT_PROFILE_ID) return
    cancelProfileNotifications(context, profileId)
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    profileChannelIds(profileId).forEach { notificationManager.deleteNotificationChannel(it) }
    Timber.tag(TAG).d("Deleted the 6 channels of profile '%s'", profileId)
}

/**
 * The [active] notifications posted on one of [channelIds] (the selection done by
 * [cancelProfileNotifications]). Pure on purpose (unit-tested without Android).
 */
fun notificationsOnChannels(
    active: Array<out StatusBarNotification>?,
    channelIds: Set<String>,
): List<StatusBarNotification> =
    active.orEmpty().filter { it.notification?.channelId in channelIds }
