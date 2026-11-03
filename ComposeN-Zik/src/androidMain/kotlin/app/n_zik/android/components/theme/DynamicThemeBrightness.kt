package app.n_zik.android.components.theme

import androidx.compose.ui.graphics.toArgb
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import app.n_zik.android.components.player.channelDelta

internal const val dynamicThemeRespectBrightnessKey = "dynamicThemeRespectBrightness"
internal const val DYNAMIC_THEME_RESPECT_BRIGHTNESS_DEFAULT = false

/** Keeps monochrome dynamic palettes dark with matching foregrounds in every theme mode. */
internal fun ColorPalette.keepMonochromeColorsDark(
    enabled: Boolean,
): ColorPalette {
    // Neutral cover extraction zeros saturation; faintly colored accents must keep their palette.
    if (!enabled || channelDelta(accent.toArgb()) != 0f) return this
    return dynamicColorPaletteOf(accent, true)
}
