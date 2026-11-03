package app.n_zik.android.components.player

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext

/** Immutable colors only: neither the completed cache nor display roles retain artwork pixels. */
data class ArtworkColorSnapshot internal constructor(
    val lightPalette: ColorPalette?,
    val darkPalette: ColorPalette?,
    val lightColors: M3ECoverColors?,
    val darkColors: M3ECoverColors?,
    val fallbackDominant: Color?,
    val isMonochrome: Boolean,
) {
    fun display(isDark: Boolean, keepDark: Boolean): ArtworkDisplayColors {
        val originalPalette = if (isDark) darkPalette else lightPalette
        val originalColors = if (isDark) darkColors else lightColors
        val source = originalColors?.dominant?.let(::Color) ?: fallbackDominant
        val forceDark = keepDark && isMonochrome && source != null
        val palette = if (forceDark) dynamicColorPaletteOf(source ?: Color.Gray, true) else originalPalette
        val background = if (forceDark) palette?.background1
            else originalColors?.let { m3eCoverBackgroundColor(it.dominant, !isDark) } ?: source
        val foreground = if (forceDark) palette?.text
            else originalColors?.let { Color(m3eCoverForegroundArgb(it.dominant, !isDark)) } ?: source
        val colors = if (forceDark && background != null) {
            val value = background.toArgb()
            M3ECoverColors(value, value, value, value, value, value, value)
        } else originalColors
        return ArtworkDisplayColors(palette, colors, if (forceDark) background else source,
            background, foreground, forceDark, isMonochrome)
    }
}

data class ArtworkDisplayColors(
    val palette: ColorPalette?,
    val colors: M3ECoverColors?,
    val dominant: Color?,
    val background: Color?,
    val foreground: Color?,
    val forceDark: Boolean,
    val isMonochrome: Boolean,
)

/** One normal quantization; the existing pure-white/black recovery is shared too. */
internal suspend fun analyzeArtworkColors(
    bitmap: Bitmap,
    quantize: (Bitmap, Boolean) -> Palette = { image, unfiltered ->
        Palette.from(image).maximumColorCount(8).apply { if (unfiltered) clearFilters() }.generate()
    },
): ArtworkColorSnapshot = withContext(NzikDispatchers.MEDIA) {
    val swatches = quantize(bitmap, false)
    val light = coverPaletteFromSwatches(swatches, false)
    val dark = coverPaletteFromSwatches(swatches, true)
    val fallback = if (light == null) quantize(bitmap, true) else null
    val sourceSwatches = fallback?.swatches ?: swatches.swatches
    val monochrome = sourceSwatches.isNotEmpty() && sourceSwatches.all {
        channelDelta(it.rgb) < ACHROMATIC_CHANNEL_DELTA_THRESHOLD
    }
    ArtworkColorSnapshot(light?.palette, dark?.palette, light?.colors, dark?.colors,
        fallback?.dominantSwatch?.rgb?.let(::Color), monochrome)
}
