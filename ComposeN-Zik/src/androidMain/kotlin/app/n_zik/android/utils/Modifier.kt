package app.n_zik.android.utils

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.ui.components.LocalShimmerClock
import app.n_zik.android.ui.components.LocalShimmerPalette

/** Draws the same screen-space sweep in each clipped loading shape. */
fun Modifier.shimmerEffect(): Modifier = composed {
    val clock = LocalShimmerClock.current
    val palette = LocalShimmerPalette.current ?: colorPalette()
    val viewport = LocalConfiguration.current
    val density = LocalDensity.current
    val width = with(density) { viewport.screenWidthDp.dp.toPx() }.coerceAtLeast(1f)
    val height = with(density) { viewport.screenHeightDp.dp.toPx() }
    var position by remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(clock) {
        if (clock != null) clock.attached++
        onDispose { if (clock != null) clock.attached-- }
    }

    onGloballyPositioned { position = it.positionInRoot() }.drawWithCache {
        val band = width * .4f
        val start = -band - height * .25f
        val distance = width + band - start
        val sheen = palette.text.copy(alpha = .12f)
        val sweep = Brush.linearGradient(
            listOf(Color.Transparent, sheen, Color.Transparent),
            start = Offset.Zero,
            end = Offset(band, band * .25f),
        )
        onDrawBehind {
            drawRect(palette.background2)
            if (clock != null) {
                val x = start + distance * clock.phase - position.x
                val y = -position.y
                translate(x, y) { drawRect(sweep, topLeft = Offset(-x, -y), size = size) }
            }
        }
    }
}

fun Modifier.vertical( enabled: Boolean = true ) =
    if ( enabled )
        layout { measurable, constraints ->
            val c: Constraints = constraints.copy( maxWidth = Int.MAX_VALUE )
            val placeable = measurable.measure( c )

            layout( placeable.height, placeable.width ) {
                placeable.place(
                    x = -(placeable.width / 2 - placeable.height / 2),
                    y = -(placeable.height / 2 - placeable.width / 2)
                )
            }
        }
    else this


