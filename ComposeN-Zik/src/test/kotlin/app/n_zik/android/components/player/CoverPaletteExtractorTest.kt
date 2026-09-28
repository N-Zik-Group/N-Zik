package app.n_zik.android.components.player

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils.colorToHSL
import androidx.palette.graphics.Palette
import app.it.fast4x.rimusic.ui.screens.player.computePlayerDynamicPalette
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import app.it.fast4x.rimusic.ui.styling.hsl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit coverage for the shared M3E cover-color extractor used by the player background
 * (`CoverColor` / `CoverColorGradient`), the lyrics screen and the visualizer ("cover" color
 * options).
 *
 * Robolectric is required (not a plain JVM unit test) because `Bitmap`/`Palette` need real pixel
 * backing to generate a deterministic swatch -- the same technique
 * `PlayerDynamicPaletteOffMainTest` uses for an equivalent palette extraction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CoverPaletteExtractorTest {

    private fun solidBitmap(color: Int, size: Int = 16): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { color }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /**
     * Two-region bitmap (verified empirically under Robolectric 33): [firstRatio]% [first] (the
     * dominant region) + the rest [second]. Solid bitmaps cannot distinguish the
     * dominant/vibrant hue sources, so the hue-source regressions are pinned on this fixture.
     */
    private fun twoRegionBitmap(first: Int, second: Int, firstRatio: Int = 70, size: Int = 32): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { index ->
            if (index < (size * size * firstRatio) / 100) first else second
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /** 70% rgb(150, 110, 170) (dominant region) + 30% rgb(100, 30, 200) (vibrant region). */
    private fun twoRegionBitmap(): Bitmap =
        twoRegionBitmap(android.graphics.Color.rgb(150, 110, 170), android.graphics.Color.rgb(100, 30, 200))

    @Test
    fun `extractM3ECoverColors returns the same 7 swatches as computePlayerDynamicPalette`() = runBlocking {
        for (isDark in listOf(true, false)) {
            // Solid blue bitmap: every swatch is the same color, so the parity assertions below
            // are exact (no get*Color fallback is used).
            val bitmap = solidBitmap(android.graphics.Color.rgb(30, 120, 200))

            val colors = extractM3ECoverColors(bitmap, isDark)
            val reference = computePlayerDynamicPalette(bitmap, isDark, DefaultDarkColorPalette)

            assertNotNull("expected 7 swatches for a solid bitmap (isDark=$isDark)", colors)
            assertEquals(reference.dominant, colors!!.dominant)
            assertEquals(reference.vibrant, colors.vibrant)
            assertEquals(reference.lightVibrant, colors.lightVibrant)
            assertEquals(reference.darkVibrant, colors.darkVibrant)
            assertEquals(reference.muted, colors.muted)
            assertEquals(reference.lightMuted, colors.lightMuted)
            assertEquals(reference.darkMuted, colors.darkMuted)
        }
    }

    /**
     * Null contract (matrix row `ECHOU_EXTRATION`): `extractM3ECoverColors` (and with it
     * `m3eDynamicColorPaletteOf`) returns `null` exactly when the legacy null gate
     * `dynamicColorPaletteOf` cannot derive a dominant swatch — an all-transparent bitmap is
     * the trigger. The gate's null result is asserted first as a fixture premise, so any
     * Robolectric/palette behavior change fails loudly at the premise instead of masking a
     * contract regression. Callers then keep their own fallbacks (violet MainActivity,
     * DKGRAY lyrics, dominant local visualizer).
     */
    @Test
    fun `extractM3ECoverColors and m3eDynamicColorPaletteOf return null without a dominant swatch`() = runBlocking {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        for (isDark in listOf(true, false)) {
            assertNull(
                "fixture premise: the legacy null gate must yield no dominant swatch for a transparent bitmap (isDark=$isDark)",
                dynamicColorPaletteOf(bitmap, isDark),
            )
            assertNull(
                "extractM3ECoverColors must propagate the null gate (isDark=$isDark)",
                extractM3ECoverColors(bitmap, isDark),
            )
            assertNull(
                "m3eDynamicColorPaletteOf must propagate the null gate (isDark=$isDark)",
                m3eDynamicColorPaletteOf(bitmap, isDark),
            )
        }
    }

    @Test
    fun `m3eDynamicColorPaletteOf builds the capped palette from the dominant swatch`() = runBlocking {
        for (isDark in listOf(true, false)) {
            // Solid blue bitmap: every swatch is the same color, so the expected palette is exact.
            val bitmap = solidBitmap(android.graphics.Color.rgb(30, 120, 200))
            val dominant = Palette.from(bitmap).maximumColorCount(8).generate().getDominantColor(0)
            val dominantHsl = FloatArray(3)
            colorToHSL(dominant, dominantHsl)

            val result = m3eDynamicColorPaletteOf(bitmap, isDark)

            assertNotNull("expected a dominant-based palette for a solid bitmap (isDark=$isDark)", result)
            assertEquals(dynamicColorPaletteOf(dominantHsl, isDark), result)
            // For a colored cover the app's result is the RiPlay reference itself.
            assertEquals(dynamicColorPaletteOf(bitmap, isDark), result)
        }
    }

    @Test
    fun `m3eDynamicColorPaletteOf accent keeps the dominant hue with capped saturation and fixed lightness`() = runBlocking {
        val bitmap = solidBitmap(android.graphics.Color.rgb(200, 30, 30))

        val result = m3eDynamicColorPaletteOf(bitmap, false)

        assertNotNull(result)
        val accent = result!!.accent.hsl
        // rgb(200, 30, 30) -> HSL(0, ~0.739, ~0.451): accent caps saturation at 0.5, fixed lightness 0.5
        assertEquals(0f, accent.hue, 5f)
        assertEquals(0.5f, accent.saturation, 0.01f)
        assertEquals(0.5f, accent.lightness, 0.01f)
    }

    /**
     * Contract for the flat "Match song cover" player background (`Player.kt`,
     * `PlayerBackgroundColors.CoverColor`): its single color is
     * `m3eSaturate(dominant, lightTheme).m3eDarkenBy(lightTheme)` -- so it renders the cover's
     * dominant hue, the same hue the app's dynamic accent is built from (RiPlay reference,
     * OQ2=B). The expected colors are derived independently from the extracted swatch's own HSL
     * (via [hslToRgb], not via `m3eSaturate`/`m3eDarkenBy`).
     */
    @Test
    fun `flat match song cover background equals the dominant swatch color`() = runBlocking {
        // Reddish (hue ~0); solid bitmap -> every swatch is the bitmap color.
        val bitmap = solidBitmap(android.graphics.Color.rgb(200, 30, 30))
        val colors = extractM3ECoverColors(bitmap, false)
        assertNotNull("expected swatches for a solid bitmap", colors)

        val swatchHsl = FloatArray(3)
        colorToHSL(colors!!.dominant, swatchHsl)
        val hue = swatchHsl[0]
        val saturation = swatchHsl[1]
        val lightness = swatchHsl[2]
        // Solid reddish bitmap yields a saturated swatch (S >= 0.1), so the dark-theme
        // saturation boost applies in the expectations below
        assertTrue("expected a saturated swatch for a solid reddish bitmap", saturation >= 0.1f)

        // Dark theme: saturation +0.35 (the swatch is saturated) clamped at 1.0, lightness kept,
        // then RGB x0.5
        val dark = m3eSaturate(colors.dominant, lightTheme = false).m3eDarkenBy(lightTheme = false)
        val expectedDark = hslToRgb(hue, saturation + 0.35f, lightness, darken = 0.5f)
        assertEquals(expectedDark.red, dark.red, 0.01f)
        assertEquals(expectedDark.green, dark.green, 0.01f)
        assertEquals(expectedDark.blue, dark.blue, 0.01f)

        // Light theme: no saturation boost, lightness raised to at least 0.5, no darkening
        val light = m3eSaturate(colors.dominant, lightTheme = true).m3eDarkenBy(lightTheme = true)
        val expectedLight = hslToRgb(hue, saturation, lightness.coerceAtLeast(0.5f), darken = 1f)
        assertEquals(expectedLight.red, light.red, 0.01f)
        assertEquals(expectedLight.green, light.green, 0.01f)
        assertEquals(expectedLight.blue, light.blue, 0.01f)
    }

    /** Independent HSL -> RGB reference used to compute expected colors in the tests above. */
    private fun hslToRgb(hue: Float, saturation: Float, lightness: Float, darken: Float): Color {
        val s = saturation.coerceIn(0f, 1f)
        val l = lightness.coerceIn(0f, 1f)
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs(((hue / 60f) % 2f) - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color((r + m) * darken, (g + m) * darken, (b + m) * darken)
    }

    @Test
    fun `m3eSaturate boosts saturation by 0_35 in dark theme when the input is saturated`() {
        // rgb(200, 30, 30) -> HSL(0, ~0.739, ~0.451); 0.739 + 0.35 > 1, so S is clamped to 1.0
        val result = m3eSaturate(android.graphics.Color.rgb(200, 30, 30), lightTheme = false)

        val hsl = result.hsl
        assertEquals(0f, hsl.hue, 5f)
        assertEquals(1f, hsl.saturation, 0.01f)
        assertEquals(0.451f, hsl.lightness, 0.01f)
    }

    @Test
    fun `m3eSaturate leaves low-saturation colors untouched in dark theme`() {
        // rgb(128, 126, 124) -> HSL(~30, ~0.016, ~0.494); S < 0.1 -> no boost, L unchanged
        val result = m3eSaturate(android.graphics.Color.rgb(128, 126, 124), lightTheme = false)

        val hsl = result.hsl
        assertEquals(0.016f, hsl.saturation, 0.01f)
        assertEquals(0.494f, hsl.lightness, 0.01f)
    }

    @Test
    fun `m3eSaturate forces lightness to at least 0_5 in light theme`() {
        // rgb(20, 60, 120) -> HSL(216, ~0.714, ~0.275); light theme -> S unchanged, L raised to 0.5
        val result = m3eSaturate(android.graphics.Color.rgb(20, 60, 120), lightTheme = true)

        val hsl = result.hsl
        assertEquals(216f, hsl.hue, 5f)
        assertEquals(0.714f, hsl.saturation, 0.01f)
        assertEquals(0.5f, hsl.lightness, 0.01f)
    }

    @Test
    fun `m3eDarkenBy halves RGB in dark theme and keeps the color in light theme`() {
        val input = Color(0.8f, 0.4f, 0.2f, 1f)

        val dark = input.m3eDarkenBy(lightTheme = false)
        // Compose Color stores 8-bit components, so quantization drifts up to 1/255 (~0.004)
        // from the ideal float values (e.g. 0.2f * 0.5f -> 26/255).
        assertEquals(0.4f, dark.red, 0.01f)
        assertEquals(0.2f, dark.green, 0.01f)
        assertEquals(0.1f, dark.blue, 0.01f)
        assertEquals(1f, dark.alpha, 0.001f)

        val light = input.m3eDarkenBy(lightTheme = true)
        assertEquals(input, light)
    }

    /**
     * Contract pinned on the two-region fixture (dominant ≠ vibrant): the app's dynamic palette
     * is built from the **dominant** swatch and equals the legacy RiPlay reference
     * `dynamicColorPaletteOf(bitmap, isDark)` bit-for-bit -- NOT the vibrant-based palette the
     * pre-2026-09-28 code produced.
     */
    @Test
    fun `m3eDynamicColorPaletteOf matches the RiPlay reference on a two-region cover, dominant not vibrant`() = runBlocking {
        for (isDark in listOf(true, false)) {
            val bitmap = twoRegionBitmap()
            val swatchPalette = Palette.from(bitmap).maximumColorCount(8).generate()
            val dominant = swatchPalette.getDominantColor(0)
            val vibrant = swatchPalette.getVibrantColor(0)
            val dominantHsl = FloatArray(3)
            colorToHSL(dominant, dominantHsl)
            val vibrantHsl = FloatArray(3)
            colorToHSL(vibrant, vibrantHsl)

            val reference = dynamicColorPaletteOf(bitmap, isDark)
            val vibrantBased = dynamicColorPaletteOf(vibrantHsl, isDark)
            val result = m3eDynamicColorPaletteOf(bitmap, isDark)

            assertNotNull("expected a palette from the RiPlay reference (isDark=$isDark)", reference)
            assertNotNull(result)
            assertNotEquals(
                "fixture must distinguish the hue sources (dominant-based != vibrant-based, isDark=$isDark)",
                reference,
                vibrantBased,
            )
            assertEquals(
                "the app's palette must be the RiPlay reference (isDark=$isDark)",
                reference,
                result,
            )
            assertEquals(
                "the palette must be built from the dominant swatch's HSL (isDark=$isDark)",
                dynamicColorPaletteOf(dominantHsl, isDark),
                result,
            )
        }
    }

    /**
     * RiPlay rescue (DOMINANT_PEU_SATURE edge case): a non-achromatic cover (maximum swatch
     * channel spread ≥ threshold) whose dominant swatch is below the S<0.08 rescue threshold
     * takes its hue from the most saturated non-zero swatch instead -- pinned against the legacy
     * reference, which performs the rescue, and against the no-rescue dominant-based palette,
     * which it must NOT produce.
     */
    @Test
    fun `low-saturation dominant is rescued to the most saturated swatch, matching the RiPlay reference`() = runBlocking {
        // 70% near-neutral mid-tone (S < 0.08, own channel spread < 0.10) + 30% saturated red
        // (spread ≈ 0.74): the cover is NOT achromatic overall, so no neutralization -- but the
        // dominant's low saturation triggers the rescue to the red swatch.
        val bitmap = twoRegionBitmap(
            android.graphics.Color.rgb(150, 148, 146),
            android.graphics.Color.rgb(200, 30, 30),
        )
        for (isDark in listOf(true, false)) {
            val swatchPalette = Palette.from(bitmap).maximumColorCount(8).generate()
            val dominant = swatchPalette.getDominantColor(0)
            val dominantHsl = FloatArray(3)
            colorToHSL(dominant, dominantHsl)
            // Fixture premises: the dominant is below the rescue threshold while the cover is
            // not achromatic (the red region keeps the max channel spread above the threshold).
            assertTrue("fixture premise: dominant S < 0.08 (isDark=$isDark)", dominantHsl[1] < 0.08f)
            assertTrue(
                "fixture premise: cover is not achromatic (isDark=$isDark)",
                swatchPalette.swatches.maxOf { channelDelta(it.rgb) } >= ACHROMATIC_CHANNEL_DELTA_THRESHOLD,
            )

            val reference = dynamicColorPaletteOf(bitmap, isDark)
            val noRescueDominantBased = dynamicColorPaletteOf(dominantHsl, isDark)
            val result = m3eDynamicColorPaletteOf(bitmap, isDark)

            assertNotNull("expected a palette from the RiPlay reference (isDark=$isDark)", reference)
            assertNotNull(result)
            assertNotEquals(
                "the rescue must change the result versus the no-rescue dominant palette (isDark=$isDark)",
                noRescueDominantBased,
                result,
            )
            assertEquals("the rescue must match the RiPlay reference (isDark=$isDark)", reference, result)
            // !! is safe here: `result` was asserted non-null two lines above.
            val accentHue = result!!.accent.hsl
            assertEquals("the rescue must pick the red swatch's hue (isDark=$isDark)", 0f, accentHue.hue, 5f)
        }
    }

    @Test
    fun `m3eCoverBackgroundColor renders the dominant swatch, not the vibrant one`() = runBlocking {
        val bitmap = twoRegionBitmap()
        val colors = extractM3ECoverColors(bitmap, false)
        assertNotNull("expected swatches for a two-region bitmap", colors)

        val vibrant = colors!!.vibrant
        val dominant = colors.dominant
        assertNotEquals(
            "fixture must yield distinct dominant/vibrant swatches",
            m3eSaturate(dominant, lightTheme = false),
            m3eSaturate(vibrant, lightTheme = false),
        )

        assertEquals(
            m3eSaturate(dominant, lightTheme = false).m3eDarkenBy(lightTheme = false),
            m3eCoverBackgroundColor(dominant, lightTheme = false),
        )
        assertNotEquals(
            m3eSaturate(vibrant, lightTheme = false).m3eDarkenBy(lightTheme = false),
            m3eCoverBackgroundColor(dominant, lightTheme = false),
        )
    }

    @Test
    fun `m3eCoverForegroundArgb uses the dominant swatch, not the vibrant one`() = runBlocking {
        val bitmap = twoRegionBitmap()
        val colors = extractM3ECoverColors(bitmap, false)
        assertNotNull("expected swatches for a two-region bitmap", colors)

        val vibrant = colors!!.vibrant
        val dominant = colors.dominant
        assertNotEquals(
            "fixture must yield distinct dominant/vibrant swatches",
            m3eSaturate(dominant, lightTheme = false).toArgb(),
            m3eSaturate(vibrant, lightTheme = false).toArgb(),
        )

        assertEquals(
            m3eSaturate(dominant, lightTheme = false).toArgb(),
            m3eCoverForegroundArgb(dominant, lightTheme = false),
        )
        assertNotEquals(
            m3eSaturate(vibrant, lightTheme = false).toArgb(),
            m3eCoverForegroundArgb(dominant, lightTheme = false),
        )
    }
}
