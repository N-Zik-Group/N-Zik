package app.n_zik.android.components.ui.screens.rewind.slides

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.TopAlbum

@Composable
fun RewindAlbumsCard(
    albums: List<TopAlbum>,
    periodLabel: String,
    page: Int,
    pageCount: Int,
    active: Boolean,
    onNext: () -> Unit
) {
    val topFive = albums.take(5)
    RewindStoryShell(
        page = page,
        pageCount = pageCount,
        background = rewindColors.value.red,
        progressColor = rewindColors.value.cream,
        onNext = onNext
    ) {
        if (topFive.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                RewindEmptyState(
                    title = stringResource(R.string.rw_albums_empty_title),
                    body = stringResource(R.string.rw_albums_empty_body),
                    foreground = rewindColors.value.cream,
                    accent = rewindColors.value.lime
                )
            }
            return@RewindStoryShell
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 700.dp
            val deckHeight = if (compact) 160.dp else 192.dp
            val topCover = if (compact) 116.dp else 140.dp
            Column(modifier = Modifier.fillMaxSize()) {
                RewindReveal(active, 40, direction = RewindRevealDirection.Left) {
                    RewindKicker(stringResource(R.string.rw_albums_kicker, periodLabel), rewindColors.value.lime)
                }
                Spacer(Modifier.height(9.dp))
                RewindReveal(active, 100, direction = RewindRevealDirection.Left) {
                    Text(
                        text = stringResource(R.string.rw_albums_heading),
                        color = rewindColors.value.cream,
                        fontSize = if (compact) 31.sp else 37.sp,
                        lineHeight = if (compact) 29.sp else 34.sp,
                        letterSpacing = (-1.8).sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(deckHeight),
                    contentAlignment = Alignment.Center
                ) {
                    // Draw from #5 to #1. #1 is therefore physically on top of the stack.
                    topFive.indices.reversed().forEach { index ->
                        val album = topFive[index]
                        val rank = index + 1
                        val isNumberOne = rank == 1
                        val coverSize = if (isNumberOne) topCover else topCover * (0.76f - index * 0.025f)
                        val x = when (rank) {
                            1 -> -42f
                            2 -> 10f
                            3 -> 36f
                            4 -> 58f
                            else -> 76f
                        }
                        val y = when (rank) {
                            1 -> 8f
                            2 -> 0f
                            3 -> -5f
                            4 -> -10f
                            else -> -14f
                        }
                        val rotation = when (rank) {
                            1 -> -5f
                            2 -> 1.5f
                            3 -> 5f
                            4 -> 8f
                            else -> 11f
                        }
                        RewindReveal(
                            active = active,
                            delayMillis = 280 + (5 - rank) * 105,
                            direction = if (rank % 2 == 0) RewindRevealDirection.Right else RewindRevealDirection.Left,
                            distance = 48.dp,
                            scaleFrom = if (isNumberOne) 0.72f else 0.82f,
                            modifier = Modifier.graphicsLayer {
                                translationX = x.dp.toPx()
                                translationY = y.dp.toPx()
                                rotationZ = rotation
                            }
                        ) {
                            Box {
                                RewindArtworkWithFallback(
                                    imageUrl = album.album.thumbnailUrl,
                                    title = album.album.cleanTitle(),
                                    modifier = Modifier.size(coverSize),
                                    background = rewindColors.value.ink,
                                    foreground = rewindColors.value.cream
                                )
                                Text(
                                    text = rank.toString(),
                                    color = if (isNumberOne) rewindColors.value.ink else rewindColors.value.cream,
                                    fontSize = if (isNumberOne) 14.sp else 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(if (isNumberOne) rewindColors.value.lime else rewindColors.value.ink)
                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                val numberOne = topFive.first()
                RewindReveal(active, 900, direction = RewindRevealDirection.Left, distance = 24.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rewindColors.value.ink, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = if (compact) 8.dp else 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "1",
                            color = rewindColors.value.lime,
                            fontSize = if (compact) 42.sp else 50.sp,
                            lineHeight = if (compact) 40.sp else 48.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-2.2).sp
                        )
                        RewindArtworkWithFallback(
                            imageUrl = numberOne.album.thumbnailUrl,
                            title = numberOne.album.cleanTitle(),
                            modifier = Modifier.size(if (compact) 54.dp else 64.dp),
                            background = rewindColors.value.red,
                            foreground = rewindColors.value.cream
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            DropCapAlbumTitle(numberOne.album.cleanTitle(), compact)
                            Text(
                                text = firstNonBlank(numberOne.album.cleanAuthorsText(), stringResource(R.string.rw_unknown_artist)),
                                color = rewindColors.value.cream.copy(alpha = 0.48f),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = stringResource(R.string.rw_minutes_compact, formatRewindNumber(numberOne.minutes)),
                            color = rewindColors.value.cream.copy(alpha = 0.65f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(if (compact) 5.dp else 7.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
                ) {
                    topFive.drop(1).forEachIndexed { index, album ->
                        val rank = index + 2
                        // Same alternating card treatment as the Deep Cuts slide
                        val isPurple = (rank - 1) % 2 != 0
                        val rowOn = if (isPurple) rewindColors.value.textOn(rewindColors.value.purple) else rewindColors.value.cream
                        RewindReveal(
                            active = active,
                            delayMillis = 1_060 + index * 90,
                            direction = RewindRevealDirection.Left,
                            distance = 14.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isPurple) rewindColors.value.purple else rewindColors.value.ink,
                                        RoundedCornerShape(11.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Text(
                                    text = rank.toString(),
                                    color = rowOn,
                                    fontSize = 18.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.width(24.dp)
                                )
                                RewindArtworkWithFallback(
                                    imageUrl = album.album.thumbnailUrl,
                                    title = album.album.cleanTitle(),
                                    modifier = Modifier.size(if (compact) 34.dp else 38.dp),
                                    background = rewindColors.value.ink,
                                    foreground = rewindColors.value.cream
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = album.album.cleanTitle(),
                                        color = rowOn,
                                        fontSize = 12.sp,
                                        lineHeight = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = firstNonBlank(album.album.cleanAuthorsText(), stringResource(R.string.rw_unknown_artist)),
                                        color = rowOn.copy(alpha = 0.40f),
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.rw_minutes_compact, formatRewindNumber(album.minutes)),
                                    color = rowOn.copy(alpha = 0.62f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
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
private fun DropCapAlbumTitle(
    title: String,
    compact: Boolean
) {
    val untitled = stringResource(R.string.rw_untitled)
    val clean = title.trim().ifBlank { untitled }
    val first = clean.take(1).uppercase()
    val rest = clean.drop(1)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = first,
            color = rewindColors.value.lime,
            fontSize = if (compact) 26.sp else 31.sp,
            lineHeight = if (compact) 25.sp else 30.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1.5).sp
        )
        Text(
            text = rest,
            color = rewindColors.value.cream,
            fontSize = if (compact) 12.sp else 14.sp,
            lineHeight = if (compact) 13.sp else 15.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
