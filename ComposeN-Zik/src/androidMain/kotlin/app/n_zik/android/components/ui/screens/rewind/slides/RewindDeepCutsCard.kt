package app.n_zik.android.components.ui.screens.rewind.slides

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.TopSong

@Composable
fun RewindDeepCutsCard(
    songs: List<TopSong>,
    year: Int,
    page: Int,
    pageCount: Int,
    active: Boolean,
    onNext: () -> Unit
) {
    val deepCuts = songs.drop(5).take(5)
    val onSlide = rewindColors.value.textOn(rewindColors.value.cream)
    RewindStoryShell(
        page = page,
        pageCount = pageCount,
        background = rewindColors.value.cream,
        progressColor = onSlide,
        onNext = onNext
    ) {
        if (deepCuts.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                RewindEmptyState(
                    title = stringResource(R.string.rw_deep_cuts_empty_title),
                    body = stringResource(R.string.rw_deep_cuts_empty_body),
                    foreground = onSlide,
                    accent = rewindColors.value.purple
                )
            }
            return@RewindStoryShell
        }
        Column(modifier = Modifier.fillMaxSize()) {
            RewindReveal(active, 40, direction = RewindRevealDirection.Left) {
                RewindKicker(stringResource(R.string.rw_deep_cuts_kicker, year), rewindColors.value.lime)
            }
            Spacer(Modifier.height(11.dp))
            RewindReveal(active, 110, direction = RewindRevealDirection.Left) {
                Text(
                    text = stringResource(R.string.rw_deep_cuts_heading),
                    color = onSlide,
                    fontSize = 34.sp,
                    lineHeight = 32.sp,
                    letterSpacing = (-1.8).sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                deepCuts.forEachIndexed { index, song ->
                    val rank = index + 6
                    val rowOn = if (index % 2 == 0) rewindColors.value.cream else rewindColors.value.textOn(rewindColors.value.purple)
                    RewindReveal(
                        active = active,
                        delayMillis = 260 + index * 105,
                        direction = if (index % 2 == 0) RewindRevealDirection.Left else RewindRevealDirection.Right,
                        scaleFrom = 0.92f
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (index % 2 == 0) rewindColors.value.ink else rewindColors.value.purple,
                                    RoundedCornerShape(11.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = rank.toString().padStart(2, '0'),
                                color = rewindColors.value.lime,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.graphicsLayer { rotationZ = if (index % 2 == 0) -2f else 2f }
                            )
                            RewindArtworkWithFallback(
                                imageUrl = song.song.thumbnailUrl,
                                title = song.song.cleanTitle(),
                                modifier = Modifier.size(46.dp),
                                background = rewindColors.value.pink,
                                foreground = rewindColors.value.textOn(rewindColors.value.pink)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.song.cleanTitle(),
                                    color = rowOn,
                                    fontSize = 12.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = firstNonBlank(song.song.cleanArtistsText(), stringResource(R.string.rw_unknown_artist)),
                                    color = rowOn.copy(alpha = 0.58f),
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${formatRewindNumber(song.playCount.toLong())}×",
                                color = rowOn.copy(alpha = 0.76f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            RewindReveal(active, 900) {
                Text(
                    text = stringResource(R.string.rw_deep_cuts_sub),
                    color = onSlide.copy(alpha = 0.60f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
