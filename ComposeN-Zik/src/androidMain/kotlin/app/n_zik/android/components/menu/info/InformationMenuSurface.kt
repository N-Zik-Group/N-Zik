package app.n_zik.android.components.menu.info

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.MenuSheetSurface
import app.n_zik.android.components.menu.menuSheetSectionColor
import app.n_zik.android.recessedSurfaceColor
import app.n_zik.android.surfaceColor

/** Shares menu optics while each information section paints its own film once. */
@Composable
fun InformationMenuSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    MenuSheetSurface(modifier, content)
}

/** Keeps the information header on the same elevated band as other media menus. */
@Composable
fun informationMenuHeaderColor(): Color =
    menuSheetSectionColor(surfaceColor(colorPalette().background1))

/** Uses the recessed body tone without placing a second film below the header. */
@Composable
fun informationMenuBodyColor(): Color =
    menuSheetSectionColor(recessedSurfaceColor(colorPalette().background0))
