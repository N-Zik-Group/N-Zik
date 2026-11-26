package app.n_zik.android.ui.screens.player

import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.components.settings.appearance.preview.ThemePreviewDemo
import app.n_zik.android.components.player.PlayerInformationGlassBlock
import app.n_zik.android.components.player.PlayerInformationGlassRegion
import app.n_zik.android.ui.saveable.TolerantBoolStateSaver
import app.n_zik.android.utils.PlayerTrackLinks
import app.n_zik.android.utils.alignInfosToDisplayOrder
import app.n_zik.android.utils.resolveTrackArtistName
import app.n_zik.android.utils.resolvePlayerTrackName

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import app.n_zik.android.components.player.playerInfoAwareContentSize
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.enums.ButtonState
import app.n_zik.android.enums.PlayerControlsType
import app.n_zik.android.enums.PlayerInfoType
import app.n_zik.android.enums.PlayerPlayButtonType
import app.n_zik.android.enums.PlayerTimelineSize
import app.n_zik.android.enums.PlayerType
import app.n_zik.android.models.ui.toUiMedia
import app.n_zik.android.ui.screens.player.components.controls.InfoAlbumAndArtistEssential
import app.n_zik.android.ui.screens.player.components.controls.InfoAlbumAndArtistModern
import app.n_zik.android.utils.GetControls
import app.n_zik.android.utils.GetSeekBar
import app.n_zik.android.utils.buttonzoomoutKey
import app.n_zik.android.utils.conditional
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.isCompositionLaunched
import app.n_zik.android.utils.isDownloadedSong
import app.n_zik.android.utils.isExplicit
import app.n_zik.android.utils.isLandscape
import app.n_zik.android.utils.playerControlsTypeKey
import app.n_zik.android.utils.playerInfoTypeKey
import app.n_zik.android.utils.playerPlayButtonTypeKey
import app.n_zik.android.utils.playerSwapControlsWithTimelineKey
import app.n_zik.android.utils.playerTimelineSizeKey
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.showlyricsthumbnailKey
import app.n_zik.android.utils.showthumbnailKey
import app.n_zik.android.utils.transparentBackgroundPlayerActionBarKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import app.n_zik.android.ui.styling.ColorPalette

