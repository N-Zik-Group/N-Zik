package app.n_zik.android.components.settings.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.it.fast4x.rimusic.utils.thumbnailSizeDpKey
import app.it.fast4x.rimusic.utils.thumbnailSizeLDpKey
import app.n_zik.android.components.player.rememberPlayerLandscapeArtworkSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class UiArtworkDefaultsTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        setActiveProfile("ui-artwork-reset", context)
        context.preferences.edit().clear().commit()
    }

    @After fun tearDown() {
        context.preferences.edit().clear().commit()
        setActiveProfile("default", context)
    }

    @Test fun `interface reset restores landscape inheritance for the mounted player and setting`() {
        val preferences = context.preferences
        val untouched = context.getSharedPreferences("preferences_ui-artwork-other", Context.MODE_PRIVATE)
        untouched.edit().clear().putFloat(thumbnailSizeLDpKey, 45f).commit()
        preferences.edit().putFloat(thumbnailSizeDpKey, 80f).putFloat(thumbnailSizeLDpKey, 20f).commit()
        var playerSize = Float.NaN
        var settingSize = Float.NaN
        compose.setContent {
            val player = rememberPlayerLandscapeArtworkSize().value
            val setting = rememberPlayerLandscapeArtworkSize().value
            SideEffect { playerSize = player; settingSize = setting }
        }
        compose.runOnIdle {
            assertEquals(20f, playerSize, 0f)
            assertEquals(20f, settingSize, 0f)
            applyUiDefaults(context)
        }
        compose.runOnIdle {
            assertEquals(90f, playerSize, 0f)
            assertEquals(90f, settingSize, 0f)
            assertFalse(preferences.contains(thumbnailSizeLDpKey))
            assertEquals(45f, untouched.getFloat(thumbnailSizeLDpKey, -1f), 0f)
            preferences.edit().putFloat(thumbnailSizeDpKey, 65f).commit()
        }
        compose.runOnIdle {
            assertEquals(65f, playerSize, 0f)
            assertEquals(65f, settingSize, 0f)
            assertFalse(preferences.contains(thumbnailSizeLDpKey))
        }
    }
}
