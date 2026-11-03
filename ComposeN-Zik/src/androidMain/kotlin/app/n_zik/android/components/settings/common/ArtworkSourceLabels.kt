package app.n_zik.android.components.settings.common

import app.it.fast4x.rimusic.enums.AnimatedGradient
import app.it.fast4x.rimusic.enums.PlayerBackgroundColors
import app.n_zik.android.R

/** Settings labels for persisted legacy background choices, sharing the current source names. */
internal fun playerBackgroundColorLabel(
    value: PlayerBackgroundColors,
    resolveString: (Int) -> String,
): String = when (value) {
    PlayerBackgroundColors.CoverColor -> resolveString(R.string.artwork)
    PlayerBackgroundColors.ThemeColor -> resolveString(R.string.theme)
    PlayerBackgroundColors.CoverColorGradient ->
        resolveString(R.string.bg_colors_gradient_background_from_cover)
    PlayerBackgroundColors.ThemeColorGradient ->
        resolveString(R.string.bg_colors_gradient_background_from_theme)
    PlayerBackgroundColors.BlurredCoverColor ->
        resolveString(R.string.bg_colors_blurred_cover_background)
    else -> resolveString(value.textId)
}

/** Keeps animation styles distinct while using the same Artwork and Theme source labels. */
internal fun animatedGradientLabel(
    value: AnimatedGradient,
    resolveString: (Int) -> String,
): String = when (value) {
    AnimatedGradient.FluidThemeColorGradient ->
        resolveString(R.string.bg_colors_fluid_gradient_background_from_theme)
    AnimatedGradient.FluidCoverColorGradient ->
        resolveString(R.string.bg_colors_fluid_gradient_background_from_cover)
    AnimatedGradient.M3EMorphingTheme ->
        resolveString(R.string.bg_colors_m3e_morphing_theme)
    AnimatedGradient.M3EMorphingCover ->
        resolveString(R.string.bg_colors_m3e_morphing_cover)
    else -> resolveString(value.textId)
}
