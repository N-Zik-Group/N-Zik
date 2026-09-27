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
import app.n_zik.android.components.ui.screens.rewind.RewindPeriod

/**
 * The all-time deck's year-by-year slide (page 14 of [RewindPeriod.Global]): one bar per
 * calendar year with data, ascending — the global twin of [RewindMonthlyCard], which shows
 * twelve months of one year. The fetcher already shaped [RewindData.monthlyStats] for the
 * global period: one [MonthlyStat] per year, the year string as the label.
 */
@Composable
fun RewindYearsCard(
    data: RewindData,
    page: Int,
    pageCount: Int,
    active: Boolean,
    onNext: () -> Unit,
    onShareSlide: (() -> Unit)? = null
) {
    val chart = remember { Animatable(0f) }
    val years = data.monthlyStats
    // Highlight the peak by position, not data-class equality: ties must not light several
    // bars, and an all-zero period must not highlight the first bar
    // (spec GH-275, patch "Peak highlighting by data-class equality").
    val peakMinutes = years.maxOfOrNull { it.minutes }?.takeIf { it > 0 }
    val peakIndex = peakMinutes?.let { peak -> years.indexOfFirst { it.minutes == peak } } ?: -1
    val peak = years.getOrNull(peakIndex)
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
        onNext = onNext,
        onShareSlide = onShareSlide
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 700.dp
            val chartHeight = if (compact) 205.dp else 250.dp
            Column(modifier = Modifier.fillMaxSize()) {
                RewindReveal(active, 40, direction = RewindRevealDirection.Left) {
                    RewindKicker(stringResource(R.string.rw_years_kicker, data.periodLabel), rewindColors.value.lime)
                }
                Spacer(Modifier.height(11.dp))
                RewindReveal(active, 110, direction = RewindRevealDirection.Left) {
                    Text(
                        text = stringResource(R.string.rw_years_heading),
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
                        text = peak?.let { stringResource(R.string.rw_years_peak_sub, it.month.uppercase()) } ?: stringResource(R.string.rw_years_default_sub),
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
                            val barCount = years.size
                            if (barCount > 0) {
                                val maxMinutes = years.maxOf { it.minutes }.coerceAtLeast(1L)
                                val gap = 5.dp.toPx()
                                val barWidth = if (barCount == 1) size.width else (size.width - gap * (barCount - 1)) / barCount
                                years.forEachIndexed { index, year ->
                                    val local = ((chart.value * 1.55f) - index * 0.05f).coerceIn(0f, 1f)
                                    val ratio = year.minutes.toFloat() / maxMinutes.toFloat()
                                    val barHeight = size.height * ratio * local
                                    val x = index * (barWidth + gap)
                                    drawRoundRect(
                                        color = when {
                                            index == peakIndex -> rewindColors.value.lime
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
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            years.forEachIndexed { index, year ->
                                // The last two digits keep the labels readable even on long histories
                                Text(
                                    text = year.month.takeLast(2),
                                    color = if (index == peakIndex) rewindColors.value.lime else rewindColors.value.cream.copy(alpha = 0.50f),
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
                        YearsMetric(
                            label = stringResource(R.string.rw_years_peak_year),
                            value = peak?.month ?: "—",
                            background = rewindColors.value.purple,
                            foreground = rewindColors.value.textOn(rewindColors.value.purple),
                            modifier = Modifier.weight(1f)
                        )
                        YearsMetric(
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
private fun YearsMetric(
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
