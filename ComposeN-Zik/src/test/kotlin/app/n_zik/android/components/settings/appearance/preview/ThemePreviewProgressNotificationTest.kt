package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PermissionInfo
import android.os.Looper
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 33], application = Application::class)
class ThemePreviewProgressNotificationTest {
    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun `install the AndroidX receiver permission declared by the real merged manifest`() {
        val permission = "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        shadowOf(context.packageManager).addPermissionInfo(PermissionInfo().apply {
            name = permission
            packageName = context.packageName
            protectionLevel = PermissionInfo.PROTECTION_SIGNATURE
        })
        shadowOf(context).grantPermissions(permission)
    }

    @Test
    fun `preparation and capture progress share a cancellable notification`() {
        val progress = ThemePreviewProgressNotification(context)
        var cancellations = 0
        progress.start { cancellations++ }
        val preparing = manager.activeNotifications.single().notification
        assertTrue(preparing.extras.getBoolean("android.progressIndeterminate"))
        assertEquals(1, preparing.actions.size)
        val action = preparing.actions.single().actionIntent
        val intent = shadowOf(action).savedIntent
        assertEquals(context.packageName, intent.`package`)
        assertTrue(shadowOf(action).isImmutable)

        progress.show(3, 10)
        val capturing = manager.activeNotifications.single().notification
        assertFalse(capturing.extras.getBoolean("android.progressIndeterminate"))
        assertEquals(3, capturing.extras.getInt("android.progress"))
        assertEquals(10, capturing.extras.getInt("android.progressMax"))
        action.send()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, cancellations)
        assertTrue(manager.activeNotifications.isEmpty())
        context.sendBroadcast(Intent(intent))
        shadowOf(Looper.getMainLooper()).idle()
        progress.cancel()
        assertEquals(1, cancellations)
    }

    @Test
    fun `old cancellation actions cannot affect retries or another session`() {
        val first = ThemePreviewProgressNotification(context)
        var firstCancelled = 0
        var retryCancelled = 0
        var otherCancelled = 0
        first.start { firstCancelled++ }
        val staleAction = manager.activeNotifications.single().notification.actions.single().actionIntent
        val staleBroadcast = Intent(shadowOf(staleAction).savedIntent)
        first.start { retryCancelled++ }
        val other = ThemePreviewProgressNotification(context)
        other.start { otherCancelled++ }
        assertThrows(PendingIntent.CanceledException::class.java) { staleAction.send() }
        context.sendBroadcast(staleBroadcast)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, firstCancelled)
        assertEquals(0, retryCancelled)
        assertEquals(0, otherCancelled)
        assertEquals(2, manager.activeNotifications.size)
        first.cancel()
        first.show(1, 2)
        assertEquals(1, manager.activeNotifications.size)
        other.cancel()
        assertTrue(manager.activeNotifications.isEmpty())
    }
}
