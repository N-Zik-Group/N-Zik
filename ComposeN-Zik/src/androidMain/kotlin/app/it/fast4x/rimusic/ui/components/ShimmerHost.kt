package app.it.fast4x.rimusic.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset // Add this import
import com.valentinilk.shimmer.shimmer

@Composable
fun ShimmerHost(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit
) {
    // gh-606 M11: was a hand-rolled `LaunchedEffect { while(true) { ...; delay(16) } }` loop
    // (an approximate ~60 FPS clock). Replaced with the same idiomatic infinite-animation

    // pattern already used elsewhere in this M11 lot (SeekBarWaved/FluidGradient/BlurredCover):
    // synced to Compose's real frame clock instead of a fixed delay. 1600ms tween covers the

    // same 0..1000 range the old loop did in ~100 steps of 10f at ~16ms/step, so the visual
    // cadence is unchanged.
    val shimmerTranslateAnim by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "shimmer"
    )

    Column(
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement,
        modifier = modifier
            .shimmer()
            .graphicsLayer(alpha = 0.99f)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        start = Offset(shimmerTranslateAnim, 0f),
                        end = Offset(shimmerTranslateAnim + 200f, 0f)
                    ),
                    blendMode = BlendMode.DstIn
                )
            },
        content = content
    )
}


