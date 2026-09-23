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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.CalendarDayStat
import app.n_zik.android.components.ui.screens.rewind.RewindData

/**
 * "DAY BY DAY" slide of a monthly deck: one bar per calendar day of the month. The annual deck
 * shows [RewindMonthlyCard] on this page instead, since a month-by-month chart of a single
 * month only has one bar of data.
 */
@Composable
fun RewindDaysCard(
    data: RewindData,
    page: Int,
    pageCount: Int,
    active: Boolean,
    onNext: () -> Unit
) {
    val chart = remember { Animatable(0f) }
    // One bar per calendar day of the month; days without stats (data not loaded) render at zero.
    val days = (1..data.daysInPeriod).map { day ->
        data.calendarDayStats.firstOrNull { stat -> stat.day == day } ?: CalendarDayStat(day, 0L, 0)
    }
    val peak = days.maxByOrNull { it.minutes }?.takeIf { it.minutes > 0 }
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
                    RewindKicker(stringResource(R.string.rw_days_kicker, data.periodLabel), rewindColors.value.lime)
                }
                Spacer(Modifier.height(11.dp))
                RewindReveal(active, 110, direction = RewindRevealDirection.Left) {
                    Text(
                        text = stringResource(R.string.rw_days_heading),
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
                        text = peak?.let { stringResource(R.string.rw_days_peak_sub, it.day) } ?: stringResource(R.string.rw_days_default_sub),
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
                            val gapPx = 2.dp.toPx()
                            val maxMinutes = days.maxOfOrNull { it.minutes }?.coerceAtLeast(1L) ?: 1L
                            val barWidth =
                                if (days.isEmpty()) size.width
                                else (size.width - gapPx * (days.size - 1f)) / days.size
                            // The monthly card staggers 12 bars by 0.05f; spread the same total
                            // delay over the (many more) day bars so the last one still lands.
                            val stagger = 0.55f / days.size.coerceAtLeast(1)
                            days.forEachIndexed { index, day ->
                                val local = ((chart.value * 1.55f) - index * stagger).coerceIn(0f, 1f)
                                val ratio = day.minutes.toFloat() / maxMinutes.toFloat()
                                val barHeight = size.height * ratio * local
                                val x = index * (barWidth + gapPx)
                                drawRoundRect(
                                    color = when {
                                        day == peak -> rewindColors.value.lime
                                        index % 3 == 0 -> rewindColors.value.purple
                                        index % 3 == 1 -> rewindColors.value.pink
                                        else -> rewindColors.value.orange
                                    },
                                    topLeft = Offset(x, size.height - barHeight),
                                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(2.dp.toPx())
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // The label row mirrors the bar layout (same count, same gaps) so every
                        // day number sits under its bar.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            days.forEach { day ->
                                Text(
                                    text = day.day.toString(),
                                    color = if (day == peak) rewindColors.value.lime else rewindColors.value.cream.copy(alpha = 0.50f),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
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
                        DayMetric(
                            label = stringResource(R.string.rw_days_peak_day),
                            value = peak?.day?.toString() ?: "—",
                            background = rewindColors.value.purple,
                            foreground = rewindColors.value.textOn(rewindColors.value.purple),
                            modifier = Modifier.weight(1f)
                        )
                        DayMetric(
                            label = stringResource(R.string.rw_days_peak_minutes),
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
private fun DayMetric(
    label: String,
    value: String,
    background: Color,
    foreground: Color,
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
