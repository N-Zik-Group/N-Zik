package app.n_zik.android.components.player

import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import com.mikepenz.hypnoticcanvas.RuntimeEffect
import com.mikepenz.hypnoticcanvas.shaders.MeshGradient
import com.mikepenz.hypnoticcanvas.shaders.Shader

/** Keep the existing mesh and update its palette on the draw clock without recompiling it. */
internal class PlayerMeshGradient(
    private val swatches: State<List<Color>>,
) : Shader by MeshGradient(Array(7) { Color.Black }, scale = 1f) {
    private val uniforms = FloatArray(21)
    private var previous: List<Color>? = null

    override fun applyUniforms(runtimeEffect: RuntimeEffect, time: Float, width: Float, height: Float) {
        val colors = swatches.value
        if (colors !== previous) {
            // Mesh uses its final swatch as the base. Our base (dominant) is first.
            colors.indices.forEach { index ->
                val color = colors[(index + 1) % colors.size]
                uniforms[index * 3] = color.red
                uniforms[index * 3 + 1] = color.green
                uniforms[index * 3 + 2] = color.blue
            }
            previous = colors
        }
        runtimeEffect.setFloatUniform("uResolution", width, height, width / height)
        runtimeEffect.setFloatUniform("uTime", time)
        runtimeEffect.setFloatUniform("uColor", uniforms)
    }
}
