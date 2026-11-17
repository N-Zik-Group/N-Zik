package app.n_zik.android.ui.screens.player.components

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.SystemClock
import android.util.Rational
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassActionCellHeight
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassRippleProvider
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.SurfaceForegroundContent
import app.n_zik.android.components.glass.effectiveSurfaceColor
import app.n_zik.android.components.glass.glassFilmColor
import app.n_zik.android.components.glass.surfaceGlyphColor
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.LocalGlassMaterialSource
import app.n_zik.android.components.glass.LocalGlassRippleActive
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.navigation.nav.GlassNavigationTabs
import app.n_zik.android.components.navigation.nav.LocalGlassTabReplica
import app.n_zik.android.components.navigation.nav.LocalGlassTabScale
import app.n_zik.android.components.player.PlayerTopAction
import app.n_zik.android.components.player.LocalPlayerBackdropSampling
import app.n_zik.android.surfaceColor
import app.n_zik.android.compose.persist.findActivityNullable
import app.n_zik.android.extensions.pip.isPipSupported
import app.n_zik.android.core.database.Database
import app.n_zik.android.enums.PlayerTimelineSize
import app.n_zik.android.models.ui.toUiMedia
import app.n_zik.android.playback.services.canResolvePlayerVideo
import app.n_zik.android.playback.services.resolvePlayerVideo
import app.n_zik.android.typography
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.collapsedPlayerProgressBar
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.GetControls
import app.n_zik.android.utils.GetSeekBar
import app.n_zik.android.utils.playerSwapControlsWithTimelineKey
import app.n_zik.android.utils.playerTimelineSizeKey
import app.n_zik.android.enums.PlayerOpeningMode
import app.n_zik.android.utils.playerLastModeWasVideoKey
import app.n_zik.android.utils.playerOpeningModeKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.roundToInt

internal val LocalPlayerMusicVideo = staticCompositionLocalOf<PlayerMusicVideo?> { null }

internal class PlayerMusicVideo(
    private val onModeChosen: (Boolean) -> Unit = {},
) {
    var selected by mutableStateOf(false)
    var openingSelection by mutableIntStateOf(0)
        private set
    var fullscreen by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var controlPalette by mutableStateOf<ColorPalette?>(null)
    var manualSearchQuery by mutableStateOf<String?>(null)
    var ready by mutableStateOf(false)
    var player by mutableStateOf<ExoPlayer?>(null)
    var videoSize by mutableStateOf(VideoSize.UNKNOWN)
    var sourceRect: Rect? = null

    val ratio: Float
        get() = videoSize.run {
            if (width > 0 && height > 0) width.toFloat() * pixelWidthHeightRatio / height else 16f / 9f
        }

    fun selectVideo(value: Boolean) {
        selected = value
        failed = false
        if (!value) fullscreen = false
    }

    fun applyOpeningMode(video: Boolean) {
        selectVideo(video)
        openingSelection++
    }

    fun chooseMode(video: Boolean) {
        selectVideo(video)
        onModeChosen(video)
    }

    fun unavailable() {
        selectVideo(false)
        failed = true
    }
}

