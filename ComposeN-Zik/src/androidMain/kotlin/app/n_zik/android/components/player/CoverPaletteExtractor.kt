package app.n_zik.android.components.player

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils.colorToHSL
import androidx.palette.graphics.Palette
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The 7 raw swatches of an album cover extracted from the capped [Palette]
 * (`maximumColorCount(8)`) — the same RiPlay-based extraction the legacy reference
 * `dynamicColorPaletteOf(bitmap, isDark)` uses. This extractor is the app's single source of
 * cover-color extraction (player, mini-player, app-wide dynamic theme, lyrics, visualizer,
 * home-widget fallbacks).
 *
 * All values are ARGB `Int`s.
 */
data class M3ECoverColors(
    val dominant: Int,
    val vibrant: Int,
    val lightVibrant: Int,
    val darkVibrant: Int,
    val muted: Int,
    val lightMuted: Int,
    val darkMuted: Int,
)

/**
 * Maximum channel spread (max − min of the R/G/B channels, normalized to 0–1) below
 * which a cover is treated as nearly achromatic (the renegotiated `NEUTRAL_COVER` case,
 * 2026-09-19 — session `problem-solution-2026-09-19`, solution S3').
 *
 * The channel spread is used instead of HSV saturation because saturation is
 * lightness-dependent: the same channel spread reads S≈0.15 on a near-white cover but
 * S≈0.08 on a mid-tone one, which would miss exactly the reported "light brown on white
 * covers" case (probe: off-white rgb(235, 233, 228) → swatch Δ≈0.03 but S≈0.148).
 * 0.10 covers every reported case (probe: off-white Δ≈0.03, warm/cool grays Δ≈0.09,
 * dark grays Δ≈0.03) while keeping genuinely colored/pastel covers (Δ ≥ 0.10) untouched.
 */
const val ACHROMATIC_CHANNEL_DELTA_THRESHOLD = 0.10f

/**
 * The lightness ceiling of the lightest background (bg0–bg4) of an achromatic ramp rendered in a
 * dark theme (spec-achromatic-ramp-luminance-cap, 2026-09-28). A near-white cover in dark mode
 * would otherwise leave the whole app near-white (bg3/4 ≈ L 0.955, inherited from the default
 * light palette).
 */
const val ACHROMATIC_RAMP_LIGHT_TONE_MAX_LIGHTNESS = 0.80f

/** The lightness floor of the darkest background (bg0–bg4) of an achromatic ramp rendered in a light theme (spec-achromatic-ramp-luminance-cap, 2026-09-28) — the mirror case: a near-black cover in light mode would otherwise leave the whole app near-black (bg0 L 0.10). */
const val ACHROMATIC_RAMP_DARK_TONE_MIN_LIGHTNESS = 0.30f

/** The normalized channel spread (max − min of the R/G/B channels) of an ARGB color, in [0, 1] — a lightness-independent measure of how far the color is from gray. */
internal fun channelDelta(rgb: Int): Float {
    val r = (rgb shr 16) and 0xFF
    val g = (rgb shr 8) and 0xFF
    val b = rgb and 0xFF
    return (maxOf(r, g, b) - minOf(r, g, b)) / 255f
}

/** Extracts neutralized cover swatches on MEDIA, or null when no dominant swatch exists. */
suspend fun extractM3ECoverColors(bitmap: Bitmap, isDark: Boolean): M3ECoverColors? {
    return extractCoverPalette(bitmap, isDark)?.colors
}

internal data class ExtractedCoverPalette(val colors: M3ECoverColors, val palette: ColorPalette)

