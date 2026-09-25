package app.n_zik.android.components.ui.screens.rewind.slides

import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.hypnoticcanvas.shaderBackground
import app.n_zik.android.components.ui.screens.rewind.rewindCaptureMode
import app.n_zik.android.components.ui.screens.rewind.rewindShareCaptureActive
import app.n_zik.android.components.ui.screens.rewind.rewindSlideShaders
import app.n_zik.android.components.ui.screens.rewind.RewindLevel
import app.it.fast4x.rimusic.MONTHLY_PREFIX
import app.it.fast4x.rimusic.PINNED_PREFIX
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.core.rewind.RewindPlaylists
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.checkFileExists
import app.n_zik.android.components.ui.screens.rewind.RewindData
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.thumbnail
import app.n_zik.android.thumbnailShape
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.Hsl
import app.it.fast4x.rimusic.ui.styling.hsl
import app.it.fast4x.rimusic.ui.styling.overlay
import app.it.fast4x.rimusic.ui.styling.onOverlay
import app.it.fast4x.rimusic.ui.styling.px
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.requests.ArtistPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Slide palette derived from the app's current [ColorPalette], so the deck recolors with the
 * NZik theme and follows the dynamic accent extracted from the now-playing cover.
 *
 * [lime] is the accent itself and every other vivid color stays on the same hue — the slides
 * are told apart by tone alone, so the whole deck reads as one violet (NZik) family.
 * [ink] and [cream] follow the app background/text.
 */
internal data class RewindColors(
    val ink: Color,
    val cream: Color,
    val purple: Color,
    val pink: Color,
    val red: Color,
    val orange: Color,
    val lime: Color,
    val blue: Color,
    val yellow: Color
) {
    companion object {
        /** Original deck defaults, used until RewindScreen publishes the themed palette. */
        val fallback = RewindColors(
            ink = Color(0xFF08070C),
            cream = Color(0xFFFFF6E6),
            purple = Color(0xFF461CF4),
            pink = Color(0xFFFF4B98),
            red = Color(0xFFE83B2F),
            orange = Color(0xFFFF5A2E),
            lime = Color(0xFFD9FF31),
            blue = Color(0xFF2369EB),
            yellow = Color(0xFFFFD72E)
        )

        fun fromPalette(palette: ColorPalette): RewindColors {
            val (hue, sat, _) = palette.accent.hsl
            // Every vivid color keeps the app's accent hue; the slides are told apart by tone
            // alone so the deck recolors as a coherent monochrome family with the theme.
            fun tonal(saturation: Float, lightness: Float) =
                Hsl(floatArrayOf(hue.mod(360f), saturation, lightness)).color
            return RewindColors(
                ink = palette.background0,
                cream = palette.text,
                lime = palette.accent,
                yellow = tonal(sat * 0.45f, 0.80f),
                pink = tonal(sat * 0.70f, 0.62f),
                blue = tonal(sat * 0.75f, 0.50f),
                orange = tonal(sat * 0.85f, 0.45f),
                purple = tonal(sat.coerceAtMost(0.9f), 0.35f),
                red = tonal(sat.coerceAtMost(0.9f), 0.26f)
            )
        }
    }

    /** ITU-R BT.709 relative luminance, 0 (black) to 1 (white). */
    private fun Color.luminance(): Float =
        0.2126f * red + 0.7152f * green + 0.0722f * blue

    /**
     * Readable side of the ink/cream pair to lay directly on [background]: the lighter side on
     * dark backgrounds, the darker side on light ones.
     *
     * Judged on the scrim-darkened background (slides always draw [REWIND_SCRIM_ALPHA] of ink
     * over it), with the sides resolved by their own luminance rather than by name, because
     * ink and cream swap dark/light roles with the app theme (light mode).
     */
    fun textOn(background: Color): Color {
        val effective = lerp(ink, background, 1f - REWIND_SCRIM_ALPHA)
        val darkSide = if (ink.luminance() <= cream.luminance()) ink else cream
        val lightSide = if (darkSide == ink) cream else ink
        return if (effective.luminance() < 0.45f) lightSide else darkSide
    }

    /**
     * Readable side of the ink/cream pair for a solid surface the slide scrim does NOT cover
     * (stat tiles, feature cards, pills): judged on the surface's own luminance, because the
     * scrim-darkened assumption of [textOn] makes light text land on bright surfaces.
     */
    fun flatTextOn(surface: Color): Color {
        val darkSide = if (ink.luminance() <= cream.luminance()) ink else cream
        val lightSide = if (darkSide == ink) cream else ink
        return if (surface.luminance() < 0.45f) lightSide else darkSide
    }
}

