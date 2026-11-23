package app.n_zik.android.components.ui.screens.rewind

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.hypnoticcanvas.shaderBackground
import com.mikepenz.hypnoticcanvas.shaders.GradientFlow

import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.slides.rewindColors
import app.n_zik.android.components.ui.screens.rewind.slides.rewindCount

internal const val MinimumOpeningRevealMs = 6_800L

/**
 * Deliberately dramatic, non-looping opening sequence.
 * The requested Rewind period supplies the opening label.
 */
@Composable
internal fun RewindLoadingScreen(
    periodLabel: String,
    isMonthly: Boolean,
    data: RewindData?
) {
    val timeline = remember(periodLabel) { Animatable(0f) }
    LaunchedEffect(periodLabel) {
        timeline.snapTo(0f)
        timeline.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = MinimumOpeningRevealMs.toInt(),
                easing = LinearEasing
            )
        )
    }
    val p = timeline.value
    val cream = rewindColors.value.cream
    val ink = rewindColors.value.ink
    val lime = rewindColors.value.lime
    val purple = rewindColors.value.purple
    val pink = rewindColors.value.pink
    val orange = rewindColors.value.orange
    Box(
        modifier = Modifier
            .fillMaxSize()
            .shaderBackground(GradientFlow)
            .background(ink.copy(alpha = 0.35f))
    ) {
        // Campaign shapes run edge-to-edge across the whole screen
        Canvas(Modifier.fillMaxSize()) {
            // Angular campaign fields only. The loader never spins or loops.
            val purpleIn = segment(p, 0.02f, 0.18f)
            val pinkIn = segment(p, 0.10f, 0.27f)
            val orangeIn = segment(p, 0.18f, 0.34f)
            val purpleShape = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.66f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height * 0.40f)
                lineTo(size.width * 0.88f, size.height * 0.32f)
                close()
            }
            drawPath(purpleShape, purple.copy(alpha = purpleIn))
            val pinkShape = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, size.height * 0.72f)
                lineTo(size.width * 0.52f, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(pinkShape, pink.copy(alpha = pinkIn))
            val orangeShape = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.84f, size.height * 0.56f)
                lineTo(size.width, size.height * 0.50f)
                lineTo(size.width, size.height * 0.76f)
                close()
            }
            drawPath(orangeShape, orange.copy(alpha = orangeIn))
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 26.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.discord_presence_name),
                    color = cream.copy(alpha = segment(p, 0.02f, 0.14f)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.3.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = periodLabel,
                    color = lime.copy(alpha = segment(p, 0.08f, 0.20f)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.1.sp
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(286.dp)
            ) {
                val wordsOut = 1f - segment(p, 0.50f, 0.62f)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = wordsOut },
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    LoadingWord(stringResource(R.string.rewind_loader_word_your), lime, ink, segment(p, 0.11f, 0.22f), fromRight = false)
                    LoadingWord(
                        stringResource(if (isMonthly) R.string.rewind_loader_word_month else R.string.rewind_loader_word_year),
                        pink,
                        ink,
                        segment(p, 0.19f, 0.30f),
                        fromRight = true
                    )
                    LoadingWord(stringResource(R.string.rewind_loader_word_in), orange, ink, segment(p, 0.27f, 0.38f), fromRight = false)
                    LoadingWord(stringResource(R.string.rewind_loader_word_music), cream, ink, segment(p, 0.35f, 0.46f), fromRight = true)
                }
                RewindLoaderLockup(
                    periodLabel = periodLabel,
                    progress = segment(p, 0.56f, 0.80f),
                    cream = cream,
                    lime = lime,
                    pink = pink,
                    ink = ink,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                )
            }
            Spacer(Modifier.weight(1f))
            // Keep the understated status language: it worked better than the old giant
            // "READY, username" message.
            val finalMessage = when {
                p < 0.68f -> stringResource(R.string.rewind_loader_status_building)
                p < 0.76f -> data?.let { rewindCount(R.plurals.rewind_loader_status_play_count, it.stats.totalPlays.toLong()) } ?: stringResource(R.string.rewind_loader_status_counting_plays)
                p < 0.84f -> data?.let { rewindCount(R.plurals.rewind_loader_status_song_count, it.totalUniqueSongs.toLong()) } ?: stringResource(R.string.rewind_loader_status_counting_songs)
                p < 0.91f -> data?.let { rewindCount(R.plurals.rewind_loader_status_minute_count, it.stats.totalMinutes) } ?: stringResource(R.string.rewind_loader_status_counting_minutes)
                p < 0.97f -> data?.let { rewindCount(R.plurals.rewind_loader_status_day_count, it.daysWithMusic.toLong()) } ?: stringResource(R.string.rewind_loader_status_counting_days)
                else -> stringResource(R.string.rewind_loader_status_ready)
            }
            Text(
                text = finalMessage,
                color = if (p >= 0.97f) lime else cream.copy(alpha = 0.78f),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp
            )
            Spacer(Modifier.height(13.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(cream.copy(alpha = 0.14f), RoundedCornerShape(100.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(p.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(if (p > 0.90f) lime else cream, RoundedCornerShape(100.dp))
                )
            }
        }
    }
}

@Composable
private fun LoadingWord(
    text: String,
    background: Color,
    foreground: Color,
    progress: Float,
    fromRight: Boolean
) {
    Text(
        text = text,
        color = foreground,
        fontSize = 42.sp,
        lineHeight = 42.sp,
        letterSpacing = (-2.2).sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .graphicsLayer {
                alpha = progress
                val distance = 90.dp.toPx() * (1f - progress)
                translationX = if (fromRight) distance else -distance
            }
            .background(background)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

@Composable
private fun RewindLoaderLockup(
    periodLabel: String,
    progress: Float,
    cream: Color,
    lime: Color,
    pink: Color,
    ink: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.height(190.dp), contentAlignment = Alignment.CenterStart) {
        // Colored shadow + compressed foreground makes a custom display lockup without requiring
        // a bundled font file or a project-specific font resource.
        Text(
            text = stringResource(R.string.rewind_loader_lockup),
            color = pink,
            fontSize = 72.sp,
            lineHeight = 68.sp,
            letterSpacing = (-4.8).sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.graphicsLayer {
                alpha = progress * 0.95f
                translationX = (12f + (1f - progress) * 46f).dp.toPx()
                translationY = 9.dp.toPx()
                scaleX = 0.78f
                scaleY = 1.04f
                rotationZ = -2.3f
            }
        )
        Text(
            text = stringResource(R.string.rewind_loader_lockup),
            color = cream,
            fontSize = 72.sp,
            lineHeight = 68.sp,
            letterSpacing = (-4.8).sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.graphicsLayer {
                alpha = progress
                translationX = ((1f - progress) * -54f).dp.toPx()
                scaleX = 0.78f
                scaleY = 1.04f
                rotationZ = -2.3f
            }
        )
        Text(
            text = periodLabel,
            color = ink,
            fontSize = 43.sp,
            lineHeight = 42.sp,
            letterSpacing = (-2.3).sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .graphicsLayer {
                    alpha = segment(progress, 0.42f, 1f)
                    translationX = ((1f - segment(progress, 0.42f, 1f)) * 46f).dp.toPx()
                    rotationZ = 2.5f
                }
                .background(lime, RoundedCornerShape(2.dp))
                .padding(horizontal = 12.dp, vertical = 2.dp)
        )
    }
}

private fun segment(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    return ((value - start) / (end - start)).coerceIn(0f, 1f)
}
