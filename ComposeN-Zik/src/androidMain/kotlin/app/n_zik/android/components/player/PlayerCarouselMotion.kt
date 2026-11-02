package app.n_zik.android.components.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp

/** Keeps height-changing expansion animated while applying a resized viewport width immediately. */
@Composable
fun animatePlayerCarouselDp(
    targetValue: Dp,
    viewportWidth: Dp,
    label: String,
): State<Dp> = key(viewportWidth) {
    animateDpAsState(targetValue, animationSpec = tween(250), label = label)
}

/** Interpolates fitted stack geometry, including the compact-mode formula switch. */
@Composable
fun animatePlayerCarouselGeometry(
    width: Dp,
    height: Dp,
    padding: Dp,
    pageSpacing: Dp,
    compactStack: Boolean,
): PlayerCarouselGeometry {
    val progress = key(width) {
        animateFloatAsState(if (compactStack) 1f else 0f, tween(250), label = "carouselStack")
    }.value
    val plain = playerCarouselGeometry(width, height, padding, pageSpacing, false)
    val fitted = playerCarouselGeometry(width, height, padding, pageSpacing, true)
    val animatedPadding = lerp(plain.coverPadding, fitted.coverPadding, progress)
    // Bound the unstacked endpoint before interpolation. Bounding the interpolated result
    // would compress the fitted endpoint a second time and distort live slider changes.
    val plainSpacing = if (compactStack || progress > 0f)
        playerCarouselBoundedStride(plain.pageSize + plain.pageSpacing, plain.pageSize) - plain.pageSize
    else plain.pageSpacing
    return PlayerCarouselGeometry(plain.pageSize, animatedPadding,
        lerp(plainSpacing, fitted.pageSpacing, progress))
}
