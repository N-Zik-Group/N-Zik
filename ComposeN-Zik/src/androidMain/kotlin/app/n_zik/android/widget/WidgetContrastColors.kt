package app.n_zik.android.widget

import androidx.compose.ui.graphics.toArgb
import app.it.fast4x.rimusic.ui.styling.ColorPalette

/**
 * The ARGB tint for the widget's transport/control icons
 * (spec-achromatic-ramp-luminance-cap, 2026-09-28): the effective palette's
 * [ColorPalette.iconButtonPlayer]. The palette already follows the cover's tone (built through
 * the shared capped M3E extraction, or the app's saved palette when the system theme matches),
 * so the icons key on the effective tone instead of `isSystemInDarkMode` — a near-white cover in
 * dark mode must not leave white icons on a near-white widget.
 *
 * Pinned by the widget suites (`NZikWidgetManagerExtractPaletteOffMainTest`,
 * `PlaylistWidgetManagerExtractPaletteOffMainTest`).
 *
 * @param palette the palette the widget renders with
 */
internal fun widgetIconTintArgb(palette: ColorPalette): Int = palette.iconButtonPlayer.toArgb()

/**
 * The ARGB primary text color of a widget (spec-achromatic-ramp-luminance-cap, 2026-09-28):
 * the effective palette's [ColorPalette.text]. Keyed on the effective tone instead of
 * `isSystemInDarkMode`, so titles/labels stay readable on a mismatched ramp (near-white cover in
 * dark mode, or the mirror case in light mode) instead of a hardcoded black/white.
 *
 * Pinned by the widget suites.
 *
 * @param palette the palette the widget renders with
 */
internal fun widgetTextArgb(palette: ColorPalette): Int = palette.text.toArgb()

/**
 * The ARGB secondary text color of a widget (spec-achromatic-ramp-luminance-cap, 2026-09-28):
 * the effective palette's [ColorPalette.textSecondary]. Keyed on the effective tone instead of
 * `isSystemInDarkMode` — the legacy light-mode branch hardcoded black, which broke on
 * light-tone ramps in a light theme.
 *
 * Pinned by the widget suites.
 *
 * @param palette the palette the widget renders with
 */
internal fun widgetSecondaryTextArgb(palette: ColorPalette): Int = palette.textSecondary.toArgb()
