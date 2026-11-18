package app.n_zik.android.ui.components.themed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.player.rememberCoverSpin
import app.n_zik.android.core.coil.imageLoadingViewport
import app.n_zik.android.core.coil.rememberImageLoadingViewportPainter
import app.n_zik.android.enums.ThumbnailCoverType
import androidx.compose.ui.res.stringResource

private val DiscCoverShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addOval(Rect(4f, 3f, size.width - 1, size.height - 1)) })
}

@Composable
private fun RotateThumbnailCover(
    modifier: Modifier,
    rotation: State<Float>,
    painter: Painter,
    type: ThumbnailCoverType,
    imageCoverSize: Float = 50f,
) {
    Box(
        modifier = modifier
            .aspectRatio(1.0f)
            .clip(DiscCoverShape)
    ) {
        // Preserve the two pivots, artwork clip, and stationary CD hub.
        Image(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation.value }
                .aspectRatio(1.0f),
            painter = painterResource(id = when (type) {
                ThumbnailCoverType.Vinyl -> R.drawable.vinyl_background
                ThumbnailCoverType.CD, ThumbnailCoverType.CDwithCover -> R.drawable.cd
            }),
            contentDescription = stringResource(R.string.accessibility_disc_background)
        )

        if (type != ThumbnailCoverType.CD) {
            val viewportPainter = rememberImageLoadingViewportPainter(painter)
            val artworkFraction = if (type == ThumbnailCoverType.Vinyl) imageCoverSize * 0.01f else 0.85f
            Image(
                modifier = Modifier
                    .fillMaxSize(artworkFraction)
                    .graphicsLayer { rotationZ = rotation.value }
                    .aspectRatio(1.0f)
                    .align(Alignment.Center)
                    .clip(DiscCoverShape)
                    .imageLoadingViewport(viewportPainter),
                painter = viewportPainter,
                contentDescription = stringResource(R.string.accessibility_song_album_cover)
            )
        }

        if (type == ThumbnailCoverType.CDwithCover) {
            Box(
                modifier = Modifier
                    .clip(DiscCoverShape)
                    .fillMaxSize(0.3f)
                    .border(BorderStroke(2.dp, Color.LightGray), DiscCoverShape)
                    .align(Alignment.Center)
            )
            Box(
                modifier = Modifier
                    .clip(DiscCoverShape)
                    .fillMaxSize(0.2f)
                    .background(Color.LightGray)
                    .align(Alignment.Center)
            )
            Box(
                modifier = Modifier
                    .clip(DiscCoverShape)
                    .fillMaxSize(0.1f)
                    .background(Color.Black)
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
fun RotateThumbnailCoverAnimation(
    modifier: Modifier = Modifier,
    isSongPlaying: Boolean = true,
    painter: Painter,
    type: ThumbnailCoverType = ThumbnailCoverType.Vinyl,
    rotationActive: Boolean = true,
) {
    RotateThumbnailCover(
        painter = painter,
        rotation = rememberCoverSpin(rotationActive, isSongPlaying, rotationMillis = 8_000, coastDegrees = 50f),
        modifier = modifier,
        type = type
    )
}

@Composable
fun RotateThumbnailCoverAnimationModern(
    modifier: Modifier = Modifier,
    type: ThumbnailCoverType = ThumbnailCoverType.Vinyl,
    isSongPlaying: Boolean = true,
    painter: Painter,
    state: PagerState,
    it: Int,
    imageCoverSize: Float,
    rotationActive: Boolean = true,
) {
    RotateThumbnailCover(
        painter = painter,
        rotation = rememberCoverSpin(
            active = rotationActive && it == state.settledPage,
            playing = isSongPlaying,
            rotationMillis = 8_000,
            coastDegrees = 50f,
        ),
        modifier = modifier,
        type = type,
        imageCoverSize = imageCoverSize
    )
}


