package app.it.fast4x.rimusic.ui.screens.player

import app.n_zik.android.core.database.*
import app.n_zik.android.ui.saveable.TolerantBoolStateSaver
import app.n_zik.android.utils.artistTextWithFallback

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.enums.ButtonState
import app.it.fast4x.rimusic.enums.PlayerControlsType
import app.it.fast4x.rimusic.enums.PlayerInfoType
import app.it.fast4x.rimusic.enums.PlayerPlayButtonType
import app.it.fast4x.rimusic.enums.PlayerTimelineSize
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.models.Info
import app.it.fast4x.rimusic.models.ui.UiMedia
import app.it.fast4x.rimusic.models.ui.toUiMedia
import app.it.fast4x.rimusic.ui.screens.player.components.controls.InfoAlbumAndArtistEssential
import app.it.fast4x.rimusic.ui.screens.player.components.controls.InfoAlbumAndArtistModern
import app.it.fast4x.rimusic.utils.GetControls
import app.it.fast4x.rimusic.utils.GetSeekBar
import app.it.fast4x.rimusic.utils.buttonzoomoutKey
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.isCompositionLaunched
import app.it.fast4x.rimusic.utils.isDownloadedSong
import app.it.fast4x.rimusic.utils.isExplicit
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.playerControlsTypeKey
import app.it.fast4x.rimusic.utils.playerInfoTypeKey
import app.it.fast4x.rimusic.utils.playerPlayButtonTypeKey
import app.it.fast4x.rimusic.utils.playerSwapControlsWithTimelineKey
import app.it.fast4x.rimusic.utils.playerTimelineSizeKey
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showlyricsthumbnailKey
import app.it.fast4x.rimusic.utils.showthumbnailKey
import app.it.fast4x.rimusic.utils.transparentBackgroundPlayerActionBarKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import app.it.fast4x.rimusic.ui.styling.ColorPalette

@ExperimentalAnimationApi
@ExperimentalFoundationApi
@UnstableApi
@ExperimentalTextApi
@Composable
fun Controls(
    navController: NavController,
    onCollapse: () -> Unit,
    onBlurScaleChange: (Float) -> Unit,
    expandedplayer: Boolean,
    titleExpanded: Boolean,
    timelineExpanded: Boolean,
    controlsExpanded: Boolean,
    isShowingLyrics: Boolean,
    mediaItem: MediaItem,
    artistIds: List<Info>?,
    albumId: String?,
    shouldBePlaying: Boolean,
    isBuffering: Boolean,
    position: () -> Long,
    duration: () -> Long,
    dynamicColorPalette: ColorPalette,
    modifier: Modifier = Modifier
) = Controls(
    navController = navController,
    onCollapse = onCollapse,
    onBlurScaleChange = onBlurScaleChange,
    expandedplayer = expandedplayer,
    titleExpanded = titleExpanded,
    timelineExpanded = timelineExpanded,
    controlsExpanded = controlsExpanded,
    isShowingLyrics = isShowingLyrics,
    media = remember(mediaItem.mediaId, duration()) {
        mediaItem.toUiMedia(duration())
    },
    mediaId = mediaItem.mediaId,
    title = cleanPrefix( mediaItem.mediaMetadata.title?.toString() ?: "" ).let { if (it == "null") "" else it },
    artist = cleanPrefix( mediaItem.artistTextWithFallback() ),
    artistIds = artistIds,
    albumId = albumId,
    shouldBePlaying = shouldBePlaying,
    isBuffering = isBuffering,
    position = position,
    duration = duration,
    isExplicit = mediaItem.isExplicit,
    dynamicColorPalette = dynamicColorPalette,
    modifier = modifier
)

