package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassFallbackExportTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `child samples current fallback film before optics resume and after quality changes`() {
        val film = mutableStateOf(Color.Red)
        val progress = mutableStateOf(0f)
        val quality = mutableStateOf(GlassQuality.Balanced)
        val settings = GlassSettings(film = 0f, rim = 0f, blur = 0f,
            vibrancy = 0f, depth = 0f, amount = 0f)
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            val page = rememberCanvasBackdrop { drawRect(Color.Yellow) }
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
                LocalGlassBackdrop provides page,
                LocalGlassOpticsTransitionProgress provides progress.value) {
                GlassMaterialContainerScope(Modifier.size(100.dp), material = { exported ->
                    Modifier.glassMaterial(settings.copy(film = 1f, quality = quality.value),
                        shape, filmColor = film.value, exportedBackdrop = exported,
                        drawShadow = false)
                }) { surface ->
                    Box(surface.background(Color.Magenta)) {
                        CompositionLocalProvider(LocalGlassOpticsTransitionProgress provides 1f) {
                            Box(Modifier.size(60.dp).testTag("child").glassMaterial(
                                settings, shape, ownBackdrop = LocalGlassContainerBackdrop.current,
                                drawShadow = false))
                        }
                    }
                }
            }
        }
        fun assertFilm(expected: Color) {
            val pixels = compose.onNodeWithTag("child").captureToImage().toPixelMap()
            val actual = pixels[pixels.width / 2, pixels.height / 2]
            assertEquals("red", expected.red, actual.red, .02f)
            assertEquals("green", expected.green, actual.green, .02f)
            assertEquals("blue", expected.blue, actual.blue, .02f)
        }
        assertFilm(Color.Red)
        compose.runOnIdle { film.value = Color.Blue }
        assertFilm(Color.Blue)
        compose.runOnIdle { progress.value = 1f }
        assertFilm(Color.Blue)
        compose.runOnIdle { quality.value = GlassQuality.Performance; film.value = Color.Green }
        assertFilm(Color.Green)
        compose.runOnIdle { film.value = Color.Red }
        assertFilm(Color.Red)
        compose.runOnIdle { quality.value = GlassQuality.Balanced }
        assertFilm(Color.Red)
    }
}
