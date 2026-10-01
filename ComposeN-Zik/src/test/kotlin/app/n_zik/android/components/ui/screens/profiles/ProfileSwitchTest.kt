package app.n_zik.android.components.ui.screens.profiles

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.core.notifications.profileChannelIds
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the profile switch wiring (spec per-profile-notifications, CAP-3 — the call-sites, not
 * the helpers): [executeProfileSwitch] cancels the notifications pending on the channels of the
 * profile LEFT (captured before the switch, so the destination can never drift into the cancel
 * target), moves the active slot, and ensures the incoming profile's 6 channels exist.
 *
 * The process exit is asserted NOT to happen here on purpose: it lives in the dialog's
 * onConfirm (the test JVM would die otherwise — Robolectric does not intercept System.exit).
 *
 * Robolectric provides the real framework plumbing (NotificationManager, SharedPreferences).
 * The encrypted-prefs commit is stubbed — EncryptedSharedPreferences needs the AndroidKeyStore,
 * which is unavailable on the Robolectric JVM (the documented constraint, see
 * RewindPlaylistWorkersTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProfileSwitchTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `switching profiles cancels the left profile notifications and ensures the incoming channels`() {
        mockkStatic("app.it.fast4x.rimusic.utils.EncryptedPreferencesKt")
        every { context.encryptedPreferences } returns
            context.getSharedPreferences("secure_preferences", Context.MODE_PRIVATE)

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel("rewind_work", "Rewind", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel("rewind", "Rewind", NotificationManager.IMPORTANCE_LOW),
            ),
        )
        // A pending notification on the OUTGOING profile's channel — it survives a process
        // death, so the switch must cancel it — and one on the base channel, which survives.
        nm.notify(1, Notification.Builder(context, "rewind_work").build())
        nm.notify(2, Notification.Builder(context, "rewind").build())
        setActiveProfile("work", context)

        executeProfileSwitch("home", context)

        // The "work" notification is gone; the base one survives.
        assertEquals(setOf(2), nm.activeNotifications.orEmpty().map { it.id }.toSet())
        // The active slot moved to the destination…
        assertEquals("home", getActiveProfile(context))
        // …and the incoming profile's 6 channels exist before the (caller's) process exit.
        profileChannelIds("home").forEach { assertNotNull(nm.getNotificationChannel(it)) }
    }
}
