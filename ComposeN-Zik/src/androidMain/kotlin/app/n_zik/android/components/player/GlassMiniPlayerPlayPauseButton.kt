package app.n_zik.android.components.player

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LiquidButton
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.GlassRenderer
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.glass.glassFilmOpacity
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.navigation.dock.LocalCompactMiniPlayerProgress
import app.n_zik.android.components.navigation.dock.LocalCompactMiniPlayerProgressProvider

/** Preserves the central control's shape and content when switching its material. */
@Composable
fun GlassMiniPlayerPlayPauseButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    contentDescription: String,
    settings: GlassSettings = rememberGlassSettings(),
    backdrop: Backdrop? = LocalGlassBackdrop.current ?: LocalGlassMenuHost.current?.backdrop,
    content: @Composable BoxScope.() -> Unit,
) {
    val fold = LocalCompactMiniPlayerProgress.current
    val dockPresentation = LocalCompactMiniPlayerProgressProvider.current != null
    // Compose clickable retains the 48dp minimum touch target around this visual control.
    val controlSize = if (dockPresentation) 42.dp - 6.dp * fold else if (fold > 0.5f) 48.dp else 42.dp
    val shape = uiRoundnessShape()
    val modifier = Modifier.semantics { this.contentDescription = contentDescription }
    if (settings.usesGlassMaterial(GlassArea.MiniPlayer)) {
        val controlSettings = settings.forArea(GlassArea.MiniPlayer).copy(film = glassFilmOpacity(
            configured = settings.film,
            overlay = true,
            fallback = backdrop == null || glassRenderer(settings) == GlassRenderer.Tint,
            dark = colorPalette().isDark,
        ))
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier.size(controlSize),
            settings = controlSettings,
            filmColor = backgroundColor,
            contentHeight = controlSize,
            contentPadding = 0.dp,
            shape = shape,
            backdropScale = 1f,
        ) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    } else {
        Box(
            modifier.clip(shape).clickable(role = Role.Button, onClick = onClick)
                .background(backgroundColor).glassAreaContour(settings, GlassArea.MiniPlayer, shape).size(controlSize),
            content = content,
        )
    }
}
