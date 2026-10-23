package app.n_zik.android.components.ui.sliders

import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.material3.ExperimentalMaterial3Api
import app.n_zik.android.colorPalette
import app.n_zik.android.exactUiRoundnessShape
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt
import androidx.compose.animation.core.Spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Slider

/** THE unique Slider component of the application: auto ticks from the step size, native magnetism to the nearest step, value always normalized/quantized to the nearest tick. @param onSlideComplete emitted when the finger is lifted @param stepSize step size (e.g. 0.1f, 1f, 5f), default 0.1f @param setState emitted during dragging */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Slider(
    isEnabled: Boolean = true,
    state: Float,
    setState: (Float) -> Unit,
    onSlideComplete: () -> Unit = {},
    range: ClosedFloatingPointRange<Float> = 0f..100f,
    stepSize: Float = 0f,
    defaultValue: Float? = null,
    drawValuePoints: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed

    val thumbWidth by animateDpAsState(
        targetValue = if (isInteracting) 12.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val thumbHeight by animateDpAsState(
        targetValue = if (isInteracting) 24.dp else 32.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val stepCount = if (stepSize > 0f) {
        ((range.endInclusive - range.start) / stepSize).roundToInt() - 1
    } else 0
    val actualSteps = if (stepCount > 0) stepCount else 0

    val rangeSize = range.endInclusive - range.start
    val magneticThreshold = if (actualSteps > 0) {
        minOf(rangeSize * 0.02f, stepSize * 0.15f)
    } else 0f

    val animatedValue by animateFloatAsState(
        targetValue = state,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    fun normalize(value: Float): Float {
        if (stepSize <= 0f) return value.coerceIn(range.start, range.endInclusive)
        val fraction = (value - range.start) / stepSize
        return (range.start + round(fraction) * stepSize)
            .coerceIn(range.start, range.endInclusive)
    }

    Slider(
        enabled = isEnabled,
        state = rememberControlledSliderState(animatedValue, range),
        onValueChange = { newValue ->
            val normalized = normalize(newValue)
            var finalValue = if (abs(newValue - normalized) <= magneticThreshold) normalized else newValue
            
            if (defaultValue != null) {
                val defaultThreshold = rangeSize * 0.02f
                if (abs(newValue - defaultValue) <= defaultThreshold) {
                    finalValue = defaultValue
                }
            }
            if (isInteracting) {
                setState(finalValue)
            }
        },
        onValueChangeFinished = {
            onSlideComplete()
        },
        modifier = modifier,
        interactionSource = interactionSource,
        thumb = {
            Box(
                modifier = Modifier
                    .size(width = thumbWidth, height = thumbHeight)
                    .background(
                        color = if (isEnabled) colorPalette().onAccent else colorPalette().text.copy(alpha = 0.4f),
                        shape = exactUiRoundnessShape()
                    )
            )
        },
        track = {
            Box(contentAlignment = Alignment.CenterStart) {
                val fraction = if (range.endInclusive > range.start) {
                    ((state - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                } else 0f
                
                // Track Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(exactUiRoundnessShape())
                        .background(if (isEnabled) colorPalette().text.copy(alpha = 0.2f) else colorPalette().text.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Active Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(24.dp)
                            .clip(exactUiRoundnessShape())
                            .background(if (isEnabled) colorPalette().accent else colorPalette().text.copy(alpha = 0.4f))
                    )
                }
                
                if (actualSteps > 0) {
                    val accentColor = colorPalette().accent
                    val passedColor = colorPalette().text.copy(alpha = 0.5f)
                    
                    Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                        val currentFraction = if (range.endInclusive > range.start) {
                            (state - range.start) / (range.endInclusive - range.start)
                        } else 0f
                        
                        val trackWidth = size.width
                        val numIntervals = actualSteps + 1
                        
                        val trackStart = 0f
                        val activeWidth = trackWidth
                        
                        var drawStep = 1
                        if (numIntervals > 10) {
                            var bestDivisor = -1
                            for (step in 2..numIntervals) {
                                val count = numIntervals / step
                                if (numIntervals % step == 0 && count in 4..10) {
                                    bestDivisor = step
                                    break
                                }
                            }
                            drawStep = if (bestDivisor != -1) bestDivisor else (numIntervals / 8.0).roundToInt().coerceAtLeast(1)
                        }
                        
                        for (i in 0..numIntervals) {
                            if (i % drawStep == 0 || i == numIntervals) {
                                val fraction = i.toFloat() / numIntervals
                                val x = trackStart + (fraction * activeWidth)
                                val isPassed = fraction <= currentFraction
                                drawCircle(
                                    color = if (isPassed) passedColor else accentColor,
                                    radius = 2.dp.toPx(),
                                    center = Offset(x = x, y = size.height / 2)
                                )
                            }
                        }
                    }
                } else if (drawValuePoints) {
                    val accentColor = colorPalette().accent
                    val passedColor = colorPalette().text.copy(alpha = 0.5f)
                    
                    Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                        val currentFraction = if (range.endInclusive > range.start) {
                            (state - range.start) / (range.endInclusive - range.start)
                        } else 0f
                        
                        val trackWidth = size.width
                        val trackStart = 0f
                        val activeWidth = trackWidth
                        
                        val pointsToDraw = mutableSetOf<Float>()
                        if (defaultValue != null) {
                            val defaultFraction = ((defaultValue - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                            pointsToDraw.add(defaultFraction)
                        }
                        
                        pointsToDraw.forEach { fraction ->
                            val x = trackStart + (fraction * activeWidth)
                            val isPassed = fraction <= currentFraction
                            drawCircle(
                                color = if (isPassed) passedColor else accentColor,
                                radius = 2.dp.toPx(),
                                center = Offset(x = x, y = size.height / 2)
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
internal fun rememberControlledSliderState(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
): SliderState {
    val sliderState = remember(range) {
        SliderState(value = value, steps = 0, trackRange = range)
    }
    // The parent owns the animated value; keep Material's state in sync on recomposition.
    sliderState.value = value
    return sliderState
}
