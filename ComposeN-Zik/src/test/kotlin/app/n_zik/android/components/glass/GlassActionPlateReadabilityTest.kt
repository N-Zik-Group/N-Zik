package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.player.GlassMiniPlayerIconButton
import app.n_zik.android.components.player.GlassMiniPlayerPlayPauseButton
import app.n_zik.android.components.player.miniPlayerPlayPauseGlyphColor
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultLightColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
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
class GlassActionPlateReadabilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `real action glyphs use dark role plates over a bright backdrop and retain colored roles`() {
        val film = mutableStateOf(1f)
        val ink = mutableStateOf(Color.Black)
        val enabled = mutableStateOf(true)
        val palette = DefaultLightColorPalette.copy(background0 = Color.White,
            background1 = Color.Black, background2 = Color.Black, text = Color.Black)
        val square = ImageVector.Builder("Solid action", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(0f, 0f); lineTo(24f, 0f); lineTo(24f, 24f); lineTo(0f, 24f); close()
            }
        }.build()
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            val backdrop = rememberCanvasBackdrop { drawRect(Color.White) }
            CompositionLocalProvider(
                LocalAppearance provides Appearance(palette,
                    typographyOf(Color.Black, true, false, FontType.Rubik), shape, shape, shape),
                LocalSurfaceBackdropColor provides Color.White,
                LocalGlassContainerBackdrop provides backdrop,
                LocalGlassBackdrop provides backdrop,
            ) {
                val settings = GlassSettings(film = film.value, blur = 0f, rim = 0f,
                    depth = 0f, vibrancy = 0f, amount = 0f, tintStrength = 0f,
                    quality = GlassQuality.Performance)
                Column(Modifier.background(Color.White)) {
                    PageButton({}, Modifier.size(120.dp, 48.dp).testTag("page"),
                        enabled = enabled.value, elevation = null, settings = settings,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black,
                            contentColor = ink.value, disabledContainerColor = Color.Black,
                            disabledContentColor = ink.value)) {
                        Box(Modifier.size(16.dp).background(LocalContentColor.current))
                    }
                    GlassToolbarRow(1, settings = settings) {
                        GlassToolbarIcon(ColorPainter(Color.White), ink.value, 24.dp, enabled.value,
                            onClick = {}, contentDescription = "standalone")
                    }
                    GlassToolbarRow(2, settings = settings) {
                        repeat(2) { index ->
                            GlassToolbarIcon(ColorPainter(Color.White), ink.value, 24.dp, enabled.value,
                                onClick = {}, contentDescription = "group.$index")
                        }
                    }
                    GlassVerticalActionGroup(settings, GlassArea.ActionButtons) {
                        repeat(2) { index ->
                            GlassActionGroupItem({}, enabled = enabled.value) {
                                Box(Modifier.size(16.dp).background(LocalContentColor.current)
                                    .testTag("vertical.$index"))
                            }
                        }
                    }
                    GlassFloatingActionIcon(ColorPainter(Color.White), {}, tint = ink.value,
                        enabled = enabled.value, settings = settings, filmColor = Color.Black,
                        contentDescription = "floating")
                    GlassMiniPlayerIconButton(square, ink.value, {}, "mini", enabled = enabled.value,
                        settings = settings, backdrop = backdrop)
                    GlassMiniPlayerPlayPauseButton({}, Color.Black, "play", settings, backdrop) {
                        Box(Modifier.align(Alignment.Center).size(16.dp)
                            .background(miniPlayerPlayPauseGlyphColor(settings, ink.value)))
                    }
                }
            }
        }
        val descriptions = listOf("standalone", "group.0", "group.1", "floating", "mini", "play")
        fun glyph(description: String): Color {
            val pixels = compose.onNodeWithContentDescription(description).captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        fun pageGlyph(): Color {
            val pixels = compose.onNodeWithTag("page").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        descriptions.forEach { assertColor(Color.White, glyph(it)) }
        assertColor(Color.White, pageGlyph())
        repeat(2) { index ->
            val pixels = compose.onNodeWithTag("vertical.$index", true).captureToImage().toPixelMap()
            assertColor(Color.White, pixels[pixels.width / 2, pixels.height / 2])
        }
        // A bright scene must not erase the actual black seat beneath these glyphs.
        listOf("standalone", "floating", "mini", "play").forEach { description ->
            val pixels = compose.onNodeWithContentDescription(description).captureToImage().toPixelMap()
            assertTrue(description, pixels[pixels.width / 8, pixels.height / 2].red < .05f)
        }
        compose.runOnIdle { ink.value = Color(0xFFB02050) }
        descriptions.forEach { assertColor(ink.value, glyph(it)) }
        assertColor(ink.value, pageGlyph())
        // With no film, the same controls inherit the bright scene rather than inventing dark plates.
        compose.runOnIdle { ink.value = Color.Black; film.value = 0f }
        descriptions.forEach { assertColor(Color.Black, glyph(it)) }
        assertColor(Color.Black, pageGlyph())
        // Disabled alpha remains the caller's role; toolbars retain their existing half-opacity policy.
        compose.runOnIdle { film.value = 1f; ink.value = Color.White.copy(alpha = .4f); enabled.value = false }
        assertColor(Color(.4f, .4f, .4f), glyph("mini"))
        assertColor(Color(.4f, .4f, .4f), pageGlyph())
        assertColor(Color(.2f, .2f, .2f), glyph("floating"))
    }

    private fun assertColor(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, .02f)
        assertEquals(expected.green, actual.green, .02f)
        assertEquals(expected.blue, actual.blue, .02f)
    }
}
