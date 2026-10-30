package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
// Robolectric native Skia cannot compile the API 33 lens AGSL (shader swizzle).
// Exercise real API 32 blur, films, rims and shapes here; refraction needs device coverage.
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassMaterialOpticsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `overlay and navigation share optics independently of their themed film`() {
        val settings = mutableStateOf(GlassSettings(enabled = true, blur = 6f, rim = .8f))
        val overlayMode = mutableStateOf(false)
        compose.setContent {
            val shape = RoundedCornerShape(20.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                val pattern: DrawScope.() -> Unit = remember {
                    {
                        drawRect(Color.Blue)
                        drawRect(Color.Red, topLeft = Offset(size.width / 2f, 0f),
                            size = Size(size.width / 2f, size.height))
                    }
                }
                val backdrop = rememberCanvasBackdrop(pattern)
                Box(Modifier.background(Color.Black)) {
                    // Compare at identical device coordinates: separate positions can
                    // quantize the downsampled blur and antialiased edge differently.
                    Box(Modifier.size(96.dp).testTag("surface").glassMaterial(
                        settings = settings.value, shape = shape, ownBackdrop = backdrop,
                        menu = overlayMode.value, drawFilm = false, drawShadow = false,
                    ))
                }
            }
        }
        fun assertMatchingOptics() {
            compose.runOnIdle { overlayMode.value = false }
            val navigation = compose.onNodeWithTag("surface").captureToImage().toPixelMap()
            compose.runOnIdle { overlayMode.value = true }
            val overlay = compose.onNodeWithTag("surface").captureToImage().toPixelMap()
            // Menus intentionally omit the bottom rim; all remaining optics stay shared.
            val bottomRim = with(compose.density) { 4.dp.roundToPx() }
            for (y in 0 until navigation.height - bottomRim) for (x in 0 until navigation.width) {
                val expected = navigation[x, y]
                val actual = overlay[x, y]
                assertEquals("red at $x,$y", expected.red, actual.red, .015f)
                assertEquals("green at $x,$y", expected.green, actual.green, .015f)
                assertEquals("blue at $x,$y", expected.blue, actual.blue, .015f)
                assertEquals("alpha at $x,$y", expected.alpha, actual.alpha, .015f)
            }
        }
        assertMatchingOptics()
        compose.runOnIdle { settings.value = settings.value.copy(blur = 18f, depth = .7f, amount = .8f) }
        assertMatchingOptics()
        compose.runOnIdle { settings.value = settings.value.copy(quality = GlassQuality.Performance) }
        assertMatchingOptics()
    }
}
