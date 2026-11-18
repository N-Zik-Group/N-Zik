package app.n_zik.android

import androidx.compose.ui.graphics.Color
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeSurfaceColorTest {
    @Test
    fun namedBlackPreferencesRetainTheirSurfacePolicyWhenForegroundCopiesThePalette() {
        val copiedPalette = DefaultDarkColorPalette.copy(text = Color.White)
        for (name in listOf(ColorPaletteName.PureBlack, ColorPaletteName.ModernBlack)) {
            val blackPalette = hasBlackPaletteSurfaces(name, copiedPalette)
            assertTrue(blackPalette)
            assertEquals(Color(0xFF1A1A1A).copy(alpha = .90f),
                nativeSurfaceColor(Color.Red, ColorPaletteMode.Dark, blackPalette, navigation = true))
        }
        assertFalse(hasBlackPaletteSurfaces(ColorPaletteName.Dynamic, copiedPalette))
    }

    @Test
    fun pureBlackKeepsNativePlatesOpaqueWhileNavigationUsesNinetyPercentCoverage() {
        for (blackPalette in listOf(false, true)) {
            assertEquals(Color.Black, nativeSurfaceColor(Color.Red.copy(alpha = .63f), ColorPaletteMode.PureBlack, blackPalette))
            assertEquals(Color.Black, nativeSurfaceColor(Color.Transparent, ColorPaletteMode.PureBlack, blackPalette, recessed = true))
            assertEquals(Color.Black.copy(alpha = .90f), nativeSurfaceColor(Color.Green.copy(alpha = .90f), ColorPaletteMode.PureBlack, blackPalette, navigation = true))
        }
    }

    @Test
    fun existingPitchBlackAndNamedBlackPalettesRetainElevatedRecessedAndNavigationPolicies() {
        val legacyModes = ColorPaletteMode.entries.filter { it != ColorPaletteMode.PureBlack }
        for (mode in legacyModes) {
            for (blackPalette in listOf(false, true)) {
                if (mode != ColorPaletteMode.PitchBlack && !blackPalette) continue
                assertEquals(Color(0xFF1A1A1A), nativeSurfaceColor(Color.Red, mode, blackPalette))
                assertEquals(Color(0xFF101010), nativeSurfaceColor(Color.Red, mode, blackPalette, recessed = true))
                assertEquals(Color(0xFF1A1A1A).copy(alpha = .90f),
                    nativeSurfaceColor(Color.Red, mode, blackPalette, navigation = true))
            }
        }
    }

    @Test
    fun existingOrdinaryModesPreserveTheirInputIncludingOpacity() {
        val base = Color.Red.copy(alpha = .63f)
        for (mode in listOf(ColorPaletteMode.Light, ColorPaletteMode.Dark, ColorPaletteMode.System)) {
            assertEquals(base, nativeSurfaceColor(base, mode, false))
            assertEquals(base, nativeSurfaceColor(base, mode, false, recessed = true))
            assertEquals(base, nativeSurfaceColor(base, mode, false, navigation = true))
        }
    }
}
