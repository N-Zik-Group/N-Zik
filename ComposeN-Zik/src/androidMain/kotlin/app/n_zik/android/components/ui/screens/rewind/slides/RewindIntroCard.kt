package app.n_zik.android.components.ui.screens.rewind.slides

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.RewindData
import app.n_zik.android.components.ui.screens.rewind.RewindPeriod
import app.n_zik.android.components.ui.screens.rewind.rewindShareCaptureActive
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun RewindIntroCard(
    data: RewindData,
    username: String,
    page: Int,
    pageCount: Int,
    active: Boolean,
    onNext: () -> Unit,
    onShareSlide: (() -> Unit)? = null
) {
    var revealComplete by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        revealComplete = false
        if (active) {
            // Export capture: reveal instantly; the normal opening holds 2_950 ms first
            if (!rewindShareCaptureActive.value) delay(2_950)
            revealComplete = true
        }
    }
    val defaultUsername = stringResource(R.string.rw_default_username)
    val displayName = username.trim().ifBlank { defaultUsername }
    val onLime = rewindColors.value.textOn(rewindColors.value.lime)
    RewindStoryShell(
        page = page,
        pageCount = pageCount,
        background = rewindColors.value.purple,
        progressColor = rewindColors.value.cream,
        onNext = if (revealComplete) onNext else null,
        onShareSlide = onShareSlide,
        backgroundArt = {
            Canvas(Modifier.fillMaxSize()) {
                // Deliberately angular. No records, wheels or circular "fingerprint" artwork.
                val orange = Path().apply {
                    moveTo(size.width * 0.76f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height * 0.48f)
                    lineTo(size.width * 0.91f, size.height * 0.40f)
                    close()
                }
                drawPath(orange, rewindColors.value.orange)
                val pink = Path().apply {
                    moveTo(0f, size.height * 0.73f)
                    lineTo(size.width * 0.48f, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(pink, rewindColors.value.pink)
                val inkSlash = Path().apply {
                    moveTo(size.width * 0.88f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height * 0.25f)
                    lineTo(size.width * 0.94f, size.height * 0.20f)
                    close()
                }
                drawPath(inkSlash, rewindColors.value.ink)
            }
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 700.dp
            // The username is the hero of the slide: it must stay larger than the
            // "THIS IS YOUR" line even for long names.
            val nameSize = when {
                displayName.length > 18 -> if (compact) 36.sp else 42.sp
                displayName.length > 12 -> if (compact) 42.sp else 49.sp
                else -> if (compact) 48.sp else 56.sp
            }
            Column(modifier = Modifier.fillMaxSize()) {
                RewindReveal(active, 50, direction = RewindRevealDirection.Left, distance = 18.dp) {
                    RewindKicker(stringResource(R.string.rw_intro_kicker, data.periodLabel), rewindColors.value.lime)
                }
                Spacer(Modifier.height(if (compact) 14.dp else 18.dp))
                RewindReveal(active, 220, direction = RewindRevealDirection.Left, distance = 30.dp) {
                    Text(
                        text = "$displayName,",
                        color = rewindColors.value.cream,
                        fontSize = nameSize,
                        lineHeight = nameSize,
                        letterSpacing = (-1.8).sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.90f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                RewindReveal(active, 430, direction = RewindRevealDirection.Left, distance = 36.dp) {
                    Text(
                        text = stringResource(R.string.rw_intro_this_is_your),
                        color = rewindColors.value.cream,
                        fontSize = if (compact) 37.sp else 44.sp,
                        lineHeight = if (compact) 36.sp else 42.sp,
                        letterSpacing = (-2.1).sp,
                        fontWeight = FontWeight.Black
                    )
                }
                RewindReveal(active, 650, direction = RewindRevealDirection.Right, distance = 46.dp) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = data.periodLabel,
                            color = rewindColors.value.cream,
                            fontSize = if (compact) 78.sp else 94.sp,
                            lineHeight = if (compact) 70.sp else 84.sp,
                            letterSpacing = (-6.2).sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.graphicsLayer {
                                scaleX = 0.88f
                            }
                        )
                        Text(
                            text = ".",
                            color = rewindColors.value.pink,
                            fontSize = if (compact) 66.sp else 78.sp,
                            lineHeight = if (compact) 64.sp else 76.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                RewindReveal(active, 850, direction = RewindRevealDirection.Left, distance = 32.dp) {
                    Text(
                        text = stringResource(R.string.rw_intro_reword),
                        color = rewindColors.value.cream,
                        fontSize = if (compact) 48.sp else 57.sp,
                        lineHeight = if (compact) 45.sp else 53.sp,
                        letterSpacing = (-3.0).sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(if (compact) 16.dp else 24.dp))
                RewindReveal(active, 1_050) {
                    RewindListeningWave(
                        active = active,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (compact) 130.dp else 164.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                RewindReveal(active, 1_900, direction = RewindRevealDirection.Up) {
                    Text(
                        text = stringResource(
                            when (data.period) {
                                is RewindPeriod.Year -> R.string.rw_intro_tagline
                                is RewindPeriod.Month -> R.string.rw_intro_tagline_month
                                is RewindPeriod.Global -> R.string.rw_intro_tagline_global
                            }
                        ),
                        color = rewindColors.value.cream,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(14.dp))
                RewindReveal(active, 2_180, scaleFrom = 0.94f) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .background(rewindColors.value.lime, RoundedCornerShape(100.dp))
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = null,
                                tint = onLime,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.rw_intro_start),
                                color = onLime,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.9.sp
                            )
                        }
                        Text(
                            text = if (revealComplete) stringResource(R.string.rw_intro_tap_to_begin) else stringResource(R.string.rw_intro_opening),
                            color = rewindColors.value.cream.copy(alpha = 0.52f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.0.sp,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated listening signature: the voice search [waveform wave][app.n_zik.android.components.VoiceSearchOverlay]
 * ported to the intro slide. Same triple-sine shape, tapering envelope and double stroke (core
 * line + soft glow), drawn as a black/white pair: the white wave travels one way while the
 * black one travels in the opposite direction (time coefficient sign flipped). Unlike the
 * voice search wave it never settles to a flat idle line: both waves run at full amplitude as
 * soon as they spawn, and the tick loop pauses while the slide is off screen so nothing
 * animates in the background.
 */
@Composable
private fun RewindListeningWave(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val white = rewindColors.value.cream
    val black = rewindColors.value.ink
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        // Frame-driven invalidation: advance the phase once per rendered frame, scaled by the
        // real frame delta (a fixed 16 ms tick was off-vsync on 120 Hz displays), and wrap on
        // the wave's common period π so the Float never grows unbounded
        // (spec GH-275, patch "hand-rolled 16 ms tick loop").
        var lastFrameNs = withFrameNanos { it }
        while (true) {
            val frameNs = withFrameNanos { it }
            val deltaNs = (frameNs - lastFrameNs).coerceAtLeast(0L)
            lastFrameNs = frameNs
            time += (deltaNs / 16_000_000.0 * 0.08).toFloat()
            time %= PI.toFloat()
        }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val numPoints = 200
        val sliceWidth = width / numPoints
        // The voice search "speaking" amplitude, applied from the first frame.
        val amplitudeMultiplier = 1.5f

        fun wavePath(direction: Float): Path {
            val path = Path()
            for (i in 0..numPoints) {
                val x = sliceWidth * i
                val normalizedI = i.toFloat() / numPoints

                // Flipping the sign of the time coefficient makes the wave travel in
                // the opposite direction.
                val t = time * direction
                val wave1 = sin(t * 4.0 + normalizedI * 14.0).toFloat() * 0.5f
                val wave2 = sin(t * 6.0 + normalizedI * 10.0).toFloat() * 0.35f
                val wave3 = sin(t * 2.0 + normalizedI * 24.0).toFloat() * 0.2f
                val envelope = (1f - abs(normalizedI - 0.5f) * 2f).coerceIn(0.2f, 1f)

                val amplitude = (wave1 + wave2 + wave3) * envelope * (height * 0.5f) * amplitudeMultiplier
                val y = centerY + amplitude

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            return path
        }

        val blackPath = wavePath(-1f)
        drawPath(
            path = blackPath,
            color = black,
            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = blackPath,
            color = black.copy(alpha = 0.3f),
            style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val whitePath = wavePath(1f)
        drawPath(
            path = whitePath,
            color = white,
            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = whitePath,
            color = white.copy(alpha = 0.3f),
            style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
