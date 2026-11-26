@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.n_zik.android.components.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable
import app.n_zik.android.components.glass.LocalSurfaceBackdropColor
import app.n_zik.android.components.glass.SurfaceForegroundContent
import app.n_zik.android.components.glass.effectiveSurfaceColor
import app.n_zik.android.components.glass.glassFilmColor
import app.n_zik.android.components.glass.surfaceForegroundScrim
import app.n_zik.android.components.glass.surfaceGlyphColor
import app.n_zik.android.components.glass.surfaceBackdropColor
import app.n_zik.android.components.glass.surfaceProgressTrackColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.ui.components.themed.DownloadStateIconButton
import app.n_zik.android.ui.components.themed.IconButton
import app.n_zik.android.utils.DOWNLOAD_INDICATOR_SIZE_NORMAL
import app.n_zik.android.utils.DOWNLOAD_INDICATOR_STROKE_WIDTH
import app.n_zik.android.utils.getDownloadProgress
import app.n_zik.android.utils.isLandscape
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
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.uiRoundnessShape

/** A slot is added only after the original visibility and offline conditions pass. */
class PlayerActionSlot(
    val id: String,
    val nativeHeight: Dp = 24.dp,
    val spansTwoCells: Boolean = false,
    val content: @Composable () -> Unit,
)

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
    private var nativeHeight by mutableStateOf<Dp?>(24.dp)
    private var surfaceEnabled by mutableStateOf(false)

    fun configure(settings: GlassSettings) {
        val scoped = settings.inScope(GlassScope.PlayerActions)
        surfaceEnabled = scoped.usesGlassMaterial(GlassArea.ActionButtons) ||
            scoped.usesGlassContour(GlassArea.ActionButtons)
    }

    fun reconcile(actions: List<PlayerActionSlot>) {
        nativeHeight = actions.maxOfOrNull { it.nativeHeight }
    }

    fun hostHeight(nativeHeight: Dp): Dp = nativeHeight + if (surfaceEnabled)
        this.nativeHeight?.let { (PlayerActionToolbarHeight - it).coerceAtLeast(0.dp) } ?: 0.dp
    else 0.dp

    fun toolbarHeight(): Dp = nativeHeight?.let { if (surfaceEnabled) PlayerActionToolbarHeight else it } ?: 0.dp
}

/** Shares the resolved toolbar footprint with its legacy outer container. */
@Composable
fun rememberPlayerActionToolbarLayout(
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerActions),
): PlayerActionToolbarLayout = remember { PlayerActionToolbarLayout() }.also { it.configure(settings) }

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
    val contrastScrim: Color,
    val placementMotions: Map<String, PlayerReflowPlacement>,
    val isTopAction: Boolean = false,
    val horizontalOverflow: Boolean = false,
    val onBounds: (Int, Rect) -> Unit,
) {
    val surfaceEnabled: Boolean get() = settings.usesGlassMaterial(GlassArea.ActionButtons) ||
        settings.usesGlassContour(GlassArea.ActionButtons)
    val height: Dp get() = when {
        isTopAction -> GlassActionCellHeight
        surfaceEnabled -> PlayerActionToolbarHeight
        else -> actions.maxOf { it.nativeHeight }
    }
}
private val LocalPlayerToolbar = staticCompositionLocalOf<PlayerToolbarStyle?> { null }
private val LocalPlayerSlotIndex = staticCompositionLocalOf { 0 }
private val LocalPlayerActionSurface = staticCompositionLocalOf<Color?> { null }
internal val LocalPlayerActionToolbarBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
private fun Modifier.playerVisibleToolbarBounds(settings: GlassSettings, background: Color, nativeHeight: Dp): Modifier {
    val geometry = LocalPlayerTransportGeometry.current
    val surface = settings.usesGlassMaterial(GlassArea.ActionButtons) ||
        settings.usesGlassContour(GlassArea.ActionButtons) || background.alpha > 0f
    val nativePixels = with(LocalDensity.current) { nativeHeight.toPx() }
    DisposableEffect(geometry) { onDispose { geometry?.toolbar = null; geometry?.visibleToolbar = null } }
    return onPlaced { coordinates ->
        val frame = geometry?.bounds(coordinates)
        geometry?.toolbar = frame
        geometry?.visibleToolbar = frame?.let {
            val inset = if (surface) 0f else ((it.height - nativePixels) / 2).coerceAtLeast(0f)
            Rect(it.left, it.top + inset, it.right, it.bottom - inset)
        }
    }
}