/** Scrim laid over every slide background so the shader never washes out the typography. */
internal const val REWIND_SCRIM_ALPHA = 0.55f

/**
 * Live slide palette, written by RewindScreen from the app's current [ColorPalette] while the
 * deck is on screen.
 *
 * Backed by snapshot state rather than a CompositionLocal so every slide can read it from any
 * context — composable calls, Canvas draw lambdas, remember blocks, default argument values —
 * while composable reads stay reactive to app palette (accent) changes.
 */
internal val rewindColors = mutableStateOf(RewindColors.fallback)

/**
 * Whether the pager page currently being composed is the one on screen. Set per page by
 * RewindScreen so slide backgrounds only run their animated shader while actually visible
 * (off-screen pages fall back to a static solid color, saving GPU work).
 */
internal val LocalRewindActive = staticCompositionLocalOf { true }

/**
 * Early activation window for slide shaders (current page and one page on each side), so the
 * animated background is already running before a swipe lands — no "pop" on arrival.
 */
internal val LocalRewindShaderWarm = staticCompositionLocalOf { false }

internal enum class RewindRevealDirection {
    Up,
    Down,
    Left,
    Right
}

/**
 * Full-screen Rewind story shell.
 *
 * Important layout rule: slide content never owns the very bottom edge. That space is
 * reserved for the NZik signature so the brand cannot land on top of slide copy.
 */
@Composable
internal fun RewindStoryShell(
    page: Int,
    pageCount: Int,
    background: Color,
    progressColor: Color,
    modifier: Modifier = Modifier,
    onNext: (() -> Unit)? = null,
    onShareSlide: (() -> Unit)? = null,
    showProgress: Boolean = true,
    showBrand: Boolean = true,
    backgroundArt: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    // During screenshot capture the deck renders its solid fallback: the capture canvas is
    // software-only, where the RuntimeShader cannot be replayed at all.
    val shader =
        if (LocalRewindShaderWarm.current && !rewindCaptureMode.value) {
            rewindSlideShaders.getOrNull(page)
        } else {
            null
        }
    // During the 16-image share capture the system bars are hidden for clean frames; the
    // ignoring-visibility insets report the bar sizes even while hidden, so the deck keeps
    // exactly the same safe-area padding as on screen (the regular insets would collapse to 0).
    val holdBarPadding = rewindShareCaptureActive.value
    // Per-slide share button (top-right, below the progress row). It is an overlay only —
    // its visibility never changes the content layout, so pages never reflow when it
    // appears or disappears.
    val showShareButton = onShareSlide != null && LocalRewindActive.current && !rewindShareCaptureActive.value
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(enabled = onNext != null) { onNext?.invoke() }
    ) {
        // Dark scrim over every slide background so the bright shader colors (and the
        // solid fallback) never wash out the typography
        val scrim = rewindColors.value.ink.copy(alpha = REWIND_SCRIM_ALPHA)
        if (shader != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shaderBackground(shader)
                    .background(scrim)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background)
                    .background(scrim)
            )
        }
        backgroundArt()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (holdBarPadding) {
                        Modifier.windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                    } else {
                        Modifier.statusBarsPadding()
                    }
                )
                .then(
                    if (holdBarPadding) {
                        Modifier.windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    } else {
                        Modifier.navigationBarsPadding()
                    }
                )
                .padding(horizontal = 20.dp)
                .padding(top = 28.dp, bottom = if (showBrand) 58.dp else 36.dp)
        ) {
            RewindStoryProgress(
                page = page,
                pageCount = pageCount,
                color = progressColor,
                visible = showProgress
            )
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
        if (showBrand) {
            RewindBrandBug(
                foreground = progressColor,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .then(
                        if (holdBarPadding) {
                            Modifier.windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                        } else {
                            Modifier.navigationBarsPadding()
                        }
                    )
                    .padding(end = 16.dp, bottom = 8.dp)
            )
        }
        // Per-slide system share (single PNG). Only the active page offers it, and it is
        // hidden during the export capture so it never lands inside the exported frames.
        // Overlay below the progress row (see RewindShareSlideButton) — never in the layout
        // flow, so toggling it can't reflow the slide content. It pops in with a small
        // scale/fade when the page activates and pops back out when it deactivates; while
        // an export capture is in flight the exit is instant, so a copied frame never shows
        // the icon mid-animation (same rule as the deck's reveal snaps).
        AnimatedVisibility(
            visible = showShareButton,
            enter = scaleIn(
                initialScale = 0.5f,
                // Spring with a light overshoot: a smooth transition that still reads as a pop
                animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
            ) + fadeIn(tween(200, easing = LinearOutSlowInEasing)),
            exit = if (rewindShareCaptureActive.value) {
                // Export capture in flight: vanish instantly for a clean frame
                fadeOut(tween(0))
            } else {
                scaleOut(
                    targetScale = 0.8f,
                    animationSpec = tween(160, easing = FastOutLinearInEasing)
                ) + fadeOut(tween(160, easing = FastOutLinearInEasing))
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 44.dp, end = 12.dp)
        ) {
            RewindShareSlideButton(
                tint = progressColor.copy(alpha = 0.85f),
                onClick = { onShareSlide?.invoke() }
            )
        }
    }
}

