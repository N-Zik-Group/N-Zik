package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class GlassFreshInstallDefaultTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `fresh profile starts off while saved opt in and opt out remain authoritative`() {
        val prefs = ApplicationProvider.getApplicationContext<Application>().preferences
        prefs.edit().remove(glassEnabledKey).commit()
        var enabled = true
        compose.setContent {
            val settings = rememberGlassSettings()
            SideEffect { enabled = settings.enabled }
        }
        compose.runOnIdle {
            assertFalse(enabled)
            assertFalse("Reading the default must not seed or migrate a saved choice", prefs.contains(glassEnabledKey))
            prefs.edit().putBoolean(glassEnabledKey, true).commit()
        }
        compose.runOnIdle { assertTrue(enabled); prefs.edit().putBoolean(glassEnabledKey, false).commit() }
        compose.runOnIdle { assertFalse(enabled); prefs.edit().remove(glassEnabledKey).commit() }
        compose.runOnIdle { assertFalse(enabled) }
    }
}
