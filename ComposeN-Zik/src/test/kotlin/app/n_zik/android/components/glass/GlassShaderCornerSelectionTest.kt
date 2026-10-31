package app.n_zik.android.components.glass

import android.app.Application
import android.graphics.RuntimeShader
import app.n_zik.android.components.glass.backdrop.internal.AmbientHighlightShaderString
import app.n_zik.android.components.glass.backdrop.internal.DefaultHighlightShaderString
import app.n_zik.android.components.glass.backdrop.internal.RoundedRectRefractionShaderString
import app.n_zik.android.components.glass.backdrop.internal.RoundedRectRefractionWithDispersionShaderString
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassShaderCornerSelectionTest {
    private val shaders = mapOf(
        "refraction" to RoundedRectRefractionShaderString,
        "dispersion" to RoundedRectRefractionWithDispersionShaderString,
        "highlight" to DefaultHighlightShaderString,
        "ambient highlight" to AmbientHighlightShaderString,
    )

    @Test fun `all real shader mains retain centered corner lookup source contract`() {
        for ((name, source) in shaders) {
            assertTrue("$name must select signed quadrants around the pane center",
                usesCenteredCornerCoordinates(source))
            // Negative control reproduces the original unsigned-pixel-coordinate bug.
            val unsigned = source.replace("radiusAt(centeredCoord, cornerRadii)", "radiusAt(coord, cornerRadii)")
            assertFalse("$name must reject the original bottom-right-only lookup",
                usesCenteredCornerCoordinates(unsigned))
        }
    }

    @Test fun `production derived scalar corner probes compile in native Skia`() {
        for (source in shaders.values) {
            // Compile the real main's coordinate/radius prefix in isolation. JVM Skia
            // cannot compile content.eval in the complete lens, and software Canvas
            // cannot render RuntimeShader: this test makes no pixel-rendering claim.
            val main = source.substringAfter("half4 main(float2 coord) {")
            val coordinateSetup = main.substringBefore("float radius =")
            val radiusExpression = main.substringAfter("float radius =").substringBefore(';')
            // The colour uniform is unused by this scalar probe; JVM Skia also lacks
            // Android's layout(color) qualifier. Keep production programs untouched.
            val prefix = source.substringBefore("half4 main(float2 coord) {")
                .replace("layout(color) uniform half4 color;", "")
            val probe = prefix + """
                half4 main(float2 coord) {
                    $coordinateSetup
                    float radius = $radiusExpression;
                    return half4(half3(radius / 32.0), 1.0);
                }
            """.trimIndent()
            RuntimeShader(probe)
        }
    }

    private fun usesCenteredCornerCoordinates(source: String): Boolean {
        val main = source.substringAfter("half4 main(float2 coord) {", "")
        val coordinateSetup = main.substringBefore("float radius =").replace(Regex("\\s+"), "")
        val expression = main.substringAfter("float radius =", "").substringBefore(';')
            .replace(Regex("\\s+"), "")
        val centeredSetup = if (source.contains("uniform float2 offset;"))
            "float2centeredCoord=(coord+offset)-halfSize;" else "float2centeredCoord=coord-halfSize;"
        return "float2halfSize=size*0.5;" in coordinateSetup && centeredSetup in coordinateSetup &&
            expression == "radiusAt(centeredCoord,cornerRadii)"
    }
}
