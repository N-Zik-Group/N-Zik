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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.RewindData
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RewindDiscoveryCard(
    data: RewindData,
    page: Int,
    pageCount: Int,
    active: Boolean,
    onNext: () -> Unit
) {
    val universe = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active) {
            universe.snapTo(0f)
        } else {
            rewindRevealDelay(280)
            rewindDriveTo(universe, 1f, tween(1_350, easing = FastOutSlowInEasing))
        }
    }
    RewindStoryShell(
        page = page,
        pageCount = pageCount,
        background = rewindColors.value.ink,
        progressColor = rewindColors.value.cream,
        onNext = onNext,
        backgroundArt = {
            Canvas(Modifier.fillMaxSize()) {
                repeat(24) { index ->
                    val x = ((index * 71 + 19) % 100) / 100f * size.width
                    val y = ((index * 43 + 11) % 100) / 100f * size.height
                    drawCircle(
                        color = rewindColors.value.cream.copy(alpha = if (index % 6 == 0) 0.55f else 0.18f),
                        radius = if (index % 6 == 0) 1.7f else 1.0f,
                        center = Offset(x, y)
                    )
                }
            }
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 700.dp
            val orbitSize = if (compact) 180.dp else 220.dp
            val uniqueSize = when {
                data.totalUniqueSongs >= 100_000 -> if (compact) 44 else 50
                data.totalUniqueSongs >= 10_000 -> if (compact) 50 else 58
                else -> if (compact) 58 else 68
            }
            Column(modifier = Modifier.fillMaxSize()) {
                RewindReveal(active, 40, direction = RewindRevealDirection.Left) {
                    RewindKicker(stringResource(R.string.rw_discovery_kicker, data.periodLabel), rewindColors.value.lime)
                }
                Spacer(Modifier.height(11.dp))
                RewindReveal(active, 110, direction = RewindRevealDirection.Left) {
                    Text(
                        text = stringResource(R.string.rw_discovery_heading),
                        color = rewindColors.value.cream,
                        fontSize = if (compact) 34.sp else 40.sp,
                        lineHeight = if (compact) 32.sp else 37.sp,
                        letterSpacing = (-1.9).sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(orbitSize),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(orbitSize)) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val progress = universe.value
                        repeat(3) { orbit ->
                            val radius = size.minDimension * (0.20f + orbit * 0.12f)
                            drawArc(
                                color = rewindColors.value.cream.copy(alpha = 0.22f + orbit * 0.05f),
                                startAngle = -90f,
                                sweepAngle = 330f * progress,
                                useCenter = false,
                                topLeft = Offset(center.x - radius, center.y - radius),
                                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        val colors = listOf(
                            rewindColors.value.pink,
                            rewindColors.value.lime,
                            rewindColors.value.purple,
                            rewindColors.value.orange,
                            rewindColors.value.blue
                        )
                        colors.forEachIndexed { index, color ->
                            val local = ((progress * 1.35f) - index * 0.10f).coerceIn(0f, 1f)
                            val angle = Math.toRadians((-75 + index * 66).toDouble())
                            val radius = size.minDimension * (0.20f + (index % 3) * 0.12f)
                            val point = Offset(
                                center.x + cos(angle).toFloat() * radius,
                                center.y + sin(angle).toFloat() * radius
                            )
                            drawCircle(color, (6.dp.toPx() + index * 1.2.dp.toPx()) * local, point)
                        }
                        drawCircle(rewindColors.value.lime, size.minDimension * 0.11f * progress, center)
                        drawCircle(rewindColors.value.ink, size.minDimension * 0.055f * progress, center)
                    }
                }
                // Number has its own zone. It can no longer collide with the orbit artwork.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    RewindAnimatedNumber(
                        value = data.totalUniqueSongs.toLong(),
                        active = active,
                        color = rewindColors.value.cream,
                        fontSize = uniqueSize,
                        delayMillis = 640,
                        durationMillis = 1_000
                    )
                    Text(
                        text = stringResource(R.string.rw_label_unique_songs),
                        color = rewindColors.value.lime,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.0.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                RewindReveal(active, 1_050) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DiscoveryMetric(stringResource(R.string.rw_label_artists), data.totalUniqueArtists, rewindColors.value.purple, Modifier.weight(1f))
                        DiscoveryMetric(stringResource(R.string.rw_label_albums), data.totalUniqueAlbums, rewindColors.value.pink, Modifier.weight(1f))
                        DiscoveryMetric(stringResource(R.string.rw_label_playlists), data.totalUniquePlaylists, rewindColors.value.orange, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryMetric(
    label: String,
    value: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val foreground = if (color == rewindColors.value.pink || color == rewindColors.value.orange) rewindColors.value.ink else rewindColors.value.cream
    Column(
        modifier = modifier
            .background(color, RoundedCornerShape(9.dp))
            .padding(horizontal = 9.dp, vertical = 10.dp)
    ) {
        Text(
            text = formatRewindNumber(value.toLong()),
            color = foreground,
            fontSize = 18.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = label,
            color = foreground.copy(alpha = 0.62f),
            fontSize = 7.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}
