package app.n_zik.android.components.navigation.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import app.n_zik.android.components.navigation.nav.bottomNavigationBarShape
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.navigation.nav.topNavigationBarShape
import app.n_zik.android.components.navigation.header.LocalUnifiedTopGlassSurface
import app.n_zik.android.components.ui.screens.home.LocalSharedHomeHeaderGlass
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassScreenEdge
import app.n_zik.android.components.glass.extendGlassPastScreenEdge
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.utils.toast.trackToastObstruction
import kotlin.math.roundToInt

/** Classic horizontal bars keep their surface while their tabs fold into the shared Home/mini row. */
@Composable
internal fun ClassicBottomDockNavigation(
    dock: FloatingDockEnvironment,
    rowHeight: Dp,
    bottomPadding: Dp,
    tabsCount: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    top: Boolean = false,
    tabs: @Composable (Modifier, Backdrop) -> Unit,
) {
    val settings = rememberGlassSettings()
    val glass = settings.usesGlassMaterial(GlassArea.Nav)
    val actionShape = uiRoundnessShape()
    val shape = if (top) topNavigationBarShape(actionShape) else bottomNavigationBarShape(actionShape)
    val color = surfaceColor(colorPalette().background1)
    val unifiedSurface = (LocalSharedHomeHeaderGlass.current
        ?.takeIf { it.topNavigation && it.owner != null }?.surface
        ?: LocalUnifiedTopGlassSurface.current).takeIf { top && glass }
    val surface = if (unifiedSurface != null) Modifier
        else if (glass) Modifier.extendGlassPastScreenEdge(if (top) GlassScreenEdge.Top else GlassScreenEdge.Bottom)
            .glassMaterial(settings.forArea(GlassArea.Nav), shape, filmColor = color)
        else Modifier.background(color, shape).glassAreaContour(settings, GlassArea.Nav, shape)
    val ownBackdrop = rememberLayerBackdrop()
    val backdrop = unifiedSurface ?: ownBackdrop
    val folded = dock.navigationFolded
    Layout(
        modifier = modifier.fillMaxWidth().navigationGestureScrollBoundary()
            .trackToastObstruction(enabled = !top),
        content = {
            // Bottom includes the Android navigation area; Top owns only its control row.
            Box((if (unifiedSurface == null) Modifier.layerBackdrop(ownBackdrop) else Modifier).then(surface))
            Box(Modifier.graphicsLayer { alpha = (1f - dock.navigationProgress * 3f).coerceIn(0f, 1f) }
                .drawWithContent {
                    val p = dock.navigationProgress
                    if (p == 0f) drawContent() else {
                        val width = lerp(size.width, FloatingDockDimensions.compactHeight.dp.toPx(), p)
                        val left = FloatingDockDimensions.outerMargin.dp.toPx() * p
                        clipPath(floatingDockClipPath(actionShape, Size(width, size.height), left, layoutDirection, this)) {
                            this@drawWithContent.drawContent()
                        }
                    }
                }
                .then(if (folded) Modifier.clearAndSetSemantics {} else Modifier)
                .pointerInput(folded) {
                    if (folded) awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }) {
                tabs(Modifier.fillMaxSize(), backdrop)
            }
            Box(Modifier.graphicsLayer { alpha = dock.navigationProgress }
                .then(if (!folded) Modifier.clearAndSetSemantics {} else Modifier)) {
                Box(Modifier.fillMaxSize().clip(actionShape)
                    .then(if (folded) Modifier.clickable(role = Role.Button, onClick = dock.state::expand) else Modifier),
                    contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.floating_dock_home), stringResource(R.string.home),
                        Modifier.size(26.dp), tint = colorPalette().text)
                }
            }
        },
    ) { children, constraints ->
        val width = constraints.maxWidth
        val row = rowHeight.roundToPx()
        val bottom = bottomPadding.roundToPx().coerceAtLeast(0)
        val compact = FloatingDockDimensions.compactHeight.dp.roundToPx().coerceAtMost(width)
        val p = dock.navigationProgress.coerceIn(0f, 1f)
        val inset = (if (glass) 4.dp else 16.dp).toPx()
        val slot = (width - 2f * inset).coerceAtLeast(0f) / tabsCount.coerceAtLeast(1)
        val sourceX = inset + slot * (selectedIndex + .5f) - compact / 2f
        val homeX = lerp(sourceX, FloatingDockDimensions.outerMargin.dp.toPx(), p).roundToInt()
        val surfacePlaceable = children[0].measure(Constraints.fixed(width, row + bottom))
        val tabPlaceable = children[1].measure(Constraints.fixed(width, row))
        val home = children[2].measure(Constraints.fixed(compact, compact))
        layout(width, row + bottom) {
            surfacePlaceable.placeRelative(0, 0)
            if (p < 1f) tabPlaceable.placeRelative(0, 0)
            if (p > 0f) home.placeRelative(homeX, (row - compact) / 2)
        }
    }
}
