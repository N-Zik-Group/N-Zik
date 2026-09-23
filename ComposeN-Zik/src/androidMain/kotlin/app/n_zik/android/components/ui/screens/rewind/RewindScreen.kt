package app.n_zik.android.components.ui.screens.rewind

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.os.SystemClock
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import com.mikepenz.hypnoticcanvas.shaderBackground
import com.mikepenz.hypnoticcanvas.shaders.GradientFlow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.clip
import androidx.navigation.NavController
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.ui.screens.rewind.slides.LocalRewindActive
import app.n_zik.android.components.ui.screens.rewind.slides.rewindColors
import app.n_zik.android.components.ui.screens.rewind.slides.LocalRewindShaderWarm
import app.n_zik.android.components.ui.screens.rewind.slides.RewindAlbumsCard
import app.n_zik.android.components.ui.screens.rewind.slides.RewindColors
import app.n_zik.android.components.ui.screens.rewind.slides.RewindDaysCard
import app.n_zik.android.components.ui.screens.rewind.slides.RewindDeepCutsCard
import app.n_zik.android.components.ui.screens.rewind.slides.RewindDiscoveryCard
import app.n_zik.android.components.ui.screens.rewind.slides.RewindFinaleCard
import app.n_zik.android.components.ui.screens.rewind.slides.RewindIntroCard
import app.n_zik.android.components.ui.screens.rewind.slides.RewindListenerBadgeCard
import app.n_zik.android.components.ui.screens.rewind.slides.RewindListeningDaysCard
import app.n_zik.android.components.ui.screens.rewind.slides.RewindMonthlyCard
import app.n_zik.android.components.ui.screens.rewind.slides.RewindPeakTimeCard
import app.n_zik.android.components.ui.screens.rewind.slides.RewindTopAlbumCard
import app.n_zik.android.components.ui.screens.rewind.slides.RewindTopArtistsCard
import app.n_zik.android.components.ui.screens.rewind.slides.RewindTopArtistSpotlightCard
import app.n_zik.android.components.ui.screens.rewind.slides.RewindTopSongCard
import app.n_zik.android.components.ui.screens.rewind.slides.RewindTopSongsCard
import app.n_zik.android.components.ui.screens.rewind.slides.RewindTopPlaylistsCard
import app.n_zik.android.components.ui.screens.rewind.slides.RewindTotalTimeCard
import app.n_zik.android.components.ui.screens.rewind.slides.formatRewindNumber
import app.n_zik.android.utils.DataStoreUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

private const val RewindDeckPageCount = 16
private const val MinimumOpeningRevealMs = 6_800L

/**
 * Screenshot capture mode, flipped by the share flow around the bitmap capture. While true the
 * slide shells render their solid-color fallback instead of the animated HypnoticCanvas
 * RuntimeShader background: view.draw() replays the tree on a software canvas, where a
 * RuntimeShader cannot be drawn at all (IllegalArgumentException: Software rendering doesn't
 * support RuntimeShader). Snapshot state (like rewindColors) so the capture coroutine can set
 * it without a composable context.
 */
internal val rewindCaptureMode = mutableStateOf(false)

/**
 * Bar-padding hold for the 16-image export capture. The capture flow hides the system bars so
 * the frames stay clean (no clock, battery icons or nav buttons), which would normally collapse
 * the deck's `statusBarsPadding()` / `navigationBarsPadding()` to zero; while this is true the
 * slide shells pin the ignoring-visibility bar insets instead, so each captured image keeps
 * exactly the same top/bottom padding as the live deck. Must be false outside a capture: a
 * stuck-true state would make the live deck pad with hidden-bar insets.
 */
