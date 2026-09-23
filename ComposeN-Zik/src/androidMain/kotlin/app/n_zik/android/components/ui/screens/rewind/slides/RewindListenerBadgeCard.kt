package app.n_zik.android.components.ui.screens.rewind.slides

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.RewindData

@Composable
fun RewindListenerBadgeCard(
    data: RewindData,
    page: Int,
    pageCount: Int,
    active: Boolean,
    onNext: () -> Unit
) {
    val badge = calculateListenerBadge(data)
    val tierNames = listOf(
        R.string.rw_badge_tier_0_name,
        R.string.rw_badge_tier_1_name,
        R.string.rw_badge_tier_2_name,
        R.string.rw_badge_tier_3_name,
        R.string.rw_badge_tier_4_name,
        R.string.rw_badge_tier_5_name,
        R.string.rw_badge_tier_6_name,
        R.string.rw_badge_tier_7_name,
        R.string.rw_badge_tier_8_name
    )
    RewindStoryShell(
        page = page,
        pageCount = pageCount,
        background = rewindColors.value.ink,
        progressColor = rewindColors.value.cream,
        onNext = onNext,
        backgroundArt = {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = rewindColors.value.purple,
                    radius = size.width * 0.34f,
                    center = Offset(size.width * 0.94f, size.height * 0.17f)
                )
                drawCircle(
                    color = rewindColors.value.pink.copy(alpha = 0.45f),
                    radius = size.width * 0.20f,
                    center = Offset(size.width * 0.05f, size.height * 0.92f)
                )
            }
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 700.dp
            val badgeSize = if (compact) 178.dp else 215.dp
            Column(modifier = Modifier.fillMaxSize()) {
                RewindReveal(active, 40, direction = RewindRevealDirection.Left) {
                    RewindKicker(stringResource(R.string.rw_badge_kicker, data.periodLabel), rewindColors.value.lime)
                }
                Spacer(Modifier.height(12.dp))
                RewindReveal(active, 120, direction = RewindRevealDirection.Left) {
                    Text(
                        text = stringResource(R.string.rw_badge_heading),
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
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    RewindReveal(active, 320, scaleFrom = 0.66f) {
                        Box(
                            modifier = Modifier
                                .size(badgeSize)
                                .background(rewindColors.value.lime, CircleShape)
                                .padding(15.dp)
                                .background(rewindColors.value.ink, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.rw_badge_index_label),
                                    color = rewindColors.value.cream.copy(alpha = 0.55f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.0.sp
                                )
                                RewindAnimatedNumber(
                                    value = badge.index.toLong(),
                                    active = active,
                                    color = rewindColors.value.lime,
                                    fontSize = if (compact) 58 else 70,
                                    delayMillis = 500,
                                    durationMillis = 900
                                )
                                Text(
                                    text = stringResource(R.string.rw_badge_index_sublabel),
                                    color = rewindColors.value.cream,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }
                }
                RewindReveal(active, 720, direction = RewindRevealDirection.Left) {
                    Text(
                        text = stringResource(badge.titleId),
                        color = rewindColors.value.lime,
                        fontSize = if (compact) 30.sp else 36.sp,
                        lineHeight = if (compact) 29.sp else 34.sp,
                        letterSpacing = (-1.5).sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(5.dp))
                RewindReveal(active, 830) {
                    Text(
                        text = stringResource(badge.subtitleId),
                        color = rewindColors.value.cream.copy(alpha = 0.68f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    tierNames.forEachIndexed { index, nameId ->
                        val reached = index <= badge.tier
                        val current = index == badge.tier
                        RewindReveal(
                            active = active,
                            delayMillis = 900 + index * 45,
                            direction = RewindRevealDirection.Left,
                            distance = 12.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (current) 19.dp else 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(if (current) 8.dp else 4.dp)
                                        .background(
                                            when {
                                                current -> rewindColors.value.lime
                                                reached -> rewindColors.value.cream.copy(alpha = 0.55f)
                                                else -> rewindColors.value.cream.copy(alpha = 0.12f)
                                            },
                                            RoundedCornerShape(100.dp)
                                        )
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = stringResource(nameId),
                                    color = when {
                                        current -> rewindColors.value.lime
                                        reached -> rewindColors.value.cream.copy(alpha = 0.76f)
                                        else -> rewindColors.value.cream.copy(alpha = 0.28f)
                                    },
                                    fontSize = if (current) 11.sp else 9.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth(0.32f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
