package app.n_zik.android.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the per-profile channel helper (spec per-profile-notifications, goal ⑩): the [channelId]
 * resolution (base unchanged / suffixed, no double suffix), the channel display names (the
 * base channel's own name — the profile is not shown in the name), the cancel selection (pure)
 * and [ensureProfileChannels] (Robolectric: the 6 channels with faithful specs, base no-op,
 * idempotent) and [deleteProfileChannels] (the deletion purge — the profile's notifications
 * cancelled, its channels deleted, the base channels untouched).
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine on the
 * JUnit 5 platform (the Robolectric runner only works with JUnit 4) — the project's established
 * pattern for Robolectric tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProfileNotificationChannelsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `channelId keeps the base profile ids unchanged`() {
        assertEquals("default_channel_id", channelId("default_channel_id", DEFAULT_PROFILE_ID))
        assertEquals("sleep_timer_channel_id", channelId("sleep_timer_channel_id", DEFAULT_PROFILE_ID))
        assertEquals("download_channel", channelId("download_channel", DEFAULT_PROFILE_ID))
        assertEquals("sync_channel_id", channelId("sync_channel_id", DEFAULT_PROFILE_ID))
        assertEquals("rewind", channelId("rewind", DEFAULT_PROFILE_ID))
        assertEquals("listen_together_channel", channelId("listen_together_channel", DEFAULT_PROFILE_ID))
    }

    @Test
    fun `channelId suffixes the user profile ids exactly once`() {
        // The base ids already contain underscores — the profile suffix must not double them.
        assertEquals("default_channel_id_work", channelId("default_channel_id", "work"))
        assertEquals("sleep_timer_channel_id_work", channelId("sleep_timer_channel_id", "work"))
        assertEquals("download_channel_work", channelId("download_channel", "work"))
        assertEquals("sync_channel_id_work", channelId("sync_channel_id", "work"))
        assertEquals("rewind_work", channelId("rewind", "work"))
        assertEquals("listen_together_channel_work", channelId("listen_together_channel", "work"))
    }

    @Test
    fun `profileChannelIds holds the six suffixed ids of a user profile`() {
        assertEquals(
            setOf(
                "default_channel_id_work",
                "sleep_timer_channel_id_work",
                "download_channel_work",
                "sync_channel_id_work",
                "rewind_work",
                "listen_together_channel_work",
            ),
            profileChannelIds("work"),
        )
    }

    @Test
    fun `profileChannelIds of the base profile holds the six current ids`() {
        assertEquals(
            setOf(
                "default_channel_id",
                "sleep_timer_channel_id",
                "download_channel",
                "sync_channel_id",
                "rewind",
                "listen_together_channel",
            ),
            profileChannelIds(DEFAULT_PROFILE_ID),
        )
    }

    @Test
    fun `notificationsOnChannels selects only the notifications on the profile channels`() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel("rewind_work", "Rewind", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel("sync_channel_id_work", "Sync", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel("rewind", "Rewind", NotificationManager.IMPORTANCE_LOW), // base channel
                NotificationChannel("update", "Update", NotificationManager.IMPORTANCE_LOW), // global channel
            ),
        )
        nm.notify(1, Notification.Builder(context, "rewind_work").build())
        nm.notify(2, Notification.Builder(context, "sync_channel_id_work").build())
        nm.notify(3, Notification.Builder(context, "rewind").build())
        nm.notify(4, Notification.Builder(context, "update").build())

        val selected = notificationsOnChannels(nm.activeNotifications, profileChannelIds("work"))

        // The "work" notifications are selected; the base ("rewind") and global ("update") survive.
        assertEquals(setOf(1, 2), selected.map { it.id }.toSet())
    }

    @Test
    fun `notificationsOnChannels preserves the base channels for the base profile`() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel("default_channel_id", "Player", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel("default_channel_id_work", "Player", NotificationManager.IMPORTANCE_LOW),
            ),
        )
        nm.notify(1, Notification.Builder(context, "default_channel_id").build())
        nm.notify(2, Notification.Builder(context, "default_channel_id_work").build())

        val selected = notificationsOnChannels(nm.activeNotifications, profileChannelIds(DEFAULT_PROFILE_ID))

        // Only the base-channel notification is selected; the suffixed one survives.
        assertEquals(listOf(1), selected.map { it.id })
    }

    @Test
    fun `ensureProfileChannels creates the six profile channels with faithful specs`() {
        ensureProfileChannels(context, "work")

        val nm = context.getSystemService(NotificationManager::class.java)

        val player = nm.getNotificationChannel("default_channel_id_work")
        assertNotNull(player)
        assertEquals(NotificationManager.IMPORTANCE_LOW, player?.importance)
        assertFalse(player?.canShowBadge() == true)
        assertEquals(context.getString(R.string.player), player?.name)
        assertEquals(context.getString(R.string.player), player?.description)

        val sleepTimer = nm.getNotificationChannel("sleep_timer_channel_id_work")
        assertNotNull(sleepTimer)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, sleepTimer?.importance)
        assertFalse(sleepTimer?.canShowBadge() == true)
        assertEquals(context.getString(R.string.sleep_timer), sleepTimer?.name)
        assertEquals(context.getString(R.string.sleep_timer), sleepTimer?.description)

        val download = nm.getNotificationChannel("download_channel_work")
        assertNotNull(download)
        assertEquals(NotificationManager.IMPORTANCE_LOW, download?.importance)
        assertFalse(download?.canShowBadge() == true)
        assertEquals(context.getString(R.string.download), download?.name)
        assertEquals(context.getString(R.string.download), download?.description)

        val sync = nm.getNotificationChannel("sync_channel_id_work")
        assertNotNull(sync)
        assertEquals(NotificationManager.IMPORTANCE_LOW, sync?.importance)
        assertFalse(sync?.canShowBadge() == true)
        assertEquals(context.getString(R.string.sync), sync?.name)
        assertEquals(context.getString(R.string.sync_notifications), sync?.description)

        val rewind = nm.getNotificationChannel("rewind_work")
        assertNotNull(rewind)
        assertEquals(NotificationManager.IMPORTANCE_LOW, rewind?.importance)
        assertFalse(rewind?.canShowBadge() == true)
        assertEquals(context.getString(R.string.rw_channel), rewind?.name)
        assertEquals(context.getString(R.string.rw_channel), rewind?.description)

        val listenTogether = nm.getNotificationChannel("listen_together_channel_work")
        assertNotNull(listenTogether)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, listenTogether?.importance)
        assertTrue(listenTogether?.canShowBadge() == true) // the badge is NOT disabled (default)
        assertEquals(context.getString(R.string.listen_together_notification_channel_name), listenTogether?.name)
        assertEquals(
            context.getString(R.string.listen_together_notification_channel_desc),
            listenTogether?.description,
        )
    }

    @Test
    fun `ensureProfileChannels is a no-op for the base profile`() {
        ensureProfileChannels(context, DEFAULT_PROFILE_ID)

        val nm = context.getSystemService(NotificationManager::class.java)
        // The base channels are MainApplication's (unchanged) — not created here…
        profileChannelIds(DEFAULT_PROFILE_ID).forEach { assertNull(nm.getNotificationChannel(it)) }
        // …and no suffixed channel either.
        profileChannelIds("work").forEach { assertNull(nm.getNotificationChannel(it)) }
    }

    @Test
    fun `ensureProfileChannels is idempotent`() {
        ensureProfileChannels(context, "work")
        val nm = context.getSystemService(NotificationManager::class.java)
        val first = nm.getNotificationChannel("rewind_work")
        assertNotNull(first)

        // The second call must not throw and must not modify the channel.
        ensureProfileChannels(context, "work")

        val second = nm.getNotificationChannel("rewind_work")
        assertNotNull(second)
        assertEquals(first?.importance, second?.importance)
        assertEquals(first?.name, second?.name)
        assertEquals(first?.canShowBadge(), second?.canShowBadge())
    }

    @Test
    fun `cancelProfileNotifications cancels the pending notifications of the profile only`() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel("rewind", "Rewind", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel("rewind_work", "Rewind", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel("sync_channel_id", "Sync", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel("sync_channel_id_work", "Sync", NotificationManager.IMPORTANCE_LOW),
            ),
        )
        nm.notify(1, Notification.Builder(context, "rewind").build())
        nm.notify(2, Notification.Builder(context, "rewind_work").build())
        nm.notify(3, Notification.Builder(context, "sync_channel_id").build())
        nm.notify(4, Notification.Builder(context, "sync_channel_id_work").build())
        // Tagged: id 4 also carries a TAGGED base-channel notification — the cancel must
        // target the (tag, id) pair, never the bare id (a bare cancel(4) on real Android only
        // removes the untagged notification).
        nm.notify("base_tag", 4, Notification.Builder(context, "sync_channel_id").build())
        nm.notify("profile_tag", 4, Notification.Builder(context, "sync_channel_id_work").build())

        cancelProfileNotifications(context, "work")

        // The notifications on the "work" channels (tagged or not) are gone, the base ones
        // survive — including the tagged one that shares id 4 with a cancelled notification.
        val remaining = nm.activeNotifications.orEmpty().map { it.tag to it.id }
        assertEquals(setOf(null to 1, null to 3, "base_tag" to 4), remaining.toSet())
    }

    @Test
    fun `deleteProfileChannels purges the profile notifications and channels only`() {
        val nm = context.getSystemService(NotificationManager::class.java)
        // The base channels (boot-managed) and the profile's suffixed channels all exist.
        nm.createNotificationChannels(
            (profileChannelIds(DEFAULT_PROFILE_ID) + profileChannelIds("work")).map {
                NotificationChannel(it, it, NotificationManager.IMPORTANCE_LOW)
            },
        )
        nm.notify(1, Notification.Builder(context, "rewind").build())
        nm.notify(2, Notification.Builder(context, "rewind_work").build())
        nm.notify(3, Notification.Builder(context, "sync_channel_id").build())
        nm.notify(4, Notification.Builder(context, "sync_channel_id_work").build())

        deleteProfileChannels(context, "work")

        // The "work" notifications are gone and its channels are deleted…
        assertEquals(setOf(1, 3), nm.activeNotifications.orEmpty().map { it.id }.toSet())
        profileChannelIds("work").forEach { assertNull(nm.getNotificationChannel(it)) }
        // …while the base channels (boot-managed) and their notifications survive.
        profileChannelIds(DEFAULT_PROFILE_ID).forEach { assertNotNull(nm.getNotificationChannel(it)) }
    }

    @Test
    fun `deleteProfileChannels never touches the base profile`() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel("rewind", "Rewind", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel("rewind_work", "Rewind", NotificationManager.IMPORTANCE_LOW),
            ),
        )
        nm.notify(1, Notification.Builder(context, "rewind").build())

        deleteProfileChannels(context, DEFAULT_PROFILE_ID)

        // The base is never deleted and its channels are boot-managed: nothing is touched
        // (not even the user profile's channels, which are not this call's target).
        assertEquals(setOf(1), nm.activeNotifications.orEmpty().map { it.id }.toSet())
        assertNotNull(nm.getNotificationChannel("rewind"))
        assertNotNull(nm.getNotificationChannel("rewind_work"))
    }
}
