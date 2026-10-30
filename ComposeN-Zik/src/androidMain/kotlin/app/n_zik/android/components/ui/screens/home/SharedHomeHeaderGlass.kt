package app.n_zik.android.components.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.NavigationBarPosition
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

/** Separate list input and material output: neither capture contains the toolbar controls. */
@Stable
internal class SharedHomeHeaderGlass(
    val list: LayerBackdrop,
    val surface: LayerBackdrop,
    val measuredPanelHeight: MutableIntState,
    val animatedPanelHeight: State<Int>,
    val topNavigation: Boolean,
) {
    var navigationBottomPx by mutableIntStateOf(0)
    var owner: Any? by mutableStateOf(null)
        private set

    fun attach(owner: Any) { this.owner = owner }
    fun detach(owner: Any) { if (this.owner === owner) this.owner = null }
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
internal fun rememberSharedHomeHeaderGlass(enabled: Boolean, pageKey: Any?, topNavigation: Boolean = false): SharedHomeHeaderGlass? =
    if (!enabled) null else key(pageKey, topNavigation) {
        val list = rememberLayerBackdrop()
        val surface = rememberLayerBackdrop()
        val height = remember { mutableIntStateOf(0) }
        val animatedHeight = animateIntAsState(height.intValue, navigationChromeTween(), label = "homeGlassExtent")
        remember(list, surface) { SharedHomeHeaderGlass(list, surface, height, animatedHeight, topNavigation) }
    }

/** Apply only to the list/grid sibling, never the Box which also contains the toolbar. */
@Composable
internal fun Modifier.sharedHomeGlassContent(): Modifier {
    val shared = LocalSharedHomeHeaderGlass.current ?: return this
    return layerBackdrop(shared.list)
}

internal fun sharedHomeGlassHeight(panelTop: Int, panelHeight: Int, panelOffset: Float): Int =
    (panelTop + panelHeight + panelOffset.roundToInt()).coerceAtLeast(0)

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
            ownBackdrop = shared.list,
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
