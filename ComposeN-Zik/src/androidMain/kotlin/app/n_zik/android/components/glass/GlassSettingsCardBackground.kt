package app.n_zik.android.components.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop

/** Uses the navigation material, with a safe independent backdrop for inline cards. */
@Composable
internal fun Modifier.glassSettingsCardBackground(
    backgroundColor: Color,
    cardColor: Color,
    shape: Shape,
    settings: GlassSettings,
): Modifier {
    if (!settings.usesGlassMaterial(GlassArea.SettingsCards)) return this
    // The page capture contains this card, so reproduce its flat backdrop independently.
    val drawBackground: DrawScope.() -> Unit = remember(backgroundColor) {
        { drawRect(backgroundColor) }
    }
    val backdrop = rememberCanvasBackdrop(drawBackground)
    return glassMaterial(
        settings = settings.forArea(GlassArea.SettingsCards),
        shape = shape,
        ownBackdrop = backdrop,
        highlightAlpha = .3f,
        // Keep the lens/highlight at the card's own bounds, without a second shadow plate.
        drawShadow = false,
        // The original themed fill also carries the existing search highlight.
        filmColor = cardColor,
    )
}
