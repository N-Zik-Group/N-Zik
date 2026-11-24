package app.n_zik.android.ui.screens.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import app.n_zik.android.components.player.ArtworkDisplayColors
import app.n_zik.android.components.player.CarouselButtonNavigation
import app.n_zik.android.components.player.slide.slideSettledPageEffect
import app.n_zik.android.components.player.animatePlayerArtworkScrollToPage
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.utils.playAtIndex

internal data class PlayerBackgroundArtwork(
    val mediaId: String,
    val coverUrl: String?,
    val coverRevision: Long,
    val display: ArtworkDisplayColors?,
    val palette: ColorPalette,
)

@Composable
internal fun rememberPlayerExpansionState(
    isLandscape: Boolean,
    portraitPreference: MutableState<Boolean>,
): MutableState<Boolean> {
    // Video can force landscape or replace the player composition during PiP.
    // Landscape layout changes must stay out of the persisted portrait preference.
    return if (isLandscape) remember { mutableStateOf(true) } else portraitPreference
}

private val playerTrackArtworkTransition = tween<Float>(
    durationMillis = 300,
    easing = CubicBezierEasing(.29f, 0f, .71f, 1f),
)

private val carouselTrackArtworkTransition = tween<Float>(
    durationMillis = 700,
    easing = CubicBezierEasing(.42f, 0f, .58f, 1f),
)

@Composable
@androidx.annotation.OptIn(UnstableApi::class)
fun PagerState.LaunchedEffectScrollToPage(
    player: androidx.media3.common.Player,
    playerUpdateTrigger: Int,
    index: Int,
    appRunningInBackground: Boolean,
    playerContentVisible: Boolean = true,
    skipAnimation: Boolean = false,
    paceArtworkMotion: Boolean = false,
    onSynchronized: (Int) -> Unit = {},
) {
    val pagerState = this
    var synchronized by remember(pagerState) { mutableStateOf(false) }
    LaunchedEffect(playerUpdateTrigger, index) {
        val animate = synchronized && !appRunningInBackground && playerContentVisible && !skipAnimation
        synchronized = true
        val targetIndex = player.currentMediaItemIndex
        try {
            if (pagerState.currentPage != targetIndex || pagerState.targetPage != targetIndex ||
                (!pagerState.isScrollInProgress && kotlin.math.abs(pagerState.currentPageOffsetFraction) > 0.001f)
            ) {
                when {
                    !animate -> pagerState.scrollToPage(targetIndex)
                    paceArtworkMotion -> pagerState.animatePlayerArtworkScrollToPage(
                        targetIndex, carouselTrackArtworkTransition, { player.currentMediaItemIndex },
                    )
                    else -> pagerState.animateScrollToPage(targetIndex, animationSpec = playerTrackArtworkTransition)
                }
            }
        } finally {
            onSynchronized(index)
        }
    }
}

/** Binds a mounted artwork pager to the current transport and to settled user swipes. */
@Composable
@androidx.annotation.OptIn(UnstableApi::class)
internal fun PagerState.AttachPlayerArtworkPager(
    player: Player,
    playerUpdateTrigger: Int,
    appRunningInBackground: Boolean,
    playerContentVisible: Boolean,
    skipAnimation: Boolean = false,
    settleDelayMillis: Long = 0L,
    carouselButtons: CarouselButtonNavigation? = null,
) {
    LaunchedEffectScrollToPage(
        player,
        playerUpdateTrigger,
        player.currentMediaItemIndex,
        appRunningInBackground,
        playerContentVisible = playerContentVisible,
        skipAnimation = skipAnimation,
        paceArtworkMotion = true,
    )
    slideSettledPageEffect(
        currentMediaItemIndex = { player.currentMediaItemIndex },
        playAtIndex = { index ->
            if (carouselButtons?.allowGestureCommit(index) != false) player.playAtIndex(index)
        },
        delayMillis = settleDelayMillis,
        transportMoveInProgress = { carouselButtons?.isMoving == true },
    )
}
