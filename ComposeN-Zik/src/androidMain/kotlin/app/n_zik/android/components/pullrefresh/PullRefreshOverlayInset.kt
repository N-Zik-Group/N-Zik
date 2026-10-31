package app.n_zik.android.components.pullrefresh

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.PlayerPosition
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.currentMediaItemIdAsState
import app.it.fast4x.rimusic.utils.playerPositionKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.LocalTopBarOffset
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.navigation.LocalAppHeaderContentInset
import app.n_zik.android.components.navigation.LocalAppHeaderOverlayInset
import app.n_zik.android.components.navigation.hidePageBarsInLandscapeMobile
import app.n_zik.android.components.navigation.dock.FloatingDockDimensions
import app.n_zik.android.components.navigation.dock.LocalFloatingDock
import app.n_zik.android.components.navigation.nav.GlassNavigationBarHeight
import app.n_zik.android.components.player.MINI_PLAYER_TOP_GAP
import app.n_zik.android.components.player.TOP_NAV_BAR_HEIGHT
import app.n_zik.android.components.player.miniPlayerTopDockPaddingPx
import app.n_zik.android.components.player.miniPlayerTopInset
import app.n_zik.android.components.player.miniPlayerTopPaddingPx
import app.n_zik.android.components.player.rememberAnimatedMiniPlayerTopInset
import app.n_zik.android.components.ui.screens.home.LocalHomeTopMiniPlayerAnchor

/** Unlike scrolling start padding, the refresh affordance must clear visible chrome at every frame. */
internal fun pullRefreshOverlayInsetPx(
    headerBottomPx: Float,
    topNavHeightPx: Float,
    miniTopPx: Float? = null,
    miniHeightPx: Float = 0f,
): Float = maxOf(
    headerBottomPx.coerceAtLeast(0f) + topNavHeightPx.coerceAtLeast(0f),
    if (miniTopPx != null) miniTopPx + miniHeightPx.coerceAtLeast(0f) else 0f,
)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun rememberPullRefreshOverlayInsetProvider(viewportTopPx: () -> Float = { 0f }): () -> Dp {
    val contentInset = LocalAppHeaderContentInset.current
    val headerInset = LocalAppHeaderOverlayInset.current
    val homeHeader = LocalHomeTopMiniPlayerAnchor.current
    val density = LocalDensity.current
    val statusTop = WindowInsets.safeDrawing.getTop(density).toFloat()
    val navigation = NavigationBarPosition.current()
    val glassNav = rememberGlassSettings().usesGlassMaterial(GlassArea.Nav)
    val dock = LocalFloatingDock.current
    val position by rememberPreference(playerPositionKey, PlayerPosition.Bottom)
    val player = LocalPlayerServiceBinder.current?.player
    val mediaId by (player?.currentMediaItemIdAsState() ?: remember { mutableStateOf<String?>(null) })
    val headerOffset = LocalTopBarOffset.current
    val collapsedHeight = with(density) { Dimensions.collapsedPlayer.toPx() }
    val barsHidden = hidePageBarsInLandscapeMobile()
    val hasTopNav = !barsHidden && navigation == NavigationBarPosition.Top && dock?.present == true
    val navHeight = with(density) {
        if (hasTopNav) (if (glassNav) GlassNavigationBarHeight else TOP_NAV_BAR_HEIGHT).toPx() else 0f
    }
    val headerHeight = with(density) { (contentInset().toPx() - statusTop).coerceAtLeast(0f) }
    val miniInset = rememberAnimatedMiniPlayerTopInset(with(density) {
        miniPlayerTopInset(statusTop.toDp(), barsHidden, hasTopNav,
            headerHeight = headerHeight.toDp(), topNavBarHeight = navHeight.toDp()).toPx()
    })
    val currentProvider by rememberUpdatedState<() -> Dp> {
        // Other routes still position their entire viewport below chrome.
        val homeBottom = homeHeader?.headerOverlayBottom()
        if (contentInset() <= 0.dp && homeBottom == null) 0.dp else with(density) {
            val fold = (dock?.progress?.value ?: 0f).coerceIn(0f, 1f)
            val miniHeight = (FloatingDockDimensions.expandedMiniHeight * (1f - fold) +
                FloatingDockDimensions.compactHeight * fold).dp.toPx()
            val miniTop = if (position == PlayerPosition.Top && mediaId != null) {
                if (hasTopNav && dock?.hasAttachedMini == true) miniPlayerTopDockPaddingPx(
                    miniInset.value, headerOffset.value, headerHeight, navHeight, miniHeight,
                    MINI_PLAYER_TOP_GAP.toPx(), dock.navigationProgress,
                ) else miniPlayerTopPaddingPx(
                    miniInset.value, headerOffset.value, headerHeight, collapsedHeight,
                    retainedTopInsetPx = miniInset.value - headerHeight,
                )
            } else null
            val chromeBottom = maxOf(
                pullRefreshOverlayInsetPx(headerInset().toPx(), navHeight, miniTop, miniHeight),
                homeBottom?.toPx() ?: 0f,
            )
            pullRefreshLocalInsetPx(chromeBottom, viewportTopPx()).toDp()
        }
    }
    return remember { { currentProvider() } }
}

/** Indicator translation is local to its refresh viewport, while chrome geometry is absolute. */
internal fun pullRefreshLocalInsetPx(chromeBottomPx: Float, viewportTopPx: Float): Float =
    (chromeBottomPx - viewportTopPx).coerceAtLeast(0f)
