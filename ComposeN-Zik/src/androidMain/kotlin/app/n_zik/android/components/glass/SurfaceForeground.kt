package app.n_zik.android.components.glass

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import app.n_zik.android.components.theme.withColor
import app.n_zik.android.ui.styling.LocalAppearance

/** Estimated scene or plate color at the origin of an overlay. */
internal val LocalSurfaceBackdropColor = compositionLocalOf<Color?> { null }
private val LocalForegroundSurface = compositionLocalOf<Color?> { null }
private val LocalForegroundScrim = compositionLocalOf { Color.Transparent }

internal const val TextContrast = 4.5f
internal const val IconContrast = 3f

internal fun contrastAgainst(foreground: Color, surface: Color): Float {
    val ink = foreground.compositeOver(surface).luminance()
    val fill = surface.luminance()
    return (maxOf(ink, fill) + 0.05f) / (minOf(ink, fill) + 0.05f)
}

internal fun neutralForeground(surface: Color): Color =
    if (contrastAgainst(Color.Black, surface) >= contrastAgainst(Color.White, surface)) Color.Black else Color.White

internal fun readableForeground(original: Color, surface: Color, minimum: Float): Color =
    if (original.isSpecified && contrastAgainst(original, surface) >= minimum) original else neutralForeground(surface)

@Composable
internal fun surfaceBackdropColor(): Color =
    LocalSurfaceBackdropColor.current ?: LocalAppearance.current.colorPalette.background0.copy(alpha = 1f)

internal fun effectiveSurfaceColor(
    backdrop: Color,
    fill: Color,
    scrim: Color = Color.Transparent,
): Color {
    val underlay = scrim.compositeOver(backdrop)
    return if (fill.isSpecified) fill.compositeOver(underlay) else underlay
}

@Composable
internal fun glassFilmColor(
    settings: GlassSettings,
    role: Color?,
    menu: Boolean = true,
    progress: Float = 1f,
): Color {
    val tint = glassFilmTint(settings, role, menu, progress)
    return tint.copy(alpha = tint.alpha * glassFilmOpacity(settings.film, menu, progress))
}

@Composable
internal fun SurfaceForegroundContent(
    surface: Color,
    scrim: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    val appearance = LocalAppearance.current
    if (appearance.colorPalette.isDark) {
        content()
        return
    }
    val foreground = neutralForeground(surface)
    val palette = appearance.colorPalette.copy(
        text = foreground,
        textSecondary = foreground,
        textDisabled = foreground.copy(alpha = 0.38f).compositeOver(surface),
        iconButtonPlayer = foreground,
    )
    CompositionLocalProvider(
        LocalAppearance provides appearance.copy(colorPalette = palette, typography = appearance.typography.withColor(foreground)),
        LocalContentColor provides foreground,
        LocalSurfaceBackdropColor provides surface,
        LocalForegroundSurface provides surface,
        LocalForegroundScrim provides scrim,
        content = content,
    )
}

/** Resolve an explicit glyph against the current surface or against its own translucent plate. */
@Composable
internal fun surfaceGlyphColor(original: Color, plate: Color? = null, minimum: Float = IconContrast): Color {
    val surface = LocalForegroundSurface.current ?: return original
    val actual = plate?.let { effectiveSurfaceColor(surface, it) } ?: surface
    return readableForeground(original, actual, minimum)
}

/** Keep an inactive track visually distinct when it approaches the adjusted progress color. */
@Composable
internal fun surfaceProgressTrackColor(progress: Color, track: Color, plate: Color? = null): Color {
    val surface = LocalForegroundSurface.current ?: return track
    if (!track.isSpecified || track.alpha == 0f) return track
    val actual = plate?.let { effectiveSurfaceColor(surface, it) } ?: surface
    val visibleProgress = progress.compositeOver(actual)
    val visibleTrack = track.compositeOver(actual)
    return if (contrastAgainst(visibleProgress, visibleTrack) >= 1.5f) track else progress.copy(alpha = 0.38f)
}

@Composable
internal fun surfaceForegroundScrim(): Color = LocalForegroundScrim.current

@Composable
internal fun surfaceForegroundIsLight(default: Boolean): Boolean =
    LocalForegroundSurface.current?.let { neutralForeground(it) == Color.White } ?: default
