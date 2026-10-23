package app.it.fast4x.rimusic.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.ui.graphics.StrokeCap

@Composable
fun SeekBarWaved(
    position: () -> Float,
    onSeek: (updated: Float) -> Unit,
    modifier: Modifier = Modifier,
    onSeekStarted: (updated: Float) -> Unit = {},
    onSeekFinished: () -> Unit = {},
    color: Color,
    backgroundColor: Color = Color.Transparent,
    range: ClosedRange<Float> = 0f..100f,
    isActive: Boolean = true,
    scrubberRadius: Dp = 6.dp,
    shape: Shape = RectangleShape,
) {
    val minimumValue = range.start
    val maximumValue = range.endInclusive
    val isDragging = remember {
        MutableTransitionState(false)
    }

    val transition = rememberTransition(transitionState = isDragging, label = null)

    val currentAmplitude by transition.animateDp(label = "") { if (it || !isActive) 0.dp else 2.dp }
    val currentScrubberHeight by transition.animateDp(label = "") {
        //if (it) 10.dp else 5.dp
        if (it) 20.dp else 15.dp
    }

    Box(modifier = modifier
        .pointerInput(minimumValue, maximumValue) {
            if (maximumValue < minimumValue) return@pointerInput

            detectDrags(isDragging, maximumValue, minimumValue, onSeek, onSeekFinished)
        }
        .pointerInput(minimumValue, maximumValue) {
            detectTaps(maximumValue, minimumValue, onSeekStarted, onSeekFinished)
        }
        .padding(horizontal = scrubberRadius)
        .drawWithContent {
            drawContent()
            drawScrubber(range, position(), color, currentScrubberHeight)
        }
    ) {
        SeekBarContent(
            backgroundColor,
            amplitude = { currentAmplitude },
            position(),
            minimumValue,
            maximumValue,
            shape,
            color
        )
    }
}

private suspend fun PointerInputScope.detectDrags(
    isDragging: MutableTransitionState<Boolean>,
    maximumValue: Float,
    minimumValue: Float,
    onSeek: (delta: Float) -> Unit,
    onSeekFinished: () -> Unit
) {
    var acc = 0f

    try {
        detectHorizontalDragGestures(onDragStart = {
            isDragging.targetState = true
        }, onHorizontalDrag = { _, delta ->
            try {
                acc += delta / size.width * (maximumValue - minimumValue)

                if (acc !in -1f..1f) {
                    onSeek(acc)
                    acc -= acc
                }
            } catch (e: IllegalStateException) {
                // View may have been detached during drag
                isDragging.targetState = false
                acc = 0f
            }
        }, onDragEnd = {
            isDragging.targetState = false
            acc = 0f
            try {
                onSeekFinished()
            } catch (e: IllegalStateException) {
                // View may have been detached
            }
        }, onDragCancel = {
            isDragging.targetState = false
            acc = 0f
            try {
                onSeekFinished()
            } catch (e: IllegalStateException) {
                // View may have been detached
            }
        })
    } catch (e: IllegalStateException) {
        // View may have been detached before gesture detection
    }
}

private suspend fun PointerInputScope.detectTaps(
    maximumValue: Float,
    minimumValue: Float,
    onSeekStarted: (updated: Float) -> Unit,
    onSeekFinished: () -> Unit
) {
    if (maximumValue < minimumValue) return

    detectTapGestures(onPress = { offset ->
        val updatedOffset = (offset.x / size.width * (maximumValue - minimumValue) + minimumValue)
        onSeekStarted(updatedOffset)
    }, onTap = {
        onSeekFinished()
    })
}

private fun ContentDrawScope.drawScrubber(
    range: ClosedRange<Float>, position: Float, color: Color, height: Dp
) {
    val minimumValue = range.start
    val maximumValue = range.endInclusive
    val scrubberPosition = if (maximumValue < minimumValue) {
        0f
    } else {
        (position - minimumValue) / (maximumValue - minimumValue) * size.width
    }

    drawRoundRect(
        color, topLeft = Offset(scrubberPosition - 5f, (size.height - height.toPx()) / 2),
        size = Size(10f, height.toPx()),
        cornerRadius = CornerRadius(5f)
    )
}


@Composable
private fun SeekBarContent(
    backgroundColor: Color,
    amplitude: () -> Dp,
    position: Float,
    minimumValue: Float,
    maximumValue: Float,
    shape: Shape,
    color: Color
) {
    val fraction = (position - minimumValue) / (maximumValue - minimumValue)
    val progress by rememberInfiniteTransition().animateFloat(
        0f,
        1f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = LinearEasing)
        ), label = ""
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)) {
        Spacer(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(1f - fraction)
                .background(color = backgroundColor, shape = shape)
                .align(Alignment.CenterEnd)
        )

        Canvas(
            Modifier
                .fillMaxWidth(fraction)
                .height(amplitude())
                .align(Alignment.CenterStart)) {
            clipRect(
                left = -50f, 
                right = size.width, 
                top = -50f, 
                bottom = size.height + 50f
            ) {
                drawPath(
                    wavePath(size.copy(height = size.height * 2), progress),
                    color,
                    style = Stroke(
                        width = 15f,
                        cap = StrokeCap.Round
                    )
                )
            }
        }
    }
}

// gh-606 M11: sampling step for wavePath()'s Path reconstruction.
// Was hardcoded to 1px (a sin()+lineTo() call per horizontal pixel, every

// animated frame while the Wavy seek bar is visible); bumping it to 3px cuts
// that per-frame CPU cost by ~3x with no perceptible visual change (the wave

// is a smooth low-frequency sine, not fine detail).
internal const val WAVE_PATH_STEP_PX = 3f

/**
 * Pure (Compose-Path-free) sampling core of [wavePath], extracted so it can be unit-tested
 * on the plain JVM: constructing an actual [Path] requires a real android.graphics.Path,
 * which isn't available in a non-Robolectric unit test. Mirrors the exact x-coordinates
 * [wavePath] used to loop over inline: starts at 0f, steps by [step] while strictly less
 * than [width].
 */
internal fun waveSampleXs(width: Float, step: Float = WAVE_PATH_STEP_PX): List<Float> {
    val xs = mutableListOf<Float>()
    var currentX = 0f
    while (currentX < width) {
        xs += currentX
        currentX += step
    }
    return xs
}

private fun wavePath(size: Size, progress: Float): Path {
    fun yFromX(x: Float) = (sin(x / 15f + progress * 2 * PI.toFloat()) + 1) * size.height / 2
    return Path().apply {
        moveTo(0f, yFromX(0f))
        for (currentX in waveSampleXs(size.width)) {
            lineTo(currentX, yFromX(currentX))
        }
    }
}
private fun wavePath2(size: Size, progress: Float): Path {
    fun yFromX(x: Float) = (x / 150f + progress * 2 * PI.toFloat() + 1) * size.height / 2
    return Path().apply {
        moveTo(0f, yFromX(0f))
        var currentX = 0f
        while (currentX < size.width) {
            lineTo(currentX, yFromX(currentX))
            //lineTo(currentX, yFromX(currentX * PI.toFloat()))
            //lineTo(currentX, yFromX(currentX * PI.toFloat() * 2))
            currentX += 1
        }
    }
}



