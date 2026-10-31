package app.n_zik.android.components.settings.general

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GeneralDefaultsTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val expected: Map<String, Any> = mapOf(
        "pauseOnHeadphoneDisconnect" to true,
        "excludeDislikedSongs" to "Enabled",
        "excludeDislikedArtists" to "Enabled",
        "excludeDislikedAlbums" to "Enabled",
    )

    @Before fun setUp() {
        setActiveProfile("general-defaults-test", context)
        context.preferences.edit().clear().commit()
        context.getSharedPreferences("preferences_general-untouched", Context.MODE_PRIVATE)
            .edit().clear().putBoolean("pauseOnHeadphoneDisconnect", false).commit()
    }

    @After fun tearDown() {
        context.preferences.edit().clear().commit()
        setActiveProfile("default", context)
    }

    @Test fun `reset restores playback safety and dislike filters only in the active profile repeatedly`() {
        val resetGeneration = mutableIntStateOf(0)
        fun seedCustomPlayback() {
            context.preferences.edit()
                .putBoolean("pauseOnHeadphoneDisconnect", false)
                .putString("excludeDislikedSongs", "Disabled")
                .putString("excludeDislikedArtists", "Disabled")
                .putString("excludeDislikedAlbums", "Disabled")
                .putString("unrelated", "keep")
                .commit()
        }
        seedCustomPlayback()
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                key(resetGeneration.intValue) { DefaultGeneralSettings(context) }
            }
        }
        repeat(2) { iteration ->
            composeRule.waitForIdle()
            expected.forEach { (name, value) -> assertEquals(name, value, context.preferences.all[name]) }
            assertEquals("keep", context.preferences.getString("unrelated", null))
            assertEquals(
                mapOf("pauseOnHeadphoneDisconnect" to false),
                context.getSharedPreferences("preferences_general-untouched", Context.MODE_PRIVATE).all,
            )
            if (iteration == 0) {
                composeRule.runOnIdle {
                    seedCustomPlayback()
                    resetGeneration.intValue++
                }
            }
        }
    }
}
