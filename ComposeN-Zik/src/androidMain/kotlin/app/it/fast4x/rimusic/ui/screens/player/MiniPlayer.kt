@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)
package app.it.fast4x.rimusic.ui.screens.player

import app.n_zik.android.core.database.*
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.artistTextWithFallback
import app.n_zik.android.utils.titleWithFallback
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import app.n_zik.android.uiRoundnessShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.alpha
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showLyricsStateKey
import app.it.fast4x.rimusic.utils.showVisualizerStateKey
import app.it.fast4x.rimusic.utils.saveLyricsStateKey
import app.it.fast4x.rimusic.utils.saveVisualizerStateKey
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.BackgroundProgress
import app.it.fast4x.rimusic.enums.MiniPlayerType
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.enums.MiniPlayerButton
import app.n_zik.android.enums.PendingMiniPlayerAction
import app.n_zik.android.LocalPendingMiniPlayerAction
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.n_zik.android.components.menu.player.AddToPlaylistPlayerMenu
import app.n_zik.android.components.menu.player.AudioDeviceMenu
import app.n_zik.android.playback.services.AudioOutputManager
import android.content.Intent
import timber.log.Timber
import android.content.pm.PackageManager
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.themed.NowPlayingSongIndicator
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.ui.styling.favoritesOverlay
import app.it.fast4x.rimusic.utils.DisposableListener
import app.it.fast4x.rimusic.utils.backgroundProgressKey
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.disableClosingPlayerSwipingDownKey
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.effectRotationKey
import app.it.fast4x.rimusic.utils.getLikedIcon
import app.it.fast4x.rimusic.utils.getUnlikedIcon
import app.it.fast4x.rimusic.utils.getDislikedIcon
import app.it.fast4x.rimusic.utils.intent
import app.it.fast4x.rimusic.utils.isExplicit
import app.it.fast4x.rimusic.utils.miniPlayerTypeKey
import app.it.fast4x.rimusic.utils.miniPlayerSlot1Key
import app.it.fast4x.rimusic.utils.miniPlayerSlot2Key
import app.it.fast4x.rimusic.utils.miniPlayerSlot3Key
import app.it.fast4x.rimusic.utils.miniPlayerSlot4Key
import app.it.fast4x.rimusic.utils.miniPlayerButtonOrderKey
import app.it.fast4x.rimusic.utils.showMiniPlayerPlayPauseKey
import app.it.fast4x.rimusic.utils.showMiniPlayerSkipBackKey
import app.it.fast4x.rimusic.utils.showMiniPlayerSkipForwardKey
import app.it.fast4x.rimusic.utils.showMiniPlayerShuffleKey
import app.it.fast4x.rimusic.utils.showMiniPlayerRepeatKey
import app.it.fast4x.rimusic.utils.showMiniPlayerLikeKey
import app.it.fast4x.rimusic.utils.showMiniPlayerAddToPlaylistKey
import app.it.fast4x.rimusic.utils.showMiniPlayerDownloadKey
import app.it.fast4x.rimusic.utils.showMiniPlayerShareKey
import app.it.fast4x.rimusic.utils.showMiniPlayerRadioKey
import app.it.fast4x.rimusic.utils.showMiniPlayerAudioOutputKey
import app.it.fast4x.rimusic.utils.showMiniPlayerSleepTimerKey
import app.it.fast4x.rimusic.utils.showMiniPlayerLyricsKey
import app.it.fast4x.rimusic.utils.showMiniPlayerVisualizerKey
import app.it.fast4x.rimusic.utils.showMiniPlayerQueueKey
import app.it.fast4x.rimusic.utils.showMiniPlayerVideoKey
import app.it.fast4x.rimusic.utils.showMiniPlayerDiscoverKey
import app.it.fast4x.rimusic.utils.playNext
import app.it.fast4x.rimusic.utils.playPrevious
import app.it.fast4x.rimusic.utils.shuffleQueue
import app.it.fast4x.rimusic.utils.positionAndDurationState
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.shouldBePlaying
import app.it.fast4x.rimusic.enums.QueueLoopType
import app.it.fast4x.rimusic.utils.isDownloadedSong
import app.it.fast4x.rimusic.utils.manageDownload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.n_zik.android.core.coil.ImageCacheFactory
import app.kreate.android.me.knighthat.sync.YouTubeSync
import app.kreate.android.me.knighthat.utils.Toaster
import kotlin.math.absoluteValue
import app.n_zik.android.uiRoundnessShape
import androidx.compose.runtime.LaunchedEffect
import app.it.fast4x.rimusic.utils.getBitmapFromUrl
import app.n_zik.android.core.coil.thumbnail
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import androidx.compose.foundation.isSystemInDarkTheme
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.n_zik.android.enums.PlayerControlsColors
import app.it.fast4x.rimusic.utils.playerControlsColorsKey
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.DisposableEffect
import app.it.fast4x.rimusic.ui.components.MenuState
import androidx.compose.animation.ExperimentalAnimationApi
import app.n_zik.android.LocalIsShowingVisualizer
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.LocalDensity
import app.it.fast4x.rimusic.utils.discoverKey
import app.n_zik.android.LocalIsShowingLyrics
import app.it.fast4x.rimusic.utils.autoLoadSongsInQueueKey
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import org.json.JSONArray
import android.content.Context

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayer(
    showPlayer: () -> Unit,
    hidePlayer: () -> Unit,
    navController: NavController? = null,
) {
    val binder = LocalPlayerServiceBinder.current
    binder?.player ?: return

    val context = LocalContext.current
    val menuState = LocalMenuState.current

    val pendingMiniPlayerAction = LocalPendingMiniPlayerAction.current
    
    val playerUpdateTrigger by binder.playerUpdateTrigger.collectAsState()

    var nullableMediaItem by remember(playerUpdateTrigger) {
        mutableStateOf(
            binder.player.currentMediaItem,
            neverEqualPolicy()
        )
    }
    var shouldBePlaying by remember(playerUpdateTrigger) { mutableStateOf(binder.player.shouldBePlaying) }
    val hapticFeedback = LocalHapticFeedback.current

    var playerError by remember(playerUpdateTrigger) {
        mutableStateOf<PlaybackException?>(binder.player.playerError)
    }
    var isBuffering by remember(playerUpdateTrigger) {
        mutableStateOf(binder.player.playbackState == Player.STATE_BUFFERING)
    }

    binder.player.DisposableListener(playerUpdateTrigger) {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                nullableMediaItem = mediaItem
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                shouldBePlaying = if (playerError == null) binder.player.shouldBePlaying else false
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playerError = binder.player.playerError
                shouldBePlaying = if (playerError == null) binder.player.shouldBePlaying else false
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(playbackException: PlaybackException) {
                playerError = playbackException
            }
        }
    }

    val mediaItem = nullableMediaItem ?: return

    val likeState by remember( mediaItem.mediaId ) {
        Database.songTable
                .likeState( mediaItem.mediaId )
                .distinctUntilChanged()
    }.collectAsState( null, Dispatchers.IO )

    var miniPlayerType by rememberPreference(
        miniPlayerTypeKey,
        MiniPlayerType.Essential
    )

    // Migration: read old pref, write new slots if slots are empty
    val slot1Key = rememberPreference(miniPlayerSlot1Key, "")
    val slot2Key = rememberPreference(miniPlayerSlot2Key, "")
    val slot3Key = rememberPreference(miniPlayerSlot3Key, "")
    val slot4Key = rememberPreference(miniPlayerSlot4Key, "")

    // New toggle-based system: read button order + individual toggles
    val defaultMiniPlayerButtonOrder = listOf(
        "skip_back", "play_pause", "skip_forward", 
        "shuffle", "repeat", "queue", "audio_output", "sleep_timer", 
        "like", "add_to_playlist", "download", "share", 
        "radio", "discover", "lyrics", "visualizer", "video"
    )

    fun parseMiniPlayerOrder(serialized: String): List<String> {
        if (serialized.isBlank()) return defaultMiniPlayerButtonOrder
        return try {
            val arr = JSONArray(serialized)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            val validIds = defaultMiniPlayerButtonOrder
            val result = list.filter { it in validIds }.toMutableList()
            for (id in validIds) {
                if (id !in result) result.add(id)
            }
            result
        } catch (_: Exception) {
            defaultMiniPlayerButtonOrder
        }
    }

    val miniPlayerButtonIdToButton = mapOf(
        "play_pause" to MiniPlayerButton.PlayPause,
        "skip_back" to MiniPlayerButton.SkipBack,
        "skip_forward" to MiniPlayerButton.SkipForward,
        "shuffle" to MiniPlayerButton.Shuffle,
        "repeat" to MiniPlayerButton.Repeat,
        "like" to MiniPlayerButton.Like,
        "add_to_playlist" to MiniPlayerButton.AddToPlaylist,
        "download" to MiniPlayerButton.Download,
        "share" to MiniPlayerButton.Share,
        "radio" to MiniPlayerButton.Radio,
        "audio_output" to MiniPlayerButton.AudioOutput,
        "sleep_timer" to MiniPlayerButton.SleepTimer,
        "lyrics" to MiniPlayerButton.Lyrics,
        "visualizer" to MiniPlayerButton.Visualizer,
        "queue" to MiniPlayerButton.Queue,
        "video" to MiniPlayerButton.Video,
        "discover" to MiniPlayerButton.Discover,
    )

    var orderSerialized by rememberPreference(miniPlayerButtonOrderKey, "")
    val orderedIds = remember(orderSerialized) { parseMiniPlayerOrder(orderSerialized) }

    val togglePlayPause by rememberPreference(showMiniPlayerPlayPauseKey, true)
    val toggleSkipBack by rememberPreference(showMiniPlayerSkipBackKey, true)
    val toggleSkipForward by rememberPreference(showMiniPlayerSkipForwardKey, true)
    val toggleShuffle by rememberPreference(showMiniPlayerShuffleKey, false)
    val toggleRepeat by rememberPreference(showMiniPlayerRepeatKey, false)
    val toggleLike by rememberPreference(showMiniPlayerLikeKey, false)
    val toggleAddToPlaylist by rememberPreference(showMiniPlayerAddToPlaylistKey, false)
    val toggleDownload by rememberPreference(showMiniPlayerDownloadKey, false)
    val toggleShare by rememberPreference(showMiniPlayerShareKey, false)
    val toggleRadio by rememberPreference(showMiniPlayerRadioKey, false)
    val toggleAudioOutput by rememberPreference(showMiniPlayerAudioOutputKey, true)
    val toggleSleepTimer by rememberPreference(showMiniPlayerSleepTimerKey, false)
    val toggleLyrics by rememberPreference(showMiniPlayerLyricsKey, false)
    val toggleVisualizer by rememberPreference(showMiniPlayerVisualizerKey, false)
    val toggleQueue by rememberPreference(showMiniPlayerQueueKey, false)
    val toggleVideo by rememberPreference(showMiniPlayerVideoKey, false)
    val toggleDiscover by rememberPreference(showMiniPlayerDiscoverKey, false)
    val toggleMap = remember(togglePlayPause, toggleSkipBack, toggleSkipForward, toggleShuffle, toggleRepeat, toggleLike, toggleAddToPlaylist, toggleDownload, toggleShare, toggleRadio, toggleAudioOutput, toggleSleepTimer, toggleLyrics, toggleVisualizer, toggleQueue, toggleVideo, toggleDiscover) {
        mapOf(
            "play_pause" to togglePlayPause,
            "skip_back" to toggleSkipBack,
            "skip_forward" to toggleSkipForward,
            "shuffle" to toggleShuffle,
            "repeat" to toggleRepeat,
            "like" to toggleLike,
            "add_to_playlist" to toggleAddToPlaylist,
            "download" to toggleDownload,
            "share" to toggleShare,
            "radio" to toggleRadio,
            "audio_output" to toggleAudioOutput,
            "sleep_timer" to toggleSleepTimer,
            "lyrics" to toggleLyrics,
            "visualizer" to toggleVisualizer,
            "queue" to toggleQueue,
            "video" to toggleVideo,
            "discover" to toggleDiscover,
        )
    }

    val activeButtons = remember(toggleMap, orderedIds) {
        orderedIds.filter { id -> toggleMap[id] == true }
            .mapNotNull { id -> miniPlayerButtonIdToButton[id] }
    }


    val color = colorPalette()
    var dynamicColorPalette by remember { mutableStateOf( color ) }
    val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)
    val lightTheme = colorPaletteMode == ColorPaletteMode.Light || (colorPaletteMode == ColorPaletteMode.System && (!isSystemInDarkTheme()))

    LaunchedEffect(mediaItem.mediaId) {
        try {
            val imageUrl = mediaItem.mediaMetadata.artworkUri.thumbnail(1000).toString()
            val bitmap = getBitmapFromUrl(
                context,
                imageUrl
            ) ?: throw Exception("Bitmap is null")
            dynamicColorPalette = dynamicColorPaletteOf(bitmap, !lightTheme) ?: color
        } catch (e: Exception) {
            dynamicColorPalette = color
        }
    }

    val playerControlsColors by rememberPreference(playerControlsColorsKey, PlayerControlsColors.Monochrome)
    val controlsColorText = when (playerControlsColors) {
        PlayerControlsColors.Cover -> dynamicColorPalette.accent
        PlayerControlsColors.Monochrome -> Color.White
        else -> colorPalette().accent
    }

    fun toggleLike() {
        CoroutineScope( Dispatchers.IO ).launch {
            mediaItem?.let {
                YouTubeSync.rotateSongLikeState( context, it )
            }
        }
    }

    val positionAndDurationState = binder.player.positionAndDurationState(playerUpdateTrigger)
    val durationState = remember(positionAndDurationState) {
        derivedStateOf { positionAndDurationState.value.second }
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd)
                if (miniPlayerType == MiniPlayerType.Essential)
                    toggleLike()
                else
                    binder.player.seekToPrevious()
            else
                if (value == SwipeToDismissBoxValue.EndToStart)
                    binder.player.seekToNext()

            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

            return@rememberSwipeToDismissBoxState false
        }
    )
    val backgroundProgress by rememberPreference(backgroundProgressKey, BackgroundProgress.MiniPlayer)
    val effectRotationEnabled by rememberPreference(effectRotationKey, false)
    val shouldBePlayingTransition = updateTransition(shouldBePlaying, label = stringResource(R.string.txt_shouldbeplaying))
    val playPauseRoundness by shouldBePlayingTransition.animateDp(
        transitionSpec = { tween(durationMillis = 100, easing = LinearEasing) },
        label = stringResource(R.string.txt_playpauseroundness),
        targetValueByState = { if (it) 24.dp else 12.dp }
    )

    var isRotated by rememberSaveable { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isRotated) 360F else 0f,
        animationSpec = tween(durationMillis = 200), label = ""
    )
    val disableClosingPlayerSwipingDown by rememberPreference(disableClosingPlayerSwipingDownKey, false)

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val isFloating = NavigationBarPosition.BottomFloating.isCurrent()
    val shape = if (isFloating) uiRoundnessShape() else uiRoundnessShape()


    SwipeToDismissBox(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .shadow(elevation = if (isFloating) 8.dp else 0.dp, shape = shape)
            .clip(shape),

        state = dismissState,
        backgroundContent = {
            /*
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                },
                label = stringResource(R.string.txt_background)
            )
             */

            val offset = try { dismissState.requireOffset() } catch (e: Exception) { 0f }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorPalette().background1)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = when {
                    offset > 0 -> Arrangement.Start
                    offset < 0 -> Arrangement.End
                    else -> Arrangement.Center
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when {
                    offset > 0 -> {
                        if (miniPlayerType == MiniPlayerType.Modern)
                            ImageVector.vectorResource(R.drawable.play_skip_back)
                        else when(likeState) {
                            false -> ImageVector.vectorResource(R.drawable.heart_dislike)
                            null -> ImageVector.vectorResource(R.drawable.heart_outline)
                            else -> ImageVector.vectorResource(R.drawable.heart)
                        }
                    }

                    offset < 0 -> ImageVector.vectorResource(R.drawable.play_skip_forward)
                    else -> null
                }
                if (icon != null)
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colorPalette().iconButtonPlayer,
                    )
            }
        }
    ) {
        val colorPalette = colorPalette()
        /***** */
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .clip(uiRoundnessShape()).combinedClickable(
                    onLongClick = {
                        navController?.navigate(NavRoutes.queue.name);
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onClick = {
                        //if (showPlayer != null)
                        showPlayer()
                        //else
                        //    navController?.navigate("player")
                    }
                )
                //.clip(uiRoundnessShape()).clickable(onClick = showPlayer)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount < 0) showPlayer()
                            else if (dragAmount > 20) {
                                if (!disableClosingPlayerSwipingDown) {
                                    binder.stopRadio()
                                    binder.player.clearMediaItems()
                                    hidePlayer()
                                    runCatching {
                                        context.stopService(context.intent<PlayerServiceModern>())
                                    }
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else
                                    Toaster.i( R.string.player_swiping_down_is_disabled )
                            }
                        }
                    )
                }
                .background(colorPalette().background2)
                .fillMaxWidth()
                .drawBehind {
                    if (backgroundProgress == BackgroundProgress.Both || backgroundProgress == BackgroundProgress.MiniPlayer) {
                        drawRect(
                            color = colorPalette.favoritesOverlay,
                            topLeft = Offset.Zero,
                            size = Size(
                                width = positionAndDurationState.value.first.toFloat() /
                                        (durationState.value.absoluteValue.takeIf { it > 0 } ?: 1L) * size.width,
                                height = size.maxDimension
                            )
                        )
                    }
                }
        ) {

            Spacer(
                modifier = Modifier
                    .width(2.dp)
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height( Dimensions.miniPlayerHeight )
            ) {
                val artworkUrl = mediaItem.mediaMetadata.artworkUri?.toString()
                val isCustomImage = artworkUrl?.let {
                    it.startsWith("file://") || it.contains("app_covers") || it.startsWith("modified:")
                } == true

                Box(
                    modifier = Modifier.size( 48.dp )
                ) {
                    ImageCacheFactory.Thumbnail(
                        thumbnailUrl = artworkUrl,
                        contentScale = if (isCustomImage) ContentScale.Crop else ContentScale.FillHeight,
                        modifier = Modifier.clip( thumbnailShape() )
                                           .fillMaxSize()
                    )

                    NowPlayingSongIndicator(mediaItem.mediaId, binder.player)

                    if (likeState != null)
                        app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton(
                            onClick = {},
                            icon = if (likeState == true) getLikedIcon() else getDislikedIcon(),
                            color = colorPalette().favoritesIcon,
                            iconSize = 10.dp,
                            modifier = Modifier.align( Alignment.BottomStart )
                                               .absoluteOffset( x = (-5).dp )
                        )
                }
            }


            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .height(Dimensions.miniPlayerHeight)
                    .weight(1f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if ( mediaItem.isExplicit )
                        IconButton(
                            icon = R.drawable.explicit,
                            color = colorPalette().text,
                            enabled = true,
                            onClick = {},
                            modifier = Modifier
                                .size(14.dp)
                        )
                    BasicText(
                        text = cleanPrefix( mediaItem.titleWithFallback() ),
                        style = typography().xxs.semiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                    )
                }

                BasicText(
                    text = cleanPrefix( mediaItem.artistTextWithFallback() ),
                    style = typography().xxs.semiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                )
            }

            Spacer(
                modifier = Modifier
                    .width(2.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(Dimensions.miniPlayerHeight)
            ) {
                activeButtons.forEach { button ->
                    if (button == MiniPlayerButton.PlayPause) {
                        // Play/Pause (distinct styling)
                        Box(
                            modifier = Modifier
                                .clip(uiRoundnessShape()).clickable {
                                    if (shouldBePlaying) {
                                        binder.gracefulPause()
                                    } else {
                                        binder.gracefulPlay()
                                    }
                                    if (effectRotationEnabled) isRotated = !isRotated
                                }
                                .background(colorPalette().background2)
                                .size(42.dp)
                        ) {
                            if (isBuffering && shouldBePlaying) {
                                CircularWavyProgressIndicator(
                                    color = colorPalette().accent,
                                    trackColor = colorPalette().text,
                                    modifier = Modifier
                                        .rotate(rotationAngle)
                                        .align(Alignment.Center)
                                        .size(24.dp),
                                    stroke = Stroke(width = with(LocalDensity.current) { 2.dp.toPx() }),
                                    trackStroke = Stroke(width = with(LocalDensity.current) { 2.dp.toPx() })
                                )
                            } else {
                                Image(
                                    painter = painterResource(if (shouldBePlaying) R.drawable.pause else R.drawable.play),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(controlsColorText),
                                    modifier = Modifier
                                        .rotate(rotationAngle)
                                        .align(Alignment.Center)
                                        .size(24.dp)
                                )
                            }
                        }
                    } else {
                        MiniPlayerSlotButton(
                            button = button,
                            likeState = likeState,
                            controlsColorText = controlsColorText,
                            rotationAngle = rotationAngle,
                            onLikeClick = ::toggleLike,
                            onAudioOutputClick = {
                                when {
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                                        menuState.display {
                                            AudioDeviceMenu(onDismiss = menuState::hide)
                                        }
                                    }
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                                        val intent = Intent("android.settings.panel.action.MEDIA_OUTPUT").apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        kotlin.runCatching {
                                            context.startActivity(intent)
                                        }.onFailure { e ->
                                            Timber.tag("MiniPlayer").w(e, "MEDIA_OUTPUT panel not available, trying broadcast fallback")
                                            val broadcastIntent = Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG").apply {
                                                setPackage("com.android.systemui")
                                                putExtra("package_name", context.packageName)
                                            }
                                            @Suppress("DEPRECATION")
                                            val receivers = context.packageManager.queryBroadcastReceivers(broadcastIntent, 0)
                                            if (receivers.isNotEmpty()) {
                                                context.sendBroadcast(broadcastIntent)
                                            } else {
                                                Timber.tag("MiniPlayer").w("No broadcast receiver found for LAUNCH_MEDIA_OUTPUT_DIALOG")
                                                Toaster.w(R.string.audio_output_not_supported)
                                            }
                                        }
                                    }
                                    else -> {
                                        Toaster.w(R.string.available_on_android_10_or_higher)
                                    }
                                }
                            },
                            onShowPlayer = showPlayer,
                            mediaItem = mediaItem,
                            context = context,
                            binder = binder,
                            effectRotationEnabled = effectRotationEnabled,
                            isRotated = isRotated,
                            onRotatedChange = { isRotated = it },
                            menuState = menuState,
                            pendingAction = pendingMiniPlayerAction,
                            navController = navController,
                            onClosePlayer = hidePlayer
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier
                    .width(2.dp)
            )
        }
        /*****  */


    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalTextApi::class)
