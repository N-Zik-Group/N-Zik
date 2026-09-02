package app.n_zik.android.extensions.audiobar.views

import android.media.audiofx.Visualizer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random
import androidx.compose.ui.platform.LocalContext
import androidx.annotation.FloatRange
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.BasicText
import app.n_zik.android.R
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.hasPermission
import app.it.fast4x.rimusic.utils.isCompositionLaunched
import app.it.fast4x.rimusic.utils.semiBold
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import utils.VisualizerHelper
import timber.log.Timber
import app.n_zik.android.LocalPlayerServiceBinder
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.media3.common.Player
import androidx.compose.runtime.DisposableEffect

private const val waveWidthPercentOfSpaceAvailable = 0.5f

@Composable
fun SeekBarVisualizer(
    audioSessionIdProvider: () -> Int? = { null },
    isPlaying: Boolean = false,
    progressPercentage: () -> ProgressPercentage,
    playedColor: Color,
    notPlayedColor: Color,
    waveInteraction: WaveInteraction,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val maxWidth = this.maxWidth
        val updatedWaveInteraction by rememberUpdatedState(waveInteraction)
        val context = LocalContext.current
        val permission = Manifest.permission.RECORD_AUDIO
        
        var audioSessionId by remember { mutableStateOf(audioSessionIdProvider()) }
        
        val binder = LocalPlayerServiceBinder.current
        var localIsPlaying by remember(binder, isPlaying) { mutableStateOf(binder?.player?.isPlaying ?: isPlaying) }

        DisposableEffect(binder) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                    localIsPlaying = isPlayingChanged
                }
            }
            binder?.player?.addListener(listener)
            // Catch state change if media started instantly
            binder?.player?.isPlaying?.let { localIsPlaying = it }
            onDispose {
                binder?.player?.removeListener(listener)
            }
        }
        
        // Also respect parameter updates just in case
        LaunchedEffect(isPlaying) {
            if (isPlaying != localIsPlaying) {
                localIsPlaying = isPlaying
            }
        }

        LaunchedEffect(localIsPlaying) {
            while (localIsPlaying) {
                val newId = audioSessionIdProvider()
                if (newId != null && newId != audioSessionId) {
                    audioSessionId = newId
                }
                delay(500)
            }
        }
        
        var hasPermission by remember(isCompositionLaunched()) {
            mutableStateOf(context.applicationContext.hasPermission(permission))
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { hasPermission = it }
        )

        val numberOfWaves = remember(maxWidth) {
            (maxWidth / 3f).value.roundToInt() //5f default
        }
        val waveWidth = remember(maxWidth) {
            (maxWidth / numberOfWaves.toFloat()) * waveWidthPercentOfSpaceAvailable
        }

        var liveWaveform by remember {
            mutableStateOf(ByteArray(numberOfWaves) { 0.toByte() })
        }
        LaunchedEffect(numberOfWaves) {
            if (liveWaveform.size != numberOfWaves) {
                liveWaveform = ByteArray(numberOfWaves) { 0.toByte() }
            }
        }

        if (!hasPermission) {
            LaunchedEffect(Unit) { launcher.launch(permission) }
        }

        if (hasPermission && audioSessionId != null && localIsPlaying) {
            @Suppress("SENSELESS_COMPARISON")
            val currentSessionId = audioSessionId ?: return@BoxWithConstraints
            LaunchedEffect(currentSessionId, localIsPlaying) {
                val helper = VisualizerHelper(currentSessionId)
                
                try {
                    while (true) {
                        val fft = helper.getFft()
                        
                        if (fft.isNotEmpty() && liveWaveform.isNotEmpty()) {
                            val newWaveform = liveWaveform.clone()
                            
                            val actualSamplingRate = 44100
                            val binsToUse = kotlin.math.max(1, (2500 * fft.size) / actualSamplingRate)
                            
                            val binsPerWave = kotlin.math.max(1, binsToUse / liveWaveform.size)
                            
                            for (index in liveWaveform.indices) {
                                val startBin = index * binsPerWave
                                val endBin = startBin + binsPerWave
                                
                                var sumMagnitude = 0f
                                var count = 0
                                
                                for (bin in startBin until endBin) {
                                    val fftIndex = bin * 2 + 2 // +2 to skip DC and Nyquist
                                    if (fftIndex + 1 < fft.size) {
                                        val real = fft[fftIndex].toInt()
                                        val imag = fft[fftIndex + 1].toInt()
                                        val magnitude = sqrt((real * real + imag * imag).toFloat())
                                        sumMagnitude += magnitude
                                        count++
                                    }
                                }
                                
                                val avgMagnitude = if (count > 0) sumMagnitude / count else 0f
                                
                                val weight = 1.0f + (index.toFloat() / liveWaveform.size) * 2.0f
                                val blockAmplitude = (avgMagnitude * 3.5f * weight).toInt().coerceIn(0, 127)
                                newWaveform[index] = blockAmplitude.toByte()
                            }
                            liveWaveform = newWaveform
                        }
                        
                        delay(40) // Poll at 25fps
                    }
                } catch (e: Exception) {
                    Timber.tag("SeekBarVisualizer").e(e, "Failed to capture visualizer waveform")
                }
            }
        } else if (!localIsPlaying) {
            // Decay to flat when paused
            LaunchedEffect(localIsPlaying) {
                while(true) {
                    var allZero = true
                    val newWaveform = liveWaveform.clone()
                    for (index in newWaveform.indices) {
                        val oldVal = newWaveform[index].toInt() and 0xFF
                        if (oldVal > 0) {
                            newWaveform[index] = kotlin.math.max(0, oldVal - 8).toByte()
                            allZero = false
                        }
                    }
                    if (allZero) break
                    liveWaveform = newWaveform
                    delay(50)
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        updatedWaveInteraction.onInteraction(
                            ProgressPercentage.of(current = offset.x.toDp(), target = maxWidth),
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change: PointerInputChange, dragAmount: Float ->
                        // Do not trigger on minuscule movements
                        if (dragAmount.absoluteValue < 1f) return@detectHorizontalDragGestures
                        updatedWaveInteraction.onInteraction(
                            ProgressPercentage.of(
                                current = change.position.x.toDp(),
                                target = maxWidth
                            ),
                        )
                    }
                },
        ) {
            if (!hasPermission) {
                BasicText(
                    text = stringResource(R.string.require_mic_permission),
                    modifier = Modifier.fillMaxWidth(),
                    style = typography().xs.semiBold.copy(color = notPlayedColor, textAlign = TextAlign.Center)
                )
            } else {
                repeat(numberOfWaves) { waveIndex ->
                    AudioWavePill(
                        waveform = liveWaveform,
                        progressPercentage = progressPercentage,
                        numberOfWaves = numberOfWaves,
                        waveIndex = waveIndex,
                        playedColor = playedColor,
                        notPlayedColor = notPlayedColor,
                        modifier = Modifier.width(waveWidth),
                    )
                }
            }
        }



        if (!localIsPlaying) {
            val thumbWidth = 2.dp
            val offsetDp = (maxWidth * progressPercentage().value) - (thumbWidth / 2)
            val maxOffset = if (maxWidth > thumbWidth) maxWidth - thumbWidth else 0.dp
            val safeOffset = offsetDp.coerceIn(0.dp, maxOffset)
            
            Surface(
                shape = CircleShape,
                color = playedColor,
                modifier = Modifier
                    .offset(x = safeOffset)
                    .width(thumbWidth)
                    .fillMaxHeight()
            ) {}
        }
    }
}

