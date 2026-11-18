package app.n_zik.android.utils

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.dropShadow(
    shape: Shape,
    color: Color = Color.Black.copy(0.25f),
    blur: Dp = 4.dp,
    offsetY: Dp = 4.dp,
    offsetX: Dp = 0.dp,
    spread: Dp = 0.dp
) = this.drawWithCache {
    val spreadPx = spread.toPx()
    val shadowSize = Size(size.width + spreadPx, size.height + spreadPx)
    val shadowOutline = shape.createOutline(shadowSize, layoutDirection, this)
    val blurPx = blur.toPx()
    val shadowOffsetX = offsetX.toPx()
    val shadowOffsetY = offsetY.toPx()
    val paint = Paint().apply {
        this.color = color
        if (blurPx > 0f) {
            asFrameworkPaint().maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
    }
    onDrawBehind {
        drawIntoCanvas { canvas ->
            canvas.save()
            canvas.translate(shadowOffsetX, shadowOffsetY)
            canvas.drawOutline(shadowOutline, paint)
            canvas.restore()
        }
    }
}

fun Modifier.doubleShadowDrop(
    shape: Shape,
    offset: Dp = 4.dp,
    blur: Dp = 8.dp,
) = this
    .dropShadow(shape, Color.Black.copy(0.75f), blur, offset, offset)
    .dropShadow(shape, Color.Black.copy(0.50f), blur, -offset, -offset)


