package app.n_zik.android.core.notifications

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.utils.saveProfileDisplayName
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.utils.writeProfileEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AppNotificationChannelsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun `startup registers every app channel before a notification is sent`() {
        installAppNotificationChannels(context, context.resources)

        val channels = manager.notificationChannels.associateBy { it.id }
        assertEquals(
            setOf(
                "default_channel_id", "sleep_timer_channel_id", "download_channel", "rewind",
                "listen_together_channel", "song_match_channel", "bridge_server_channel", "update_channel",
                "module_download_channel", "theme_store_creator_submissions", "theme_store_publications",
                "theme_store_downloads", "theme_store_preview_generation",
            ),
            channels.keys,
        )
        assertEquals(0, manager.activeNotifications.size)
        val groupedIds = channels.values.groupBy({ it.group }, { it.id }).mapValues { it.value.toSet() }
        assertEquals(
            mapOf(
                "nzik_profile_default" to setOf(
                    "default_channel_id", "sleep_timer_channel_id", "download_channel", "rewind", "listen_together_channel",
                ),
                "nzik_tools_updates" to setOf("song_match_channel", "bridge_server_channel", "update_channel", "module_download_channel"),
                "nzik_theme_store" to setOf(
                    "theme_store_creator_submissions", "theme_store_publications", "theme_store_downloads", "theme_store_preview_generation",
                ),
            ),
            groupedIds,
        )
        assertTrue(channels.values.all { !it.name.isNullOrBlank() && !it.description.isNullOrBlank() })
        assertEquals(channels.size, channels.values.map { it.name.toString() }.toSet().size)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channels["listen_together_channel"]?.importance)
        assertEquals(
            context.getString(R.string.listen_together_notification_channel_description),
            channels["listen_together_channel"]?.description,
        )
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channels["theme_store_publications"]?.importance)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channels["theme_store_downloads"]?.importance)
    }

    @Test
    fun `startup refreshes existing labels and keeps notification preferences`() {
        manager.createNotificationChannel(
            NotificationChannel(
                "theme_store_publications", "Old name", NotificationManager.IMPORTANCE_NONE,
            ).apply { setShowBadge(false) },
        )

        installAppNotificationChannels(context, context.resources)
        ensureStartupNotificationChannel(context, THEME_STORE_PUBLICATIONS_CHANNEL_ID, "default")

        val channel = manager.getNotificationChannel("theme_store_publications")
        assertNotNull(channel)
        assertEquals(context.getString(R.string.notification_channel_theme_publications), channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_NONE, channel.importance)
        assertFalse(channel.canShowBadge())
        assertEquals("nzik_theme_store", channel.group)
    }

    @Test
    fun `startup registers isolated channels for active and inactive saved profiles`() {
        val profiles = listOf("work" to "Work", "home" to "Home")
        assertTrue(context.writeProfileEntries(profiles))
        profiles.forEach { (id, name) -> context.saveProfileDisplayName(id, name) }
        setActiveProfile("work", context)

        installAppNotificationChannels(context, context.resources)

        val channels = manager.notificationChannels.associateBy { it.id }
        val profiledIds = listOf(
            "default_channel_id", "sleep_timer_channel_id", "download_channel", "rewind", "listen_together_channel",
        )
        val themeIds = listOf(
            "theme_store_creator_submissions", "theme_store_publications", "theme_store_downloads", "theme_store_preview_generation",
        )
        for ((profileId, profileName) in profiles) {
            val expectedIds = (profiledIds + themeIds).mapTo(mutableSetOf()) { "${it}_$profileId" }
            assertEquals(expectedIds, channels.keys.filterTo(mutableSetOf()) { it.endsWith("_$profileId") })
            for (baseId in profiledIds + themeIds) {
                val base = channels.getValue(baseId)
                val scoped = channels.getValue("${baseId}_$profileId")
                assertEquals(
                    context.getString(R.string.profile_channel_name, base.name, profileName),
                    scoped.name.toString(),
                )
                assertEquals(base.description, scoped.description)
                assertEquals(base.importance, scoped.importance)
                assertEquals(base.canShowBadge(), scoped.canShowBadge())
                val expectedGroup = if (baseId in themeIds) "nzik_theme_store_$profileId" else "nzik_profile_$profileId"
                assertEquals(expectedGroup, scoped.group)
            }
            val groups = manager.notificationChannelGroups.associate { it.id to it.name.toString() }
            assertEquals(context.getString(R.string.profile_channel_name, context.getString(R.string.notification_group_audio), profileName), groups["nzik_profile_$profileId"])
            assertEquals(context.getString(R.string.profile_channel_name, context.getString(R.string.theme_store_title), profileName), groups["nzik_theme_store_$profileId"])
        }
        assertEquals(31, channels.size)
        assertTrue(channels.containsKey("theme_store_publications"))
        assertTrue(channels.containsKey("bridge_server_channel"))
        assertEquals(0, manager.activeNotifications.size)
    }

    @Test
    fun `startup leaves an already assigned channel group in place`() {
        manager.createNotificationChannelGroup(NotificationChannelGroup("original_group", "Original group"))
        manager.createNotificationChannel(
            NotificationChannel("update_channel", "Old name", NotificationManager.IMPORTANCE_HIGH).apply {
                group = "original_group"
            },
        )

        installAppNotificationChannels(context, context.resources)

        val channel = manager.getNotificationChannel("update_channel")
        assertEquals("original_group", channel.group)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    @Test
    fun `early registration creates only the requested channels and their groups`() {
        ensureStartupNotificationChannel(context, THEME_STORE_DOWNLOADS_CHANNEL_ID, "work")
        ensureStartupNotificationChannel(context, "download_channel", "work")

        assertEquals(setOf("nzik_theme_store_work", "nzik_profile_work"), manager.notificationChannelGroups.map { it.id }.toSet())
        assertEquals("nzik_theme_store_work", manager.getNotificationChannel("${THEME_STORE_DOWNLOADS_CHANNEL_ID}_work").group)
        assertEquals("nzik_profile_work", manager.getNotificationChannel("download_channel_work").group)
        assertNull(manager.getNotificationChannel("download_channel"))
    }
}
