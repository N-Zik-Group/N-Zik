package app.n_zik.android.components.menu.info

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.MenuSheetSurface
import app.n_zik.android.components.menu.menuSheetSectionColor
import app.n_zik.android.components.menu.menuSheetFilmOpacity
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.glassOverlayPaint
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.recessedSurfaceColor
import app.n_zik.android.surfaceColor

/** Shares menu optics while each information section paints its own film once. */
@Composable
fun InformationMenuSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val settings = rememberGlassSettings()
    val film = if (settings.usesGlassMaterial(GlassArea.Menus)) menuSheetFilmOpacity(settings) else 1f
    // The information screen paints either a full loading body, or adjacent header/body
    // bands filling this surface. Use the least opaque band, never the transparent host.
    val bandAlpha = minOf(surfaceColor(colorPalette().background1).alpha,
        recessedSurfaceColor(colorPalette().background0).alpha) * film
    MenuSheetSurface(modifier.glassOverlayPaint(bandAlpha), content)
}

/** Keeps the information header on the same elevated band as other media menus. */
@Composable
fun informationMenuHeaderColor(): Color =
    menuSheetSectionColor(surfaceColor(colorPalette().background1))

/** Uses the recessed body tone without placing a second film below the header. */
@Composable
fun informationMenuBodyColor(): Color =
    menuSheetSectionColor(recessedSurfaceColor(colorPalette().background0))