@Composable
@OptIn(UnstableApi::class)
internal fun rememberPlayerMusicVideo(
    audio: Player?,
    collapsed: Boolean,
    inPip: Boolean,
    enabled: Boolean,
): PlayerMusicVideo {
    val context = LocalContext.current
    val openingMode by rememberPreference(playerOpeningModeKey, PlayerOpeningMode.Audio)
    var lastModeWasVideo by rememberPreference(playerLastModeWasVideoKey, false)
    val onModeChosen by androidx.compose.runtime.rememberUpdatedState<(Boolean) -> Unit> {
        lastModeWasVideo = it
    }
    val session = remember { PlayerMusicVideo { onModeChosen(it) } }
    var openingModeApplied by remember { mutableStateOf(false) }
    var item by remember(audio) { mutableStateOf(audio?.currentMediaItem) }
    var revision by remember(audio) { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }

    LifecycleStartEffect(Unit) {
        started = true
        onStopOrDispose { started = false }
    }
    DisposableEffect(audio) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    session.failed = false
                    item = mediaItem
                    revision++
                }
            }
        }
        audio?.addListener(listener)
        onDispose { audio?.removeListener(listener) }
    }

    LaunchedEffect(enabled, audio, revision, collapsed, inPip) {
        if (!enabled || collapsed && !inPip) {
            openingModeApplied = false
            session.selectVideo(false)
            session.manualSearchQuery = null
        } else if (!inPip && !openingModeApplied && item != null) {
            openingModeApplied = true
            session.applyOpeningMode(
                openingMode.opensInVideo(lastModeWasVideo) && item?.let(::canResolvePlayerVideo) == true,
            )
        }
        if (inPip) session.fullscreen = false
    }

    val active = enabled && started && (!collapsed || inPip) && session.selected
    LaunchedEffect(audio, item, revision, active) {
        val current = item ?: return@LaunchedEffect
        if (!active || audio == null) return@LaunchedEffect
        if (!canResolvePlayerVideo(current)) {
            session.unavailable()
            return@LaunchedEffect
        }
        val duration = audio.duration
        val stream = withContext(NzikDispatchers.MEDIA) { resolvePlayerVideo(current, duration) }
        if (stream == null) {
            session.unavailable()
            return@LaunchedEffect
        }
        val video = ExoPlayer.Builder(context.applicationContext).build()
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { session.ready = true }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                session.videoSize = videoSize
            }

            override fun onPlayerError(error: PlaybackException) {
                Timber.tag("PlayerMusicVideo").w("Video playback failed (%d)", error.errorCode)
                session.unavailable()
            }
        }
        var lastVideoSeekMs = 0L
        var autoSeekUsed = false
        fun synchronize(forceSeek: Boolean) {
            val position = stream.positionForAudio(audio.currentPosition.coerceAtLeast(0))
            val nowMs = SystemClock.elapsedRealtime()
            val driftMs = position - video.currentPosition
            val audioPlaying = audio.isPlaying
            val canCatchUp = audioPlaying && video.playbackState == Player.STATE_READY
            val elapsedSinceSeek = nowMs - lastVideoSeekMs
            if (forceSeek || canCatchUp && abs(driftMs) <= 180L && elapsedSinceSeek >= 3_000L) {
                autoSeekUsed = false
            }
            // A seek flushes the decoder. Allow one per drift episode before relying on rate correction.
            val needsSeek = canCatchUp && !autoSeekUsed && abs(driftMs) > 800L && elapsedSinceSeek >= 2_000L
            val audioParameters = audio.playbackParameters
            if (forceSeek || needsSeek) {
                video.seekTo(position)
                lastVideoSeekMs = nowMs
                autoSeekUsed = !forceSeek && needsSeek
                video.playbackParameters = audioParameters
            } else {
                val correction = if (canCatchUp && abs(driftMs) > 80L) {
                    (driftMs / 2_500f).coerceIn(-0.15f, 0.15f)
                } else 0f
                video.playbackParameters = audioParameters.withSpeed(audioParameters.speed * (1f + correction))
            }
            video.playWhenReady = audioPlaying
        }
        val audioListener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (player.currentMediaItem?.mediaId != current.mediaId) {
                    video.pause()
                    return
                }
                synchronize(events.contains(Player.EVENT_POSITION_DISCONTINUITY))
            }
        }

        try {
            video.volume = 0f
            video.trackSelectionParameters = video.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            val dataSource = DefaultHttpDataSource.Factory().setDefaultRequestProperties(stream.headers)
            val videoItem = MediaItem.Builder().setUri(stream.uri).setMimeType(stream.mimeType).build()
            video.setMediaSource(ProgressiveMediaSource.Factory(dataSource).createMediaSource(videoItem))
            video.addListener(listener)
            audio.addListener(audioListener)
            synchronize(forceSeek = true)
            video.prepare()
            session.player = video
            while (true) {
                delay(750)
                if (audio.currentMediaItem?.mediaId != current.mediaId) break
                synchronize(forceSeek = false)
            }
        } finally {
            audio.removeListener(audioListener)
            video.removeListener(listener)
            if (session.player === video) {
                session.ready = false
                session.player = null
                session.videoSize = VideoSize.UNKNOWN
                session.sourceRect = null
            }
            video.release()
        }
    }
    return session
}

