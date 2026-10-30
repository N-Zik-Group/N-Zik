package app.n_zik.android.components.menu

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.glassAreaContour
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import app.n_zik.android.components.glass.GlassHorizontalSeparator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.n_zik.android.components.glass.GlassMaterialContainerColumn
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.glassNativeSurfaceTint
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.recessedSurfaceColor
import app.n_zik.android.surfaceColor
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.typography
import app.n_zik.android.components.menu.MenuConstants.CONTENT_HEIGHT_FRACTION
import app.n_zik.android.components.menu.MenuConstants.DRAG_HANDLE_BOTTOM_PADDING
import app.n_zik.android.components.menu.MenuConstants.DRAG_HANDLE_CORNER_RADIUS
import app.n_zik.android.components.menu.MenuConstants.DRAG_HANDLE_HEIGHT
import app.n_zik.android.components.menu.MenuConstants.DRAG_HANDLE_TOP_PADDING
import app.n_zik.android.components.menu.MenuConstants.DRAG_HANDLE_WIDTH
import app.n_zik.android.topUiRoundnessShape

object ListMenu {

    @Composable
    fun Menu( showDragHandle: Boolean = true, title: String? = null, headerTrailing: (@Composable RowScope.() -> Unit)? = null, drawSurface: Boolean = true, content: @Composable ColumnScope.() -> Unit ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp
        val hasHeader = showDragHandle || title != null || headerTrailing != null
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
                    .conditional(ownsSurface || !useGlass) { clip(menuShape) }
                    .then(if (useGlass) Modifier else Modifier.background(recessedSurfaceColor(colorPalette().background0))
                        .then(if (ownsSurface) Modifier.glassAreaContour(glass, GlassArea.Menus, menuShape) else Modifier)),
            horizontalAlignment = Alignment.CenterHorizontally,
            includeParent = useGlass && !ownsSurface,
            sectionFilm = if (useGlass) null else nativeBodyColor,
            material = if (useGlass && ownsSurface) ({ exported -> Modifier.menuSheetMaterial(
                glass, menuShape, drawFilm = false, exportedBackdrop = exported,
            ) }) else null,
            content = {
                if (hasHeader) {
                    // Header with handle bar
                    GlassMaterialContainerColumn(
                        includeParent = true,
                        sectionFilm = headerFilm,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .conditional(!useGlass) { clip(menuShape) }
                            .background(headerFilm)
                    ) {
                        if (showDragHandle) {
                            Box(
                                modifier = Modifier
                                    .padding(top = DRAG_HANDLE_TOP_PADDING.dp, bottom = DRAG_HANDLE_BOTTOM_PADDING.dp)
                                    .size(width = DRAG_HANDLE_WIDTH.dp, height = DRAG_HANDLE_HEIGHT.dp)
                                    .clip(RoundedCornerShape(DRAG_HANDLE_CORNER_RADIUS.dp))
                                    .background(colorPalette().text)
                            )
                        }

                        if (headerTrailing != null) {
                            // Mirror the action width to keep the title centered.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .alpha(0f)
                                        .clearAndSetSemantics {}
                                ) {
                                    headerTrailing()
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                title?.let {
                                    Text(
                                        text = it,
                                        style = typography().m.copy(color = colorPalette().text),
                                        modifier = Modifier.padding(top = 5.dp, bottom = 10.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                headerTrailing()
                            }
                        } else {
                            title?.let {
                                Text(
                                    text = it,
                                    style = typography().m.copy(color = colorPalette().text),
                                    modifier = Modifier.padding(top = 5.dp, bottom = 10.dp)
                                )
                            }
                        }

                        GlassHorizontalSeparator(Modifier.height(1.dp))
                    }
                }

                val topShape = topUiRoundnessShape()
                // Content
                GlassMaterialContainerColumn(
                    includeParent = true,
                    sectionFilm = bodyFilm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(bodyFilm)
                        .conditional(!hasHeader && (!useGlass || ownsSurface)) {
                            clip(topShape)
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    content()
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        )
    }

    @Composable
    fun Entry(
        text: String,
        icon: @Composable RowScope.() -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        subtitle: String? = null,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {},
        trailingContent: @Composable () -> Unit = {}
    ) {
        val alpha = if (enabled) 1f else 0.5f
        val isScrollingTextDisabled by rememberPreference( disableScrollingTextKey, false )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy( 12.dp ),
            modifier = modifier
                .fillMaxWidth()
                .clip(uiRoundnessShape())
                .combinedClickable(
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding( vertical = 10.dp )
        ) {
            icon()

            Column(
                modifier = Modifier.weight( 1f )
            ) {
                Text(
                    text = text,
                    overflow = TextOverflow.Ellipsis,
                    color = colorPalette().text.copy(alpha = alpha),
                    textAlign = TextAlign.Start,
                    style = typography().s.semiBold,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                                       .conditional( !isScrollingTextDisabled ) {
                                           basicMarquee( iterations = Int.MAX_VALUE )
                                       }
                )

                subtitle?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = it,
                        overflow = TextOverflow.Ellipsis,
                        color = colorPalette().textSecondary.copy(alpha = alpha),
                        textAlign = TextAlign.Start,
                        style = typography().xs,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                                           .conditional( !isScrollingTextDisabled ) {
                                               basicMarquee( iterations = Int.MAX_VALUE )
                                           }
                    )
                }
            }

            trailingContent()
        }
    }
}
