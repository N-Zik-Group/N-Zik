package app.n_zik.android.components.navigation.header

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassScreenEdge
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.extendGlassPastScreenEdge
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.navigation.appHeaderOverlayTopPx
import app.n_zik.android.components.navigation.nav.topNavigationBarShape
import app.n_zik.android.uiRoundnessShape
import kotlin.math.roundToInt

/** The pane records only its material; sibling controls can safely sample that visible surface. */
@Composable
internal fun StandaloneHeaderGlass(
    settings: GlassSettings,
    filmColor: Color,
    pageBackdrop: Backdrop?,
    statusBarTopPx: Float,
    headerHeightPx: Float,
    headerOffsetPx: () -> Float,
    horizontalInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    content: @Composable (Backdrop) -> Unit,
) {
    val surface = rememberLayerBackdrop()
    val shape = topNavigationBarShape(uiRoundnessShape())
    // The optical renderer accepts CornerBasedShape; zero roundness must not fall back to tint.
    val materialShape = if (shape == RectangleShape) RoundedCornerShape(0.dp) else shape
    Layout(content = {
        Box(Modifier.windowInsetsPadding(horizontalInsets).graphicsLayer {
            alpha = headerScrollOpacity(statusBarTopPx, headerHeightPx, headerOffsetPx())
        }.extendGlassPastScreenEdge(GlassScreenEdge.Top)
            .layerBackdrop(surface)
            .glassMaterial(
                settings = settings.forArea(GlassArea.Header),
                shape = materialShape,
                ownBackdrop = pageBackdrop,
                filmColor = filmColor,
                drawShadow = false,
            ))
        Box { content(surface) }
    }) { children, constraints ->
        val foreground = children[1].measure(constraints)
        val visibleHeight = appHeaderOverlayTopPx(headerHeightPx, statusBarTopPx, headerOffsetPx())
            .roundToInt().coerceIn(0, foreground.height)
        val pane = children[0].measure(Constraints.fixed(foreground.width, visibleHeight))
        layout(foreground.width, foreground.height) {
            pane.placeRelative(0, 0)
            foreground.placeRelative(0, 0)
        }
    }
}
