package app.n_zik.android.core.migration

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.utils.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Covers the I/O matrix rows of the "remove Advanced notification" spec that concern stored values: an old `notificationType=Advanced`, an old enabled wallpaper, and a repeated run. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class RemovedSettingsMigrationTest {

    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Application>()
            .getSharedPreferences("removed-settings-migration-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun `stored Advanced notification type is removed`() {
        prefs.edit().putString("notificationType", "Advanced").commit()

        assertTrue(RemovedSettingsMigration.run(prefs))

        assertFalse(prefs.contains("notificationType"))
    }

    @Test
    fun `stored wallpaper settings are removed`() {
        prefs.edit()
            .putBoolean("enableWallpaper", true)
            .putString("wallpaperType", "Both")
            .commit()

        assertTrue(RemovedSettingsMigration.run(prefs))

        assertFalse(prefs.contains("enableWallpaper"))
        assertFalse(prefs.contains("wallpaperType"))
    }

    @Test
    fun `other preferences are left untouched`() {
        prefs.edit()
            .putString("notificationType", "Advanced")
            .putString("notificationPlayerFirstIcon", "Download")
            .putBoolean("enablePicturInPicture", true)
            .commit()

        RemovedSettingsMigration.run(prefs)

        assertEquals("Download", prefs.getString("notificationPlayerFirstIcon", null))
        assertTrue(prefs.getBoolean("enablePicturInPicture", false))
    }

    @Test
    fun `running twice removes nothing the second time`() {
        prefs.edit().putString("notificationType", "Advanced").commit()

        assertTrue(RemovedSettingsMigration.run(prefs))
        assertFalse(RemovedSettingsMigration.run(prefs))
    }

    @Test
    fun `nothing to migrate returns false and writes nothing`() {
        prefs.edit().putString("someOtherKey", "value").commit()

        assertFalse(RemovedSettingsMigration.run(prefs))

        assertEquals(mapOf<String, Any?>("someOtherKey" to "value"), prefs.all)
    }
}
