package app.n_zik.android.components.ui.screens.rewind.slides

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.TopPlaylist
import app.n_zik.android.components.ui.screens.rewind.rewindPlaylistLevel

@Composable
fun RewindTopPlaylistsCard(
    playlists: List<TopPlaylist>,
    periodLabel: String,
    page: Int,
    pageCount: Int,
    active: Boolean,
    onNext: () -> Unit
) {
    val topFive = playlists.take(5)
    // Same bright-slide treatment as the Deep Cuts slide.
    val onSlide = rewindColors.value.textOn(rewindColors.value.cream)
    RewindStoryShell(
        page = page,
        pageCount = pageCount,
        background = rewindColors.value.cream,
        progressColor = onSlide,
        onNext = onNext
    ) {
        if (topFive.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                RewindEmptyState(
                    title = stringResource(R.string.rw_top_playlists_empty_title),
                    body = stringResource(R.string.rw_top_playlists_empty_body),
                    foreground = onSlide,
                    accent = rewindColors.value.purple
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
                    text = stringResource(R.string.rw_top_playlists_heading),
                    color = onSlide,
                    fontSize = 34.sp,
                    lineHeight = 32.sp,
                    letterSpacing = (-1.8).sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.height(10.dp))
            RewindReveal(active, 200) {
                RewindLevelBadge(
                    level = rewindPlaylistLevel(topFive.first().minutes),
                    foreground = onSlide,
                    pillBackground = rewindColors.value.ink,
                    pillForeground = rewindColors.value.cream
                )
            }
            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                topFive.forEachIndexed { index, entry ->
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
                            // Deep Cuts style: standalone rank number on the left, artwork with
                            // scrim + origin icon on the right.
                            Text(
                                text = (index + 1).toString().padStart(2, '0'),
                                color = rewindColors.value.lime,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.graphicsLayer {
                                    rotationZ = if (index % 2 == 0) -2f else 2f
                                }
                            )
                            RewindPlaylistArtwork(
                                playlistId = entry.playlist.playlist.id,
                                title = entry.playlist.playlist.cleanName(),
                                name = entry.playlist.playlist.name,
                                browseId = entry.playlist.playlist.browseId,
                                isYoutubePlaylist = entry.playlist.playlist.isYoutubePlaylist,
                                sizeDp = 46.dp,
                                modifier = Modifier.size(46.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.playlist.playlist.cleanName().ifBlank { "—" },
                                    color = rowOn,
                                    fontSize = 12.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.rw_meta_songs, formatRewindNumber(entry.songCount.toLong())),
                                    color = rowOn.copy(alpha = 0.58f),
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = stringResource(R.string.rw_minutes_compact, formatRewindNumber(entry.minutes)),
                                color = rowOn.copy(alpha = 0.76f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            RewindReveal(active, 980) {
                Text(
                    text = stringResource(R.string.rw_top_playlists_sub),
                    color = onSlide.copy(alpha = 0.60f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
