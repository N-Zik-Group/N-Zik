package app.n_zik.android.components.glass

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import app.n_zik.android.colorPalette
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
internal val LocalEmbeddedQueueMini = staticCompositionLocalOf { false }
private val IgnoreEmbeddedQueueRequest: () -> Unit = {}

/** Shares only the list recording between sibling surfaces, never a recording of the panel. */
@Composable
fun GlassQueuePanel(
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
    content: @Composable BoxScope.() -> Unit,
) {
    val list = rememberLayerBackdrop()
    // Full-player owners provide only their cover/background here. Never use the whole scene:
    // it can already contain this panel and its sampling surfaces.
    val underlay = LocalGlassBackdrop.current
    val visible = if (underlay != null) rememberCombinedBackdrop(underlay, list) else list
    CompositionLocalProvider(
        LocalQueueListBackdrop provides if (settings.enabled) list else null,
        LocalQueueGlassBackdrop provides if (settings.enabled) visible else null,
        LocalQueueRowUnderlay provides underlay,
    ) {
        Box(modifier, content = content)
    }
}

/** Reconstructs only the actual native queue fill over its isolated underlay, excluding all rows. */
@Composable
internal fun rememberQueueRowGlassBackdrop(nativeFill: Color): Backdrop {
    val fill = rememberCanvasBackdrop { drawRect(nativeFill) }
    val underlay = LocalQueueRowUnderlay.current
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
    content: @Composable BoxScope.() -> Unit,
) {
    val list = LocalQueueGlassBackdrop.current
    val shape = topUiRoundnessShape()
    val nativeColor = colorPalette().background1
    val material = settings.usesGlassMaterial(GlassArea.ActionButtons) && list != null
    Box(modifier) {
        val toolbarScope = this
        GlassMaterialContainerScope(
            modifier = Modifier.matchParentSize(),
            sectionFilm = if (material) null else nativeColor,
            material = if (material) ({ exported ->
                Modifier.glassMaterial(settings.forArea(GlassArea.ActionButtons), shape,
                    ownBackdrop = list, exportedBackdrop = exported, filmColor = nativeColor)
            }) else null,
        ) { surface ->
            val native = if (material) surface else surface.background(nativeColor, shape)
                .glassAreaContour(settings, GlassArea.ActionButtons, shape)
            // The mini is offset above the toolbar. Clip only the material, never its content.
            Box(native)
            content(toolbarScope)
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
    ) {
        Box(Modifier.trackToastObstruction()) { content() }
    }
}

/** One inexpensive edge around the user's native queue fill; its original alpha is unchanged. */
@Composable
fun Modifier.glassQueuePanelBackground(
    originalColor: Color,
    shape: Shape,
    settings: GlassSettings = rememberGlassSettings(),
): Modifier {
    val filled = background(originalColor)
    return if (settings.enabled && settings.rim > 0f) filled.glassEdgeRim(settings, shape, glassTint(settings)) else filled
}

/** Always keep the same content group when toggling Glass so queue state is retained. */
@Composable
fun NativeQueueRows(
    settings: GlassSettings = rememberGlassSettings(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalNativeQueueRows provides settings.enabled,
        LocalGlassRippleActive provides if (settings.enabled) false else LocalGlassRippleActive.current,
        LocalIndication provides if (settings.enabled) ripple() else LocalIndication.current,
        content = content,
    )
}
