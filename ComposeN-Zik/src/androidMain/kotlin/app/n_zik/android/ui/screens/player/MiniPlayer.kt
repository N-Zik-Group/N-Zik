@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)
package app.n_zik.android.ui.screens.player

import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.components.player.rememberMiniPlayerProgress


import app.n_zik.android.components.rememberSongRowArtistCredits
import app.n_zik.android.components.rememberSongRowMetadata
import app.n_zik.android.components.song.songArtistDisplayLabel
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.displayTrackTitle
import app.n_zik.android.ui.saveable.TolerantBoolStateSaver
import app.n_zik.android.uiRoundnessShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import app.n_zik.android.playback.utils.isSmartShuffleRecommendation
import app.n_zik.android.components.ui.screens.player.isQueuePodcast
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.runtime.CompositionLocalProvider
import app.n_zik.android.components.glass.rememberGlassArtwork
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.LocalGlassMaterialSource
import app.n_zik.android.components.glass.LocalGlassOverlayBackdrop
import app.n_zik.android.components.glass.glassSceneOverlayBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.glassMiniPlayerBackground
import app.n_zik.android.components.glass.glassMiniPlayerBodyVisibility
import app.n_zik.android.components.glass.miniPlayerSwipeBackgroundColor
import app.n_zik.android.core.navigation.LocalMiniPlayerQueueRequest
import app.n_zik.android.components.glass.LocalEmbeddedQueueMini
import app.n_zik.android.components.glass.rememberMiniPlayerControlBackdrop
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.glassSwipeActionBackground
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.rememberGlassSettings
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import app.n_zik.android.components.navigation.dock.LocalCompactMiniPlayerProgress
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import app.n_zik.android.utils.rememberPreference
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
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
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import app.n_zik.android.enums.BackgroundProgress
import app.n_zik.android.enums.MiniPlayerType
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.enums.MiniPlayerButton
import app.n_zik.android.LocalPendingMiniPlayerAction
import app.n_zik.android.LocalPlayerSheetState
import app.n_zik.android.ui.components.LocalMenuState
import androidx.compose.ui.graphics.graphicsLayer
import app.n_zik.android.components.menu.player.AudioDeviceMenu
import android.content.Intent
import timber.log.Timber
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.ui.components.themed.NowPlayingSongIndicator
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.favoritesIcon
import app.n_zik.android.ui.styling.favoritesOverlay
import app.n_zik.android.utils.DisposableListener
import app.n_zik.android.utils.backgroundProgressKey
import app.n_zik.android.utils.conditional
import app.n_zik.android.utils.disableClosingPlayerSwipingDownKey
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.effectRotationKey
import app.n_zik.android.utils.getLikedIcon
import app.n_zik.android.utils.getDislikedIcon
import app.n_zik.android.utils.intent
import app.n_zik.android.utils.isExplicit
import app.n_zik.android.utils.miniPlayerTypeKey
import app.n_zik.android.utils.miniPlayerSlot1Key
import app.n_zik.android.utils.miniPlayerSlot2Key
import app.n_zik.android.utils.miniPlayerSlot3Key
import app.n_zik.android.utils.miniPlayerSlot4Key
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.shouldBePlaying
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.sync.YouTubeSync
import app.n_zik.android.core.utils.Toaster
import androidx.compose.runtime.LaunchedEffect
import app.n_zik.android.core.coil.thumbnail
import app.n_zik.android.components.player.MiniPlayerSwipeAction
import app.n_zik.android.components.ExplicitBadge
import app.n_zik.android.components.player.miniPlayerButtonSpacing
import app.n_zik.android.components.player.miniPlayerPlayPauseGlyphColor
import app.n_zik.android.components.player.GlassMiniPlayerPlayPauseButton
import app.n_zik.android.components.player.playButtonNativeColor
import app.n_zik.android.components.player.SwipeActionLatch
import app.n_zik.android.components.player.WithDoubledTouchSlop
import app.n_zik.android.components.player.rememberMiniPlayerSheetGestureState
import app.n_zik.android.components.player.miniPlayerSwipeAction
import app.n_zik.android.components.player.monochromeControlsColor
import androidx.compose.foundation.isSystemInDarkTheme
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.n_zik.android.ui.components.themed.IconButton
import app.n_zik.android.enums.PlayerControlsColors
import app.n_zik.android.utils.playerControlsColorsKey
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.platform.LocalDensity
import android.os.Build
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.components.player.rememberArtworkDisplayColors
import app.n_zik.android.components.player.rememberPlayerColorFade
import app.n_zik.android.components.theme.LocalPaletteFadeTarget
import app.n_zik.android.components.theme.LocalAmbientArtworkPaint
import androidx.compose.ui.graphics.lerp
import app.n_zik.android.components.theme.LocalNativeSurfaceMix

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayer(
    showPlayer: () -> Unit,
    hidePlayer: () -> Unit,
    navController: NavController? = null,
) {
    val progressProvider = app.n_zik.android.components.navigation.dock.LocalCompactMiniPlayerProgressProvider.current
    val dockPresentation = progressProvider != null
    val defaultProgress = LocalCompactMiniPlayerProgress.current
    val foldProgress = remember(progressProvider, defaultProgress) {
        { (progressProvider?.invoke() ?: defaultProgress).coerceIn(0f, 1f) }
    }
    val folding = remember(foldProgress) { { foldProgress().let { it > 0f && it < 1f } } }
    val expandedMiniHeight = if (dockPresentation) app.n_zik.android.components.navigation.dock.FloatingDockDimensions.expandedMiniHeight.dp else Dimensions.miniPlayerHeight
    val preview = LocalThemePreview.current
    val binder = LocalPlayerServiceBinder.current
    val player = preview?.player ?: binder?.player ?: return

    val context = LocalContext.current
    val menuState = LocalMenuState.current

    val pendingMiniPlayerAction = LocalPendingMiniPlayerAction.current
    val requestQueue = LocalMiniPlayerQueueRequest.current
    
    val playerUpdateTrigger by (binder?.playerUpdateTrigger?.collectAsStateWithLifecycle(0, context = NzikDispatchers.DATA) ?: remember { mutableStateOf(0) })

    var nullableMediaItem by remember(playerUpdateTrigger) {
        mutableStateOf(
            player.currentMediaItem,
            neverEqualPolicy()
        )
    }
    var shouldBePlaying by remember(playerUpdateTrigger) { mutableStateOf(player.shouldBePlaying) }
    val hapticFeedback = LocalHapticFeedback.current

    var playerError by remember(playerUpdateTrigger) {
        mutableStateOf<PlaybackException?>(player.playerError)
    }
    var isBuffering by remember(playerUpdateTrigger) {
        mutableStateOf(player.playbackState == Player.STATE_BUFFERING)
    }

    player.DisposableListener(playerUpdateTrigger) {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                nullableMediaItem = mediaItem
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                shouldBePlaying = if (playerError == null) player.shouldBePlaying else false
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playerError = player.playerError
                shouldBePlaying = if (playerError == null) player.shouldBePlaying else false
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(playbackException: PlaybackException) {
                playerError = playbackException
            }
        }
    }

    val mediaItem = nullableMediaItem ?: return
    val songId = mediaItem.mediaId.substringAfterLast('/')
    val songIds = remember(songId) { listOf(songId) }
    val storedSong = rememberSongRowMetadata(songIds).value[songId]
    val storedCredits = rememberSongRowArtistCredits(songIds).value[songId].orEmpty()
    val song = storedSong ?: remember(mediaItem, songId) { mediaItem.asSong.copy(id = songId) }
    val displayTitle = displayTrackTitle(song.title) ?: displayTrackTitle(mediaItem.mediaMetadata.title?.toString())
        ?: stringResource(R.string.metadata_unknown_title)
    val displayArtist = songArtistDisplayLabel(song, storedCredits, mediaItem)
    val artworkUrl = rememberGlassArtwork(player)?.takeIf { it.mediaId == mediaItem.mediaId }?.uri
        ?: mediaItem.mediaMetadata.artworkUri?.toString()

    val likeState by remember( mediaItem.mediaId, preview ) {
        if (preview != null) kotlinx.coroutines.flow.flowOf(null) else Database.songTable
                .likeState( mediaItem.mediaId )
                .distinctUntilChanged()
    }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)

    var miniPlayerType by rememberPreference(
        miniPlayerTypeKey,
        MiniPlayerType.Essential
    )

    // Migration: read old pref, write new slots if slots are empty
    val slot1Key = rememberPreference(miniPlayerSlot1Key, "")
    val slot2Key = rememberPreference(miniPlayerSlot2Key, "")
    val slot3Key = rememberPreference(miniPlayerSlot3Key, "")
    val slot4Key = rememberPreference(miniPlayerSlot4Key, "")

    // Folding changes presentation only; every configured action keeps its node and handler.
    val buttons = rememberMiniPlayerButtons()
    val activeButtons = if (preview == null && mediaItem.isQueuePodcast) buttons - MiniPlayerButton.SmartShuffle else buttons

    val color = colorPalette()
    val colorPaletteName by rememberPreference(colorPaletteNameKey, ColorPaletteName.Dynamic)
    val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)
    val lightTheme = colorPaletteMode == ColorPaletteMode.Light || (colorPaletteMode == ColorPaletteMode.System && (!isSystemInDarkTheme()))

    val playerControlsColors by rememberPreference(playerControlsColorsKey, PlayerControlsColors.Monochrome)
    val controlsColorTextState = when (playerControlsColors) {
        PlayerControlsColors.Cover -> {
            val appArtwork = LocalAmbientArtworkPaint.current?.value
            if (appArtwork != null) {
                rememberUpdatedState(if (colorPaletteName == ColorPaletteName.Dynamic)
                    lerp(color.accent, color.text, appArtwork.glass.darkness) else appArtwork.coverControl)
            } else {
                val coverDisplay = rememberArtworkDisplayColors(
                    artworkUrl, !lightTheme, awaitArtworkMetadata = true,
                )
                val coverAccent = if (coverDisplay?.forceDark == true) coverDisplay.foreground else coverDisplay?.palette?.accent
                val themeAccent = LocalPaletteFadeTarget.current?.colorPalette?.accent ?: color.accent
                rememberPlayerColorFade(coverAccent ?: themeAccent, active = true, interpolate = ::lerp)
            }
        }
        // Monochrome follows the effective palette tone, not a hardcoded white
        // (spec-achromatic-ramp-luminance-cap)
        PlayerControlsColors.Monochrome -> rememberUpdatedState(monochromeControlsColor(color))
        else -> rememberUpdatedState(color.accent)
    }

    fun toggleLike() {
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            mediaItem?.let {
                YouTubeSync.rotateSongLikeState( context, it )
            }
        }
    }

    // Get player sheet state for gesture handling
    val playerSheetState = LocalPlayerSheetState.current

    // rememberSwipeToDismissBoxState keeps the confirmValueChange it got at the first composition:
    // read what the swipe needs through these, or it uses the sheet and mini-player type of that

    // first composition. The song is not taken from composition at all: it is read from the player
    // when the swipe happens (issue #816: swipe-to-like liked the first song of the queue).
    val swipeSheetState by rememberUpdatedState(playerSheetState)
    val swipeMiniPlayerType by rememberUpdatedState(miniPlayerType)
    val swipePlayer by rememberUpdatedState(player)
    val swipeContext by rememberUpdatedState(context)
    // The callback is called again on every drag delta once the finger reaches the end anchor (it
    // never settles, as it answers false): run the action once per gesture (issue #818)
    val swipeActionLatch = remember { SwipeActionLatch() }
    // Listen Together guest lock (spec-listen-together-guest-lock-hardening): the like swipe
    // stays available for a guest in a room; seek swipes reach the central guard, which

    // no-ops them and shows the throttled toast.

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (swipeSheetState.progress > 0f) return@rememberSwipeToDismissBoxState false

            val action = miniPlayerSwipeAction(value, swipeMiniPlayerType == MiniPlayerType.Essential)
            if (action != null && swipeActionLatch.tryFire()) {
                val player = swipePlayer
                when (action) {
                    MiniPlayerSwipeAction.LikeCurrentSong -> player.currentMediaItem?.let { current ->
                        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                            YouTubeSync.rotateSongLikeState( swipeContext, current )
                        }
                    }

                    MiniPlayerSwipeAction.SeekToPrevious -> player.seekToPrevious()
                    MiniPlayerSwipeAction.SeekToNext -> player.seekToNext()
                }

                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }

            return@rememberSwipeToDismissBoxState false
        }
    )

    // Back at rest means the gesture is over: rearm the latch. The offset is not readable before
    // the first layout, which is also a state at rest.
    LaunchedEffect(dismissState) {
        snapshotFlow {
            try { dismissState.requireOffset() == 0f } catch (e: IllegalStateException) { true }
        }
            .distinctUntilChanged()
            .filter { atRest -> atRest }
            .collect { swipeActionLatch.release() }
    }

    // Once the action ran the swipe is off (see gesturesEnabled): send the box back to its position.
    // It comes back on when the box is at rest, which releases the latch above.
    LaunchedEffect(swipeActionLatch.isFired) {
        if (swipeActionLatch.isFired) dismissState.reset()
    }

    // The action changes the content (new title, like icon), which recomputes the swipe anchors,
    // so the state can settle in a dismissed direction and stay displaced (gestures off

    // until back at rest). Bring the mini-player back to its position.
    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.settledValue }
            .filter { settled -> settled != SwipeToDismissBoxValue.Settled }
            .collect { dismissState.reset() }
    }
    val backgroundProgress by rememberPreference(backgroundProgressKey, BackgroundProgress.MiniPlayer)
    val showProgress = backgroundProgress == BackgroundProgress.Both || backgroundProgress == BackgroundProgress.MiniPlayer
    val progressFraction = if (showProgress) rememberMiniPlayerProgress(player, playerUpdateTrigger) else ({ 0f })
    val effectRotationEnabled by rememberPreference(effectRotationKey, false)
    val shouldBePlayingTransition = updateTransition(shouldBePlaying, label = stringResource(R.string.player_debug_should_be_playing))
    val playPauseRoundness by shouldBePlayingTransition.animateDp(
        transitionSpec = { tween(durationMillis = 100, easing = LinearEasing) },
        label = stringResource(R.string.player_debug_play_pause_roundness),
        targetValueByState = { if (it) 24.dp else 12.dp }
    )

    // Issue #881 (gh-881): tolerant saver (spec M5).
    var isRotated by rememberSaveable(saver = TolerantBoolStateSaver("miniPlayer.isRotated")) { mutableStateOf(false) }

    val sheetGestureState by rememberMiniPlayerSheetGestureState { playerSheetState.progress }
    LaunchedEffect(sheetGestureState.isOpening) {
        if (sheetGestureState.isOpening && dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
            try {
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isRotated) 360F else 0f,
        animationSpec = tween(durationMillis = 200), label = ""
    )
    val disableClosingPlayerSwipingDown by rememberPreference(disableClosingPlayerSwipingDownKey, false)

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val isFloating = NavigationBarPosition.BottomFloating.isCurrent()
    val glass = rememberGlassSettings()
    val useGlass = glass.usesGlassMaterial(GlassArea.MiniPlayer)
    val inQueue = LocalEmbeddedQueueMini.current
    val parentMaterial = LocalGlassContainerBackdrop.current
    val glassBackdrop = if (inQueue) parentMaterial else LocalGlassMenuHost.current?.let {
        it.miniPlayerBackdrop ?: it.backdrop
    }
    val controlsBackdrop = rememberLayerBackdrop()
    val miniBodyProgress = remember(inQueue, playerSheetState) {
        { if (inQueue) 0f else playerSheetState.progress }
    }
    val controlMaterial = rememberMiniPlayerControlBackdrop(
        if (useGlass) parentMaterial else null, controlsBackdrop, miniBodyProgress,
    )
    // Keep the raw scene available to other descendants, while controls sample the
    // material-only export so the moving progress remains visible through their seats.
    val controlSource = if (!useGlass) controlMaterial
        else if (glassBackdrop === parentMaterial) LocalGlassMaterialSource.current ?: glassBackdrop
        else glassBackdrop
    val visibleMiniPlayerBackdrop = rememberLayerBackdrop()
    val miniOverlayBackdrop = glassSceneOverlayBackdrop(visibleMiniPlayerBackdrop)
    val shape = uiRoundnessShape()
    val density = LocalDensity.current
    var measuredWidth by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val showArtwork by remember(foldProgress, density) {
        derivedStateOf { foldProgress() <= .01f || with(density) { measuredWidth >= 156.dp.roundToPx() } }
    }
    val showSongInfo by remember(foldProgress, density) {
        derivedStateOf { foldProgress() <= .01f || with(density) { measuredWidth > 96.dp.roundToPx() } }
    }
    // Keep the composition stable while the dock remeasures the miniplayer each frame.
    val contentHeight = remember(foldProgress, expandedMiniHeight) {
        {
            val progress = foldProgress()
            expandedMiniHeight * (1f - progress) +
                app.n_zik.android.components.navigation.dock.FloatingDockDimensions.compactHeight.dp * progress
        }
    }
    val bodySpacing = remember(foldProgress) { miniFoldSpacing(12.dp, foldProgress) }
    val controlsSpacing = remember(useGlass, foldProgress) {
        miniFoldSpacing(miniPlayerButtonSpacing(useGlass), foldProgress)
    }
    val bodyPadding = remember(dockPresentation, foldProgress) {
        if (!dockPresentation) PaddingValues(0.dp) else miniFoldPadding { PaddingValues(horizontal = 10.dp * foldProgress()) }
    }
    val artworkPadding = remember(dockPresentation, foldProgress) {
        if (!dockPresentation) PaddingValues(0.dp) else miniFoldPadding { PaddingValues(end = 8.dp * foldProgress()) }
    }

    // The swipe waits for twice the usual movement, so the vertical open gesture wins on a slow diagonal drag
    WithDoubledTouchSlop {
    SwipeToDismissBox(
        modifier = Modifier
            .layout { measurable, constraints ->
                val margin = (16.dp * (1f - foldProgress())).roundToPx()
                val child = measurable.measure(constraints.offset(horizontal = -2 * margin))
                layout(constraints.constrainWidth(child.width + 2 * margin), child.height) {
                    child.placeRelative(margin, 0)
                }
            }
            .shadow(elevation = if (isFloating && !useGlass) 8.dp else 0.dp, shape = shape),
        enableDismissFromStartToEnd = sheetGestureState.isAtRest,
        enableDismissFromEndToStart = sheetGestureState.isAtRest,
        // Switched off once the action ran: that ends a drag the finger keeps alive, so the box
        // goes back to its position without waiting for the release.

        // Also off as soon as the sheet leaves its original position (being opened).
        gesturesEnabled = !swipeActionLatch.isFired && sheetGestureState.isAtRest,
        state = dismissState,
        backgroundContent = {
            /*
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                },
                label = stringResource(R.string.common_debug_background)
            )
             */

            val offset = try { dismissState.requireOffset() } catch (e: Exception) { 0f }
            val isSwipeRevealed = offset.isFinite() && offset != 0f
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .glassSwipeActionBackground(
                        originalColor = miniPlayerSwipeBackgroundColor(glass, isSwipeRevealed,
                            surfaceColor(colorPalette().background1)),
                        shape = shape,
                        isRevealed = isSwipeRevealed,
                        settings = glass,
                        area = GlassArea.MiniPlayer,
                        backdrop = glassBackdrop,
                    )
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
        val nativeBackground = surfaceColor(colorPalette.background2)
        val miniPlayerBlack = LocalNativeSurfaceMix.current?.miniPlayerBlack
            ?: if (colorPaletteName == ColorPaletteName.PureBlack || colorPaletteName == ColorPaletteName.ModernBlack) 1f else 0f
        Box(
            modifier = Modifier
                .then(
                    if (miniOverlayBackdrop === visibleMiniPlayerBackdrop) Modifier.layerBackdrop(visibleMiniPlayerBackdrop)
                    else Modifier,
                )
                .combinedClickable(
                    onLongClick = {
                        requestQueue?.invoke() ?: navController?.navigate(NavRoutes.queue.name)
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onClick = {
                        //if (showPlayer != null)
                        showPlayer()
                        //else
                        //    navController?.navigate("player")
                    }
                )
                .fillMaxWidth()
                .onSizeChanged { measuredWidth = it.width }
        ) {
            Box(Modifier.matchParentSize()
                .clip(shape)
                .glassMiniPlayerBodyVisibility(glass, miniBodyProgress)
                .glassMiniPlayerBackground(
                    glass, shape, glassBackdrop,
                    lerp(nativeBackground, Color.Black, miniPlayerBlack),
                    exportedBackdrop = controlsBackdrop,
                    foldInProgress = folding.takeIf { isFloating || NavigationBarPosition.Bottom.isCurrent() },
                    progressColor = colorPalette.favoritesOverlay,
                    progressFraction = progressFraction,
                )
            )
            Row(
                horizontalArrangement = bodySpacing,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .padding(bodyPadding),
            ) {
            // Export only the rendered body, never the cover or these controls themselves.
            CompositionLocalProvider(
                LocalGlassBackdrop provides controlMaterial,
                LocalGlassContainerBackdrop provides controlMaterial,
                LocalGlassMaterialSource provides controlSource,
                LocalGlassOverlayBackdrop provides miniOverlayBackdrop,
            ) {
            Spacer(
                modifier = Modifier.miniFoldSize(width = { 2.dp * (1f - foldProgress()) })
            )

            if (showArtwork) Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.miniFoldSize(height = contentHeight)
                    .padding(artworkPadding)
            ) {
                val isCustomImage = artworkUrl?.let {
                    it.startsWith("file://") || it.contains("app_covers") || it.startsWith("modified:")
                } == true

                Box(
                    modifier = Modifier.miniFoldSize(width = { 48.dp - 12.dp * foldProgress() }, square = true)
                ) {
                    ImageCacheFactory.Thumbnail(
                        shape = thumbnailShape(),
                        thumbnailUrl = artworkUrl,
                        contentScale = if (isCustomImage) ContentScale.Crop else ContentScale.FillHeight,
                        modifier = Modifier.clip( thumbnailShape() )
                                           .fillMaxSize()
                    )

                    // Preserve the original animation geometry, then scale its dots and gaps
                    // together with the artwork instead of squeezing a fixed-width row.
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.requiredSize(48.dp).graphicsLayer {
                            val scale = 1f - .25f * foldProgress()
                            scaleX = scale
                            scaleY = scale
                        }) {
                            NowPlayingSongIndicator(mediaItem.mediaId, player, containerSize = 48.dp)
                        }
                    }

                    if (likeState != null)
                        app.n_zik.android.ui.components.themed.HeaderIconButton(
                            onClick = {},
                            icon = if (likeState == true) getLikedIcon() else getDislikedIcon(),
                            color = if (likeState == false) colorPalette().red else colorPalette().favoritesIcon,
                            iconSize = 10.dp,
                            placementModifier = Modifier.align( Alignment.BottomStart )
                                               .absoluteOffset( x = (-5).dp )
                        )
                }
            }


            if (showSongInfo) Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .miniFoldSize(height = contentHeight)
                    .weight(1f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedVisibility(
                        visible = mediaItem.isSmartShuffleRecommendation,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        Icon(
                            painterResource(R.drawable.smart_shuffle),
                            contentDescription = stringResource(R.string.recommendations_action_smart_shuffle),
                            tint = colorPalette.accent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if ( mediaItem.isExplicit )
                        ExplicitBadge(
                            color = colorPalette.text,
                            modifier = Modifier.size(14.dp),
                            passive = true,
                        )
                    BasicText(
                        text = displayTitle,
                        style = typography().xxs.semiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                    )
                }

                BasicText(
                    text = displayArtist,
                    style = typography().xxs.semiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                )
            }

            Spacer(
                modifier = Modifier.miniFoldSize(width = { 2.dp * (1f - foldProgress()) })
            )

            Row(
                horizontalArrangement = controlsSpacing,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .miniFoldSize(height = contentHeight)
            ) {
                activeButtons.forEach { button ->
                    if (button == MiniPlayerButton.PlayPause) {
                        GlassMiniPlayerPlayPauseButton(
                            onClick = {
                                if (shouldBePlaying) {
                                    binder?.gracefulPause()
                                } else {
                                    binder?.gracefulPlay()
                                }
                                if (effectRotationEnabled) isRotated = !isRotated
                            },
                            backgroundColor = playButtonNativeColor(
                                colorPaletteName, colorPalette().background2, colorPaletteMode,
                                blackCoverage = LocalNativeSurfaceMix.current?.transportBlack,
                            ),
                            contentDescription = stringResource(
                                if (shouldBePlaying) R.string.player_notification_pause else R.string.player_action_play,
                            ),
                            settings = glass,
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
                                    colorFilter = ColorFilter.tint(miniPlayerPlayPauseGlyphColor(glass, controlsColorTextState.value)),
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
                            controlsColorTextState = controlsColorTextState,
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
                                        Toaster.w(R.string.audio_output_android_10_required_message)
                                    }
                                }
                            },
                            onShowPlayer = showPlayer,
                            mediaItem = mediaItem,
                            context = context,
                            player = player,
                            binder = binder,
                            effectRotationEnabled = effectRotationEnabled,
                            isRotated = isRotated,
                            onRotatedChange = { isRotated = it },
                            pendingAction = pendingMiniPlayerAction,
                            navController = navController,
                            onClosePlayer = hidePlayer
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.miniFoldSize(width = { 2.dp * (1f - foldProgress()) })
            )
            }
        }
        }
    }
    }
}

