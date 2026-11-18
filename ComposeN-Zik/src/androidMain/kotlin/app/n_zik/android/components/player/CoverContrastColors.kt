package app.n_zik.android.components.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.IconContrast
import app.n_zik.android.components.glass.TextContrast
import app.n_zik.android.components.glass.contrastAgainst
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassFilmColor
import app.n_zik.android.components.glass.inScope
import app.n_zik.android.components.glass.neutralForeground
import app.n_zik.android.components.glass.playerControlGlyphColor
import app.n_zik.android.components.glass.surfaceNeutralForeground
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.theme.LocalPaletteDarkness
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.ui.styling.ColorPalette

/**
 * The outline (halo) color for the player's title/artist text
 * (spec-achromatic-ramp-luminance-cap, 2026-09-28): white at [alpha] on a light-tone ramp, black
 * at [alpha] on a dark-tone one. Keyed on the effective tone of the palette instead of the
 * system theme / `colorPaletteMode`, so the halo stays readable when a cover's tone mismatches
 * the theme (near-white cover in dark mode, or the mirror cases).
 *
 * Called from the legacy call sites (Player, Essential, Modern, the themed ActionBar, Dialog) and
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

/** The base color for the dimmed title/artist placeholder `compositeOver` (spec-achromatic-ramp-luminance-cap, 2026-09-28): white on a dark-tone ramp, black on a light-tone one — the placeholder (`textDisabled` at 0.35 alpha) must read as a ghost of the tone, not as white on a light background or black on a dark one. */
internal fun placeholderCompositeBase(effectiveIsDark: Boolean): Color =
    if (effectiveIsDark) Color.White else Color.Black

/** Resolve the placeholder from the shared displayed palette fraction, including retargets. */
@Composable
internal fun playerPlaceholderCompositeBase(effectiveIsDark: Boolean): Color =
    placeholderCompositeBase(LocalPaletteDarkness.current ?: if (effectiveIsDark) 1f else 0f)

internal fun placeholderCompositeBase(darkness: Float): Color =
    lerp(Color.Black, Color.White, darkness.coerceIn(0f, 1f))

/** The halo follows the displayed ink rather than thresholding an interpolated color. */
internal fun opposingTextOutlineColor(foreground: Color, alpha: Float): Color {
    val lightness = (foreground.red + foreground.green + foreground.blue) / 3f
    return lerp(Color.White, Color.Black, lightness.coerceIn(0f, 1f)).copy(alpha = alpha)
}

/**
 * Re-caps a restored dynamic palette (spec-achromatic-ramp-luminance-cap, loopback 2, EC-1):
 * the Saver persists only `accent + isDark` and rebuilds the ramp through the legacy NON-capped
 * `dynamicColorPaletteOf`, so a capped achromatic ramp comes back uncapped after a process
 * death. Only [ColorPaletteName.Dynamic] reaches [m3eCapAchromaticBackgrounds] (a no-op
 * same-instance when the tone is already in range); any other name gets the same instance back.
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
 * The outline color for the player's duration indicator (the themed `DurationIndicator`,
 * spec-achromatic-ramp-luminance-cap, loopback 2, VG-3, 2026-09-28): transparent when the user
 * opted out of text outlines — the legacy light-mode quirk that ignored the opt-out is fixed —
 * otherwise the effective-tone halo [textOutlineColor] at 0.5 alpha. The decision lives here
 * (pinned by `DurationIndicatorOutlineOffMainTest`); the composable only forwards.
 */
internal fun durationOutlineColorOf(textOutline: Boolean, palette: ColorPalette): Color =
    if (!textOutline) Color.Transparent else textOutlineColor(palette.isDark, 0.5f)

internal data class PlayerControlContrast(val backdrop: Color, val scrim: Color) {
    val surface: Color get() = scrim.compositeOver(backdrop)
}

/** Make the local controls readable across the estimated artwork and gradient range. */
internal fun playerControlContrast(backdrop: Color, samples: List<Color>): PlayerControlContrast {
    val foreground = neutralForeground(backdrop)
    val scrimColor = if (foreground == Color.Black) Color.White else Color.Black
    fun meetsTarget(alpha: Float): Boolean =
        samples.all { contrastAgainst(foreground, scrimColor.copy(alpha = alpha).compositeOver(it)) >= TextContrast }
    if (meetsTarget(0f)) return PlayerControlContrast(backdrop, Color.Transparent)
    var lower = 0f
    var upper = 1f
    repeat(10) {
        val midpoint = (lower + upper) / 2
        if (meetsTarget(midpoint)) upper = midpoint else lower = midpoint
    }
    return PlayerControlContrast(backdrop, scrimColor.copy(alpha = upper))
}

internal fun playerTintedArtworkSample(
    artwork: Color,
    wash: Color,
    bottom: Color,
    gradientStart: Float,
    height: Float,
    position: Float,
): Color {
    val fraction = if (height <= gradientStart) 0f
        else ((position * height - gradientStart) / (height - gradientStart)).coerceIn(0f, 1f)
    return bottom.copy(alpha = bottom.alpha * fraction)
        .compositeOver(wash.compositeOver(artwork))
}

@Composable
internal fun playerTransportGlyph(
    color: Color,
    settings: GlassSettings,
    nativePlate: Color = Color.Transparent,
    hasGlassPlate: Boolean = true,
    minimum: Float = IconContrast,
    enabled: Boolean = true,
): Color = playerControlGlyphColor(color, playerTransportPlate(settings, nativePlate, hasGlassPlate), minimum, enabled)

@Composable
internal fun playerTransportPlate(
    settings: GlassSettings,
    nativePlate: Color,
    hasGlassPlate: Boolean = true,
): Color {
    val transport = settings.inScope(GlassScope.PlayerTransport)
    return if (hasGlassPlate && transport.usesGlassMaterial(GlassArea.ActionButtons))
        glassFilmColor(transport.forArea(GlassArea.ActionButtons), role = nativePlate, menu = false) else nativePlate
}

@Composable
internal fun playerTextOutlineColor(effectiveIsDark: Boolean, alpha: Float): Color {
    val darkness = LocalPaletteDarkness.current ?: if (effectiveIsDark) 1f else 0f
    return opposingTextOutlineColor(surfaceNeutralForeground(placeholderCompositeBase(darkness)), alpha)
}
