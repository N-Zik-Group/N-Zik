package app.n_zik.android.components.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.core.navigation.navigationChromeTween
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassScreenEdge
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.extendGlassPastScreenEdge
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.glassNativeSurfaceTint
import app.n_zik.android.components.glass.glassFilmOpacity
import app.n_zik.android.components.glass.usesGlassMaterial
import kotlin.math.roundToInt

/** Separate captures keep the pane, its content source, and the toolbar acyclic. */
@Stable
internal class SharedHomeHeaderGlass(
    val list: LayerBackdrop,
    val surface: LayerBackdrop,
    val measuredPanelHeight: MutableIntState,
    val animatedPanelHeight: State<Int>,
    val topNavigation: Boolean,
    val expansion: State<Float>,
) {
    val toolbars = mutableStateListOf<HomeToolbarLayer>()

    var panelTopPx by mutableIntStateOf(0)
    var panelOffset: (() -> Float)? by mutableStateOf(null)
    var navigationBottomPx by mutableIntStateOf(0)
    var owner: Any? by mutableStateOf(null)
        private set

    fun panelVisibility(): Float =
        homeHeaderVisibleFraction(measuredPanelHeight.intValue, panelOffset?.invoke() ?: 0f)

    fun expandedBottomPx(): Int {
        val height = animatedPanelHeight.value
        val visible = panelVisibility()
        return if (topNavigation && navigationBottomPx > 0) {
            sharedTopHomeGlassHeight(navigationBottomPx, panelTopPx, height, visible)
        } else sharedHomeGlassHeight(panelTopPx, height, -height * (1f - visible))
    }

    fun attach(owner: Any) { this.owner = owner }
    fun detach(owner: Any) { if (this.owner === owner) this.owner = null }
}

internal class HomeToolbarLayer(
    val backdrop: LayerBackdrop,
    val tabProgress: State<Float>?,
    val routeProgress: State<Float>?,
)

internal val LocalHomeToolbarListBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
internal val LocalHomeToolbarTabProgress = staticCompositionLocalOf<State<Float>?> { null }
internal val LocalHomeToolbarRouteProgress = staticCompositionLocalOf<State<Float>?> { null }

@Composable
internal fun AnimatedVisibilityScope.homeToolbarProgress(): State<Float> =
    transition.animateFloat(transitionSpec = { navigationChromeTween() }, label = "homeToolbarVisibility") {
        if (it == EnterExitState.Visible) 1f else 0f
    }

internal val LocalSharedHomeHeaderGlass = staticCompositionLocalOf<SharedHomeHeaderGlass?> { null }

internal fun usesSharedHomeHeaderGlass(
    isHome: Boolean,
    tab: String?,
    position: NavigationBarPosition,
    settings: GlassSettings,
): Boolean = isHome && tab in setOf("songs", "artists", "albums", "playlists") &&
    (position.isBottomType || position == NavigationBarPosition.Left || position == NavigationBarPosition.Right ||
        (position == NavigationBarPosition.Top && settings.usesGlassMaterial(GlassArea.Nav))) &&
    settings.usesGlassMaterial(GlassArea.Header)

@Composable
internal fun rememberSharedHomeHeaderGlass(
    enabled: Boolean,
    pageKey: Any?,
    topNavigation: Boolean = false,
    keepHeaderSurface: Boolean = false,
): SharedHomeHeaderGlass? =
    key(pageKey, topNavigation) {
        val expansion = animateFloatAsState(if (enabled) 1f else 0f, navigationChromeTween(), label = "homeGlassExpansion")
        val list = rememberLayerBackdrop()
        val surface = rememberLayerBackdrop()
        val height = remember { mutableIntStateOf(0) }
        val animatedHeight = animateIntAsState(height.intValue, navigationChromeTween(), label = "homeGlassExtent")
        val shared = remember(list, surface) { SharedHomeHeaderGlass(list, surface, height, animatedHeight, topNavigation, expansion) }
        shared.takeIf { keepHeaderSurface || enabled || expansion.value > 0f }
    }

/** Apply only to the list/grid sibling, never the Box which also contains the toolbar. */
@Composable
internal fun Modifier.sharedHomeGlassContent(): Modifier {
    LocalHomeToolbarListBackdrop.current?.let { return layerBackdrop(it) }
    val shared = LocalSharedHomeHeaderGlass.current ?: return this
    return if (LocalDrawHomeGlassPane.current) layerBackdrop(shared.list) else this
}

internal fun sharedHomeGlassHeight(panelTop: Int, panelHeight: Int, panelOffset: Float): Int =
    (panelTop + panelHeight + panelOffset.roundToInt()).coerceAtLeast(0)

