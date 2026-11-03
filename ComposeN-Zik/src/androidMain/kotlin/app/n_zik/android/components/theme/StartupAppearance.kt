package app.n_zik.android.components.theme

import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.n_zik.android.components.player.m3eRecapRestoredDynamicPalette

/** Restore playback colors only for Dynamic; persisted preferences own the other themes. */
internal fun startupAppearance(
    restored: Appearance,
    configured: Appearance,
    paletteName: ColorPaletteName,
    paletteMode: ColorPaletteMode,
    systemIsDark: Boolean,
    respectThemeBrightness: Boolean = DYNAMIC_THEME_RESPECT_BRIGHTNESS_DEFAULT,
): Appearance {
    if (paletteName != ColorPaletteName.Dynamic) return configured.forPaletteMode(paletteMode)

    val themeIsDark = paletteMode == ColorPaletteMode.Dark ||
        paletteMode == ColorPaletteMode.PitchBlack ||
        (paletteMode == ColorPaletteMode.System && systemIsDark)
    val palette = restored.colorPalette.m3eRecapRestoredDynamicPalette(paletteName, themeIsDark)
        .keepMonochromeColorsDark(respectThemeBrightness)
    return configured.copy(
        colorPalette = palette,
        typography = configured.typography.withColor(palette.text),
    ).forPaletteMode(paletteMode)
}
