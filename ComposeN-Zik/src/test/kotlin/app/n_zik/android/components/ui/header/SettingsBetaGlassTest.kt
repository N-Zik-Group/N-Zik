package app.n_zik.android.components.ui.header

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsBetaGlassTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `settings beta gains the shared glass rim without changing its native footprint`() {
        val settings = mutableStateOf(GlassSettings(enabled = false, quality = GlassQuality.Performance))
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                Typography.fallback, CircleShape, CircleShape, CircleShape)) {
                Box(Modifier.background(Color.Black)) {
                    HeaderBadgeBox("BETA", Color.Blue, modifier = Modifier.testTag("beta"),
                        glassSettings = settings.value)
                }
            }
        }
        fun rim(): Color {
            val pixels = compose.onNodeWithTag("beta").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, 0]
        }
        val bounds = compose.onNodeWithTag("beta").fetchSemanticsNode().boundsInRoot
        val native = rim()
        compose.runOnIdle { settings.value = settings.value.copy(enabled = true) }
        assertEquals(bounds, compose.onNodeWithTag("beta").fetchSemanticsNode().boundsInRoot)
        assertTrue("the beta badge must receive the shared light rim", rim().luminance() > native.luminance())
        compose.runOnIdle { settings.value = settings.value.copy(enabled = false) }
        assertEquals(native, rim())
        assertEquals(bounds, compose.onNodeWithTag("beta").fetchSemanticsNode().boundsInRoot)
    }
}
