package app.n_zik.android.components.menu.song

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.glassAreaContour
import androidx.compose.foundation.background
import app.n_zik.android.components.glass.GlassMaterialContainerColumn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import app.n_zik.android.components.glass.GlassHorizontalSeparator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.glassNativeSurfaceTint
import app.n_zik.android.components.menu.LocalMenuSheetSurface
import app.n_zik.android.components.menu.menuSheetMaterial
import app.n_zik.android.components.menu.menuSheetFilmOpacity
import app.n_zik.android.recessedSurfaceColor
import app.n_zik.android.surfaceColor
import app.n_zik.android.topUiRoundnessShape

/** Keeps the song header and actions inside one glass surface. */
@Composable
internal fun SongMenuSurface(
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = rememberGlassSettings()
    val useGlass = glass.usesGlassMaterial(GlassArea.Menus)
    val nativeHeaderColor = surfaceColor(colorPalette().background1)
    val nativeBodyColor = recessedSurfaceColor(colorPalette().background0)
    val headerColor = if (useGlass) glassNativeSurfaceTint(glass, nativeHeaderColor) else nativeHeaderColor
    val bodyColor = if (useGlass) glassNativeSurfaceTint(glass, nativeBodyColor) else nativeBodyColor
    val filmOpacity = if (useGlass) menuSheetFilmOpacity(glass) else 1f
    GlassMaterialContainerColumn(modifier.fillMaxWidth().then(
        if (useGlass) Modifier else Modifier.background(bodyColor).glassAreaContour(glass, GlassArea.Menus, topUiRoundnessShape())
    ), sectionFilm = if (useGlass) null else bodyColor,
        material = if (useGlass) ({ exported -> Modifier.menuSheetMaterial(
            glass, topUiRoundnessShape(), drawFilm = false, exportedBackdrop = exported,
        ) }) else null,
    ) {
        GlassMaterialContainerColumn(
            includeParent = true,
            sectionFilm = headerColor.copy(alpha = headerColor.alpha * filmOpacity),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().background(headerColor.copy(alpha = headerColor.alpha * filmOpacity)),
        ) {
            CompositionLocalProvider(LocalMenuSheetSurface provides (useGlass || glass.usesGlassContour(GlassArea.Menus))) { header() }
            GlassHorizontalSeparator(Modifier.height(1.dp))
        }
        GlassMaterialContainerColumn(
            Modifier.fillMaxWidth().background(bodyColor.copy(alpha = bodyColor.alpha * filmOpacity)),
            includeParent = true,
            sectionFilm = bodyColor.copy(alpha = bodyColor.alpha * filmOpacity),
        ) {
            CompositionLocalProvider(LocalMenuSheetSurface provides (useGlass || glass.usesGlassContour(GlassArea.Menus))) { content() }
        }
    }
}
