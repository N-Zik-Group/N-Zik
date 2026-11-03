package app.n_zik.android.components.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.n_zik.android.components.theme.withColor

/** Scope readable foreground roles to a real Artwork Glass surface, preserving its native theme. */
@Composable
internal fun GlassArtworkContent(
    settings: GlassSettings,
    active: Boolean = true,
    content: @Composable () -> Unit,
) {
    val appearance = LocalAppearance.current
    val forced = active && settings.enabled && glassArtworkForcesDark(settings)
    val palette = appearance.colorPalette
    val effective = if (forced) appearance.copy(
        colorPalette = palette.copy(text = Color.White, textSecondary = Color(0xFFBDBDBD),
            textDisabled = Color(0xFF858585), iconButtonPlayer = Color.White),
        typography = appearance.typography.withColor(Color.White),
    ) else appearance
    val contentColor = if (forced) Color.White else LocalContentColor.current
    CompositionLocalProvider(
        LocalAppearance provides effective,
        LocalContentColor provides contentColor,
        content = content,
    )
}

/** Explicit glyph colors may have been evaluated outside the surface's content provider. */
@Composable
internal fun glassArtworkForeground(settings: GlassSettings, original: Color, active: Boolean = true): Color =
    if (active && settings.enabled && glassArtworkForcesDark(settings)) Color.White.copy(alpha = original.alpha)
    else original