// UI reference: Metrolist KMP (GPL-3.0), classic player mode selector and MusicVideoSurface.
@Composable
internal fun PlayerAudioVideoToggle(
    session: PlayerMusicVideo,
    modifier: Modifier = Modifier,
    onSearchManually: () -> Unit,
    interactive: Boolean = true,
) {
    val palette = colorPalette()
    val glass = rememberGlassSettings(GlassScope.PlayerActions)
    val navbarGlass = rememberGlassSettings().forArea(GlassArea.Nav)
    val useGlass = navbarGlass.usesGlassMaterial(GlassArea.Nav)
    val sampling = LocalPlayerBackdropSampling.current
    var toggleBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // The header can have a different background than the title/transport union. Do not register
    // this region in that union: sample its retained background without changing the shared decision.
    val header = toggleBounds?.let { sampling?.colorAt(it) }
    val plate = if (useGlass) glassFilmColor(navbarGlass, surfaceColor(palette.background1), menu = false)
        else palette.background2
    val toggleSurface = header?.let { effectiveSurfaceColor(it, plate) }
        ?: plate.takeIf { it.alpha == 1f }
    val shape = uiRoundnessShape()
    val manualSearchLabel = stringResource(R.string.player_video_search_manually)
    val modeButton: @Composable (Boolean, Modifier) -> Unit = { video, slot ->
        val selected = session.selected == video
        val label = stringResource(if (video) R.string.player_video_mode else R.string.player_audio_mode)
        val replica = LocalGlassTabReplica.current
        val scale = LocalGlassTabScale.current
        val indication = if (!useGlass || LocalGlassRippleActive.current) LocalIndication.current else null
        val originalTint = when {
            useGlass && (selected || replica) -> palette.text
            useGlass -> palette.textSecondary
            selected -> palette.onAccent
            else -> palette.text
        }
        val glyphPlate = if (!useGlass && selected) palette.accent else toggleSurface ?: plate
        val tint = surfaceGlyphColor(originalTint, glyphPlate)
        val button = slot.then(if (useGlass) {
            Modifier.graphicsLayer { scaleX = scale(); scaleY = scale() }.clip(shape)
        } else {
            Modifier.clip(shape).background(if (selected) palette.accent else Color.Transparent)
        })
        Box(
            button.then(if (replica || !interactive) Modifier else Modifier
                .combinedClickable(
                    interactionSource = null,
                    indication = indication,
                    role = Role.RadioButton,
                    onLongClickLabel = manualSearchLabel.takeIf { video },
                    onLongClick = onSearchManually.takeIf { video },
                    onClick = { session.chooseMode(video) },
                )
                .semantics {
                    contentDescription = label
                    this.selected = selected
                }),
            contentAlignment = Alignment.Center,
        ) {
            if (video && selected && !session.ready) {
                CircularProgressIndicator(Modifier.size(16.dp), color = tint, strokeWidth = 2.dp)
            } else {
                Image(
                    painterResource(if (video) R.drawable.video else R.drawable.headphones),
                    null,
                    Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(tint),
                )
            }
        }
    }
    SurfaceForegroundContent(toggleSurface) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val toggleModifier = Modifier.height(GlassActionCellHeight).width(96.dp).selectableGroup()
            .onGloballyPositioned { toggleBounds = it.boundsInWindow() }
        if (useGlass) {
            GlassRippleProvider(navbarGlass) {
                // Start the indicator at the resolved opening mode. Only in-player changes animate.
                key(session.openingSelection) {
                    GlassNavigationTabs(
                        selectedIndex = if (session.selected) 1 else 0,
                        indices = listOf(0, 1),
                        onTabChanged = { session.chooseMode(it == 1) },
                        settings = navbarGlass,
                        vertical = false,
                        modifier = toggleModifier,
                        containerShape = shape,
                        dragSelectedTab = false,
                    ) {
                        modeButton(false, Modifier.fillMaxSize())
                        modeButton(true, Modifier.fillMaxSize())
                    }
                }
            }
        } else {
            Row(
                toggleModifier.glassAreaContour(glass, GlassArea.ActionButtons, shape)
                    .clip(shape).background(palette.background2).padding(2.dp),
            ) {
                modeButton(false, Modifier.weight(1f).fillMaxSize())
                modeButton(true, Modifier.weight(1f).fillMaxSize())
            }
        }
    }
    }
}

