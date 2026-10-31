package app.n_zik.android.components.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Pager dimensions fitted to the actual artwork slot, including the stacked-card decoration. */
data class PlayerCarouselGeometry(val pageSize: Dp, val coverPadding: Dp, val pageSpacing: Dp)

/** Consume decorative padding and stack spread before reducing the selected visible cover. */
fun playerCarouselGeometry(
    width: Dp,
    height: Dp,
    padding: Dp,
    pageSpacing: Dp,
    compactStack: Boolean,
): PlayerCarouselGeometry {
    val page = minOf(width, height).coerceAtLeast(0.dp)
    if (!compactStack || height >= width) return PlayerCarouselGeometry(page, padding, pageSpacing)
    val visibleCover = (width - padding * 2).coerceAtLeast(0.dp)
    val fittedPadding = minOf(padding, ((page - visibleCover) / 2).coerceAtLeast(0.dp))
    val decorationFraction = if (padding > 0.dp) (fittedPadding / padding).coerceIn(0f, 1f) else 0f
    // Scaling a negative spacing alone increases the distance between cards.
    // Fit their center-to-center stride instead, keeping the pager's stride positive.
    val stride = ((width + pageSpacing) * decorationFraction).coerceAtLeast(1.dp)
    return PlayerCarouselGeometry(page, fittedPadding, stride - page)
}
