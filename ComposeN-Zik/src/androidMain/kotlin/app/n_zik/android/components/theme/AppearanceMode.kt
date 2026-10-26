package app.n_zik.android.components.theme

import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.applyPitchBlack

/** Reapply the selected mode after initialization, restoration or dynamic palette extraction. */
fun Appearance.forPaletteMode(mode: ColorPaletteMode): Appearance {
    if (mode != ColorPaletteMode.PitchBlack) return this
    val normalized = colorPalette.applyPitchBlack
    if (normalized == colorPalette) return this
    return copy(colorPalette = normalized, typography = typography.withColor(normalized.text))
}
