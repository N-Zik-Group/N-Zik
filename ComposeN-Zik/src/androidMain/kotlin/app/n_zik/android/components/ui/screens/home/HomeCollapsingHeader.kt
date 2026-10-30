package app.n_zik.android.components.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import app.n_zik.android.components.navigation.LocalSideRailContentInsets
import app.n_zik.android.components.navigation.nav.topNavigationBarShape
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.rememberGlassSettings
import kotlin.math.roundToInt

/** The four library Homes own status backing when no horizontal app header covers it. */
internal val LocalHomeTopCover = staticCompositionLocalOf { false }

/** Landscape library Homes initially reserve no horizontal navigation row. */
internal val LocalHomeLandscapeTopCover = staticCompositionLocalOf { false }

/** Only Top navigation folds the opaque space around its mini-player into a compact dock. */
internal val LocalHomeTopDockFoldProgress = staticCompositionLocalOf<() -> Float> { { 0f } }

/** Clips the moving panel, including its background and hit targets, below the top overlays. */
@Composable
internal fun HomeCollapsingHeader(
    headerOffset: MutableFloatState,
    headerHeight: MutableIntState,
    topContentInset: () -> Dp,
    modifier: Modifier = Modifier,
    backgroundColor: Color = colorPalette().background0,
    panelColor: Color = backgroundColor,
    shape: Shape = topNavigationBarShape(uiRoundnessShape()),
    content: @Composable () -> Unit,
) {
    val anchor = LocalHomeTopMiniPlayerAnchor.current
    val density = LocalDensity.current
    val owner = remember { Any() }
    val sharedGlass = LocalSharedHomeHeaderGlass.current
    val railInsets = LocalSideRailContentInsets.current
    val direction = LocalLayoutDirection.current
    val leftRail = with(density) { railInsets.getLeft(density, direction).toDp() }
    val rightRail = with(density) { railInsets.getRight(density, direction).toDp() }
    val sideGlass = sharedGlass != null && (leftRail > 0.dp || rightRail > 0.dp)
    // Bound both the optical surface and the scrolling controls, not just their layout.
    val panePadding = if (sideGlass) PaddingValues.Absolute(
        left = if (leftRail > 0.dp) leftRail + 8.dp else 0.dp,
        right = if (rightRail > 0.dp) rightRail + 8.dp else 0.dp,
    ) else PaddingValues(0.dp)
    DisposableEffect(sharedGlass, owner) {
        sharedGlass?.attach(owner)
        onDispose { sharedGlass?.detach(owner) }
    }
    DisposableEffect(anchor, owner, headerHeight, headerOffset, density) {
        anchor?.attach(
            owner,
            measuredHeight = { with(density) { headerHeight.intValue.toDp() } },
            scrollOffset = { with(density) { headerOffset.floatValue.toDp() } },
        ) {
            with(density) { visibleHomeHeaderHeightPx(headerHeight.intValue, headerOffset.floatValue).toDp() }
        }
        onDispose { anchor?.detach(owner) }
    }
    val panelTop = anchor?.headerTopInset() ?: topContentInset()
    val coverTop = LocalHomeTopCover.current
    val topDockFoldProgress = LocalHomeTopDockFoldProgress.current
    if (sharedGlass == null && (coverTop || anchor?.miniPrecedesHeader() == true)) {
        val top = if (coverTop) 0.dp else topContentInset()
        Box(Modifier
            .zIndex(1f)
            .offset { IntOffset(0, with(density) { top.roundToPx() }) }
            .fillMaxWidth()
            .height((panelTop - top).coerceAtLeast(0.dp))
            .graphicsLayer {
                val toolbarAlpha = if (coverTop) {
                    homeHeaderVisibleFraction(headerHeight.intValue, headerOffset.floatValue)
                } else 1f
                alpha = toolbarAlpha * (1f - topDockFoldProgress().coerceIn(0f, 1f))
            }
            .background(backgroundColor))
    }
    val panelTopPx = with(density) { panelTop.roundToPx() }
    val panelOffsetPx = { panelTopPx + headerOffset.floatValue.roundToInt() }
    if (sharedGlass != null) {
        SharedHomeHeaderPane(
            shared = sharedGlass,
            settings = rememberGlassSettings(),
            filmColor = backgroundColor,
            shape = shape,
            heightPx = {
                val height = sharedGlass.animatedPanelHeight.value
                val visible = homeHeaderVisibleFraction(headerHeight.intValue, headerOffset.floatValue)
                if (sharedGlass.topNavigation && sharedGlass.navigationBottomPx > 0) {
                    sharedTopHomeGlassHeight(sharedGlass.navigationBottomPx, panelTopPx, height, visible)
                } else sharedHomeGlassHeight(panelTopPx, height, -height * (1f - visible))
            },
            opacity = {
                if (sharedGlass.topNavigation && sharedGlass.navigationBottomPx > 0) 1f
                else homeHeaderVisibleFraction(headerHeight.intValue, headerOffset.floatValue)
            },
            modifier = Modifier.zIndex(1f).padding(panePadding),
            upperFilmColor = if (sharedGlass.topNavigation) surfaceColor(colorPalette().background1) else null,
            upperHeightPx = { sharedGlass.navigationBottomPx },
        )
    }
    // Move the panel and its opaque corner mask together. Shrinking a viewport
    // around stationary content clipped the icons before the toolbar left the screen.
    if (sharedGlass == null) Box(Modifier
        .zIndex(1f)
        .offset { IntOffset(0, panelOffsetPx()) }
        .fillMaxWidth()
        .height(with(density) { headerHeight.intValue.toDp() })
        .graphicsLayer {
            alpha = homeHeaderVisibleFraction(headerHeight.intValue, headerOffset.floatValue)
        }
        .background(backgroundColor))
    Box(
        modifier
            .zIndex(1f)
            .offset { IntOffset(0, panelOffsetPx()) }
            .graphicsLayer {
                alpha = homeHeaderVisibleFraction(headerHeight.intValue, headerOffset.floatValue)
            }
            .padding(panePadding)
            .clip(shape)
            .then(if (sharedGlass == null) Modifier.background(panelColor) else Modifier)
            .onSizeChanged {
                headerHeight.intValue = it.height
                sharedGlass?.measuredPanelHeight?.intValue = it.height
            }
    ) {
        Box(
            Modifier
                .padding(if (sideGlass) PaddingValues(0.dp) else railInsets.asPaddingValues())
        ) {
            CompositionLocalProvider(
                LocalGlassContainerBackdrop provides (sharedGlass?.surface ?: LocalGlassContainerBackdrop.current),
                LocalGlassBackdrop provides (sharedGlass?.surface ?: LocalGlassBackdrop.current),
            ) { content() }
        }
    }
}
