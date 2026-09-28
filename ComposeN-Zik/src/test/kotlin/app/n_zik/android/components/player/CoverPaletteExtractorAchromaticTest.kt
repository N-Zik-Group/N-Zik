package app.n_zik.android.components.player

import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils.colorToHSL
import androidx.palette.graphics.Palette
import app.it.fast4x.rimusic.ui.screens.player.computePlayerDynamicPalette
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit coverage for the achromatic neutralization guard (renegotiated `NEUTRAL_COVER` case,
 * 2026-09-19 — session `problem-solution-2026-09-19`, solution S3'): nearly achromatic covers
 * must render a neutral gray following the cover's lightness on every cover-based surface,
 * while colored/pastel covers must be extracted bit-identically to the pre-guard behavior
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

    @Test
    fun `the dynamic theme tone follows the cover lightness for achromatic covers`() = runBlocking {
        val darkGray = solidBitmap(android.graphics.Color.rgb(58, 56, 54))
        val offWhite = solidBitmap(android.graphics.Color.rgb(235, 233, 228))

        // Light theme + dark cover: the tone must flip to dark (previously the theme stayed white).
        val lightModeDarkCover = m3eDynamicColorPaletteOf(darkGray, false)
        assertNotNull("expected a palette for a dark achromatic cover (light theme)", lightModeDarkCover)
        assertEquals("dark cover must yield a dark tone", true, lightModeDarkCover!!.isDark)
        assertEquals(0.10f, hslOf(lightModeDarkCover.background0.toArgb())[2], 0.02f)
        assertEquals(0.88f, hslOf(lightModeDarkCover.text.toArgb())[2], 0.02f)

        // Dark theme + off-white cover: the tone must flip to light.
        val darkModeOffWhiteCover = m3eDynamicColorPaletteOf(offWhite, true)
        assertNotNull("expected a palette for an off-white achromatic cover (dark theme)", darkModeOffWhiteCover)
        assertEquals("off-white cover must yield a light tone", false, darkModeOffWhiteCover!!.isDark)
        assertEquals(0.925f, hslOf(darkModeOffWhiteCover.background0.toArgb())[2], 0.02f)
        assertEquals(0.12f, hslOf(darkModeOffWhiteCover.text.toArgb())[2], 0.02f)

        // Matching tones are unchanged.
        assertEquals(true, m3eDynamicColorPaletteOf(darkGray, true)!!.isDark)
        assertEquals(false, m3eDynamicColorPaletteOf(offWhite, false)!!.isDark)
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
