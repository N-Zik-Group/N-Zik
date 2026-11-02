package app.n_zik.android.components.player

import android.app.Application
import android.graphics.Matrix
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import org.robolectric.annotation.GraphicsMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class PlayerDecorationAnimationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `visibility and lifecycle pause the production clock without counting hidden time`() {
        val visible = mutableStateOf(true)
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        lateinit var elapsed: State<Long>
        var active = false
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                active = rememberPlayerDecorationsActive(visible.value)
                elapsed = rememberPlayerDecorationElapsedNanos(active)
            }
        }
        fun awaitActive(expected: Boolean) {
            // External snapshot writes need to reach the recomposer before its next frame.
            // A single advanceTimeByFrame can process that notification without recomposing.
            compose.waitForIdle()
            compose.mainClock.advanceTimeUntil(500) { active == expected }
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(96)
        compose.runOnIdle {
            assertTrue(active)
            assertTrue(elapsed.value > 0L)
            visible.value = false
        }
        awaitActive(false)
        var pausedAt = 0L
        compose.runOnIdle { pausedAt = elapsed.value; assertTrue(!active) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle {
            assertEquals(pausedAt, elapsed.value)
            visible.value = true
        }
        awaitActive(true)
        compose.runOnIdle { assertTrue(active); assertEquals(pausedAt, elapsed.value) }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle {
            assertTrue(elapsed.value > pausedAt)
            assertTrue(elapsed.value - pausedAt <= 64_000_000L)
            owner.lifecycle.currentState = Lifecycle.State.CREATED
        }
        awaitActive(false)
        compose.runOnIdle { pausedAt = elapsed.value; assertTrue(!active) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle {
            assertEquals(pausedAt, elapsed.value)
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
        }
        awaitActive(true)
        compose.runOnIdle { assertTrue(active); assertEquals(pausedAt, elapsed.value) }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle {
            assertTrue(elapsed.value > pausedAt)
            assertTrue(elapsed.value - pausedAt <= 64_000_000L)
        }
    }
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `fluid brush redraws with a retained brush and matrix then freezes while hidden`() {
        val active = mutableStateOf(true)
        val shader = LinearGradientShader(Offset.Zero, Offset(80f, 0f), listOf(Color.Red, Color.Blue))
        var lastBrush: ShaderBrush? = null
        var drawnMatrix = FloatArray(9)
        var draws = 0
        var appliedActive = true
        compose.mainClock.autoAdvance = false
        compose.setContent {
            appliedActive = active.value
            val brush = animatePlayerBrushRotation(shader, Size(80f, 80f), 1_000, true, appliedActive)
            Box(Modifier.size(80.dp).testTag("fluid.rotation").drawBehind {
                val value = brush.value
                lastBrush?.let { assertSame(it, value) }
                lastBrush = value
                val matrix = Matrix()
                shader.getLocalMatrix(matrix)
                matrix.getValues(drawnMatrix)
                draws++
                drawRect(value)
            })
        }
        fun pixels(): IntArray {
            val image = compose.onNodeWithTag("fluid.rotation").captureToImage()
            return IntArray(image.width * image.height).also { image.readPixels(it) }
        }
        fun awaitActive(expected: Boolean) {
            compose.waitForIdle()
            compose.mainClock.advanceTimeUntil(500) { appliedActive == expected }
            compose.waitForIdle()
        }
        // Native rendering plus an initial capture establishes a real draw before ticking.
        val initialPixels = pixels()
        var before = drawnMatrix.copyOf()
        var initialDraws = 0
        compose.runOnIdle { initialDraws = draws; assertTrue("Initial scene was rendered", draws > 0) }
        compose.mainClock.advanceTimeBy(96)
        compose.waitForIdle()
        // MainTestClock advances Compose frames; Android's render traversal is separate.
        // Request the rendered frame before inspecting draw observations or its pixels.
        val animatedPixels = pixels()
        compose.runOnIdle {
            assertTrue("Time reads in drawBehind must invalidate the cached brush: $draws <= $initialDraws",
                draws > initialDraws)
            assertFalse("The shader's rendered matrix must rotate", before.contentEquals(drawnMatrix))
        }
        assertFalse("The real rendered gradient must change", initialPixels.contentEquals(animatedPixels))
        compose.runOnIdle { active.value = false }
        awaitActive(false)
        val pausedPixels = pixels()
        var pausedDraws = 0
        compose.runOnIdle { before = drawnMatrix.copyOf(); pausedDraws = draws }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("Inactive clock must not schedule decorative draws", pausedDraws, draws)
            assertArrayEquals(before, drawnMatrix, 0f)
        }
        assertTrue("Inactive image stays identical", pausedPixels.contentEquals(pixels()))
        compose.runOnIdle { active.value = true }
        awaitActive(true)
        assertTrue("Resume keeps the last frame before the next active tick", pausedPixels.contentEquals(pixels()))
        compose.runOnIdle { assertArrayEquals(before, drawnMatrix, 0f) }
        compose.mainClock.advanceTimeBy(48)
        compose.waitForIdle()
        val resumedPixels = pixels()
        compose.runOnIdle { assertFalse("Active frames resume rotating", before.contentEquals(drawnMatrix)) }
        assertFalse("Resumed gradient is visibly animated", pausedPixels.contentEquals(resumedPixels))
    }
}
