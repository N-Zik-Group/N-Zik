package app.n_zik.android.components.glass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.EllipsisMenuComponent
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.uiRoundnessShape

/** Individual toolbar controls use the same roundness policy as native short and long actions. */
@Composable
internal fun toolbarItemShape(override: Shape? = null): Shape = override ?: uiRoundnessShape()

private data class ToolbarStyle(val count: Int, val settings: GlassSettings,
    val cellWidth: Dp = GlassActionCellWidth, val wide: Boolean = false, val itemShape: Shape? = null)
private val LocalToolbarStyle = staticCompositionLocalOf<ToolbarStyle?> { null }

/** Retains the original toolbar controls and overflow; only their icon presentation changes. */
@Composable
fun GlassToolbarButtons(
    buttons: List<Button>,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceEvenly,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    modifier: Modifier = Modifier,
    disableAnimation: Boolean = false,
    settings: GlassSettings = rememberGlassSettings(),
    showTrailingSeparator: Boolean = true,
    itemShape: Shape? = null,
) {
    val material = settings.usesGlassMaterial(GlassArea.ActionButtons)
    if (!material) {
        TabToolBar.Buttons(buttons, horizontalArrangement, verticalAlignment,
            modifier.glassAreaContour(settings, GlassArea.ActionButtons, uiRoundnessShape()), disableAnimation)
        return
    }
    // Match the original TabToolBar measurement exactly, including its caller's
    // width fraction. Glass must not reveal additional actions or change overflow.
    val density = LocalDensity.current.density
    var availableWidth by remember { mutableStateOf(0.dp) }
    var canDisplay by remember { mutableIntStateOf(0) }
    LaunchedEffect(availableWidth) {
        canDisplay = (availableWidth / (TabToolBar.TOOLBAR_ICON_SIZE + 15.dp)).toInt()
    }
    val baseModifier = modifier.fillMaxWidth()
        .padding(TabToolBar.HORIZONTAL_PADDING, TabToolBar.VERTICAL_PADDING)
        .onGloballyPositioned {
            availableWidth = (it.size.width / density).dp - TabToolBar.HORIZONTAL_PADDING * 2
        }
    val content: @Composable (List<Button>) -> Unit = { targetButtons ->
        if (canDisplay == 0) {
            Spacer(Modifier.fillMaxWidth())
        } else {
            val clustered = targetButtons.size > canDisplay
            val ellipsis = EllipsisMenuComponent.init {
                targetButtons.takeLast((targetButtons.size - canDisplay + 1).coerceAtLeast(0))
            }
            val visible = targetButtons.take(if (clustered) canDisplay - 1 else targetButtons.size)
            Row(Modifier.fillMaxWidth(), horizontalArrangement, verticalAlignment) {
                val controls: @Composable RowScope.() -> Unit = {
                    visible.forEach { it.ToolBarButton() }
                    if (clustered) ellipsis.ToolBarButton()
                }
                if (material) {
                    GlassToolbarRow(visible.size + if (clustered) 1 else 0,
                        modifier = Modifier.fillMaxWidth(),
                        settings = settings, expandToWidth = true, itemShape = itemShape,
                        trailingSeparator = clustered && showTrailingSeparator, content = controls)
                } else CompositionLocalProvider(LocalToolbarStyle provides null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement, verticalAlignment, content = controls)
                }
            }
        }
    }
    val motion = isGlassMotionAllowed()
    var appeared by rememberSaveable { mutableStateOf(false) }
    val appearance = remember { Animatable(if (appeared) 1f else 0f) }
    val ready = canDisplay > 0 && buttons.isNotEmpty()
    LaunchedEffect(ready, motion) {
        if (ready && !appeared) {
            if (motion) appearance.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            else appearance.snapTo(1f)
            appeared = true
        }
    }
    Box(baseModifier) {
        // Enter once when the width is known, including Home bars that disable
        // list-change animations. Fresh Button instances are not a new toolbar.
        Box(Modifier.graphicsLayer {
            val progress = if (appeared) 1f else appearance.value
            alpha = progress
            scaleX = .96f + .04f * progress
            scaleY = scaleX
        }) {
            if (disableAnimation || !motion) content(buttons)
            else AnimatedContent(buttons, contentKey = { target -> target.map { it.javaClass.name } },
                transitionSpec = {
                    // Preserve Compose's default entrance while allowing the pressed pane to overdraw.
                    ((fadeIn(tween(220, delayMillis = 90)) +
                        scaleIn(tween(220, delayMillis = 90), initialScale = .92f)) togetherWith fadeOut(tween(90)))
                        .using(SizeTransform(clip = false))
                },
                label = "GlassToolbarButtonsAnimation") { content(it) }
        }
    }
}

