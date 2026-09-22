/**
 *  (C) 2026 "Wrapped" feature.
 * Original licensed under GPL-3.0 | See git history for contributors.
 * Ported into Rewind feature.
 */

package app.n_zik.android.components.ui.screens.rewind.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

enum class RewindShapeType {
    Circle, Rect, Line
}

private data class RewindFloatingElement(
    val shapeType: RewindShapeType,
    val initialX: Float,
    val initialY: Float,
    val targetX: Float,
    val targetY: Float,
    val size: Float,
    val alpha: Float,
    val duration: Int
)

/**
 * A field of slowly drifting particles used as ambient motion behind Rewind slides,
 * in the style of the Wrapped feature. [tint] lets callers match each slide's accent color.
 */
@Composable
fun AnimatedRewindBackground(
    modifier: Modifier = Modifier,
    elementCount: Int = 16,
    tint: Color = Color.White,
    shapeTypes: List<RewindShapeType> = listOf(RewindShapeType.Circle)
) {
    val random = remember { Random(System.currentTimeMillis()) }
    val elements = remember {
        List(elementCount) {
            val shapeType = shapeTypes.random(random)
            RewindFloatingElement(
                shapeType = shapeType,
                initialX = random.nextFloat(),
                initialY = random.nextFloat(),
                targetX = random.nextFloat(),
                targetY = random.nextFloat(),
                size = if (shapeType == RewindShapeType.Circle) random.nextFloat() * 12f + 4f else random.nextFloat() * 40f + 8f,
                alpha = random.nextFloat() * 0.22f + 0.06f,
                duration = random.nextInt(5000, 11000)
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "rewind_animated_bg")
    val progressAnims = elements.map {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(it.duration, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rewind_element_progress"
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        elements.forEachIndexed { index, element ->
            val progress = progressAnims[index].value
            val currentX = element.initialX + (element.targetX - element.initialX) * progress
            val currentY = element.initialY + (element.targetY - element.initialY) * progress

            when (element.shapeType) {
                RewindShapeType.Circle -> {
                    drawCircle(
                        color = tint.copy(alpha = element.alpha),
                        radius = element.size,
                        center = Offset(currentX * size.width, currentY * size.height)
                    )
                }
                RewindShapeType.Rect -> {
                    drawRect(
                        color = tint.copy(alpha = element.alpha),
                        topLeft = Offset(currentX * size.width, currentY * size.height),
                        size = Size(element.size, element.size)
                    )
                }
                RewindShapeType.Line -> {
                    val endX = currentX + (element.targetX - element.initialX) * 0.1f
                    val endY = currentY + (element.targetY - element.initialY) * 0.1f
                    drawLine(
                        color = tint.copy(alpha = element.alpha),
                        start = Offset(currentX * size.width, currentY * size.height),
                        end = Offset(endX * size.width, endY * size.height),
                        strokeWidth = 2f
                    )
                }
            }
        }
    }
}
