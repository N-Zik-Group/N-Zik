package app.n_zik.android

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.PROFILE_NAMES_FILE_NAME
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.core.profiles.selectColdStorageProfile
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityBoundProfileShortcutTest {
    @Test
    fun `a cold shortcut hands off instead of switching the preferences of a bound process`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setActiveProfile(DEFAULT_PROFILE_ID, context)
        context.filesDir.resolve(PROFILE_NAMES_FILE_NAME).writeText("work\tWork\n")
        val activity = Robolectric.buildActivity(MainActivity::class.java, Intent(Intent.ACTION_MAIN)).create().get()
        val shortcut = Intent(MainActivity.action_profile).putExtra(MainActivity.EXTRA_PROFILE_ID, "work")
        mockkStatic(::selectColdStorageProfile)
        try {
            every { selectColdStorageProfile(activity, "work") } returns false
            var handoff: String? = null
            activity.consumeProfileShortcut(shortcut) { handoff = it }
            assertEquals("work", handoff)
            assertEquals(DEFAULT_PROFILE_ID, getActiveProfile(activity))
            assertFalse(shortcut.hasExtra(MainActivity.EXTRA_PROFILE_ID))
        } finally {
            unmockkStatic(::selectColdStorageProfile)
        }
    }
}
