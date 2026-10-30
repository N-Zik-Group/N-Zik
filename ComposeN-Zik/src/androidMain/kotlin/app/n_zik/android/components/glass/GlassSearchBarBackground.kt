package app.n_zik.android.components.glass

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop

/** One themed search surface; its foreground and focus choreography remain native. */
@Composable
internal fun Modifier.glassSearchBarBackground(
    backgroundColor: Color,
    containerColor: Color,
    shape: Shape,
    shadowColor: Color,
    shadowElevation: Dp = 0.dp,
    settings: GlassSettings,
): Modifier {
    if (!settings.usesGlassMaterial(GlassArea.SearchBars)) {
        return then(if (shadowElevation > 0.dp) Modifier.shadow(
            elevation = shadowElevation, shape = shape, spotColor = shadowColor,
        ) else Modifier).background(containerColor, shape)
            .glassAreaContour(settings, GlassArea.SearchBars, shape)
    }
    // Search is inside the captured page: never read that capture recursively.
    val drawBackground: DrawScope.() -> Unit = remember(backgroundColor) {
        { drawRect(backgroundColor) }
    }
    val backdrop = rememberCanvasBackdrop(drawBackground)
    return glassMaterial(
        settings = settings.forArea(GlassArea.SearchBars),
        shape = shape,
        ownBackdrop = backdrop,
        filmColor = containerColor,
        drawShadow = false,
    )
}