@Composable
@OptIn(UnstableApi::class)
private fun VideoTexture(
    session: PlayerMusicVideo,
    modifier: Modifier = Modifier,
    cropToFill: Boolean = false,
    backdrop: LayerBackdrop? = null,
) {
    val player = session.player ?: return
    val ratio = session.ratio
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var texture by remember { mutableStateOf<TextureView?>(null) }
    var frame by remember(player, backdrop) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(texture, backdrop, player, viewport, cropToFill, ratio) {
        val view = texture ?: return@LaunchedEffect
        if (backdrop == null) return@LaunchedEffect
        var capturedFrame: Pair<SurfaceTexture, Long>? = null
        while (true) {
            val surface = view.surfaceTexture
            // A paused seek changes the playback position before its frame reaches the surface.
            val renderedFrame = surface?.let { it to it.timestamp }
            if (renderedFrame != null && renderedFrame != capturedFrame && view.width > 0 && view.height > 0) {
                // Only the lens needs a copy of the frame. Keep the primary TextureView live.
                val scale = minOf(1f, 144f / maxOf(view.width, view.height))
                val width = (view.width * scale).roundToInt().coerceAtLeast(1)
                val height = (view.height * scale).roundToInt().coerceAtLeast(1)
                view.getBitmap(width, height)?.let {
                    // Refresh retained optical captures once the new video frame is recorded.
                    backdrop.materialChanged()
                    frame = it.asImageBitmap()
                    capturedFrame = renderedFrame
                }
            }
            delay(150)
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { TextureView(it) },
            modifier = Modifier.matchParentSize().onSizeChanged { viewport = it }.onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                session.sourceRect = Rect(
                    bounds.left.roundToInt(), bounds.top.roundToInt(),
                    bounds.right.roundToInt(), bounds.bottom.roundToInt(),
                )
            },
            onReset = null,
            onRelease = player::clearVideoTextureView,
            update = { view ->
                texture = view
                player.setVideoTextureView(view)
                if (cropToFill && viewport.width > 0 && viewport.height > 0) {
                    val viewportRatio = viewport.width.toFloat() / viewport.height
                    view.setTransform(Matrix().apply {
                        setScale(
                            maxOf(1f, ratio / viewportRatio),
                            maxOf(1f, viewportRatio / ratio),
                            viewport.width / 2f,
                            viewport.height / 2f,
                        )
                    })
                } else {
                    view.setTransform(null)
                }
            },
        )
        if (backdrop != null) {
            Canvas(Modifier.matchParentSize().layerBackdrop(backdrop, drawOnScreen = false)) {
                frame?.let {
                    drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                }
            }
        }
    }
}

