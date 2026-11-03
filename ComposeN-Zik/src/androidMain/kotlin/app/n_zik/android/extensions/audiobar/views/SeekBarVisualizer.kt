package app.n_zik.android.extensions.audiobar.views
import app.n_zik.android.components.player.PlayerSeekGlassThumb
import app.n_zik.android.components.player.PlayerSeekOpticalThumb
import app.n_zik.android.components.player.playerSeekTrack
import app.n_zik.android.components.player.playerSeekVisualizerMarkerHeight

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.annotation.FloatRange
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import android.Manifest
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
import timber.log.Timber
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.extensions.audiobar.VisualizerCaptureCoordinator
import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.PermissionUsageJournal
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.media3.common.Player
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    glassThumb: PlayerSeekGlassThumb? = null,
) {
    val player = LocalPlayerServiceBinder.current?.player
    // Parameter updates must not replace the state retained by this player's listener.
    val playbackState = remember(player) { mutableStateOf(player?.isPlaying ?: isPlaying) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                playbackState.value = isPlayingChanged
            }
        }
        player?.addListener(listener)
        player?.isPlaying?.let { playbackState.value = it }
        onDispose { player?.removeListener(listener) }
    }
    LaunchedEffect(isPlaying) { playbackState.value = isPlaying }
    val localIsPlaying = playbackState.value
    BoxWithConstraints(modifier) {
        val visualProgress: () -> ProgressPercentage = {
            glassThumb?.motion?.value?.let { ProgressPercentage.safeValue(it) } ?: progressPercentage()
        }
        val maxWidth = this.maxWidth
        val trackHeight = maxHeight
        val updatedWaveInteraction by rememberUpdatedState(waveInteraction)
        val context = LocalContext.current
        val permission = Manifest.permission.RECORD_AUDIO
        
        var audioSessionId by remember { mutableStateOf(audioSessionIdProvider()) }
        
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
            onResult = {
                PermissionUsageJournal.result(context, PermissionFeature.VISUALIZER, permission, it)
                hasPermission = it
            }
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
            LaunchedEffect(Unit) {
                PermissionUsageJournal.request(context, PermissionFeature.VISUALIZER, permission)
                launcher.launch(permission)
            }
        }

        if (hasPermission && audioSessionId != null && localIsPlaying) {
            @Suppress("SENSELESS_COMPARISON")
            val currentSessionId = audioSessionId ?: return@BoxWithConstraints

            // Share the lifecycle-aware capture instead of polling FFT on the UI thread.
            val snapshot by VisualizerCaptureCoordinator.snapshotFlow(currentSessionId)
                .collectAsStateWithLifecycle()

            LaunchedEffect(snapshot) {
                val currentSnapshot = snapshot ?: return@LaunchedEffect
                val fft = currentSnapshot.fft
                try {
                    if (fft.isNotEmpty() && liveWaveform.isNotEmpty()) {
                        val newWaveform = liveWaveform.clone()

                        // Visualizer.getSamplingRate() (and VisualizerSnapshot.samplingRate) is in
                        // milliHertz (e.g. 44100000), while this formula's constant is Hz-scale.
                        val actualSamplingRate = currentSnapshot.samplingRate / 1000
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
                } catch (e: Exception) {
                    Timber.tag("SeekBarVisualizer").e(e, "Failed to decode visualizer waveform")
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

        Box(Modifier.fillMaxWidth().height(trackHeight).playerSeekTrack(glassThumb)
                    .pointerInput(maxWidth) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            var dragging = false
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                    ?: break
                                if (change.isConsumed) break
                                if (!change.pressed) {
                                    if (!dragging) updatedWaveInteraction.onInteraction(
                                        ProgressPercentage.of(current = change.position.x.toDp(), target = maxWidth))
                                    change.consume()
                                    break
                                }
                                val distance = change.position - down.position
                                if (!dragging) {
                                    // Let vertical player scrolling win before taking seek ownership.
                                    if (distance.y.absoluteValue > viewConfiguration.touchSlop &&
                                        distance.y.absoluteValue >= distance.x.absoluteValue) break
                                    if (distance.x.absoluteValue <= viewConfiguration.touchSlop) continue
                                    dragging = true
                                }
                                updatedWaveInteraction.onInteraction(
                                    ProgressPercentage.of(current = change.position.x.toDp(), target = maxWidth))
                                change.consume()
                            }
                        }
                    }
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(trackHeight),
            ) {
                if (!hasPermission) {
                    BasicText(
                        text = stringResource(R.string.visualizer_microphone_permission_message),
                        modifier = Modifier.fillMaxWidth(),
                        style = typography().xs.semiBold.copy(color = notPlayedColor, textAlign = TextAlign.Center)
                    )
                } else {
                    repeat(numberOfWaves) { waveIndex ->
                        AudioWavePill(
                            waveform = liveWaveform,
                            progressPercentage = visualProgress,
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
                val offsetDp = (maxWidth * visualProgress().value) - (thumbWidth / 2)
                val maxOffset = if (maxWidth > thumbWidth) maxWidth - thumbWidth else 0.dp
                val safeOffset = offsetDp.coerceIn(0.dp, maxOffset)

                Surface(
                    shape = CircleShape,
                    color = playedColor,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = safeOffset)
                        .width(thumbWidth)
                        .height(playerSeekVisualizerMarkerHeight(trackHeight))
                        .testTag("player.seek.visualizer.pausedCursor")
                ) {}
            }
        }
        PlayerSeekOpticalThumb(glassThumb, progressPercentage().value)
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
