package app.it.fast4x.rimusic.ui.screens.player

import app.n_zik.android.core.database.*

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.enums.ThumbnailCoverType
import app.it.fast4x.rimusic.enums.ThumbnailType
import app.n_zik.android.playback.exceptions.LoginRequiredException
import app.n_zik.android.playback.exceptions.NoInternetException
import app.n_zik.android.playback.exceptions.PlayableFormatNonSupported
import app.n_zik.android.playback.exceptions.PlayableFormatNotFoundException
import app.n_zik.android.playback.exceptions.TimeoutException
import app.n_zik.android.playback.exceptions.UnknownException
import app.n_zik.android.playback.exceptions.UnplayableException
import app.n_zik.android.playback.exceptions.VideoIdMismatchException
import app.n_zik.android.playback.services.isDnsFailure
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.playback.services.isRawConnectionFailure
import app.n_zik.android.playback.services.isStreamResolutionOffline
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.extensions.nextvisualizer.views.NextVisualizer
import app.it.fast4x.rimusic.ui.components.themed.RotateThumbnailCoverAnimation
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.px
import app.it.fast4x.rimusic.utils.DisposableListener
import app.it.fast4x.rimusic.utils.clickOnLyricsTextKey
import app.it.fast4x.rimusic.utils.coverThumbnailAnimationKey
import app.it.fast4x.rimusic.utils.currentWindow
import app.it.fast4x.rimusic.utils.doubleShadowDrop
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.isNetworkConnected
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showCoverThumbnailAnimationKey
import app.it.fast4x.rimusic.utils.showlyricsthumbnailKey
import app.it.fast4x.rimusic.utils.showvisthumbnailKey
import app.it.fast4x.rimusic.utils.thumbnailTypeKey
import app.it.fast4x.rimusic.utils.thumbnailpauseKey
import app.it.fast4x.rimusic.utils.cropVideoThumbnailsKey

import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.n_zik.android.components.player.lyrics.LyricsScreen
import app.n_zik.android.playback.exceptions.ExplicitContentException
import androidx.media3.datasource.HttpDataSource

