package app.n_zik.android.components.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.zIndex

/** Keeps a card's clipped decoration and indication behind its unmasked control content. */
@Composable
internal fun GlassControlOverflowSurface(
    modifier: Modifier,
    shape: Shape,
    surface: @Composable BoxScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val parent = LocalGlassGroupElevation.current
    val elevation = remember(parent) { GlassGroupElevation(parent) }
    Box(modifier.zIndex(if (elevation.raised) 3f else 0f)) {
        surface()
        CompositionLocalProvider(LocalGlassGroupElevation provides elevation) {
            Column(Modifier.fillMaxWidth().drawWithCache {
                val path = when (val outline = shape.createOutline(size, layoutDirection, this)) {
                    is Outline.Generic -> outline.path
                    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                }
                onDrawWithContent {
                    // Static content keeps the native card's corners. Only a live spring
                    // needs to paint beyond the card; use no raster layer around that motion.
                    if (elevation.raised) drawContent()
                    else clipPath(path) { this@onDrawWithContent.drawContent() }
                }
            }, content = content)
        }
    }
}
