package app.n_zik.android.components.glass

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import app.n_zik.android.components.ui.InlineCircularLoader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import app.n_zik.android.components.ui.nativeControlIndication
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.ui.components.navigation.header.TabToolBar
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.ui.components.tab.toolbar.EllipsisMenuComponent
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.uiRoundnessShape

/** Individual toolbar controls use the same roundness policy as native short and long actions. */
@Composable
internal fun toolbarItemShape(override: Shape? = null): Shape = override ?: uiRoundnessShape()

private data class ToolbarStyle(val count: Int, val settings: GlassSettings,
    val cellWidth: Dp = GlassActionCellWidth, val wide: Boolean = false, val itemShape: Shape? = null,
    val compact: Boolean = false, val grouped: Boolean = false) {
    val actionWidth get() = if (count == 1 && !(compact && wide)) GlassStandaloneActionSize else cellWidth
}
private val LocalToolbarStyle = staticCompositionLocalOf<ToolbarStyle?> { null }

/** Retains the original toolbar controls and overflow; only their icon presentation changes. */
@Composable
fun GlassToolbarButtons(
    buttons: List<Button>,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceEvenly,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
    showTrailingSeparator: Boolean = true,
    itemShape: Shape? = null,
) {
    val material = settings.usesGlassMaterial(GlassArea.ActionButtons)
    if (!material) {
        TabToolBar.Buttons(buttons, horizontalArrangement, verticalAlignment,
            modifier.glassAreaContour(settings, GlassArea.ActionButtons, uiRoundnessShape()))
        return
    }
    // Match the original TabToolBar measurement exactly, including its caller's
    // width fraction. Glass must not reveal additional actions or change overflow.
    BoxWithConstraints(modifier.fillMaxWidth().padding(TabToolBar.HORIZONTAL_PADDING, TabToolBar.VERTICAL_PADDING)) {
        val canDisplay = ((maxWidth - TabToolBar.HORIZONTAL_PADDING * 2) / (TabToolBar.TOOLBAR_ICON_SIZE + 15.dp)).toInt().coerceAtLeast(0)
        if (canDisplay == 0) {
            Spacer(Modifier.fillMaxWidth())
        } else {
            val clustered = buttons.size > canDisplay
            val visible = buttons.take(if (clustered) canDisplay - 1 else buttons.size)
            val ellipsis = EllipsisMenuComponent.init { buttons.drop(visible.size) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement, verticalAlignment) {
                GlassToolbarRow(
                    visible.size + if (clustered) 1 else 0,
                    modifier = Modifier.fillMaxWidth(),
                    settings = settings,
                    expandToWidth = true,
                    itemShape = itemShape,
                    trailingSeparator = clustered && showTrailingSeparator,
                ) {
                    visible.forEach { it.ToolBarButton() }
                    if (clustered) ellipsis.ToolBarButton()
                }
            }
        }
    }
}

@Composable
fun GlassToolbarButtons(
    vararg buttons: Button,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceEvenly,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
    showTrailingSeparator: Boolean = true,
    itemShape: Shape? = null,
) = GlassToolbarButtons(buttons.toList(), horizontalArrangement, verticalAlignment, modifier, settings, showTrailingSeparator, itemShape)

/** Inline toolbars opt into the same scoped presentation without replacing their controls. */
@Composable
fun GlassToolbarRow(
    actionCount: Int,
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
    expandToWidth: Boolean = false,
    trailingSeparator: Boolean = false,
    itemShape: Shape? = null,
    compact: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    if (actionCount <= 0) return
    val material = settings.usesGlassMaterial(GlassArea.ActionButtons)
    if (!material && !compact) {
        Row(modifier.glassAreaContour(settings, GlassArea.ActionButtons,
            if (actionCount == 1) toolbarItemShape(itemShape) else uiRoundnessShape()),
            verticalAlignment = Alignment.CenterVertically, content = content)
        return
    }
    BoxWithConstraints(modifier) {
        val cellWidth = if (constraints.hasBoundedWidth && (actionCount > 1 || compact && expandToWidth))
            (maxWidth / actionCount).let { if (expandToWidth) it else it.coerceAtMost(GlassActionCellWidth) }
            else GlassActionCellWidth
        CompositionLocalProvider(LocalToolbarStyle provides ToolbarStyle(actionCount, settings, cellWidth, expandToWidth, itemShape, compact)) {
            if (actionCount == 1) Row(verticalAlignment = Alignment.CenterVertically, content = content)
            else if (!material) Row(Modifier.width(cellWidth * actionCount),
                verticalAlignment = Alignment.CenterVertically, content = content)
            else {
                // Fit every inline action: dragging a button belongs to that button,
                // so no actions may depend on scrolling through the capsule.
                GlassActionGroup(settings, GlassArea.ActionButtons,
                    modifier = Modifier.width(cellWidth * actionCount), separatorBefore = emptySet()) {
                    Row(Modifier.glassActionGroupSeparators(
                        settings, GlassArea.ActionButtons, cellWidth,
                        separatorBefore = if (trailingSeparator) setOf(actionCount - 1) else emptySet(),
                    ), verticalAlignment = Alignment.CenterVertically, content = content)
                }
            }
        }
    }
}