/** Quantizes once on MEDIA, sharing the swatches between the cover roles and dynamic theme. */
internal suspend fun extractCoverPalette(
    bitmap: Bitmap,
    isDark: Boolean,
    generatePalette: (Bitmap) -> Palette = { Palette.from(it).maximumColorCount(8).generate() },
): ExtractedCoverPalette? = withContext(NzikDispatchers.MEDIA) {
    val swatchPalette = generatePalette(bitmap)
    val palette = dynamicPaletteFromSwatches(swatchPalette, isDark) ?: return@withContext null
    val fallback = palette.accent.toArgb()
    val colors = M3ECoverColors(
        dominant = swatchPalette.getDominantColor(fallback),
        vibrant = swatchPalette.getVibrantColor(fallback),
        lightVibrant = swatchPalette.getLightVibrantColor(fallback),
        darkVibrant = swatchPalette.getDarkVibrantColor(fallback),
        muted = swatchPalette.getMutedColor(fallback),
        lightMuted = swatchPalette.getLightMutedColor(fallback),
        darkMuted = swatchPalette.getDarkMutedColor(fallback),
    ).m3eNeutralizeIfAchromatic(swatchPalette, fallback)
    ExtractedCoverPalette(colors, dominantDynamicPaletteOf(colors, isDark) { palette })
}

private fun dynamicPaletteFromSwatches(palette: Palette, isDark: Boolean): ColorPalette? {
    val dominantHsl = palette.dominantSwatch?.hsl ?: return null
    // Preserve the reference's S<0.08 rescue, including first-swatch tie ordering.
    val hsl = if (dominantHsl[1] < 0.08) {
        palette.swatches.map(Palette.Swatch::getHsl)
            .sortedByDescending { it[1] }
            .firstOrNull { it[1] != 0f } ?: dominantHsl
    } else {
        dominantHsl
    }
    return dynamicColorPaletteOf(hsl, isDark)
}

/**
 * Neutralizes the extracted cover swatches when the cover is nearly achromatic
 * (renegotiated `NEUTRAL_COVER` case, 2026-09-19 — session `problem-solution-2026-09-19`,
 * solution S3').
 *
 * `androidx.palette` has no vibrant-role swatch for achromatic bitmaps, so every
 * `get*Color(fallback)` falls back to the legacy dynamic accent — a mid-tone carrying the
 * cover's faint hue, which [m3eSaturate]'s dark-theme boost then amplifies into the
 * artificial light brown/blue/pink tints reported on gray/white covers.
 *
 * Below [ACHROMATIC_CHANNEL_DELTA_THRESHOLD] every swatch becomes a neutral gray (saturation
 * zeroed, its own lightness kept — except swatches that fell back to [fallbackArgb], which
 * take the dominant's lightness, "neutral gray following the cover"); covers at or above the
 * threshold are unchanged (strict M3E parity). Applied inside [extractM3ECoverColors] so
 * every cover-based surface renders the same neutral result.
 *
 * @param swatchPalette the capped `Palette.from(bitmap).maximumColorCount(8).generate()`
 *  the swatches came from
 * @param fallbackArgb the dynamic accent ARGB used as each swatch's fallback
 * @return this instance neutralized when achromatic, unchanged otherwise
 */
internal fun M3ECoverColors.m3eNeutralizeIfAchromatic(
    swatchPalette: Palette,
    fallbackArgb: Int,
): M3ECoverColors {
    if (swatchPalette.swatches.maxOf { channelDelta(it.rgb) } >= ACHROMATIC_CHANNEL_DELTA_THRESHOLD) {
        return this
    }

    val dominantHsl = FloatArray(3)
    colorToHSL(dominant, dominantHsl)
    val dominantLuminance = dominantHsl[2]

    fun neutralize(rgb: Int): Int {
        val lightness = if (rgb == fallbackArgb) {
            dominantLuminance
        } else {
            val hsl = FloatArray(3)
            colorToHSL(rgb, hsl)
            hsl[2]
        }
        return Color.hsl(0f, 0f, lightness).toArgb()
    }

    return copy(
        dominant = neutralize(dominant),
        vibrant = neutralize(vibrant),
        lightVibrant = neutralize(lightVibrant),
        darkVibrant = neutralize(darkVibrant),
        muted = neutralize(muted),
        lightMuted = neutralize(lightMuted),
        darkMuted = neutralize(darkMuted),
    )
}

