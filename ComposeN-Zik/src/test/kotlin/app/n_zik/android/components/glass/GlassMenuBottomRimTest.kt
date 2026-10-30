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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassMenuBottomRimTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `contour only menus and dropdowns retain gradient and native fill except bottom rim`() {
        val area = mutableStateOf(GlassArea.Nav)
        val shape = mutableStateOf<Shape>(RectangleShape)
        val fill = Color(0xFF182430)
        compose.setContent {
            val rounded = RoundedCornerShape(20.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = rounded, uiRoundnessShape = rounded, artistThumbnailShape = rounded,
            )) {
                val settings = GlassSettings(rim = 1f, areaModes = mapOf(
                    GlassArea.Nav to GlassAreaMode.ContourOnly,
                    GlassArea.Menus to GlassAreaMode.ContourOnly,
                    GlassArea.Dropdowns to GlassAreaMode.ContourOnly,
                ))
                Box(Modifier.size(96.dp).background(fill).testTag("contour")
                    .glassAreaContour(settings, area.value, shape.value))
            }
        }
        fun capture() = compose.onNodeWithTag("contour").captureToImage().toPixelMap()
        for (outline in listOf(RectangleShape, RoundedCornerShape(20.dp),
            RoundedCornerShape(topStart = 20.dp, topEnd = 12.dp))) {
            compose.runOnIdle { shape.value = outline; area.value = GlassArea.Nav }
            val full = capture()
            for (menuArea in listOf(GlassArea.Menus, GlassArea.Dropdowns)) {
                compose.runOnIdle { area.value = menuArea }
                val menu = capture()
                val center = menu.width / 2
                assertTrue("Native contour bottom was visible", full[center, full.height - 1].red > fill.red + .05f)
                assertEquals("Native fill at bottom", fill, menu[center, menu.height - 1])
                for (y in 0 until full.height - 1) for (x in 0 until full.width) {
                    assertEquals("Gradient preserved at $x,$y area=$menuArea shape=$outline",
                        full[x, y].red, menu[x, y].red, .015f)
                    assertEquals(full[x, y].green, menu[x, y].green, .015f)
                    assertEquals(full[x, y].blue, menu[x, y].blue, .015f)
                }
            }
        }
    }

    @Test fun `menus omit only bottom rim for square rounded and fallback surfaces`() {
        val menu = mutableStateOf(false)
        val shape = mutableStateOf<Shape>(RectangleShape)
        val fallback = mutableStateOf(false)
        compose.setContent {
            val rounded = RoundedCornerShape(20.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = rounded, uiRoundnessShape = rounded, artistThumbnailShape = rounded,
            )) {
                val backdrop = rememberCanvasBackdrop { drawRect(Color.Black) }
                Box(Modifier.size(96.dp).background(Color.Black).testTag("surface")) {
                    Box(Modifier.size(96.dp).glassMaterial(
                        settings = GlassSettings(blur = 6f, rim = 1f), shape = shape.value,
                        ownBackdrop = backdrop, menu = menu.value, separateWindow = fallback.value,
                        drawFilm = false, drawShadow = false,
                    ))
                }
            }
        }
        fun capture() = compose.onNodeWithTag("surface").captureToImage().toPixelMap()
        for (native in listOf(false, true)) for (outline in listOf(RectangleShape, RoundedCornerShape(20.dp))) {
            compose.runOnIdle { fallback.value = native; shape.value = outline; menu.value = false }
            val full = capture()
            compose.runOnIdle { menu.value = true }
            val open = capture()
            val center = full.width / 2
            assertTrue("Bottom rim should be visible before exclusion", full[center, full.height - 1].red > .05f)
            assertEquals("Bottom menu rim", 0f, open[center, open.height - 1].red, .01f)
            // Upper corners, top, side edges, blur and interior remain identical.
            for (y in 0 until full.height - 4) for (x in 0 until full.width) {
                assertEquals("pixel at $x,$y native=$native shape=$outline",
                    full[x, y].red, open[x, y].red, .015f)
            }
            assertTrue("Top rim stays visible", open[center, 0].red > .05f)
            assertTrue("Side rim stays visible", open[0, center].red > .05f)
        }
    }
}
