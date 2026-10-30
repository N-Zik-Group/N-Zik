package app.n_zik.android.components.menu

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.forArea
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassRenderer
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.glassFilmOpacity
import app.n_zik.android.components.glass.glassRenderer

@Composable
private fun canSampleMenuBackdrop(): Boolean {
    val host = LocalGlassMenuHost.current
    return host?.dialogs?.windowRoot === LocalView.current.rootView && LocalGlassBackdrop.current != null
}

@Composable
internal fun menuSheetFilmOpacity(settings: GlassSettings): Float = glassFilmOpacity(
    configured = settings.film,
    overlay = true,
    fallback = !canSampleMenuBackdrop() || glassRenderer(settings) == GlassRenderer.Tint,
    dark = colorPalette().isDark,
)

/** Never samples a graphics layer from a different Android window. */
@Composable
internal fun Modifier.menuSheetMaterial(settings: GlassSettings, shape: Shape, drawFilm: Boolean = true,
    exportedBackdrop: LayerBackdrop? = null): Modifier {
    return glassMaterial(
        settings.forArea(GlassArea.Menus),
        shape,
        separateWindow = !canSampleMenuBackdrop(),
        menu = true,
        drawFilm = drawFilm,
        exportedBackdrop = exportedBackdrop,
    )
}
