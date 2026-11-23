package app.n_zik.android.ui.screens.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.core.coil.imageLoadingViewport
import app.n_zik.android.core.coil.rememberImageLoadingViewportPainter
import app.n_zik.android.enums.ThumbnailCoverType
import app.n_zik.android.ui.components.themed.NowPlayingSongIndicator
import app.n_zik.android.ui.components.themed.RotateThumbnailCoverAnimationModern
import app.n_zik.android.ui.styling.Dimensions

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PlayerCarouselArtwork(
    coverPainter: Painter,
    player: Player,
    mediaItems: List<MediaItem>,
    pagerState: PagerState,
    pageIndex: Int,
    showCoverThumbnailAnimation: Boolean,
    imageCoverSize: Float,
    decorationsActive: Boolean,
    coverThumbnailAnimation: ThumbnailCoverType,
    showDragIndicator: () -> Boolean,
) {
    if (showCoverThumbnailAnimation)
        RotateThumbnailCoverAnimationModern(
            painter = coverPainter,
            isSongPlaying = player.isPlaying,
            modifier = Modifier.fillMaxSize(),
            state = pagerState,
            it = pageIndex,
            imageCoverSize = imageCoverSize,
            rotationActive = decorationsActive,
            type = coverThumbnailAnimation
        )
    else {
        val viewportPainter = rememberImageLoadingViewportPainter(coverPainter)
        Image(
            painter = viewportPainter,
            contentDescription = "",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().imageLoadingViewport(viewportPainter)
        )
        if (showDragIndicator() && pageIndex == player.currentMediaItemIndex) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NowPlayingSongIndicator(
                    mediaItems.getOrNull(
                        player.currentMediaItemIndex
                    )?.mediaId ?: "", player,
                    Dimensions.thumbnails.album
                )
            }
        }
    }
}
