package app.n_zik.android.components.player

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerTransportRibbedOpticsTest {
    private val field by lazy {
        val resources = ApplicationProvider.getApplicationContext<Application>().resources
        BitmapFactory.decodeResource(resources, R.drawable.player_ribbed_sdf,
            BitmapFactory.Options().apply { inScaled = false })
    }

    // Reconstruct the raw RG16 data exactly as AGSL does; filtering precedes decoding.
    private fun distance(x: Float, y: Float): Float {
        val px = x * field.width - .5f
        val py = y * field.height - .5f
        val ix = floor(px).toInt()
        val iy = floor(py).toInt()
        fun pixel(dx: Int, dy: Int): Float {
            val color = field.getPixel((ix + dx).coerceIn(0, field.width - 1),
                (iy + dy).coerceIn(0, field.height - 1))
            return ((((color shr 16) and 255) * 256 + ((color shr 8) and 255)) / 65535f - .5f) * 2f
        }
        val fx = px - ix
        val fy = py - iy
        return (pixel(0, 0) * (1 - fx) + pixel(1, 0) * fx) * (1 - fy) +
            (pixel(0, 1) * (1 - fx) + pixel(1, 1) * fx) * fy
    }

    private fun outline(): FloatArray = (PlayerTransportRibbedShape.createOutline(
        Size(1f, 1f), LayoutDirection.Ltr, Density(1f)) as Outline.Generic)
        .path.asAndroidPath().approximate(.00001f)

    // Robolectric SDK26-34 uses Android31 native SkSL and rejects every child.eval call.
    // SDK35 uses a modern compiler: https://github.com/robolectric/robolectric/issues/9691
    // This validates AGSL syntax and binding, not API33 device/GPU rendering.
    @Test @Config(sdk = [35])
    fun `modern native AGSL accepts the actual lens highlight and raw data binding`() {
        val buffer = android.graphics.BitmapShader(field, android.graphics.Shader.TileMode.CLAMP,
            android.graphics.Shader.TileMode.CLAMP).apply {
            setFilterMode(android.graphics.BitmapShader.FILTER_MODE_LINEAR)
        }
        for (source in listOf(RibbedLensShader, RibbedHighlightShader)) {
            android.graphics.RuntimeShader(source).apply {
                setInputBuffer("field", buffer)
                setFloatUniform("size", 100f, 100f)
                setFloatUniform("offset", 0f, 0f)
                setFloatUniform("fieldSize", 256f)
            }
        }
    }

    @Test fun `distance asset matches all exact polygon vertices and is not a circle or rectangle`() {
        assertEquals(256, field.width)
        assertEquals(256, field.height)
        val points = outline()
        assertTrue(points.size >= 360 * 3)
        for (i in points.indices step 3) {
            assertTrue("boundary $i: ${points[i + 1]},${points[i + 2]}",
                abs(distance(points[i + 1], points[i + 2])) < .0018f)
        }
        assertTrue(distance(.5f, .5f) < -.35f)
        assertTrue(distance(.01f, .01f) > .1f)
        // The real right-hand rib at y=.5 crosses x=.93457, inside the bounding box.
        assertTrue(distance(.925f, .5f) < 0f)
        assertTrue(distance(.945f, .5f) > 0f)
        // This asymmetric contour cannot be substituted with a circular distance field.
        assertTrue(abs(distance(.5f, .1f) - distance(.9f, .5f)) > .005f)
    }

    @Test fun `lookup normals follow the real boundary and contain no singular center values`() {
        val points = outline()
        var checked = 0
        for (i in 0 until points.size - 3 step 39) {
            val ax = points[i + 1]; val ay = points[i + 2]
            val bx = points[i + 4]; val by = points[i + 5]
            val length = hypot(bx - ax, by - ay)
            if (length < .001f) continue
            val nx = (by - ay) / length
            val ny = -(bx - ax) / length
            val x = (ax + bx) / 2 + nx * .008f
            val y = (ay + by) / 2 + ny * .008f
            val e = 1f / 256f
            val gx = distance(x + e, y) - distance(x - e, y)
            val gy = distance(x, y + e) - distance(x, y - e)
            val dot = (gx * nx + gy * ny) / hypot(gx, gy).coerceAtLeast(.000001f)
            assertTrue("normal at $x,$y: $dot", dot > .85f)
            assertTrue(distance(x, y) > 0f)
            checked++
        }
        assertTrue(checked >= 20)
        assertTrue(distance(.5f, .5f).isFinite())
    }
}
