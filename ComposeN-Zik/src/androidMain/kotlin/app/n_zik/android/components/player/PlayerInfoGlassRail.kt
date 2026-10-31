package app.n_zik.android.components.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassGroupPressMotion
import app.n_zik.android.components.glass.InteractiveHighlight
import app.n_zik.android.components.glass.LocalGlassGroupPressMotion
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.glassButtonFeedback
import app.n_zik.android.components.glass.glassButtonMotion
import app.n_zik.android.components.glass.glassGroupPressMotion
import app.n_zik.android.components.glass.isGlassMotionAllowed
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.inScope
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.retainGlassButtonGesture
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.uiRoundnessShape
import kotlin.math.roundToInt

private class PlayerInfoRailMotion(
    val controller: GlassGroupPressMotion,
    val scope: PlayerInfoGlassRailScope,
    val bounds: Rect,
)

private val LocalPlayerInfoRailMotion = staticCompositionLocalOf<PlayerInfoRailMotion?> { null }

/** Apply one host-space transform only to the connected pane and its glyphs, never the row text. */
@Composable
private fun Modifier.playerInfoRailMotion(motion: PlayerInfoRailMotion?): Modifier {
    if (motion == null) return this
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return onGloballyPositioned { coordinates = it }.drawWithContent {
        val node = coordinates?.takeIf { it.isAttached }
        val nodeBounds = node?.let(motion.scope::boundsOf)
        if (nodeBounds == null) {
            drawContent()
        } else {
            val frame = glassButtonMotion(motion.bounds.size, 4.dp.toPx(), motion.controller.highlight)
            val pivot = motion.bounds.center - nodeBounds.topLeft
            withTransform({
                translate(frame.x, frame.y)
                scale(frame.scaleX, frame.scaleY, pivot)
            }) { this@drawWithContent.drawContent() }
        }
    }
}

/** Keeps the original row slot and glyph while giving either feedback style a full button canvas. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlayerInfoIconButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerInformation),
) {
    val settings = settings.inScope(GlassScope.PlayerInformation)
    val interaction = remember { MutableInteractionSource() }
    val shape = uiRoundnessShape()
    val groupMotion = LocalGlassGroupPressMotion.current
    val railMotion = LocalPlayerInfoRailMotion.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val glyphWidth = maxWidth
        val glyphHeight = maxHeight
        Box(Modifier.matchParentSize().layout { measurable, constraints ->
            // The minimum row-center distance is 35dp (26dp + 10dp gap + 24dp).
            val canvasSize = 34.dp.roundToPx()
            val canvas = measurable.measure(Constraints.fixed(canvasSize, canvasSize))
            layout(constraints.maxWidth, constraints.maxHeight) {
                canvas.place((constraints.maxWidth - canvasSize) / 2, (constraints.maxHeight - canvasSize) / 2)
            }
        }.testTag("player.infoIcon.canvas.$icon")
            .retainGlassButtonGesture(enabled && settings.enabled)
            .glassGroupPressMotion(interaction, enabled && settings.enabled)
            .playerInfoRailMotion(railMotion)
            .glassButtonFeedback(interaction, shape, enabled && settings.enabled,
                retainPressUntilUp = true, animateMotion = groupMotion == null)
            .clip(shape)
            .combinedClickable(interactionSource = interaction,
                indication = if (settings.enabled) null else ripple(bounded = true),
                enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
            Image(painterResource(icon), null, colorFilter = ColorFilter.tint(color),
                modifier = Modifier.requiredSize(glyphWidth, glyphHeight)
                    .testTag("player.infoIcon.glyph.$icon").graphicsLayer {
                        scaleX = if (settings.enabled) .85f else 1f
                        scaleY = if (settings.enabled) .85f else 1f
                    })
        }
    }
}

/** Measures the native icon slots without owning their gestures or layout. */
@Stable
class PlayerInfoGlassRailScope internal constructor() {
    internal var album by mutableStateOf<Rect?>(null)
    internal var artist by mutableStateOf<Rect?>(null)

