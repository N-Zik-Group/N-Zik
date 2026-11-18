package app.n_zik.android.components.theme

import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.applyPitchBlack

/** Reapply the selected mode after initialization, restoration or dynamic palette extraction. */
fun Appearance.forPaletteMode(mode: ColorPaletteMode): Appearance {
    if (mode != ColorPaletteMode.PitchBlack && mode != ColorPaletteMode.PureBlack) return this
    val normalized = colorPalette.applyPitchBlack
    val normalizedTypography = typography.withColor(normalized.text)
    if (normalized == colorPalette && normalizedTypography == typography) return this
    return copy(colorPalette = normalized, typography = normalizedTypography)
}
