package app.n_zik.android.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import app.n_zik.android.R
import app.n_zik.android.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.utils.profileDisplayName
import timber.log.Timber

/**
 * Audio and Theme Store notification channels are scoped per profile.
 * The base profile keeps its unsuffixed channel IDs and registration at app startup.
 */

private const val TAG = "ProfileNotificationChannels"

private fun profileChannelGroupId(profileId: String): String = "nzik_profile_$profileId"

private fun NotificationChannelSpec.groupId(profileId: String): String =
    if (appGroup == AppNotificationGroup.ThemeStore) channelId(appGroup.id, profileId) else profileChannelGroupId(profileId)

private fun profileName(context: Context, strings: Resources, profileId: String): String =
    context.profileDisplayName(profileId)
        ?: if (profileId == DEFAULT_PROFILE_ID) strings.getString(R.string.profile_base_name) else profileId

private fun profileLabel(strings: Resources, category: String, profileName: String): String =
    strings.getString(R.string.profile_channel_name, category, profileName)

private fun NotificationChannelSpec.channelName(strings: Resources, profileId: String, profileName: String): String =
    if (profileId == DEFAULT_PROFILE_ID) strings.getString(nameRes) else profileLabel(strings, strings.getString(nameRes), profileName)

@RequiresApi(Build.VERSION_CODES.O)
private fun NotificationChannelSpec.ensureProfileGroup(
    manager: NotificationManager,
    strings: Resources,
    profileId: String,
    profileName: String,
) {
    val category = appGroup?.nameRes ?: R.string.notification_group_audio
    manager.ensureChannelGroup(groupId(profileId), profileLabel(strings, strings.getString(category), profileName))
}

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
 * The channel IDs of [profileId] — what the emitters post on and what the profile
 * switch cancels. Pure on purpose (unit-tested without Android).
 */
fun profileChannelIds(profileId: String): Set<String> =
    PROFILE_CHANNEL_SPECS.mapTo(mutableSetOf()) { channelId(it.baseId, profileId) }

/**
 * Registers [profileId]'s channels under the profile's display name.
 *
 * No-op for the base profile (its channels are created by MainApplication, unchanged, zero
 * migration). Called at boot for saved profiles and when creating or switching profiles.
 */
fun ensureProfileChannels(context: Context, profileId: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    if (profileId == DEFAULT_PROFILE_ID) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val strings = ContextCompat.getContextForLanguage(context).resources
    registerProfileChannels(context, manager, strings, profileId)
    Timber.tag(TAG).d("Ensured notification channels for profile '%s'", profileId)
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun registerProfileChannels(
    context: Context,
    manager: NotificationManager,
    strings: Resources,
    profileId: String,
    existing: Map<String, NotificationChannel> = manager.notificationChannels.associateBy { it.id },
) {
    val name = profileName(context, strings, profileId)
    PROFILE_CHANNEL_SPECS.distinctBy { it.appGroup }.forEach {
        it.ensureProfileGroup(manager, strings, profileId, name)
    }
    val pending = PROFILE_CHANNEL_SPECS.mapNotNull { spec ->
        val id = channelId(spec.baseId, profileId)
        spec.pendingRegistration(existing[id], strings, spec.groupId(profileId), id, spec.channelName(strings, profileId, name))
    }
    if (pending.isNotEmpty()) manager.createNotificationChannels(pending)
}

/** Ensures the canonical channel exists before library initialization. */
internal fun ensureStartupNotificationChannel(context: Context, baseId: String, profileId: String): String {
    val id = channelId(baseId, profileId)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return id
    val manager = context.getSystemService(NotificationManager::class.java) ?: return id
    val existing = manager.getNotificationChannel(id)
    if (existing?.group != null) return id
    val spec = PROFILE_CHANNEL_SPECS.first { it.baseId == baseId }
    val strings = ContextCompat.getContextForLanguage(context).resources
    val name = profileName(context, strings, profileId)
    spec.ensureProfileGroup(manager, strings, profileId, name)
    spec.pendingRegistration(
        existing, strings, spec.groupId(profileId), id, spec.channelName(strings, profileId, name),
    )?.let(manager::createNotificationChannel)
    return id
}

/** Refreshes only the names and descriptions of existing channels when the app locale changes. */
@RequiresApi(Build.VERSION_CODES.O)
internal fun refreshNotificationChannelLabels(context: Context, strings: Resources, manager: NotificationManager) {
    for (channel in manager.notificationChannels) {
        val profileSpec = PROFILE_CHANNEL_SPECS.firstOrNull {
            channel.id == it.baseId || channel.id.startsWith("${it.baseId}_")
        }
        val spec = profileSpec ?: APP_WIDE_CHANNEL_SPECS.firstOrNull { it.baseId == channel.id } ?: continue
        val profileId = profileSpec?.let { channel.id.removePrefix(it.baseId).removePrefix("_") }
            ?.ifEmpty { DEFAULT_PROFILE_ID } ?: DEFAULT_PROFILE_ID
        val name = spec.channelName(strings, profileId, profileName(context, strings, profileId))
        manager.refreshChannelLabel(channel, name, strings.getString(spec.descRes))
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun NotificationManager.refreshChannelLabel(channel: NotificationChannel, name: String, description: String) {
    if (channel.name.toString() == name && channel.description == description) return
    channel.name = name
    channel.description = description
    createNotificationChannel(channel)
}

/** Updates the owning groups and channel labels in place. */
fun renameProfileChannels(context: Context, profileId: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val strings = ContextCompat.getContextForLanguage(context).resources
    val name = profileName(context, strings, profileId)
    PROFILE_CHANNEL_SPECS.distinctBy { it.appGroup }.forEach {
        it.ensureProfileGroup(manager, strings, profileId, name)
    }
    PROFILE_CHANNEL_SPECS.forEach { spec ->
        val channel = manager.getNotificationChannel(channelId(spec.baseId, profileId)) ?: return@forEach
        manager.refreshChannelLabel(channel, spec.channelName(strings, profileId, name), strings.getString(spec.descRes))
    }
}

/**
 * Cancels every active notification posted on the channels of [profileId] — channel
 * by channel, never by tag: the non-ongoing ones (Rewind, Listen Together, sleep timer) survive a process
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
 * pending notifications ([cancelProfileNotifications]) and deletes its channels, so a deleted
 * profile leaves no orphan channel (or surviving notification) behind in the system. Never
 * touches the base profile's startup-managed channels.
 */
fun deleteProfileChannels(context: Context, profileId: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    if (profileId == DEFAULT_PROFILE_ID) return
    cancelProfileNotifications(context, profileId)
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    profileChannelIds(profileId).forEach { notificationManager.deleteNotificationChannel(it) }
    PROFILE_CHANNEL_SPECS.map { it.groupId(profileId) }.distinct().forEach(notificationManager::deleteNotificationChannelGroup)
    Timber.tag(TAG).d("Deleted notification channels for profile '%s'", profileId)
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
