package app.n_zik.android.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import kotlinx.coroutines.delay
import kotlin.math.sin

@Composable
fun VoiceSearchOverlay(
    isVisible: Boolean,
    recognizedText: String,
    isSpeaking: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                FmWaveformVisualizer(
                    isSpeaking = isSpeaking && errorMessage == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )

                Spacer(modifier = Modifier.height(40.dp))

                if (errorMessage != null) {
                    BasicText(
                        text = errorMessage,
                        style = TextStyle(
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            fontSize = 20.sp
                        ),
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                                .clickable { onCancel() }
                                .padding(horizontal = 32.dp, vertical = 12.dp)
                        ) {
                            BasicText(
                                text = stringResource(R.string.cancel),
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(colorPalette().accent)
                                .clickable { onRetry() }
                                .padding(horizontal = 32.dp, vertical = 12.dp)
                        ) {
                            BasicText(
                                text = stringResource(R.string.voice_search_retry),
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            )
                        }
                    }
                } else {
                    BasicText(
                        text = if (recognizedText.isEmpty()) stringResource(R.string.voice_search_listening) else recognizedText,
                        style = TextStyle(
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            fontSize = 26.sp
                        ),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    BasicText(
                        text = stringResource(R.string.voice_search_speak_now),
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { onCancel() }
                            .padding(horizontal = 32.dp, vertical = 12.dp)
                    ) {
                        BasicText(
                            text = stringResource(R.string.cancel),
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FmWaveformVisualizer(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor = colorPalette().accent
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            time += 0.08f
            delay(16)
        }
    }

    val amplitudeMultiplier by animateFloatAsState(
        targetValue = if (isSpeaking) 1.5f else 0.05f,
        animationSpec = tween(150),
        label = "amplitude"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val numPoints = 200
        val sliceWidth = width / numPoints

        val path = Path()
        var prevX = 0f
        var prevY = centerY

        for (i in 0..numPoints) {
            val x = sliceWidth * i
            val normalizedI = i.toFloat() / numPoints

            val wave1 = sin(time * 4.0 + normalizedI * 14.0).toFloat() * 0.5f
            val wave2 = sin(time * 6.0 + normalizedI * 10.0).toFloat() * 0.35f
            val wave3 = sin(time * 2.0 + normalizedI * 24.0).toFloat() * 0.2f
            val envelope = (1f - kotlin.math.abs(normalizedI - 0.5f) * 2f).coerceIn(0.2f, 1f)

            val amplitude = (wave1 + wave2 + wave3) * envelope * (height * 0.5f) * amplitudeMultiplier
            val y = centerY + amplitude

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }

            prevX = x
            prevY = y
        }

        drawPath(
            path = path,
            color = accentColor,
            style = Stroke(
                width = 4f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        drawPath(
            path = path,
            color = accentColor.copy(alpha = 0.3f),
            style = Stroke(
                width = 12f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
