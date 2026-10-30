package app.n_zik.android.components.menu

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.glassAreaContour
import androidx.compose.foundation.background
import app.n_zik.android.components.glass.GlassMaterialContainerColumn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.glassNativeSurfaceTint
import app.n_zik.android.recessedSurfaceColor
import app.n_zik.android.topUiRoundnessShape

/** A custom media header and its list/grid share one optical surface and outer rim. */
internal val LocalMenuSheetSurface = staticCompositionLocalOf { false }

@Composable
internal fun MenuSheetSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val settings = rememberGlassSettings()
    val useGlass = settings.usesGlassMaterial(GlassArea.Menus)
    GlassMaterialContainerColumn(
        modifier.fillMaxWidth().then(
            if (useGlass) Modifier else Modifier.background(recessedSurfaceColor(colorPalette().background0))
                .glassAreaContour(settings, GlassArea.Menus, topUiRoundnessShape())
        ),
        sectionFilm = if (useGlass) null else recessedSurfaceColor(colorPalette().background0),
        material = if (useGlass) ({ exported -> Modifier.menuSheetMaterial(
            settings, topUiRoundnessShape(), drawFilm = false, exportedBackdrop = exported,
        ) }) else null,
    ) {
        CompositionLocalProvider(LocalMenuSheetSurface provides (useGlass || settings.usesGlassContour(GlassArea.Menus))) {
            content()
        }
    }
}

/** Preserve the original section colour while letting the shared material show through. */
@Composable
internal fun menuSheetSectionColor(color: Color): Color {
    if (!LocalMenuSheetSurface.current || !rememberGlassSettings().usesGlassMaterial(GlassArea.Menus)) return color
    val settings = rememberGlassSettings()
    return glassNativeSurfaceTint(settings, color)
        .copy(alpha = color.alpha * menuSheetFilmOpacity(settings))
}
