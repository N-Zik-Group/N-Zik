package app.n_zik.android.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.topUiRoundnessShape
import app.n_zik.android.utils.toast.trackToastObstruction
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCombinedBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.core.navigation.LocalMiniPlayerQueueRequest

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

/** Owns the fixed toolbar material while preserving the native Box and fill when disabled. */
@Composable
fun GlassQueueToolbar(
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
    systemBottomInset: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    content: @Composable BoxScope.() -> Unit,
) {
    val settings = settings.inScope(GlassScope.Queue)
    val list = LocalQueueGlassBackdrop.current
    val shape = topUiRoundnessShape()
    val nativeColor = colorPalette().background1
    val material = queueToolbarUsesGlassMaterial(settings)
    val fallback = rememberCanvasBackdrop { drawRect(nativeColor) }
    val bottomInset = if (material) systemBottomInset else 0.dp
    Box(modifier) {
        GlassMaterialContainerScope(
            modifier = Modifier.matchParentSize(),
            sectionFilm = if (material) null else nativeColor,
            materialSource = if (material) list ?: fallback else null,
            material = if (material) ({ exported ->
                Modifier.glassMaterial(settings.forArea(GlassArea.Menus).copy(rim = 0f), shape,
                    ownBackdrop = list ?: fallback, exportedBackdrop = exported,
                    applyEdgeEffects = false, highlightAlpha = 0f, drawShadow = false)
            }) else null,
        ) { surface ->
            // The fixed optical pane deliberately omits lens edges; draw its one
            // contour through the same Menus policy as the native-fill branch.
            val native = (if (material) surface else surface.background(nativeColor, shape))
                .glassAreaContour(settings, GlassArea.Menus, shape)
            // The mini is offset above the toolbar. Clip only the material, never its content.
            Box(native)
            Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) { content(this) }
        }
    }
}

/** The panel hosts use the same gate when handing the system bottom inset to this toolbar. */
@Composable
fun usesQueueToolbarGlass(settings: GlassSettings = rememberGlassSettings()): Boolean =
    queueToolbarUsesGlassMaterial(settings)

/** Content keeps its original safe position while the backing reaches the screen edge. */
@Composable
fun queueToolbarBottomInset(): Dp = if (usesQueueToolbarGlass())
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp

internal fun queueToolbarUsesGlassMaterial(settings: GlassSettings): Boolean =
    settings.inScope(GlassScope.Queue).usesGlassMaterial(GlassArea.Menus)

/** Uses the Home toolbar verbatim; the native total stays outside and the arrow has its own bubble. */
@Composable
fun RowScope.GlassQueueToolbarActions(
    buttons: List<app.it.fast4x.rimusic.ui.components.tab.toolbar.Button>,
    arrow: app.it.fast4x.rimusic.ui.components.tab.toolbar.Icon?,
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

/** The shared material replaces the native queue fill only in material modes. */
@Composable
fun Modifier.glassQueuePanelBackground(
    originalColor: Color,
    shape: Shape,
    settings: GlassSettings = rememberGlassSettings(),
): Modifier {
    val settings = settings.inScope(GlassScope.Queue)
    if (settings.usesGlassMaterial(GlassArea.Menus)) {
        val underlay = LocalGlassBackdrop.current
        return glassMaterial(settings.forArea(GlassArea.Menus).copy(rim = 0f), shape, menu = true,
            ownBackdrop = underlay, separateWindow = underlay == null,
            applyEdgeEffects = false, drawShadow = false)
            .glassAreaContour(settings, GlassArea.Menus, shape)
    }
    val filled = background(originalColor)
    // Queue sheets meet the physical bottom edge, just like the other Menus panes.
    return filled.glassAreaContour(settings, GlassArea.Menus, shape)
}

/** The panel owns one film; a standalone queue owns its film on the list instead. */
@Composable
fun Modifier.glassQueueListBackground(originalColor: Color, settings: GlassSettings = rememberGlassSettings()): Modifier {
    if (!queueToolbarUsesGlassMaterial(settings)) return background(originalColor)
    return if (LocalQueuePanelHosted.current) this
        else glassQueuePanelBackground(originalColor, RoundedCornerShape(0.dp), settings)
}

/** Native queue-type colours remain exact when the queue material is disabled. */
internal fun queueContentBackground(originalColor: Color, settings: GlassSettings): Color =
    if (queueToolbarUsesGlassMaterial(settings)) Color.Transparent else originalColor

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
