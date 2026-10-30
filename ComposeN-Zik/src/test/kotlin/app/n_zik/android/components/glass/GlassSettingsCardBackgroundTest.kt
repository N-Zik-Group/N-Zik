package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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
import app.n_zik.android.components.settings.common.SettingsCardSurface
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
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
class GlassSettingsCardBackgroundTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `Glass surface has no native shadow plate outside its bounds`() {
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = DefaultDarkColorPalette,
                    typography = typographyOf(Color.White, true, false, FontType.Rubik),
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
            ) {
                Box(Modifier.size(112.dp).background(Color.White).testTag("gutter")) {
                    SettingsCardSurface(
                        modifier = Modifier.padding(16.dp).size(80.dp),
                        shape = shape,
                        containerColor = Color.Blue,
                        backgroundColor = Color.White,
                        shadowColor = Color.Red,
                        shadowElevation = 24.dp,
                        settings = GlassSettings(enabled = true, film = .5f, tintStrength = 0f),
                    ) {}
                }
            }
        }
        val pixels = compose.onNodeWithTag("gutter").captureToImage().toPixelMap()
        val gutter = with(compose.density) { 12.dp.roundToPx() }
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                if (x < gutter || x >= pixels.width - gutter || y < gutter || y >= pixels.height - gutter) {
                    assertEquals(Color.White, pixels[x, y])
                }
            }
        }
        assertEquals(Color.White, pixels[gutter, gutter])
    }

    @Test fun `full card tints its native film while preserving content and exact native fallback`() {
        var settings by mutableStateOf(GlassSettings(enabled = true, film = .5f,
            tintStrength = 0f, rim = 1f, settingsCardRims = false, blur = 0f, vibrancy = 0f))
        var shape: Shape by mutableStateOf(RoundedCornerShape(0.dp))
        var artwork: Color? by mutableStateOf(null)
        var fill by mutableStateOf(Color.Blue)
        val page = Color.Black
        val forbiddenPageCapture = object : Backdrop {
            override val isCoordinatesDependent = false
            override fun DrawScope.drawBackdrop(
                density: Density,
                coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?,
            ) { error("A settings card must not sample the page containing itself") }
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = DefaultDarkColorPalette,
                    typography = typographyOf(Color.White, true, false, FontType.Rubik),
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
                LocalGlassBackdrop provides forbiddenPageCapture,
                LocalGlassArtworkColor provides artwork,
            ) {
                Box(Modifier.size(80.dp).background(page).testTag("card")) {
                    SettingsCardSurface(
                        modifier = Modifier.size(80.dp),
                        shape = shape,
                        containerColor = fill,
                        backgroundColor = page,
                        shadowColor = Color.Red,
                        settings = settings,
                    ) {
                        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(16.dp).background(Color.Yellow))
                        }
                    }
                }
            }
        }
        fun capture() = compose.onNodeWithTag("card").captureToImage().toPixelMap()
        var pixels = capture()
        assertColor(fill.copy(alpha = .5f).compositeOver(page), pixels[pixels.width / 4, pixels.height / 2])
        for (y in pixels.height / 2 - 3..pixels.height / 2 + 3) {
            for (x in pixels.width / 2 - 3..pixels.width / 2 + 3) assertEquals(Color.Yellow, pixels[x, y])
        }
        compose.runOnIdle { shape = RoundedCornerShape(20.dp) }
        pixels = capture()
        assertEquals(page, pixels[0, 0])
        compose.runOnIdle { fill = Color.Magenta }
        pixels = capture()
        assertColor(fill.copy(alpha = .5f).compositeOver(page), pixels[pixels.width / 4, pixels.height / 2])
        compose.runOnIdle {
            settings = settings.copy(source = GlassTintSource.Artwork, tintStrength = 1f)
            artwork = Color.Green
        }
        pixels = capture()
        assertColor(Color.Green.copy(alpha = .5f).compositeOver(page), pixels[pixels.width / 4, pixels.height / 2])
        compose.runOnIdle { artwork = Color.Cyan }
        pixels = capture()
        assertColor(Color.Cyan.copy(alpha = .5f).compositeOver(page), pixels[pixels.width / 4, pixels.height / 2])
        assertEquals(Color.Yellow, pixels[pixels.width / 2, pixels.height / 2])
        compose.runOnIdle { settings = settings.copy(settingsCards = false) }
        val native = capture()
        assertEquals(fill, native[native.width / 4, native.height / 2])
        compose.runOnIdle { settings = settings.copy(settingsCards = true, enabled = false) }
        val masterOff = capture()
        for (y in 0 until native.height) {
            for (x in 0 until native.width) assertEquals(native[x, y], masterOff[x, y])
        }
        compose.runOnIdle {
            settings = settings.copy(enabled = true, settingsCards = false, settingsCardRims = true)
        }
        val rimOnly = capture()
        assertEquals(fill, rimOnly[rimOnly.width / 4, rimOnly.height / 2])
        assertNotEquals(native[0, native.height / 2], rimOnly[0, rimOnly.height / 2])
        compose.runOnIdle { settings = settings.copy(settingsCards = true, quality = GlassQuality.Performance) }
        val fallback = capture()
        assertColor(Color.Cyan.copy(alpha = .5f).compositeOver(page), fallback[fallback.width / 4, fallback.height / 2])
        assertEquals(Color.Yellow, fallback[fallback.width / 2, fallback.height / 2])
    }

    @Test fun `card matches navigation optics with original film one rim and existing search highlight`() {
        var settings by mutableStateOf(GlassSettings(enabled = true, film = .5f, tintStrength = 0f,
            rim = 1f, blur = 0f, vibrancy = 0f))
        var cardColor by mutableStateOf(Color.Blue)
        val page = Color.Black
        compose.setContent {
            val shape = RoundedCornerShape(18.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = DefaultDarkColorPalette,
                    typography = typographyOf(Color.White, true, false, FontType.Rubik),
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
            ) {
                val drawBackground: DrawScope.() -> Unit = remember { { drawRect(page) } }
                val backdrop = rememberCanvasBackdrop(drawBackground)
                Row(Modifier.background(page)) {
                    SettingsCardSurface(
                        modifier = Modifier.size(80.dp).testTag("card"), shape = shape,
                        containerColor = cardColor, backgroundColor = page, shadowColor = Color.Red,
                        settings = settings,
                    ) {}
                    Box(Modifier.size(80.dp).testTag("navigation").clip(shape).glassMaterial(
                        settings = if (settings.settingsCardRims) settings else settings.copy(rim = 0f),
                        shape = shape, highlightAlpha = .3f, ownBackdrop = backdrop, drawShadow = false,
                        // Optics match navigation; the original card fill intentionally differs.
                        filmColor = cardColor,
                    ))
                }
            }
        }
        fun assertNavigationPixels() {
            val card = compose.onNodeWithTag("card").captureToImage().toPixelMap()
            val navigation = compose.onNodeWithTag("navigation").captureToImage().toPixelMap()
            for (y in 0 until card.height) for (x in 0 until card.width) {
                assertColor(navigation[x, y], card[x, y])
            }
        }
        assertNavigationPixels()
        compose.runOnIdle { settings = settings.copy(settingsCardRims = false) }
        assertNavigationPixels()
        compose.runOnIdle { settings = settings.copy(quality = GlassQuality.Performance) }
        assertNavigationPixels()
        compose.runOnIdle {
            cardColor = androidx.compose.ui.graphics.lerp(Color.Blue, Color.Yellow, .22f)
        }
        assertNavigationPixels()
        val pixels = compose.onNodeWithTag("card").captureToImage().toPixelMap()
        assertColor(cardColor.copy(alpha = .5f).compositeOver(page), pixels[pixels.width / 2, pixels.height / 2])
    }

    private fun assertColor(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, .02f)
        assertEquals(expected.green, actual.green, .02f)
        assertEquals(expected.blue, actual.blue, .02f)
        assertEquals(expected.alpha, actual.alpha, .02f)
    }
}
