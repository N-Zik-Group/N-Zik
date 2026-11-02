package app.n_zik.android.components.glass

import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/** Inline mood tiles must never sample the page capture containing themselves. */
@Composable
internal fun Modifier.glassMoodCardBackground(
    backgroundColor: Color,
    cardColor: Color,
    shape: Shape,
    settings: GlassSettings = rememberGlassSettings(),
    exportedBackdrop: LayerBackdrop? = null,
): Modifier {
    if (!settings.usesGlassMaterial(GlassArea.MoodCards)) {
        return background(cardColor, shape)
            .glassAreaContour(settings, GlassArea.MoodCards, shape)
    }
    val backdrop = rememberInlineGlassBackdrop(backgroundColor)
    return glassMaterial(
        settings = settings.forArea(GlassArea.MoodCards),
        shape = shape,
        ownBackdrop = backdrop,
        highlightAlpha = .3f,
        drawShadow = false,
        filmColor = cardColor,
        exportedBackdrop = exportedBackdrop,
    )
}
