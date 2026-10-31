package app.n_zik.android.components.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.MutableInteractionSource
import app.n_zik.android.components.ui.nativeControlIndication
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import app.n_zik.android.components.glass.glassGlobalIndication
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

@OptIn(ExperimentalFoundationApi::class)
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
    onLongClick: (() -> Unit)? = null,
    preserveNativeGeometry: Boolean = false,
) {
    MiniPlayerIconSurface(
        enabled, rotationAngle, alpha, contentDescription, onClick, onLongClick, settings, backdrop, preserveNativeGeometry,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier.size(it).testTag("mini.player.glyph"),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    onLongClick: (() -> Unit)? = null,
    preserveNativeGeometry: Boolean = false,
) {
    MiniPlayerIconSurface(
        enabled, rotationAngle, alpha, contentDescription, onClick, onLongClick, settings, backdrop, preserveNativeGeometry,
    ) {
        Image(
            imageVector = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier.size(it).testTag("mini.player.glyph"),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniPlayerIconSurface(
    enabled: Boolean,
    rotationAngle: Float,
    alpha: Float,
    description: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    settings: GlassSettings,
    backdrop: Backdrop?,
    preserveNativeGeometry: Boolean,
    icon: @Composable (androidx.compose.ui.unit.Dp) -> Unit,
) {
    val fold = LocalCompactMiniPlayerProgress.current
    val dockPresentation = LocalCompactMiniPlayerProgressProvider.current != null
    val material = settings.usesGlassMaterial(GlassArea.MiniPlayer)
    val geometry = miniPlayerIconGeometry(fold, dockPresentation, material && !preserveNativeGeometry)
    val shape = uiRoundnessShape()
    MiniPlayerActionCanvas(geometry, Modifier.rotate(rotationAngle)) { canvas ->
        val modifier = canvas.alpha(alpha).semantics {
            contentDescription = description
            if (!enabled) disabled()
        }
        if (!material) {
            Box(modifier.glassAreaContour(settings, GlassArea.MiniPlayer, shape).clip(shape)
                .combinedClickable(interactionSource = remember { MutableInteractionSource() },
                    indication = glassGlobalIndication(nativeControlIndication()),
                    enabled = enabled, role = Role.Button, onClick = onClick, onLongClick = onLongClick),
                contentAlignment = Alignment.Center) { icon(24.dp) }
        } else {
            LiquidButton(
                onClick = onClick,
                onLongClick = onLongClick,
                backdrop = backdrop,
                modifier = modifier,
                settings = settings.forArea(GlassArea.MiniPlayer),
                filmColor = surfaceColor(colorPalette().background2),
                isInteractive = enabled,
                enabled = enabled,
                contentHeight = geometry.canvas,
                contentPadding = 0.dp,
                shape = shape,
                backdropScale = 1f,
            ) { icon(if (preserveNativeGeometry) 24.dp else 22.dp) }
        }
    }
}
