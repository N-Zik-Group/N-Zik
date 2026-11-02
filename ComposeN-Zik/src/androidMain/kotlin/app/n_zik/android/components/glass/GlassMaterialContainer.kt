package app.n_zik.android.components.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.Backdrop
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
    materialSource: Backdrop? = null,
    sectionFilmIsMaterial: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassMaterialContainerScope(modifier, includeParent, sectionFilm, material, materialSource, sectionFilmIsMaterial) { surface ->
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
    materialSource: Backdrop? = null,
    sectionFilmIsMaterial: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    val exported = if (material != null) rememberLayerBackdrop() else null
    val parent = if (includeParent) LocalGlassContainerBackdrop.current else null
    val surface = when {
        sectionFilm != null -> {
            val drawSection: DrawScope.() -> Unit = remember(sectionFilm) { { drawRect(sectionFilm) } }
            rememberCanvasBackdrop(drawSection)
        }
        material != null -> exported
        else -> null
    }
    val backdrop = if (parent != null && surface != null) rememberCombinedBackdrop(parent, surface)
        else surface ?: parent
    val materialModifier = if (material != null && exported != null) material(exported) else Modifier
    val inheritedSource = LocalGlassMaterialSource.current
    val source = materialSource ?: when {
        material != null -> inheritedSource ?: LocalGlassContainerBackdrop.current
        // A native section is the real background even inside an optical parent.
        // Glass bands explicitly retain the raw source to avoid applying film twice.
        sectionFilm != null && !sectionFilmIsMaterial -> backdrop
        else -> inheritedSource
    }
    CompositionLocalProvider(LocalGlassContainerBackdrop provides backdrop,
        LocalGlassMaterialSource provides source) {
        content(modifier.then(materialModifier))
    }
}