private const val minWaveHeightFraction = 0.05f
private const val maxWaveHeightFractionForSideWaves = 0.1f
private const val maxWaveHeightFraction = 0.85f

@Composable
private fun AudioWavePill(
    waveform: ByteArray?,
    progressPercentage: () -> ProgressPercentage,
    numberOfWaves: Int,
    waveIndex: Int,
    playedColor: Color,
    notPlayedColor: Color,
    modifier: Modifier = Modifier,
) {
    val height = remember(waveIndex, numberOfWaves, waveform) {
        if (waveform != null && waveform.isNotEmpty()) {
            val bucketIndex = ((waveIndex.toFloat() / numberOfWaves.toFloat()) * waveform.size).toInt().coerceIn(0, waveform.size - 1)
            val amplitude = waveform[bucketIndex].toInt() and 0xFF
            val fraction = amplitude.toFloat() / 128f
            lerp(minWaveHeightFraction, maxWaveHeightFraction, fraction).coerceIn(minWaveHeightFraction, maxWaveHeightFraction)
        } else {
            val wavePosition = waveIndex + 1
            val centerPoint = numberOfWaves / 2
            val distanceFromCenterPoint = abs(centerPoint - wavePosition)
            val percentageToCenterPoint = ((centerPoint - distanceFromCenterPoint).toFloat() / centerPoint)
            val maxHeightFraction = lerp(
                maxWaveHeightFractionForSideWaves,
                maxWaveHeightFraction,
                percentageToCenterPoint,
            )
            val validMaxHeightFraction = if (maxHeightFraction.isNaN()) 0.1f else maxHeightFraction
            if (validMaxHeightFraction <= minWaveHeightFraction) {
                validMaxHeightFraction
            } else {
                Random.nextDouble(minWaveHeightFraction.toDouble(), validMaxHeightFraction.toDouble()).toFloat()
            }
        }
    }
    
    val animatedHeight = remember { Animatable(minWaveHeightFraction) }
    
    LaunchedEffect(height) {
        if (height >= animatedHeight.value) {
            animatedHeight.snapTo(height)
        } else {
            animatedHeight.animateTo(
                targetValue = height,
                animationSpec = tween(
                    durationMillis = 90, 
                    easing = LinearEasing
                )
            )
        }
    }
    
    val currentProgressPercentage by rememberUpdatedState(progressPercentage)
    
    val hasPlayedThisWave by remember {
        derivedStateOf { currentProgressPercentage().value * numberOfWaves > waveIndex }
    }
    Surface(
        shape = CircleShape,
        color = if (hasPlayedThisWave) playedColor else notPlayedColor,
        modifier = modifier.fillMaxHeight(fraction = animatedHeight.value),
    ) {}
}

@JvmInline
value class ProgressPercentage(
    @FloatRange(from = 0.0, to = 1.0, fromInclusive = true, toInclusive = true)
    val value: Float,
) {
    init {
        require(value in 0.0f..1.0f) {
            "Progress percentage must be within 0.0f inclusive to 1.0f inclusive. Value: $value"
        }
    }

    val isDone: Boolean
        get() = value == 1f

    companion object {
        fun safeValue(float: Float): ProgressPercentage {
            if (float.isNaN()) return ProgressPercentage(0f)
            return ProgressPercentage(float.coerceIn(0f, 1f))
        }

        fun of(
            current: Dp,
            target: Dp,
        ): ProgressPercentage {
            return ProgressPercentage(
                (current / target).coerceIn(0f, 1f),
            )
        }
    }
}

fun interface WaveInteraction {
    fun onInteraction(horizontalProgressPercentage: ProgressPercentage)
}