/** Whether every swatch is neutral (channel spread below [ACHROMATIC_CHANNEL_DELTA_THRESHOLD]) — true for covers neutralized by [m3eNeutralizeIfAchromatic]. */
val M3ECoverColors.allAchromatic: Boolean
    get() = listOf(dominant, vibrant, lightVibrant, darkVibrant, muted, lightMuted, darkMuted)
        .all { channelDelta(it) < ACHROMATIC_CHANNEL_DELTA_THRESHOLD }

/** Builds the reference dynamic palette on MEDIA, retaining neutral ramps and their tone caps. */
suspend fun m3eDynamicColorPaletteOf(bitmap: Bitmap, isDark: Boolean): ColorPalette? =
    extractCoverPalette(bitmap, isDark)?.palette

/** Compatibility path for the player's already-extracted colors; callers dispatch to MEDIA. */
internal fun m3eDominantDynamicPaletteOf(
    colors: M3ECoverColors,
    bitmap: Bitmap,
    isDark: Boolean,
): ColorPalette = dominantDynamicPaletteOf(colors, isDark) {
    requireNotNull(dynamicColorPaletteOf(bitmap, isDark)) {
        "dominant swatch disappeared between extraction and palette build"
    }
}

private inline fun dominantDynamicPaletteOf(
    colors: M3ECoverColors,
    isDark: Boolean,
    coloredPalette: () -> ColorPalette,
): ColorPalette {
    if (colors.allAchromatic) {
        val dominantHsl = FloatArray(3)
        colorToHSL(colors.dominant, dominantHsl)
        val toneIsDark = dominantHsl[2] < 0.5f
        return dynamicColorPaletteOf(dominantHsl, toneIsDark)
            .m3eCapAchromaticBackgrounds(themeIsDark = isDark, toneIsDark = toneIsDark)
    }
    return coloredPalette()
}

/**
 * Caps (light tone in a dark theme) / floors (dark tone in a light theme) the five background
 * lightnesses of an achromatic dynamic ramp when the ramp's tone mismatches the theme
 * (spec-achromatic-ramp-luminance-cap, 2026-09-28): a near-white cover in dark mode must not
 * leave the whole app near-white (mirror: near-black cover in light theme).
 *
 * The shift is uniform across bg0–bg4 and keyed on the REAL max/min lightness of the five
 * backgrounds — bg3/4 are inherited from the default palettes and are the lightest/darkest
 * surfaces (≈ L 0.955, lighter than bg0's 0.925) — so the spacing is preserved and the
 * lightest/darkest background lands exactly on the ceiling / floor. Matching tones and tones
 * already in range return the same instance (bit-identical, no log); text colors and
 * [ColorPalette.accent] are untouched.
 *
 * Single choke point for dynamic palettes: applied on the achromatic branch of
 * [m3eDominantDynamicPaletteOf] and re-applied on restored palettes via
 * `m3eRecapRestoredDynamicPalette` (loopback 2, EC-1 — the Saver rebuilds the ramp uncapped).
 *
 * @param themeIsDark whether the ramp renders in a dark theme
 * @param toneIsDark the tone of this palette ([ColorPalette.isDark])
 * @return the capped/floored palette, or the same instance when no shift is needed
 */
