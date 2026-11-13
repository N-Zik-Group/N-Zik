package app.n_zik.android.ui.styling

import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.enums.NavigationBarType
import app.n_zik.android.LocalPlayerServiceBinder

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi

@UnstableApi
@Suppress("ClassName")
object Dimensions {
    val itemsVerticalPadding = 8.dp

    val navigationRailWidth = 50.dp
    val navigationRailWidthLandscape = 128.dp
    val navigationRailIconOffset = 6.dp
    val headerHeight = 140.dp
    val halfheaderHeight = 60.dp

    val standardNavBarHeight = 64.dp
    val floatingNavBarHeight = 84.dp
    val floatingNavBarIconOnlyHeight = 64.dp

    @Composable
    fun navBarBottomPadding(isFloating: Boolean): Dp {
        return if (isFloating) {
            with(LocalDensity.current) {
                val systemBottom = WindowInsets.systemBars.getBottom(this).toDp()
                if (systemBottom > 30.dp) {
                    systemBottom + 20.dp // Elevated slightly for buttons
                } else {
                    (systemBottom + 5.dp).coerceAtLeast(25.dp)
                }
            }
        } else {
            with(LocalDensity.current) {
                WindowInsets.systemBars.getBottom(this).toDp()
            }
        }
    }

    val miniPlayerHeight: Dp
        @Composable
        get() = if (NavigationBarPosition.BottomFloating.isCurrent()) 72.dp else 65.dp

    val collapsedPlayer: Dp
        @Composable
        get() = if (NavigationBarPosition.BottomFloating.isCurrent()) 72.dp else 65.dp
    val navigationBarHeight = 64.dp
    val contentWidthRightBar = 0.88f
    val additionalVerticalSpaceForFloatingAction = 40.dp
    
    val bottomSpacer: Dp
        @Composable
        get() {
            val isFloating = NavigationBarPosition.BottomFloating.isCurrent()
            val isIconOnly = NavigationBarType.IconOnly.isCurrent()
            val binder = LocalPlayerServiceBinder.current
            val isMiniPlayerActive = binder?.player?.currentMediaItem != null

            return if (isFloating) {
                val barHeight = if (isIconOnly) floatingNavBarIconOnlyHeight else floatingNavBarHeight
                val barBottom = navBarBottomPadding(true)
                val visualGap = 10.dp
                if (isMiniPlayerActive) {
                    barHeight + barBottom + miniPlayerHeight + visualGap
                } else {
                    barHeight + barBottom + visualGap
                }
            } else 100.dp

        }

    val fadeSpacingTop = 30.dp
    val fadeSpacingBottom = 65.dp
    val musicAnimationHeight = 20.dp

    object thumbnails {
        val album = 128.dp
        val artist = 128.dp
        val song = 54.dp
        val playlist = album

        object player {
            val song: Dp
                @Composable
                get() = with(LocalConfiguration.current) {
                    minOf(screenHeightDp, screenWidthDp)
                }.dp
        }
    }
}

inline val Dp.px: Int
    @Composable
    get() = with(LocalDensity.current) { roundToPx() }



