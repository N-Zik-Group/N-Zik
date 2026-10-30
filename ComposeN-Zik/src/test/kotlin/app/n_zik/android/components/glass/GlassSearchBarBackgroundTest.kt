package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.backdrop.Backdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
class GlassSearchBarBackgroundTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `search uses original translucent film sharp foreground live shape and independent backdrop`() {
        var settings by mutableStateOf(GlassSettings(enabled = true, film = .5f,
            rim = 0f, blur = 0f, vibrancy = 0f))
        var shape: Shape by mutableStateOf(RoundedCornerShape(0.dp))
        var fill by mutableStateOf(Color.Blue.copy(alpha = .8f))
        val page = Color.Black
        val forbiddenPage = object : Backdrop {
            override val isCoordinatesDependent = false
            override fun DrawScope.drawBackdrop(
                density: Density,
                coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?,
            ) { error("Inline search must not sample the page containing itself") }
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = DefaultDarkColorPalette,
                    typography = typographyOf(Color.White, true, false, FontType.Rubik),
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
                LocalGlassBackdrop provides forbiddenPage,
            ) {
                Box(Modifier.size(80.dp).background(page).testTag("search_surface")) {
                    Box(
                        Modifier.size(80.dp).glassSearchBarBackground(
                            backgroundColor = page, containerColor = fill, shape = shape,
                            shadowColor = Color.Red, settings = settings,
                        ).clip(shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(16.dp).background(Color.Yellow))
                    }
                }
            }
        }
        fun capture() = compose.onNodeWithTag("search_surface").captureToImage().toPixelMap()
        fun assertFilm() {
            val pixels = capture()
            val film = if (settings.source == GlassTintSource.Custom) Color.Green.copy(alpha = fill.alpha) else fill
            assertColor(film.copy(alpha = film.alpha * .5f).compositeOver(page),
                pixels[pixels.width / 4, pixels.height / 2])
            for (y in pixels.height / 2 - 3..pixels.height / 2 + 3) {
                for (x in pixels.width / 2 - 3..pixels.width / 2 + 3) assertEquals(Color.Yellow, pixels[x, y])
            }
        }
        assertFilm()
        compose.runOnIdle { shape = RoundedCornerShape(20.dp); fill = Color.Magenta }
        assertFilm()
        assertEquals(page, capture()[0, 0])
        compose.runOnIdle {
            settings = settings.copy(source = GlassTintSource.Custom,
                customColor = 0xFF00FF00.toInt(), tintStrength = 1f)
        }
        assertFilm()
        compose.runOnIdle { settings = settings.copy(searchBars = false) }
        val native = capture()
        assertEquals(fill, native[native.width / 4, native.height / 2])
        compose.runOnIdle { settings = settings.copy(searchBars = true, enabled = false) }
        val masterOff = capture()
        for (y in 0 until native.height) {
            for (x in 0 until native.width) assertEquals(native[x, y], masterOff[x, y])
        }
        compose.runOnIdle { settings = settings.copy(enabled = true, quality = GlassQuality.Performance) }
        assertFilm()
    }

    private fun assertColor(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, .025f)
        assertEquals(expected.green, actual.green, .025f)
        assertEquals(expected.blue, actual.blue, .025f)
        assertEquals(expected.alpha, actual.alpha, .025f)
    }

    @Test fun `four modes preserve native fill or glass film and draw only their selected contour`() {
        var settings by mutableStateOf(GlassSettings(enabled = true, quality = GlassQuality.Performance,
            film = .5f, rim = 1f, tintStrength = 0f,
            areaModes = mapOf(GlassArea.SearchBars to GlassAreaMode.Disabled)))
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                Box(Modifier.size(80.dp).background(Color.Black).testTag("mode_surface")) {
                    Box(Modifier.size(80.dp).glassSearchBarBackground(
                        backgroundColor = Color.Black, containerColor = Color.Blue, shape = shape,
                        shadowColor = Color.Red, settings = settings,
                    ), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(16.dp).background(Color.Yellow))
                    }
                }
            }
        }
        fun capture() = compose.onNodeWithTag("mode_surface").captureToImage().toPixelMap()
        val native = capture()
        GlassAreaMode.entries.forEach { mode ->
            compose.runOnIdle { settings = settings.copy(areaModes = mapOf(GlassArea.SearchBars to mode)) }
            val pixels = capture()
            val expected = if (mode.hasMaterial) Color.Blue.copy(alpha = .5f).compositeOver(Color.Black) else Color.Blue
            assertColor(expected, pixels[pixels.width / 4, pixels.height / 2])
            assertEquals(Color.Yellow, pixels[pixels.width / 2, pixels.height / 2])
            if (mode.hasContour) assertNotEquals(expected, pixels[0, pixels.height / 2])
            else assertColor(expected, pixels[0, pixels.height / 2])
        }
        compose.runOnIdle { settings = settings.copy(enabled = false) }
        val disabled = capture()
        for (y in 0 until native.height) for (x in 0 until native.width) assertEquals(native[x, y], disabled[x, y])
        compose.runOnIdle { settings = settings.copy(enabled = true, rim = 0f,
            areaModes = mapOf(GlassArea.SearchBars to GlassAreaMode.ContourOnly)) }
        val rimZero = capture()
        for (y in 0 until native.height) for (x in 0 until native.width) assertEquals(native[x, y], rimZero[x, y])
    }
}