/**
 * Share icon in the top-right corner of a slide, below the 3dp progress row. It is an
 * overlay (outside the content layout) so showing or hiding it never reflows the slide.
 * The 24dp glyph sits in a 40dp touch target. Capturing and sharing the displayed slide is
 * handled by the caller
 * (see [app.n_zik.android.components.ui.screens.rewind.shareRewindSlide]).
 */
@Composable
private fun RewindShareSlideButton(
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            // Same rounded chrome as the deck's volume toggle (spec 3)
            .clip(RoundedCornerShape(14.dp))
            .background(rewindColors.value.cream.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
    ) {
        Icon(
            painter = painterResource(R.drawable.share_social),
            contentDescription = stringResource(R.string.rw_share_slide),
            tint = tint,
            modifier = Modifier
                .align(Alignment.Center)
                .size(24.dp)
        )
    }
}

@Composable
private fun RewindStoryProgress(
    page: Int,
    pageCount: Int,
    color: Color,
    visible: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(pageCount) { index ->
            val alpha = if (!visible) 0f else when {
                index < page -> 0.82f
                index == page -> 1f
                else -> 0.22f
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}

@Composable
internal fun RewindBrandBug(
    foreground: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = "NZik",
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(5.dp))
        )
        Text(
            text = "N-ZIK",
            color = foreground.copy(alpha = 0.68f),
            fontSize = 8.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.9.sp
        )
    }
}

/**
 * Stagger delay used by the deck's reveal chains. Skipped entirely while the 16-image export
 * capture is running: every page settles straight to its final state, so a stagger must not
 * hold a page back before its frame is copied.
 */
internal suspend fun rewindRevealDelay(millis: Int) {
    if (!rewindShareCaptureActive.value) delay(millis.toLong())
}

/**
 * Drives a reveal [Animatable] to [target]: animated with [spec] in normal use, but snapped
 * instantly while the export capture is running, so no page is ever screenshotted mid-reveal.
 * Float-only: every reveal in the deck drives a Float progress (the star projection keeps
 * the Animatable's animation-vector parameter out of the way).
 */
internal suspend fun rewindDriveTo(
    anim: Animatable<Float, *>,
    target: Float,
    spec: AnimationSpec<Float>
) {
    if (rewindShareCaptureActive.value) {
        anim.snapTo(target)
    } else {
        anim.animateTo(target, spec)
    }
}

/**
 * One-shot reveal. Nothing loops: when a page becomes active the element enters once,
 * settles, and stays still until the user leaves the page.
 */
@Composable
internal fun RewindReveal(
    active: Boolean,
    delayMillis: Int = 0,
    modifier: Modifier = Modifier,
    distance: Dp = 24.dp,
    direction: RewindRevealDirection = RewindRevealDirection.Up,
    scaleFrom: Float = 0.98f,
    durationMillis: Int = 520,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active) {
            progress.snapTo(0f)
        } else {
            progress.snapTo(0f)
            if (rewindShareCaptureActive.value) {
                // Export capture: settle straight to the revealed state
                progress.snapTo(1f)
            } else {
                if (delayMillis > 0) delay(delayMillis.toLong())
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis, easing = LinearOutSlowInEasing)
                )
            }
        }
    }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            val travel = distance.toPx() * (1f - progress.value)
            when (direction) {
                RewindRevealDirection.Up -> translationY = travel
                RewindRevealDirection.Down -> translationY = -travel
                RewindRevealDirection.Left -> translationX = travel
                RewindRevealDirection.Right -> translationX = -travel
            }
            scaleX = scaleFrom + (1f - scaleFrom) * progress.value
            scaleY = scaleFrom + (1f - scaleFrom) * progress.value
        }
    ) {
        content()
    }
}

