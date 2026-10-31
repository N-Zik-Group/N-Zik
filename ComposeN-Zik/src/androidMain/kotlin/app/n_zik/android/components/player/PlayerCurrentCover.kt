package app.n_zik.android.components.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.core.coil.ImageCacheFactory
import coil3.compose.asPainter
import kotlin.math.sqrt

/** A media ID alone is insufficient when metadata refreshes the artwork URI. */
internal data class PlayerCoverIdentity(val mediaId: String, val artworkUri: String)

internal data class PlayerCoverFrame<T>(val identity: PlayerCoverIdentity, val image: T)

/** Keeps the displayed frame until the requested replacement succeeds; stale requests cannot win. */
internal class PlayerCoverFrameState<T> {
    private var requested: PlayerCoverIdentity? = null
    var displayed by mutableStateOf<PlayerCoverFrame<T>?>(null)
        private set

    fun request(identity: PlayerCoverIdentity) {
        requested = identity
    }

    fun complete(identity: PlayerCoverIdentity, image: T) {
        if (identity == requested) displayed = PlayerCoverFrame(identity, image)
    }
}

/** One committed cover frame serves both the visible background and its enclosing glass capture. */
@Composable
fun PlayerCurrentCover(
    mediaId: String,
    artworkUri: String,
    blurAdjuster: BlurAdjuster,
    showThumbnail: Boolean,
    noBlur: Boolean,
    isShowingLyrics: Boolean,
    isShowingVisualizer: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val identity = PlayerCoverIdentity(mediaId, artworkUri)
    val frames = remember { PlayerCoverFrameState<Painter>() }
    frames.request(identity)
    val fallback = painterResource(R.drawable.ic_launcher_box)
    if (artworkUri.isBlank() || artworkUri == "null") {
        LaunchedEffect(identity) { frames.complete(identity, fallback) }
    } else key(identity) {
        ImageCacheFactory.Painter(
            thumbnailUrl = artworkUri,
            onSuccess = { frames.complete(identity, it.result.image.asPainter(context)) },
            onError = { frames.complete(identity, fallback) },
        )
    }
    val frame = frames.displayed
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val squareSize = maxOf(maxWidth, maxHeight)
        val scale = sqrt(maxWidth.value * maxWidth.value + maxHeight.value * maxHeight.value) / squareSize.value
        val blurRadius = if (showThumbnail || (isShowingLyrics && !isShowingVisualizer) || !noBlur)
            blurAdjuster.strength else 0f
        val angle by rememberInfiniteTransition(label = "currentCoverRotation").animateFloat(
            initialValue = 0f,
            targetValue = if (blurAdjuster.isCoverRotating) 360f else 0f,
            animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing)),
            label = "currentCoverAngle",
        )
        Box(Modifier.requiredSize(squareSize)) {
            if (frame != null) Image(
                painter = frame.image,
                contentDescription = stringResource(R.string.cd_blurred_background),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .then(if (blurRadius > 0f) Modifier.blur(blurRadius.dp) else Modifier)
                    .graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = angle },
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(
            alpha = (blurAdjuster.backdrop / 100f).coerceIn(0f, 1f),
        )))
    }
}
