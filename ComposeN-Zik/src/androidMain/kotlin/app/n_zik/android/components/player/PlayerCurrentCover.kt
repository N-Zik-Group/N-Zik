package app.n_zik.android.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.cleanPrefix
import app.n_zik.android.utils.cropVideoThumbnailsKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.isVideoArtwork
import app.n_zik.android.core.coil.rememberDisplayArtworkSource
import coil3.BitmapImage
import coil3.compose.asPainter
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/** A cover frame acknowledges the pixels for its media item, URI and source revision. */
internal data class PlayerCoverIdentity(val mediaId: String, val artworkUri: String, val sourceRevision: Long = 0L)

internal fun shouldWaitForPlayerBackdrop(
    blurredBackground: Boolean,
    expectsForegroundCover: Boolean,
    fullscreenPagerOwnsPresentation: Boolean,
    artworkUri: String?,
    backdropMatches: Boolean,
): Boolean {
    val missingArtwork = artworkUri.isNullOrBlank() || artworkUri == "null"
    return blurredBackground && !backdropMatches &&
        (missingArtwork || (!expectsForegroundCover && !fullscreenPagerOwnsPresentation))
}

@Composable
internal fun rememberPlayerCoverIdentity(mediaId: String, artworkUri: String): PlayerCoverIdentity {
    val source = rememberDisplayArtworkSource(artworkUri)
    val revision = ImageCacheFactory.rememberArtworkRevision(source)
    return remember(mediaId, artworkUri, revision) { PlayerCoverIdentity(mediaId, artworkUri, revision) }
}

internal data class PlayerCoverFrame<T>(
    val identity: PlayerCoverIdentity,
    val image: T,
    val hasOpaquePixels: Boolean = false,
)

/** Keeps the displayed frame until the requested replacement succeeds; stale requests cannot win. */
internal class PlayerCoverFrameState<T> {
    private var requested: PlayerCoverIdentity? = null
    var displayed by mutableStateOf<PlayerCoverFrame<T>?>(null)
        private set

    fun request(identity: PlayerCoverIdentity) {
        requested = identity
    }

    fun complete(identity: PlayerCoverIdentity, image: T, hasOpaquePixels: Boolean = false) {
        if (identity == requested) displayed = PlayerCoverFrame(identity, image, hasOpaquePixels)
    }
}

/** Publishes after the decoded painter and settled visible layout have both reached a frame. */
@Composable
internal fun ReportPresentedPlayerCover(
    identity: PlayerCoverIdentity,
    ready: Boolean,
    active: Boolean,
    motionSettled: Boolean = true,
    onPresented: () -> Unit,
) {
    val report by rememberUpdatedState(onPresented)
    LaunchedEffect(identity, ready, active, motionSettled) {
        if (ready && active && motionSettled) {
            withFrameNanos { }
            withFrameNanos { }
            report()
        }
    }
}

internal const val PLAYER_COVER_FADE_MS = 600

@Composable
@OptIn(ExperimentalAnimationApi::class)
internal fun PlayerCoverCrossfade(
    frame: PlayerCoverFrame<Painter>,
    modifier: Modifier = Modifier,
    onIncomingArtworkDrawn: (() -> Unit)? = null,
    onSettled: ((Boolean) -> Unit)? = null,
) {
    val cropVideo by rememberPreference(cropVideoThumbnailsKey, true)
    val source = rememberDisplayArtworkSource(frame.identity.artworkUri)
    val size = frame.image.intrinsicSize
    val fillsSquare = cropVideo && isVideoArtwork(source) || size.width > 0f && size.width == size.height
    val keepOutgoingOpaque = frame.hasOpaquePixels && fillsSquare
    val transition = updateTransition(frame, label = "playerCoverFade")
    if (onSettled != null) {
        val settled = transition.currentState == frame && !transition.isRunning
        SideEffect { onSettled(settled) }
    }
    transition.AnimatedContent(
        modifier = modifier,
        transitionSpec = {
            val duration = PLAYER_COVER_FADE_MS
            val exit = if (keepOutgoingOpaque) ExitTransition.KeepUntilTransitionsFinished
                else fadeOut(tween(duration, easing = FastOutSlowInEasing))
            (fadeIn(tween(duration, easing = FastOutSlowInEasing)) togetherWith exit).using(null)
        },
    ) { visible ->
        val source = rememberDisplayArtworkSource(visible.identity.artworkUri)
        val reportDraw = if (visible.identity == frame.identity && onIncomingArtworkDrawn != null) {
            Modifier.drawWithContent {
                drawContent()
                onIncomingArtworkDrawn()
            }
        } else Modifier
        Image(
            painter = visible.image,
            contentDescription = stringResource(R.string.accessibility_blurred_background),
            // The theme covers the whole rotation-safe surface; a fitted video leaves
            // empty strips above and below the player even when its foreground is cropped.
            contentScale = if (cropVideo && isVideoArtwork(source)) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize().then(reportDraw),
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
    presentationMediaId: String? = null,
    onArtworkPresented: ((PlayerCoverIdentity) -> Unit)? = null,
) {
    val context = LocalContext.current
    val identity = rememberPlayerCoverIdentity(mediaId, artworkUri)
    val canRequest = committedRevision == null || committedRevision == identity.sourceRevision
    val frames = remember { PlayerCoverFrameState<Painter>() }
    frames.request(identity)
    val fallback = ImageCacheFactory.rememberArtworkResourcePainter()
    val artworkSource = rememberDisplayArtworkSource(artworkUri)?.let(::cleanPrefix)
    if (canRequest && (artworkSource.isNullOrBlank() || artworkSource == "null")) {
        LaunchedEffect(identity) {
            // Revisions supplied by the player have already passed the metadata grace.
            if (committedRevision == null && frames.displayed != null) delay(ARTWORK_METADATA_GRACE_MS)
            frames.complete(identity, fallback)
        }
    } else if (canRequest) key(identity) {
        ImageCacheFactory.Painter(
            thumbnailUrl = artworkUri,
            onSuccess = {
                val image = it.result.image
                val opaque = (image as? BitmapImage)?.bitmap?.hasAlpha() == false
                frames.complete(identity, image.asPainter(context), hasOpaquePixels = opaque)
            },
            onError = { frames.complete(identity, fallback) },
            publishArtworkColors = publishArtworkColors,
            sharePlayerArtwork = true,
        )
    }
    val frame = frames.displayed
    var drawnIdentity by remember { mutableStateOf<PlayerCoverIdentity?>(null) }
    if (presentationMediaId != null && onArtworkPresented != null) {
        val presentation = identity.copy(mediaId = presentationMediaId)
        ReportPresentedPlayerCover(presentation, ready = drawnIdentity == identity, active = true) {
            onArtworkPresented(presentation)
        }
    }
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
                onIncomingArtworkDrawn = if (presentationMediaId != null &&
                    frame.identity == identity && drawnIdentity != identity
                ) {
                    { drawnIdentity = identity }
                } else null,
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
