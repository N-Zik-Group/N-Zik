package app.n_zik.android.core.migration

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RetiredPresentationMigrationTest {
    @Test
    fun `every legacy presentation value is removed without applying player defaults`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (ui in listOf("ViMusic", "RiMusic", "N-Zik", "invalid")) {
            for (toast in listOf("Essential", "Modern", "invalid")) {
                val prefs = context.getSharedPreferences("retired-presentation", Context.MODE_PRIVATE)
                prefs.edit().clear().putString("UiType", ui).putString("messageType", toast)
                    .putString("playerType", "Essential").putBoolean("showSearchTab", false)
                    .putBoolean("persistentQueue", true).putString("accountName", "kept").commit()
                val expected = prefs.all.filterKeys { it != "UiType" && it != "messageType" }

                assertTrue("$ui / $toast", RemovedSettingsMigration.run(prefs))
                assertEquals(expected, prefs.all)
                assertFalse(RemovedSettingsMigration.run(prefs))
            }
        }
    }

    @Test
    fun `migration removes selectors regardless of stored value type`() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("retired-presentation-types", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("UiType", true).putInt("messageType", 42).commit()

        assertTrue(RemovedSettingsMigration.run(prefs))
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun `activating each profile cleans its selectors while retaining its independent values`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            for (profile in listOf("default", "retired_first", "retired_second")) {
                val name = if (profile == "default") "preferences" else "preferences_$profile"
                val stored = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                stored.edit().clear().putString("UiType", "ViMusic").putString("messageType", "Essential")
                    .putString("playerType", profile).putBoolean("showSearchTab", false).commit()
                setActiveProfile(profile, context)

                val active = context.preferences

                assertFalse(active.contains("UiType"))
                assertFalse(active.contains("messageType"))
                assertEquals(profile, active.getString("playerType", null))
                assertFalse(active.getBoolean("showSearchTab", true))
                assertFalse(RemovedSettingsMigration.run(active))
            }
        } finally {
            setActiveProfile("default", context)
        }
    }
}