/** Returns false outside a Glass toolbar so the caller can execute its native renderer. */
@Composable
fun GlassToolbarColumn(
    actionCount: Int,
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (actionCount <= 0) return
    if (!settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, content = content)
        return
    }
    CompositionLocalProvider(LocalToolbarStyle provides ToolbarStyle(actionCount, settings, GlassStandaloneActionSize, grouped = true)) {
        GlassActionGroup(settings, GlassArea.ActionButtons,
            modifier.width(GlassStandaloneActionSize), separatorBefore = emptySet()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
        }
    }
}

@Composable
fun GlassToolbarLoading(): Boolean {
    val style = LocalToolbarStyle.current ?: return false
    Box(Modifier.size(style.actionWidth, GlassActionCellHeight),
        contentAlignment = Alignment.Center) {
        InlineCircularLoader(Modifier.size(GlassActionIconSize), color = toolbarForeground(style, colorPalette().text))
    }
    return true
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassToolbarIcon(
    icon: Painter,
    tint: Color,
    size: Dp,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    modifierOnContainer: Boolean = false,
): Boolean {
    val style = LocalToolbarStyle.current ?: return false
    val backdrop = toolbarActionBackdrop()
    val itemShape = toolbarItemShape(style.itemShape)
    val foreground = toolbarForeground(style, tint, enabled)
    val glyph: @Composable () -> Unit = {
        val iconSize = if (style.wide && !style.compact) (size - 8.dp).coerceIn(GlassActionIconSize, 24.dp) else GlassActionIconSize
        Icon(icon, contentDescription, Modifier.size(iconSize).then(if (modifierOnContainer || style.compact) Modifier else modifier),
            tint = if (enabled) foreground else foreground.copy(alpha = .5f))
    }
    // Collection rows own the complete cell. Legacy per-icon padding must not
    // enlarge a cell after the row has divided its available width.
    val containerModifier = if (modifierOnContainer && !style.compact) modifier else Modifier
    if (style.compact && !style.settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        Box(containerModifier.size(style.actionWidth,
            GlassActionCellHeight).clip(itemShape)
            .glassAreaContour(style.settings, GlassArea.ActionButtons, itemShape)
            .combinedClickable(remember { MutableInteractionSource() },
                glassGlobalIndication(nativeControlIndication()), enabled = enabled, role = Role.Button,
                onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center) { glyph() }
        return true
    }
    if (style.count == 1 && enabled) {
        LiquidButton(onClick, backdrop, containerModifier.size(style.actionWidth, GlassActionCellHeight),
            onLongClick = onLongClick, settings = style.settings.forArea(GlassArea.ActionButtons),
            filmColor = glassActionFilmColor(),
            contentHeight = GlassStandaloneActionSize, contentPadding = 0.dp, shape = itemShape, content = { glyph() })
    } else {
        val dimensions = containerModifier.size(style.actionWidth, GlassActionCellHeight)
        val surface = if (style.count == 1) dimensions.glassMaterial(
            style.settings.forArea(GlassArea.ActionButtons), itemShape,
            ownBackdrop = backdrop,
            filmColor = glassActionFilmColor(),
        ) else dimensions
        GlassActionGroupItem(onClick, modifier = surface, enabled = enabled, onLongClick = onLongClick,
            shape = itemShape) { glyph() }
    }
    return true
}

@Composable
internal fun toolbarActionBackdrop(): Backdrop {
    val background = colorPalette().background0
    val drawBackground: DrawScope.() -> Unit = remember(background) { { drawRect(background) } }
    val fallback = rememberCanvasBackdrop(drawBackground)
    return LocalGlassContainerBackdrop.current ?: LocalGlassBackdrop.current ?: fallback
}

/** Group cells inherit their capsule; only standalone controls composite another film. */
@Composable
private fun toolbarForeground(style: ToolbarStyle, color: Color, enabled: Boolean = true): Color {
    if (!enabled || !style.settings.usesGlassMaterial(GlassArea.ActionButtons)) return color
    val surface = if (style.count > 1 || style.grouped) {
        LocalGlassActionGroupSurface.current ?: surfaceBackdropColor()
    } else {
        val film = glassFilmColor(style.settings.forArea(GlassArea.ActionButtons), glassActionFilmColor(), menu = false)
        effectiveSurfaceColor(surfaceBackdropColor(), film)
    }
    return glassPaneForeground(color, surface, IconContrast, glassSurfaceDarkness(style.settings), glyph = true)
}
