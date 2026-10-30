package app.n_zik.android.components.navigation.nav

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassTintSource
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
// API 32 exercises real blur without Robolectric's unsupported API 33 lens shader.
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassNavigationBackgroundTest {
    @get:Rule val compose = createComposeRule()

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setActiveProfile("default", context)
        context.preferences.edit().clear().commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
    }

    @Test fun `panel and selected puck retain live theme film in optical and fallback renderers`() {
        var palette by mutableStateOf(ColorPalette.fallback.copy(background1 = Color.Blue))
        var optical by mutableStateOf(false)
        var settings by mutableStateOf(GlassSettings(enabled = true, film = .5f,
            rim = 0f, blur = 0f, vibrancy = 0f, indicatorOpacity = .2f))
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            val backdrop = rememberCanvasBackdrop { drawRect(Color.Black) }
            CompositionLocalProvider(
                LocalAppearance provides Appearance(palette, Typography.fallback, shape, shape, shape),
                LocalGlassBackdrop provides if (optical) backdrop else null,
            ) {
                Box(Modifier.size(160.dp, 64.dp).background(Color.Black).testTag("navigation")) {
                    GlassNavigationTabs(0, listOf(0, 1), {}, settings, false, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        fun assertFilm() {
            val pixels = compose.onNodeWithTag("navigation").captureToImage().toPixelMap()
            val fill = if (settings.source == GlassTintSource.Custom)
                lerp(palette.background1, Color.Green, .5f).copy(alpha = palette.background1.alpha)
                else palette.background1
            val film = fill.copy(alpha = fill.alpha * settings.film).compositeOver(Color.Black)
            val wash = (if (palette.isDark) Color.Black else Color.White).copy(alpha = .2f)
            val scenario = "optical=$optical source=${settings.source} film=${settings.film} base=${palette.background1}"
            assertColor(film, pixels[pixels.width * 3 / 4, pixels.height / 2], "panel $scenario")
            assertColor(wash.compositeOver(film), pixels[pixels.width / 4, pixels.height / 2], "puck $scenario")
        }
        listOf(false, true).forEach { useOptics ->
            compose.runOnIdle {
                optical = useOptics
                palette = ColorPalette.fallback.copy(background1 = Color.Blue)
            }
            listOf(GlassTintSource.None, GlassTintSource.Custom).forEach { source ->
                compose.runOnIdle {
                    settings = settings.copy(source = source, customColor = 0xFF00FF00.toInt(), tintStrength = .5f)
                }
                listOf(0f, .5f, 1f).forEach { opacity ->
                    compose.runOnIdle { settings = settings.copy(film = opacity) }
                    assertFilm()
                }
            }
            compose.runOnIdle {
                palette = palette.copy(background1 = Color.Magenta.copy(alpha = .8f), isDark = false)
                settings = settings.copy(source = GlassTintSource.None, film = .5f)
            }
            assertFilm()
        }
    }

    @Test fun `navigation disabled and master off retain the original native fill`() {
        var settings by mutableStateOf(GlassSettings(enabled = true, film = .5f, rim = 1f,
            quality = GlassQuality.Performance, indicatorOpacity = 0f, nav = false,
            source = GlassTintSource.Custom, customColor = 0xFF00FF00.toInt(), tintStrength = 1f))
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                ColorPalette.fallback.copy(background1 = Color.Blue), Typography.fallback, shape, shape, shape,
            )) {
                Box(Modifier.size(160.dp, 64.dp).background(Color.Black).testTag("modes")) {
                    GlassNavigationTabs(0, listOf(0, 1), {}, settings,
                        false, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        fun assertFill(expected: Color) {
            val pixels = compose.onNodeWithTag("modes").captureToImage().toPixelMap()
            assertColor(expected, pixels[pixels.width / 4, pixels.height / 2])
            assertColor(expected, pixels[pixels.width * 3 / 4, pixels.height / 2])
            assertEquals(Color.Black, pixels[0, 0])
        }
        assertFill(Color.Blue)
        compose.runOnIdle { settings = settings.copy(enabled = false, nav = true) }
        assertFill(Color.Blue)
    }

    private fun assertColor(expected: Color, actual: Color, scenario: String = "native") {
        assertEquals("red $scenario", expected.red, actual.red, .025f)
        assertEquals("green $scenario", expected.green, actual.green, .025f)
        assertEquals("blue $scenario", expected.blue, actual.blue, .025f)
        assertEquals("alpha $scenario", expected.alpha, actual.alpha, .025f)
    }
}
