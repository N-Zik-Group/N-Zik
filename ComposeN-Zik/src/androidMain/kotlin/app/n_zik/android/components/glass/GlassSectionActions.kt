package app.n_zik.android.components.glass

import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import app.n_zik.android.components.ui.InlineCircularLoader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import app.n_zik.android.colorPalette
import app.n_zik.android.components.navigation.header.HeaderActionCanvasSize
import app.n_zik.android.components.navigation.header.HeaderActionCellSize
import app.n_zik.android.components.navigation.header.HeaderNativeActionIconSize
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.ui.nativeControlIndication
import app.n_zik.android.components.ui.reservedIconActionCanvas

/** Shared native action seat; the glyph remains independently sized. */
internal val NativeIconActionSize = 42.dp

/** Legacy glyph spacing is already partly supplied by the larger native action seat. */
private fun nativeSectionEndPadding(canvasSize: Dp, iconSize: Dp, endSpacing: Dp): Dp =
    (endSpacing - (canvasSize - iconSize).coerceAtLeast(0.dp)).coerceAtLeast(0.dp)

/** A section-heading action with its original native spacing. */
@Stable
internal data class GlassSectionAction(
    @DrawableRes val icon: Int,
    val contentDescription: String?,
    val nativeEndPadding: Dp = 0.dp,
    val iconModifier: Modifier = Modifier,
    val nativeIconSize: Dp = HeaderNativeActionIconSize,
    val nativeColor: Color? = null,
    val glassColor: Color? = null,
    val loading: Boolean = false,
    val separateBefore: Boolean = false,
    val onLongClick: (() -> Unit)? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** One action forms a bubble; several share a capsule and its short separators. */
@Composable
internal fun GlassSectionActions(
    actions: List<GlassSectionAction>,
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
) {
    if (actions.isEmpty()) return
    if (settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        val singleAction = actions.singleOrNull()?.takeUnless { it.loading }
        if (singleAction != null) {
            val backgroundColor = colorPalette().background0
            val drawBackground: DrawScope.() -> Unit = remember(backgroundColor) { { drawRect(backgroundColor) } }
            // A supplied container is child-free; otherwise avoid sampling the ancestor page capture.
            val fallbackBackdrop = rememberCanvasBackdrop(drawBackground)
            val backdrop = LocalGlassContainerBackdrop.current ?: fallbackBackdrop
            Box(modifier.size(HeaderActionCanvasSize), contentAlignment = Alignment.Center) {
                LiquidButton(
                    onClick = singleAction.onClick,
                    onLongClick = singleAction.onLongClick,
                    backdrop = backdrop,
                    modifier = Modifier.size(HeaderActionCanvasSize),
                    settings = settings.forArea(GlassArea.ActionButtons),
                    enabled = singleAction.enabled,
                    filmColor = glassActionFilmColor(),
                    contentHeight = HeaderActionCanvasSize,
                    contentPadding = 0.dp,
                    shape = uiRoundnessShape(),
                ) {
                    Icon(
                        painterResource(singleAction.icon), singleAction.contentDescription,
                        tint = if (singleAction.enabled) singleAction.glassColor ?: colorPalette().textSecondary else colorPalette().textDisabled,
                        modifier = Modifier.size(GlassActionIconSize).then(singleAction.iconModifier),
                    )
                }
            }
            return
        }
        CompositionLocalProvider(LocalGlassActionCellSize provides HeaderActionCellSize) {
            GlassActionGroup(settings, GlassArea.ActionButtons, modifier = modifier,
                separatorBefore = actions.indices.filter { it > 0 && actions[it].separateBefore }.toSet()) {
                actions.forEach { action ->
                    if (action.loading) {
                        Box(
                            Modifier.size(HeaderActionCanvasSize),
                            contentAlignment = Alignment.Center,
                        ) {
                            InlineCircularLoader(Modifier.size(18.dp), color = colorPalette().textSecondary)
                        }
                    } else {
                        GlassActionGroupItem(
                            onClick = action.onClick,
                            onLongClick = action.onLongClick,
                            enabled = action.enabled,
                        ) {
                            Icon(
                                painterResource(action.icon), action.contentDescription,
                                tint = if (action.enabled) action.glassColor ?: colorPalette().textSecondary else colorPalette().textDisabled,
                                modifier = Modifier.size(GlassActionIconSize).then(action.iconModifier),
                            )
                        }
                    }
                }
            }
        }
    } else {
        val shape = uiRoundnessShape()
        Row(
            modifier = modifier.glassAreaContour(settings, GlassArea.ActionButtons, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { action ->
                if (action.loading) {
                    Box(Modifier.padding(end = nativeSectionEndPadding(HeaderActionCanvasSize, action.nativeIconSize, action.nativeEndPadding))
                        .size(HeaderActionCanvasSize), contentAlignment = Alignment.Center) {
                        InlineCircularLoader(Modifier.size(18.dp),
                            color = colorPalette().textSecondary)
                    }
                } else {
                    NativeSectionIconAction(
                        icon = action.icon,
                        contentDescription = action.contentDescription,
                        endSpacing = action.nativeEndPadding,
                        iconModifier = action.iconModifier,
                        iconSize = action.nativeIconSize,
                        canvasSize = HeaderActionCanvasSize,
                        color = action.nativeColor ?: if (action.enabled) colorPalette().textSecondary else colorPalette().textDisabled,
                        settings = settings,
                        enabled = action.enabled,
                        onLongClick = action.onLongClick,
                        onClick = action.onClick,
                    )
                }
            }
        }
    }
}

/** Native actions keep the glyph centered in the caller's action canvas. */
@Composable
internal fun NativeSectionIconAction(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    iconSize: Dp = HeaderNativeActionIconSize,
    color: Color = colorPalette().textSecondary,
    settings: GlassSettings = rememberGlassSettings(),
    endSpacing: Dp = 0.dp,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    canvasSize: Dp = NativeIconActionSize,
    onClick: () -> Unit,
) {
    val shape = uiRoundnessShape()
    Box(modifier.padding(end = nativeSectionEndPadding(canvasSize, iconSize, endSpacing)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(canvasSize)
            .glassAreaContour(settings, GlassArea.ActionButtons, shape)
            .reservedIconActionCanvas(shape, remember { MutableInteractionSource() },
                glassGlobalIndication(nativeControlIndication()), enabled = enabled,
                onClick = onClick, onLongClick = onLongClick, role = Role.Button), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription, tint = color,
                modifier = Modifier.size(iconSize).then(iconModifier))
        }
    }
}
