package app.n_zik.android.components.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils.colorToHSL
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the pure contrast helpers of spec-achromatic-ramp-luminance-cap (2026-09-28):
 * [textOutlineColor] (halo keyed on the effective tone), [monochromeControlsColor] (the
 * effective palette's text), [placeholderCompositeBase] (the placeholder composite base) and —
 * loopback 2, EC-1 — [m3eRecapRestoredDynamicPalette] (the one-shot re-cap of a restored
 * dynamic palette, which the Saver `ColorPalette.Companion` rebuilds through the legacy
 * non-capped `dynamicColorPaletteOf`).
 *
 * Robolectric is required because [m3eRecapRestoredDynamicPalette] delegates to
 * [m3eCapAchromaticBackgrounds], whose HSL round-trips need the real
 * `androidx.core.graphics.ColorUtils` (not a plain JVM unit test).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CoverContrastColorsTest {

    private fun lightnessOf(color: Color): Float {
        val hsl = FloatArray(3)
        colorToHSL(color.toArgb(), hsl)
        return hsl[2]
    }

    /**
     * The exact loopback-2 scenario: the Saver's legacy non-capped rebuild of a light-tone ramp
     * (bg0 0.925 / bg1 0.90 / bg2 0.85, bg3/4 0.955 inherited from the default light palette).
     */
    private fun restoredLightToneRamp(): ColorPalette = ColorPalette(
        background0 = Color.hsl(0f, 0f, 0.925f),
        background1 = Color.hsl(0f, 0f, 0.9f),
        background2 = Color.hsl(0f, 0f, 0.85f),
        background3 = Color.hsl(0f, 0f, 0.955f),
        background4 = Color.hsl(0f, 0f, 0.955f),
        accent = Color.hsl(0f, 0f, 0.5f),
        onAccent = Color.Black,
        text = Color.hsl(0f, 0f, 0.12f),
        textSecondary = Color.hsl(0f, 0f, 0.4f),
        textDisabled = Color.hsl(0f, 0f, 0.65f),
        isDark = false,
        iconButtonPlayer = Color.hsl(0f, 0f, 0.12f),
    )

    /**
     * The mirror scenario: the legacy non-capped rebuild of a dark-tone ramp (bg0 0.10 /
     * bg1 0.15 / bg2 0.20, bg3/4 inherited from the default dark palette).
     */
    private fun restoredDarkToneRamp(): ColorPalette = ColorPalette(
        background0 = Color.hsl(0f, 0f, 0.1f),
        background1 = Color.hsl(0f, 0f, 0.15f),
        background2 = Color.hsl(0f, 0f, 0.2f),
        background3 = Color.hsl(0f, 0f, 0.3f),
        background4 = Color.hsl(0f, 0f, 0.2f),
        accent = Color.hsl(0f, 0f, 0.5f),
        onAccent = Color.White,
        text = Color.hsl(0f, 0f, 0.88f),
        textSecondary = Color.hsl(0f, 0f, 0.65f),
        textDisabled = Color.hsl(0f, 0f, 0.4f),
        isDark = true,
        iconButtonPlayer = Color.hsl(0f, 0f, 0.88f),
    )

    @Test
    fun `textOutlineColor is a white halo on a light tone and a black halo on a dark tone`() {
        assertEquals(Color.White.copy(alpha = 0.5f), textOutlineColor(effectiveIsDark = false, alpha = 0.5f))
        assertEquals(Color.Black.copy(alpha = 0.5f), textOutlineColor(effectiveIsDark = true, alpha = 0.5f))
        // The call-site alpha is preserved (the kreate ActionBar uses 0.65f).
        assertEquals(Color.White.copy(alpha = 0.65f), textOutlineColor(effectiveIsDark = false, alpha = 0.65f))
        assertEquals(Color.Black.copy(alpha = 0.65f), textOutlineColor(effectiveIsDark = true, alpha = 0.65f))
    }

    @Test
    fun `monochromeControlsColor is the effective palette text`() {
        val light = restoredLightToneRamp()
        val dark = restoredDarkToneRamp()
        assertEquals(light.text, monochromeControlsColor(light))
        assertEquals(dark.text, monochromeControlsColor(dark))
    }

    @Test
    fun `placeholderCompositeBase is white on a dark tone and black on a light tone`() {
        assertEquals(Color.White, placeholderCompositeBase(effectiveIsDark = true))
        assertEquals(Color.Black, placeholderCompositeBase(effectiveIsDark = false))
    }

    @Test
    fun `the restored dynamic light-tone ramp is re-capped for a dark theme`() {
        // The Saver restored the ramp through the legacy non-capped dynamicColorPaletteOf:
        // re-capping must bring bg3/4 (the real max, 0.955) down to exactly the 0.80 ceiling
        // with a uniform shift -- no near-white window before the next extraction (EC-1).
        val restored = restoredLightToneRamp()
        val recapped = restored.m3eRecapRestoredDynamicPalette(ColorPaletteName.Dynamic, themeIsDark = true)

        assertEquals("bg3 must land exactly on the ceiling", ACHROMATIC_RAMP_LIGHT_TONE_MAX_LIGHTNESS, lightnessOf(recapped.background3), 0.02f)
        assertEquals("bg4 must land exactly on the ceiling", ACHROMATIC_RAMP_LIGHT_TONE_MAX_LIGHTNESS, lightnessOf(recapped.background4), 0.02f)
        assertEquals("bg0 must keep its relative spacing (0.925 - 0.155)", 0.77f, lightnessOf(recapped.background0), 0.02f)
        // The re-cap touches only the backgrounds.
        assertEquals(restored.text, recapped.text)
        assertEquals(restored.accent, recapped.accent)
    }

    @Test
    fun `the restored dynamic dark-tone ramp is re-floored for a light theme`() {
        val restored = restoredDarkToneRamp()
        val recapped = restored.m3eRecapRestoredDynamicPalette(ColorPaletteName.Dynamic, themeIsDark = false)

        assertEquals("bg0 must land exactly on the floor", ACHROMATIC_RAMP_DARK_TONE_MIN_LIGHTNESS, lightnessOf(recapped.background0), 0.02f)
        assertEquals("bg3 must keep its relative spacing (0.30 + 0.20)", 0.5f, lightnessOf(recapped.background3), 0.02f)
        assertEquals(restored.text, recapped.text)
        assertEquals(restored.accent, recapped.accent)
    }

    @Test
    fun `matching tones restore no-op same instance`() {
        val light = restoredLightToneRamp()
        assertSame(
            "a matching light tone must return the very same instance",
            light,
            light.m3eRecapRestoredDynamicPalette(ColorPaletteName.Dynamic, themeIsDark = false)
        )
        val dark = restoredDarkToneRamp()
        assertSame(
            "a matching dark tone must return the very same instance",
            dark,
            dark.m3eRecapRestoredDynamicPalette(ColorPaletteName.Dynamic, themeIsDark = true)
        )
    }

    @Test
    fun `static palettes are never re-capped`() {
        // The cap applies only to cover-derived (Dynamic) palettes: any other saved name gets
        // the very same instance back, even when the tone mismatches the theme.
        val light = restoredLightToneRamp()
        assertSame(
            "a static palette must be returned unchanged",
            light,
            light.m3eRecapRestoredDynamicPalette(ColorPaletteName.PureBlack, themeIsDark = true)
        )
        val dark = restoredDarkToneRamp()
        assertSame(
            "a static palette must be returned unchanged",
            dark,
            dark.m3eRecapRestoredDynamicPalette(ColorPaletteName.ModernBlack, themeIsDark = false)
        )
    }
}
