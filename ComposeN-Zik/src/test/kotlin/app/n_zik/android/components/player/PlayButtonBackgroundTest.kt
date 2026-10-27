package app.n_zik.android.components.player

import androidx.compose.ui.graphics.Color
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.enums.PlayerPlayButtonType
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayButtonBackgroundTest {
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
    fun filledButtonsRetainTheirExistingPaletteSurfaces() {
        val palette = DefaultDarkColorPalette.copy(
            background1 = Color.Red, background2 = Color.Green, background4 = Color.Blue)
        for (name in ColorPaletteName.entries) {
            for (gradient in listOf(false, true)) {
                for (type in listOf(PlayerPlayButtonType.Default, PlayerPlayButtonType.Rectangular, PlayerPlayButtonType.Square)) {
                    val expected = when (name) {
                        ColorPaletteName.PureBlack, ColorPaletteName.ModernBlack -> Color.Blue
                        else -> if (gradient) Color.Red else Color.Green
                    }
                    assertEquals("$name/$type/$gradient", expected,
                        playButtonContainerColor(type, name, palette, gradient))
                }
            }
        }
    }
}
