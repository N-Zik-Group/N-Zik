package app.n_zik.android.components.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.components.theme.withColor

/** Scope readable foreground roles to a real Artwork Glass surface, preserving its native theme. */
@Composable
internal fun GlassArtworkContent(
    settings: GlassSettings,
    active: Boolean = true,
    surface: Color? = null,
    content: @Composable () -> Unit,
) {
    val appearance = LocalAppearance.current
    if (surface != null) {
        // A concrete plate owns its ink independently of a surrounding player transition.
        CompositionLocalProvider(
            LocalNeutralForegroundTransition provides null,
            LocalNeutralForegroundOverride provides null,
            LocalNeutralForegroundSurface provides null,
            LocalNeutralForegroundAmount provides 1f,
        ) {
            SurfaceForegroundContent(surface, content = content)
        }
        return
    }
    val forced = active && settings.enabled && glassArtworkForcesDark(settings)
    val palette = appearance.colorPalette
    val artworkSurface = surface ?: glassSurfaceTint(settings)
    fun neutralRole(original: Color): Color = readableForeground(original, artworkSurface, IconContrast)
    val foregroundPalette = if (forced) palette.copy(
        text = neutralRole(palette.text),
        textSecondary = neutralRole(palette.textSecondary),
        textDisabled = neutralRole(palette.textDisabled),
        iconButtonPlayer = neutralRole(palette.iconButtonPlayer),
    ) else palette
    val effective = if (forced) appearance.copy(
        colorPalette = foregroundPalette,
        typography = appearance.typography.withColor(foregroundPalette.text),
    ) else appearance
    val inheritedContent = LocalContentColor.current
    val contentColor = if (forced) neutralRole(inheritedContent) else inheritedContent
    CompositionLocalProvider(
        LocalAppearance provides effective,
        LocalContentColor provides contentColor,
        content = content,
    )
}

/** Explicit glyph colors may have been evaluated outside the surface's content provider. */
@Composable
internal fun glassArtworkForeground(settings: GlassSettings, original: Color, active: Boolean = true): Color =
    if (active && settings.enabled && glassArtworkForcesDark(settings) && isNeutralForeground(original))
        readableGlyphForeground(original, glassSurfaceTint(settings), IconContrast)
    else original
