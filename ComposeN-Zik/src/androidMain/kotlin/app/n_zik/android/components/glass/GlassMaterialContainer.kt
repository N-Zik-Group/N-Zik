package app.n_zik.android.components.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCombinedBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop

/** Provides only the rendered material or uniform section fill to descendant controls. */
@Composable
fun GlassMaterialContainerColumn(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    includeParent: Boolean = false,
    sectionFilm: Color? = null,
    material: (@Composable (LayerBackdrop) -> Modifier)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassMaterialContainerScope(modifier, includeParent, sectionFilm, material) { surface ->
        Column(surface, verticalArrangement, horizontalAlignment, content)
    }
}

/** Exports material directly; it never re-records the layout with its children suppressed. */
@Composable
fun GlassMaterialContainerScope(
    modifier: Modifier,
    includeParent: Boolean = false,
    sectionFilm: Color? = null,
    material: (@Composable (LayerBackdrop) -> Modifier)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    val exported = rememberLayerBackdrop()
    val parent = if (includeParent) LocalGlassContainerBackdrop.current else null
    val surface = when {
        sectionFilm != null -> rememberCanvasBackdrop { drawRect(sectionFilm) }
        material != null -> exported
        else -> null
    }
    val backdrop = if (parent != null && surface != null) rememberCombinedBackdrop(parent, surface)
        else surface ?: parent
    val materialModifier = material?.invoke(exported) ?: Modifier
    CompositionLocalProvider(LocalGlassContainerBackdrop provides backdrop) {
        content(modifier.then(materialModifier))
    }
}
