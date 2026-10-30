// GeometricFloatingTabBar/FoldGeometry port from Square (GPL-3.0); see NOTICE-square.txt.
package app.n_zik.android.components.navigation.dock

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.toast.trackToastObstruction
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt

/** Stable resting-width tabs and one fold clock, as in Square's active geometric bar. */
@Composable
internal fun FloatingDockNavigation(
    dock: FloatingDockEnvironment,
    expandedHeight: Dp,
    bottomPadding: Dp,
    onSearch: () -> Unit,
    showSearch: Boolean = true,
    tabsCount: Int = 5,
    selectedIndex: Int = 0,
    tabs: @Composable (Modifier, Backdrop) -> Unit,
) {
    val settings = rememberGlassSettings()
    val shape = uiRoundnessShape()
    val nativeColor = surfaceColor(colorPalette().background1)
    val useGlass = settings.usesGlassMaterial(GlassArea.Nav)
    val surface = if (useGlass)
        Modifier.glassMaterial(settings.forArea(GlassArea.Nav), shape, filmColor = nativeColor)
    else Modifier.background(nativeColor, shape).glassAreaContour(settings, GlassArea.Nav, shape)
    val surfaceBackdrop = rememberLayerBackdrop()
    val folded = dock.navigationFolded
    val bottomMiniFraction by animateFloatAsState(
        targetValue = if (dock.hasBottomMini) 1f else 0f,
        animationSpec = tween(320, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)),
        label = "dockBottomMiniPresence",
    )
    Layout(
        modifier = Modifier.fillMaxWidth()
            .navigationGestureScrollBoundary()
            .trackToastObstruction()
            .padding(horizontal = FloatingDockDimensions.outerMargin.dp)
            .padding(bottom = bottomPadding),
        content = {
            // Surface alone changes width. The selector samples this sibling, never itself.
            Box(Modifier.layerBackdrop(surfaceBackdrop).then(surface))
            Box(Modifier.graphicsLayer {
                alpha = (1f - dock.navigationProgress * 3f).coerceIn(0f, 1f)
            }.drawWithContent {
                val p = dock.navigationProgress
                if (p == 0f) drawContent() else {
                    val compact = FloatingDockDimensions.compactHeight.dp.toPx()
                    val targetWidth = compact * if (showSearch) 2f - bottomMiniFraction else 1f
                    val width = lerp(size.width, targetWidth, p)
                    val left = (size.width - width) / 2f * (1f - bottomMiniFraction)
                    val path = floatingDockClipPath(shape, Size(width, size.height), left, layoutDirection, this)
                    clipPath(path) { this@drawWithContent.drawContent() }
                }
            }.then(if (folded) Modifier.clearAndSetSemantics {} else Modifier)
                .pointerInput(folded) {
                    if (folded) awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }) {
                tabs(Modifier.fillMaxSize(), surfaceBackdrop)
            }
            // The user chose Home as NZik's folded control; its source is the active tab slot.
            Box(Modifier.graphicsLayer { alpha = dock.navigationProgress }
                .then(if (!folded) Modifier.clearAndSetSemantics {} else Modifier)) {
                DockAction(R.drawable.floating_dock_home, stringResource(R.string.home), folded, dock.state::expand)
            }
            // Search belongs to the last tab at rest and becomes a square detached control.
            if (showSearch) {
                Box(Modifier.graphicsLayer { alpha = bottomMiniFraction * dock.navigationProgress }.then(surface))
                Box(Modifier.graphicsLayer { alpha = dock.navigationProgress }
                    .then(if (!folded) Modifier.clearAndSetSemantics {} else Modifier)) {
                    DockAction(R.drawable.search, stringResource(R.string.search), folded, onSearch)
                }
            }
        },
    ) { children, constraints ->
        val width = constraints.maxWidth
        val expanded = expandedHeight.roundToPx()
        val compact = FloatingDockDimensions.compactHeight.dp.roundToPx()
        val p = dock.navigationProgress.coerceIn(0f, 1f)
        val height = lerp(expanded.toFloat(), compact.toFloat(), p).roundToInt()
        val circle = height.coerceAtMost(width)
        val geometry = foldGeometry(width, expanded, FloatingDockDimensions.componentGap.dp.roundToPx(),
            0, false, p, 0f, tabsCount, selectedIndex, 4.dp.toPx(), compact)
        val joinedWidth = minOf(width, (if (showSearch) 2 else 1) * circle)
        val joinedX = ((width - joinedWidth) / 2f * p).roundToInt()
        val navWidth = lerp(lerp(width.toFloat(), joinedWidth.toFloat(), p), geometry.navWidth.toFloat(), bottomMiniFraction).roundToInt()
        val navX = (joinedX * (1f - bottomMiniFraction)).roundToInt()
        val searchX = geometry.searchX - navX
        val selectedX = geometry.selectedX + navX
        val nav = children[0].measure(Constraints.fixed(navWidth, height))
        // Width never collapses, preventing slot churn and preserving the liquid drag state.
        val tabs = children[1].measure(Constraints.fixed(width, height))
        val home = children[2].measure(Constraints.fixed(circle, height))
        val searchSurface = children.getOrNull(3)?.measure(Constraints.fixed(circle, height))
        val searchControl = children.getOrNull(4)?.measure(Constraints.fixed(circle, height))
        layout(width, height) {
            nav.placeRelative(navX, 0)
            if (p < 1f) tabs.placeRelative(0, 0)
            // Invisible detached controls must not enter hit testing over the resting tabs.
            // A disabled clickable still owns pointer input and would shield the selector.
            if (p > 0f) {
                home.placeRelative(selectedX, 0)
                searchSurface?.placeRelative(searchX, 0)
                searchControl?.placeRelative(searchX, 0)
            }
        }
    }
}

internal fun floatingDockClipPath(shape: Shape, size: Size, left: Float, direction: LayoutDirection, density: Density): Path =
    Path().apply {
        addOutline(shape.createOutline(size, direction, density))
        translate(Offset(left, 0f))
    }

@Composable
private fun DockAction(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().clip(uiRoundnessShape())
        .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), description, Modifier.size(26.dp), tint = colorPalette().text)
    }
}