internal fun ColorPalette.m3eCapAchromaticBackgrounds(
    themeIsDark: Boolean,
    toneIsDark: Boolean,
): ColorPalette {
    val lightnesses = listOf(background0, background1, background2, background3, background4)
        .map { background ->
            val hsl = FloatArray(3)
            colorToHSL(background.toArgb(), hsl)
            hsl[2]
        }
    val delta = when {
        themeIsDark && !toneIsDark ->
            lightnesses.max() - ACHROMATIC_RAMP_LIGHT_TONE_MAX_LIGHTNESS
        !themeIsDark && toneIsDark ->
            ACHROMATIC_RAMP_DARK_TONE_MIN_LIGHTNESS - lightnesses.min()
        else -> 0f
    }
    if (delta <= 0f) return this

    Timber.tag("CoverPaletteExtractor")
        .d("Achromatic ramp tone/theme mismatch: shifting bg0-bg4 lightness by $delta")

    fun shift(background: Color): Color {
        val hsl = FloatArray(3)
        colorToHSL(background.toArgb(), hsl)
        hsl[2] = (hsl[2] + if (toneIsDark) delta else -delta).coerceIn(0f, 1f)
        return Color.hsl(hsl[0], hsl[1], hsl[2])
    }

    return copy(
        background0 = shift(background0),
        background1 = shift(background1),
        background2 = shift(background2),
        background3 = shift(background3),
        background4 = shift(background4),
    )
}

/**
 * The lyrics color for the `Thememode` option: the theme's [ColorPalette.accent], the same
 * color the visualizer's `Theme` option uses, for colored and achromatic themes alike.
 *
 * @param palette the current theme palette
 * @param onAccentBackground whether the lyrics background is itself painted with the accent
 * (`showBackgroundLyrics` with the cover shown); the theme's [ColorPalette.text] is returned
 * instead so the lyrics stay readable on it
 */
fun lyricsThemeColor(palette: ColorPalette, onAccentBackground: Boolean = false): Color =
    if (onAccentBackground) palette.text else palette.accent

/**
 * The flat "Match song cover" player background color (`Player.kt`, `CoverColor`): the
 * dominant swatch with the same saturate transformation as the player background, so the
 * surface renders the cover's dominant hue -- the same hue the app's dynamic accent is built
 * from (RiPlay reference).
 *
 * @param dominant the dominant swatch as an ARGB `Int` (see [M3ECoverColors.dominant])
 * @param lightTheme whether the current theme is light
 */
fun m3eCoverBackgroundColor(dominant: Int, lightTheme: Boolean): Color =
    m3eSaturate(dominant, lightTheme).m3eDarkenBy(lightTheme)

/**
 * The "cover" foreground color used by the lyrics screen and the visualizer when their cover
 * color option is selected: the dominant swatch with the same saturate transformation as the
 * player background -- `m3eSaturate(dominant).toArgb()`.
 *
 * @param dominant the dominant swatch as an ARGB `Int` (see [M3ECoverColors.dominant])
 * @param lightTheme whether the current theme is light
 */
fun m3eCoverForegroundArgb(dominant: Int, lightTheme: Boolean): Int =
    m3eSaturate(dominant, lightTheme).toArgb()

/**
 * Pure (non-composable) copy of `Player.saturate()`: adds 0.35 to the saturation in dark theme
 * when the input saturation is at least 0.1 (clamped to [0, 1]), and forces the lightness to at
 * least 0.5 in light theme.
 *
 * @param color the input color as an ARGB `Int`
 * @param lightTheme whether the current theme is light
 */
fun m3eSaturate(color: Int, lightTheme: Boolean): Color {
    val hsl = FloatArray(3)
    colorToHSL(color, hsl)
    hsl[1] = (hsl[1] + if (lightTheme || hsl[1] < 0.1f) 0f else 0.35f).coerceIn(0f, 1f)
    hsl[2] = if (lightTheme) hsl[2].coerceIn(0.5f, 1f) else hsl[2]
    return Color.hsl(hsl[0], hsl[1], hsl[2])
}

/**
 * Pure (non-composable) copy of `Player.Color.darkenBy()`: multiplies RGB by 0.5 in dark theme
 * and leaves the color untouched in light theme.
 *
 * @param lightTheme whether the current theme is light
 */
fun Color.m3eDarkenBy(lightTheme: Boolean): Color {
    val ratio = if (lightTheme) 1f else 0.5f
    return copy(
        red = red * ratio,
        green = green * ratio,
        blue = blue * ratio,
        alpha = alpha,
    )
}
