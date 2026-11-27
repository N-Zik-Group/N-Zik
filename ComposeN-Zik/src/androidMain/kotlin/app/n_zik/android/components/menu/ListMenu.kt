package app.n_zik.android.components.menu

import app.n_zik.android.components.glass.glassOverlayPaint

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.ui.PressFeedbackHorizontalInset
import app.n_zik.android.components.ui.horizontalLayoutOutset
import app.n_zik.android.ui.components.LocalMenuScrollState
import app.n_zik.android.ui.components.themed.ItemTextPlaceholder

import app.n_zik.android.utils.conditional
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassHorizontalSeparator
import app.n_zik.android.components.glass.GlassMaterialContainerColumn
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassNativeSurfaceTint
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.menu.MenuConstants.CONTENT_HEIGHT_FRACTION
import app.n_zik.android.recessedSurfaceColor
import app.n_zik.android.surfaceColor
import app.n_zik.android.topUiRoundnessShape
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape

object ListMenu {

    @Composable
    fun Menu(
        showDragHandle: Boolean = true,
        title: String? = null,
        headerTrailing: (@Composable RowScope.() -> Unit)? = null,
        drawSurface: Boolean = true,
        headerContent: (@Composable ColumnScope.() -> Unit)? = null,
        bodyHeader: (@Composable ColumnScope.() -> Unit)? = null,
        emptyBody: (@Composable () -> Unit)? = null,
        headerLeading: (@Composable RowScope.() -> Unit)? = null,
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
                        foregroundFill = if (useGlass) headerFilm else if (hasHeader) headerFilm else nativeBodyColor,
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

                val topShape = topUiRoundnessShape()
                // Content
                GlassMaterialContainerColumn(
                    includeParent = true,
                    sectionFilm = bodyFilm,
                    foregroundFill = bodyFilm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(bodyFilm)
                        .glassOverlayPaint(bodyFilm.alpha)
                        .conditional(!hasHeader && (!useGlass || ownsSurface)) {
                            clip(topShape)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    bodyHeader?.invoke(this)
                    if (emptyBody != null) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            emptyBody()
                        }
                    } else {
                        val scrollState = LocalMenuScrollState.current?.rememberListState() ?: rememberScrollState()
                        Column(
                            Modifier.fillMaxWidth().weight(1f)
                                .verticalScroll(scrollState)
                                .padding(horizontal = MenuConstants.LIST_CONTENT_HORIZONTAL_PADDING.dp)
                                .padding(top = MenuConstants.LIST_CONTENT_TOP_PADDING.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CompositionLocalProvider(
                                LocalListMenuRowOutset provides
                                    (MenuConstants.LIST_CONTENT_HORIZONTAL_PADDING.dp - PressFeedbackHorizontalInset),
                                content = { content() },
                            )
                            Spacer(Modifier.height(100.dp))
                        }
                    }
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
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
        loading: Boolean = false,
        trailingContent: @Composable () -> Unit = {}
    ) {
        val alpha = if (enabled) 1f else 0.5f
        val isScrollingTextDisabled by rememberPreference( disableScrollingTextKey, false )
        val shape = uiRoundnessShape()
        val outset = LocalListMenuRowOutset.current

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy( 12.dp ),
            modifier = Modifier.horizontalLayoutOutset(outset)
                .then(modifier)
                .fillMaxWidth()
                .clip(shape)
                .menuEntryClick(shape, enabled && !loading, onClick, onLongClick, insetFeedback = false)
                .padding(vertical = 10.dp, horizontal = outset)
        ) {
            icon()

            Column(
                modifier = Modifier.weight( 1f )
            ) {
                if (loading) ItemTextPlaceholder(null, typography().s.semiBold)
                else Text(
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
                    if (loading) ItemTextPlaceholder(null, typography().xs, width = 0.4f)
                    else Text(
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
