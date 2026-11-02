package app.n_zik.android.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Icon
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCombinedBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.core.navigation.LocalMiniPlayerQueueRequest
import app.n_zik.android.topUiRoundnessShape
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.toast.trackToastObstruction

/** Only row decorations consult this scope; menus continue using their normal Glass policy. */
internal val LocalNativeQueueRows = staticCompositionLocalOf { false }
internal val LocalQueueListBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
internal val LocalQueueGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
internal val LocalQueueRowBackdrop = staticCompositionLocalOf<Backdrop?> { null }
private val LocalQueueRowUnderlay = staticCompositionLocalOf<Backdrop?> { null }
private val LocalQueuePanelHosted = staticCompositionLocalOf { false }
internal val LocalEmbeddedQueueMini = staticCompositionLocalOf { false }
private val IgnoreEmbeddedQueueRequest: () -> Unit = {}

/** Shares only the list recording between sibling surfaces, never a recording of the panel. */
@Composable
fun GlassQueuePanel(
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
    content: @Composable BoxScope.() -> Unit,
) {
    val settings = settings.inScope(GlassScope.Queue)
    val list = rememberLayerBackdrop()
    val capture = glassContentCaptureEnabled(settings)
    // Full-player owners provide only their cover/background here. Never use the whole scene:
    // it can already contain this panel and its sampling surfaces.
    val underlay = LocalGlassBackdrop.current
    val visible = if (underlay != null) rememberCombinedBackdrop(underlay, list) else list
    CompositionLocalProvider(
        LocalGlassScope provides GlassScope.Queue,
        LocalQueueListBackdrop provides if (capture) list else null,
        LocalQueueGlassBackdrop provides if (capture) visible else null,
        LocalQueueRowUnderlay provides underlay,
        LocalQueuePanelHosted provides true,
    ) {
        Box(modifier, content = content)
    }
}

/** Reconstructs the queue's isolated underlay, excluding all rows and repeated Glass film. */
@Composable
internal fun rememberQueueRowGlassBackdrop(nativeFill: Color, settings: GlassSettings = rememberGlassSettings()): Backdrop {
    val color = queueContentBackground(nativeFill, settings)
    val fill = rememberCanvasBackdrop { drawRect(color) }
    val underlay = LocalQueueRowUnderlay.current ?: LocalGlassContainerBackdrop.current
    return if (underlay != null) rememberCombinedBackdrop(underlay, fill) else fill
}

/** The recording excludes the sibling mini player, toolbar and search overlay. */
@Composable
@Suppress("UNUSED_PARAMETER") // Keep source compatibility for queue owners passing their list state.
fun Modifier.glassQueueListBackdrop(state: LazyListState): Modifier {
    val source = LocalQueueListBackdrop.current ?: return this
    return layerBackdrop(source)
}

/**
 * Keeps the native fill and contour; the host reserves system insets.
 * @param systemBottomInset Ignored; retained for source compatibility.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun GlassQueueToolbar(
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
    systemBottomInset: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val settings = settings.inScope(GlassScope.Queue)
    val shape = topUiRoundnessShape()
    val nativeColor = colorPalette().background1
    Box(modifier) {
        GlassMaterialContainerScope(
            modifier = Modifier.matchParentSize(),
            sectionFilm = nativeColor,
        ) { surface ->
            val native = surface.background(nativeColor, shape)
                .glassAreaContour(settings, GlassArea.Menus, shape)
            Box(native)
            Box(Modifier.fillMaxSize()) { content(this) }
        }
    }
}

/** Keeps inset ownership in the native queue hosts, independent of Glass settings. */
@Composable
fun usesQueueToolbarGlass(settings: GlassSettings = rememberGlassSettings()): Boolean =
    queueToolbarUsesGlassMaterial(settings)

/** Native queue owners already account for the system bottom inset. */
@Composable
fun queueToolbarBottomInset(): Dp = 0.dp

@Suppress("UNUSED_PARAMETER") // Keep existing callers while queue backgrounds remain contour-only.
internal fun queueToolbarUsesGlassMaterial(settings: GlassSettings): Boolean = false

/** Uses the Home toolbar verbatim; the native total stays outside and the arrow has its own bubble. */
@Composable
fun RowScope.GlassQueueToolbarActions(
    buttons: List<Button>,
    arrow: Icon?,
    settings: GlassSettings = rememberGlassSettings(),
) {
    val settings = settings.inScope(GlassScope.Queue)
    CompositionLocalProvider(LocalGlassBackdrop provides LocalGlassContainerBackdrop.current) {
        GlassToolbarButtons(
            buttons = buttons,
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).testTag("queue.toolbar.actions"),
            settings = settings,
            itemShape = uiRoundnessShape(),
            disableAnimation = settings.usesGlassMaterial(GlassArea.ActionButtons),
        )
        if (arrow != null && arrow.isEnabled) {
            GlassToolbarRow(actionCount = 1, settings = settings, itemShape = uiRoundnessShape(),
                modifier = Modifier.testTag("queue.toolbar.arrow")) { arrow.ToolBarButton() }
        }
    }
}

/** The embedded mini samples the actual list, independently of the full player's opening progress. */
@Composable
fun QueueEmbeddedMini(content: @Composable () -> Unit) {
    val list = LocalQueueGlassBackdrop.current
    CompositionLocalProvider(
        LocalEmbeddedQueueMini provides true,
        LocalMiniPlayerQueueRequest provides IgnoreEmbeddedQueueRequest,
        LocalNativeQueueRows provides false,
        LocalGlassBackdrop provides (list ?: LocalGlassBackdrop.current),
        LocalGlassContainerBackdrop provides (list ?: LocalGlassContainerBackdrop.current),
        LocalGlassMaterialSource provides (list ?: LocalGlassMaterialSource.current),
    ) {
        Box(Modifier.trackToastObstruction()) { content() }
    }
}

/** Preserves the theme's exact opacity; Glass decorates only the panel contour. */
@Composable
fun Modifier.glassQueuePanelBackground(
    originalColor: Color,
    shape: Shape,
    settings: GlassSettings = rememberGlassSettings(),
): Modifier {
    val settings = settings.inScope(GlassScope.Queue)
    return background(originalColor).glassAreaContour(settings, GlassArea.Menus, shape)
}

/** The list keeps its native fill and leaves the contour to its panel when hosted. */
@Composable
fun Modifier.glassQueueListBackground(originalColor: Color, settings: GlassSettings = rememberGlassSettings()): Modifier {
    return if (LocalQueuePanelHosted.current) background(originalColor)
        else glassQueuePanelBackground(originalColor, RoundedCornerShape(0.dp), settings)
}

/** Glass contours never replace a queue row's native fill. */
@Suppress("UNUSED_PARAMETER") // Legacy queue owners still pass their scoped settings.
internal fun queueContentBackground(originalColor: Color, settings: GlassSettings): Color = originalColor

/** Always keep the same content group when toggling Glass so queue state is retained. */
@Composable
fun NativeQueueRows(
    settings: GlassSettings = rememberGlassSettings(),
    content: @Composable () -> Unit,
) {
    val settings = settings.inScope(GlassScope.Queue)
    CompositionLocalProvider(
        LocalNativeQueueRows provides settings.enabled,
        content = content,
    )
}
