package app.n_zik.android.components.player.slide

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.enums.ThumbnailType
import app.it.fast4x.rimusic.utils.doubleShadowDrop
import app.n_zik.android.thumbnailShape
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged


/** Unified positional threshold for player pager flings. */
const val SLIDE_SNAP_POSITIONAL_THRESHOLD = 0.25f

/** Duration used for player pager settle animations. */
const val SLIDE_SETTLE_DURATION_MS = 700

/**
 * Single source for the player pager fling behavior.
 */
@Composable
@OptIn(UnstableApi::class)
fun flingBehaviorFor(state: PagerState) =
    PagerDefaults.flingBehavior(
        state = state,
        snapAnimationSpec = tween(SLIDE_SETTLE_DURATION_MS, easing = FastOutSlowInEasing),
        snapPositionalThreshold = SLIDE_SNAP_POSITIONAL_THRESHOLD,
    )

/**
 * Returns true when a settled page change should trigger playback.
 */
fun shouldPlaySettledPage(
    previousPage: Int,
    settledPage: Int,
    currentMediaItemIndex: Int,
): Boolean = previousPage != settledPage && settledPage != currentMediaItemIndex

/**
 * Observes the settled page and plays the newly settled track.
 */
@Composable
fun PagerState.slideSettledPageEffect(
    currentMediaItemIndex: () -> Int,
    playAtIndex: (Int) -> Unit,
    delayMillis: Long = 0L,
) {
    LaunchedEffect(this) {
        var previousPage = settledPage
        snapshotFlow { settledPage }.distinctUntilChanged().collect {
            if (previousPage != it) {
                if (delayMillis > 0L) delay(delayMillis)
                if (shouldPlaySettledPage(previousPage, it, currentMediaItemIndex())) {
                    playAtIndex(it)
                }
            }
            previousPage = it
        }
    }
}

/**
 * Shared z-index mapping for player thumbnails.
 */
fun itemZIndexFor(page: Int, currentPage: Int): Float = when {
    page == currentPage -> 1f
    page == currentPage + 1 || page == currentPage - 1 -> 0.85f
    page == currentPage + 2 || page == currentPage - 2 -> 0.78f
    page == currentPage + 3 || page == currentPage - 3 -> 0.73f
    page == currentPage + 4 || page == currentPage - 4 -> 0.68f
    page == currentPage + 5 || page == currentPage - 5 -> 0.63f
    else -> 0.57f
}

/**
 * Shared transform values for player thumbnails.
 */
fun thumbnailCoverTransform(pageOffset: Float, alphaStart: Float, scaleStart: Float): Triple<Float, Float, Float> {
    val alphaFraction = 1f - pageOffset.coerceIn(0f, 1f)
    val scaleFraction = 1f - pageOffset.coerceIn(0f, 5f)
    return Triple(
        lerp(alphaStart, 1f, alphaFraction),
        lerp(scaleStart, 1f, scaleFraction),
        lerp(scaleStart, 1f, scaleFraction),
    )
}

@Composable
fun Modifier.slideConditional(
    condition: Boolean,
    modifier: @Composable Modifier.() -> Modifier,
): Modifier = if (condition) {
    then(modifier(Modifier))
} else {
    this
}

/**
 * Shared item modifier chain for the Modern player thumbnails.
 */
@Composable
fun Modifier.playerThumbnailCover(
    pagerState: PagerState,
    page: Int,
    padding: Dp,
    carousel: Boolean,
    scaleAlways: Boolean,
    scaleStart: Float,
    alphaStart: Float = scaleStart,
    thumbnailType: ThumbnailType,
    showCoverThumbnailAnimation: Boolean,
    onTap: (Int) -> Unit,
    onLongClick: (Int) -> Unit,
): Modifier = this
    .aspectRatio(1f)
    .padding(all = padding)
    .slideConditional(scaleAlways || carousel) {
        graphicsLayer {
            val pageOffset =
                ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            val (alphaValue, scaleYValue, scaleXValue) = thumbnailCoverTransform(pageOffset, alphaStart, scaleStart)
            alpha = alphaValue
            scaleY = scaleYValue
            scaleX = scaleXValue
        }
    }
    .slideConditional(thumbnailType == ThumbnailType.Modern) {
        padding(all = 10.dp)
    }
    .slideConditional(thumbnailType == ThumbnailType.Modern) {
        doubleShadowDrop(
            if (showCoverThumbnailAnimation) CircleShape else thumbnailShape(),
            4.dp,
            8.dp,
        )
    }
    .clip(thumbnailShape())
    .combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = { onTap(page) },
        onLongClick = { onLongClick(page) },
    )
