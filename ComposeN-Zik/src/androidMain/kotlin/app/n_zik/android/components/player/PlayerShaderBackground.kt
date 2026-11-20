package app.n_zik.android.components.player

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import com.mikepenz.hypnoticcanvas.RuntimeEffect
import com.mikepenz.hypnoticcanvas.shaders.Shader

/** Draws the retained shader phase even when the player is not running its decorations. */
@Composable
internal fun Modifier.playerShaderBackground(shader: Shader, animating: Boolean): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val effect = remember(shader.sksl) { PlayerRuntimeEffect(shader.sksl) }
    val elapsed = rememberPlayerDecorationElapsedNanos(animating)
    return drawBehind {
        if (size.width > 0f && size.height > 0f) {
            val time = elapsed.value / 166_000_000f * shader.speedModifier
            shader.applyUniforms(effect, time, size.width, size.height)
            drawRect(brush = effect.build())
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class PlayerRuntimeEffect(source: String) : RuntimeEffect {
    private val shader = RuntimeShader(source)
    private val brush = ShaderBrush(shader)
    override val supported = true
    override val ready = true
    override fun build(): Brush = brush

    override fun setFloatUniform(name: String, value1: Float) = shader.setFloatUniform(name, value1)
    override fun setFloatUniform(name: String, value1: Float, value2: Float) =
        shader.setFloatUniform(name, value1, value2)
    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) =
        shader.setFloatUniform(name, value1, value2, value3)
    override fun setFloatUniform(name: String, values: FloatArray) = shader.setFloatUniform(name, values)
}