    private var hostCoordinates: LayoutCoordinates? = null
    private var albumCoordinates: LayoutCoordinates? = null
    private var artistCoordinates: LayoutCoordinates? = null

    internal fun boundsOf(coordinates: LayoutCoordinates): Rect? =
        hostCoordinates?.takeIf { it.isAttached }?.localBoundingBoxOf(coordinates, clipBounds = false)

    internal fun updateHost(coordinates: LayoutCoordinates) {
        hostCoordinates = coordinates
        updateBounds()
    }

    private fun updateBounds() {
        val host = hostCoordinates?.takeIf { it.isAttached } ?: return
        album = albumCoordinates?.takeIf { it.isAttached }?.let {
            host.localBoundingBoxOf(it, clipBounds = false)
        }
        artist = artistCoordinates?.takeIf { it.isAttached }?.let {
            host.localBoundingBoxOf(it, clipBounds = false)
        }
    }

    /** Attach to the existing album icon's modifier. */
    fun Modifier.albumIconBounds(): Modifier = onGloballyPositioned { albumCoordinates = it; updateBounds() }

    /** Attach to the existing artist icon's modifier. */
    fun Modifier.artistIconBounds(): Modifier = onGloballyPositioned { artistCoordinates = it; updateBounds() }
}

/** Adds one passive capsule behind the original information rows and their spacer. */
@Composable
fun PlayerInfoGlassRail(
    iconsVisible: Boolean,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerInformation),
    content: @Composable PlayerInfoGlassRailScope.() -> Unit,
) {
    val settings = settings.inScope(GlassScope.PlayerInformation)
    val scope = remember(iconsVisible) { PlayerInfoGlassRailScope() }
    val album = scope.album
    val artist = scope.artist
    val padding = with(LocalDensity.current) { 6.dp.toPx() }
    val material = settings.usesGlassMaterial(GlassArea.ActionButtons)
    val contour = settings.usesGlassContour(GlassArea.ActionButtons)
    val shape = uiRoundnessShape()
    val backdrop = LocalGlassContainerBackdrop.current
    val coroutineScope = rememberCoroutineScope()
    val controller = remember(coroutineScope) { GlassGroupPressMotion(InteractiveHighlight(coroutineScope)) }
    val bounds = if (iconsVisible && (material || contour) && album != null && artist != null) Rect(
        minOf(album.left, artist.left) - padding,
        minOf(album.top, artist.top) - padding,
        maxOf(album.right, artist.right) + padding,
        maxOf(album.bottom, artist.bottom) + padding,
    ) else null
    val motion = if (material && isGlassMotionAllowed() && bounds != null)
        PlayerInfoRailMotion(controller, scope, bounds) else null
    Box(Modifier.onGloballyPositioned(scope::updateHost)) {
        if (bounds != null) {
            // matchParentSize leaves the rows in charge of layout and avoids a zero-sized optical host.
            Box(Modifier.matchParentSize().layout { measurable, constraints ->
                val surface = measurable.measure(Constraints.fixed(bounds.width.roundToInt(), bounds.height.roundToInt()))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    surface.place(bounds.left.roundToInt(), bounds.top.roundToInt())
                }
            }.playerInformationGlassVisualBounds().testTag("player.infoRail.surface")
                .playerInfoRailMotion(motion)
                .then(if (material) Modifier.glassMaterial(settings.forArea(GlassArea.ActionButtons), shape,
                    ownBackdrop = backdrop, separateWindow = backdrop == null)
                else Modifier.glassAreaContour(settings, GlassArea.ActionButtons, shape)))
        }
        CompositionLocalProvider(LocalGlassGroupPressMotion provides motion?.controller,
            LocalPlayerInfoRailMotion provides motion) {
            Column { scope.content() }
        }
    }
}