@Composable
fun GlassToolbarButtons(
    vararg buttons: Button,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceEvenly,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    modifier: Modifier = Modifier,
    disableAnimation: Boolean = false,
    settings: GlassSettings = rememberGlassSettings(),
    showTrailingSeparator: Boolean = true,
    itemShape: Shape? = null,
) = GlassToolbarButtons(buttons.toList(), horizontalArrangement, verticalAlignment, modifier, disableAnimation, settings, showTrailingSeparator, itemShape)

/** Inline toolbars opt into the same scoped presentation without replacing their controls. */
@Composable
fun GlassToolbarRow(
    actionCount: Int,
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
    expandToWidth: Boolean = false,
    trailingSeparator: Boolean = false,
    itemShape: Shape? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (actionCount <= 0) return
    if (!settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        Row(modifier.glassAreaContour(settings, GlassArea.ActionButtons,
            if (actionCount == 1) toolbarItemShape(itemShape) else uiRoundnessShape()),
            verticalAlignment = Alignment.CenterVertically, content = content)
        return
    }
    BoxWithConstraints(modifier) {
        val cellWidth = if (constraints.hasBoundedWidth && actionCount > 1)
            (maxWidth / actionCount).let { if (expandToWidth) it else it.coerceAtMost(GlassActionCellWidth) }
            else GlassActionCellWidth
        CompositionLocalProvider(LocalToolbarStyle provides ToolbarStyle(actionCount, settings, cellWidth, expandToWidth, itemShape)) {
            if (actionCount == 1) Row(verticalAlignment = Alignment.CenterVertically, content = content)
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
    CompositionLocalProvider(LocalToolbarStyle provides ToolbarStyle(actionCount, settings, GlassStandaloneActionSize)) {
        GlassActionGroup(settings, GlassArea.ActionButtons,
            modifier.width(GlassStandaloneActionSize), separatorBefore = emptySet()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
        }
    }
}

@Composable
fun GlassToolbarLoading(): Boolean {
    val style = LocalToolbarStyle.current ?: return false
    Box(Modifier.size(if (style.count == 1) GlassStandaloneActionSize else style.cellWidth, GlassActionCellHeight),
        contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(GlassActionIconSize), color = colorPalette().text, strokeWidth = 2.dp)
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
    val background = colorPalette().background0
    val drawBackground: DrawScope.() -> Unit = remember(background) { { drawRect(background) } }
    val fallbackBackdrop = rememberCanvasBackdrop(drawBackground)
    val backdrop = LocalGlassContainerBackdrop.current ?: LocalGlassBackdrop.current ?: fallbackBackdrop
    val itemShape = toolbarItemShape(style.itemShape)
    val glyph: @Composable () -> Unit = {
        val iconSize = if (style.wide) (size - 8.dp).coerceIn(GlassActionIconSize, 24.dp) else GlassActionIconSize
        Icon(icon, contentDescription, Modifier.size(iconSize).then(if (modifierOnContainer) Modifier else modifier),
            tint = if (enabled) tint else tint.copy(alpha = .5f))
    }
    if (style.count == 1 && enabled) {
        LiquidButton(onClick, backdrop, (if (modifierOnContainer) modifier else Modifier).size(GlassStandaloneActionSize),
            onLongClick = onLongClick, settings = style.settings.forArea(GlassArea.ActionButtons),
            filmColor = glassActionFilmColor(),
            contentHeight = GlassStandaloneActionSize, contentPadding = 0.dp, shape = itemShape, content = { glyph() })
    } else {
        val dimensions = (if (modifierOnContainer) modifier else Modifier).size(if (style.count == 1) GlassStandaloneActionSize else style.cellWidth,
            GlassActionCellHeight)
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