internal fun sharedHomeGlassTransitionHeight(headerBottom: Int, expandedBottom: Int, expansion: Float): Int =
    headerBottom + ((expandedBottom - headerBottom) * expansion.coerceIn(0f, 1f)).roundToInt()

/** The initial header/mini clearance must fold away with the Home toolbar as well. */
internal fun sharedTopHomeGlassHeight(navigationBottom: Int, panelTop: Int, panelHeight: Int, visible: Float): Int {
    val floor = navigationBottom.coerceAtLeast(0)
    val expandedBottom = maxOf(floor, panelTop + panelHeight)
    return floor + ((expandedBottom - floor) * visible.coerceIn(0f, 1f)).roundToInt()
}

@Composable
internal fun SharedHomeHeaderPane(
    shared: SharedHomeHeaderGlass,
    settings: GlassSettings,
    filmColor: Color,
    shape: Shape,
    heightPx: () -> Int,
    opacity: () -> Float,
    modifier: Modifier = Modifier,
    upperFilmColor: Color? = null,
    upperHeightPx: () -> Int = { 0 },
    source: LayerBackdrop = shared.list,
) {
    val areaSettings = settings.forArea(GlassArea.Header)
    val filmOpacity = glassFilmOpacity(areaSettings.film, overlay = false)
    val lowerFilm = glassNativeSurfaceTint(areaSettings, filmColor).let { it.copy(alpha = it.alpha * filmOpacity) }
    val upperFilm = upperFilmColor?.let { color ->
        glassNativeSurfaceTint(areaSettings, color).let { it.copy(alpha = it.alpha * filmOpacity) }
    }
    Box(modifier
        .layout { measurable, constraints ->
            val height = heightPx().coerceIn(0, constraints.maxHeight)
            val pane = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
            layout(pane.width, height) { pane.place(0, 0) }
        }
        .graphicsLayer { alpha = opacity().coerceIn(0f, 1f) }
        .extendGlassPastScreenEdge(GlassScreenEdge.Top)
        .layerBackdrop(shared.surface)
        .glassMaterial(
            settings = areaSettings,
            shape = if (shape == RectangleShape) RoundedCornerShape(0.dp) else shape,
            ownBackdrop = source,
            filmColor = filmColor,
            drawFilm = upperFilm == null,
            onDrawTint = if (upperFilm == null) null else {
                {
                    // Work back from the bottom so the hidden screen-edge bleed belongs
                    // to the upper film. The two films never overlap or double their opacity.
                    val lowerHeight = (heightPx() - upperHeightPx()).coerceAtLeast(0).toFloat().coerceAtMost(size.height)
                    val split = size.height - lowerHeight
                    drawRect(upperFilm, size = Size(size.width, split))
                    drawRect(lowerFilm, topLeft = Offset(0f, split), size = Size(size.width, lowerHeight))
                }
            },
            drawShadow = false,
        ))
}

/** Paints the live toolbar above the pane. Its layout and input stay with its Home tab. */
@Composable
internal fun SharedHomeHeaderToolbar(
    toolbar: () -> LayerBackdrop?,
    tabProgress: State<Float>? = null,
    routeProgress: State<Float>? = null,
    headerBottomPx: (() -> Int)? = null,
) {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(Modifier.fillMaxSize()
        .graphicsLayer {
            val tab = tabProgress?.value ?: 1f
            val route = routeProgress?.value ?: 1f
            alpha = tab * route
        }
        .onGloballyPositioned { coordinates = it }
        .drawBehind {
            val progress = (tabProgress?.value ?: 1f) * (routeProgress?.value ?: 1f)
            val backdrop = toolbar() ?: return@drawBehind
            val viewport = coordinates?.takeIf { it.isAttached } ?: return@drawBehind
            val source = backdrop.layerCoordinates?.takeIf { it.isAttached } ?: return@drawBehind
            val anchor = headerBottomPx?.invoke()?.toFloat()
            if (anchor == null || progress >= 1f) {
                with(backdrop) { drawBackdrop(this@drawBehind, viewport, null) }
            } else {
                val toolbarTop = viewport.localPositionOf(source, Offset.Zero).y
                val offset = (anchor - toolbarTop).coerceAtMost(0f) * (1f - progress)
                // Reveal the moving toolbar at the header edge without painting over its controls.
                clipRect(top = anchor.coerceIn(0f, size.height)) {
                    translate(top = offset) {
                        with(backdrop) { drawBackdrop(this@translate, viewport, null) }
                    }
                }
            }
        })
}
