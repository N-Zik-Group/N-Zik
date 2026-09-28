package app.n_zik.android.components.player

import androidx.compose.ui.graphics.Color
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.ui.styling.ColorPalette

/**
 * The outline (halo) color for the player's title/artist text
 * (spec-achromatic-ramp-luminance-cap, 2026-09-28): white at [alpha] on a light-tone ramp, black
 * at [alpha] on a dark-tone one. Keyed on the effective tone of the palette instead of the
 * system theme / `colorPaletteMode`, so the halo stays readable when a cover's tone mismatches
 * the theme (near-white cover in dark mode, or the mirror cases).
 *
 * Called from the legacy call sites (Player, Essential, Modern, kreate ActionBar, Dialog) and
 * pinned by `CoverContrastColorsTest`.
 */
// @PublishedApi: called from the public inline `SelectorArtistsDialog` (legacy themed Dialog.kt)
@PublishedApi
internal fun textOutlineColor(effectiveIsDark: Boolean, alpha: Float): Color =
    if (effectiveIsDark) Color.Black.copy(alpha = alpha) else Color.White.copy(alpha = alpha)

/**
 * The transport color for the `Monochrome` player controls option
 * (spec-achromatic-ramp-luminance-cap, 2026-09-28): the effective palette's
 * [ColorPalette.text]. The legacy call sites (MiniPlayer, Essential, Modern) hardcoded
 * `Color.White`, which broke on light-tone ramps (near-white cover in dark mode, or any cover
 * in light mode). The `PlayerControlsColors.Monochrome` option itself is unchanged — only the
 * color it resolves to now follows the tone.
 */
internal fun monochromeControlsColor(palette: ColorPalette): Color = palette.text

/**
 * The base color for the dimmed title/artist placeholder `compositeOver`
 * (spec-achromatic-ramp-luminance-cap, 2026-09-28): white on a dark-tone ramp, black on a
 * light-tone one — the placeholder (`textDisabled` at 0.35 alpha) must read as a ghost of the
 * tone, not as white on a light background or black on a dark one.
 */
internal fun placeholderCompositeBase(effectiveIsDark: Boolean): Color =
    if (effectiveIsDark) Color.White else Color.Black

/**
 * Re-caps a restored dynamic palette (spec-achromatic-ramp-luminance-cap, loopback 2, EC-1,
 * 2026-09-28): the Saver `ColorPalette.Companion` persists only `accent + isDark` and rebuilds
 * the ramp through the legacy NON-capped `dynamicColorPaletteOf`, so after a process death a
 * capped achromatic ramp (e.g. a light-tone ramp restored in a dark theme) comes back uncapped
 * until the next extraction. Pure gating: only [ColorPaletteName.Dynamic] reaches
 * [m3eCapAchromaticBackgrounds] (which itself is a no-op same-instance when the tone is already
 * in range or matching) — any other name, including static palettes, gets the very same
 * instance back.
 *
 * @param colorPaletteName the saved palette name (legacy enum, read-only here)
 * @param themeIsDark the theme the restored palette renders in — the same expression
 *  `setDynamicPalette` uses in `MainActivity`
 * @return the re-capped palette, or the same instance when no shift is needed
 */
internal fun ColorPalette.m3eRecapRestoredDynamicPalette(
    colorPaletteName: ColorPaletteName,
    themeIsDark: Boolean,
): ColorPalette {
    if (colorPaletteName != ColorPaletteName.Dynamic) return this
    return m3eCapAchromaticBackgrounds(themeIsDark, isDark)
}

/**
 * The outline color for the player's duration indicator (kreate `DurationIndicator`,
 * spec-achromatic-ramp-luminance-cap, loopback 2, VG-3, 2026-09-28): transparent when the user
 * opted out of text outlines — the legacy light-mode quirk that ignored the opt-out is fixed —
 * otherwise the effective-tone halo [textOutlineColor] at 0.5 alpha. The decision lives here
 * (pinned by `DurationIndicatorOutlineOffMainTest`); the composable only forwards.
 */
internal fun durationOutlineColorOf(textOutline: Boolean, palette: ColorPalette): Color =
    if (!textOutline) Color.Transparent else textOutlineColor(palette.isDark, 0.5f)
