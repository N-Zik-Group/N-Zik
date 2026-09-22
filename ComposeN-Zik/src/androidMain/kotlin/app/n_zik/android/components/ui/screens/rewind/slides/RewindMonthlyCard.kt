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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.RewindData

@Composable
fun RewindMonthlyCard(
    data: RewindData,
    page: Int,
    pageCount: Int,
    active: Boolean,
    onNext: () -> Unit
) {
    val chart = remember { Animatable(0f) }
    val months = data.monthlyStats.take(12)
    val peak = months.maxByOrNull { it.minutes }
    LaunchedEffect(active) {
        if (!active) {
            chart.snapTo(0f)
        } else {
            rewindRevealDelay(420)
            rewindDriveTo(chart, 1f, tween(1_650, easing = FastOutSlowInEasing))
        }
    }
    val onSlide = rewindColors.value.textOn(rewindColors.value.cream)
    RewindStoryShell(
        page = page,
        pageCount = pageCount,
        background = rewindColors.value.cream,
        progressColor = onSlide,
        onNext = onNext
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 700.dp
            val chartHeight = if (compact) 205.dp else 250.dp
            Column(modifier = Modifier.fillMaxSize()) {
                RewindReveal(active, 40, direction = RewindRevealDirection.Left) {
                    RewindKicker(stringResource(R.string.rw_monthly_kicker, data.year), rewindColors.value.lime)
                }
                Spacer(Modifier.height(11.dp))
                RewindReveal(active, 110, direction = RewindRevealDirection.Left) {
                    Text(
                        text = stringResource(R.string.rw_monthly_heading),
                        color = onSlide,
                        fontSize = if (compact) 35.sp else 41.sp,
                        lineHeight = if (compact) 33.sp else 38.sp,
                        letterSpacing = (-1.9).sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(8.dp))
                RewindReveal(active, 210) {
                    Text(
                        text = peak?.let { stringResource(R.string.rw_monthly_peak_sub, it.month.uppercase()) } ?: stringResource(R.string.rw_monthly_default_sub),
                        color = onSlide,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.7.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rewindColors.value.ink, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(chartHeight)
                        ) {
                            val maxMinutes = months.maxOfOrNull { it.minutes }?.coerceAtLeast(1L) ?: 1L
                            val gap = 5.dp.toPx()
                            val barWidth = if (months.isEmpty()) size.width else (size.width - gap * 11f) / 12f
                            months.forEachIndexed { index, month ->
                                val local = ((chart.value * 1.55f) - index * 0.05f).coerceIn(0f, 1f)
                                val ratio = month.minutes.toFloat() / maxMinutes.toFloat()
                                val barHeight = size.height * ratio * local
                                val x = index * (barWidth + gap)
                                drawRoundRect(
                                    color = when {
                                        month == peak -> rewindColors.value.lime
                                        index % 3 == 0 -> rewindColors.value.purple
                                        index % 3 == 1 -> rewindColors.value.pink
                                        else -> rewindColors.value.orange
                                    },
                                    topLeft = Offset(x, size.height - barHeight),
                                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(4.dp.toPx())
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            months.forEach { month ->
                                Text(
                                    text = month.month.take(1).uppercase(),
                                    color = if (month == peak) rewindColors.value.lime else rewindColors.value.cream.copy(alpha = 0.50f),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
                RewindReveal(active, 1_250) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        MonthMetric(
                            label = stringResource(R.string.rw_monthly_peak_month),
                            value = peak?.month?.uppercase() ?: "—",
                            background = rewindColors.value.purple,
                            foreground = rewindColors.value.textOn(rewindColors.value.purple),
                            modifier = Modifier.weight(1f)
                        )
                        MonthMetric(
                            label = stringResource(R.string.rw_monthly_peak_minutes),
                            value = formatRewindNumber(peak?.minutes ?: 0L),
                            background = rewindColors.value.pink,
                            foreground = rewindColors.value.textOn(rewindColors.value.pink),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthMetric(
    label: String,
    value: String,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(background, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = value,
            color = foreground,
            fontSize = 19.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = label,
            color = foreground.copy(alpha = 0.62f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.7.sp
        )
    }
}
