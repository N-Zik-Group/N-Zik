package app.n_zik.android.components.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassHorizontalSeparator
import app.n_zik.android.components.glass.GlassMaterialContainerColumn
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassNativeSurfaceTint
import app.n_zik.android.components.glass.glassOverlayPaint
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.menu.MenuConstants.CONTENT_HEIGHT_FRACTION
import app.n_zik.android.recessedSurfaceColor
import app.n_zik.android.surfaceColor
import app.n_zik.android.topUiRoundnessShape
import app.n_zik.android.utils.conditional

@Composable
internal fun MenuFrame(
    showDragHandle: Boolean,
    title: String?,
    headerLeading: (@Composable RowScope.() -> Unit)?,
    headerTrailing: (@Composable RowScope.() -> Unit)?,
    drawSurface: Boolean,
    headerContent: (@Composable ColumnScope.() -> Unit)?,
    bodyHeader: (@Composable ColumnScope.() -> Unit)?,
    emptyBody: (@Composable () -> Unit)?,
    bodyAlignment: Alignment.Horizontal,
    content: @Composable ColumnScope.() -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val hasHeader = showDragHandle || title != null || headerLeading != null || headerTrailing != null || headerContent != null
    val leading = menuHeaderLeading(headerLeading, hasHeader && headerContent == null)
    val glass = rememberGlassSettings()
    val useGlass = glass.usesGlassMaterial(GlassArea.Menus)
    val ownsSurface = drawSurface && !LocalMenuSheetSurface.current
    val menuShape = topUiRoundnessShape()
    val filmOpacity = if (useGlass) menuSheetFilmOpacity(glass) else 1f
    val nativeHeaderColor = surfaceColor(colorPalette().background1)
    val nativeBodyColor = recessedSurfaceColor(colorPalette().background0)
    val headerColor = if (useGlass) glassNativeSurfaceTint(glass, nativeHeaderColor) else nativeHeaderColor
    val bodyColor = if (useGlass) glassNativeSurfaceTint(glass, nativeBodyColor) else nativeBodyColor
    val headerFilm = if (useGlass && !drawSurface) Color.Transparent
        else headerColor.copy(alpha = headerColor.alpha * filmOpacity)
    val bodyFilm = if (useGlass && !drawSurface) Color.Transparent
        else bodyColor.copy(alpha = bodyColor.alpha * filmOpacity)

    GlassMaterialContainerColumn(
        Modifier.heightIn( max = (screenHeight * CONTENT_HEIGHT_FRACTION).dp )
                .fillMaxWidth()
                .glassOverlayPaint(if (useGlass) 0f else nativeBodyColor.alpha)
                .conditional(ownsSurface || !useGlass) { clip(menuShape) }
                .then(if (useGlass) Modifier else Modifier.background(recessedSurfaceColor(colorPalette().background0))
                    .then(if (ownsSurface) Modifier.glassAreaContour(glass, GlassArea.Menus, menuShape) else Modifier)),
        horizontalAlignment = Alignment.CenterHorizontally,
        includeParent = useGlass && !ownsSurface,
        sectionFilm = if (useGlass) null else nativeBodyColor,
        materialSource = if (useGlass && ownsSurface) LocalGlassBackdrop.current else null,
        material = if (useGlass && ownsSurface) ({ exported -> Modifier.menuSheetMaterial(
            glass, menuShape, drawFilm = false, exportedBackdrop = exported,
        ) }) else null,
        content = {
            if (hasHeader) {
                // Header with handle bar
                GlassMaterialContainerColumn(
                    includeParent = true,
                    sectionFilm = headerFilm,
                    foregroundFill = headerFilm,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .conditional(!useGlass) { clip(menuShape) }
                        .background(headerFilm)
                        .glassOverlayPaint(headerFilm.alpha)
                ) {
                    if (showDragHandle) {
                        MenuSheetDragHandle(
                            color = colorPalette().text,
                            centeredVertically = title == null && leading == null && headerTrailing == null && headerContent == null,
                        )
                    }

                    MenuHeaderTitle(title, leading, headerTrailing)
                    headerContent?.invoke(this)

                    GlassHorizontalSeparator(Modifier.height(1.dp))
                }
            }

            GlassMaterialContainerColumn(
                includeParent = true,
                sectionFilm = bodyFilm,
                foregroundFill = bodyFilm,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .conditional(!hasHeader && (!useGlass || ownsSurface)) { clip(menuShape) }
                    .background(bodyFilm)
                    .glassOverlayPaint(bodyFilm.alpha),
                horizontalAlignment = bodyAlignment,
            ) {
                bodyHeader?.invoke(this)
                if (emptyBody != null) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        emptyBody()
                    }
                } else {
                    content()
                }
            }
        },
    )
}
