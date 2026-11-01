package app.n_zik.android.components.player.slide

import app.n_zik.android.components.glass.glassGlobalIndication

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.enums.ThumbnailType
import app.n_zik.android.enums.PlayerSwipeSensitivity
import app.it.fast4x.rimusic.utils.doubleShadowDrop
import app.n_zik.android.components.glass.glassImageRim
import app.n_zik.android.thumbnailShape
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged


/**
 * Carousel pager snap threshold (fraction of a page drag needed to switch the
 * track) derived from the player's finger-sensitivity setting. Landscape pages
 * are wider, so every level needs a longer deliberate drag there.
 */
fun PlayerSwipeSensitivity.carouselSnapThreshold(landscape: Boolean): Float = when (this) {
    PlayerSwipeSensitivity.VeryHigh -> if (landscape) 0.75f else 0.15f
    PlayerSwipeSensitivity.High -> if (landscape) 0.80f else 0.20f
    PlayerSwipeSensitivity.Medium -> if (landscape) 0.85f else 0.25f
    PlayerSwipeSensitivity.Low -> if (landscape) 0.90f else 0.35f
}

/** Duration used for player pager settle animations. */
const val SLIDE_SETTLE_DURATION_MS = 700

/** Keep tightly stacked pages from travelling several tracks during a small drag. */
internal class CarouselDragConnection(
    private val pageStridePx: () -> Float,
    private val sensitivity: PlayerSwipeSensitivity,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        val stride = pageStridePx().coerceAtLeast(1f)
        val dragFraction = (stride / sensitivity.threshold).coerceAtMost(1f)
        if (dragFraction == 1f) return Offset.Zero
        return Offset(0f, available.y * (1f - dragFraction))
    }
}

@Composable
fun Modifier.landscapeCarouselDrag(
    state: PagerState,
    sensitivity: PlayerSwipeSensitivity,
): Modifier = nestedScroll(remember(state, sensitivity) {
    CarouselDragConnection(
        pageStridePx = { (state.layoutInfo.pageSize + state.layoutInfo.pageSpacing).toFloat() },
        sensitivity = sensitivity,
    )
})

internal fun carouselDragDisplacement(current: Int, settled: Int, offset: Float): Float =
    ((current - settled) + offset).absoluteValue

/**
 * Wide landscape pages make the default pager fling switch the track on almost any
 * motion — even a light flick snaps past the default velocity range. This behavior
 * ignores velocity entirely: only a drag that covers at least [minDragFraction]
 * of a page moves to a neighbor track, anything weaker settles back on the
 * current page.
 */
private class GuardedPagerFlingBehavior(
    private val state: PagerState,
    private val minDragFraction: Float,
) : TargetedFlingBehavior {

    override suspend fun ScrollScope.performFling(
        initialVelocity: Float,
        onRemainingDistanceUpdated: (Float) -> Unit,
    ): Float {
        val settled = state.settledPage
        val current = state.currentPage
        val offset = state.currentPageOffsetFraction
        // The offset fraction wraps at half a page (it stays within [-0.5, 0.5]),
        // so the displacement from the pre-gesture page is only unambiguous when
        // combined with the page change itself.
        val displacement = carouselDragDisplacement(current, settled, offset)
        val targetPage = when {
            // Past the threshold, settle on the page the drag crossed into
            // (also the nearest page for multi-page drags).
            current != settled && displacement >= minDragFraction -> current
            else -> settled
        }
        state.animateScrollToPage(
            page = targetPage,
            animationSpec = tween(SLIDE_SETTLE_DURATION_MS, easing = FastOutSlowInEasing),
        )
        return 0f
    }
}

/** Single source for the player pager fling behavior. */
@Composable
@OptIn(UnstableApi::class)
fun flingBehaviorFor(
    state: PagerState,
    landscape: Boolean = false,
    sensitivity: PlayerSwipeSensitivity = PlayerSwipeSensitivity.Medium,
): TargetedFlingBehavior {
    val threshold = sensitivity.carouselSnapThreshold(landscape)
    if (!landscape) return PagerDefaults.flingBehavior(
        state = state,
        snapAnimationSpec = tween(SLIDE_SETTLE_DURATION_MS, easing = FastOutSlowInEasing),
        snapPositionalThreshold = threshold,
    )
    // Wide landscape pages: only a drag covering most of a page may switch the
    // track, whatever the page size (the stacked pages are small).
    return GuardedPagerFlingBehavior(
        state = state,
        minDragFraction = threshold,
    )
}

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
    .glassImageRim(if (showCoverThumbnailAnimation) CircleShape else thumbnailShape())
    .combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = glassGlobalIndication(null),
        onClick = { onTap(page) },
        onLongClick = { onLongClick(page) },
    )
