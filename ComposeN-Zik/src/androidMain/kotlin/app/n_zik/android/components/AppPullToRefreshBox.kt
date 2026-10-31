@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package app.n_zik.android.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.n_zik.android.components.pullrefresh.rememberPullRefreshOverlayInsetProvider

@Composable
fun AppPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val pullRefreshState = rememberPullToRefreshState()
    val colorPalette = LocalAppearance.current.colorPalette
    var viewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val indicatorInset = rememberPullRefreshOverlayInsetProvider {
        viewportCoordinates?.takeIf { it.isAttached }?.positionInRoot()?.y ?: 0f
    }
    val density = LocalDensity.current

    PullToRefreshBox(
        state = pullRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.onPlaced { viewportCoordinates = it },
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).offset {
                    IntOffset(0, with(density) { indicatorInset().roundToPx() })
                },
                containerColor = colorPalette.accent,
                color = if (colorPalette.isDark) Color.White else Color.Black
            )
        },
        content = content
    )
}
