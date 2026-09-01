package app.it.fast4x.rimusic.ui.styling

import app.n_zik.android.uiRoundnessShape

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CircleShape

class BoundedCornerSize(val dp: Dp, val maxFraction: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val requestedPx = with(density) { dp.toPx() }
        val maxPx = shapeSize.minDimension * maxFraction
        return kotlin.math.min(requestedPx, maxPx)
    }
}

data class Appearance(
    val colorPalette: ColorPalette,
    val typography: Typography,
    val thumbnailShape: Shape,
    val uiRoundnessShape: Shape,
    val artistThumbnailShape: Shape
) {
    companion object : Saver<Appearance, List<Any>> {
        @Suppress("UNCHECKED_CAST")
        override fun restore(value: List<Any>): Appearance {
            val thumbRadius = (value[2] as? Float) ?: (value[2] as? Int)?.toFloat() ?: 12f
            val uiRadius = (value[3] as? Float) ?: (value[3] as? Int)?.toFloat() ?: 20f
            val artistThumbRadius = (value.getOrNull(4) as? Float) ?: (value.getOrNull(4) as? Int)?.toFloat() ?: 48f
            return Appearance(
                colorPalette = ColorPalette.restore(value[0] as List<Any>),
                typography = Typography.restore(value[1] as List<Any>),
                thumbnailShape = if (thumbRadius >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbRadius.dp, 0.25f)),
                uiRoundnessShape = RoundedCornerShape(BoundedCornerSize(uiRadius.dp, 0.4f)),
                artistThumbnailShape = if (artistThumbRadius >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(artistThumbRadius.dp, 0.25f))
            )
        }

        override fun SaverScope.save(value: Appearance): List<Any> {
            val thumbRadius = when (val shape = value.thumbnailShape) {
                is RoundedCornerShape -> {
                    val size = shape.topStart
                    if (size is BoundedCornerSize) size.dp.value else 12f
                }
                else -> 48f // For CircleShape
            }

            val uiRadius = when (val shape = value.uiRoundnessShape) {
                is RoundedCornerShape -> {
                    val size = shape.topStart
                    if (size is BoundedCornerSize) size.dp.value else 20f
                }
                else -> 20f
            }

            val artistThumbRadius = when (val shape = value.artistThumbnailShape) {
                is RoundedCornerShape -> {
                    val size = shape.topStart
                    if (size is BoundedCornerSize) size.dp.value else 12f
                }
                else -> 48f // For CircleShape
            }

            return listOf(
                with(ColorPalette.Companion) { save(value.colorPalette) },
                with(Typography.Companion) { save(value.typography) },
                thumbRadius,
                uiRadius,
                artistThumbRadius
            )
        }
    }
}

val LocalAppearance = staticCompositionLocalOf<Appearance> { error("No Appearance provided") }