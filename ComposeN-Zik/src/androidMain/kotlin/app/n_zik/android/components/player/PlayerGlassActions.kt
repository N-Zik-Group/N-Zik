@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.n_zik.android.components.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import app.it.fast4x.rimusic.ui.components.themed.DownloadStateIconButton
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.utils.DOWNLOAD_INDICATOR_SIZE_NORMAL
import app.it.fast4x.rimusic.utils.DOWNLOAD_INDICATOR_STROKE_WIDTH
import app.it.fast4x.rimusic.utils.getDownloadProgress
import app.it.fast4x.rimusic.utils.isLandscape
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassActionCellHeight
import app.n_zik.android.components.glass.GlassActionGroup
import app.n_zik.android.components.glass.GlassActionGroupMotionHost
import app.n_zik.android.components.glass.LocalGlassGroupPressMotion
import app.n_zik.android.components.glass.glassGroupPressMotion
import app.n_zik.android.components.glass.isGlassMotionAllowed
import app.n_zik.android.components.glass.retainGlassButtonGesture
import app.n_zik.android.components.glass.glassButtonFeedback
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassFloatingActionIcon
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassActionGroupSeparators
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.inScope
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.uiRoundnessShape

/** A slot is added only after the original visibility and offline conditions pass. */
class PlayerActionSlot(val id: String, val nativeHeight: Dp = 24.dp, val content: @Composable () -> Unit)

/** Full-player controls share the same physical side margins. */
const val PlayerControlsWidthFraction = .9f

/** Shared horizontal geometry for both full-player lower rows. */
fun Modifier.playerActionToolbarWidth(landscape: Boolean): Modifier =
    if (landscape) padding(horizontal = 12.dp).fillMaxWidth()
    else fillMaxWidth(PlayerControlsWidthFraction)

/** Lower actions retain generous vertical padding independently of the upper bubbles. */
val PlayerActionToolbarHeight = 56.dp

/** Adds only the toolbar's new footprint, retaining the native container's breathing room. */
class PlayerActionToolbarLayout {
    private var extraHeight by mutableStateOf(PlayerActionToolbarHeight - 24.dp)

    fun reconcile(actions: List<PlayerActionSlot>) {
        extraHeight = actions.maxOfOrNull { it.nativeHeight }?.let {
            (PlayerActionToolbarHeight - it).coerceAtLeast(0.dp)
        } ?: 0.dp
    }

    fun hostHeight(nativeHeight: Dp): Dp = nativeHeight + extraHeight
}

/** Shares the resolved toolbar footprint with its legacy outer container. */
@Composable
fun rememberPlayerActionToolbarLayout(): PlayerActionToolbarLayout = remember { PlayerActionToolbarLayout() }

/** Empty toolbar space opens the queue without painting over the individual action canvases. */
@Composable
fun Modifier.playerActionQueueTap(enabled: Boolean, onClick: () -> Unit): Modifier = clickable(
    enabled = enabled,
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

private data class PlayerToolbarStyle(
    val settings: GlassSettings,
    val single: Boolean,
    val actions: List<PlayerActionSlot>,
    val bounds: Map<Int, Rect>,
    val nativeBackground: Color,
    val onBounds: (Int, Rect) -> Unit,
)
private val LocalPlayerToolbar = staticCompositionLocalOf<PlayerToolbarStyle?> { null }
private val LocalPlayerSlotIndex = staticCompositionLocalOf { 0 }

/** Upper controls use the player's existing live container backdrop. */
@Composable
fun PlayerTopAction(
    @DrawableRes icon: Int,
    color: Color,
    rotation: Float,
    onClick: () -> Unit,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerActions),
    contentDescription: String? = null,
) {
    val settings = settings.inScope(GlassScope.PlayerActions)
    if (GlassFloatingActionIcon(painterResource(icon), onClick, size = GlassActionCellHeight,
            iconSize = 24.dp, iconModifier = Modifier.rotate(rotation), tint = color,
            settings = settings, contentDescription = contentDescription)) return
    Box(Modifier.size(GlassActionCellHeight)
        .glassAreaContour(settings, GlassArea.ActionButtons, uiRoundnessShape())
        .clip(uiRoundnessShape()).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Image(painterResource(icon), contentDescription, colorFilter = ColorFilter.tint(color),
            modifier = Modifier.rotate(rotation).size(24.dp))
    }
}

