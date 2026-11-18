package app.n_zik.android.components.player

import androidx.compose.ui.graphics.Color
import app.n_zik.android.components.glass.IconContrast
import app.n_zik.android.components.glass.contrastAgainst
import app.n_zik.android.components.glass.readableGlyphForeground
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.PlayerPlayButtonType
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayButtonBackgroundTest {
    @Test
    fun pureBlackModeOwnsNativePlateWhileShapedAndDisabledContainersStayTransparent() {
        for (name in ColorPaletteName.entries) {
            assertEquals(Color.Black, playButtonNativeColor(name, Color.Green.copy(alpha = .95f), ColorPaletteMode.PureBlack))
            for (gradient in listOf(false, true)) {
                for (type in PlayerPlayButtonType.entries) {
                    val expected = if (type == PlayerPlayButtonType.Disabled || type == PlayerPlayButtonType.CircularRibbed)
                        Color.Transparent else Color.Black
                    assertEquals("$name/$type/$gradient", expected,
                        playButtonContainerColor(type, name, DefaultDarkColorPalette, gradient, ColorPaletteMode.PureBlack))
                }
            }
        }
    }

    @Test
    fun shapedAndDisabledButtonsNeverPaintTheirContainer() {
        for (name in ColorPaletteName.entries) {
            for (gradient in listOf(false, true)) {
                for (type in listOf(PlayerPlayButtonType.CircularRibbed, PlayerPlayButtonType.Disabled)) {
                    assertEquals("$name/$type/$gradient", Color.Transparent,
                        playButtonContainerColor(type, name, DefaultDarkColorPalette, gradient))
                }
            }
        }
    }

    @Test
    fun filledButtonsUseBlackForBlackPalettesAndRetainOtherPaletteSurfaces() {
        val palette = DefaultDarkColorPalette.copy(
            background1 = Color.Red, background2 = Color.Green, background4 = Color.Blue)
        for (name in ColorPaletteName.entries) {
            for (gradient in listOf(false, true)) {
                for (type in listOf(PlayerPlayButtonType.Default, PlayerPlayButtonType.Rectangular, PlayerPlayButtonType.Square)) {
                    val expected = when (name) {
                        ColorPaletteName.PureBlack, ColorPaletteName.ModernBlack -> Color.Black
                        else -> if (gradient) Color.Red else Color.Green
                    }
                    assertEquals("$name/$type/$gradient", expected,
                        playButtonContainerColor(type, name, palette, gradient))
                }
            }
        }
    }

    @Test
    fun `central native play plates are opaque black only for black palettes`() {
        for (original in listOf(Color.Blue, Color.Green.copy(alpha = .95f), Color.DarkGray)) {
            for (name in ColorPaletteName.entries) {
                val expected = when (name) {
                    ColorPaletteName.PureBlack, ColorPaletteName.ModernBlack -> Color.Black
                    else -> original
                }
                assertEquals(name.name, expected,
                    playButtonNativeColor(name, original))
            }
        }
    }

    @Test
    fun `neutral play glyphs remain readable against black palette plates`() {
        for (name in listOf(ColorPaletteName.PureBlack, ColorPaletteName.ModernBlack)) {
            val plate = playButtonNativeColor(name, Color.DarkGray)
            for (ink in listOf(Color.Black, Color.White)) {
                val glyph = readableGlyphForeground(ink, plate)
                assertTrue(contrastAgainst(glyph, plate) >= IconContrast)
            }
        }
    }
}
