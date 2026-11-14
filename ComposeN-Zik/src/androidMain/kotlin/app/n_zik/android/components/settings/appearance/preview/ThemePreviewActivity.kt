package app.n_zik.android.components.settings.appearance.preview

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.PixelCopy
import android.view.Window
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import app.n_zik.android.components.navigation.Skeleton
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.compose.rememberNavController
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
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.LocalGlassPlayerOverlayActive
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.navigation.AppNavigation
import app.n_zik.android.components.navigation.dock.FloatingDockDimensions
import app.n_zik.android.components.navigation.dock.FloatingDockEnvironment
import app.n_zik.android.components.navigation.dock.FloatingDockState
import app.n_zik.android.components.navigation.nav.GlassNavigationBarHeight
import app.n_zik.android.components.navigation.nav.GlassNavigationPlayerGap
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.usesGlassMaterial
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
import app.n_zik.android.utils.navigationBarPositionKey
import app.n_zik.android.utils.searchResultScreenTabIndexKey
import app.n_zik.android.utils.pauseSearchHistoryKey
import app.n_zik.android.utils.transitionEffectKey
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber

open class ThemePreviewActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer

    /** Only the debug manifest exposes the automated catalog capture activity. */
    protected open val automation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val document = intent.getStringExtra(Document)
            ?: if (automation) intent.getStringExtra(EncodedDocument)?.let {
                String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8)
            } else null
        player = ExoPlayer.Builder(this).build().apply {
            volume = 0f
            setMediaItems(ThemePreviewDemo.mediaItems, 0, ThemePreviewDemo.positionMs)
            prepare()
        }
        setContent {
            CaptureGallery(document, player, automation, window, filesDir)
        }
    }

    override fun onDestroy() {
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    companion object {
        private const val Document = "themeDocument"
        private const val EncodedDocument = "themeDocumentBase64"

        fun intent(context: Context, document: String? = null): Intent =
            Intent(context, ThemePreviewActivity::class.java).putExtra(Document, document)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class, ExperimentalTextApi::class, ExperimentalComposeUiApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun ThemeStoreScreen(navController: NavController, miniPlayer: @Composable () -> Unit) {
    val context = LocalContext.current
    val title = stringResource(R.string.theme_store_title)
    val floating = NavigationBarPosition.BottomFloating.isCurrent()
    val bottom = Dimensions.navBarBottomPadding(floating) + if (floating) 10.dp else 15.dp
    Skeleton(navController = navController, topInsetHandledByContent = true, miniPlayer = miniPlayer,
        navBarContent = { item -> item(0, title, R.drawable.ui) }) { _ ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = rememberPageChromeContentPadding(PaddingValues(bottom = bottom))) {
            item {
                app.n_zik.android.ui.components.themed.HeaderWithIcon(
                    title = title, iconId = R.drawable.color_palette, enabled = false, modifier = Modifier, onClick = {},
                )
                SettingsDescription(
                    text = stringResource(R.string.theme_store_preview_description),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            item {
                app.n_zik.android.components.settings.common.OtherSettingsEntry(
                    title = stringResource(R.string.theme_store_preview_section),
                    text = "",
                    icon = R.drawable.ui,
                    onClick = { context.startActivity(ThemePreviewActivity.intent(context)) },
                )
            }
        }
    }
}

private data class CaptureFrame(val scene: ThemePreviewScene, val glass: Boolean) {
    val name: String get() = "${scene.fileName}-${if (glass) "glass" else "normal"}"
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class, ExperimentalTextApi::class, ExperimentalComposeUiApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun CaptureGallery(document: String?, player: ExoPlayer, automation: Boolean, window: Window, filesDir: File) {
    val host = LocalContext.current
    val requestedScene = if (automation) {
        (host as? ComponentActivity)?.intent?.getStringExtra("scene")?.let { requested ->
            ThemePreviewScene.entries.firstOrNull { it.fileName == requested }
        }
    } else null
    val inspectOnly = requestedScene != null &&
        (host as? ComponentActivity)?.intent?.getBooleanExtra("inspectOnly", false) == true
    val scenes = requestedScene?.let(::listOf) ?: ThemePreviewScene.entries
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
    val completionKey = document?.let { Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) }.orEmpty()
    val working = remember { File(filesDir, "theme-preview-working") }
    val output = remember { File(filesDir, "theme-preview-output") }

    BackHandler(frame != null) {
        frameIndex = -1
    }
    if (frame != null) {
        val theme = if (frame.glass) glass else normal
        key(frame) {
            val previewContext = remember {
                ThemePreviewContext.create(
                    host,
                    theme.preferences + mapOf(
                        transitionEffectKey to "None",
                        pauseSearchHistoryKey to true,
                    ) + (frame.scene.searchTab?.let { mapOf(searchResultScreenTabIndexKey to it) }.orEmpty()),
                )
            }
            CompositionLocalProvider(LocalContext provides previewContext) {
                PreviewPage(ThemePreviewSession(frame.scene, player), theme)
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
                if (inspectOnly) return@LaunchedEffect
                try {
                    if (frameIndex == 0) withContext(NzikDispatchers.DATA) {
                        resetCaptureFolder(working)
                        staging.clear()
                    }
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !theme.appearance.colorPalette.isDark
                        isAppearanceLightNavigationBars = !theme.appearance.colorPalette.isDark
                    }
                    kotlinx.coroutines.withTimeoutOrNull(5_000) {
                        while (player.duration <= 0 && player.playerError == null) delay(50)
                    }
                    check(player.duration > 0) { "Preview audio did not load" }
                    delay(2200)
                    withFrameNanos { }
                    val bitmap = window.captureFrame()
                    try {
                        val destination = File(working, "${frame.name}.webp")
                        withContext(NzikDispatchers.DATA) {
                            destination.outputStream().use { stream ->
                                @Suppress("DEPRECATION")
                                check(bitmap.compress(Bitmap.CompressFormat.WEBP, 92, stream))
                            }
                            staging[frame] = destination
                            if (frameIndex == frames.lastIndex) {
                                File(working, "complete").writeText(completionKey)
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                    if (frameIndex == frames.lastIndex) {
                        gallery = withContext(NzikDispatchers.DATA) { publishCapture(working, output, staging) }
                        frameIndex = -1
                    } else {
                        frameIndex++
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.tag("ThemePreview").e(error, "Capture failed for %s", frame.name)
                    failed = frame
                    frameIndex = -1
                }
            }
        }
    } else {
        LaunchedEffect(Unit) { withContext(NzikDispatchers.DATA) { resetCaptureFolder(working) } }
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
    val fullPlayer = session.scene in setOf(ThemePreviewScene.PLAYER, ThemePreviewScene.QUEUE, ThemePreviewScene.LYRICS)
    val navPosition = (theme.preferences[navigationBarPositionKey] as? String)?.let { position ->
        NavigationBarPosition.entries.firstOrNull { it.name == position }
    } ?: NavigationBarPosition.BottomFloating

    val dockState = remember { FloatingDockState() }
    val dockProgress = remember { mutableStateOf(0f) }
    val hasTabs = session.scene !in setOf(ThemePreviewScene.ALBUM, ThemePreviewScene.ARTIST, ThemePreviewScene.PLAYLIST, ThemePreviewScene.LOCAL_PLAYLIST)
    val dock = FloatingDockEnvironment(dockState, null, navPosition.isHorizontal, dockProgress, navPosition.isBottomType,
        classic = navPosition == NavigationBarPosition.Bottom, floating = navPosition.isFloating)
    val glassNav = theme.glass.usesGlassMaterial(GlassArea.Nav)
    val navBottomPadding = Dimensions.navBarBottomPadding(navPosition.isFloating)
    val navClearance = when {
        fullPlayer || !hasTabs || !navPosition.isBottomType -> 0.dp
        navPosition.isFloating -> (FloatingDockDimensions.expandedHeight + FloatingDockDimensions.componentGap).dp
        glassNav -> GlassNavigationBarHeight + GlassNavigationPlayerGap
        else -> Dimensions.standardNavBarHeight + 4.dp
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val miniHeight = Dimensions.collapsedPlayer
        val safeInsets = WindowInsets.safeDrawing
        val safePadding = safeInsets.asPaddingValues()
        val sheet = rememberPlayerSheetState(0.dp, maxHeight, miniHeight + safePadding.calculateBottomPadding(),
            if (fullPlayer) expandedAnchor else collapsedAnchor)
        val windowInsets = safeInsets.add(WindowInsets(bottom = if (fullPlayer) 0.dp else miniHeight))

        CompositionLocalProvider(
            LocalAppearance provides theme.appearance,
            LocalThemePreview provides session,
            LocalGlassPreviewSettings provides theme.glass,
            LocalGlassMenuHost provides host,
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
                                openTabFromShortcut = if (session.scene == ThemePreviewScene.NAVIGATION) HomeScreenTabs.Songs.index else -1,
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
                    val standaloneBottomMargin = if (navPosition.isFloating) navBottomPadding else navBottomPadding + 5.dp
                    val sideWidth = if (hasTabs) Dimensions.navigationRailWidth else 0.dp
                    val leftRail = navPosition == NavigationBarPosition.Left
                    val rightRail = navPosition == NavigationBarPosition.Right
                    val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
                    CustomBottomSheet(
                        state = sheet,
                        modifier = Modifier.fillMaxWidth(),
                        bottomPadding = { navClearance + standaloneBottomMargin },
                        collapsedStartInset = safePadding.calculateStartPadding(layoutDirection) + if (if (rtl) rightRail else leftRail) sideWidth else 0.dp,
                        collapsedEndInset = safePadding.calculateEndPadding(layoutDirection) + if (if (rtl) leftRail else rightRail) sideWidth else 0.dp,
                        collapsedContentHeight = miniHeight,
                        collapsedHeightProvider = { FloatingDockDimensions.expandedMiniHeight.dp },
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

private fun resetCaptureFolder(folder: File) {
    folder.listFiles()?.filter { it.isFile }?.forEach { check(it.delete()) }
    check(folder.isDirectory || folder.mkdir())
}

private fun publishCapture(staging: File, output: File, frames: Map<CaptureFrame, File>): Map<CaptureFrame, File> {
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
