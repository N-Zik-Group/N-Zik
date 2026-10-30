package app.n_zik.android.components.glass

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop

/** Inline mood tiles must never sample the page capture containing themselves. */
@Composable
internal fun Modifier.glassMoodCardBackground(
    backgroundColor: Color,
    cardColor: Color,
    shape: Shape,
    settings: GlassSettings = rememberGlassSettings(),
): Modifier {
    if (!settings.usesGlassMaterial(GlassArea.MoodCards)) {
        return background(cardColor, shape)
            .glassAreaContour(settings, GlassArea.MoodCards, shape)
    }
    val drawBackground: DrawScope.() -> Unit = remember(backgroundColor) {
        { drawRect(backgroundColor) }
    }
    val backdrop = rememberCanvasBackdrop(drawBackground)
    return glassMaterial(
        settings = settings.forArea(GlassArea.MoodCards),
        shape = shape,
        ownBackdrop = backdrop,
        highlightAlpha = .3f,
        drawShadow = false,
        filmColor = cardColor,
    )
}
