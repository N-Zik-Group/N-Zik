package app.n_zik.android.components.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.PlayerPosition
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.currentMediaItemIdAsState
import app.it.fast4x.rimusic.utils.playerPositionKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.navigation.nav.GlassNavigationBarHeight
import app.n_zik.android.components.player.MINI_PLAYER_CONTENT_GAP
import app.n_zik.android.components.player.MINI_PLAYER_TOP_GAP
import app.n_zik.android.components.player.TOP_NAV_BAR_HEIGHT
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/** Header clearance belongs inside migrated scroll owners, rather than their viewport. */
internal val LocalAppHeaderContentInset = staticCompositionLocalOf<() -> Dp> { { 0.dp } }

/** Library Home starts with landscape chrome hidden; its transient reveal must not move rows. */
internal val LocalAppHeaderInitialContentInset = staticCompositionLocalOf<() -> Dp> { { 0.dp } }

/** Overlay motion is independent of the initial clearance inside scrolling content. */
internal val LocalAppHeaderOverlayInset = staticCompositionLocalOf<() -> Dp> { { 0.dp } }

/** Landscape Home can remove its header by toggle; scrolling alone must not resize content. */
internal fun appHeaderContentInsetPx(
    headerRowHeightPx: Float,
    statusBarTopPx: Float,
    landscapeToggleOwnsHeader: Boolean,
    headerProgress: Float,
): Float = statusBarTopPx.coerceAtLeast(0f) + headerRowHeightPx.coerceAtLeast(0f) *
    if (landscapeToggleOwnsHeader) headerProgress.coerceIn(0f, 1f) else 1f

internal fun appHeaderOverlayTopPx(headerRowHeightPx: Float, statusBarTopPx: Float, headerOffsetPx: Float): Float =
    statusBarTopPx.coerceAtLeast(0f) + (headerRowHeightPx + headerOffsetPx).coerceIn(0f, headerRowHeightPx.coerceAtLeast(0f))

/** At rest content clears the overlays; scrolling lets the actual page fill their backdrop. */
internal fun topOverlayClearancePx(navHeightPx: Float, miniReservePx: Float, fold: Float): Float =
    (navHeightPx.coerceAtLeast(0f) + miniReservePx.coerceAtLeast(0f)) * (1f - fold.coerceIn(0f, 1f))

/** Initial clearance belongs to the scroll and never follows overlay hiding or squeezing. */
internal fun topOverlayContentClearancePx(
    navHeightPx: Float,
    miniReservePx: Float,
): Float = navHeightPx.coerceAtLeast(0f) + miniReservePx.coerceAtLeast(0f)

/** Read motion during layout, without adding a painted or interactive overlay surface. */
internal fun Modifier.topOverlayViewport(clearancePx: () -> Float): Modifier = layout { measurable, constraints ->
    val reserve = clearancePx().roundToInt().coerceIn(0, constraints.maxHeight)
    val placeable = measurable.measure(constraints.copy(
        minHeight = (constraints.minHeight - reserve).coerceAtLeast(0),
        maxHeight = (constraints.maxHeight - reserve).coerceAtLeast(0),
    ))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(0, reserve)
    }
}

/** One clearance source for either the legacy viewport or a screen's scrolling content. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun rememberTopContentInsetProvider(
    hasNavBar: Boolean,
    stableHomeScroll: Boolean = false,
): () -> Dp {
    val headerContentInset = if (stableHomeScroll) {
        LocalAppHeaderInitialContentInset.current
    } else LocalAppHeaderContentInset.current
    val navigationPosition = NavigationBarPosition.current()
    val glass = rememberGlassSettings().usesGlassMaterial(GlassArea.Nav)
    val position by rememberPreference(playerPositionKey, PlayerPosition.Bottom)
    val player = LocalPlayerServiceBinder.current?.player
    val mediaId by (player?.currentMediaItemIdAsState() ?: remember { mutableStateOf<String?>(null) })
    val density = LocalDensity.current
    val hideNavBar = if (stableHomeScroll) {
        app.n_zik.android.components.ui.screens.home.LocalHomeLandscapeTopCover.current
    } else hidePageBarsInLandscapeMobile()
    val topNavHeight = if (hasNavBar && !hideNavBar && navigationPosition == NavigationBarPosition.Top) {
        with(density) { (if (glass) GlassNavigationBarHeight else TOP_NAV_BAR_HEIGHT).roundToPx().toFloat() }
    } else 0f
    val fullMiniReserve = with(density) {
        (Dimensions.collapsedPlayer + MINI_PLAYER_TOP_GAP + MINI_PLAYER_CONTENT_GAP).toPx()
    }
    val miniReserve = animateFloatAsState(
        targetValue = if (position == PlayerPosition.Top && mediaId != null) fullMiniReserve else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "playerTopReserve",
    )
    // Animation values are sampled only by layout / LazyColumn's padding measurement.
    val currentProvider by rememberUpdatedState<() -> Dp> {
        with(density) {
            headerContentInset() + topOverlayContentClearancePx(
                navHeightPx = topNavHeight,
                miniReservePx = miniReserve.value,
            ).toDp()
        }
    }
    return remember { { currentProvider() } }
}

/** The leading space belongs to the scroll, so rows can pass behind the top overlays. */
internal class TopOverlayContentPadding(
    private val topInset: () -> Dp,
    private val base: PaddingValues = PaddingValues(0.dp),
) : PaddingValues {
    override fun calculateTopPadding(): Dp = base.calculateTopPadding() + topInset()
    override fun calculateBottomPadding(): Dp = base.calculateBottomPadding()
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = base.calculateLeftPadding(layoutDirection)
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = base.calculateRightPadding(layoutDirection)
}
