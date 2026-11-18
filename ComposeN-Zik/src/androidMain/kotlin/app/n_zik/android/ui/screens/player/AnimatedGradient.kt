package app.n_zik.android.ui.screens.player

import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.media3.common.util.UnstableApi
import kotlin.math.sqrt

@OptIn(UnstableApi::class)
fun Modifier.animatedGradient(
    animating: Boolean,
    swatches: State<List<Color>>,
): Modifier = composed {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(rotation, animating) {
        if (!animating) return@LaunchedEffect
        val target = rotation.value + 360f
        rotation.animateTo(
            targetValue = target,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 30_000,
                    easing = LinearEasing,
                ),
            ),
        )
    }

    drawWithCache {
        val colors = swatches.value
        val rectSize = sqrt(size.width * size.width + size.height * size.height)
        val topLeft = Offset(
            x = -(rectSize - size.width) / 2,
            y = -(rectSize - size.height) / 2,
        )

        val brush1 = Brush.linearGradient(
            0f to colors[0],
            0.33f to colors[2],
            0.66f to colors[1],
            1f to colors[3],
            start = topLeft,
            end = Offset(rectSize * 0.7f, rectSize * 0.7f),
        )

        val brush2 = Brush.linearGradient(
            0f to colors[0],
            0.33f to colors[5],
            0.66f to colors[4],
            1f to colors[6],
            start = Offset(rectSize, 0f),
            end = Offset(0f, rectSize),
        )

        val maskBrush = Brush.linearGradient(
            0f to colors[0],
            1f to Color.Transparent,
            start = Offset(rectSize / 2f, 0f),
            end = Offset(rectSize / 2f, rectSize),
        )

        onDrawBehind {
            val value = rotation.value

            withTransform(transformBlock = { rotate(value) }) {
                drawRect(
                    brush = brush1,
                    topLeft = topLeft,
                    size = Size(rectSize, rectSize),
                )
            }

            withTransform(transformBlock = { rotate(-value) }) {
                drawRect(
                    brush = maskBrush,
                    topLeft = topLeft,
                    size = Size(rectSize, rectSize),
                    blendMode = BlendMode.DstOut,
                )
            }

            withTransform(transformBlock = { rotate(value) }) {
                drawRect(
                    brush = brush2,
                    topLeft = topLeft,
                    size = Size(rectSize, rectSize),
                    blendMode = BlendMode.DstAtop,
                )
            }
        }
    }
}


