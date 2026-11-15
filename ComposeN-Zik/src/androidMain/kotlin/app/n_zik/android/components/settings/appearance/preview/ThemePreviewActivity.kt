package app.n_zik.android.components.settings.appearance.preview

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.Base64
import android.view.PixelCopy
import android.view.Window
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsSectionCard
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import app.n_zik.android.LocalIsShowingLyrics
import app.n_zik.android.LocalIsShowingVisualizer
import app.n_zik.android.LocalPendingMiniPlayerAction
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.LocalPlayerSheetState
import app.n_zik.android.R
import app.n_zik.android.components.CustomBottomSheet
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.components.ui.UiButton
import app.n_zik.android.components.glass.GlassMenuHostState
import app.n_zik.android.components.glass.GlassRippleProvider
import app.n_zik.android.components.glass.GlassSceneContent
import app.n_zik.android.components.glass.LocalGlassArtworkColor
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.LocalGlassPlayerOverlayActive
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.navigation.AppNavigation
import app.n_zik.android.components.player.APP_HEADER_HEIGHT
import app.n_zik.android.components.player.MINI_PLAYER_TOP_GAP
import app.n_zik.android.components.player.TOP_NAV_BAR_HEIGHT
import app.n_zik.android.components.player.miniPlayerSideInset
import app.n_zik.android.components.player.miniPlayerTopInset
import app.n_zik.android.enums.PlayerPosition
import app.n_zik.android.ui.components.InterfaceLayoutDirection
import app.n_zik.android.utils.playerPositionKey
import app.n_zik.android.components.navigation.dock.FloatingDockDimensions
import app.n_zik.android.components.navigation.dock.FloatingDockEnvironment
import app.n_zik.android.components.navigation.dock.FloatingDockState
import app.n_zik.android.components.navigation.dock.classicDockGeometry
import app.n_zik.android.components.navigation.dock.dockHasAttachedMini
import app.n_zik.android.components.navigation.dock.floatingDockGeometry
import app.n_zik.android.components.navigation.nav.GlassNavigationBarHeight
import app.n_zik.android.components.navigation.nav.GlassNavigationPlayerGap
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.rememberGlassArtworkDisplayColors
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.ui.screens.home.HomeTopMiniPlayerAnchorState
import app.n_zik.android.components.ui.screens.home.LocalHomeTopMiniPlayerAnchor
import app.n_zik.android.components.theme.appearanceBackground
import app.n_zik.android.compose.persist.LocalPersistMap
import app.n_zik.android.enums.HomeScreenTabs
import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.PendingMiniPlayerAction
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.screens.player.MiniPlayer
import app.n_zik.android.ui.screens.player.Player
import app.n_zik.android.ui.screens.player.collapsedAnchor
import app.n_zik.android.ui.screens.player.expandedAnchor
import app.n_zik.android.ui.screens.player.rememberPlayerSheetState
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.hideStatusBarKey
import app.n_zik.android.utils.navigationBarPositionKey
import app.n_zik.android.utils.searchResultScreenTabIndexKey
import app.n_zik.android.utils.pauseSearchHistoryKey
import app.n_zik.android.utils.transitionEffectKey
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

