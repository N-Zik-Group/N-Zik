package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassActionJoinShapeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `vertical fusion and search reveal are the horizontal masks transposed`() {
        val revealingSearch = mutableStateOf(false)
        val progress = mutableStateOf(.2f)
        compose.setContent {
            val cell = with(LocalDensity.current) { 40.dp.toPx() }
            val base = RoundedCornerShape(12.dp)
            fun mask(vertical: Boolean) = if (revealingSearch.value)
                glassActionJoinShape(base, cell, progress.value, fixedTrailingCell = true, vertical = vertical)
            else glassGroupFusionShape(base, cell, progress.value, vertical = vertical)
            val length = if (revealingSearch.value) 60.dp else 80.dp
            Column {
                Box(Modifier.size(length, 44.dp).background(Color.Red).testTag("horizontal")) {
                    Box(Modifier.size(length, 44.dp).background(Color.Blue, mask(false)))
                }
                Box(Modifier.size(44.dp, length).background(Color.Red).testTag("vertical")) {
                    Box(Modifier.size(44.dp, length).background(Color.Blue, mask(true)))
                }
            }
        }
        listOf(false, true).forEach { search ->
            listOf(.2f, .5f, .8f).forEach { phase ->
                compose.runOnIdle { revealingSearch.value = search; progress.value = phase }
                val horizontal = compose.onNodeWithTag("horizontal").captureToImage().toPixelMap()
                val vertical = compose.onNodeWithTag("vertical").captureToImage().toPixelMap()
                assertEquals(horizontal.width, vertical.height)
                assertEquals(horizontal.height, vertical.width)
                var coverageError = 0f
                var solidMismatch = 0
                for (x in 0 until horizontal.width) for (y in 0 until horizontal.height) {
                    val difference = kotlin.math.abs(horizontal[x, y].blue - vertical[y, x].blue)
                    coverageError += difference
                    if (difference > .9f) solidMismatch++
                }
                // Skia's horizontal and vertical edge antialiasing need not produce
                // identical boundary pixels; compare coverage and solid interiors.
                assertEquals("same solid neck: search=$search phase=$phase", 0, solidMismatch)
                assertTrue("same liquid coverage: search=$search phase=$phase error=$coverageError",
                    coverageError < horizontal.width * horizontal.height * .005f)
            }
        }
    }

    @Test fun `neck cuts both edges while keeping one connected bridge across roundness settings`() {
        val radius = mutableStateOf(21.dp)
        compose.setContent {
            val density = LocalDensity.current
            val mask = glassActionJoinShape(RoundedCornerShape(radius.value), with(density) { 46.dp.toPx() }, .5f)
            Box(Modifier.size(69.dp, 42.dp).background(Color.Red).testTag("mask")) {
                Box(Modifier.size(69.dp, 42.dp).background(Color.Blue, mask))
            }
        }
        listOf(0.dp, 8.dp, 21.dp).forEach { roundness ->
            compose.runOnIdle { radius.value = roundness }
            val pixels = compose.onNodeWithTag("mask").captureToImage().toPixelMap()
            val seam = pixels.width * 2 / 3
            assertEquals(Color.Red, pixels[seam, pixels.height / 6])
            assertEquals(Color.Red, pixels[seam, pixels.height * 5 / 6])
            // Every centre pixel belongs to the same bridge, including the join itself.
            for (x in 1 until pixels.width - 1) assertEquals(Color.Blue, pixels[x, pixels.height / 2])
            assertEquals(if (roundness == 0.dp) Color.Blue else Color.Red, pixels[0, 0])
        }
    }

    @Test fun `settled and transferred states return exactly the configured capsule`() {
        val capsule = RoundedCornerShape(12.dp)
        listOf(0f, 1f, 1.2f, Float.NaN, Float.POSITIVE_INFINITY).forEach { phase ->
            assertSame(capsule, glassActionJoinShape(capsule, 46f, phase))
            assertEquals(0f, glassActionJoinInset(42f, phase))
        }
        assertTrue(glassActionJoinInset(42f, .5f) > 14f)
        assertTrue(glassActionJoinInset(42f, .9f) < glassActionJoinInset(42f, .5f))
        assertEquals(92, glassActionGroupSurfaceWidth(92, 46, 1.2f, false))
    }

    @Test fun `search grows from the fixed trailing profile with the same liquid neck`() {
        compose.setContent {
            val density = LocalDensity.current
            val mask = glassActionJoinShape(RoundedCornerShape(12.dp),
                with(density) { 44.dp.toPx() }, .5f, fixedTrailingCell = true)
            Box(Modifier.size(66.dp, 40.dp).background(Color.Red).testTag("leadingMask")) {
                Box(Modifier.size(66.dp, 40.dp).background(Color.Blue, mask))
            }
        }
        val pixels = compose.onNodeWithTag("leadingMask").captureToImage().toPixelMap()
        val seam = pixels.width / 3
        assertEquals(Color.Red, pixels[seam, pixels.height / 6])
        assertEquals(Color.Red, pixels[seam, pixels.height * 5 / 6])
        assertEquals(Color.Blue, pixels[seam, pixels.height / 2])
        assertEquals(Color.Blue, pixels[pixels.width * 2 / 3, pixels.height / 6])
    }
}
