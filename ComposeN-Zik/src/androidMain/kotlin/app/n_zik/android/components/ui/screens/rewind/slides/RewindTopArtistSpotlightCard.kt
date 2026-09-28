package app.n_zik.android.components.ui.screens.rewind.slides

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.TopArtist
import app.n_zik.android.components.ui.screens.rewind.rewindArtistLevel
import app.n_zik.android.components.ui.screens.rewind.rewindShareCaptureActive
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.rememberPreference
import kotlinx.coroutines.delay

@Composable
fun RewindTopArtistSpotlightCard(
    artist: TopArtist?,
    periodLabel: String,
    page: Int,
    pageCount: Int,
    active: Boolean,
    onNext: () -> Unit,
    onShareSlide: (() -> Unit)? = null
) {
    val artistName = artist?.artist?.cleanName().orEmpty()
    val bioMeta = rememberArtistBio(artist?.artist?.id, artist?.artist?.description)
    RewindStoryShell(
        page = page,
        pageCount = pageCount,
        background = rewindColors.value.ink,
        progressColor = rewindColors.value.cream,
        onNext = onNext,
        onShareSlide = onShareSlide,
        backgroundArt = {
            Canvas(Modifier.fillMaxSize()) {
                val pinkSlash = Path().apply {
                    moveTo(size.width * 0.77f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height * 0.20f)
                    lineTo(size.width * 0.93f, size.height * 0.16f)
                    close()
                }
                drawPath(pinkSlash, rewindColors.value.pink)
                drawRect(
                    color = rewindColors.value.lime,
                    topLeft = Offset(0f, size.height * 0.79f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.05f, size.height * 0.13f)
                )
            }
        }
    ) {
        if (artist == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                RewindEmptyState(
                    title = stringResource(R.string.rw_artist_spotlight_empty_title),
                    body = stringResource(R.string.rw_artist_spotlight_empty_body),
                    foreground = rewindColors.value.cream,
                    accent = rewindColors.value.lime
                )
            }
            return@RewindStoryShell
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 700.dp
            val textScale = LocalRewindTextScale.current
            // The photo scales with the deck's text squared (layoutFactor² = 1.0 at the
            // reference): it yields vertical room quickly on a narrow screen / enlarged
            // font, so the bio below gets space even before its internal scroll kicks in.
            val heroHeight = (if (compact) 238.dp else 292.dp) * (textScale.layoutFactor * textScale.layoutFactor)
            // Length ladder of the design, then the deck sizing policy on top — the ladder
            // picks the tier for the name length, the scale adapts it to the screen
            val nameSize = textScale.size(
                when {
                    artistName.length > 20 -> if (compact) 31.sp else 36.sp
                    artistName.length > 13 -> if (compact) 37.sp else 43.sp
                    else -> if (compact) 43.sp else 50.sp
                }
            )
            Column(modifier = Modifier.fillMaxSize()) {
                RewindReveal(active, 40, direction = RewindRevealDirection.Left) {
                    RewindKicker(stringResource(R.string.rw_artist_spotlight_kicker, periodLabel), rewindColors.value.lime)
                }
                Spacer(Modifier.height(12.dp))
                RewindReveal(active, 180, scaleFrom = 0.90f, durationMillis = 760) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroHeight)
                            .background(rewindColors.value.purple)
                    ) {
                        RewindArtistArtwork(
                            artistName = artistName,
                            primaryUrl = artist.artist.thumbnailUrl,
                            preferWikipedia = false,
                            circular = false,
                            enableSlideshow = true,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Fade the portrait naturally into the black page instead of cropping it
                        // into a circle. This also makes portrait/landscape source images behave.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        0.00f to rewindColors.value.ink,
                                        0.12f to rewindColors.value.ink.copy(alpha = 0.70f),
                                        0.28f to Color.Transparent,
                                        0.74f to Color.Transparent,
                                        0.91f to rewindColors.value.ink.copy(alpha = 0.72f),
                                        1.00f to rewindColors.value.ink
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.00f to rewindColors.value.ink.copy(alpha = 0.15f),
                                        0.60f to Color.Transparent,
                                        1.00f to rewindColors.value.ink
                                    )
                                )
                        )
                        RewindReveal(
                            active = active,
                            delayMillis = 620,
                            scaleFrom = 0.55f,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 13.dp, end = 12.dp)
                        ) {
                            Text(
                                text = "#1",
                                color = rewindColors.value.textOn(rewindColors.value.lime),
                                fontSize = textScale.size(30.sp),
                                lineHeight = textScale.size(30.sp),
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .background(rewindColors.value.lime, RoundedCornerShape(2.dp))
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                RewindReveal(active, 520, direction = RewindRevealDirection.Left, distance = 30.dp) {
                    RewindAdaptiveTitle(
                        text = artistName.uppercase(),
                        color = rewindColors.value.cream,
                        fontSize = nameSize,
                        lineHeight = nameSize * 0.90f,
                        letterSpacing = textScale.letterSpacing((-2.3).sp),
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(10.dp))
                RewindReveal(active, 650) {
                    RewindLevelBadge(
                        level = rewindArtistLevel(artist.minutes),
                        foreground = rewindColors.value.cream,
                        pillBackground = rewindColors.value.cream,
                        pillForeground = rewindColors.value.ink
                    )
                }
                Spacer(Modifier.height(10.dp))
                RewindReveal(active, 760) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpotlightMetric(
                            label = stringResource(R.string.rw_label_minutes),
                            value = formatRewindNumber(artist.minutes),
                            modifier = Modifier.weight(1f)
                        )
                        SpotlightMetric(
                            label = stringResource(R.string.rw_label_songs),
                            value = formatRewindNumber(artist.songCount.toLong()),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                val bio = bioMeta?.bio ?: bioMeta?.description
                if (!bio.isNullOrBlank()) {
                    // The bio block takes whatever vertical space is left after the
                    // photo/name/badge/metrics. A long bio auto-drifts slowly on its own —
                    // down, hold, back up, hold, in an endless palindrome — so the whole
                    // text is readable on a small screen with no input. There is deliberately
                    // NO manual scroll zone: a swipeable area leaks into the deck's swipe
                    // and reloads the card, so the drift is the only way the text moves.
                    // The "disable scrolling text" setting stops the drift, and a running
                    // capture freezes the bio at its top within one frame so shared images
                    // are stable. A bio that fits renders exactly as before (nothing moves).
                    val density = LocalDensity.current
                    var contentHeightPx by remember { mutableStateOf(0) }
                    var blockHeightPx by remember { mutableStateOf(0) }
                    val overflowPx = (contentHeightPx - blockHeightPx).coerceAtLeast(0)
                    var driftOffset by remember { mutableStateOf(0f) } // px down from the top
                    val isScrollingTextDisabled by rememberPreference(disableScrollingTextKey, false)
                    LaunchedEffect(active, isScrollingTextDisabled, overflowPx) {
                        if (!active || isScrollingTextDisabled || overflowPx <= 0) {
                            driftOffset = 0f
                            return@LaunchedEffect
                        }
                        // Drift speed in px per second (20 dp/s, density-scaled), and the
                        // one-frame step (≈ 16 ms) in px.
                        val speedPxPerSecond = BIO_DRIFT_DP_PER_SECOND * density.density
                        val stepPx = speedPxPerSecond * (BIO_DRIFT_STEP_MILLIS / 1000f)
                        val totalMillis = ((overflowPx.toFloat() / speedPxPerSecond) * 1000f).toLong()
                        while (true) {
                            if (rewindShareCaptureActive.value) {
                                // Capture in progress: freeze at the top — shared frames must
                                // be static and show the start of the bio.
                                driftOffset = 0f
                                delay(250)
                                continue
                            }
                            // Drift down slowly (per-frame steps, so a capture can freeze
                            // the bio within one frame).
                            var elapsed = 0L
                            while (elapsed < totalMillis) {
                                if (rewindShareCaptureActive.value) break
                                driftOffset += stepPx
                                delay(BIO_DRIFT_STEP_MILLIS)
                                elapsed += BIO_DRIFT_STEP_MILLIS
                            }
                            driftOffset = overflowPx.toFloat()
                            delay(BIO_DRIFT_HOLD_MILLIS)
                            // Drift back up slowly, then hold at the top, then loop.
                            elapsed = 0L
                            while (elapsed < totalMillis) {
                                if (rewindShareCaptureActive.value) break
                                driftOffset -= stepPx
                                delay(BIO_DRIFT_STEP_MILLIS)
                                elapsed += BIO_DRIFT_STEP_MILLIS
                            }
                            driftOffset = 0f
                            delay(BIO_DRIFT_HOLD_MILLIS)
                        }
                    }
                    RewindReveal(
                        active = active,
                        delayMillis = 960,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { blockHeightPx = it.height }
                                .clip(RoundedCornerShape(0.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // Measured at its natural height even when taller than
                                    // the block (default maxHeight = infinity), so the
                                    // drift knows how far to travel.
                                    .wrapContentHeight()
                                    .onSizeChanged { contentHeightPx = it.height }
                                    .graphicsLayer { translationY = driftOffset }
                            ) {
                            Text(
                                text = stringResource(R.string.rw_artist_spotlight_about),
                                color = rewindColors.value.lime,
                                fontSize = textScale.size(9.sp),
                                fontWeight = FontWeight.Black,
                                letterSpacing = textScale.letterSpacing(1.0.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                // The bio wraps fully — no line cap, no marquee. The whole
                                // text is shown; when it is taller than the space left on
                                // the slide it drifts slowly on its own (see the Launched
                                // effect above) — no manual swipe, by design.
                                text = bio,
                                color = rewindColors.value.cream.copy(alpha = 0.78f),
                                fontSize = textScale.size(if (compact) 10.sp else 11.sp),
                                lineHeight = textScale.size(if (compact) 14.sp else 15.sp)
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = stringResource(R.string.rw_artist_spotlight_innertube),
                                color = rewindColors.value.cream.copy(alpha = 0.35f),
                                fontSize = textScale.size(8.sp),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = textScale.letterSpacing(0.8.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotlightMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val textScale = LocalRewindTextScale.current
    Column(
        modifier = modifier
            .background(rewindColors.value.cream, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = value,
            color = rewindColors.value.ink,
            fontSize = textScale.size(22.sp),
            lineHeight = textScale.size(22.sp),
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.rewindMarqueeOnly()
        )
        Text(
            text = label,
            color = rewindColors.value.ink.copy(alpha = 0.58f),
            fontSize = textScale.size(8.sp),
            fontWeight = FontWeight.Black,
            letterSpacing = textScale.letterSpacing(0.8.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Slow auto-drift of the artist bio, in dp per second — the owner wants the description
 * to slide by itself, slowly ("haut en bas tout seul lent"). ≈ 1.3 lines/s at the deck
 * bio line height, a readable pace.
 */
private const val BIO_DRIFT_DP_PER_SECOND = 20f

/** One drift step, in ms (≈ one frame at 20 dp/s). */
private const val BIO_DRIFT_STEP_MILLIS = 16L

/** How long the bio holds at each end before drifting back, in ms — the edges stay readable. */
private const val BIO_DRIFT_HOLD_MILLIS = 1_800L