open class ThemePreviewActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var progressNotification: ThemePreviewProgressNotification? = null

    /** Only the debug manifest exposes the automated catalog capture activity. */
    protected open val automation = false

    override fun attachBaseContext(newBase: Context) {
        val english = Configuration().apply { setLocale(Locale.ENGLISH) }
        super.attachBaseContext(newBase.createConfigurationContext(english))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
        val submissionCapture = intent.getBooleanExtra(SubmissionCapture, false)
        if (submissionCapture) progressNotification = ThemePreviewProgressNotification(this)
        val document = intent.getStringExtra(Document)
            ?: if (automation) {
                if (intent.getBooleanExtra("useDefaultTheme", false)) defaultThemeAppearanceDocument()
                else intent.getStringExtra(EncodedDocument)?.let { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }
            } else null
        setContent {
            var ready by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var failed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var attempt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
            LaunchedEffect(attempt) {
                failed = false
                progressNotification?.show()
                try {
                    ThemePreviewAssets.prepare(applicationContext)
                    ThemePreviewDemo.useAssets(applicationContext)
                    player = ExoPlayer.Builder(this@ThemePreviewActivity).build().apply {
                        volume = 0f
                        setMediaItems(ThemePreviewDemo.mediaItems, 0, ThemePreviewDemo.positionMs)
                        prepare()
                    }
                    ready = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.tag("ThemePreview").w(error, "Could not load demo assets")
                    progressNotification?.cancel()
                    failed = true
                }
            }
            if (ready) {
                CaptureGallery(document, player, automation || submissionCapture, window, filesDir,
                    progressNotification = progressNotification,
                    onCancel = if (submissionCapture) ({ finish() }) else null,
                    onCaptured = if (submissionCapture) { files ->
                        setResult(RESULT_OK, Intent().putStringArrayListExtra(CapturedFiles, ArrayList(files.map(File::getAbsolutePath))))
                        finish()
                    } else null)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (failed) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText(stringResource(R.string.theme_store_catalog_unavailable))
                            UiButton(onClick = { attempt++ }) {
                                androidx.compose.material3.Text(stringResource(R.string.common_action_retry))
                            }
                        }
                    } else {
                        val appearance = rememberThemePreviewAppearance(document, false)
                        CompositionLocalProvider(LocalAppearance provides appearance.appearance) {
                            app.n_zik.android.components.ui.InlineLoader()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        progressNotification?.cancel()
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    companion object {
        private const val Document = "themeDocument"
        private const val EncodedDocument = "themeDocumentBase64"
        private const val SubmissionCapture = "submissionCapture"
        internal const val CapturedFiles = "capturedFiles"

        fun submissionIntent(context: Context, document: String): Intent =
            intent(context, document).putExtra(SubmissionCapture, true)

        fun intent(context: Context, document: String? = null): Intent =
            Intent(context, ThemePreviewActivity::class.java).putExtra(Document, document)
    }
}

private data class CaptureFrame(val scene: ThemePreviewScene, val glass: Boolean) {
    val name: String get() = "${scene.fileName}-${if (glass) "glass" else "normal"}"
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class, ExperimentalTextApi::class, ExperimentalComposeUiApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun CaptureGallery(
    document: String?, player: ExoPlayer, automation: Boolean, window: Window, filesDir: File,
    progressNotification: ThemePreviewProgressNotification? = null,
    onCancel: (() -> Unit)? = null,
    onCaptured: ((List<File>) -> Unit)? = null,
) {
    val host = LocalContext.current
    val intent = (host as? ComponentActivity)?.intent
    val requestedScenes = if (automation) {
        intent?.getStringExtra("scene")?.split(',')?.mapNotNull { requested ->
            ThemePreviewScene.entries.firstOrNull { it.fileName == requested }
        }?.distinct().orEmpty()
    } else emptyList()
    val inspectOnly = requestedScenes.size == 1 && intent?.getBooleanExtra("inspectOnly", false) == true
    val playbackOnly = automation && intent?.getBooleanExtra("playbackOnly", false) == true
    val scenes = requestedScenes.ifEmpty { ThemePreviewScene.entries.filter { !playbackOnly || it.playing } }
    val normal = rememberThemePreviewAppearance(document, false)
    val glass = rememberThemePreviewAppearance(document, true)
    val frames = remember(scenes) {
        scenes.flatMap { scene -> listOf(CaptureFrame(scene, false), CaptureFrame(scene, true)) }
    }
    val staging = remember { mutableMapOf<CaptureFrame, File>() }
    var gallery by remember { mutableStateOf<Map<CaptureFrame, File>>(emptyMap()) }
    var frameIndex by remember { mutableStateOf(if (automation) 0 else -1) }
    var failed by remember { mutableStateOf<CaptureFrame?>(null) }
    val frame = frames.getOrNull(frameIndex)
    val captureJob = remember { java.util.concurrent.atomic.AtomicReference<Job?>() }
    val cancelCapture by rememberUpdatedState {
        captureJob.getAndSet(null)?.cancel()
        player.pause()
        progressNotification?.cancel()
        frameIndex = -1
        onCancel?.invoke()
        Unit
    }
    if (frame != null) {
        DisposableEffect(window, inspectOnly) {
            val alreadyKeptAwake = window.attributes.flags and FLAG_KEEP_SCREEN_ON != 0
            val keepAwake = !inspectOnly && !alreadyKeptAwake
            if (keepAwake) window.addFlags(FLAG_KEEP_SCREEN_ON)
            val backCallback = if (Build.VERSION.SDK_INT >= 33) {
                OnBackInvokedCallback { cancelCapture() }.also {
                    window.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_OVERLAY, it,
                    )
                }
            } else null
            onDispose {
                if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
                    window.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
                }
                if (keepAwake) window.clearFlags(FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    val visibleTheme = if (frame?.glass == true) glass else normal
    val dark = visibleTheme.appearance.colorPalette.isDark
    val hideStatusBar = frame != null && visibleTheme.preferences[hideStatusBarKey] == true
    LaunchedEffect(window, dark, hideStatusBar) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
            if (hideStatusBar) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.statusBars())
            } else {
                show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
    val completionKey = document?.let { Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) }.orEmpty()
    val output = remember {
        File(filesDir, if (onCaptured == null) "theme-preview-output" else "theme-submission-previews/${java.util.UUID.randomUUID()}")
    }

    val working = remember(output) {
        if (onCaptured == null) File(filesDir, "theme-preview-working")
        else File(filesDir, "theme-preview-working-${output.name}")
    }

    BackHandler(frame != null && Build.VERSION.SDK_INT < 33, onBack = cancelCapture)
    if (frame != null) {
        val theme = visibleTheme
        val lifecycleOwner = LocalLifecycleOwner.current
        val previewBackOwner = remember(lifecycleOwner) {
            // Demo destinations can install back handlers without handling activity navigation.
            object : OnBackPressedDispatcherOwner {
                override val lifecycle = lifecycleOwner.lifecycle
                override val onBackPressedDispatcher = OnBackPressedDispatcher()
            }
        }
        key(frame) {
            val previewContext = remember {
                ThemePreviewContext.create(
                    host,
                    theme.preferences + mapOf(
                        transitionEffectKey to "None",
                        pauseSearchHistoryKey to true,
                    ) + (frame.scene.searchTab?.let { mapOf(searchResultScreenTabIndexKey to it) }.orEmpty()),
                    frame.scene,
                )
            }
            val session = remember(frame, player) { ThemePreviewSession(frame.scene, player) }
            CompositionLocalProvider(
                LocalContext provides previewContext,
                LocalOnBackPressedDispatcherOwner provides previewBackOwner,
            ) {
                InterfaceLayoutDirection { PreviewPage(session, theme) }
            }
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            })
            LaunchedEffect(frame) {
                player.playWhenReady = frame.scene.playing
                if (!frame.scene.playing || player.currentPosition > ThemePreviewDemo.positionMs + 3_000) {
                    player.seekTo(0, ThemePreviewDemo.positionMs)
                }
                if (inspectOnly) return@LaunchedEffect
                progressNotification?.show(frameIndex, frames.size)
                val job = currentCoroutineContext().job
                captureJob.set(job)
                val captureIndex = frameIndex
                try {
                    if (captureIndex == 0) withContext(NzikDispatchers.DATA) {
                        previewCaptureFiles.withLock { resetCaptureFolder(working) }
                        staging.clear()
                    }
                    withTimeoutOrNull(5_000) {
                        while (player.duration <= 0 && player.playerError == null) delay(50)
                    }
                    check(player.duration > 0) { "Preview audio did not load" }
                    if (frame.scene == ThemePreviewScene.QUEUE || frame.scene == ThemePreviewScene.LOCAL_PLAYLIST) {
                        val rowsPlaced = withTimeoutOrNull(10_000) {
                            snapshotFlow { session.previewRowsPlaced.value }.first { it }
                        }
                        check(rowsPlaced == true) { "Preview list did not render" }
                    }
                    delay(2200)
                    if (frame.scene.playing) check(player.isPlaying) { "Preview playback did not start" }
                    withFrameNanos { }
                    val bitmap = window.captureFrame()
                    try {
                        val destination = File(working, "${frame.name}.webp")
                        withContext(NzikDispatchers.DATA) {
                            previewCaptureFiles.withLock {
                                destination.outputStream().use { stream ->
                                    @Suppress("DEPRECATION")
                                    check(bitmap.compress(Bitmap.CompressFormat.WEBP, 92, stream))
                                }
                                staging[frame] = destination
                                if (captureIndex == frames.lastIndex) {
                                    File(working, "complete").writeText(completionKey)
                                }
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                    if (captureIndex == frames.lastIndex) {
                        gallery = withContext(NzikDispatchers.DATA) {
                            previewCaptureFiles.withLock { publishCapture(working, output, staging) }
                        }
                        player.pause()
                        progressNotification?.cancel()
                        onCaptured?.invoke(gallery.values.toList())
                        frameIndex = -1
                    } else {
                        frameIndex++
                    }
                } catch (cancelled: CancellationException) {
                    if (onCaptured != null) withContext(NonCancellable + NzikDispatchers.DATA) {
                        working.deleteRecursively()
                        output.deleteRecursively()
                    }
                    throw cancelled
                } catch (error: Exception) {
                    Timber.tag("ThemePreview").e(error, "Capture failed for %s", frame.name)
                    player.pause()
                    progressNotification?.cancel()
                    failed = frame
                    frameIndex = -1
                } finally {
                    captureJob.compareAndSet(job, null)
                }
            }
        }
    } else {
        CompositionLocalProvider(LocalAppearance provides normal.appearance) {
            GalleryActions(
                gallery,
                failed,
                onGenerate = {
                    failed = null
                    frameIndex = 0
                },
                onReplace = { target, file -> gallery = gallery + (target to file) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class, ExperimentalTextApi::class, ExperimentalComposeUiApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PreviewPage(session: ThemePreviewSession, theme: ThemePreviewAppearance) {
    val nav = rememberNavController()
    val host = remember { GlassMenuHostState() }
    val lyrics = remember { mutableStateOf(session.scene == ThemePreviewScene.LYRICS) }
    val visualizer = remember { mutableStateOf(false) }
    val pending = remember { mutableStateOf(if (session.scene == ThemePreviewScene.QUEUE) PendingMiniPlayerAction.Queue else null) }
    val persistMap = remember { hashMapOf<String, Any?>() }
    val menu = remember { MenuState() }
    val fullPlayer = session.scene in setOf(
        ThemePreviewScene.PLAYER, ThemePreviewScene.PLAYER_PLAYING, ThemePreviewScene.QUEUE, ThemePreviewScene.LYRICS,
    )
    // Full-player scenes skip AppNavigation, which normally supplies this artwork tint.
    val artworkColor = if (fullPlayer) {
        rememberGlassArtworkDisplayColors(session.player, theme.glass.enabled, theme.appearance.colorPalette.isDark)?.dominant
    } else null
    val navPosition = (theme.preferences[navigationBarPositionKey] as? String)?.let { position ->
        NavigationBarPosition.entries.firstOrNull { it.name == position }
    } ?: NavigationBarPosition.BottomFloating

    val topPlayer = theme.preferences[playerPositionKey] == PlayerPosition.Top.name
    val dockState = remember { FloatingDockState() }
    val dockProgress = remember { mutableStateOf(0f) }
    val currentEntry by nav.currentBackStackEntryAsState()
    val hasTabs = !fullPlayer && dockState.hasDock(currentEntry?.id)
    val topDock = navPosition == NavigationBarPosition.Top
    val classicDock = navPosition == NavigationBarPosition.Bottom || topDock
    val dock = FloatingDockEnvironment(
        dockState, currentEntry?.id, navPosition.isHorizontal, dockProgress, !topPlayer,
        classic = classicDock,
        floating = navPosition.isFloating,
        attachedMini = dockHasAttachedMini(true, navPosition, if (topPlayer) PlayerPosition.Top else PlayerPosition.Bottom),
    )
    val glassNav = theme.glass.usesGlassMaterial(GlassArea.Nav)
    val navBottomPadding = Dimensions.navBarBottomPadding(navPosition.isFloating)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val miniHeight = Dimensions.collapsedPlayer
        val safeInsets = WindowInsets.safeDrawing
        val safePadding = safeInsets.asPaddingValues()
        val topNavHeight = if (glassNav) GlassNavigationBarHeight else TOP_NAV_BAR_HEIGHT
        val safeWidth = maxWidth - with(density) {
            (safeInsets.getLeft(this, androidx.compose.ui.unit.LayoutDirection.Ltr) +
                safeInsets.getRight(this, androidx.compose.ui.unit.LayoutDirection.Ltr)).toDp()
        }
        val dockGeometry = if (classicDock) {
            classicDockGeometry(
                safeWidth.value,
                (if (topDock) topNavHeight else if (glassNav) GlassNavigationBarHeight else Dimensions.navigationBarHeight - 10.dp).value,
                FloatingDockDimensions.expandedMiniHeight,
                dock.hasAttachedMini, 0f,
                expandedGap = if (glassNav) GlassNavigationPlayerGap.value else FloatingDockDimensions.componentGap,
            )
        } else {
            floatingDockGeometry(safeWidth.value, FloatingDockDimensions.expandedHeight,
                FloatingDockDimensions.expandedMiniHeight, dock.hasBottomMini, 0f, hasSearch = false)
        }
        val bottomPadding = when {
            topPlayer -> 5.dp
            navPosition.isBottomType -> navBottomPadding + if (hasTabs) dockGeometry.miniBottomOffset.dp else 0.dp
            else -> navBottomPadding + 5.dp
        }
        val sheet = rememberPlayerSheetState(0.dp, maxHeight, miniHeight + safePadding.calculateBottomPadding(),
            if (fullPlayer) expandedAnchor else collapsedAnchor)
        val windowInsets = safeInsets.add(WindowInsets(
            top = if (!fullPlayer && topPlayer) miniHeight else 0.dp,
            bottom = if (!fullPlayer && !topPlayer) miniHeight else 0.dp,
        ))
        val topInset = miniPlayerTopInset(
            statusBarTop = safePadding.calculateTopPadding(),
            barsHidden = false,
            hasTopNavBar = hasTabs && navPosition == NavigationBarPosition.Top,
            headerHeight = if (navPosition.isHorizontal) APP_HEADER_HEIGHT else 0.dp,
            topNavBarHeight = topNavHeight,
            gap = if (navPosition.isHorizontal) MINI_PLAYER_TOP_GAP else 0.dp,
        )
        val baseTopInset = rememberUpdatedState<(() -> Dp)?>(if (topPlayer) ({ topInset }) else null)
        val topGap = rememberUpdatedState(if (navPosition.isHorizontal) MINI_PLAYER_TOP_GAP else 0.dp)
        val retainTopMiniBelowNavigation = topDock && hasTabs
        val miniBeforeHeader = rememberUpdatedState<Dp?>(dockGeometry.miniHeight.dp.takeIf { retainTopMiniBelowNavigation })
        val homeAnchor = remember { HomeTopMiniPlayerAnchorState(baseTopInset, topGap, miniBeforeHeader) }
        val homePlacement by remember(homeAnchor) { derivedStateOf { homeAnchor.placementKey() } }
        val sideHomeGap = if (navPosition.isHorizontal) 0.dp else MINI_PLAYER_TOP_GAP
        val homeMiniTopInset = remember(homeAnchor, homePlacement, sideHomeGap) {
            homeAnchor.miniTopInsetProvider(sideHomeGap)
        }
        val isHome = currentEntry?.destination?.route?.startsWith(NavRoutes.home.name) == true
        val topPadding: (() -> Dp)? = if (topPlayer) ({
            if (isHome && !retainTopMiniBelowNavigation) homeMiniTopInset() ?: topInset else topInset
        }) else null

        CompositionLocalProvider(
            LocalAppearance provides theme.appearance,
            LocalThemePreview provides session,
            LocalGlassPreviewSettings provides theme.glass,
            LocalGlassMenuHost provides host,
            LocalGlassArtworkColor provides artworkColor,
            LocalHomeTopMiniPlayerAnchor provides homeAnchor,
            app.n_zik.android.components.navigation.dock.LocalFloatingDock provides dock,
            LocalGlassPlayerOverlayActive provides fullPlayer,
            LocalPlayerAwareWindowInsets provides windowInsets,
            LocalPlayerSheetState provides sheet,
            LocalIsShowingLyrics provides lyrics,
            LocalIsShowingVisualizer provides visualizer,
            LocalPendingMiniPlayerAction provides pending,
            LocalPersistMap provides persistMap,
            LocalMenuState provides menu,
        ) {
            GlassRippleProvider {
                GlassSceneContent(host, enabled = theme.glass.enabled, modifier = Modifier.appearanceBackground()) {
                    if (!fullPlayer) {
                            AppNavigation(
                                navController = nav,
                                openTabFromShortcut = when (session.scene) {
                                    ThemePreviewScene.HOME -> HomeScreenTabs.QuickPics.index
                                    ThemePreviewScene.NAVIGATION -> HomeScreenTabs.Songs.index
                                    else -> -1
                                },
                                hasTopNavBarVisible = navPosition == NavigationBarPosition.Top,
                                hasSideNavBarVisible = !navPosition.isHorizontal,
                                hasPageNavBar = hasTabs,
                            )
                            LaunchedEffect(session.scene) {
                                when {
                                    session.scene.searchTab != null -> nav.navigate("${NavRoutes.searchResults.name}/${Uri.encode(ThemePreviewDemo.searchQuery)}")
                                    session.scene == ThemePreviewScene.ALBUM -> nav.navigate("${NavRoutes.album.name}/${ThemePreviewDemo.firstAlbumId}")
                                    session.scene == ThemePreviewScene.ARTIST -> nav.navigate("${NavRoutes.artist.name}/${ThemePreviewDemo.firstAlbumId}-artist")
                                    session.scene == ThemePreviewScene.PLAYLIST -> nav.navigate("${NavRoutes.playlist.name}/${ThemePreviewDemo.firstAlbumId}-collection-4")
                                    session.scene == ThemePreviewScene.LOCAL_PLAYLIST -> nav.navigate("${NavRoutes.localPlaylist.name}/1")
                                }
                            }
                    }
                    val layoutDirection = LocalLayoutDirection.current
                    val leftRail = navPosition == NavigationBarPosition.Left
                    val rightRail = navPosition == NavigationBarPosition.Right
                    val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
                    CustomBottomSheet(
                        state = sheet,
                        modifier = Modifier.fillMaxWidth(),
                        bottomPadding = { bottomPadding },
                        topPadding = topPadding,
                        placementKey = listOf(currentEntry?.id, topPlayer, navPosition, hasTabs, topInset,
                            homePlacement.takeIf { isHome && topPlayer && !retainTopMiniBelowNavigation }),
                        collapsedStartInset = miniPlayerSideInset(
                            railWidth = if (if (rtl) rightRail else leftRail) Dimensions.navigationRailWidth else 0.dp,
                            safeInset = safePadding.calculateStartPadding(layoutDirection),
                        ),
                        collapsedEndInset = miniPlayerSideInset(
                            railWidth = if (if (rtl) leftRail else rightRail) Dimensions.navigationRailWidth else 0.dp,
                            safeInset = safePadding.calculateEndPadding(layoutDirection),
                        ),
                        collapsedContentHeight = miniHeight,
                        collapsedHeightProvider = { dockGeometry.miniHeight.dp },
                        disableDismiss = true,
                        collapsedContent = { MiniPlayer(showPlayer = {}, hidePlayer = {}, navController = nav) },
                    ) {
                        if (fullPlayer) Player(nav, onDismiss = {})
                    }
                }
            }
        }
    }
}

private suspend fun Window.captureFrame(): Bitmap = suspendCancellableCoroutine { continuation ->
    val view = decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    PixelCopy.request(this, bitmap, { status ->
        if (!continuation.isActive) {
            bitmap.recycle()
        } else if (status == PixelCopy.SUCCESS) {
            continuation.resume(bitmap)
        } else {
            bitmap.recycle()
            continuation.resumeWithException(IllegalStateException("PixelCopy: $status"))
        }
    }, view.handler)
}

private val previewCaptureFiles = Mutex()

private fun resetCaptureFolder(folder: File) {
    folder.listFiles()?.filter { it.isFile }?.forEach { check(it.delete()) }
    check(folder.isDirectory || folder.mkdir())
}

private fun publishCapture(staging: File, output: File, frames: Map<CaptureFrame, File>): Map<CaptureFrame, File> {
    check(output.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
    val previous = File(output.parentFile, "theme-preview-previous")
    resetCaptureFolder(previous)
    check(previous.delete())
    if (output.exists()) check(output.renameTo(previous))
    try {
        check(staging.renameTo(output))
    } catch (error: Exception) {
        if (previous.exists()) check(previous.renameTo(output))
        throw error
    }
    resetCaptureFolder(previous)
    return frames.keys.associateWith { File(output, "${it.name}.webp") }
}

@Composable
private fun GalleryActions(
    gallery: Map<CaptureFrame, File>,
    failed: CaptureFrame?,
    onGenerate: () -> Unit,
    onReplace: (CaptureFrame, File) -> Unit,
) {
    val context = LocalContext.current
    var replacement by remember { mutableStateOf<CaptureFrame?>(null) }
    var replacementUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var replacementFailed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        replacementUri = uri
    }
    LaunchedEffect(replacementUri) {
        val uri = replacementUri ?: return@LaunchedEffect
        val target = replacement ?: return@LaunchedEffect
        try {
            val file = withContext(NzikDispatchers.DATA) {
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    android.graphics.BitmapFactory.decodeStream(input, null, options)
                }
                check(options.outWidth > 0 && options.outHeight > options.outWidth)
                check(options.outWidth.toLong() * options.outHeight <= 16_000_000)
                val image = checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    checkNotNull(android.graphics.BitmapFactory.decodeStream(input))
                }
                val destination = checkNotNull(gallery[target])
                val atomic = android.util.AtomicFile(destination)
                try {
                    val stream = atomic.startWrite()
                    try {
                        @Suppress("DEPRECATION")
                        check(image.compress(Bitmap.CompressFormat.WEBP, 92, stream))
                        atomic.finishWrite(stream)
                    } catch (error: Exception) {
                        atomic.failWrite(stream)
                        throw error
                    }
                } finally {
                    image.recycle()
                }
                destination
            }
            onReplace(target, file)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("ThemePreview").w(error, "Image replacement failed")
            replacementFailed = true
        } finally {
            replacementUri = null
            replacement = null
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().appearanceBackground().statusBarsPadding().navigationBarsPadding(),
    ) {
        item {
            app.n_zik.android.ui.components.themed.HeaderWithIcon(
                title = stringResource(R.string.theme_store_title),
                iconId = R.drawable.color_palette,
                enabled = false,
                showIcon = true,
                modifier = Modifier,
                onClick = {},
            )
            SettingsDescription(
                text = stringResource(R.string.theme_preview_gallery_description),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        item {
            SettingsSectionCard(
                title = stringResource(R.string.theme_store_preview_section),
                icon = R.drawable.color_palette,
                content = {
                    UiButton(onClick = onGenerate) { PreviewText(stringResource(R.string.theme_preview_generate)) }
                    if (failed != null) PreviewText(stringResource(R.string.theme_preview_scene_failed, failed.name))
                    if (replacementFailed) PreviewText(stringResource(R.string.theme_preview_replacement_failed))
                },
            )
        }
        items(gallery.entries.toList(), key = { it.key.name }) { (target, file) ->
            var bitmap by remember(file, file.lastModified()) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(file, file.lastModified()) {
                bitmap = withContext(NzikDispatchers.DATA) {
                    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                    android.graphics.BitmapFactory.decodeFile(file.path, options)
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                PreviewText(target.name)
                bitmap?.let { Image(it.asImageBitmap(), target.name, Modifier.fillMaxWidth().height(260.dp)) }
                UiButton(onClick = {
                    replacementFailed = false
                    replacement = target
                    picker.launch("image/*")
                }) { PreviewText(stringResource(R.string.theme_preview_replace)) }
            }
        }
    }
}


@Composable
private fun PreviewText(text: String) {
    BasicText(text, style = typography().s.copy(color = colorPalette().text))
}
