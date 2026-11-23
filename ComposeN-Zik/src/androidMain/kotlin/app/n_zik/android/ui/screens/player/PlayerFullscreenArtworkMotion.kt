package app.n_zik.android.ui.screens.player

import android.annotation.SuppressLint
import android.graphics.RenderEffect
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.n_zik.android.enums.SwipeAnimationNoThumbnail
import app.n_zik.android.utils.conditional
import kotlin.math.absoluteValue
import kotlin.math.sqrt

@SuppressLint("NewApi")
internal fun Modifier.playerFullscreenArtworkMotion(
    pagerState: PagerState,
    page: Int,
    allowRotation: Boolean,
    shouldRotate: () -> Boolean,
    rotation: State<Float>,
    rotatingCoverScale: Float,
    swipeAnimation: SwipeAnimationNoThumbnail,
    showThumbnail: Boolean,
    isDragged: Boolean,
    circleOriginY: () -> Float,
): Modifier {
    return this.conditional(allowRotation) {
        graphicsLayer {
            scaleX = if (shouldRotate()) rotatingCoverScale else 1f
            scaleY = if (shouldRotate()) rotatingCoverScale else 1f
            rotationZ = if ((page == pagerState.settledPage) && (shouldRotate())) rotation.value else 0f
        }
    }
    .conditional(swipeAnimation == SwipeAnimationNoThumbnail.Fade && !showThumbnail) {
        graphicsLayer {
            val pageOffset = pagerState.currentPageOffsetFraction
            translationX = pageOffset * size.width
            alpha = 1 - pageOffset.absoluteValue
        }
    }
    .conditional(swipeAnimation == SwipeAnimationNoThumbnail.Carousel && isDragged) { // by sinasamaki
        graphicsLayer {
            val startOffset = pagerState.startOffsetForPage(page)
            translationX = size.width * (startOffset * .99f)
            alpha = (2f - startOffset) / 2f
            val blur = (startOffset * 20f).coerceAtLeast(0.1f)
            renderEffect = RenderEffect
                .createBlurEffect(
                    blur, blur, android.graphics.Shader.TileMode.DECAL
                ).asComposeRenderEffect()
            val scale = 1f - (startOffset * .1f)
            scaleX = scale
            scaleY = scale
        }
    }
    .conditional(swipeAnimation == SwipeAnimationNoThumbnail.Circle && !showThumbnail) { // by sinasamaki
        graphicsLayer {
            val pageOffset = pagerState.offsetForPage(page)
            translationX = size.width * pageOffset

            val endOffset = pagerState.endOffsetForPage(page)
            shadowElevation = 20f

            shape = CirclePath(
                progress = 1f - endOffset.absoluteValue,
                origin = Offset(
                    size.width,
                    circleOriginY(),
                )
            )

            clip = true

            val absoluteOffset = pagerState.offsetForPage(page).absoluteValue
            val scale = 1f + (absoluteOffset.absoluteValue * .4f)

            scaleX = scale
            scaleY = scale

            val startOffset = pagerState.startOffsetForPage(page)
            alpha = (2f - startOffset) / 2f
        }
    }
}

private fun PagerState.offsetForPage(page: Int) = (currentPage - page) + currentPageOffsetFraction

private fun PagerState.startOffsetForPage(page: Int): Float {
    return offsetForPage(page).coerceAtLeast(0f)
}

private fun PagerState.endOffsetForPage(page: Int): Float {
    return offsetForPage(page).coerceAtMost(0f)
}

private class CirclePath(private val progress: Float, private val origin: Offset = Offset(0f, 0f)) : Shape {
    override fun createOutline(
        size: Size, layoutDirection: LayoutDirection, density: Density
    ): Outline {

        val center = Offset(
            x = size.center.x - ((size.center.x - origin.x) * (1f - progress)),
            y = size.center.y - ((size.center.y - origin.y) * (1f - progress)),
        )
        val radius = (sqrt(
            size.height * size.height + size.width * size.width
        ) * .5f) * progress

        return Outline.Generic(Path().apply {
            addOval(
                Rect(
                    center = center,
                    radius = radius,
                )
            )
        })
    }
}
