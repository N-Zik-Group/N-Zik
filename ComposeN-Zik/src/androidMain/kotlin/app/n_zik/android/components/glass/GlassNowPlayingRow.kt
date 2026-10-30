package app.n_zik.android.components.glass

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import timber.log.Timber

/** Active song highlight; the row's artwork, text and actions stay outside the material. */
@Composable
internal fun Modifier.glassNowPlayingRow(
    backgroundColor: Color,
    highlightColor: Color,
    shape: Shape,
    settings: GlassSettings = rememberGlassSettings(),
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
    // Reproduce only the row's actual flat background, never its foreground or its parent layer.
    val drawBackground: DrawScope.() -> Unit = remember(backgroundColor) {
        { drawRect(backgroundColor) }
    }
    val backdrop = rememberCanvasBackdrop(drawBackground)
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
        ownBackdrop = queueBackdrop ?: backdrop,
        onDrawTint = drawHighlight,
    )
}
