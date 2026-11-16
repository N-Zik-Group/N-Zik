package app.n_zik.android.components.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalNativeQueueRows
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassFilmColor
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.surfaceGlyphColor
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.uiRoundnessShape

/** Settings-style accent badge centered in the caller's existing leading-icon slot. */
@Composable
fun LeadingIconBadge(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accent = colorPalette().accent
    val badgeFill = accent.copy(alpha = 0.1f)
    val glass = rememberGlassSettings()
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Keep the 32dp Settings badge without moving labels or enlarging its host.
        Box(
            modifier = Modifier
                .requiredSize(32.dp)
                .alpha(if (enabled) 1f else 0.5f)
                .iconBadgeBackground(badgeFill, uiRoundnessShape(), glass),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = iconBadgeGlyphColor(accent, glass),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Contrast for the accent glyph on the same plate that iconBadgeBackground draws. */
@Composable
internal fun iconBadgeGlyphColor(accent: Color, settings: GlassSettings = rememberGlassSettings()): Color {
    val fill = accent.copy(alpha = 0.1f)
    val plate = if (!LocalNativeQueueRows.current && settings.usesGlassMaterial(GlassArea.ActionButtons))
        glassFilmColor(settings.forArea(GlassArea.ActionButtons), fill, menu = false) else fill
    return surfaceGlyphColor(accent, plate)
}
