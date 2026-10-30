package app.n_zik.android.components.ui.screens.easter

import android.app.Application
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import app.n_zik.android.components.glass.GlassHighlightIndication
import app.n_zik.android.components.glass.GlassRippleProvider
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.LocalGlassRippleActive
import app.n_zik.android.components.glass.rememberGlassSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class EasterNativeContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `easter excludes material rim and Glass feedback without changing surrounding settings`() {
        var innerEnabled = true
        var innerRipple = true
        var innerHighlight = true
        var outerEnabled = false
        compose.setContent {
            val settings = GlassSettings(enabled = true)
            CompositionLocalProvider(LocalGlassPreviewSettings provides settings) {
                GlassRippleProvider(settings) {
                    EasterNativeContent {
                        val current = rememberGlassSettings()
                        val ripple = LocalGlassRippleActive.current
                        val indication = LocalIndication.current
                        SideEffect {
                            innerEnabled = current.enabled
                            innerRipple = ripple
                            innerHighlight = indication is GlassHighlightIndication
                        }
                    }
                    val outer = rememberGlassSettings()
                    SideEffect { outerEnabled = outer.enabled }
                }
            }
        }
        compose.runOnIdle {
            assertFalse(innerEnabled)
            assertFalse(innerRipple)
            assertFalse(innerHighlight)
            assertTrue(outerEnabled)
        }
    }
}