@Composable
private fun MiniPlayerSlotButton(
    button: MiniPlayerButton?,
    likeState: Boolean?,
    controlsColorText: androidx.compose.ui.graphics.Color,
    rotationAngle: Float,
    onLikeClick: () -> Unit,
    onAudioOutputClick: () -> Unit,
    onShowPlayer: () -> Unit,
    mediaItem: MediaItem?,
    context: Context,
    binder: PlayerServiceModern.Binder,
    effectRotationEnabled: Boolean,
    isRotated: Boolean,
    onRotatedChange: (Boolean) -> Unit,
    menuState: MenuState?,
    pendingAction: MutableState<PendingMiniPlayerAction?>?,
    navController: NavController?,
    onClosePlayer: () -> Unit
) {
    if (button == null) return

    val modifier = Modifier
        .rotate(rotationAngle)
        .padding(horizontal = 2.dp, vertical = 8.dp)
        .size(24.dp)

    when (button) {
        MiniPlayerButton.SkipBack -> {
            IconButton(
                icon = R.drawable.play_skip_back,
                color = controlsColorText,
                onClick = {
                    binder.player.playPrevious()
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                modifier = modifier
            )
        }
        MiniPlayerButton.SkipForward -> {
            IconButton(
                icon = R.drawable.play_skip_forward,
                color = controlsColorText,
                onClick = {
                    binder.player.playNext()
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                modifier = modifier
            )
        }
        MiniPlayerButton.Shuffle -> {
            IconButton(
                icon = R.drawable.shuffle,
                color = controlsColorText,
                onClick = {
                    binder.player.shuffleQueue()
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                modifier = modifier
            )
        }
        MiniPlayerButton.Repeat -> {
            var currentRepeatMode by remember { mutableIntStateOf(binder.player.repeatMode) }
            val queueLoopType = QueueLoopType.from(currentRepeatMode)
            IconButton(
                icon = queueLoopType.iconId,
                color = if (queueLoopType != QueueLoopType.Default) colorPalette().accent else controlsColorText,
                onClick = {
                    val next = queueLoopType.next()
                    binder.player.repeatMode = next.type
                    currentRepeatMode = next.type
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                modifier = modifier
            )
        }
        MiniPlayerButton.Like -> {
            IconButton(
                icon = when(likeState) {
                    false -> getDislikedIcon()
                    null -> getUnlikedIcon()
                    else -> getLikedIcon()
                },
                color = colorPalette().favoritesIcon,
                onClick = onLikeClick,
                modifier = modifier
            )
        }
        MiniPlayerButton.AddToPlaylist -> {
            IconButton(
                icon = R.drawable.add_in_playlist,
                color = controlsColorText,
                onClick = {
                    menuState?.display {
                        AddToPlaylistPlayerMenu(
                            navController = navController ?: return@display,
                            onDismiss = menuState::hide,
                            mediaItem = mediaItem ?: return@display,
                            binder = binder,
                            onClosePlayer = onClosePlayer
                        )
                    }
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                modifier = modifier
            )
        }
        MiniPlayerButton.Download -> {
            val isDownloaded = mediaItem?.let { isDownloadedSong(it.mediaId) } ?: false
            IconButton(
                icon = if (isDownloaded) R.drawable.downloaded else R.drawable.download,
                color = if (isDownloaded) colorPalette().accent else controlsColorText,
                onClick = {
                    mediaItem?.let { manageDownload(context, it, isDownloaded) }
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                modifier = modifier
            )
        }
        MiniPlayerButton.Share -> {
            IconButton(
                icon = R.drawable.share_social,
                color = controlsColorText,
                onClick = {
                    mediaItem?.let { item ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${item.mediaId}")
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                modifier = modifier
            )
        }
        MiniPlayerButton.Radio -> {
            val isRadioActive = binder.isRadioActive
            IconButton(
                icon = R.drawable.radio,
                color = if (isRadioActive) colorPalette().accent else controlsColorText,
                onClick = {
                    mediaItem?.let { binder.startRadio(it, false, null, true) }
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                modifier = modifier
            )
        }
        MiniPlayerButton.AudioOutput -> {
            val context = LocalContext.current
            val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
            val audioOutputManager = remember { AudioOutputManager(context, audioManager) }
            var activeDevice by remember { mutableStateOf(audioOutputManager.getAvailableDevices().firstOrNull { it.isCurrentlyActive }) }

            DisposableEffect(Unit) {
                audioOutputManager.registerDeviceChanges { devices ->
                    activeDevice = devices.firstOrNull { it.isCurrentlyActive }
                }
                onDispose {
                    audioOutputManager.unregisterDeviceChanges()
                }
            }

            val isExternal = (activeDevice != null && activeDevice?.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER && activeDevice?.type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) || activeDevice?.isCar == true
            
            val iconVal = activeDevice?.icon ?: R.drawable.devices

            val isAudioOutputEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            val finalColor = if (!isAudioOutputEnabled) {
                controlsColorText.copy(alpha = 0.3f)
            } else if (isExternal) {
                colorPalette().accent
            } else {
                controlsColorText
            }

            val finalIcon = if (isExternal) iconVal else R.drawable.devices
            if (finalIcon is ImageVector) {
                IconButton(
                    icon = finalIcon,
                    color = finalColor,
                    enabled = true,
                    onClick = onAudioOutputClick,
                    modifier = modifier
                )
            } else {
                IconButton(
                    icon = finalIcon as Int,
                    color = finalColor,
                    enabled = true,
                    onClick = onAudioOutputClick,
                    modifier = modifier
                )
            }
        }
        MiniPlayerButton.SleepTimer -> {
            IconButton(
                icon = R.drawable.sleep,
                color = controlsColorText,
                onClick = {
                    pendingAction?.value = PendingMiniPlayerAction.SleepTimer
                    onShowPlayer()
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                modifier = modifier
            )
        }
        MiniPlayerButton.Lyrics -> {
            var isLyricsActive by LocalIsShowingLyrics.current
            var savedLyricsState by rememberPreference(saveLyricsStateKey, false)
            val shouldRememberLyricsState by rememberPreference(showLyricsStateKey, false)
            
            IconButton(
                icon = R.drawable.song_lyrics,
                color = if (isLyricsActive) colorPalette().accent else controlsColorText,
                onClick = {
                    if (isLyricsActive) {
                        isLyricsActive = false
                        if (shouldRememberLyricsState) savedLyricsState = false
                    } else {
                        pendingAction?.value = PendingMiniPlayerAction.Lyrics
                        onShowPlayer()
                    }
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                modifier = modifier
            )
        }
        MiniPlayerButton.Visualizer -> {
            var isVisualizerActive by LocalIsShowingVisualizer.current
            var savedVisualizerState by rememberPreference(saveVisualizerStateKey, false)
            val shouldRememberVisualizerState by rememberPreference(showVisualizerStateKey, false)
            
            IconButton(
                icon = R.drawable.sound_effect,
                color = if (isVisualizerActive) colorPalette().accent else controlsColorText,
                onClick = {
                    if (isVisualizerActive) {
                        isVisualizerActive = false
                        if (shouldRememberVisualizerState) savedVisualizerState = false
                    } else {
                        pendingAction?.value = PendingMiniPlayerAction.Visualizer
                        onShowPlayer()
                    }
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                modifier = modifier
            )
        }
        MiniPlayerButton.Queue -> {
            IconButton(
                icon = R.drawable.reorder,
                color = controlsColorText,
                onClick = {
                    pendingAction?.value = PendingMiniPlayerAction.Queue
                    onShowPlayer()
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                modifier = modifier
            )
        }
        MiniPlayerButton.Video -> {
            IconButton(
                icon = R.drawable.video,
                color = controlsColorText,
                onClick = {
                    pendingAction?.value = PendingMiniPlayerAction.Video
                    onShowPlayer()
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                modifier = modifier
            )
        }
        MiniPlayerButton.Discover -> {
            val discoverIsEnabled by rememberPreference(discoverKey, false)
            val isAutoFillEnabled by rememberPreference(autoLoadSongsInQueueKey, true)
            val isDiscoverClickable = binder.service.nzikRadio.isRadioActive || isAutoFillEnabled

            IconButton(
                icon = R.drawable.discover,
                color = if (discoverIsEnabled && isDiscoverClickable) colorPalette().accent else controlsColorText,
                onClick = {
                    binder.service.nzikRadio.toggleDiscover()
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                onLongClick = { Toaster.i(R.string.discoverinfo) },
                modifier = modifier.alpha(if (isDiscoverClickable) 1f else 0.4f)
            )
        }
        else -> {}
    }
}


