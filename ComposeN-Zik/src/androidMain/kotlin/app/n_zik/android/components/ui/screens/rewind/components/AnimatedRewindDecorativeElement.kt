/**
 *  Project (C) 2026 "Wrapped" feature.
 * Original licensed under GPL-3.0 | See git history for contributors.
 * Ported into Rewind feature.
 */

package app.n_zik.android.components.ui.screens.rewind.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * A small rotating outline shape (arc, circle, or square) used to add ambient
 * motion in the corners of Rewind slides, in the style of the Wrapped feature.
 */
@Composable
fun AnimatedRewindDecorativeElement(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    tint: Color = Color.White
) {
    val rotation = remember { Animatable(0f) }
    val shapeType = remember { Random.nextInt(3) }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(Random.nextLong(500))
            rotation.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(Random.nextInt(1000, 3000)),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }
    Canvas(modifier.graphicsLayer { rotationZ = rotation.value }) {
        val strokeWidth = 2.dp.toPx()
        when (shapeType) {
            0 -> drawArc(tint.copy(0.18f), 0f, 90f, false, style = Stroke(strokeWidth))
            1 -> drawCircle(tint.copy(0.18f), style = Stroke(strokeWidth))
            2 -> drawRect(tint.copy(0.18f), style = Stroke(strokeWidth))
        }
    }
}