internal val rewindShareCaptureActive = mutableStateOf(false)

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RewindScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {},
    rewindYear: Int? = null,
    rewindMonth: Int? = null
) {
    val fallbackYear = LocalDate.now().year
    val defaultUsername = stringResource(R.string.rw_default_username)
    var activeYear by remember(rewindYear) { mutableStateOf(rewindYear ?: fallbackYear) }
    var rewindData by remember(activeYear, rewindMonth) { mutableStateOf<RewindData?>(null) }
    var isLoading by remember(activeYear, rewindMonth) { mutableStateOf(true) }
    var username by remember { mutableStateOf(defaultUsername) }
    var shareMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val rootView = LocalView.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { RewindDeckPageCount })
    val palette = colorPalette()

    // The deck is a full-screen pager that swallows the system edge-swipe, and the app-wide
    // "back goes home" preference would pop past the rewind home: any back event while the
    // deck is on screen pops exactly one level, landing on the rewind home.
    BackHandler { navController.popBackStack() }

    // SAF folder picker for the deck export (same flow as the cached-song export): the user
    // chooses where the 16 images land, then the deck plays itself and each settled page is
    // written into the folder. The system share sheet cannot carry a full deck of images
    // through third-party apps, so the deck is exported to a folder instead.
    val exportFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { folderUri ->
        folderUri ?: return@rememberLauncherForActivityResult
        val data = rewindData ?: createEmptyRewindData(activeYear, rewindMonth)
        scope.launch {
            shareMode = true
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    folderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            // Two frames so the shareMode recomposition lands before the deck plays itself.
            withFrameNanos { }
            withFrameNanos { }
            // The deck plays itself: one PixelCopy per page, each after its settle. The
            // display framebuffer carries the GPU-rendered shader backgrounds, which a
            // software canvas could never replay.
            val deckPages = captureRewindDeckPages(
                view = rootView,
                pagerState = pagerState,
                year = data.year,
                month = rewindMonth,
                pageCount = RewindDeckPageCount
            )
            val exported = if (deckPages != null) {
                exportRewindPagesToFolder(context, folderUri, deckPages)
            } else {
                // Last resort: software-replay only the final slide, exported to the same
                // folder. The Bitmap canvas cannot draw a RuntimeShader, so the deck
                // switches to its solid fallback backgrounds for the capture, then restores.
                if (pagerState.currentPage != RewindDeckPageCount - 1) {
                    // The capture may have failed mid-deck: come back to the finale
                    // (fresh reveal, then wait for its footer to settle).
                    pagerState.scrollToPage(RewindDeckPageCount - 1)
                    withFrameNanos { }
                    rewindCaptureMode.value = true
                    delay(1_700)
                    withFrameNanos { }
                    withFrameNanos { }
                } else {
                    rewindCaptureMode.value = true
                    withFrameNanos { }
                    withFrameNanos { }
                }
                val fallbackFile = captureRewindScreenshot(context, rootView, data.year, rewindMonth)
                rewindCaptureMode.value = false
                fallbackFile?.let { exportRewindPagesToFolder(context, folderUri, listOf(it)) } ?: false
            }
            shareMode = false
            if (exported) {
                // Kreate toaster: app-styled confirmation instead of a raw system toast.
                Toaster.done()
            } else {
                Toaster.e(R.string.rw_error_export)
            }
        }
    }

    // Publish the themed palette; the slides read it from the rewindColors snapshot state,
    // which is readable from any context (composable calls, Canvas, remember, defaults).
    LaunchedEffect(palette) {
        rewindColors.value = RewindColors.fromPalette(palette)
    }

    LaunchedEffect(activeYear, rewindMonth) {
        val startedAt = SystemClock.elapsedRealtime()
        try {
            username = withContext(Dispatchers.IO) {
                DataStoreUtils.getString(
                    context,
                    DataStoreUtils.KEY_USERNAME,
                    defaultUsername
                )
            }
            rewindData = RewindDataFetcher.getRewindData(activeYear, rewindMonth)
        } catch (error: Throwable) {
            Timber.e(error, "Failed to build Rewind for %d (month=%s)", activeYear, rewindMonth)
            rewindData = createEmptyRewindData(activeYear, rewindMonth)
        } finally {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            val remaining = (MinimumOpeningRevealMs - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) delay(remaining)
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(rewindColors.value.ink)
    ) {
        if (isLoading) {
            RewindLoadingScreen(
                periodLabel = rewindPeriodLabel(activeYear, rewindMonth),
                isMonthly = rewindMonth != null,
                username = username,
                data = rewindData
            )
        } else {
            val data = rewindData ?: createEmptyRewindData(activeYear, rewindMonth)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { it }
            ) { page ->
                val active = pagerState.currentPage == page && !pagerState.isScrollInProgress
                // Shader is pre-warmed one page early so swiping never "pops" a fresh background
                val shaderWarm = page in (pagerState.currentPage - 1)..(pagerState.currentPage + 1)
                val next: () -> Unit = {
                    scope.launch {
                        if (page < RewindDeckPageCount - 1) {
                            pagerState.animateScrollToPage(
                                page + 1,
                                animationSpec = tween(480, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                }
                CompositionLocalProvider(
                    LocalRewindActive provides active,
                    LocalRewindShaderWarm provides shaderWarm
                ) {
                    when (page) {
                        0 -> RewindIntroCard(data, username, page, RewindDeckPageCount, active, next)
                        1 -> RewindListenerBadgeCard(data, page, RewindDeckPageCount, active, next)
                        2 -> RewindTotalTimeCard(data, page, RewindDeckPageCount, active, next)
                        3 -> RewindTopSongCard(data.topSongs, data.periodLabel, page, RewindDeckPageCount, active, next)
                        4 -> RewindTopArtistsCard(data.topArtists, data.periodLabel, page, RewindDeckPageCount, active, next)
                        5 -> RewindTopArtistSpotlightCard(data.topArtists.firstOrNull(), data.periodLabel, page, RewindDeckPageCount, active, next)
                        6 -> RewindTopSongsCard(data.topSongs, data.periodLabel, page, RewindDeckPageCount, active, next)
                        7 -> RewindDeepCutsCard(data.topSongs, data.periodLabel, page, RewindDeckPageCount, active, next)
                        8 -> RewindPeakTimeCard(data, page, RewindDeckPageCount, active, next)
                        9 -> RewindListeningDaysCard(data, page, RewindDeckPageCount, active, next)
                        10 -> RewindDiscoveryCard(data, page, RewindDeckPageCount, active, next)
                        11 -> RewindTopAlbumCard(data.topAlbums.firstOrNull(), data.periodLabel, page, RewindDeckPageCount, active, next)
                        12 -> RewindAlbumsCard(data.topAlbums, data.periodLabel, page, RewindDeckPageCount, active, next)
                        13 -> RewindTopPlaylistsCard(data.topPlaylists, data.periodLabel, page, RewindDeckPageCount, active, next)
                        14 -> if (rewindMonth != null) {
                            // A single-month deck breaks the month down day by day instead of
                            // showing twelve months of which only one has data
                            RewindDaysCard(data, page, RewindDeckPageCount, active, next)
                        } else {
                            RewindMonthlyCard(data, page, RewindDeckPageCount, active, next)
                        }
                        else -> RewindFinaleCard(
                            data = data,
                            username = username,
                            page = page,
                            pageCount = RewindDeckPageCount,
                            active = active,
                            shareMode = shareMode,
                            onShare = {
                                if (!shareMode) {
                                    // The system folder picker chooses where the exported
                                    // images land; the capture starts once a folder is picked.
                                    exportFolderLauncher.launch(null)
                                }
                            },
                            onRestart = {
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        0,
                                        animationSpec = tween(560, easing = FastOutSlowInEasing)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // The mini player is drawn as on every screen; AppNavigation wraps it in a jelly-spring
        // slide-down + fade while the deck is on screen (same animation as the header hide)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            miniPlayer()
        }
    }
}

/**
 * Deliberately dramatic, non-looping opening sequence.
 * The technical monochrome language is inspired by the reference the user supplied, but the
 * content/branding is NZik and the year always comes from the requested rewind year.
 */
@Composable
private fun RewindLoadingScreen(
    periodLabel: String,
    isMonthly: Boolean,
    username: String,
    data: RewindData?
) {
    val timeline = remember(periodLabel) { Animatable(0f) }
    LaunchedEffect(periodLabel) {
        timeline.snapTo(0f)
        timeline.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = MinimumOpeningRevealMs.toInt(),
                easing = LinearEasing
            )
        )
    }
    val p = timeline.value
    val cream = rewindColors.value.cream
    val ink = rewindColors.value.ink
    val lime = rewindColors.value.lime
    val purple = rewindColors.value.purple
    val pink = rewindColors.value.pink
    val orange = rewindColors.value.orange
    Box(
        modifier = Modifier
            .fillMaxSize()
            .shaderBackground(GradientFlow)
            .background(ink.copy(alpha = 0.35f))
    ) {
        // Campaign shapes run edge-to-edge across the whole screen
        Canvas(Modifier.fillMaxSize()) {
            // Angular campaign fields only. The loader never spins or loops.
            val purpleIn = segment(p, 0.02f, 0.18f)
            val pinkIn = segment(p, 0.10f, 0.27f)
            val orangeIn = segment(p, 0.18f, 0.34f)
            val purpleShape = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.66f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height * 0.40f)
                lineTo(size.width * 0.88f, size.height * 0.32f)
                close()
            }
            drawPath(purpleShape, purple.copy(alpha = purpleIn))
            val pinkShape = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, size.height * 0.72f)
                lineTo(size.width * 0.52f, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(pinkShape, pink.copy(alpha = pinkIn))
            val orangeShape = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.84f, size.height * 0.56f)
                lineTo(size.width, size.height * 0.50f)
                lineTo(size.width, size.height * 0.76f)
                close()
            }
            drawPath(orangeShape, orange.copy(alpha = orangeIn))
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 26.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "N-ZIK",
                    color = cream.copy(alpha = segment(p, 0.02f, 0.14f)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.3.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = periodLabel,
                    color = lime.copy(alpha = segment(p, 0.08f, 0.20f)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.1.sp
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(286.dp)
            ) {
                val wordsOut = 1f - segment(p, 0.50f, 0.62f)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = wordsOut },
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    LoadingWord(stringResource(R.string.rw_loader_word_your), lime, ink, segment(p, 0.11f, 0.22f), fromRight = false)
                    LoadingWord(
                        stringResource(if (isMonthly) R.string.rw_loader_word_month else R.string.rw_loader_word_year),
                        pink,
                        ink,
                        segment(p, 0.19f, 0.30f),
                        fromRight = true
                    )
                    LoadingWord(stringResource(R.string.rw_loader_word_in), orange, ink, segment(p, 0.27f, 0.38f), fromRight = false)
                    LoadingWord(stringResource(R.string.rw_loader_word_music), cream, ink, segment(p, 0.35f, 0.46f), fromRight = true)
                }
                RewindLoaderLockup(
                    periodLabel = periodLabel,
                    progress = segment(p, 0.56f, 0.80f),
                    cream = cream,
                    lime = lime,
                    pink = pink,
                    ink = ink,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                )
            }
            Spacer(Modifier.weight(1f))
            // Keep the understated status language: it worked better than the old giant
            // "READY, username" message.
            val finalMessage = when {
                p < 0.68f -> stringResource(R.string.rw_loader_status_building)
                p < 0.76f -> data?.let { stringResource(R.string.rw_loader_status_plays, formatRewindNumber(it.stats.totalPlays.toLong())) } ?: stringResource(R.string.rw_loader_status_counting_plays)
                p < 0.84f -> data?.let { stringResource(R.string.rw_loader_status_songs, formatRewindNumber(it.totalUniqueSongs.toLong())) } ?: stringResource(R.string.rw_loader_status_counting_songs)
                p < 0.91f -> data?.let { stringResource(R.string.rw_loader_status_minutes, formatRewindNumber(it.stats.totalMinutes)) } ?: stringResource(R.string.rw_loader_status_counting_minutes)
                p < 0.97f -> data?.let { stringResource(R.string.rw_loader_status_days, formatRewindNumber(it.daysWithMusic.toLong())) } ?: stringResource(R.string.rw_loader_status_counting_days)
                else -> stringResource(R.string.rw_loader_status_ready)
            }
            Text(
                text = finalMessage,
                color = if (p >= 0.97f) lime else cream.copy(alpha = 0.78f),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp
            )
            Spacer(Modifier.height(13.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(cream.copy(alpha = 0.14f), RoundedCornerShape(100.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(p.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(if (p > 0.90f) lime else cream, RoundedCornerShape(100.dp))
                )
            }
        }
    }
}

@Composable
private fun LoadingWord(
    text: String,
    background: Color,
    foreground: Color,
    progress: Float,
    fromRight: Boolean
) {
    Text(
        text = text,
        color = foreground,
        fontSize = 42.sp,
        lineHeight = 42.sp,
        letterSpacing = (-2.2).sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .graphicsLayer {
                alpha = progress
                val distance = 90.dp.toPx() * (1f - progress)
                translationX = if (fromRight) distance else -distance
            }
            .background(background)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

@Composable
private fun RewindLoaderLockup(
    periodLabel: String,
    progress: Float,
    cream: Color,
    lime: Color,
    pink: Color,
    ink: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.height(190.dp), contentAlignment = Alignment.CenterStart) {
        // Colored shadow + compressed foreground makes a custom display lockup without requiring
        // a bundled font file or a project-specific font resource.
        Text(
            text = stringResource(R.string.rw_loader_lockup),
            color = pink,
            fontSize = 72.sp,
            lineHeight = 68.sp,
            letterSpacing = (-4.8).sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.graphicsLayer {
                alpha = progress * 0.95f
                translationX = (12f + (1f - progress) * 46f).dp.toPx()
                translationY = 9.dp.toPx()
                scaleX = 0.78f
                scaleY = 1.04f
                rotationZ = -2.3f
            }
        )
        Text(
            text = stringResource(R.string.rw_loader_lockup),
            color = cream,
            fontSize = 72.sp,
            lineHeight = 68.sp,
            letterSpacing = (-4.8).sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.graphicsLayer {
                alpha = progress
                translationX = ((1f - progress) * -54f).dp.toPx()
                scaleX = 0.78f
                scaleY = 1.04f
                rotationZ = -2.3f
            }
        )
        Text(
            text = periodLabel,
            color = ink,
            fontSize = 43.sp,
            lineHeight = 42.sp,
            letterSpacing = (-2.3).sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .graphicsLayer {
                    alpha = segment(progress, 0.42f, 1f)
                    translationX = ((1f - segment(progress, 0.42f, 1f)) * 46f).dp.toPx()
                    rotationZ = 2.5f
                }
                .background(lime, RoundedCornerShape(2.dp))
                .padding(horizontal = 12.dp, vertical = 2.dp)
        )
    }
}

private fun segment(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    return ((value - start) / (end - start)).coerceIn(0f, 1f)
}

/**
 * Software-replays the current deck page onto a fresh bitmap and saves it as a PNG in the
 * shared cache directory. Last-resort single-slide capture for devices below API 34, where
 * the display-framebuffer PixelCopy overloads do not exist; the caller exports the returned
 * file like any captured deck page.
 *
 * The caller has switched the deck into capture mode (solid slide backgrounds): a Bitmap
 * canvas replays the view in software mode, where the HypnoticCanvas RuntimeShaders would
 * throw (Software rendering doesn't support RuntimeShader). Coil images are safe too: the
 * shared loader decodes software bitmaps (ImageCacheFactory, allowHardware(false)).
 *
 * @return the saved PNG, or null when the replay or the compression failed
 */
private suspend fun captureRewindScreenshot(
    context: Context,
    view: View,
    year: Int,
    month: Int? = null
): File? {
    return runCatching {
        val bitmap = withContext(Dispatchers.Main.immediate) {
            val width = view.width.coerceAtLeast(1)
            val height = view.height.coerceAtLeast(1)
            Timber.tag("RewindShare").d("Capturing %dx%d from %s", width, height, view.javaClass.simpleName)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { target ->
                view.draw(AndroidCanvas(target))
            }
        }
        val imageFile = withContext(Dispatchers.IO) {
            val shareDirectory = File(context.cacheDir, "shared_rewind").apply { mkdirs() }
            val staleBefore = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
            shareDirectory.listFiles()
                ?.filter { it.lastModified() < staleBefore }
                ?.forEach(File::delete)
            File(
                shareDirectory,
                "NZik_Rewind_${year}${rewindMonthSuffix(month)}_${System.currentTimeMillis()}.png"
            ).also { outputFile ->
                outputFile.outputStream().buffered().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        "PNG compression failed"
                    }
                }
                Timber.tag("RewindShare").d("Saved %d bytes to %s", outputFile.length(), outputFile.name)
            }
        }
        bitmap.recycle()
        imageFile
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        Timber.tag("RewindShare").e(error, "Failed to capture Rewind screenshot")
        null
    }
}

private fun createEmptyRewindData(year: Int, month: Int? = null): RewindData {
    return RewindData(
        topSongs = emptyList(),
        topArtists = emptyList(),
        topAlbums = emptyList(),
        topPlaylists = emptyList(),
        stats = ListeningStats(
            totalPlays = 0,
            totalMinutes = 0,
            mostActiveDay = null,
            mostActiveHour = null,
            mostActiveMonth = null,
            averageDailyMinutes = 0.0,
            firstPlayDate = null,
            lastPlayDate = null
        ),
        monthlyStats = emptyList(),
        dailyStats = emptyList(),
        hourlyStats = emptyList(),
        totalUniqueSongs = 0,
        totalUniqueArtists = 0,
        totalUniqueAlbums = 0,
        totalUniquePlaylists = 0,
        year = year,
        daysWithMusic = 0,
        periodLabel = rewindPeriodLabel(year, month),
        daysInPeriod = rewindDaysInPeriod(year, month)
    )
}

/** "_03" style suffix for a monthly deck file name, empty for the annual deck. */
private fun rewindMonthSuffix(month: Int?): String =
    month?.let { "_${it.toString().padStart(2, '0')}" }.orEmpty()
