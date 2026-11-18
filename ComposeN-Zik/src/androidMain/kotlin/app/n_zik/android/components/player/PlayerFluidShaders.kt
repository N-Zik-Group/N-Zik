package app.n_zik.android.components.player

import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.lerp
import app.n_zik.android.components.glass.GlassArtworkTint
import app.n_zik.android.ui.styling.ColorPalette

private fun playerFluidShader(size: Size, start: Color, end: Color): Shader =
    LinearGradientShader(Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height),
        listOf(start, end), listOf(0f, 1f))

internal fun Modifier.playerFluidBackground(colors: State<PlayerFluidColors>, elapsed: State<Long>) = drawWithCache {
    val stops = colors.value
    val base = playerFluidShader(size, stops.baseStart, stops.baseEnd)
    val mask = playerFluidShader(size, stops.baseEnd, Color.Transparent)
    val fill = playerFluidShader(size, stops.fillStart, stops.fillEnd)
    val baseBrush = ShaderBrush(base)
    val maskBrush = ShaderBrush(mask)
    val fillBrush = ShaderBrush(fill)
    val matrix = Matrix()
    fun rotate(shader: Shader, timeNanos: Long, durationNanos: Long, direction: Float) {
        val phase = (timeNanos % durationNanos).toFloat() / durationNanos
        matrix.setRotate(phase * 360f * direction, size.width / 2f, size.height / 2f)
        shader.setLocalMatrix(matrix)
    }
    onDrawBehind {
        val timeNanos = elapsed.value
        rotate(base, timeNanos, 20_000_000_000L, 1f)
        drawRect(brush = baseBrush)
        rotate(mask, timeNanos, 15_000_000_000L, 1f)
        drawRect(brush = maskBrush, blendMode = BlendMode.DstOut)
        rotate(fill, timeNanos, 12_000_000_000L, -1f)
        drawRect(brush = fillBrush, blendMode = BlendMode.DstAtop)
    }
}

/** Both composited brushes and the provisional contrast derive from these same stops. */
internal data class PlayerFluidColors(
    val baseStart: Color,
    val baseEnd: Color,
    val fillStart: Color,
    val fillEnd: Color,
) {
    companion object {
        val ExtremeBlackCover = PlayerFluidColors(Color(0xFF242424), Color.Black, Color(0xFF171717), Color(0xFF070707))
    }

    fun at(target: PlayerFluidColors, fraction: Float) = PlayerFluidColors(
        lerp(baseStart, target.baseStart, fraction),
        lerp(baseEnd, target.baseEnd, fraction),
        lerp(fillStart, target.fillStart, fraction),
        lerp(fillEnd, target.fillEnd, fraction),
    )

    // At the rotation center the mask leaves half of the base over the fill.
    fun centerSurface(): Color = lerp(lerp(baseStart, baseEnd, .5f), lerp(fillStart, fillEnd, .5f), .5f)
}

@Composable
internal fun <T> rememberPlayerColorFade(
    target: T,
    active: Boolean,
    interpolate: (T, T, Float) -> T,
): State<T> {
    val displayed = remember { mutableStateOf(target) }
    LaunchedEffect(target, active) {
        val from = displayed.value
        if (active && from != target) {
            Animatable(0f).animateTo(1f, tween(350, easing = FastOutSlowInEasing)) {
                displayed.value = interpolate(from, target, value)
            }
        }
        displayed.value = target
    }
    return displayed
}

internal fun interpolatePlayerSwatches(from: List<Color>, target: List<Color>, fraction: Float): List<Color> =
    from.indices.map { lerp(from[it], target[it], fraction) }

/** Paint roles driven by the same presented artwork and the same animation frame. */
internal data class PlayerArtworkPaint(
    val accent: Color,
    val surface: Color,
    val fluid: PlayerFluidColors?,
    val swatches: List<Color>?,
    val glass: GlassArtworkTint,
    val lyricsForeground: Color = Color.DarkGray,
    val visualizerForeground: Color = Color.White,
    val lyricsSurface: Color = Color.Unspecified,
    val themePalette: ColorPalette? = null,
) {
    fun at(target: PlayerArtworkPaint, fraction: Float) = PlayerArtworkPaint(
        accent = lerp(accent, target.accent, fraction),
        surface = lerp(surface, target.surface, fraction),
        fluid = target.fluid?.let { (fluid ?: it).at(it, fraction) },
        swatches = target.swatches?.let { interpolatePlayerSwatches(swatches ?: it, it, fraction) },
        glass = glass.lerpTo(target.glass, fraction),
        lyricsForeground = lerp(lyricsForeground, target.lyricsForeground, fraction),
        visualizerForeground = lerp(visualizerForeground, target.visualizerForeground, fraction),
        lyricsSurface = if (lyricsSurface == target.lyricsSurface) lyricsSurface else lerp(lyricsSurface, target.lyricsSurface, fraction),
        themePalette = target.themePalette,
    )
}