@ExperimentalAnimationApi
@UnstableApi
@Composable
fun Thumbnail(
    thumbnailTapEnabledKey: Boolean,
    isShowingLyrics: Boolean,
    onShowLyrics: (Boolean) -> Unit,
    isShowingStatsForNerds: Boolean,
    onShowStatsForNerds: (Boolean) -> Unit,
    isShowingVisualizer: Boolean,
    onShowEqualizer: (Boolean) -> Unit,
    onMaximize: () -> Unit,
    onDoubleTap: () -> Unit,
    showthumbnail: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val playerUpdateTrigger by binder?.playerUpdateTrigger?.collectAsStateWithLifecycle(0, context = NzikDispatchers.DATA) ?: remember { mutableStateOf(0) }
    val player = binder?.player ?: return

    val (thumbnailSizeDp, thumbnailSizePx) = Dimensions.thumbnails.player.song.let {
        it to (it - 64.dp).px
    }

    var showlyricsthumbnail by rememberPreference(showlyricsthumbnailKey, true)
    var nullableWindow by remember(playerUpdateTrigger) {
        mutableStateOf(player.currentWindow)
    }

    var error by remember {
        mutableStateOf<PlaybackException?>(player.playerError)
    }

    val localMusicFileNotFoundError = stringResource(R.string.error_local_music_not_found)
    val networkerror = stringResource(R.string.error_a_network_error_has_occurred)
    val notfindplayableaudioformaterror =
        stringResource(R.string.error_couldn_t_find_a_playable_audio_format)
    val originalvideodeletederror =
        stringResource(R.string.error_the_original_video_source_of_this_song_has_been_deleted)
    val songnotplayabledueserverrestrictionerror =
        stringResource(R.string.error_this_song_cannot_be_played_due_to_server_restrictions)
    val videoidmismatcherror =
        stringResource(R.string.error_the_returned_video_id_doesn_t_match_the_requested_one)
    val unknownplaybackerror =
        stringResource(R.string.error_an_unknown_playback_error_has_occurred)

    val unknownerror = stringResource(R.string.error_unknown)
    val nointerneterror = stringResource(R.string.error_no_internet)
    val timeouterror = stringResource(R.string.error_timeout)
    val explicterror = stringResource(R.string.parental_control_is_enabled)
    val formatUnsupported = stringResource(R.string.error_file_unsupported_format)



    val clickLyricsText by rememberPreference(clickOnLyricsTextKey, true)
    var showvisthumbnail by rememberPreference(showvisthumbnailKey, true)
    //var expandedlyrics by rememberPreference(expandedlyricsKey,false)

    player.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                nullableWindow = player.currentWindow
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                error = player.playerError
            }

            override fun onPlayerError(playbackException: PlaybackException) {
                error = playbackException
                binder.stopRadio()
                //context.stopService(context.intent<PlayerService>())
                //context.stopService(context.intent<MyDownloadService>())
            }
        }
    }

    val window = nullableWindow ?: return



    val showCoverThumbnailAnimation by rememberPreference(showCoverThumbnailAnimationKey, false)
    var coverThumbnailAnimation by rememberPreference(coverThumbnailAnimationKey, ThumbnailCoverType.Vinyl)


    AnimatedContent(
        targetState = window,
        transitionSpec = {
            val duration = 500
            val slideDirection = if (targetState.firstPeriodIndex > initialState.firstPeriodIndex)
                AnimatedContentTransitionScope.SlideDirection.Left
            else AnimatedContentTransitionScope.SlideDirection.Right

            ContentTransform(
                targetContentEnter = slideIntoContainer(
                    towards = slideDirection,
                    animationSpec = tween(duration)
                ) + fadeIn(
                    animationSpec = tween(duration)
                ) + scaleIn(
                    initialScale = 0.85f,
                    animationSpec = tween(duration)
                ),
                initialContentExit = slideOutOfContainer(
                    towards = slideDirection,
                    animationSpec = tween(duration)
                ) + fadeOut(
                    animationSpec = tween(duration)
                ) + scaleOut(
                    targetScale = 0.85f,
                    animationSpec = tween(duration)
                ),
                sizeTransform = SizeTransform(clip = false)
            )
        },
        contentAlignment = Alignment.Center, label = ""
    ) { currentWindow ->

        var artImageAvailable by remember {
            mutableStateOf(true)
        }

        val coverPainter = ImageCacheFactory.Painter(
            thumbnailUrl = currentWindow.mediaItem.mediaMetadata.artworkUri.toString(),
            onError = { 
                artImageAvailable = false 
                // Retry loading after a short delay
                NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                    delay(1000) // Wait 1 second
                    if (!artImageAvailable) {
                        // Try to preload the image
                        ImageCacheFactory.preloadImage(currentWindow.mediaItem.mediaMetadata.artworkUri.toString())
                    }
                }
            },
            onSuccess = { 
                artImageAvailable = true 
            }
        )

        val thumbnailType by rememberPreference(thumbnailTypeKey, ThumbnailType.Modern)
        val cropVideoThumbnails by rememberPreference(cropVideoThumbnailsKey, true)

        var modifierUiType by remember { mutableStateOf(modifier) }

        if (showthumbnail)
            if ((!isShowingLyrics && !isShowingVisualizer) || (isShowingVisualizer && showvisthumbnail) || (isShowingLyrics && showlyricsthumbnail))
                if (thumbnailType == ThumbnailType.Modern)
                    modifierUiType = modifier
                        .padding(vertical = 8.dp)
                        .aspectRatio(1f)
                        //.size(thumbnailSizeDp)
                        .fillMaxSize()
                        //.dropShadow(thumbnailShape(), colorPalette().overlay.copy(0.1f), 6.dp, 2.dp, 2.dp)
                        //.dropShadow(thumbnailShape(), colorPalette().overlay.copy(0.1f), 6.dp, (-2).dp, (-2).dp)
                        .doubleShadowDrop(if (showCoverThumbnailAnimation) CircleShape else thumbnailShape(), 4.dp, 8.dp)
                        //.clip(thumbnailShape())
                        .clip(if (showCoverThumbnailAnimation) CircleShape else thumbnailShape())
                //.padding(14.dp)
                else modifierUiType = modifier
                    .aspectRatio(1f)
                    //.size(thumbnailSizeDp)
                    //.padding(14.dp)
                    .fillMaxSize()
                    //.clip(thumbnailShape())
                    .clip(if (showCoverThumbnailAnimation) CircleShape else thumbnailShape())



        Box(
            modifier = modifierUiType
        ) {
            if (showthumbnail) {
                if ((!isShowingLyrics && !isShowingVisualizer) || (isShowingVisualizer && showvisthumbnail) || (isShowingLyrics && showlyricsthumbnail))
                    if (artImageAvailable) {
                        if (showCoverThumbnailAnimation)
                            RotateThumbnailCoverAnimation(
                                painter = coverPainter,
                                isSongPlaying = player.isPlaying,
                                modifier = Modifier
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onLongPress = { onShowStatsForNerds(true) },
                                            onTap = if (thumbnailTapEnabledKey) {
                                                {
                                                    onShowLyrics(true)
                                                    onShowEqualizer(false)
                                                }
                                            } else null,
                                            onDoubleTap = { onDoubleTap() }
                                        )

                                    },
                                type = coverThumbnailAnimation
                            )
                        else
                            Image (
                                painter = coverPainter,
                                contentDescription = null,
                                contentScale = if (cropVideoThumbnails) ContentScale.Crop else ContentScale.Fit,
                                modifier = Modifier
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onLongPress = { onShowStatsForNerds(true) },
                                            onTap = if (thumbnailTapEnabledKey) {
                                                {
                                                    onShowLyrics(true)
                                                    onShowEqualizer(false)
                                                }
                                            } else null,
                                            onDoubleTap = { onDoubleTap() }
                                        )

                                    }
                                    .fillMaxSize()
                                    .clip(thumbnailShape())
                            )

                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_box),
                            modifier = Modifier
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = { onShowStatsForNerds(true) },
                                        onTap = if (thumbnailTapEnabledKey) {
                                            {
                                                onShowLyrics(true)
                                                onShowEqualizer(false)
                                            }
                                        } else null,
                                        onDoubleTap = { onDoubleTap() }
                                    )

                                }
                                .fillMaxSize()
                                .clip(thumbnailShape()),
                            contentDescription = stringResource(R.string.cd_background_image),
                            contentScale = if (cropVideoThumbnails) ContentScale.Crop else ContentScale.Fit
                        )
                    }

                //if (!currentWindow.mediaItem.isLocal)
                if (showlyricsthumbnail)
                    LyricsScreen(
                        mediaId = currentWindow.mediaItem.mediaId,
                        isDisplayed = isShowingLyrics && error == null,
                        onDismiss = {
                            //if (thumbnailTapEnabledKey)
                            onShowLyrics(false)
                        },
                        ensureSongInserted = { Database.insertIgnore( currentWindow.mediaItem ) },
                        size = thumbnailSizeDp,
                        mediaMetadataProvider = currentWindow.mediaItem::mediaMetadata,
                        durationProvider = player::getDuration,
                        isLandscape = isLandscape,
                        clickLyricsText = clickLyricsText,
                    )

                StatsForNerds(
                    mediaId = currentWindow.mediaItem.mediaId,
                    isDisplayed = isShowingStatsForNerds && error == null,
                    onDismiss = { onShowStatsForNerds(false) }
                )
                if (showvisthumbnail) {
                    NextVisualizer(
                        isDisplayed = isShowingVisualizer
                    )
                }

                LaunchedEffect(error) {
                    val currentError = error
                    if (currentError != null) {
                        // S5/A3/F5a (offline gate, 2026-10-22): the service gate toasts the offline marker,
                        // DNS failures (even while the monitor reads "online") and offline raw connection failures — skip the duplicate.
                        if (isStreamResolutionOffline(currentError) ||
                            isDnsFailure(currentError) ||
                            (currentWindow.mediaItem.isLocal == false &&
                                isRawConnectionFailure(currentError) &&
                                !isNetworkConnected(context))
                        ) return@LaunchedEffect
                        Timber.tag("Thumbnail").e("Playback error: ${error?.cause?.cause}")
                        
                        var httpCode: Int? = null
                        var currentCause: Throwable? = error?.cause
                        while (currentCause != null) {
                            if (currentCause is HttpDataSource.InvalidResponseCodeException) {
                                httpCode = currentCause.responseCode
                                break
                            }
                            currentCause = currentCause.cause
                        }

                        val specificErrorString = if (currentWindow.mediaItem.isLocal)
                            localMusicFileNotFoundError
                        else if (httpCode == 403)
                            songnotplayabledueserverrestrictionerror
                        else when (error?.cause?.cause) {
                            is UnresolvedAddressException, is UnknownHostException -> networkerror
                            is PlayableFormatNotFoundException -> notfindplayableaudioformaterror
                            is UnplayableException -> originalvideodeletederror
                            is LoginRequiredException -> songnotplayabledueserverrestrictionerror
                            is VideoIdMismatchException -> videoidmismatcherror
                            is PlayableFormatNonSupported -> formatUnsupported
                            is NoInternetException -> nointerneterror
                            is TimeoutException -> timeouterror
                            is ExplicitContentException -> explicterror
                            is UnknownException -> unknownerror
                            else -> unknownplaybackerror
                        }
                        
                        if (error?.cause?.cause is ExplicitContentException) {
                            Toaster.w(specificErrorString)
                        } else {
                            Toaster.e(specificErrorString)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
fun Modifier.thumbnailpause(
    shouldBePlaying: Boolean
) = composed {
    var thumbnailpause by rememberPreference(thumbnailpauseKey, true)
    val scale by animateFloatAsState(if ((thumbnailpause) && (!shouldBePlaying)) 0.9f else 1f)

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }

}



