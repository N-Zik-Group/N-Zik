package app.n_zik.android.listentogether

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

import app.n_zik.android.R
import app.n_zik.android.core.notifications.ensureStartupNotificationChannel
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.setActiveProfile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ListenTogetherNotificationsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifications = ListenTogetherNotifications(context)

    @Test
    fun `join request actions identify and cancel the correct notification`() {
        val channel = grantNotifications()
        val id = "guest-123"
        notifications.showJoinRequest(JoinRequestPayload.newBuilder().setUserId(id).setUsername("Guest").build())

        val posted = manager.activeNotifications.single()
        assertEquals(channel, posted.notification.channelId)
        assertEquals(
            context.getString(R.string.listen_together_join_request_notification, "Guest"),
            posted.notification.extras.getCharSequence(Notification.EXTRA_TEXT),
        )
        assertActions(
            posted.notification,
            posted.id,
            ListenTogetherClient.EXTRA_USER_ID,
            id,
            ListenTogetherClient.ACTION_APPROVE_JOIN,
            ListenTogetherClient.ACTION_REJECT_JOIN,
        )
        notifications.cancelSuggestion(id)
        assertEquals(posted.id, manager.activeNotifications.single().id)
        notifications.cancelJoinRequest(id)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun `suggestion uses the active profile and preserves its action intents`() {
        setActiveProfile("work", context)
        val channel = grantNotifications()
        val id = "suggestion-456"
        notifications.showSuggestion(
            SuggestionReceivedPayload.newBuilder().setSuggestionId(id).setFromUsername("Guest").build(),
        )

        val posted = manager.activeNotifications.single()
        assertEquals("${ListenTogetherClient.NOTIFICATION_CHANNEL_ID}_work", channel)
        assertEquals(channel, posted.notification.channelId)
        assertEquals(
            context.getString(R.string.listen_together_suggestion_received, "Guest", context.getString(R.string.metadata_unknown_title)),
            posted.notification.extras.getCharSequence(Notification.EXTRA_TEXT),
        )
        assertActions(
            posted.notification,
            posted.id,
            ListenTogetherClient.EXTRA_SUGGESTION_ID,
            id,
            ListenTogetherClient.ACTION_APPROVE_SUGGESTION,
            ListenTogetherClient.ACTION_REJECT_SUGGESTION,
        )
        notifications.cancelJoinRequest(id)
        assertEquals(posted.id, manager.activeNotifications.single().id)
        notifications.cancelSuggestion(id)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    private fun grantNotifications(): String {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        return ensureStartupNotificationChannel(
            context, ListenTogetherClient.NOTIFICATION_CHANNEL_ID, getActiveProfile(context),
        )
    }

    private fun assertActions(
        notification: Notification,
        notificationId: Int,
        key: String,
        id: String,
        approve: String,
        reject: String,
    ) {
        val actions = notification.actions.map { shadowOf(it.actionIntent) }
        assertEquals(listOf(approve, reject), actions.map { it.savedIntent.action })
        assertEquals(listOf(id.hashCode(), id.hashCode().inv()), actions.map { it.requestCode })
        for (action in actions) {
            assertEquals(ListenTogetherActionReceiver::class.java.name, action.savedIntent.component?.className)
            assertEquals(id, action.savedIntent.getStringExtra(key))
            assertEquals(notificationId, action.savedIntent.getIntExtra(ListenTogetherClient.EXTRA_NOTIFICATION_ID, -1))
            assertTrue(action.isImmutable)
        }
    }
}