@ExperimentalAnimationApi
@ExperimentalFoundationApi
@UnstableApi
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@Composable
internal fun Controls(
    navController: NavController,
    onCollapse: () -> Unit,
    onBlurScaleChange: (Float) -> Unit,
    expandedplayer: Boolean,
    titleExpanded: Boolean,
    timelineExpanded: Boolean,
    controlsExpanded: Boolean,
    isShowingLyrics: Boolean,
    mediaItem: MediaItem,
    links: PlayerTrackLinks,
    shouldBePlaying: Boolean,
    isBuffering: Boolean,
    position: () -> Long,
    duration: () -> Long,
    dynamicColorPalette: ColorPalette,
    modifier: Modifier = Modifier
) {
    val preview = LocalThemePreview.current
    val binder = LocalPlayerServiceBinder.current
    val player = preview?.player ?: binder?.player ?: return

    val mediaId = mediaItem.mediaId
    val currentSong = key(mediaId, preview) {
        val songId = mediaId.substringAfterLast('/')
        remember {
            if (preview != null) kotlinx.coroutines.flow.flowOf(ThemePreviewDemo.songs.firstOrNull { it.id == songId })
            else Database.songTable.findById(songId).distinctUntilChanged()
        }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA).value
    }
    val metadata = mediaItem.mediaMetadata
    val unknownTitle = stringResource(R.string.metadata_unknown_title)
    val unknownArtist = stringResource(R.string.metadata_unknown_artist)
    val title = resolvePlayerTrackName(metadata.title?.toString(), currentSong?.title, unknownTitle, "Unknown Title")
        ?: unknownTitle
    val artist = resolveTrackArtistName(metadata.artist?.toString(), currentSong?.artistsText, links.artists, unknownArtist)
        ?: unknownArtist
    val artistIds = remember(links.artists, artist) { alignInfosToDisplayOrder(links.artists, artist) }
    val albumId = links.albumId
    val albumTitle = metadata.albumTitle?.toString()
    val media = mediaItem.toUiMedia(duration()).copy(title = title, artist = artist)
    val isExplicit = mediaItem.isExplicit

    var disableScrollingText by rememberPreference(disableScrollingTextKey, false)


    // Issue #881 (gh-881): tolerant saver (spec M5).
    var isDownloaded by rememberSaveable(saver = TolerantBoolStateSaver("controls.isDownloaded")) {
        mutableStateOf(false)
    }

    isDownloaded = preview == null && isDownloadedSong(mediaId)

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

    // Weight belongs to the panel child so the lower toolbar retains its own
    // measured space with both the native and Glass presentation.
    PlayerInformationGlassBlock(modifier = if (isLandscape) modifier else Modifier) {
    Box(
        modifier = Modifier
            .playerInfoAwareContentSize(modernInfo = playerInfoType == PlayerInfoType.Modern)
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
                        PlayerInformationGlassRegion("info", horizontalContentInset = 10.dp) {
                            InfoAlbumAndArtistModern(
                                player = player,
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
                                isExplicit = isExplicit,
                                albumTitle = albumTitle,
                                albumPending = links.albumPending,
                                artistsPending = links.artistsPending,
                            )
                        }

                    if (playerInfoType == PlayerInfoType.Essential)
                        PlayerInformationGlassRegion("info", vertical = true, horizontalContentInset = 10.dp) {
                            InfoAlbumAndArtistEssential(
                                player = player,
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
                        }
                    Spacer(
                        modifier = Modifier
                            .height(10.dp)
                    )
                }
                val showTimeline = !isShowingLyrics || timelineExpanded
                val showControls = !isShowingLyrics || controlsExpanded
                val timelineGap = if (playerPlayButtonType != PlayerPlayButtonType.Disabled) 10.dp else 5.dp
                val timeline: @Composable () -> Unit = {
                    PlayerInformationGlassRegion("timeline", vertical = true, horizontalContentInset = 10.dp) {
                        GetSeekBar(
                            position = position,
                            duration = duration,
                            media = media,
                            mediaId = mediaId,
                            shouldBePlaying = shouldBePlaying,
                            isBuffering = isBuffering
                        )
                    }
                }
                val transport: @Composable () -> Unit = {
                    GetControls(
                        timelineHorizontalInset = playerTimelineSize.size.dp,
                        dynamicColorPalette = dynamicColorPalette,
                        binder = binder,
                        player = player,
                        position = position,
                        shouldBePlaying = shouldBePlaying,
                        isBuffering = isBuffering,
                        likedAt = currentSong?.likedAt,
                        mediaId = mediaId,
                        onBlurScaleChange = onBlurScaleChange
                    )
                }
                if (playerSwapControlsWithTimeline) {
                    if (showControls) {
                        transport()
                        Spacer(Modifier.height(if (showTimeline) timelineGap else 5.dp))
                    }
                    if (showTimeline) {
                        timeline()
                        Spacer(Modifier.height(if (showControls) 5.dp else timelineGap))
                    }
                } else {
                    if (showTimeline) {
                        timeline()
                        Spacer(Modifier.height(timelineGap))
                    }
                    if (showControls) {
                        transport()
                        Spacer(Modifier.height(5.dp))
                    }
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
                    PlayerInformationGlassRegion("info", horizontalContentInset = 10.dp) {
                        InfoAlbumAndArtistModern(
                            player = player,
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
                            isExplicit = isExplicit,
                            albumTitle = albumTitle,
                            albumPending = links.albumPending,
                            artistsPending = links.artistsPending,
                        )
                    }

                if (playerInfoType == PlayerInfoType.Essential)
                    PlayerInformationGlassRegion("info", vertical = true, horizontalContentInset = 10.dp) {
                        InfoAlbumAndArtistEssential(
                            player = player,
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
                    }

                Spacer(
                    modifier = Modifier
                        .height(25.dp)
                )

                if (!playerSwapControlsWithTimeline) {
                PlayerInformationGlassRegion("timeline", vertical = true, horizontalContentInset = 10.dp) {
                    GetSeekBar(
                        position = position,
                        duration = duration,
                        media = media,
                        mediaId = mediaId,
                        shouldBePlaying = shouldBePlaying,
                        isBuffering = isBuffering
                    )
                }
                    Spacer(
                        modifier = Modifier
                            .weight(0.4f)
                    )
                    GetControls(
                        timelineHorizontalInset = playerTimelineSize.size.dp,
                        dynamicColorPalette = dynamicColorPalette,
                        binder = binder,
                        player = player,
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
                        timelineHorizontalInset = playerTimelineSize.size.dp,
                        dynamicColorPalette = dynamicColorPalette,
                        binder = binder,
                        player = player,
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
                PlayerInformationGlassRegion("timeline", vertical = true, horizontalContentInset = 10.dp) {
                    GetSeekBar(
                        position = position,
                        duration = duration,
                        media = media,
                        mediaId = mediaId,
                        shouldBePlaying = shouldBePlaying,
                        isBuffering = isBuffering
                    )
                }
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = playerTimelineSize.size.dp)
        ) {

            if (playerInfoType == PlayerInfoType.Modern)
                PlayerInformationGlassRegion("info", horizontalContentInset = 10.dp) {
                    InfoAlbumAndArtistModern(
                        player = player,
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
                        isExplicit = isExplicit,
                        albumTitle = albumTitle,
                        albumPending = links.albumPending,
                        artistsPending = links.artistsPending,
                    )
                }

            if (playerInfoType == PlayerInfoType.Essential)
                PlayerInformationGlassRegion("info", vertical = true, horizontalContentInset = 10.dp) {
                    InfoAlbumAndArtistEssential(
                        player = player,
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
                }

            Spacer(
                modifier = Modifier
                    .height(if (expandedlandscape) 10.dp else 25.dp)
            )

            if (!playerSwapControlsWithTimeline) {
                PlayerInformationGlassRegion("timeline", vertical = true, horizontalContentInset = 10.dp) {
                    GetSeekBar(
                        position = position,
                        duration = duration,
                        media = media,
                        mediaId = mediaId,
                        shouldBePlaying = shouldBePlaying,
                        isBuffering = isBuffering
                    )
                }
                Spacer(
                    modifier = Modifier
                        .animateContentSize()
                        .conditional(!expandedlandscape) { weight(0.4f) }
                        .conditional(expandedlandscape) { height(15.dp) }
                )
                GetControls(
                    timelineHorizontalInset = playerTimelineSize.size.dp,
                    dynamicColorPalette = dynamicColorPalette,
                    binder = binder,
                        player = player,
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
                    timelineHorizontalInset = playerTimelineSize.size.dp,
                    dynamicColorPalette = dynamicColorPalette,
                    binder = binder,
                        player = player,
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
                PlayerInformationGlassRegion("timeline", vertical = true, horizontalContentInset = 10.dp) {
                    GetSeekBar(
                        position = position,
                        duration = duration,
                        media = media,
                        mediaId = mediaId,
                        shouldBePlaying = shouldBePlaying,
                        isBuffering = isBuffering
                    )
                }
                Spacer(
                    modifier = Modifier
                        .animateContentSize()
                        .conditional(!expandedlandscape) { weight(0.4f) }
                        .conditional(expandedlandscape) { height(15.dp) }
                )
            }
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