@Composable
internal fun InlineMusicVideo(
    session: PlayerMusicVideo,
    modifier: Modifier = Modifier,
    artworkPadding: Dp?,
    viewportShape: Shape? = null,
    artwork: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!session.ready) artwork()
        if (!session.fullscreen) {
            val viewport = if (artworkPadding == null) {
                Modifier.aspectRatio(session.ratio)
            } else {
                val coverWidth = (minOf(maxWidth, maxHeight) - artworkPadding * 2).coerceAtLeast(1.dp)
                Modifier.fillMaxWidth().height(coverWidth)
            }
            val captureVideo = session.ready &&
                rememberGlassSettings(GlassScope.PlayerActions).usesGlassMaterial(GlassArea.ActionButtons)
            val videoBackdrop = if (captureVideo) rememberLayerBackdrop() else null
            Box(if (viewportShape == null) viewport.clipToBounds() else viewport.clip(viewportShape)) {
                VideoTexture(
                    session,
                    Modifier.matchParentSize(),
                    cropToFill = artworkPadding != null,
                    backdrop = videoBackdrop,
                )
                if (session.ready) {
                    CompositionLocalProvider(
                        LocalGlassBackdrop provides (videoBackdrop ?: LocalGlassBackdrop.current),
                        LocalGlassContainerBackdrop provides (videoBackdrop ?: LocalGlassContainerBackdrop.current),
                        LocalGlassMaterialSource provides (videoBackdrop ?: LocalGlassMaterialSource.current),
                    ) {
                        VideoWindowButtons(
                            session,
                            Modifier.align(Alignment.BottomEnd)
                                .padding(end = 8.dp, bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MusicVideoPresentation(session: PlayerMusicVideo, audio: Player?, inPip: Boolean) {
    val context = LocalContext.current
    val activity = context.findActivityNullable()
    var showControls by remember { mutableStateOf(true) }
    var playing by remember(audio) { mutableStateOf(audio?.isPlaying == true) }
    var shouldBePlaying by remember(audio) { mutableStateOf(audio?.playWhenReady == true) }
    var buffering by remember(audio) { mutableStateOf(audio?.playbackState == Player.STATE_BUFFERING) }
    var media by remember(audio) { mutableStateOf(audio?.currentMediaItem) }
    var position by remember(audio) { mutableStateOf(audio?.currentPosition ?: 0L) }
    var scrubbing by remember(audio) { mutableStateOf(false) }

    BackHandler(enabled = !inPip) { session.fullscreen = false }

    DisposableEffect(activity, inPip) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousBehavior = controller?.systemBarsBehavior
        val previousOrientation = activity?.requestedOrientation
        if (!inPip) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (!inPip && previousOrientation != null) activity.requestedOrientation = previousOrientation
            if (!inPip && controller != null) {
                if (previousBehavior != null) controller.systemBarsBehavior = previousBehavior
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(audio, inPip) {
        if (inPip || audio == null) return@DisposableEffect onDispose {}
        fun syncPlaybackState(player: Player) {
            playing = player.isPlaying
            shouldBePlaying = player.playWhenReady
            buffering = player.playbackState == Player.STATE_BUFFERING
            media = player.currentMediaItem
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                syncPlaybackState(player)
                if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
                    position = player.currentPosition
                }
            }
        }
        audio.addListener(listener)
        syncPlaybackState(audio)
        position = audio.currentPosition
        onDispose { audio.removeListener(listener) }
    }

    LaunchedEffect(audio, inPip) {
        if (inPip || audio == null) return@LaunchedEffect
        while (true) {
            position = audio.currentPosition
            delay(250)
        }
    }
    LaunchedEffect(showControls, playing, scrubbing, inPip) {
        if (!inPip && showControls && playing && !scrubbing) {
            delay(5_000)
            showControls = false
        }
    }

    val captureVideo = !inPip && showControls && (
        rememberGlassSettings(GlassScope.PlayerActions).usesGlassMaterial(GlassArea.ActionButtons) ||
            rememberGlassSettings(GlassScope.PlayerSeek).usesGlassMaterial(GlassArea.ActionButtons) ||
            rememberGlassSettings(GlassScope.PlayerTransport).usesGlassMaterial(GlassArea.ActionButtons)
        )
    val videoBackdrop = if (captureVideo) rememberLayerBackdrop() else null
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        VideoTexture(session, Modifier.aspectRatio(session.ratio), backdrop = videoBackdrop)
        if (!inPip) CompositionLocalProvider(
            LocalGlassBackdrop provides (videoBackdrop ?: LocalGlassBackdrop.current),
            LocalGlassContainerBackdrop provides (videoBackdrop ?: LocalGlassContainerBackdrop.current),
            LocalGlassMaterialSource provides (videoBackdrop ?: LocalGlassMaterialSource.current),
        ) {
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showControls = !showControls },
                ),
            )
            if (showControls) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                Row(
                    Modifier.align(Alignment.TopStart).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    VideoWindowButtons(session, Modifier)
                    Text(
                        media?.mediaMetadata?.title?.toString().orEmpty(),
                        color = colorPalette().text,
                        style = typography().s,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val currentMedia = media
                if (audio != null && currentMedia != null) {
                    FullscreenVideoControls(
                        session = session,
                        audio = audio,
                        media = currentMedia,
                        shouldBePlaying = shouldBePlaying,
                        isBuffering = buffering,
                        position = { position },
                        onScrubbingChanged = { scrubbing = it },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(UnstableApi::class)
private fun FullscreenVideoControls(
    session: PlayerMusicVideo,
    audio: Player,
    media: MediaItem,
    shouldBePlaying: Boolean,
    isBuffering: Boolean,
    position: () -> Long,
    onScrubbingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timelineSize by rememberPreference(playerTimelineSizeKey, PlayerTimelineSize.Biggest)
    val swap by rememberPreference(playerSwapControlsWithTimelineKey, false)
    val song by remember(media.mediaId) { Database.songTable.findById(media.mediaId).distinctUntilChanged() }
        .collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)
    val latestScrubbingChanged by androidx.compose.runtime.rememberUpdatedState(onScrubbingChanged)
    val timeline: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().pointerInput(media.mediaId) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                latestScrubbingChanged(true)
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                } finally {
                    latestScrubbingChanged(false)
                }
            }
        }) {
            GetSeekBar(
                position = position,
                duration = { audio.duration },
                mediaId = media.mediaId,
                media = media.toUiMedia(audio.duration),
                shouldBePlaying = shouldBePlaying,
                isBuffering = isBuffering,
            )
        }
    }
    val transport: @Composable () -> Unit = {
        GetControls(
            timelineHorizontalInset = timelineSize.size.dp,
            binder = LocalPlayerServiceBinder.current,
            player = audio,
            position = position,
            shouldBePlaying = shouldBePlaying,
            isBuffering = isBuffering,
            likedAt = song?.likedAt,
            mediaId = media.mediaId,
            onBlurScaleChange = {},
            dynamicColorPalette = session.controlPalette ?: colorPalette(),
            installMedleyEffect = false,
        )
    }
    Column(
        modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = timelineSize.size.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (swap) {
            transport()
            timeline()
        } else {
            timeline()
            transport()
        }
    }
}

@Composable
private fun VideoWindowButtons(session: PlayerMusicVideo, modifier: Modifier) {
    val activity = LocalContext.current.findActivityNullable()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (Build.VERSION.SDK_INT >= 26 && isPipSupported && activity != null) {
            VideoAction(R.drawable.images_sharp, R.string.settings_picture_in_picture_open) {
                val ratio = session.ratio.coerceIn(1f / 2.39f, 2.39f)
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational((ratio * 100).roundToInt(), 100))
                    .setSourceRectHint(session.sourceRect?.takeUnless(Rect::isEmpty))
                    .build()
                runCatching { activity.enterPictureInPictureMode(params) }.onFailure {
                    Timber.tag("PlayerMusicVideo").w("Unable to enter video picture-in-picture")
                }
            }
        }
        VideoAction(
            if (session.fullscreen) R.drawable.close else R.drawable.maximize,
            if (session.fullscreen) R.string.player_video_exit_fullscreen else R.string.player_video_fullscreen,
        ) {
            session.fullscreen = !session.fullscreen
        }
    }
}

@Composable
private fun VideoAction(@DrawableRes icon: Int, label: Int, onClick: () -> Unit) {
    PlayerTopAction(
        icon = icon,
        color = colorPalette().collapsedPlayerProgressBar,
        rotation = 0f,
        contentDescription = stringResource(label),
        glassFilm = colorPalette().background0.copy(alpha = 0.12f),
        onClick = onClick,
    )
}
