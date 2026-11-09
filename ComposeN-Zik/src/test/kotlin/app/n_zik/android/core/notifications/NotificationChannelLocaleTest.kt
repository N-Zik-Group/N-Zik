package app.n_zik.android.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationChannelLocaleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `locale refresh renames existing channels without changing their settings`() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val bridgeId = "bridge_server_channel"
        manager.createNotificationChannel(NotificationChannel(bridgeId, "PC server", NotificationManager.IMPORTANCE_HIGH))
        val localized = context.createConfigurationContext(
            android.content.res.Configuration(context.resources.configuration).apply {
                setLocale(java.util.Locale.FRENCH)
            },
        ).resources

        refreshNotificationChannelLabels(context, localized, manager)

        val channel = manager.getNotificationChannel(bridgeId)
        assertEquals(localized.getString(R.string.bridge_server), channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertNull(manager.getNotificationChannel("update_channel"))
    }

    @Test
    fun `early startup uses the profiled label without rewriting an existing channel`() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val baseId = "sync_channel_id"
        val id = ensureStartupNotificationChannel(context, baseId, "work")
        val created = manager.getNotificationChannel(id)
        assertEquals("sync_channel_id_work", id)
        assertEquals(
            context.getString(R.string.profile_channel_name, context.getString(R.string.sync_action_sync), "work"),
            created.name.toString(),
        )
        assertEquals(context.getString(R.string.sync_notifications), created.description)

        created.name = "Existing profile label"
        manager.createNotificationChannel(created)
        assertEquals(id, ensureStartupNotificationChannel(context, baseId, "work"))
        assertEquals("Existing profile label", manager.getNotificationChannel(id).name.toString())
    }
}