@Composable
internal fun RewindAnimatedNumber(
    value: Long,
    active: Boolean,
    color: Color,
    fontSize: Int,
    delayMillis: Int = 220,
    modifier: Modifier = Modifier,
    durationMillis: Int = 1050
) {
    val anim = remember(value) { Animatable(0f) }
    LaunchedEffect(active, value) {
        if (!active) {
            anim.snapTo(0f)
        } else {
            anim.snapTo(0f)
            val target = value.coerceAtLeast(0).toFloat()
            if (rewindShareCaptureActive.value) {
                // Export capture: show the final number straight away
                anim.snapTo(target)
            } else {
                delay(delayMillis.toLong())
                anim.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)
                )
            }
        }
    }
    Text(
        text = formatRewindNumber(anim.value.toLong()),
        color = color,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 0.90f).sp,
        letterSpacing = (-3.2).sp,
        fontWeight = FontWeight.Black,
        modifier = modifier
    )
}

@Composable
internal fun RewindArtwork(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onError: (() -> Unit)? = null
) {
    ImageCacheFactory.AsyncImage(
        thumbnailUrl = imageUrl,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onError = {
            if (!imageUrl.isNullOrBlank()) onError?.invoke()
        }
    )
}

@Composable
internal fun RewindArtworkWithFallback(
    imageUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
    background: Color = rewindColors.value.purple,
    foreground: Color = rewindColors.value.cream,
    onError: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(if (circular) CircleShape else RoundedCornerShape(8.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.trim().take(2).uppercase().ifBlank { "♪" },
            color = foreground.copy(alpha = 0.70f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        RewindArtwork(
            imageUrl = imageUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            onError = onError
        )
    }
}

/**
 * Origin indicator overlay for a playlist artwork, mirroring the statistics screen playlist
 * rows: the N-Zik launcher icon on local playlists, the pin on pinned playlists, the month
 * icon on monthly playlists, and the source icon on imported or YouTube playlists.
 *
 * Spec 2 adds the dedicated origin icons of the generated rewind-* playlists
 * (monthly -> month, yearly -> year, all-time -> notes, accent tint).
 */
@Composable
internal fun RewindPlaylistOriginIcon(
    name: String,
    browseId: String?,
    isYoutubePlaylist: Boolean,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    val (painter, tint) = when {
        name.startsWith(PINNED_PREFIX, true) ->
            painterResource(R.drawable.pin_filled) to colorPalette().accent

        name.startsWith(MONTHLY_PREFIX, true) ->
            painterResource(R.drawable.stat_month) to colorPalette().accent

        RewindPlaylists.isMonthly(name) ->
            painterResource(R.drawable.stat_month) to colorPalette().accent

        RewindPlaylists.isYearly(name) ->
            painterResource(R.drawable.stat_year) to colorPalette().accent

        RewindPlaylists.isAlltime(name) ->
            painterResource(R.drawable.musical_notes) to colorPalette().accent

        browseId == "SPOTIFY_IMPORT" || browseId?.startsWith("SPOTIFY_IMPORT") == true ->
            painterResource(R.drawable.spotify) to Color.Unspecified

        browseId == "RIPLAY_IMPORT" || browseId?.startsWith("RIPLAY_IMPORT") == true ->
            painterResource(R.drawable.riplay) to Color.Unspecified

        isYoutubePlaylist || browseId?.startsWith("VL") == true ->
            painterResource(R.drawable.ytmusic) to Color.Red

        else ->
            painterResource(R.drawable.ic_launcher) to Color.Unspecified
    }
    Icon(
        painter = painter,
        contentDescription = stringResource(R.string.cd_origin_indicator),
        tint = tint,
        modifier = modifier.size(iconSize)
    )
}

/**
 * Playlist "logo" used on the top playlists slide.
 *
 * Copied from the statistics screen pipeline: the four most-played track covers form a 2x2
 * mosaic (a single cover is shown when they all match), dimmed by the app overlay (scrim)
 * with the origin indicator (N-Zik logo / pin / source icon) placed top-right. The rank
 * number is not drawn here: it lives in the row, like the Deep Cuts slide.
 *
 * Uses the exact same pipeline as the statistics screen and Home Library:
 * 1. Check for custom playlist thumbnail file
 * 2. Fall back to sortSongsByPlayTime → mapNotNull(Song::thumbnailUrl).takeLast(4)
 * 3. Render via ImageCacheFactory.Thumbnail (handles HTTP URLs, scaling, etc.)
 */
@Composable
internal fun RewindPlaylistArtwork(
    playlistId: Long,
    title: String,
    name: String,
    browseId: String?,
    isYoutubePlaylist: Boolean,
    // Named sizeDp (not "size") so the parameter does not shadow Modifier.size().
    sizeDp: Dp = 46.dp,
    shape: Shape = thumbnailShape(),
    // When true, a playlist without artwork shows the app launcher box (ImageCacheFactory's
    // fallback) instead of the initials placeholder.
    emptyFallback: Boolean = false,
    modifier: Modifier = Modifier
) {
    val quadrant = sizeDp / 2
    val context = LocalContext.current
    val thumbnails by remember(playlistId) {
        val customThumbnail = checkFileExists(
            context,
            "thumbnail/playlist_$playlistId"
        )
        if (customThumbnail != null) {
            flowOf(listOf(customThumbnail))
        } else {
            Database.songPlaylistMapTable
                .sortSongsByPlayTime(playlistId)
                .distinctUntilChanged()
                .map { list ->
                    list.mapNotNull(Song::thumbnailUrl).takeLast(4)
                }
        }
    }.collectAsStateWithLifecycle(emptyList(), context = NzikDispatchers.DATA)

    val showFallback = emptyFallback && thumbnails.isEmpty()
    Box(
        modifier = modifier
            .clip(shape)
            .background(rewindColors.value.ink),
        contentAlignment = Alignment.Center
    ) {
        when {
            showFallback ->
                Image(
                    painter = painterResource(R.drawable.ic_launcher_box),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            thumbnails.isEmpty() ->
                Text(
                    text = title.trim().take(2).uppercase().ifBlank { "♪" },
                    color = rewindColors.value.cream.copy(alpha = 0.70f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            thumbnails.toSet().size == 1 ->
                ImageCacheFactory.Thumbnail(
                    thumbnailUrl = thumbnails.first(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            else ->
                listOf(
                    Alignment.TopStart,
                    Alignment.TopEnd,
                    Alignment.BottomStart,
                    Alignment.BottomEnd
                ).forEachIndexed { index, alignment ->
                    val thumbnail = thumbnails.getOrNull(index)
                    if (thumbnail != null) {
                        ImageCacheFactory.Thumbnail(
                            thumbnailUrl = thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(alignment)
                                .size(quadrant)
                        )
                    }
                }
        }
        if (!showFallback) {
            // Same dimming overlay as the statistics screen, without the rank: the rank
            // number is rendered by the row itself (Deep Cuts style).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(colorPalette().overlay)
            ) {
                // No rank — it's on the row (Deep Cuts style).
            }
            // The statistics screen also paints an origin indicator (N-Zik logo, pin,
            // source icon) over the artwork; it is kept top-right on the deck artworks.
            RewindPlaylistOriginIcon(
                name = name,
                browseId = browseId,
                isYoutubePlaylist = isYoutubePlaylist,
                iconSize = sizeDp * 0.30f,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(sizeDp * 0.05f)
            )
        }
    }
}

@Composable
internal fun RewindArtistArtwork(
    artistName: String,
    primaryUrl: String?,
    modifier: Modifier = Modifier,
    preferWikipedia: Boolean = false,
    circular: Boolean = true,
    enableSlideshow: Boolean = false
) {
    var wikipediaUrl by remember(artistName) { mutableStateOf<String?>(null) }
    var failedUrls by remember(artistName, primaryUrl) { mutableStateOf(emptySet<String>()) }
    var activeIndex by remember(artistName, primaryUrl) { mutableStateOf(0) }
    LaunchedEffect(artistName, primaryUrl, preferWikipedia, enableSlideshow) {
        wikipediaUrl = null
        failedUrls = emptySet()
        activeIndex = 0
        if (artistName.isBlank()) return@LaunchedEffect
        if (rewindShareCaptureActive.value) {
            // Export capture: skip the wiki fetch entirely — the frame settles on the
            // primary artwork only (no network wait, no late portrait).
            return@LaunchedEffect
        }
        if (enableSlideshow || preferWikipedia || primaryUrl.isNullOrBlank()) {
            wikipediaUrl = withContext(Dispatchers.IO) {
                WikipediaArtistImageResolver.findImage(artistName)
            }
        }
    }
    val orderedUrls = if (preferWikipedia) {
        listOf(wikipediaUrl, primaryUrl)
    } else {
        listOf(primaryUrl, wikipediaUrl)
    }
    val candidates = orderedUrls
        .filterNotNull()
        .filter { it.isNotBlank() && it !in failedUrls }
        .distinct()
    LaunchedEffect(candidates, enableSlideshow) {
        activeIndex = activeIndex.coerceIn(0, (candidates.size - 1).coerceAtLeast(0))
        if (enableSlideshow && candidates.size > 1) {
            while (true) {
                delay(7_000)
                // Export capture: stop cycling so a frame cannot land mid-crossfade
                if (rewindShareCaptureActive.value) break
                activeIndex = (activeIndex + 1) % candidates.size
            }
        }
    }
    val selectedUrl = candidates.getOrNull(activeIndex) ?: candidates.firstOrNull()
    Crossfade(
        targetState = selectedUrl,
        animationSpec = if (rewindShareCaptureActive.value) tween(0) else tween(900),
        label = "rewindArtistArtwork"
    ) { imageUrl ->
        RewindArtworkWithFallback(
            imageUrl = imageUrl,
            title = artistName,
            modifier = modifier,
            circular = circular,
            background = rewindColors.value.purple,
            foreground = rewindColors.value.cream,
            onError = {
                if (!imageUrl.isNullOrBlank()) failedUrls = failedUrls + imageUrl
            }
        )
    }
}

internal data class ArtistWikiMetadata(
    val imageUrl: String?,
    val description: String?,
    val bio: String? = null,
    val wikipediaUrl: String? = null
)

/**
 * In-app artist bio for the Top Artist spotlight page. Uses the same source the artist
 * screen uses: the description already stored on the artist row (written by the artist
 * screen from the Innertube artist page), then the Innertube artist page fetched by
 * browseId.
 *
 * A by-name Wikipedia lookup is deliberately NOT used for the bio: for ambiguous or
 * channel-derived artist names it resolves to unrelated pages (e.g. the Wikipedia article
 * of a same-named song), which is what the spotlight used to show.
 *
 * Returns null while loading or when no description is available — the card renders
 * without the "about" section in that case.
 */
@Composable
internal fun rememberArtistBio(
    browseId: String?,
    storedDescription: String? = null
): ArtistWikiMetadata? {
    var metadata by remember(browseId, storedDescription) { mutableStateOf<ArtistWikiMetadata?>(null) }
    LaunchedEffect(browseId, storedDescription) {
        metadata = null
        metadata = withContext(Dispatchers.IO) {
            RewindArtistBioResolver.resolve(browseId, storedDescription)
        }
    }
    return metadata
}

/**
 * Compose-free bio resolution so the in-app-first ordering is unit-testable: a non-blank
 * stored description short-circuits any network fetch, and otherwise the Innertube artist
 * page (fetched by [browseId]) is the sole source. [fetchPage] is injectable for tests.
 */
internal object RewindArtistBioResolver {
    suspend fun resolve(
        browseId: String?,
        storedDescription: String?,
        fetchPage: suspend (String) -> Result<ArtistPage> = { YtMusic.getArtistPage(it) }
    ): ArtistWikiMetadata? {
        val stored = storedDescription?.trim()?.takeIf(String::isNotBlank)
        if (stored != null) {
            return ArtistWikiMetadata(imageUrl = null, description = stored, bio = stored)
        }
        val id = browseId?.trim()?.takeIf(String::isNotBlank) ?: return null
        return fetchPage(id).getOrNull()?.let { page ->
            val description = page.description?.trim()?.takeIf(String::isNotBlank) ?: return@let null
            ArtistWikiMetadata(
                imageUrl = page.artist.thumbnail?.url,
                description = description,
                bio = description
            )
        }
    }
}

private object WikipediaArtistImageResolver {
    suspend fun findImage(artistName: String): String? =
        WikipediaArtistMetadataResolver.find(artistName)?.imageUrl
}

private object WikipediaArtistMetadataResolver {
    private const val MissCacheTtlMs = 5L * 60L * 1000L
    private const val CacheCapacity = 64
    // Bounded cache: process-lifetime retention of every resolved artist would grow without
    // bound (spec GH-275, patch "Wikipedia cache unbounded").
    private val cache = LruCache<String, ArtistWikiMetadata>(CacheCapacity)
    private val misses = ConcurrentHashMap<String, Long>()
    // In-flight coalescing: concurrent searches for the same artist share one network request.
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<ArtistWikiMetadata?>>()

    suspend fun find(artistName: String): ArtistWikiMetadata? {
        val key = artistName.trim().lowercase()
        if (key.isBlank()) return null
        cache.get(key)?.let { return it }
        val now = System.currentTimeMillis()
        misses[key]?.let { missedAt ->
            if (now - missedAt < MissCacheTtlMs) return null
            misses.remove(key, missedAt)
        }
        val slot = CompletableDeferred<ArtistWikiMetadata?>()
        val existing = inFlight.putIfAbsent(key, slot)
        val resolved: ArtistWikiMetadata? = if (existing == null) {
            // We own the in-flight slot [slot]: resolve, publish to the waiters, release the
            // slot
            val result = runCatching {
                listOf(
                    "$artistName musician",
                    "$artistName singer",
                    "$artistName rapper",
                    artistName
                ).firstNotNullOfOrNull { query ->
                    runCatching { requestMetadata(artistName, query) }.getOrNull()
                }
            }.getOrNull()
            try {
                slot.complete(result)
                result
            } finally {
                // Remove only when still the slot owner (a newer in-flight search replaced it)
                inFlight.remove(key, slot)
            }
        } else {
            // A concurrent search already owns the slot: await its result instead of firing
            // a duplicate network request
            existing.await()
        }
        if (resolved != null) {
            cache.put(key, resolved)
            misses.remove(key)
        } else {
            misses[key] = now
        }
        return resolved
    }

    private fun requestMetadata(artistName: String, query: String): ArtistWikiMetadata? {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val endpoint =
            "https://en.wikipedia.org/w/api.php?action=query&generator=search" +
                "&gsrsearch=$encoded&gsrnamespace=0&gsrlimit=5" +
                "&prop=pageimages|description|extracts|info&pithumbsize=1000" +
                "&exintro=1&explaintext=1&exchars=480&inprop=url" +
                "&format=json&formatversion=2&redirects=1"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 7000
            connection.setRequestProperty("User-Agent", "NZik-Rewind/1.0")
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val pages = JSONObject(body)
                .optJSONObject("query")
                ?.optJSONArray("pages")
                ?: return null
            val normalized = artistName.trim().lowercase()
            val results = buildList {
                for (i in 0 until pages.length()) {
                    val page = pages.optJSONObject(i) ?: continue
                    val title = page.optString("title").trim().lowercase()
                    val source = page.optJSONObject("thumbnail")?.optString("source").orEmpty()
                    val description = page.optString("description")
                        .takeIf { it.isNotBlank() && !it.contains("may refer to", ignoreCase = true) }
                    val bio = page.optString("extract")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .takeIf {
                            it.isNotBlank() &&
                                !it.contains("may refer to", ignoreCase = true) &&
                                !it.contains("disambiguation", ignoreCase = true)
                        }
                    val pageUrl = page.optString("fullurl").takeIf { it.isNotBlank() }
                    if (source.isBlank() && description.isNullOrBlank() && bio.isNullOrBlank()) continue
                    val score = when {
                        title == normalized -> 0
                        title.startsWith("$normalized (") -> 1
                        title.contains(normalized) -> 2
                        else -> 3
                    }
                    add(score to ArtistWikiMetadata(source.ifBlank { null }, description, bio, pageUrl))
                }
            }
            results.minByOrNull { it.first }?.second
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
internal fun RewindKicker(
    text: String,
    background: Color,
    // The pill is a small solid surface the slide scrim does not cover, so contrast is
    // judged on the pill itself.
    foreground: Color = rewindColors.value.flatTextOn(background),
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        color = foreground,
        fontSize = 10.sp,
        lineHeight = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.0.sp,
        modifier = modifier
            .background(background, RoundedCornerShape(2.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp)
    )
}

@Composable
internal fun RewindRankRow(
    rank: Int,
    title: String,
    subtitle: String,
    meta: String,
    imageUrl: String?,
    foreground: Color,
    accent: Color,
    active: Boolean,
    delayMillis: Int,
    modifier: Modifier = Modifier,
    circularArt: Boolean = false,
    rowBackground: Color? = null
) {
    RewindReveal(
        active = active,
        delayMillis = delayMillis,
        direction = RewindRevealDirection.Left,
        distance = 18.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (rowBackground != null) {
                        Modifier
                            .background(rowBackground, RoundedCornerShape(11.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    } else {
                        Modifier
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = rank.toString(),
                color = accent,
                fontSize = 25.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.width(27.dp),
                textAlign = TextAlign.Center
            )
            RewindArtworkWithFallback(
                imageUrl = imageUrl,
                title = title,
                modifier = Modifier.size(44.dp),
                circular = circularArt,
                background = accent.copy(alpha = 0.28f),
                foreground = foreground
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleanPrefix(title).ifBlank { "—" },
                    color = foreground,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = cleanPrefix(subtitle).ifBlank { "—" },
                    color = foreground.copy(alpha = 0.62f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = meta,
                color = foreground.copy(alpha = 0.72f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.width(60.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Compact listening-level badge used on the show slides (top song, album, artist, playlist):
 * a small "YOUR LEVEL" label above the tier pill, with the tier tagline below it.
 */
@Composable
internal fun RewindLevelBadge(
    level: RewindLevel,
    foreground: Color,
    pillBackground: Color,
    pillForeground: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.rw_label_your_level),
            color = foreground.copy(alpha = 0.62f),
            fontSize = 8.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.0.sp
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(level.nameId),
            color = pillForeground,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.7.sp,
            modifier = Modifier
                .background(pillBackground, RoundedCornerShape(100.dp))
                .padding(horizontal = 11.dp, vertical = 6.dp)
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(level.taglineId),
            color = foreground.copy(alpha = 0.72f),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun RewindEmptyState(
    title: String,
    body: String,
    foreground: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RewindKicker(stringResource(R.string.rw_empty_state_kicker), accent)
        Text(
            text = title,
            color = foreground,
            fontSize = 34.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1.7).sp
        )
        Text(
            text = body,
            color = foreground.copy(alpha = 0.66f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

internal data class ListenerBadge(
    val titleId: Int,
    val subtitleId: Int,
    val index: Int,
    val tier: Int
)

/**
 * NZik listening index. This is deliberately NOT called a percentile because the app only
 * has the listener's local history, not a global population. The reference point is calibrated
 * so ~45k minutes / 14k plays / 201 days / 5.6k unique songs lands around index 70 on a FULL
 * YEAR, leaving several genuinely harder tiers above it.
 *
 * The four annual thresholds are scaled by the period's length
 * ([RewindData.daysInPeriod] / 365.25), so a month is judged against a month's worth of the
 * reference instead of a whole year's (spec GH-275, patch "Listener badge not scaled").
 * A period with zero days (empty all-time data) scores every ratio 0 — no 0/0 NaN.
 */
internal fun calculateListenerBadge(data: RewindData): ListenerBadge {
    val scale = data.daysInPeriod.toDouble() / 365.25
    val minuteRatio = if (scale > 0.0) (data.stats.totalMinutes.toDouble() / (45_000.0 * scale)).coerceIn(0.0, 2.4) else 0.0
    val playRatio = if (scale > 0.0) (data.stats.totalPlays.toDouble() / (14_000.0 * scale)).coerceIn(0.0, 2.4) else 0.0
    val dayRatio = if (scale > 0.0) (data.daysWithMusic.toDouble() / (201.0 * scale)).coerceIn(0.0, 1.82) else 0.0
    val uniqueRatio = if (scale > 0.0) (data.totalUniqueSongs.toDouble() / (5_612.0 * scale)).coerceIn(0.0, 2.4) else 0.0
    val intensity = (
        minuteRatio * 0.38 +
            playRatio * 0.25 +
            dayRatio * 0.22 +
            uniqueRatio * 0.15
        )
    val index = (intensity * 70.0).roundToInt().coerceIn(0, 170)
    return when {
        index < 15 -> ListenerBadge(R.string.rw_badge_tier_0_name, R.string.rw_badge_tier_0_sub, index, 0)
        index < 30 -> ListenerBadge(R.string.rw_badge_tier_1_name, R.string.rw_badge_tier_1_sub, index, 1)
        index < 45 -> ListenerBadge(R.string.rw_badge_tier_2_name, R.string.rw_badge_tier_2_sub, index, 2)
        index < 60 -> ListenerBadge(R.string.rw_badge_tier_3_name, R.string.rw_badge_tier_3_sub, index, 3)
        index < 75 -> ListenerBadge(R.string.rw_badge_tier_4_name, R.string.rw_badge_tier_4_sub, index, 4)
        index < 90 -> ListenerBadge(R.string.rw_badge_tier_5_name, R.string.rw_badge_tier_5_sub, index, 5)
        index < 110 -> ListenerBadge(R.string.rw_badge_tier_6_name, R.string.rw_badge_tier_6_sub, index, 6)
        index < 130 -> ListenerBadge(R.string.rw_badge_tier_7_name, R.string.rw_badge_tier_7_sub, index, 7)
        else -> ListenerBadge(R.string.rw_badge_tier_8_name, R.string.rw_badge_tier_8_sub, index, 8)
    }
}

internal fun formatRewindNumber(value: Long): String =
    NumberFormat.getIntegerInstance().format(value.coerceAtLeast(0))

internal fun formatRewindMinutes(minutes: Long): String =
    formatRewindNumber(minutes.coerceAtLeast(0))

internal fun firstNonBlank(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() && it != "null" }.orEmpty()

@Composable
internal fun formatHourLabel(hour: String?): String {
    val raw = hour?.substringBefore(':')?.toIntOrNull() ?: return "—"
    val normalized = when {
        raw == 0 -> 12
        raw > 12 -> raw - 12
        else -> raw
    }
    val meridiem = if (raw < 12) stringResource(R.string.rw_hour_am) else stringResource(R.string.rw_hour_pm)
    return "$normalized $meridiem"
}
