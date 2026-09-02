package app.n_zik.android.extensions.audiobar.views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.it.fast4x.rimusic.models.ui.UiMedia
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import kotlinx.coroutines.isActive
import kotlin.math.abs
import java.util.Random

@Composable
fun SeekBarStaticAudioWaves(
    modifier: Modifier = Modifier,
    uiMedia: UiMedia?,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    onPositionChange: (Long) -> Unit,
    onPositionChangeFinished: (() -> Unit)? = null,
    audioSessionId: () -> Int = { -1 },
    unplayedColor: Color = Color.Unspecified
) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val caches = remember { 
        listOfNotNull(
            binder?.downloadCache ?: MyDownloadHelper.getDownloadCache(context),
            binder?.cache
        )
    }
    
    var amplitudes by remember { mutableStateOf<List<Int>?>(null) }
    var isExtracting by remember { mutableStateOf(false) }

    val currentSongStateDownload by binder?.service?.currentSongStateDownload?.collectAsStateWithLifecycle() 
        ?: mutableStateOf(androidx.media3.exoplayer.offline.Download.STATE_STOPPED)

    val refreshSignal by WaveformExtractor.refreshSignal.collectAsStateWithLifecycle(initialValue = 0L)

    LaunchedEffect(uiMedia?.id, currentSongStateDownload, refreshSignal) {
        if (uiMedia != null) {
            amplitudes = null
            isExtracting = true

            var retryRound = 0
            while (amplitudes == null && isActive) {
                retryRound++
                var extracted: List<Int>? = null
                for (i in 0..5) {
                    extracted = WaveformExtractor.getOrExtractWaveform(context, uiMedia.id, caches)
                    if (extracted != null) break
                    kotlinx.coroutines.delay(500)
                }

                amplitudes = extracted
                isExtracting = false

                if (extracted == null) {
                    kotlinx.coroutines.delay(5000)
                }
            }
        } else {
            amplitudes = null
            isExtracting = false
        }
    }

    val playedColor = colorPalette().accent
    val defaultUnplayedColor = if (unplayedColor != Color.Unspecified) unplayedColor else colorPalette().textSecondary.copy(alpha = 0.3f)

    val displayAmplitudes = amplitudes ?: remember(uiMedia?.id) {
        val seed = uiMedia?.id?.hashCode()?.toLong() ?: System.currentTimeMillis()
        val random = Random(seed)
        
        List(150) { i ->
            val wavePosition = i + 1
            val centerPoint = 150 / 2
            val distanceFromCenterPoint = kotlin.math.abs(centerPoint - wavePosition)
            val percentageToCenterPoint = ((centerPoint - distanceFromCenterPoint).toFloat() / centerPoint)
            
            // Interpolate max height from 10% at edges to 85% at center
            val maxHeightFraction = lerp(
                0.1f,
                0.85f,
                percentageToCenterPoint
            )
            
            val validMaxHeightFraction = if (maxHeightFraction.isNaN()) 0.1f else maxHeightFraction
            
            val amplitude = if (validMaxHeightFraction <= 0.05f) {
                0.05f
            } else {
                0.05f + random.nextFloat() * (validMaxHeightFraction - 0.05f)
            }
            
            // Map fraction to 0-100 amplitude
            (amplitude * 100f).toInt().coerceIn(5, 100)
        }
    }

    if (displayAmplitudes.isNotEmpty()) {
        // Draw static waves
        val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
        
        val waveAmplitudeMultiplier by animateFloatAsState(
            targetValue = if (isPlaying) 1f else 0f,
            animationSpec = tween(durationMillis = 300)
        )
        
        val updatedOnPositionChange by rememberUpdatedState(onPositionChange)
        val updatedOnPositionChangeFinished by rememberUpdatedState(onPositionChangeFinished)

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(duration) {
                    detectTapGestures(
                        onPress = { offset ->
                            val newPosition = (offset.x / size.width) * duration
                            updatedOnPositionChange(newPosition.toLong())
                        },
                        onTap = { offset ->
                            val newPosition = (offset.x / size.width) * duration
                            updatedOnPositionChange(newPosition.toLong())
                            updatedOnPositionChangeFinished?.invoke()
                        }
                    )
                }
                .pointerInput(duration) {
                    var dragPosition: Float = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragPosition = offset.x
                            val newPosition = (dragPosition / size.width) * duration
                            updatedOnPositionChange(newPosition.toLong())
                        },
                        onDragEnd = {
                            updatedOnPositionChangeFinished?.invoke()
                        },
                        onHorizontalDrag = { change: PointerInputChange, dragAmount: Float ->
                            change.consume()
                            dragPosition += dragAmount
                            dragPosition = dragPosition.coerceIn(0f, size.width.toFloat())
                            val newPosition = (dragPosition / size.width) * duration
                            updatedOnPositionChange(newPosition.toLong())
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas
                
                val maxAmp = displayAmplitudes.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
                
                val barCount = 150 // We draw 150 bars across the width
                val barWidth = canvasWidth / (barCount * 1.5f) // Leave space between bars
                val spacing = (canvasWidth - (barWidth * barCount)) / (barCount - 1).coerceAtLeast(1)
                
                // Group the amplitudes into 150 chunks
                val chunkSize = (displayAmplitudes.size.toFloat() / barCount).coerceAtLeast(1f)
                
                for (i in 0 until barCount) {
                    val startIdx = (i * chunkSize).toInt().coerceIn(0, displayAmplitudes.size - 1)
                    val endIdx = ((i + 1) * chunkSize).toInt().coerceIn(0, displayAmplitudes.size)
                    
                    var chunkMax = 0
                    if (startIdx < endIdx) {
                        for (j in startIdx until endIdx) {
                            if (displayAmplitudes[j] > chunkMax) chunkMax = displayAmplitudes[j]
                        }
                    } else {
                        chunkMax = displayAmplitudes[startIdx]
                    }
                    
                    // Normalize chunk amplitude between 0.1 and 1.0
                    val normalizedAmp = (chunkMax / maxAmp).coerceIn(0.1f, 1.0f)
                    val currentAmp = 0.05f + (normalizedAmp - 0.05f) * waveAmplitudeMultiplier
                    val barHeight = canvasHeight * currentAmp
                    
                    val x = i * (barWidth + spacing)
                    val y = (canvasHeight - barHeight) / 2f
                    
                    val isPlayed = (i.toFloat() / barCount) <= progress
                    val barColor = if (isPlayed) playedColor else defaultUnplayedColor
                    
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                    )
                }
                
                if (!isPlaying) {
                    val thumbWidthPx = 2.dp.toPx()
                    val thumbX = (canvasWidth * progress) - (thumbWidthPx / 2f)
                    val maxThumbX = (canvasWidth - thumbWidthPx).coerceAtLeast(0f)
                    val safeThumbX = thumbX.coerceIn(0f, maxThumbX)
                    drawRoundRect(
                        color = playedColor,
                        topLeft = Offset(safeThumbX, 0f),
                        size = Size(thumbWidthPx, canvasHeight),
                        cornerRadius = CornerRadius(thumbWidthPx / 2, thumbWidthPx / 2)
                    )
                }
            }
        }
    }
}
