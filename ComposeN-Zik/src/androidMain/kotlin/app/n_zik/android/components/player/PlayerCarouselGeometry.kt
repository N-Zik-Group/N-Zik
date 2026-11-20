package app.n_zik.android.components.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.enums.ThumbnailType

internal val ModernPlayerArtworkInset = 10.dp
internal const val PlayerCarouselCoverScaleStart = .9f

/** Pager dimensions fitted to the actual artwork slot, including the stacked-card decoration. */
data class PlayerCarouselGeometry(val pageSize: Dp, val coverPadding: Dp, val pageSpacing: Dp)

/** Passive replay extends a stack whose painted cards overlap at the configured spacing. */
internal fun PlayerCarouselGeometry.hasArtworkStackOverlap(thumbnailType: ThumbnailType): Boolean {
    val inset = coverPadding + if (thumbnailType == ThumbnailType.Modern) ModernPlayerArtworkInset else 0.dp
    val coverSize = pageSize - inset * 2
    val stride = pageSize + pageSpacing
    // During a one-page glide, the two nearest cards' distances add up to one page.
    val combinedHalfHeights = coverSize * ((1f + PlayerCarouselCoverScaleStart) / 2f)
    return stride > 0.dp && stride < combinedHalfHeights
}

/** Consume decorative padding and stack spread before reducing the selected visible cover. */
fun playerCarouselGeometry(
    width: Dp,
    height: Dp,
    padding: Dp,
    pageSpacing: Dp,
    compactStack: Boolean,
): PlayerCarouselGeometry {
    val page = minOf(width, height).coerceAtLeast(0.dp)
    if (!compactStack) return PlayerCarouselGeometry(page, padding, pageSpacing)
    if (height >= width) return PlayerCarouselGeometry(page, padding,
        playerCarouselBoundedStride(page + pageSpacing, page) - page)
    val visibleCover = (width - padding * 2).coerceAtLeast(0.dp)
    val fittedPadding = minOf(padding, ((page - visibleCover) / 2).coerceAtLeast(0.dp))
    val decorationFraction = if (padding > 0.dp) (fittedPadding / padding).coerceIn(0f, 1f) else 0f
    val fittedVisibleCover = (page - fittedPadding * 2).coerceAtLeast(0.dp)
    val visibleFraction = if (width > 0.dp) (fittedVisibleCover / width).coerceIn(0f, 1f) else 0f
    // Exhausted decoration must not collapse the stride: negative spacing would make
    // the pager measure hundreds of overlapping pages in a short landscape slot.
    val stride = playerCarouselBoundedStride(
        (width + pageSpacing) * maxOf(decorationFraction, visibleFraction), page)
    return PlayerCarouselGeometry(page, fittedPadding, stride - page)
}

/** Compresses extreme spacing continuously toward an eighth-page stride to keep the pager lazy. */
internal fun playerCarouselBoundedStride(stride: Dp, page: Dp): Dp {
    val minimum = maxOf(1.dp, page / 8)
    val knee = minimum * 2
    return if (stride >= knee) stride
    else minimum + minimum * (minimum / (minimum * 3 - stride))
}
