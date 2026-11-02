package app.n_zik.android.components.player.slide

import app.n_zik.android.components.glass.glassGlobalIndication

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import app.n_zik.android.components.player.PlayerArtworkShadowOverflow
import app.n_zik.android.components.player.registerPlayerArtworkShadow
import app.n_zik.android.thumbnailShape
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import app.n_zik.android.BuildConfig
import timber.log.Timber


/** Use the same release sensitivity in either orientation. */
fun PlayerSwipeSensitivity.carouselSnapThreshold(): Float = when (this) {
    PlayerSwipeSensitivity.VeryHigh -> 0.15f
    PlayerSwipeSensitivity.High -> 0.20f
    PlayerSwipeSensitivity.Medium -> 0.25f
    PlayerSwipeSensitivity.Low -> 0.35f
}

/** Duration used for player pager settle animations. */
const val SLIDE_SETTLE_DURATION_MS = 250

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

/** Single source for the player pager fling behavior. */
@Composable
@OptIn(UnstableApi::class)
fun flingBehaviorFor(
    state: PagerState,
    sensitivity: PlayerSwipeSensitivity = PlayerSwipeSensitivity.Medium,
): TargetedFlingBehavior {
    // PagerDefaults remembers its provider without keying snapPositionalThreshold.
    // Recreate only that provider when the user's sensitivity changes.
    return key(sensitivity) {
        PagerDefaults.flingBehavior(
            state = state,
            snapAnimationSpec = tween(SLIDE_SETTLE_DURATION_MS, easing = FastOutSlowInEasing),
            snapPositionalThreshold = sensitivity.carouselSnapThreshold(),
        )
    }
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
    val currentIndex = rememberUpdatedState(currentMediaItemIndex)
    val play = rememberUpdatedState(playAtIndex)
    LaunchedEffect(this, delayMillis) {
        observeSettledPlayerPages(snapshotFlow {
            // Drag and fling can be separate scroll mutations. An idle fractional
            // offset between them must not start playback and interrupt snapping.
            if (BuildConfig.DEBUG && !isScrollInProgress) {
                Timber.tag("PlayerPager").d(
                    "Idle current=%d settled=%d offset=%f size=%d spacing=%d forward=%s backward=%s",
                    currentPage, settledPage, currentPageOffsetFraction,
                    layoutInfo.pageSize, layoutInfo.pageSpacing, canScrollForward, canScrollBackward,
                )
            }
            settledPage to (isScrollInProgress || currentPageOffsetFraction.absoluteValue > 0.001f)
        },
            initialPage = settledPage, currentMediaItemIndex = { currentIndex.value() },
            playAtIndex = { play.value(it) }, delayMillis = delayMillis)
    }
}

/** Cancels obsolete settle requests before they can change playback. */
internal suspend fun observeSettledPlayerPages(
    pages: Flow<Pair<Int, Boolean>>,
    initialPage: Int,
    currentMediaItemIndex: () -> Int,
    playAtIndex: (Int) -> Unit,
    delayMillis: Long,
) {
    var previousPage = initialPage
    pages.distinctUntilChanged().collectLatest { (page, scrolling) ->
        if (scrolling) return@collectLatest
        val previous = previousPage
        val playing = currentMediaItemIndex()
        if (!shouldPlaySettledPage(previous, page, playing)) {
            previousPage = page
            return@collectLatest
        }
        if (delayMillis > 0L) delay(delayMillis)
        if (currentMediaItemIndex() == playing) {
            playAtIndex(page)
            previousPage = page
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
    shadowOverflow: PlayerArtworkShadowOverflow? = null,
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
    .registerPlayerArtworkShadow(
        overflow = shadowOverflow,
        shape = if (showCoverThumbnailAnimation) CircleShape else thumbnailShape(),
        alpha = {
            if (scaleAlways || carousel) thumbnailCoverTransform(
                ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue,
                alphaStart,
                scaleStart,
            ).first else 1f
        },
        zIndex = { itemZIndexFor(page, pagerState.currentPage) },
        isVisible = { carousel || pagerState.layoutInfo.visiblePagesInfo.any { it.index == page } },
        shadow = thumbnailType == ThumbnailType.Modern,
        imageOverflow = carousel,
    )
    .clip(thumbnailShape())
    .glassImageRim(if (showCoverThumbnailAnimation) CircleShape else thumbnailShape())
    .combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = glassGlobalIndication(null),
        onClick = { onTap(page) },
        onLongClick = { onLongClick(page) },
    )