/** Portrait matches the header margins; landscape retains the existing panel margins. */
@Composable
fun PlayerActionToolbar(
    actions: List<PlayerActionSlot>,
    spaceEvenly: Boolean,
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerActions),
    layout: PlayerActionToolbarLayout? = null,
    horizontalInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    landscape: Boolean = isLandscape,
    nativeBackground: Color = Color.Transparent,
) {
    val settings = settings.inScope(GlassScope.PlayerActions)
    SideEffect { layout?.reconcile(actions) }
    if (actions.isEmpty()) return
    val glass = settings.usesGlassMaterial(GlassArea.ActionButtons)
    val geometry = LocalPlayerTransportGeometry.current
    val arrangement = if (spaceEvenly) Arrangement.SpaceEvenly else Arrangement.SpaceBetween
    val content: @Composable () -> Unit = {
        actions.forEachIndexed { index, action -> key(action.id, index) {
            CompositionLocalProvider(LocalPlayerSlotIndex provides index,
                app.n_zik.android.components.glass.LocalGlassScope provides GlassScope.PlayerActions) { action.content() }
        } }
    }
    val glassRowModifier = Modifier.playerActionToolbarWidth(landscape)
    val cellPadding = if (actions.size > 1) Modifier.padding(
        start = ((48.dp - actions.first().nativeHeight) / 2).coerceAtLeast(0.dp),
        end = ((48.dp - actions.last().nativeHeight) / 2).coerceAtLeast(0.dp)) else Modifier
    val bounds = remember(actions.map { it.id }) { mutableStateMapOf<Int, Rect>() }
    val style = PlayerToolbarStyle(settings, actions.size == 1, actions, bounds, nativeBackground) { index, rect -> bounds[index] = rect }
    if (!glass) {
        Box(modifier.fillMaxWidth().windowInsetsPadding(horizontalInsets), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalPlayerToolbar provides style) {
                Box(glassRowModifier.height(PlayerActionToolbarHeight)) {
                    if (actions.size > 1) Box(Modifier.matchParentSize()
                        .onGloballyPositioned { geometry?.toolbar = geometry?.bounds(it) }.testTag("player.actionToolbar.native")
                        .background(nativeBackground, uiRoundnessShape())
                        .glassAreaContour(settings, GlassArea.ActionButtons, uiRoundnessShape()))
                    Row(Modifier.fillMaxWidth().height(PlayerActionToolbarHeight).then(cellPadding).testTag("player.actionToolbar.row"),
                        horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) { content() }
                }
            }
        }
        return
    }
    Box(modifier.fillMaxWidth().windowInsetsPadding(horizontalInsets), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalPlayerToolbar provides style) {
            if (actions.size == 1) {
                Row(glassRowModifier.height(PlayerActionToolbarHeight),
                    horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) { content() }
            } else {
                val previous = bounds[actions.lastIndex - 1]
                val trailing = bounds[actions.lastIndex]
                val boundary = if (actions.last().id in setOf("menu", "arrow") && previous != null && trailing != null)
                    with(LocalDensity.current) { ((previous.right + trailing.left) / 2f).toDp() } +
                        ((48.dp - actions.first().nativeHeight) / 2).coerceAtLeast(0.dp) else 0.dp
                GlassActionGroupMotionHost(glass && isGlassMotionAllowed(), glassRowModifier.height(PlayerActionToolbarHeight)) {
                    Box(Modifier.matchParentSize().requiredHeight(PlayerActionToolbarHeight)
                        .onGloballyPositioned { geometry?.toolbar = geometry?.bounds(it) }.testTag("player.actionToolbar.material")
                        .glassMaterial(settings.forArea(GlassArea.ActionButtons), uiRoundnessShape(),
                            ownBackdrop = LocalGlassContainerBackdrop.current)
                        .glassActionGroupSeparators(settings, GlassArea.ActionButtons, boundary, separatorBefore = setOf(1)))
                    GlassActionGroup(settings, GlassArea.ActionButtons, modifier = Modifier.fillMaxWidth(),
                        separatorBefore = emptySet(), drawSurface = false) {
                        Row(Modifier.fillMaxWidth().height(PlayerActionToolbarHeight).then(cellPadding).testTag("player.actionToolbar.row"), horizontalArrangement = arrangement,
                            verticalAlignment = Alignment.CenterVertically) { content() }
                    }
                }
            }
        }
    }
}

