package app.n_zik.android.components.ui.screens.rewind.slides

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.TopArtist

@Composable
fun RewindTopArtistsCard(
    artists: List<TopArtist>,
    periodLabel: String,
    page: Int,
    pageCount: Int,
    active: Boolean,
    onNext: () -> Unit,
    onShareSlide: (() -> Unit)? = null
) {
    val textScale = LocalRewindTextScale.current
    val topFive = artists.take(5)
    val top = topFive.firstOrNull()
    val onSlide = rewindColors.value.textOn(rewindColors.value.cream)
    RewindStoryShell(
        page = page,
        pageCount = pageCount,
        background = rewindColors.value.cream,
        progressColor = onSlide,
        onNext = onNext,
        onShareSlide = onShareSlide
    ) {
        if (top == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                RewindEmptyState(
                    title = stringResource(R.string.rw_top_artists_empty_title),
                    body = stringResource(R.string.rw_top_artists_empty_body),
                    foreground = onSlide,
                    accent = rewindColors.value.lime
                )
            }
            return@RewindStoryShell
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 700.dp
            val heroWidth = if (compact) 112.dp else 132.dp
            val heroHeight = if (compact) 142.dp else 166.dp
            val rowArt = if (compact) 38.dp else 44.dp
            Column(modifier = Modifier.fillMaxSize()) {
                RewindReveal(active, 40, direction = RewindRevealDirection.Left) {
                    RewindKicker(stringResource(R.string.rw_top_artists_kicker, periodLabel), rewindColors.value.lime)
                }
                Spacer(Modifier.height(10.dp))
                RewindReveal(active, 120, direction = RewindRevealDirection.Left) {
                    RewindAdaptiveTitle(
                        text = stringResource(R.string.rw_top_artists_heading),
                        color = onSlide,
                        fontSize = textScale.size(if (compact) 34.sp else 40.sp),
                        lineHeight = textScale.size(if (compact) 32.sp else 37.sp),
                        letterSpacing = textScale.letterSpacing((-1.9).sp),
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(15.dp))
                RewindReveal(active, 360, direction = RewindRevealDirection.Left, distance = 34.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rewindColors.value.ink, RoundedCornerShape(11.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RewindArtistArtwork(
                            artistName = top.artist.cleanName(),
                            primaryUrl = top.artist.thumbnailUrl,
                            preferWikipedia = true,
                            circular = false,
                            modifier = Modifier
                                .width(heroWidth)
                                .height(heroHeight)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "01",
                                color = rewindColors.value.lime,
                                fontSize = textScale.size(38.sp),
                                lineHeight = textScale.size(36.sp),
                                fontWeight = FontWeight.Black,
                                letterSpacing = textScale.letterSpacing((-2.0).sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // The name is the slide hero — two lines max, then marquee
                            RewindAdaptiveTitle(
                                text = top.artist.cleanName(),
                                color = rewindColors.value.cream,
                                fontSize = textScale.size(if (compact) 18.sp else 21.sp),
                                lineHeight = textScale.size(if (compact) 19.sp else 22.sp),
                                fontWeight = FontWeight.Black
                            )
                            Spacer(Modifier.height(7.dp))
                            Text(
                                text = stringResource(R.string.rw_minutes_compact, formatRewindNumber(top.minutes)),
                                color = rewindColors.value.cream.copy(alpha = 0.50f),
                                fontSize = textScale.size(10.sp),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.rewindMarqueeOnly()
                            )
                        }
                    }
                }
                Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
                ) {
                    topFive.drop(1).forEachIndexed { index, artist ->
                        val rank = index + 2
                        // Same alternating card treatment as the Deep Cuts slide:
                        // even positions in the top list get an ink card, odd ones purple
                        val isPurple = (rank - 1) % 2 != 0
                        val rowOn = if (isPurple) rewindColors.value.textOn(rewindColors.value.purple) else rewindColors.value.cream
                        RewindReveal(
                            active = active,
                            delayMillis = 620 + index * 120,
                            direction = RewindRevealDirection.Right,
                            distance = 24.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isPurple) rewindColors.value.purple else rewindColors.value.ink,
                                        RoundedCornerShape(11.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = rank.toString().padStart(2, '0'),
                                    color = rewindColors.value.lime,
                                    fontSize = textScale.size(18.sp),
                                    lineHeight = textScale.size(18.sp),
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(min = 29.dp)
                                )
                                RewindArtistArtwork(
                                    artistName = artist.artist.cleanName(),
                                    primaryUrl = artist.artist.thumbnailUrl,
                                    preferWikipedia = artist.artist.thumbnailUrl.isNullOrBlank(),
                                    circular = false,
                                    modifier = Modifier.size(rowArt)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = artist.artist.cleanName(),
                                        color = rowOn,
                                        fontSize = textScale.size(12.sp),
                                        lineHeight = textScale.size(14.sp),
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.rewindMarqueeOnly()
                                    )
                                    Text(
                                        text = stringResource(R.string.rw_minutes_compact, formatRewindNumber(artist.minutes)),
                                        color = rowOn.copy(alpha = 0.42f),
                                        fontSize = textScale.size(9.sp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.rewindMarqueeOnly()
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
