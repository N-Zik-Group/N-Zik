package app.n_zik.android.components.ui.screens.rewind.slides

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.TopSong

@Composable
fun RewindTopSongsCard(
    songs: List<TopSong>,
    periodLabel: String,
    page: Int,
    pageCount: Int,
    active: Boolean,
    onNext: () -> Unit
) {
    val topFive = songs.take(5)
    val onAccent = rewindColors.value.textOn(rewindColors.value.lime)
    RewindStoryShell(
        page = page,
        pageCount = pageCount,
        background = rewindColors.value.lime,
        progressColor = onAccent,
        onNext = onNext,
        backgroundArt = {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = rewindColors.value.pink,
                    radius = size.width * 0.15f,
                    center = Offset(size.width * 0.96f, size.height * 0.10f)
                )
            }
        }
    ) {
        if (topFive.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                RewindEmptyState(
                    title = stringResource(R.string.rw_top_songs_empty_title),
                    body = stringResource(R.string.rw_top_songs_empty_body),
                    foreground = onAccent,
                    accent = rewindColors.value.pink
                )
            }
            return@RewindStoryShell
        }
        Column(modifier = Modifier.fillMaxSize()) {
            RewindReveal(active, 40, direction = RewindRevealDirection.Left) {
                RewindKicker(stringResource(R.string.rw_top_five_kicker, periodLabel), rewindColors.value.lime)
            }
            Spacer(Modifier.height(11.dp))
            RewindReveal(active, 110, direction = RewindRevealDirection.Left) {
                Text(
                    text = stringResource(R.string.rw_top_songs_heading),
                    color = onAccent,
                    fontSize = 35.sp,
                    lineHeight = 33.sp,
                    letterSpacing = (-1.9).sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                topFive.forEachIndexed { index, song ->
                    // Same alternating card treatment as the Deep Cuts slide:
                    // even positions get an ink card, odd ones purple
                    val rowOn = if (index % 2 != 0) rewindColors.value.textOn(rewindColors.value.purple) else rewindColors.value.cream
                    RewindRankRow(
                        rank = index + 1,
                        title = song.song.cleanTitle(),
                        subtitle = firstNonBlank(song.song.cleanArtistsText(), stringResource(R.string.rw_unknown_artist)),
                        meta = stringResource(R.string.rw_meta_plays, formatRewindNumber(song.playCount.toLong())),
                        imageUrl = song.song.thumbnailUrl,
                        foreground = rowOn,
                        accent = if (index == 0) rewindColors.value.pink else rowOn,
                        active = active,
                        delayMillis = 240 + index * 120,
                        rowBackground = if (index % 2 != 0) rewindColors.value.purple else rewindColors.value.ink
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            RewindReveal(active, 980) {
                Text(
                    text = stringResource(R.string.rw_top_songs_next),
                    color = onAccent.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
