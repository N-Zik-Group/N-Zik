package app.n_zik.android.components.ui.screens.rewind.slides

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract for the slide contrast selectors ([RewindColors.textOn] / [RewindColors.flatTextOn]):
 * both resolve the ink/cream sides by luminance (so a light theme inverts them) and pick the
 * readable side at the 0.45 luminance threshold — [textOn] on the scrim-darkened slide
 * background, [flatTextOn] on the surface itself (spec GH-275, patch "textOn/flatTextOn
 * contrast selector untested").
 */
class RewindTextOnContrastTest {

    /** Dark-theme palette: ink is dark, cream is light. */
    private val dark = RewindColors(
        ink = Color(0xFF08070C),
        cream = Color(0xFFFFF6E6),
        purple = Color(0xFF461CF4),
        pink = Color(0xFFFF4B98),
        red = Color(0xFFE83B2F),
        orange = Color(0xFFFF5A2E),
        lime = Color(0xFFD9FF31),
        blue = Color(0xFF2369EB),
        yellow = Color(0xFFFFD72E)
    )

    /** Light-theme palette: the ink/cream roles are swapped. */
    private val light = dark.copy(ink = Color(0xFFFFF6E6), cream = Color(0xFF08070C))

    @Test
    fun textOnPicksLightTextOnDarkBackgrounds() {
        assertEquals(dark.cream, dark.textOn(Color.Black))
        // The purple slide background is dark once the scrim darkens it further
        assertEquals(dark.cream, dark.textOn(dark.purple))
    }

    @Test
    fun flatTextOnPicksLightTextOnDarkSurfaces() {
        assertEquals(dark.cream, dark.flatTextOn(Color.Black))
        assertEquals(dark.cream, dark.flatTextOn(dark.purple))
    }

    @Test
    fun flatTextOnPicksDarkTextOnBrightSurfaces() {
        // The lime accent tile is bright on its own → dark text, even though the slide
        // background behind it may be dark
        assertEquals(dark.ink, dark.flatTextOn(dark.lime))
    }

    @Test
    fun invertedPaletteSwapsTheSides() {
        // Light theme: ink is now the light side — both selectors return it on black,
        // because the sides are resolved by luminance, not by the ink/cream names
        assertEquals(light.ink, light.textOn(Color.Black))
        assertEquals(light.ink, light.flatTextOn(Color.Black))
        // A bright surface still gets the dark side
        assertEquals(light.cream, light.flatTextOn(light.lime))
    }

    @Test
    fun textOnJudgesTheScrimDarkenedBackgroundWhileFlatTextOnJudgesTheSurface() {
        // Mid-grey: bright enough on its own (flatTextOn → dark text), but the
        // REWIND_SCRIM_ALPHA scrim of ink pulls it under the 0.45 threshold (textOn → light)
        val mid = Color(0xFF808080)
        assertEquals(dark.cream, dark.textOn(mid))
        assertEquals(dark.ink, dark.flatTextOn(mid))
    }
}
