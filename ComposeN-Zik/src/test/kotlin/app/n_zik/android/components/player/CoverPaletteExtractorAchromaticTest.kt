package app.n_zik.android.components.player

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils.colorToHSL
import androidx.palette.graphics.Palette
import app.it.fast4x.rimusic.ui.screens.player.computePlayerDynamicPalette
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit coverage for the achromatic neutralization guard (renegotiated `NEUTRAL_COVER` case,
 * 2026-09-19 — session `problem-solution-2026-09-19`, solution S3') and for the achromatic
 * ramp luminance cap (spec-achromatic-ramp-luminance-cap, 2026-09-28): nearly achromatic covers
 * must render a neutral gray following the cover's lightness on every cover-based surface, and
 * when that tone mismatches the theme the five ramp backgrounds must be capped (light tone in a
 * dark theme, lightest background = L 0.80) / floored (dark tone in a light theme, darkest
 * background = L 0.30) by a uniform shift keyed on the real max/min lightness -- while
 * colored/pastel covers must be extracted bit-identically to the pre-guard behavior
 * (strict M3E parity).
 *
 * Robolectric is required (not a plain JVM unit test) because `Bitmap`/`Palette` need real pixel
 * backing to generate a deterministic swatch -- the same technique
 * `CoverPaletteExtractorTest` uses. Fixture sizes are 32x32 to match the CIS probe's measured
 * values, and fixture channel spreads keep a comfortable margin below
 * [ACHROMATIC_CHANNEL_DELTA_THRESHOLD] so palette quantization cannot flip the guard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CoverPaletteExtractorAchromaticTest {

    private fun solidBitmap(color: Int, size: Int = 32): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { color }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    private fun twoRegionBitmap(first: Int, second: Int, firstRatio: Int = 70, size: Int = 32): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { index ->
            if (index < (size * size * firstRatio) / 100) first else second
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    private fun hslOf(rgb: Int): FloatArray {
        val hsl = FloatArray(3)
        colorToHSL(rgb, hsl)
        return hsl
    }

    private fun M3ECoverColors.swatches(): List<Int> =
        listOf(dominant, vibrant, lightVibrant, darkVibrant, muted, lightMuted, darkMuted)

    /**
     * The 7 swatches as extracted without the guard (independent reference, pre-guard parity) —
     * on the same RiPlay-based extraction base as the extractor: capped 8 palette
     * (`maximumColorCount(8)`), each role falling back to the dynamic palette's accent.
     */
    private fun preGuardSwatches(bitmap: Bitmap, isDark: Boolean): M3ECoverColors? {
        val palette = dynamicColorPaletteOf(bitmap, isDark) ?: return null
        val swatchPalette = Palette.from(bitmap).maximumColorCount(8).generate()
        val fallback = palette.accent.toArgb()
        return M3ECoverColors(
            dominant = swatchPalette.getDominantColor(fallback),
            vibrant = swatchPalette.getVibrantColor(fallback),
            lightVibrant = swatchPalette.getLightVibrantColor(fallback),
            darkVibrant = swatchPalette.getDarkVibrantColor(fallback),
            muted = swatchPalette.getMutedColor(fallback),
            lightMuted = swatchPalette.getLightMutedColor(fallback),
            darkMuted = swatchPalette.getDarkMutedColor(fallback),
        )
    }

    @Test
    fun `channelDelta is the normalized max-min channel spread and is lightness independent`() {
        assertEquals(0f, channelDelta(android.graphics.Color.rgb(160, 160, 160)), 0.001f)
        // The same 7-channel spread must read identically near white (L≈0.9) and near dark (L≈0.23)
        // -- unlike HSV saturation, which inflates toward the extremes.
        assertEquals(
            channelDelta(android.graphics.Color.rgb(235, 233, 228)),
            channelDelta(android.graphics.Color.rgb(60, 58, 53)),
            0.001f
        )
        assertEquals(7f / 255f, channelDelta(android.graphics.Color.rgb(235, 233, 228)), 0.001f)
        // A colored cover is well above the threshold, a near-white cover is far below it
        assertTrue(
            channelDelta(android.graphics.Color.rgb(30, 120, 200)) >= ACHROMATIC_CHANNEL_DELTA_THRESHOLD
        )
        assertTrue(
            channelDelta(android.graphics.Color.rgb(235, 233, 228)) < ACHROMATIC_CHANNEL_DELTA_THRESHOLD
        )
    }

    @Test
    fun `near achromatic covers are neutralized to a gray following the cover lightness`() = runBlocking {
        val achromaticCases = listOf(
            "pure light gray" to android.graphics.Color.rgb(160, 160, 160),
            "dark gray (type Angels)" to android.graphics.Color.rgb(58, 56, 54),
            "off-white" to android.graphics.Color.rgb(235, 233, 228),
            "warm-tinted gray" to android.graphics.Color.rgb(168, 159, 152),
            "cool-tinted gray" to android.graphics.Color.rgb(152, 159, 168),
            "dark gray tinted rose" to android.graphics.Color.rgb(58, 52, 54),
        )
        for ((name, color) in achromaticCases) {
            for (isDark in listOf(true, false)) {
                val bitmap = solidBitmap(color)
                val colors = extractM3ECoverColors(bitmap, isDark)
                assertNotNull("expected swatches for $name (isDark=$isDark)", colors)

                val dominantLuminance = hslOf(colors!!.dominant)[2]
                for (rgb in colors.swatches()) {
                    assertEquals(
                        "every swatch must be a neutral gray for $name (isDark=$isDark)",
                        0f,
                        hslOf(rgb)[1],
                        0.001f
                    )
                }

                // Each swatch keeps its own lightness, except the ones that fell back to the
                // legacy mid-tone accent, which take the dominant (cover) lightness instead.
                val preGuard = preGuardSwatches(bitmap, isDark)!!
                val fallback = dynamicColorPaletteOf(bitmap, isDark)!!.accent.toArgb()
                for ((after, before) in colors.swatches().zip(preGuard.swatches())) {
                    val expectedLightness = if (before == fallback) {
                        dominantLuminance
                    } else {
                        hslOf(before)[2]
                    }
                    assertEquals(
                        "lightness must follow the cover for $name (isDark=$isDark)",
                        expectedLightness,
                        hslOf(after)[2],
                        0.02f
                    )
                }
            }
        }
    }

    @Test
    fun `each non-fallback swatch keeps its own lightness`() = runBlocking {
        // Two achromatic regions far apart in lightness: the palette keeps both swatches, so the
        // guard must preserve each one's own lightness (and only remap the fallback swatches).
        val bitmap = twoRegionBitmap(android.graphics.Color.rgb(200, 200, 200), android.graphics.Color.rgb(60, 60, 60), 50)
        val colors = extractM3ECoverColors(bitmap, false)
        assertNotNull("expected swatches for a two-region achromatic bitmap", colors)

        val preGuard = preGuardSwatches(bitmap, false)!!
        val fallback = dynamicColorPaletteOf(bitmap, false)!!.accent.toArgb()
        var checkedOwnLightness = 0
        for ((after, before) in colors!!.swatches().zip(preGuard.swatches())) {
            assertEquals(0f, hslOf(after)[1], 0.001f)
            val expectedLightness = if (before == fallback) hslOf(preGuard.dominant)[2] else hslOf(before)[2]
            assertEquals(expectedLightness, hslOf(after)[2], 0.02f)
            if (before != fallback) checkedOwnLightness++
        }
        assertTrue("fixture must exercise at least one non-fallback swatch", checkedOwnLightness >= 1)
    }

    @Test
    fun `colored and pastel covers are extracted unchanged`() = runBlocking {
        val coloredCases = listOf(
            "solid blue" to solidBitmap(android.graphics.Color.rgb(30, 120, 200)),
            "solid red" to solidBitmap(android.graphics.Color.rgb(200, 30, 30)),
            "two-region colored" to twoRegionBitmap(
                android.graphics.Color.rgb(150, 110, 170),
                android.graphics.Color.rgb(100, 30, 200)
            ),
        )
        for ((name, bitmap) in coloredCases) {
            for (isDark in listOf(true, false)) {
                val colors = extractM3ECoverColors(bitmap, isDark)
                val preGuard = preGuardSwatches(bitmap, isDark)
                assertNotNull("expected swatches for $name (isDark=$isDark)", colors)
                assertEquals(
                    "must be bit-identical to the pre-guard extraction for $name (isDark=$isDark)",
                    preGuard,
                    colors
                )
            }
        }
    }

    @Test
    fun `m3eNeutralizeIfAchromatic is a no-op above the threshold and neutralizes below it`() = runBlocking {
        val achromaticPalette = Palette.from(solidBitmap(android.graphics.Color.rgb(120, 118, 116))).generate()
        val fallbackArgb = 0xFF808080.toInt()
        val sample = M3ECoverColors(
            android.graphics.Color.rgb(120, 118, 116),
            fallbackArgb,
            0xFF909090.toInt(),
            0xFF707070.toInt(),
            0xFF858585.toInt(),
            0xFF888888.toInt(),
            0xFF7A7A7A.toInt(),
        )
        val neutralized = sample.m3eNeutralizeIfAchromatic(achromaticPalette, fallbackArgb)
        for (rgb in neutralized.swatches()) {
            assertEquals(0f, hslOf(rgb)[1], 0.001f)
        }

        val coloredPalette = Palette.from(solidBitmap(android.graphics.Color.rgb(30, 120, 200))).generate()
        assertTrue(
            "above the threshold the guard must return the very same instance",
            sample.m3eNeutralizeIfAchromatic(coloredPalette, 0) === sample
        )
    }

    @Test
    fun `computePlayerDynamicPalette applies the same achromatic guard as the shared extractor`() = runBlocking {
        // Gray + warm-tinted-gray regions: both regions are achromatic (Δ < threshold) so the
        // whole extraction must be neutralized on both the player's pipeline and the shared
        // extractor -- pinning the two paths together (regression guard for the raw swatches fed
        // to the morphing shapes, animated gradients and ColorPalette stripes).
        val bitmap = twoRegionBitmap(android.graphics.Color.rgb(160, 160, 160), android.graphics.Color.rgb(172, 165, 156))
        for (isDark in listOf(true, false)) {
            val result = computePlayerDynamicPalette(bitmap, isDark, DefaultDarkColorPalette)
            val shared = extractM3ECoverColors(bitmap, isDark)
            assertNotNull("expected swatches for a two-region achromatic bitmap (isDark=$isDark)", shared)
            assertEquals(
                "player swatches must be identical to the shared extractor's (isDark=$isDark)",
                shared,
                M3ECoverColors(
                    result.dominant,
                    result.vibrant,
                    result.lightVibrant,
                    result.darkVibrant,
                    result.muted,
                    result.lightMuted,
                    result.darkMuted,
                )
            )
            for (rgb in shared!!.swatches()) {
                assertEquals("player surface must render a neutral gray (isDark=$isDark)", 0f, hslOf(rgb)[1], 0.001f)
            }
        }
    }

    private fun ColorPalette.backgroundLightnesses(): List<Float> =
        listOf(background0, background1, background2, background3, background4)
            .map { background -> hslOf(background.toArgb())[2] }

    @Test
    fun `the dynamic theme tone follows the cover lightness for achromatic covers`() = runBlocking {
        val darkGray = solidBitmap(android.graphics.Color.rgb(58, 56, 54))
        val offWhite = solidBitmap(android.graphics.Color.rgb(235, 233, 228))

        // Light theme + dark cover: the tone must flip to dark (previously the theme stayed
        // white), and the floor lifts the darkest background (bg0, L 0.10) to L 0.30.
        val lightModeDarkCover = m3eDynamicColorPaletteOf(darkGray, false)
        assertNotNull("expected a palette for a dark achromatic cover (light theme)", lightModeDarkCover)
        assertEquals("dark cover must yield a dark tone", true, lightModeDarkCover!!.isDark)
        assertEquals(
            ACHROMATIC_RAMP_DARK_TONE_MIN_LIGHTNESS,
            hslOf(lightModeDarkCover.background0.toArgb())[2],
            0.02f
        )
        assertEquals(0.88f, hslOf(lightModeDarkCover.text.toArgb())[2], 0.02f)

        // Dark theme + off-white cover: the tone must flip to light, and the cap shifts all
        // five backgrounds so the lightest one lands exactly on the 0.80 ceiling -- the app
        // must never render near-white in dark mode.
        val darkModeOffWhiteCover = m3eDynamicColorPaletteOf(offWhite, true)
        assertNotNull("expected a palette for an off-white achromatic cover (dark theme)", darkModeOffWhiteCover)
        assertEquals("off-white cover must yield a light tone", false, darkModeOffWhiteCover!!.isDark)
        assertEquals(
            "the lightest background must land exactly on the ceiling",
            ACHROMATIC_RAMP_LIGHT_TONE_MAX_LIGHTNESS,
            darkModeOffWhiteCover.backgroundLightnesses().max(),
            0.02f
        )
        assertTrue(
            "no background may exceed the light-tone ceiling",
            darkModeOffWhiteCover.backgroundLightnesses().all {
                it <= ACHROMATIC_RAMP_LIGHT_TONE_MAX_LIGHTNESS + 0.02f
            }
        )
        assertEquals(0.12f, hslOf(darkModeOffWhiteCover.text.toArgb())[2], 0.02f)

        // Matching tones are unchanged.
        assertEquals(true, m3eDynamicColorPaletteOf(darkGray, true)!!.isDark)
        assertEquals(false, m3eDynamicColorPaletteOf(offWhite, false)!!.isDark)
    }

    @Test
    fun `the light-tone cap shifts all five backgrounds uniformly keyed on the real max lightness`() = runBlocking {
        // Regression guard (loopback 1, G1): the delta must be keyed on the REAL max lightness of
        // bg0-bg4, not on bg0 alone -- the five backgrounds all move by the same delta, and the
        // lightest one lands exactly on the ceiling.
        val offWhite = solidBitmap(android.graphics.Color.rgb(235, 233, 228))
        val colors = extractM3ECoverColors(offWhite, true)
        assertNotNull("expected swatches for the off-white fixture", colors)

        val preCap = dynamicColorPaletteOf(hslOf(colors!!.dominant), false)
        val capped = requireNotNull(m3eDynamicColorPaletteOf(offWhite, true)) { "expected a capped palette" }

        val delta = preCap.backgroundLightnesses().max() - ACHROMATIC_RAMP_LIGHT_TONE_MAX_LIGHTNESS
        assertTrue("the light tone in a dark theme must trigger a positive shift", delta > 0f)
        capped.backgroundLightnesses().zip(preCap.backgroundLightnesses()).forEach { (after, before) ->
            assertEquals(
                "every background must be shifted by exactly the same delta",
                before - delta,
                after,
                0.02f
            )
        }
        assertEquals(
            "the lightest background must land exactly on the ceiling",
            ACHROMATIC_RAMP_LIGHT_TONE_MAX_LIGHTNESS,
            capped.backgroundLightnesses().max(),
            0.02f
        )
    }

    @Test
    fun `the cap and the floor leave text, textSecondary, textDisabled and accent untouched`() = runBlocking {
        // Cap direction (dark theme + light tone).
        val offWhite = solidBitmap(android.graphics.Color.rgb(235, 233, 228))
        val whiteColors = extractM3ECoverColors(offWhite, true)
        assertNotNull("expected swatches for the off-white fixture", whiteColors)
        val preCap = dynamicColorPaletteOf(hslOf(whiteColors!!.dominant), false)
        val capped = requireNotNull(m3eDynamicColorPaletteOf(offWhite, true)) { "expected a capped palette" }
        assertEquals("text must be untouched by the cap", preCap.text, capped.text)
        assertEquals("textSecondary must be untouched by the cap", preCap.textSecondary, capped.textSecondary)
        assertEquals("textDisabled must be untouched by the cap", preCap.textDisabled, capped.textDisabled)
        assertEquals("accent must be untouched by the cap", preCap.accent, capped.accent)

        // Floor direction (light theme + dark tone).
        val darkGray = solidBitmap(android.graphics.Color.rgb(58, 56, 54))
        val grayColors = extractM3ECoverColors(darkGray, false)
        assertNotNull("expected swatches for the dark-gray fixture", grayColors)
        val preFloor = dynamicColorPaletteOf(hslOf(grayColors!!.dominant), true)
        val floored = requireNotNull(m3eDynamicColorPaletteOf(darkGray, false)) { "expected a floored palette" }
        assertEquals("text must be untouched by the floor", preFloor.text, floored.text)
        assertEquals("textSecondary must be untouched by the floor", preFloor.textSecondary, floored.textSecondary)
        assertEquals("textDisabled must be untouched by the floor", preFloor.textDisabled, floored.textDisabled)
        assertEquals("accent must be untouched by the floor", preFloor.accent, floored.accent)
    }

    @Test
    fun `matching tones are bit-identical to the pre-cap ramp`() = runBlocking {
        // Dark tone in a dark theme: the cap must be a no-op.
        val darkGray = solidBitmap(android.graphics.Color.rgb(58, 56, 54))
        val grayColors = extractM3ECoverColors(darkGray, true)
        assertNotNull("expected swatches for the dark-gray fixture", grayColors)
        assertEquals(
            "matching dark tones must be bit-identical to the uncapped ramp",
            dynamicColorPaletteOf(hslOf(grayColors!!.dominant), true),
            m3eDynamicColorPaletteOf(darkGray, true)
        )

        // Light tone in a light theme: the cap must be a no-op.
        val offWhite = solidBitmap(android.graphics.Color.rgb(235, 233, 228))
        val whiteColors = extractM3ECoverColors(offWhite, false)
        assertNotNull("expected swatches for the off-white fixture", whiteColors)
        assertEquals(
            "matching light tones must be bit-identical to the uncapped ramp",
            dynamicColorPaletteOf(hslOf(whiteColors!!.dominant), false),
            m3eDynamicColorPaletteOf(offWhite, false)
        )
    }

    @Test
    fun `colored covers are bit-identical at the palette level too`() = runBlocking {
        val blue = solidBitmap(android.graphics.Color.rgb(30, 120, 200))
        for (isDark in listOf(true, false)) {
            assertEquals(
                "colored covers must be bit-identical to the legacy reference (isDark=$isDark)",
                dynamicColorPaletteOf(blue, isDark),
                m3eDynamicColorPaletteOf(blue, isDark)
            )
        }
    }

    /**
     * A synthetic gray ramp with exact per-background HSL lightnesses, used to pin
     * [m3eCapAchromaticBackgrounds] directly (internal, module-scoped access).
     */
    private fun syntheticRamp(
        bg0: Float,
        bg1: Float,
        bg2: Float,
        bg3: Float,
        bg4: Float,
        toneIsDark: Boolean,
    ): ColorPalette {
        fun gray(lightness: Float) = Color.hsl(0f, 0f, lightness)
        return ColorPalette(
            background0 = gray(bg0),
            background1 = gray(bg1),
            background2 = gray(bg2),
            background3 = gray(bg3),
            background4 = gray(bg4),
            accent = gray(0.5f),
            onAccent = if (toneIsDark) Color.Black else Color.White,
            text = gray(if (toneIsDark) 0.88f else 0.12f),
            textSecondary = gray(if (toneIsDark) 0.65f else 0.4f),
            textDisabled = gray(if (toneIsDark) 0.4f else 0.65f),
            isDark = toneIsDark,
            iconButtonPlayer = gray(if (toneIsDark) 0.88f else 0.12f),
        )
    }

    @Test
    fun `the direct cap is a no-op same instance when the tone is in range or matching`() {
        // Light tone in a dark theme, but the lightest background is already below the ceiling.
        val inRangeLight = syntheticRamp(0.75f, 0.725f, 0.675f, 0.77f, 0.77f, toneIsDark = false)
        assertSame(
            "an in-range light tone must return the very same instance",
            inRangeLight,
            inRangeLight.m3eCapAchromaticBackgrounds(themeIsDark = true, toneIsDark = false)
        )

        // Dark tone in a light theme, but the darkest background is already above the floor.
        val inRangeDark = syntheticRamp(0.35f, 0.4f, 0.45f, 0.55f, 0.45f, toneIsDark = true)
        assertSame(
            "an in-range dark tone must return the very same instance",
            inRangeDark,
            inRangeDark.m3eCapAchromaticBackgrounds(themeIsDark = false, toneIsDark = true)
        )

        // Matching tones are never capped, even far out of range.
        val matchingLight = syntheticRamp(0.925f, 0.9f, 0.85f, 0.955f, 0.955f, toneIsDark = false)
        assertSame(
            "a matching light tone must return the very same instance",
            matchingLight,
            matchingLight.m3eCapAchromaticBackgrounds(themeIsDark = false, toneIsDark = false)
        )
        val matchingDark = syntheticRamp(0.1f, 0.15f, 0.2f, 0.314f, 0.2f, toneIsDark = true)
        assertSame(
            "a matching dark tone must return the very same instance",
            matchingDark,
            matchingDark.m3eCapAchromaticBackgrounds(themeIsDark = true, toneIsDark = true)
        )
    }

    @Test
    fun `the direct cap shifts all five backgrounds uniformly keyed on the real max`() {
        // Light tone in a dark theme where bg3/4 (0.955) are the real max -- the delta must be
        // keyed on them, not on bg0 (0.925): the G1 regression from loopback 1.
        val light = syntheticRamp(0.925f, 0.9f, 0.85f, 0.955f, 0.955f, toneIsDark = false)
        val capped = light.m3eCapAchromaticBackgrounds(themeIsDark = true, toneIsDark = false)

        val after = capped.backgroundLightnesses()
        assertEquals("bg0 must keep its relative spacing (0.925 - 0.155)", 0.77f, after[0], 0.02f)
        assertEquals("bg1 must keep its relative spacing (0.90 - 0.155)", 0.745f, after[1], 0.02f)
        assertEquals("bg2 must keep its relative spacing (0.85 - 0.155)", 0.695f, after[2], 0.02f)
        assertEquals("bg3 must land exactly on the ceiling", ACHROMATIC_RAMP_LIGHT_TONE_MAX_LIGHTNESS, after[3], 0.02f)
        assertEquals("bg4 must land exactly on the ceiling", ACHROMATIC_RAMP_LIGHT_TONE_MAX_LIGHTNESS, after[4], 0.02f)
        // The KDoc promises four untouched fields, not just text.
        assertEquals("text must be untouched by the direct cap", light.text, capped.text)
        assertEquals("textSecondary must be untouched by the direct cap", light.textSecondary, capped.textSecondary)
        assertEquals("textDisabled must be untouched by the direct cap", light.textDisabled, capped.textDisabled)
        assertEquals("accent must be untouched by the direct cap", light.accent, capped.accent)
    }

    @Test
    fun `the direct floor shifts all five backgrounds uniformly keyed on the real min`() {
        // Dark tone in a light theme where bg0 (0.10) is the real min.
        val dark = syntheticRamp(0.1f, 0.15f, 0.2f, 0.314f, 0.2f, toneIsDark = true)
        val floored = dark.m3eCapAchromaticBackgrounds(themeIsDark = false, toneIsDark = true)

        val after = floored.backgroundLightnesses()
        assertEquals("bg0 must land exactly on the floor", ACHROMATIC_RAMP_DARK_TONE_MIN_LIGHTNESS, after[0], 0.02f)
        assertEquals("bg1 must keep its relative spacing (0.15 + 0.20)", 0.35f, after[1], 0.02f)
        assertEquals("bg2 must keep its relative spacing (0.20 + 0.20)", 0.4f, after[2], 0.02f)
        assertEquals("bg3 must keep its relative spacing (0.314 + 0.20)", 0.514f, after[3], 0.02f)
        assertEquals("bg4 must keep its relative spacing (0.20 + 0.20)", 0.4f, after[4], 0.02f)
        assertEquals("text must be untouched by the direct floor", dark.text, floored.text)
        assertEquals("textSecondary must be untouched by the direct floor", dark.textSecondary, floored.textSecondary)
        assertEquals("textDisabled must be untouched by the direct floor", dark.textDisabled, floored.textDisabled)
        assertEquals("accent must be untouched by the direct floor", dark.accent, floored.accent)
    }

    @Test
    fun `the dynamic theme tone follows the requested mode for colored covers`() = runBlocking {
        val blue = solidBitmap(android.graphics.Color.rgb(30, 120, 200))
        assertEquals("light mode must keep the light tone", false, m3eDynamicColorPaletteOf(blue, false)!!.isDark)
        assertEquals("dark mode must keep the dark tone", true, m3eDynamicColorPaletteOf(blue, true)!!.isDark)
    }

    @Test
    fun `the lyrics theme color is the theme accent for achromatic themes`() = runBlocking {
        for (bitmap in listOf(
            solidBitmap(android.graphics.Color.rgb(58, 56, 54)),
            solidBitmap(android.graphics.Color.rgb(235, 233, 228)),
        )) {
            for (isDark in listOf(true, false)) {
                val palette = m3eDynamicColorPaletteOf(bitmap, isDark)
                assertNotNull("expected a palette (isDark=$isDark)", palette)
                assertEquals(
                    "lyrics theme color must be the neutral accent (isDark=$isDark)",
                    palette!!.accent,
                    lyricsThemeColor(palette)
                )
                assertEquals("the lyrics color must be a neutral gray (isDark=$isDark)", 0f, hslOf(palette.accent.toArgb())[1], 0.001f)
            }
        }
    }

    @Test
    fun `the lyrics theme color is the theme accent, not the text, for colored themes`() = runBlocking {
        for (isDark in listOf(true, false)) {
            val palette = m3eDynamicColorPaletteOf(solidBitmap(android.graphics.Color.rgb(30, 120, 200)), isDark)
            assertNotNull("expected a palette for a colored cover (isDark=$isDark)", palette)
            assertNotEquals("accent must differ from the text (isDark=$isDark)", palette!!.text, palette.accent)
            assertEquals("lyrics theme color must be the theme accent (isDark=$isDark)", palette.accent, lyricsThemeColor(palette))
            assertNotEquals("lyrics theme color must not be the theme text (isDark=$isDark)", palette.text, lyricsThemeColor(palette))
        }
    }

    @Test
    fun `the lyrics theme color is the theme text on an accent background`() = runBlocking {
        val palette = m3eDynamicColorPaletteOf(solidBitmap(android.graphics.Color.rgb(30, 120, 200)), true)
        assertNotNull("expected a palette for a colored cover", palette)
        assertEquals("lyrics theme color must be the theme text", palette!!.text, lyricsThemeColor(palette, onAccentBackground = true))
    }
}
