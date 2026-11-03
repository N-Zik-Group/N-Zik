package app.n_zik.android.components.settings.common

import app.it.fast4x.rimusic.enums.AnimatedGradient
import app.it.fast4x.rimusic.enums.PlayerBackgroundColors
import app.n_zik.android.R

/** Settings labels for persisted legacy background choices, sharing the current source names. */
internal fun playerBackgroundColorLabel(
    value: PlayerBackgroundColors,
    resolveString: (Int) -> String,
): String = when (value) {
    PlayerBackgroundColors.CoverColor -> resolveString(R.string.common_color_source_artwork)
    PlayerBackgroundColors.ThemeColor -> resolveString(R.string.common_color_source_theme)
    PlayerBackgroundColors.CoverColorGradient ->
        resolveString(R.string.player_background_artwork_gradient_label)
    PlayerBackgroundColors.ThemeColorGradient ->
        resolveString(R.string.player_background_theme_gradient_label)
    PlayerBackgroundColors.BlurredCoverColor ->
        resolveString(R.string.player_background_artwork_blurred_label)
    else -> resolveString(value.textId)
}

/** Keeps animation styles distinct while using the same Artwork and Theme source labels. */
internal fun animatedGradientLabel(
    value: AnimatedGradient,
    resolveString: (Int) -> String,
): String = when (value) {
    AnimatedGradient.FluidThemeColorGradient ->
        resolveString(R.string.player_background_theme_fluid_gradient_label)
    AnimatedGradient.FluidCoverColorGradient ->
        resolveString(R.string.player_background_artwork_fluid_gradient_label)
    AnimatedGradient.M3EMorphingTheme ->
        resolveString(R.string.player_background_theme_m3e_morphing_label)
    AnimatedGradient.M3EMorphingCover ->
        resolveString(R.string.player_background_artwork_m3e_morphing_label)
    else -> resolveString(value.textId)
}
