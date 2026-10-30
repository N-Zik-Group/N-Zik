package app.n_zik.android.components.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LiquidButton
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.searchBubbleShape
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.surfaceColor
import app.n_zik.android.components.navigation.dock.LocalCompactMiniPlayerProgress
import app.n_zik.android.components.navigation.dock.LocalCompactMiniPlayerProgressProvider

@Composable
fun GlassMiniPlayerIconButton(
    @DrawableRes icon: Int,
    color: Color,
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    rotationAngle: Float = 0f,
    alpha: Float = 1f,
    settings: GlassSettings = rememberGlassSettings(),
    backdrop: Backdrop? = LocalGlassBackdrop.current ?: LocalGlassMenuHost.current?.backdrop,
) {
    MiniPlayerIconSurface(
        enabled, rotationAngle, alpha, contentDescription, onClick, settings, backdrop,
        fallback = { modifier -> IconButton(onClick, icon, color, modifier, enabled) },
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun GlassMiniPlayerIconButton(
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    rotationAngle: Float = 0f,
    alpha: Float = 1f,
    settings: GlassSettings = rememberGlassSettings(),
    backdrop: Backdrop? = LocalGlassBackdrop.current ?: LocalGlassMenuHost.current?.backdrop,
) {
    MiniPlayerIconSurface(
        enabled, rotationAngle, alpha, contentDescription, onClick, settings, backdrop,
        fallback = { modifier -> IconButton(onClick, icon, color, modifier, enabled) },
    ) {
        Image(
            imageVector = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun MiniPlayerIconSurface(
    enabled: Boolean,
    rotationAngle: Float,
    alpha: Float,
    description: String,
    onClick: () -> Unit,
    settings: GlassSettings,
    backdrop: Backdrop?,
    fallback: @Composable (Modifier) -> Unit,
    icon: @Composable () -> Unit,
) {
    val fold = LocalCompactMiniPlayerProgress.current
    val compact = fold > 0.5f
    val dockPresentation = LocalCompactMiniPlayerProgressProvider.current != null
    val controlSize = if (dockPresentation) 42.dp - 6.dp * fold else if (compact) 48.dp else 42.dp
    val shape = if (dockPresentation) uiRoundnessShape() else searchBubbleShape()
    val modifier = Modifier.rotate(rotationAngle)
    if (!settings.usesGlassMaterial(GlassArea.MiniPlayer)) {
        fallback(
            modifier.then(if (dockPresentation) Modifier.padding(horizontal = 2.dp + 4.dp * fold, vertical = 8.dp - 2.dp * fold)
                else Modifier.padding(horizontal = if (compact) 12.dp else 2.dp, vertical = if (compact) 12.dp else 8.dp)).size(24.dp)
                .alpha(alpha).glassAreaContour(settings, GlassArea.MiniPlayer, shape).semantics { contentDescription = description },
        )
        return
    }
    LiquidButton(
        onClick = { if (enabled) onClick() },
        backdrop = backdrop,
        modifier = modifier.padding(horizontal = if (dockPresentation) 2.dp * (1f - fold) else if (compact) 0.dp else 2.dp).size(controlSize).alpha(alpha)
            .semantics {
                contentDescription = description
                if (!enabled) disabled()
            },
        settings = settings.forArea(GlassArea.MiniPlayer),
        filmColor = surfaceColor(colorPalette().background2),
        isInteractive = enabled,
        contentHeight = controlSize,
        contentPadding = 0.dp,
        shape = shape,
        // A 42 dp pane must keep enough source pixels for the lens at low density.
        backdropScale = 1f,
    ) { icon() }
}