private fun miniFoldSpacing(resting: Dp, progress: () -> Float): Arrangement.Horizontal =
    object : Arrangement.Horizontal {
        override val spacing: Dp get() = resting * (1f - progress())

        override fun Density.arrange(totalSize: Int, sizes: IntArray, layoutDirection: LayoutDirection, outPositions: IntArray) {
            with(Arrangement.spacedBy(spacing)) { arrange(totalSize, sizes, layoutDirection, outPositions) }
        }
    }

private fun miniFoldPadding(values: () -> PaddingValues): PaddingValues = object : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = values().calculateLeftPadding(layoutDirection)
    override fun calculateTopPadding() = values().calculateTopPadding()
    override fun calculateRightPadding(layoutDirection: LayoutDirection) = values().calculateRightPadding(layoutDirection)
    override fun calculateBottomPadding() = values().calculateBottomPadding()
}

private fun Modifier.miniFoldSize(
    width: (() -> Dp)? = null,
    height: (() -> Dp)? = null,
    square: Boolean = false,
): Modifier = layout { measurable, constraints ->
    val widthPx = width?.invoke()?.roundToPx()
    val heightPx = if (square) widthPx else height?.invoke()?.roundToPx()
    val fittedWidth = widthPx?.let(constraints::constrainWidth)
    val fittedHeight = heightPx?.let(constraints::constrainHeight)
    val child = measurable.measure(constraints.copy(
        minWidth = fittedWidth ?: constraints.minWidth,
        maxWidth = fittedWidth ?: constraints.maxWidth,
        minHeight = fittedHeight ?: constraints.minHeight,
        maxHeight = fittedHeight ?: constraints.maxHeight,
    ))
    layout(child.width, child.height) { child.placeRelative(0, 0) }
}