@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun Controls(
    navController: NavController,
    onCollapse: () -> Unit,
    onBlurScaleChange: (Float) -> Unit,
    expandedplayer: Boolean,
    titleExpanded: Boolean,
    timelineExpanded: Boolean,
    controlsExpanded: Boolean,
    isShowingLyrics: Boolean,
    media: UiMedia,
    mediaId: String,
    title: String?,
    artist: String?,
    artistIds: List<Info>?,
    albumId: String?,
    shouldBePlaying: Boolean,
    isBuffering: Boolean,
    position: () -> Long,
    duration: () -> Long,
    isExplicit: Boolean,
    dynamicColorPalette: ColorPalette,
    modifier: Modifier = Modifier
) {
    val binder = LocalPlayerServiceBinder.current
    binder?.player ?: return

    val currentSong by remember {
        Database.songTable
                .findById( mediaId )
                .distinctUntilChanged()
    }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)

    var disableScrollingText by rememberPreference(disableScrollingTextKey, false)


    // Issue #881 (gh-881): tolerant saver (spec M5).
    var isDownloaded by rememberSaveable(saver = TolerantBoolStateSaver("controls.isDownloaded")) {
        mutableStateOf(false)
    }

    isDownloaded = isDownloadedSong(mediaId)

    var showSelectDialog by remember { mutableStateOf(false) }

    var playerTimelineSize by rememberPreference(
        playerTimelineSizeKey,
        PlayerTimelineSize.Biggest
    )

    val playerInfoType by rememberPreference(playerInfoTypeKey, PlayerInfoType.Modern)
    var playerSwapControlsWithTimeline by rememberPreference(
        playerSwapControlsWithTimelineKey,
        false
    )
    var showlyricsthumbnail by rememberPreference(showlyricsthumbnailKey, true)
    var transparentBackgroundActionBarPlayer by rememberPreference(
        transparentBackgroundPlayerActionBarKey,
        true
    )
    var playerControlsType by rememberPreference(playerControlsTypeKey, PlayerControlsType.Essential)
    var playerPlayButtonType by rememberPreference(playerPlayButtonTypeKey, PlayerPlayButtonType.CircularRibbed)
    var showthumbnail by rememberPreference(showthumbnailKey, true)
    var playerType by rememberPreference(playerTypeKey, PlayerType.Essential)
    val expandedlandscape = (isLandscape && playerType == PlayerType.Modern) || (expandedplayer && !showthumbnail)

    Box(
        modifier = Modifier
            .animateContentSize()
    ) {
        if ((!isLandscape) and ((expandedplayer || isShowingLyrics) && !showlyricsthumbnail))
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .padding(horizontal = playerTimelineSize.size.dp)
            ) {
                if (!isShowingLyrics || titleExpanded) {
                    if (playerInfoType == PlayerInfoType.Modern)
                        InfoAlbumAndArtistModern(
                            binder = binder,
                            navController = navController,
                            media = media,
                            title = title,
                            albumId = albumId,
                            mediaId = mediaId,
                            likedAt = currentSong?.likedAt,
                            onCollapse = onCollapse,
                            disableScrollingText = disableScrollingText,
                            artist = artist,
                            artistIds = artistIds,
                            isExplicit = isExplicit
                        )

                    if (playerInfoType == PlayerInfoType.Essential)
                        InfoAlbumAndArtistEssential(
                            binder = binder,
                            navController = navController,
                            media = media,
                            title = title,
                            albumId = albumId,
                            mediaId = mediaId,
                            likedAt = currentSong?.likedAt,
                            onCollapse = onCollapse,
                            disableScrollingText = disableScrollingText,
                            artist = artist,
                            artistIds = artistIds,
                            isExplicit = isExplicit
                        )
                    Spacer(
                        modifier = Modifier
                            .height(10.dp)
                    )
                }
                if (!isShowingLyrics || timelineExpanded) {
                GetSeekBar(
                    position = position,
                    duration = duration,
                    media = media,
                    mediaId = mediaId,
                    shouldBePlaying = shouldBePlaying,
                    isBuffering = isBuffering
                )
                    Spacer(
                        modifier = Modifier
                            .height(if (playerPlayButtonType != PlayerPlayButtonType.Disabled) 10.dp else 5.dp)
                    )
                }
                if (!isShowingLyrics || controlsExpanded) {
                    GetControls(
                        dynamicColorPalette = dynamicColorPalette,
                        binder = binder,
                        position = position,
                        shouldBePlaying = shouldBePlaying,
                        isBuffering = isBuffering,
                        likedAt = currentSong?.likedAt,
                        mediaId = mediaId,
                        onBlurScaleChange = onBlurScaleChange
                    )
                    Spacer(
                        modifier = Modifier
                            .height(5.dp)
                    )
                }
                if (((playerControlsType == PlayerControlsType.Modern) || (!transparentBackgroundActionBarPlayer)) && (playerPlayButtonType != PlayerPlayButtonType.Disabled)) {
                    Spacer(
                        modifier = Modifier
                            .height(10.dp)
                    )
                }
            }
        else if (!isLandscape)
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = playerTimelineSize.size.dp)
                    //.fillMaxHeight(0.40f)
            ) {

                if (playerInfoType == PlayerInfoType.Modern)
                    InfoAlbumAndArtistModern(
                        binder = binder,
                        navController = navController,
                        media = media,
                        title = title,
                        albumId = albumId,
                        mediaId = mediaId,
                        likedAt = currentSong?.likedAt,
                        onCollapse = onCollapse,
                        disableScrollingText = disableScrollingText,
                        artist = artist,
                        artistIds = artistIds,
                        isExplicit = isExplicit
                    )

                if (playerInfoType == PlayerInfoType.Essential)
                    InfoAlbumAndArtistEssential(
                        binder = binder,
                        navController = navController,
                        media = media,
                        title = title,
                        albumId = albumId,
                        mediaId = mediaId,
                        likedAt = currentSong?.likedAt,
                        onCollapse = onCollapse,
                        disableScrollingText = disableScrollingText,
                        artist = artist,
                        artistIds = artistIds,
                        isExplicit = isExplicit
                    )

                Spacer(
                    modifier = Modifier
                        .height(25.dp)
                )

                if (!playerSwapControlsWithTimeline) {
                GetSeekBar(
                    position = position,
                    duration = duration,
                    media = media,
                    mediaId = mediaId,
                    shouldBePlaying = shouldBePlaying,
                    isBuffering = isBuffering
                )
                    Spacer(
                        modifier = Modifier
                            .weight(0.4f)
                    )
                    GetControls(
                        dynamicColorPalette = dynamicColorPalette,
                        binder = binder,
                        position = position,
                        shouldBePlaying = shouldBePlaying,
                        isBuffering = isBuffering,
                        likedAt = currentSong?.likedAt,
                        mediaId = mediaId,
                        onBlurScaleChange = onBlurScaleChange
                    )
                    Spacer(
                        modifier = Modifier
                            .weight(0.5f)
                    )
                } else {
                    GetControls(
                        dynamicColorPalette = dynamicColorPalette,
                        binder = binder,
                        position = position,
                        shouldBePlaying = shouldBePlaying,
                        isBuffering = isBuffering,
                        likedAt = currentSong?.likedAt,
                        mediaId = mediaId,
                        onBlurScaleChange = onBlurScaleChange
                    )
                    Spacer(
                        modifier = Modifier
                            .weight(0.5f)
                    )
                GetSeekBar(
                    position = position,
                    duration = duration,
                    media = media,
                    mediaId = mediaId,
                    shouldBePlaying = shouldBePlaying,
                    isBuffering = isBuffering
                )
                    Spacer(
                        modifier = Modifier
                            .weight(0.4f)
                    )
                }

            }

    }
    if (isLandscape)
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Bottom,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = playerTimelineSize.size.dp)
        ) {

            if (playerInfoType == PlayerInfoType.Modern)
                InfoAlbumAndArtistModern(
                    binder = binder,
                    navController = navController,
                    media = media,
                    title = title,
                    albumId = albumId,
                    mediaId = mediaId,
                    likedAt = currentSong?.likedAt,
                    onCollapse = onCollapse,
                    disableScrollingText = disableScrollingText,
                    artist = artist,
                    artistIds = artistIds,
                    isExplicit = isExplicit
                )

            if (playerInfoType == PlayerInfoType.Essential)
                InfoAlbumAndArtistEssential(
                    binder = binder,
                    navController = navController,
                    media = media,
                    title = title,
                    albumId = albumId,
                    mediaId = mediaId,
                    likedAt = currentSong?.likedAt,
                    onCollapse = onCollapse,
                    disableScrollingText = disableScrollingText,
                    artist = artist,
                    artistIds = artistIds,
                    isExplicit = isExplicit
                )

            Spacer(
                modifier = Modifier
                    .height(if (expandedlandscape) 10.dp else 25.dp)
            )

            if (!playerSwapControlsWithTimeline) {
                GetSeekBar(
                    position = position,
                    duration = duration,
                    media = media,
                    mediaId = mediaId,
                    shouldBePlaying = shouldBePlaying,
                    isBuffering = isBuffering
                )
                Spacer(
                    modifier = Modifier
                        .animateContentSize()
                        .conditional(!expandedlandscape) { weight(0.4f) }
                        .conditional(expandedlandscape) { height(15.dp) }
                )
                GetControls(
                    dynamicColorPalette = dynamicColorPalette,
                    binder = binder,
                    position = position,
                    shouldBePlaying = shouldBePlaying, isBuffering = isBuffering,
                    likedAt = currentSong?.likedAt,
                    mediaId = mediaId,
                    onBlurScaleChange = onBlurScaleChange
                )
                Spacer(
                    modifier = Modifier
                        .animateContentSize()
                        .conditional(!expandedlandscape) { weight(0.5f) }
                        .conditional(expandedlandscape) { height(15.dp) }
                )
            } else {
                GetControls(
                    dynamicColorPalette = dynamicColorPalette,
                    binder = binder,
                    position = position,
                    shouldBePlaying = shouldBePlaying, isBuffering = isBuffering,
                    likedAt = currentSong?.likedAt,
                    mediaId = mediaId,
                    onBlurScaleChange = onBlurScaleChange
                )
                Spacer(
                    modifier = Modifier
                        .animateContentSize()
                        .conditional(!expandedlandscape) { weight(0.5f) }
                        .conditional(expandedlandscape) { height(15.dp) }
                )
                GetSeekBar(
                    position = position,
                    duration = duration,
                    media = media,
                    mediaId = mediaId,
                    shouldBePlaying = shouldBePlaying,
                    isBuffering = isBuffering
                )
                Spacer(
                    modifier = Modifier
                        .animateContentSize()
                        .conditional(!expandedlandscape) { weight(0.4f) }
                        .conditional(expandedlandscape) { height(15.dp) }
                )
            }
        }
}

fun Modifier.bounceClick() = composed {
    var buttonState by remember { mutableStateOf(ButtonState.Idle) }
    var buttonzoomout by rememberPreference(buttonzoomoutKey,true)
    val scale by animateFloatAsState(if ((buttonState == ButtonState.Pressed) && (buttonzoomout)) 0.8f else 1f)

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(buttonState) {
            awaitPointerEventScope {
                buttonState = if (buttonState == ButtonState.Pressed) {
                    waitForUpOrCancellation()
                    ButtonState.Idle
                } else {
                    awaitFirstDown(false)
                    ButtonState.Pressed
                }
            }
        }
}



