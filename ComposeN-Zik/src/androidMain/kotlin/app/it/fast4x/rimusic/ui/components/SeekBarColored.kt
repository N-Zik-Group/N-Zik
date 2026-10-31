package app.it.fast4x.rimusic.ui.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import kotlin.math.roundToLong
import app.n_zik.android.components.player.PlayerSeekGlassThumb
import app.n_zik.android.components.player.PlayerSeekOpticalThumb
import app.n_zik.android.components.player.playerSeekTrack

@Composable
fun SeekBarColored(
    value: Long,
    minimumValue: Long,
    maximumValue: Long,
    onDragStart: (Long) -> Unit,
    onDrag: (Long) -> Unit,
    onDragEnd: () -> Unit,
    color: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    barHeight: Dp = 3.dp,
    scrubberColor: Color = color,
    scrubberRadius: Dp = 6.dp,
    shape: Shape = RectangleShape,
    glassThumb: PlayerSeekGlassThumb? = null,
) {
    val visualFraction = glassThumb?.motion?.value
        ?: ((value.toFloat() - minimumValue) / (maximumValue - minimumValue))
    val colorPosition = if (glassThumb == null) value
        else minimumValue + (visualFraction * (maximumValue - minimumValue)).toLong()

    val (colorPalette, typography) = LocalAppearance.current
    val isDragging = remember {
        MutableTransitionState(false)
    }

    val transition = rememberTransition(transitionState = isDragging, label = null)

    val currentBarHeight by transition.animateDp(label = "") { if (it) scrubberRadius else barHeight }
    val currentScrubberRadius by transition.animateDp(label = "") { if (it) 0.dp else scrubberRadius }

    val barColor = when {
        colorPosition >= 0 && colorPosition <= maximumValue / 5 -> colorPalette.text
        colorPosition >= maximumValue / 5 && colorPosition <= maximumValue / 4 -> colorPalette.background0
        colorPosition >= maximumValue / 4 && colorPosition <= maximumValue / 3 -> colorPalette.textDisabled
        colorPosition >= maximumValue / 3 && colorPosition <= maximumValue / 2 -> colorPalette.background2
        colorPosition >= maximumValue / 2 && colorPosition <= maximumValue -> colorPalette.accent
        else -> colorPalette.text
    }

    Box(
        modifier = modifier
            .pointerInput(minimumValue, maximumValue) {
                if (maximumValue < minimumValue) return@pointerInput

                var acc = 0f

                try {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging.targetState = true
                        },
                        onHorizontalDrag = { _, delta ->
                            try {
                                acc += delta / size.width * (maximumValue - minimumValue)

                                if (acc !in -1f..1f) {
                                    onDrag(acc.toLong())
                                    acc -= acc.toLong()
                                }
                            } catch (e: IllegalStateException) {
                                // View may have been detached during drag
                                isDragging.targetState = false
                                acc = 0f
                            }
                        },
                        onDragEnd = {
                            isDragging.targetState = false
                            acc = 0f
                            try {
                                onDragEnd()
                            } catch (e: IllegalStateException) {
                                // View may have been detached
                            }
                        },
                        onDragCancel = {
                            isDragging.targetState = false
                            acc = 0f
                            try {
                                onDragEnd()
                            } catch (e: IllegalStateException) {
                                // View may have been detached
                            }
                        }
                    )
                } catch (e: IllegalStateException) {
                    // View may have been detached before gesture detection
                }
            }
            .pointerInput(minimumValue, maximumValue) {
                if (maximumValue < minimumValue) return@pointerInput

                detectTapGestures(
                    onPress = { offset ->
                        onDragStart((offset.x / size.width * (maximumValue - minimumValue) + minimumValue).roundToLong())
                    },
                    onTap = {
                        onDragEnd()
                    }
                )
            }
            .padding(horizontal = scrubberRadius)

            .height(scrubberRadius)
    ) {

        Box(Modifier.align(Alignment.Center).playerSeekTrack(glassThumb)
            .drawWithContent {
                drawContent()

                val scrubberPosition = if (maximumValue < minimumValue) {
                    0f
                } else {
                    visualFraction * size.width
                }

                drawCircle(
                    color = barColor,
                    radius = currentScrubberRadius.toPx(),
                    center = center.copy(x = scrubberPosition)
                )

            }
        ) {
        Spacer(
            modifier = Modifier
                .height(currentBarHeight)
                .fillMaxWidth()
                .background(color = barColor, shape = shape)
                .align(Alignment.Center)
        )

        Spacer(
            modifier = Modifier
                .height(currentBarHeight)
                .fillMaxWidth(visualFraction)
                .background(color = backgroundColor, shape = shape)
                .align(Alignment.CenterStart)
        )
        }
        PlayerSeekOpticalThumb(glassThumb,
            (value.toFloat() - minimumValue) / (maximumValue - minimumValue))
    }


}



