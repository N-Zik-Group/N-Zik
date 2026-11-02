package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.it.fast4x.rimusic.utils.thumbnailSizeDpKey
import app.it.fast4x.rimusic.utils.thumbnailSizeLDpKey
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
class PlayerLandscapeArtworkPreferenceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun setup() {
        setActiveProfile("default", context)
        context.preferences.edit().clear().commit()
    }

    @After fun cleanup() {
        context.preferences.edit().clear().commit()
        setActiveProfile("default", context)
        context.preferences.edit().clear().commit()
    }

    @Test fun `player and settings follow portrait until explicitly overridden including zero`() {
        val preferences = context.preferences
        preferences.edit().putFloat(thumbnailSizeDpKey, 100f).commit()
        lateinit var setting: MutableState<Float>
        var rendered = Float.NaN
        var slider = Float.NaN
        compose.setContent {
            val playerSize = rememberPlayerLandscapeArtworkSize().value
            setting = rememberPlayerLandscapeArtworkSize()
            val settingSize = setting.value
            SideEffect { rendered = playerSize; slider = settingSize }
        }
        fun assertSizes(expected: Float, overridden: Boolean) {
            compose.runOnIdle {
                assertEquals(expected, rendered, 0f)
                assertEquals(expected, slider, 0f)
                assertEquals(overridden, preferences.contains(thumbnailSizeLDpKey))
            }
        }
        assertSizes(100f, false)
        compose.runOnIdle { preferences.edit().putFloat(thumbnailSizeDpKey, 65f).commit() }
        assertSizes(65f, false)
        // Choosing the currently inherited size must still create an explicit override.
        compose.runOnIdle { setting.value = 65f }
        compose.runOnIdle { preferences.edit().putFloat(thumbnailSizeDpKey, 20f).commit() }
        assertSizes(65f, true)
        compose.runOnIdle { setting.value = 0f }
        compose.runOnIdle { preferences.edit().putFloat(thumbnailSizeDpKey, 100f).commit() }
        assertSizes(0f, true)
        compose.runOnIdle { preferences.edit().remove(thumbnailSizeLDpKey).commit() }
        assertSizes(100f, false)
        compose.runOnIdle { preferences.edit().clear().commit() }
        assertSizes(90f, false)
        compose.runOnIdle { assertFalse(preferences.contains(thumbnailSizeDpKey)) }
    }

    @Test fun `recomposing for another profile detaches old store and writes only the new one`() {
        val original = context.preferences
        original.edit().putFloat(thumbnailSizeDpKey, 100f).commit()
        val revision = mutableStateOf(0)
        lateinit var setting: MutableState<Float>
        var rendered = Float.NaN
        var observedRevision = -1
        compose.setContent {
            val currentRevision = revision.value
            setting = rememberPlayerLandscapeArtworkSize()
            val size = setting.value
            SideEffect { rendered = size; observedRevision = currentRevision }
        }
        compose.runOnIdle { assertEquals(100f, rendered, 0f) }
        compose.runOnIdle {
            setActiveProfile("landscape-size-test", context)
            context.preferences.edit().clear().putFloat(thumbnailSizeDpKey, 45f).commit()
            revision.value += 1
        }
        compose.runOnIdle {
            assertEquals(1, observedRevision)
            assertEquals(45f, rendered, 0f)
        }
        compose.runOnIdle { original.edit().putFloat(thumbnailSizeDpKey, 10f).commit() }
        compose.runOnIdle {
            assertEquals(45f, rendered, 0f)
            setting.value = 25f
        }
        compose.runOnIdle {
            assertEquals(25f, rendered, 0f)
            assertEquals(25f, context.preferences.getFloat(thumbnailSizeLDpKey, -1f), 0f)
            assertFalse(original.contains(thumbnailSizeLDpKey))
        }
    }
}
