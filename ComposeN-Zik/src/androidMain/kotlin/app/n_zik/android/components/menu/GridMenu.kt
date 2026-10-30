package app.n_zik.android.components.menu

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.gridMenuShape

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import app.n_zik.android.components.glass.GlassHorizontalSeparator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.n_zik.android.components.glass.GlassMaterialContainerColumn
import app.n_zik.android.components.glass.GlassMaterialContainerScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.glassNativeSurfaceTint
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.colorPalette
import app.n_zik.android.recessedSurfaceColor
import app.n_zik.android.surfaceColor
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.menu.MenuConstants.CONTENT_HEIGHT_FRACTION
import app.n_zik.android.components.menu.MenuConstants.CONTENT_HORIZONTAL_PADDING
import app.n_zik.android.components.menu.MenuConstants.CONTENT_TOP_PADDING
import app.n_zik.android.components.menu.MenuConstants.DRAG_HANDLE_BOTTOM_PADDING
import app.n_zik.android.components.menu.MenuConstants.DRAG_HANDLE_CORNER_RADIUS
import app.n_zik.android.components.menu.MenuConstants.DRAG_HANDLE_HEIGHT
import app.n_zik.android.components.menu.MenuConstants.DRAG_HANDLE_TOP_PADDING
import app.n_zik.android.components.menu.MenuConstants.DRAG_HANDLE_WIDTH
import app.n_zik.android.topUiRoundnessShape

object GridMenu {

    @Composable
    fun Menu( showDragHandle: Boolean = true, title: String? = null, headerTrailing: (@Composable RowScope.() -> Unit)? = null, drawSurface: Boolean = true, content: LazyGridScope.() -> Unit ) {
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
        ) {
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

            // Grid content
            GlassMaterialContainerScope(
                Modifier.fillMaxWidth().weight(1f)
                    .conditional(!hasHeader && (!useGlass || ownsSurface)) { clip(menuShape) }
                    .background(bodyFilm),
                includeParent = true,
                sectionFilm = bodyFilm,
            ) { bodyModifier ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive( minSize = 120.dp ),
                    contentPadding = PaddingValues(
                        start = CONTENT_HORIZONTAL_PADDING.dp,
                        end = CONTENT_HORIZONTAL_PADDING.dp,
                        top = CONTENT_TOP_PADDING.dp,
                        bottom = 100.dp
                    ),
                    modifier = bodyModifier,
                    content = content
                )
            }
        }
    }

    @Composable
    fun Entry(
        text: String,
        icon: @Composable BoxScope.() -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        subtitle: String? = null,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {},
        iconHasOwnBackground: Boolean = false,
        trailingContent: @Composable () -> Unit = {}
    ) {
        val alpha = if (enabled) 1f else 0.5f
        val isScrollingTextDisabled by rememberPreference( disableScrollingTextKey, false )
        val glass = rememberGlassSettings()
        val innerDrawsMaterial = iconHasOwnBackground &&
            (glass.usesGlassMaterial(GlassArea.ActionButtons) || glass.usesGlassContour(GlassArea.ActionButtons))

        Column(
            modifier = modifier
                .clip(gridMenuShape())
                .combinedClickable(
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon + trailing content side by side
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Icon with accent background
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .then(if (innerDrawsMaterial) Modifier else Modifier.iconBadgeBackground(
                            color = colorPalette().accent.copy(alpha = 0.1f),
                            shape = uiRoundnessShape(),
                            settings = glass,
                        )),
                    contentAlignment = Alignment.Center,
                    content = icon
                )

                // Trailing content next to icon (arrow/toggle)
                trailingContent()
            }

            // Text centered
            Text(
                text = text,
                overflow = TextOverflow.Ellipsis,
                color = colorPalette().text.copy(alpha = alpha),
                style = typography().xs.semiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .conditional( !isScrollingTextDisabled ) {
                        basicMarquee( iterations = Int.MAX_VALUE )
                    }
            )

            // Subtitle
            subtitle?.let {
                Text(
                    text = it,
                    overflow = TextOverflow.Ellipsis,
                    color = colorPalette().textSecondary.copy(alpha = alpha),
                    style = typography().xxs,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .conditional( !isScrollingTextDisabled ) {
                            basicMarquee( iterations = Int.MAX_VALUE )
                        }
                )
            }
        }
    }
}
