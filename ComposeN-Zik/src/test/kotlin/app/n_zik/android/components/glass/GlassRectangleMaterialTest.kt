package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.effects.lensCornerRadii
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
// API 32 exercises native blur; Robolectric's API 33 Skia cannot compile the lens AGSL.
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassRectangleMaterialTest {
    @get:Rule val compose = createComposeRule()

    private val unsupported = object : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
            RectangleShape.createOutline(size, layoutDirection, density)
    }

    @Test fun `square optics retain exact outline and finite zero lens radii`() {
        val size = Size(96f, 64f)
        val density = Density(1f)
        val square = requireNotNull(glassOpticalShape(RectangleShape))
        assertEquals(RectangleShape.createOutline(size, LayoutDirection.Ltr, density),
            square.createOutline(size, LayoutDirection.Ltr, density))
        for (scale in listOf(.3f, 1f)) {
            val radii = requireNotNull(lensCornerRadii(square, size * scale, scale, density, LayoutDirection.Ltr))
            assertArrayEquals(FloatArray(4), radii, 0f)
            assertTrue(radii.all { it.isFinite() })
        }
        val rounded = RoundedCornerShape(20.dp)
        assertSame(rounded, glassOpticalShape(rounded))
        assertNull(glassOpticalShape(unsupported))
    }

    @Test fun `live square shape retains source blur while unsupported and native windows retain fallback`() {
        val shape = mutableStateOf<Shape>(RoundedCornerShape(20.dp))
        val reverse = mutableStateOf(false)
        val separateWindow = mutableStateOf(false)
        compose.setContent {
            val rounded = RoundedCornerShape(20.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = rounded, uiRoundnessShape = rounded, artistThumbnailShape = rounded,
            )) {
                val backdrop = rememberCanvasBackdrop {
                    drawRect(if (reverse.value) Color.Red else Color.Blue)
                    drawRect(if (reverse.value) Color.Blue else Color.Red,
                        topLeft = Offset(size.width / 2f, 0f), size = Size(size.width / 2f, size.height))
                }
                Box(Modifier.size(96.dp).background(Color.White).testTag("scene")) {
                    Box(Modifier.size(96.dp).glassMaterial(
                        settings = GlassSettings(blur = 8f, rim = 0f, vibrancy = 0f),
                        shape = shape.value, ownBackdrop = backdrop, backdropScale = 1f,
                        separateWindow = separateWindow.value, drawFilm = false, drawShadow = false,
                    ))
                }
            }
        }
        fun pixels() = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
        assertEquals(Color.White, pixels()[1, 1])
        compose.runOnIdle { shape.value = RectangleShape }
        val square = pixels()
        // Blur may blend the capture boundary with its white underlay; a square
        // corner must still contain the blue source rather than stay fully clipped.
        val corner = square[1, 1]
        assertTrue("Square corner must sample blue: $corner",
            corner.blue > corner.red + .15f && corner.blue > corner.green + .15f)
        assertTrue(square[16, 48].blue > .9f)
        val boundary = square[47, 48]
        assertTrue("Square material must blur the source boundary", boundary.red > .1f && boundary.blue > .1f)
        compose.runOnIdle { reverse.value = true }
        assertTrue(pixels()[16, 48].red > .9f)
        compose.runOnIdle { shape.value = RoundedCornerShape(20.dp) }
        assertEquals(Color.White, pixels()[1, 1])
        compose.runOnIdle { shape.value = unsupported }
        assertEquals(Color.White, pixels()[48, 48])
        compose.runOnIdle { shape.value = RectangleShape; separateWindow.value = true }
        assertEquals(Color.White, pixels()[48, 48])
        compose.runOnIdle { separateWindow.value = false }
        assertTrue(pixels()[16, 48].red > .9f)
    }
}
