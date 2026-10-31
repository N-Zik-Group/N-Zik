package app.n_zik.android.components.glass

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCombinedBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import timber.log.Timber

/** Active song highlight; the row's artwork, text and actions stay outside the material. */
@Composable
internal fun Modifier.glassNowPlayingRow(
    backgroundColor: Color,
    highlightColor: Color,
    shape: Shape,
    settings: GlassSettings = rememberGlassSettings(),
    exportedBackdrop: LayerBackdrop? = null,
): Modifier {
    val base = background(backgroundColor)
    val queueBackdrop = LocalQueueRowBackdrop.current
    if (LocalNativeQueueRows.current && queueBackdrop == null) return base.background(highlightColor)
    if (!settings.usesGlassMaterial(GlassArea.NowPlaying)) return base.background(highlightColor)
        .glassAreaContour(settings, GlassArea.NowPlaying, shape)

    val artworkColor = LocalGlassArtworkColor.current
    LaunchedEffect(settings.source, backgroundColor, highlightColor, artworkColor) {
        Timber.tag("GlassTintFlow").d(
            "NowPlaying inputs: source=%s, background=%08x, highlight=%08x, artwork=%s",
            settings.source, backgroundColor.toArgb(), highlightColor.toArgb(),
            artworkColor?.toArgb()?.toUInt()?.toString(16),
        )
    }

    // The page capture contains this row: sampling it here would create a render-node cycle.
    // Reproduce the flat fill over a child-free container export when it is translucent.
    // LocalGlassBackdrop may contain this row, so it must never be used as that underlay.
    val drawBackground: DrawScope.() -> Unit = remember(backgroundColor) {
        { drawRect(backgroundColor) }
    }
    val backdrop = rememberCanvasBackdrop(drawBackground)
    val parent = LocalGlassContainerBackdrop.current.takeIf { backgroundColor.alpha < 1f }
    val rowBackdrop = if (parent != null) rememberCombinedBackdrop(parent, backdrop) else backdrop
    // An explicit Glass source owns the film hue; a second theme overlay can lag behind artwork.
    // Read the changing palette in the draw pass. The row can live inside recorded
    // page layers, so replacing a captured colour alone must not be its redraw signal.
    val latestHighlight = rememberUpdatedState(highlightColor)
    val drawHighlight: (DrawScope.() -> Unit)? = remember(settings.source) {
        if (settings.source == GlassTintSource.None) ({ drawRect(latestHighlight.value) }) else null
    }
    return base.glassMaterial(
        settings = settings.forArea(GlassArea.NowPlaying),
        shape = shape,
        ownBackdrop = queueBackdrop ?: rowBackdrop,
        onDrawTint = drawHighlight,
        exportedBackdrop = exportedBackdrop,
    )
}

/** Exposes only the immediate row surface; badges never sample a page containing their own glyph. */
@Composable
internal fun GlassSongRowSurface(
    backgroundColor: Color,
    highlightColor: Color?,
    shape: Shape,
    settings: GlassSettings = rememberGlassSettings(),
    useNowPlayingMaterial: Boolean = true,
    paintBackground: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    val exported = rememberLayerBackdrop()
    val parent = LocalGlassContainerBackdrop.current
    val material = useNowPlayingMaterial && highlightColor != null &&
        settings.usesGlassMaterial(GlassArea.NowPlaying) &&
        (!LocalNativeQueueRows.current || LocalQueueRowBackdrop.current != null)
    val nativeSurface = rememberCanvasBackdrop {
        // These are the exact same flat fills painted by the native row below.
        if (paintBackground) drawRect(backgroundColor)
        if (highlightColor != null) drawRect(highlightColor)
    }
    val nativeUnderlay = if (paintBackground && backgroundColor.alpha >= 1f) null
        else LocalQueueRowBackdrop.current ?: parent
            ?: if (!paintBackground) rememberCanvasBackdrop { drawRect(backgroundColor) } else null
    val backdrop = if (material) exported else if (nativeUnderlay != null)
        rememberCombinedBackdrop(nativeUnderlay, nativeSurface) else nativeSurface
    val surface = if (useNowPlayingMaterial && highlightColor != null) {
        Modifier.glassNowPlayingRow(backgroundColor, highlightColor, shape, settings, exportedBackdrop = exported)
    } else {
        (if (paintBackground) Modifier.background(backgroundColor) else Modifier)
            .then(if (highlightColor != null) Modifier.background(highlightColor) else Modifier)
    }
    CompositionLocalProvider(LocalGlassContainerBackdrop provides backdrop) { content(surface) }
}
