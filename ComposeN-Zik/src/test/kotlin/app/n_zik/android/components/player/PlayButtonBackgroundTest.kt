package app.n_zik.android.components.player

import androidx.compose.ui.graphics.Color
import app.n_zik.android.components.glass.IconContrast
import app.n_zik.android.components.glass.contrastAgainst
import app.n_zik.android.components.glass.readableGlyphForeground
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.PlayerBackgroundColors
import app.n_zik.android.enums.PlayerPlayButtonType
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.colorPaletteOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayButtonBackgroundTest {
    @Test
    fun `every named palette and mode preserves its play plate rules across player backgrounds`() {
        var configurations = 0
        for (name in ColorPaletteName.entries) for (mode in ColorPaletteMode.entries) {
            val systemDarkModes = if (mode == ColorPaletteMode.System) listOf(false, true) else listOf(false)
            for (systemDark in systemDarkModes) {
                val palette = colorPaletteOf(name, mode, systemDark)
                for (background in PlayerBackgroundColors.entries) for (shape in PlayerPlayButtonType.entries) {
                    val gradient = background in listOf(PlayerBackgroundColors.CoverColorGradient, PlayerBackgroundColors.ThemeColorGradient)
                    val expected = when {
                        shape in listOf(PlayerPlayButtonType.Disabled, PlayerPlayButtonType.CircularRibbed) -> Color.Transparent
                        name.isAlwaysDark || mode == ColorPaletteMode.PureBlack -> Color.Black
                        gradient -> palette.background1
                        else -> palette.background2
                    }
                    assertEquals("$name/$mode/systemDark=$systemDark/$background/$shape", expected,
                        playButtonContainerColor(shape, name, palette, gradient, mode))
                    configurations++
                }
            }
        }
        assertEquals(1_470, configurations)
    }

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
