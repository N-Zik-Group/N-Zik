package app.n_zik.android.components.ui.screens.rewind.slides

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.it.fast4x.rimusic.models.Album
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.TopAlbum
import app.n_zik.android.components.ui.screens.rewind.rewindAlbumLevel

/**
 * Full "top album" show: sleeve + vinyl reveal, title swipe, AOTY badge and metrics, shown by
 * the Album of the Year slide.
 */
@Composable
internal fun RewindTopAlbumShow(
    topAlbum: TopAlbum?,
    periodLabel: String,
    page: Int,
    pageCount: Int,
    active: Boolean,
    onNext: () -> Unit,
    kickerId: Int,
    headingId: Int,
    emptyTitleId: Int,
    emptyBodyId: Int
) {
    val titleIn = remember { Animatable(0f) }
    LaunchedEffect(active) {
        titleIn.snapTo(0f)
        if (!active) return@LaunchedEffect
        rewindRevealDelay(430)
        rewindDriveTo(titleIn, 1f, tween(620, easing = FastOutSlowInEasing))
    }

    val onYellow = rewindColors.value.textOn(rewindColors.value.yellow)
    val onPink = rewindColors.value.textOn(rewindColors.value.pink)
    RewindStoryShell(
        page = page,
        pageCount = pageCount,
        background = rewindColors.value.yellow,
        progressColor = onYellow,
        onNext = onNext
    ) {
        if (topAlbum == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                RewindEmptyState(
                    title = stringResource(emptyTitleId),
                    body = stringResource(emptyBodyId),
                    foreground = onYellow,
                    accent = rewindColors.value.pink
                )
            }
            return@RewindStoryShell
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 700.dp
            val sleeveSize = if (compact) 198.dp else 236.dp
            val heroHeight = if (compact) 235.dp else 282.dp
            val title = topAlbum.album.cleanTitle()
            Column(modifier = Modifier.fillMaxSize()) {
                RewindReveal(active, 40, direction = RewindRevealDirection.Left) {
                    RewindKicker(stringResource(kickerId, periodLabel), rewindColors.value.lime)
                }
                Spacer(Modifier.height(11.dp))
                RewindReveal(active, 110, direction = RewindRevealDirection.Left) {
                    Text(
                        text = stringResource(headingId),
                        color = onYellow,
                        fontSize = if (compact) 34.sp else 40.sp,
                        lineHeight = if (compact) 32.sp else 37.sp,
                        letterSpacing = (-1.9).sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight),
                    contentAlignment = Alignment.Center
                ) {
                    // Vinyl + sleeve artwork, shared with the Favorite Album slide.
                    RewindAlbumHero(
                        album = topAlbum.album,
                        active = active,
                        sleeveSize = sleeveSize
                    )
                    RewindReveal(
                        active = active,
                        delayMillis = 1_420,
                        scaleFrom = 0.55f,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Text(
                            text = stringResource(R.string.rw_top_album_aoty),
                            color = onPink,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .background(rewindColors.value.pink, RoundedCornerShape(100.dp))
                                .padding(horizontal = 13.dp, vertical = 10.dp)
                                .graphicsLayer { rotationZ = -7f }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                AlbumTitleSwipe(
                    title = title,
                    progress = titleIn.value,
                    compact = compact
                )
                Spacer(Modifier.height(6.dp))
                RewindReveal(active, 1_920) {
                    Text(
                        text = firstNonBlank(topAlbum.album.cleanAuthorsText(), stringResource(R.string.rw_unknown_artist)),
                        color = onYellow.copy(alpha = 0.62f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                RewindReveal(active, 1_980) {
                    RewindLevelBadge(
                        level = rewindAlbumLevel(topAlbum.minutes),
                        foreground = onYellow,
                        pillBackground = rewindColors.value.ink,
                        pillForeground = rewindColors.value.cream,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                Spacer(Modifier.height(8.dp))
                RewindReveal(active, 2_050) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        AlbumMetric(stringResource(R.string.rw_label_minutes), formatRewindMinutes(topAlbum.minutes), Modifier.weight(1f))
                        AlbumMetric(stringResource(R.string.rw_label_songs), formatRewindNumber(topAlbum.songCount.toLong()), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun RewindTopAlbumCard(
    topAlbum: TopAlbum?,
    periodLabel: String,
    page: Int,
    pageCount: Int,
    active: Boolean,
    onNext: () -> Unit
) {
    RewindTopAlbumShow(
        topAlbum = topAlbum,
        periodLabel = periodLabel,
        page = page,
        pageCount = pageCount,
        active = active,
        onNext = onNext,
        kickerId = R.string.rw_top_album_kicker,
        headingId = R.string.rw_top_album_heading,
        emptyTitleId = R.string.rw_top_album_empty_title,
        emptyBodyId = R.string.rw_top_album_empty_body
    )
}

/**
 * Vinyl + sleeve artwork shared by the Album of the Year slide and the Favorite Album slide:
 * the sleeve enters from the left and the record rolls in from the right, both settling
 * around the center of the hero.
 */
@Composable
internal fun RewindAlbumHero(
    album: Album,
    active: Boolean,
    sleeveSize: Dp,
    modifier: Modifier = Modifier
) {
    val sleeveIn = remember { Animatable(0f) }
    val recordIn = remember { Animatable(0f) }
    LaunchedEffect(active) {
        sleeveIn.snapTo(0f)
        recordIn.snapTo(0f)
        if (!active) return@LaunchedEffect
        rewindRevealDelay(240)
        rewindDriveTo(sleeveIn, 1f, tween(720, easing = FastOutSlowInEasing))
        rewindRevealDelay(90)
        rewindDriveTo(recordIn, 1f, tween(900, easing = FastOutSlowInEasing))
    }

    val recordSize = sleeveSize * 0.88f
    val onOrange = rewindColors.value.textOn(rewindColors.value.orange)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // The vinyl starts outside the hero on the right and rolls into position.
        Box(
            modifier = Modifier
                .size(recordSize)
                .graphicsLayer {
                    val p = recordIn.value
                    alpha = p
                    translationX = (154f - 92f * p).dp.toPx()
                    translationY = (18f - 18f * p).dp.toPx()
                    rotationZ = 118f - 100f * p
                    scaleX = 0.72f + 0.28f * p
                    scaleY = 0.72f + 0.28f * p
                }
                .background(rewindColors.value.ink, CircleShape)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f
                repeat(7) { index ->
                    drawCircle(
                        color = rewindColors.value.cream.copy(alpha = 0.10f),
                        radius = radius * (0.30f + index * 0.085f),
                        center = center,
                        style = Stroke(width = 1.2f)
                    )
                }
                drawCircle(rewindColors.value.pink, radius * 0.22f, center)
                drawCircle(rewindColors.value.yellow, radius * 0.095f, center)
                drawCircle(rewindColors.value.ink, radius * 0.035f, center)
            }
        }
        // Sleeve enters from the opposite side and settles before the vinyl arrives.
        RewindArtworkWithFallback(
            imageUrl = album.thumbnailUrl,
            title = album.cleanTitle(),
            modifier = Modifier
                .size(sleeveSize)
                .graphicsLayer {
                    val p = sleeveIn.value
                    alpha = p
                    translationX = (-165f * (1f - p) - 24f).dp.toPx()
                    rotationZ = -11f + 8.5f * p
                    scaleX = 0.90f + 0.10f * p
                    scaleY = 0.90f + 0.10f * p
                },
            background = rewindColors.value.orange,
            foreground = onOrange
        )
    }
}

@Composable
private fun AlbumTitleSwipe(
    title: String,
    progress: Float,
    compact: Boolean
) {
    val textProgress = ((progress - 0.34f) / 0.66f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 58.dp else 68.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(if (compact) 43.dp else 51.dp)
                .background(rewindColors.value.pink, RoundedCornerShape(2.dp))
        )
        Text(
            text = title,
            color = rewindColors.value.textOn(rewindColors.value.yellow),
            fontSize = if (compact) 24.sp else 29.sp,
            lineHeight = if (compact) 24.sp else 29.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1.3).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .graphicsLayer {
                    alpha = textProgress
                    translationY = (1f - textProgress) * 18.dp.toPx()
                }
        )
    }
}

@Composable
private fun AlbumMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(rewindColors.value.ink, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = value,
            color = rewindColors.value.cream,
            fontSize = 19.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = label,
            color = rewindColors.value.pink,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.7.sp
        )
    }
}
