package app.n_zik.android.components.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.n_zik.android.components.player.playButtonNativeColor
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.nativeSurfaceMix
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.DefaultLightColorPalette
import app.n_zik.android.ui.styling.ModernBlackColorPalette
import app.n_zik.android.ui.styling.PureBlackColorPalette
import app.n_zik.android.ui.styling.collapsedPlayerProgressBar
import app.n_zik.android.ui.styling.favoritesIcon
import app.n_zik.android.ui.styling.favoritesOverlay
import app.n_zik.android.ui.styling.lerpTo
import app.n_zik.android.ui.styling.primaryButton
import app.n_zik.android.ui.styling.shimmer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ColorPaletteTransitionTest {
    @Test
    fun `black play plate follows the visible palette and stays black between black roles`() {
        val normal = nativeSurfaceMix(ColorPaletteMode.Dark, ColorPaletteName.Default, DefaultDarkColorPalette)
        val named = nativeSurfaceMix(ColorPaletteMode.Dark, ColorPaletteName.ModernBlack, ModernBlackColorPalette)
        val mode = nativeSurfaceMix(ColorPaletteMode.PureBlack, ColorPaletteName.Dynamic, DefaultDarkColorPalette)
        val base = Color(0xCC808080)
        assertEquals(lerp(base, Color.Black, .5f), playButtonNativeColor(
            ColorPaletteName.ModernBlack, base, blackCoverage = normal.at(named, .5f).transportBlack,
        ))
        assertEquals(1f, named.at(mode, .5f).transportBlack, 0f)
    }

    @Test
    fun `default icon roles remain familiar during a light to dark fade`() {
        val from = DefaultLightColorPalette
        val target = DefaultDarkColorPalette
        val halfway = from.lerpTo(target, .5f)

        assertSame(from, from.lerpTo(target, 0f))
        assertSame(target, from.lerpTo(target, 1f))
        assertEquals(lerp(from.text, target.text, .5f), halfway.collapsedPlayerProgressBar)
        assertEquals(from.red, halfway.favoritesIcon)
        assertEquals(from.favoritesOverlay, halfway.favoritesOverlay)
        assertEquals(from.shimmer, halfway.shimmer)
    }

    @Test
    fun `retargeting keeps the displayed role colors as the next start`() {
        val from = DefaultDarkColorPalette
        val first = from.lerpTo(ModernBlackColorPalette, .5f)
        val target = PureBlackColorPalette
        val next = first.lerpTo(target, .25f)

        assertEquals(lerp(from.primaryButton, Color(0xFF272727), .5f), first.primaryButton)
        assertSame(first, first.lerpTo(target, 0f))
        assertEquals(lerp(first.favoritesIcon, target.favoritesIcon, .25f), next.favoritesIcon)
        assertEquals(lerp(first.primaryButton, target.primaryButton, .25f), next.primaryButton)
    }
}