/** Upper controls can refract the passive information plate where their surfaces overlap. */
@Composable
fun PlayerTopAction(
    @DrawableRes icon: Int,
    color: Color,
    rotation: Float,
    onClick: () -> Unit,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerActions),
    contentDescription: String? = null,
    glassFilm: Color? = null,
) {
    val settings = settings.inScope(GlassScope.PlayerActions)
    val backdrop = playerInformationControlBackdrop()
    CompositionLocalProvider(LocalGlassContainerBackdrop provides backdrop) {
    if (GlassFloatingActionIcon(painterResource(icon), onClick, size = GlassActionCellHeight,
            iconSize = 24.dp, iconModifier = Modifier.rotate(rotation), tint = color,
            settings = settings, contentDescription = contentDescription, filmColor = glassFilm)) return@CompositionLocalProvider
    Box(Modifier.size(GlassActionCellHeight)
        .glassAreaContour(settings, GlassArea.ActionButtons, uiRoundnessShape())
        .clip(uiRoundnessShape()).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Image(painterResource(icon), contentDescription, colorFilter = ColorFilter.tint(color),
            modifier = Modifier.rotate(rotation).size(24.dp))
    }
    }
}

/** A moved control keeps its behavior without participating in the lower toolbar's geometry. */
@Composable
internal fun PlayerPlacedTopAction(action: PlayerActionSlot, settings: GlassSettings) {
    val scopedSettings = settings.inScope(GlassScope.PlayerActions)
    val scope = rememberCoroutineScope()
    val motion = remember(action.id, scope) { PlayerReflowPlacement(scope) }
    val style = PlayerToolbarStyle(
        settings = scopedSettings,
        single = true,
        actions = listOf(action),
        bounds = emptyMap(),
        nativeBackground = Color.Transparent,
        contrastScrim = surfaceForegroundScrim(),
        placementMotions = mapOf(action.id to motion),
        isTopAction = true,
        onBounds = { _, _ -> },
    )
    CompositionLocalProvider(
        LocalPlayerToolbar provides style,
        LocalPlayerSlotIndex provides 0,
        app.n_zik.android.components.glass.LocalGlassScope provides GlassScope.PlayerActions,
    ) {
        Box(Modifier.size(GlassActionCellHeight), contentAlignment = Alignment.Center) { action.content() }
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
    layout?.configure(settings)
    SideEffect { layout?.reconcile(actions) }
    val motionScope = rememberCoroutineScope()
    val placementMotions = remember(motionScope) { mutableMapOf<String, PlayerReflowPlacement>() }
    val activeMotions = actions.associate { action ->
        action.id to placementMotions.getOrPut(action.id) { PlayerReflowPlacement(motionScope) }
    }
    SideEffect { placementMotions.keys.retainAll(activeMotions.keys) }
    if (actions.isEmpty()) return
    val glass = settings.usesGlassMaterial(GlassArea.ActionButtons)
    val toolbarBackdrop = if (glass && actions.size > 1 && actions.any { it.spansTwoCells }) {
        rememberLayerBackdrop()
    } else null
    val arrangement = if (spaceEvenly) Arrangement.SpaceEvenly else Arrangement.SpaceBetween
    val contrastBackdrop = LocalSurfaceBackdropColor.current
    val contrastScrim = surfaceForegroundScrim()
    val shape = uiRoundnessShape()
    val plate = if (glass) glassFilmColor(settings.forArea(GlassArea.ActionButtons), nativeBackground, menu = false) else nativeBackground
    val actionSurface = if (glass) effectiveSurfaceColor(contrastBackdrop ?: surfaceBackdropColor(), plate) else null
    val slot: @Composable (Int) -> Unit = { index ->
        val action = actions[index]
        key(action.id) {
            CompositionLocalProvider(
                LocalPlayerSlotIndex provides index,
                app.n_zik.android.components.glass.LocalGlassScope provides GlassScope.PlayerActions,
            ) {
                if (action.spansTwoCells) {
                    val slotStyle = LocalPlayerToolbar.current
                    val boundsModifier = if (actions.size == 1) {
                        Modifier.playerVisibleToolbarBounds(settings, nativeBackground, action.nativeHeight)
                    } else Modifier
                    Box(boundsModifier.onGloballyPositioned { coordinates ->
                        slotStyle?.onBounds?.invoke(index, coordinates.boundsInParent())
                    }) {
                        CompositionLocalProvider(LocalPlayerActionToolbarBackdrop provides toolbarBackdrop) {
                            action.content()
                        }
                    }
                } else action.content()
            }
        }
    }
    val rowModifier = Modifier.playerActionToolbarWidth(landscape)
        .then(if (contrastScrim.alpha > 0f && actions.size > 1) Modifier.background(contrastScrim, shape) else Modifier)
    val edgeStart = if (actions.size > 1) ((48.dp - actions.first().nativeHeight) / 2).coerceAtLeast(0.dp) else 0.dp
    val edgeEnd = if (actions.size > 1) ((48.dp - actions.last().nativeHeight) / 2).coerceAtLeast(0.dp) else 0.dp
    val requiredWidth = with(LocalDensity.current) {
        actions.sumOf { (if (it.spansTwoCells) 96.dp else it.nativeHeight).roundToPx() } +
            edgeStart.roundToPx() + edgeEnd.roundToPx()
    }
    val menuIndex = actions.indexOfFirst { it.id == "menu" }
    val maxNativeHeight = actions.maxOf { it.nativeHeight }
    val toolbarHeight = if (settings.usesGlassMaterial(GlassArea.ActionButtons) ||
        settings.usesGlassContour(GlassArea.ActionButtons)) PlayerActionToolbarHeight else maxNativeHeight
    Box(modifier.fillMaxWidth().windowInsetsPadding(horizontalInsets), contentAlignment = Alignment.Center) {
        BoxWithConstraints(rowModifier.height(toolbarHeight)) {
            val overflow = constraints.hasBoundedWidth && requiredWidth > constraints.maxWidth
            val bounds = remember(actions.map { it.id }, overflow) { mutableStateMapOf<Int, Rect>() }
            val style = PlayerToolbarStyle(settings, actions.size == 1, actions, bounds, nativeBackground,
                contrastScrim, activeMotions, horizontalOverflow = overflow) { index, rect ->
                if (!overflow) bounds[index] = rect
            }
            val row: @Composable () -> Unit = {
                CompositionLocalProvider(LocalPlayerActionSurface provides actionSurface) {
                    SurfaceForegroundContent(contrastBackdrop?.let { effectiveSurfaceColor(it, plate) }) {
                        if (overflow) {
                            Row(Modifier.fillMaxWidth().height(toolbarHeight).testTag("player.actionToolbar.row"),
                                verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    Modifier.weight(1f).horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    actions.indices.forEach { index -> if (index != menuIndex) slot(index) }
                                }
                                if (menuIndex >= 0) {
                                    Box(Modifier.width(48.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                        slot(menuIndex)
                                    }
                                }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth().height(toolbarHeight).padding(start = edgeStart, end = edgeEnd)
                                .testTag("player.actionToolbar.row"), horizontalArrangement = arrangement,
                                verticalAlignment = Alignment.CenterVertically) {
                                actions.indices.forEach { slot(it) }
                            }
                        }
                    }
                }
            }
            CompositionLocalProvider(LocalPlayerToolbar provides style) {
                if (!glass) {
                    if (actions.size > 1) Box(Modifier.matchParentSize()
                        .playerVisibleToolbarBounds(settings, nativeBackground, maxNativeHeight)
                        .testTag("player.actionToolbar.native")
                        .background(nativeBackground, shape)
                        .glassAreaContour(settings, GlassArea.ActionButtons, shape))
                    row()
                } else if (actions.size == 1) {
                    row()
                } else {
                    val separatorIndex = if (menuIndex >= 0) menuIndex
                        else actions.lastIndex.takeIf { actions.last().id == "arrow" } ?: 0
                    val previous = bounds[separatorIndex - 1]
                    val trailing = bounds[separatorIndex]
                    val boundary = if (!overflow && previous != null && trailing != null) {
                        with(LocalDensity.current) {
                            val (left, right) = if (previous.center.x <= trailing.center.x) previous to trailing
                                else trailing to previous
                            ((left.right + right.left) / 2f).toDp()
                        } + edgeStart
                    } else 0.dp
                    GlassActionGroupMotionHost(isGlassMotionAllowed(), Modifier.fillMaxWidth().height(toolbarHeight)) {
                        Box(Modifier.matchParentSize().requiredHeight(toolbarHeight)
                            .playerVisibleToolbarBounds(settings, nativeBackground, maxNativeHeight)
                            .testTag("player.actionToolbar.material")
                            .glassMaterial(settings.forArea(GlassArea.ActionButtons), shape,
                                ownBackdrop = playerInformationControlBackdrop(), filmColor = nativeBackground,
                                exportedBackdrop = toolbarBackdrop)
                            .glassActionGroupSeparators(settings, GlassArea.ActionButtons, boundary,
                                separatorBefore = if (boundary > 0.dp) setOf(1) else emptySet(),
                                fromPhysicalLeft = true))
                        GlassActionGroup(settings, GlassArea.ActionButtons, modifier = Modifier.fillMaxWidth(),
                            separatorBefore = emptySet(), drawSurface = false) { row() }
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
    contentDescription: String? = null,
) {
    val style = LocalPlayerToolbar.current
    if (style == null) {
        IconButton(
            onClick, icon, color, modifier, enabled,
            onLongClick = onLongClick,
            contentDescription = contentDescription,
        )
        return
    }
    PlayerActionCell(enabled, onClick, onLongClick) {
        Image(painterResource(icon), contentDescription, colorFilter = ColorFilter.tint(if (enabled)
            surfaceGlyphColor(color, LocalPlayerActionSurface.current) else color), modifier = modifier)
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
    val index = LocalPlayerSlotIndex.current
    val glass = style.settings.usesGlassMaterial(GlassArea.ActionButtons)
    val interaction = remember { MutableInteractionSource() }
    val shape = uiRoundnessShape()
    val nativeSize = style.actions[index].nativeHeight
    val placementMotion = requireNotNull(style.placementMotions[style.actions[index].id])
    val density = LocalDensity.current
    val own = style.bounds[index].takeUnless { style.horizontalOverflow }
    val previous = style.bounds[index - 1]
    val next = style.bounds[index + 1]
    val requested = when {
        style.isTopAction -> GlassActionCellHeight
        style.surfaceEnabled && style.single -> PlayerActionToolbarHeight
        else -> 48.dp
    }
    val width = with(density) {
        fun availableWidth(neighbor: Rect?, neighborIndex: Int): Float {
            if (own == null || neighbor == null) return Float.POSITIVE_INFINITY
            val distance = kotlin.math.abs(own.center.x - neighbor.center.x)
            return if (style.actions.getOrNull(neighborIndex)?.spansTwoCells == true) {
                (distance - neighbor.width / 2) * 2
            } else distance
        }
        minOf(requested.toPx(), availableWidth(previous, index - 1), availableWidth(next, index + 1))
            .toDp().coerceAtLeast(nativeSize)
    }
    val cell: @Composable () -> Unit = {
        // The row still measures its original glyph slots. Only the interaction
        // canvas fills the free space around them, keeping every glyph center fixed.
        Box(Modifier.testTag("player.actionSlot.$index").onGloballyPositioned {
            style.onBounds(index, it.boundsInParent())
        }.then(if (style.single || style.horizontalOverflow) Modifier
            else Modifier.playerReflowPlacement(placementMotion, style.settings))) {
        Box(Modifier.layout { measurable, constraints ->
            val slot = if (style.single) style.height.roundToPx() else nativeSize.roundToPx()
            val canvas = measurable.measure(Constraints.fixed(width.roundToPx(), requested.roundToPx()))
            val slotWidth = slot.coerceIn(constraints.minWidth, constraints.maxWidth)
            val slotHeight = slot.coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(slotWidth, slotHeight) {
                // Center alignment rounds a half pixel toward the positive side. Its
                // inverse must round down, including for an odd, larger touch canvas.
                canvas.place(Math.floorDiv(slotWidth - canvas.width, 2), Math.floorDiv(slotHeight - canvas.height, 2))
            }
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
        val placement = if (style.horizontalOverflow) Modifier else Modifier.playerReflowPlacement(placementMotion, style.settings)
        GlassActionGroupMotionHost(glass && isGlassMotionAllowed(), placement.layout { measurable, constraints ->
            val surface = style.height.roundToPx()
            val nativeWidth = nativeSize.roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
            val child = measurable.measure(Constraints.fixed(surface, surface))
            // SpaceBetween anchors the native glyph slot, including a lone action.
            // Its larger optical canvas grows around that original center.
            layout(nativeWidth, surface) { child.place(Math.floorDiv(nativeWidth - surface, 2), 0) }
        }) {
            // The single action shares the toolbar height and its interaction treatment.
            Box(Modifier.matchParentSize().requiredSize(style.height)
                .then(if (style.isTopAction) Modifier else Modifier.playerVisibleToolbarBounds(
                    style.settings, style.nativeBackground, nativeSize))
                .testTag(if (glass) "player.actionToolbar.material" else "player.actionToolbar.native")
                .background(style.contrastScrim, shape)
                .then(if (glass) Modifier.glassMaterial(style.settings.forArea(GlassArea.ActionButtons), uiRoundnessShape(),
                    ownBackdrop = playerInformationControlBackdrop(), filmColor = style.nativeBackground) else Modifier.background(style.nativeBackground, uiRoundnessShape())
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
        if (!active) Image(painterResource(icon), null, colorFilter = ColorFilter.tint(if (enabled)
            surfaceGlyphColor(color, LocalPlayerActionSurface.current) else color), modifier = modifier)
        else Box(modifier, contentAlignment = Alignment.Center) {
            val arc = surfaceGlyphColor(colorPalette().accent, LocalPlayerActionSurface.current)
            val track = surfaceProgressTrackColor(arc, colorPalette().textDisabled, LocalPlayerActionSurface.current)
            val stroke = Stroke(with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() })
            val progressModifier = Modifier.size(DOWNLOAD_INDICATOR_SIZE_NORMAL.dp)
            if (progress > .01f && downloadState == Download.STATE_DOWNLOADING)
                CircularWavyProgressIndicator(progress = { progress }, modifier = progressModifier,
                    color = arc, trackColor = track,
                    stroke = stroke, trackStroke = stroke)
            else CircularWavyProgressIndicator(modifier = progressModifier,
                color = arc, trackColor = track,
                stroke = stroke, trackStroke = stroke)
        }
    }
}
