package app.n_zik.android.components.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
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
import app.n_zik.android.cleanPrefix
import app.n_zik.android.utils.cropVideoThumbnailsKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.isVideoArtwork
import app.n_zik.android.core.coil.rememberDisplayArtworkSource
import coil3.compose.asPainter
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/** Local artwork can be replaced in place while the song and its URI remain the same. */
internal data class PlayerCoverIdentity(val mediaId: String, val artworkUri: String, val localRevision: Long = 0L)

@Composable
internal fun rememberPlayerCoverIdentity(mediaId: String, artworkUri: String): PlayerCoverIdentity {
    val source = rememberDisplayArtworkSource(artworkUri)
    val revision = ImageCacheFactory.rememberLocalArtworkRevision(source)
    return remember(mediaId, artworkUri, revision) { PlayerCoverIdentity(mediaId, artworkUri, revision) }
}

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

/** Publishes only after the decoded painter and the visible layout have both reached a frame. */
@Composable
internal fun ReportPresentedPlayerCover(
    identity: PlayerCoverIdentity,
    ready: Boolean,
    active: Boolean,
    onPresented: () -> Unit,
) {
    val report by rememberUpdatedState(onPresented)
    LaunchedEffect(identity, ready, active) {
        if (ready && active) {
            withFrameNanos { }
            withFrameNanos { }
            report()
        }
    }
}

internal const val PLAYER_COVER_FADE_MS = 500

@Composable
@OptIn(ExperimentalAnimationApi::class)
internal fun PlayerCoverCrossfade(
    frame: PlayerCoverFrame<Painter>,
    modifier: Modifier = Modifier,
    onSettled: ((Boolean) -> Unit)? = null,
) {
    val cropVideo by rememberPreference(cropVideoThumbnailsKey, true)
    val transition = updateTransition(frame, label = "playerCoverFade")
    if (onSettled != null) {
        val settled = transition.currentState == frame && !transition.isRunning
        SideEffect { onSettled(settled) }
    }
    transition.Crossfade(
        modifier = modifier,
        animationSpec = tween(PLAYER_COVER_FADE_MS, easing = FastOutSlowInEasing),
    ) { visible ->
        val source = rememberDisplayArtworkSource(visible.identity.artworkUri)
        Image(
            painter = visible.image,
            contentDescription = stringResource(R.string.accessibility_blurred_background),
            // The theme covers the whole rotation-safe surface; a fitted video leaves
            // empty strips above and below the player even when its foreground is cropped.
            contentScale = if (cropVideo && isVideoArtwork(source)) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** One committed cover frame serves both the visible background and its enclosing glass capture. */
@Composable
internal fun PlayerCurrentCover(
    mediaId: String,
    artworkUri: String,
    blurAdjuster: BlurAdjuster,
    showThumbnail: Boolean,
    noBlur: Boolean,
    isShowingLyrics: Boolean,
    isShowingVisualizer: Boolean,
    modifier: Modifier = Modifier,
    publishArtworkColors: Boolean = false,
    allowOwnRotation: Boolean = true,
    committedRevision: Long? = null,
    onBackdropSettled: ((PlayerCoverFrame<Painter>?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val identity = rememberPlayerCoverIdentity(mediaId, artworkUri)
    val canRequest = committedRevision == null || committedRevision == identity.localRevision
    val frames = remember { PlayerCoverFrameState<Painter>() }
    frames.request(identity)
    val fallback = painterResource(R.drawable.ic_launcher_box)
    val artworkSource = rememberDisplayArtworkSource(artworkUri)?.let(::cleanPrefix)
    if (canRequest && (artworkSource.isNullOrBlank() || artworkSource == "null")) {
        LaunchedEffect(identity) {
            if (frames.displayed != null) delay(ARTWORK_METADATA_GRACE_MS)
            frames.complete(identity, fallback)
        }
    } else if (canRequest) key(identity) {
        ImageCacheFactory.Painter(
            thumbnailUrl = artworkUri,
            onSuccess = { frames.complete(identity, it.result.image.asPainter(context)) },
            onError = { frames.complete(identity, fallback) },
            publishArtworkColors = publishArtworkColors,
        )
    }
    val frame = frames.displayed
    if (onBackdropSettled != null && frame == null) {
        SideEffect { onBackdropSettled(null) }
    }
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val squareSize = maxOf(maxWidth, maxHeight)
        val scale = sqrt(maxWidth.value * maxWidth.value + maxHeight.value * maxHeight.value) / squareSize.value
        val targetBlurRadius = if (showThumbnail || (isShowingLyrics && !isShowingVisualizer) || !noBlur)
            blurAdjuster.strength else 0f
        val targetBackdropAlpha = (blurAdjuster.backdrop / 100f).coerceIn(0f, 1f)
        val blurRadius by animateFloatAsState(
            targetValue = targetBlurRadius,
            animationSpec = tween(PLAYER_COVER_FADE_MS, easing = FastOutSlowInEasing),
            label = "playerCoverBlur",
        )
        val backdropAlpha by animateFloatAsState(
            targetValue = targetBackdropAlpha,
            animationSpec = tween(PLAYER_COVER_FADE_MS, easing = FastOutSlowInEasing),
            label = "playerCoverBackdrop",
        )
        val ownsRotation = allowOwnRotation && blurAdjuster.isCoverRotating
        val angle = rememberCoverSpin(
            active = ownsRotation && frame != null,
            playing = true,
            rotationMillis = 30_000,
            coastDegrees = 0f,
        )
        Box(Modifier.requiredSize(squareSize)) {
            if (frame != null) PlayerCoverCrossfade(
                frame = frame,
                modifier = Modifier.fillMaxSize()
                    .then(if (blurRadius > 0f) Modifier.blur(blurRadius.dp) else Modifier)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        rotationZ = if (ownsRotation) angle.value else 0f
                    },
                onSettled = onBackdropSettled?.let { report ->
                    { settled ->
                        report(frame.takeIf {
                            settled && frame.identity == identity &&
                                blurRadius == targetBlurRadius && backdropAlpha == targetBackdropAlpha
                        })
                    }
                },
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(
            alpha = backdropAlpha,
        )))
    }
}