/** Owns the only click target; modifiers supplied by legacy callers affect the glyph alone. */
@Composable
fun PlayerActionIcon(
    @DrawableRes icon: Int,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val style = LocalPlayerToolbar.current
    if (style == null) {
        IconButton(onClick, icon, color, modifier, enabled, onLongClick = onLongClick)
        return
    }
    PlayerActionCell(enabled, onClick, onLongClick) {
        Image(painterResource(icon), null, colorFilter = ColorFilter.tint(color), modifier = modifier)
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun PlayerActionCell(
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val style = LocalPlayerToolbar.current ?: return
    val geometry = LocalPlayerTransportGeometry.current
    val index = LocalPlayerSlotIndex.current
    val glass = style.settings.usesGlassMaterial(GlassArea.ActionButtons)
    val interaction = remember { MutableInteractionSource() }
    val shape = uiRoundnessShape()
    val nativeSize = style.actions[index].nativeHeight
    val density = LocalDensity.current
    val own = style.bounds[index]
    val previous = style.bounds[index - 1]
    val next = style.bounds[index + 1]
    val requested = if (style.single) PlayerActionToolbarHeight else 48.dp
    val width = with(density) {
        val leftSpace = if (own != null && previous != null) (own.center.x - previous.center.x) else Float.POSITIVE_INFINITY
        val rightSpace = if (own != null && next != null) (next.center.x - own.center.x) else Float.POSITIVE_INFINITY
        minOf(requested.toPx(), leftSpace, rightSpace).toDp().coerceAtLeast(nativeSize)
    }
    val cell: @Composable () -> Unit = {
        // The row still measures its original glyph slots. Only the interaction
        // canvas fills the free space around them, keeping every glyph center fixed.
        Box(Modifier.testTag("player.actionSlot.$index").onGloballyPositioned {
            style.onBounds(index, it.boundsInParent())
        }) {
        Box(Modifier.layout { measurable, constraints ->
            val slot = if (style.single) PlayerActionToolbarHeight.roundToPx() else nativeSize.roundToPx()
            val canvas = measurable.measure(Constraints.fixed(width.roundToPx(), requested.roundToPx()))
            val slotWidth = slot.coerceIn(constraints.minWidth, constraints.maxWidth)
            val slotHeight = slot.coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(slotWidth, slotHeight) { canvas.place((slotWidth - canvas.width) / 2, (slotHeight - canvas.height) / 2) }
        }.clip(shape).retainGlassButtonGesture(enabled && glass)
            .glassGroupPressMotion(interaction, enabled = enabled && glass)
            .combinedClickable(interactionSource = interaction,
            indication = if (glass) null else ripple(), enabled = enabled,
            onClick = onClick, onLongClick = onLongClick), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().glassButtonFeedback(interaction, shape,
                enabled = enabled && glass, clipToBounds = true, retainPressUntilUp = true,
                animateMotion = LocalGlassGroupPressMotion.current == null), contentAlignment = Alignment.Center) { content() }
        }
        }
    }
    if (style.single) {
        GlassActionGroupMotionHost(glass && isGlassMotionAllowed(), Modifier.size(PlayerActionToolbarHeight)) {
            // The single action shares the toolbar height and its interaction treatment.
            Box(Modifier.matchParentSize().requiredSize(PlayerActionToolbarHeight)
                .onGloballyPositioned { geometry?.toolbar = geometry?.bounds(it) }
                .testTag(if (glass) "player.actionToolbar.material" else "player.actionToolbar.native")
                .then(if (glass) Modifier.glassMaterial(style.settings.forArea(GlassArea.ActionButtons), uiRoundnessShape(),
                    ownBackdrop = LocalGlassContainerBackdrop.current) else Modifier.background(style.nativeBackground, uiRoundnessShape())
                    .glassAreaContour(style.settings, GlassArea.ActionButtons, uiRoundnessShape())))
            cell()
        }
    } else cell()
}

/** Active downloads retain cancellation and the same determinate/indeterminate progress. */
@UnstableApi
@Composable
fun PlayerDownloadAction(
    onClick: () -> Unit,
    onCancelButtonClicked: () -> Unit,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    downloadState: Int,
    mediaId: String? = null,
) {
    if (LocalPlayerToolbar.current == null) {
        DownloadStateIconButton(onClick, onCancelButtonClicked, icon, color, modifier, enabled,
            downloadState = downloadState, mediaId = mediaId)
        return
    }
    val active = downloadState == Download.STATE_DOWNLOADING || downloadState == Download.STATE_QUEUED ||
        downloadState == Download.STATE_RESTARTING
    val progress = if (mediaId != null) getDownloadProgress(mediaId) else 0f
    PlayerActionCell(enabled, if (active) onCancelButtonClicked else onClick) {
        if (!active) Image(painterResource(icon), null, colorFilter = ColorFilter.tint(color), modifier = modifier)
        else Box(modifier, contentAlignment = Alignment.Center) {
            val stroke = Stroke(with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() })
            val progressModifier = Modifier.size(DOWNLOAD_INDICATOR_SIZE_NORMAL.dp)
            if (progress > .01f && downloadState == Download.STATE_DOWNLOADING)
                CircularWavyProgressIndicator(progress = { progress }, modifier = progressModifier,
                    color = colorPalette().accent, trackColor = colorPalette().textDisabled,
                    stroke = stroke, trackStroke = stroke)
            else CircularWavyProgressIndicator(modifier = progressModifier,
                color = colorPalette().accent, trackColor = colorPalette().textDisabled,
                stroke = stroke, trackStroke = stroke)
        }
    }
}
