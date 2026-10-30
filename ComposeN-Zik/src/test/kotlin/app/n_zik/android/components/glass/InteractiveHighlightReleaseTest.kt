package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class InteractiveHighlightReleaseTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `held highlight fades monotonically and never flashes after becoming invisible`() {
        val highlight = createHighlight()
        compose.runOnIdle { highlight.startPress(Offset(20f, 20f)) }
        compose.mainClock.advanceTimeBy(1600)
        compose.runOnIdle { assertEquals(1f, highlight.pressProgress, .001f) }
        sampleRelease(highlight, monotonic = true)
    }

    @Test fun `immediate cancel and quick release never flash after becoming invisible`() {
        val highlight = createHighlight()
        listOf(0L, 32L, 96L).forEach { heldMillis ->
            compose.runOnIdle { highlight.startPress(Offset(20f, 20f)) }
            if (heldMillis > 0) compose.mainClock.advanceTimeBy(heldMillis)
            sampleRelease(highlight, monotonic = false)
        }
    }

    private fun createHighlight(): InteractiveHighlight {
        lateinit var highlight: InteractiveHighlight
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            highlight = remember(scope) { InteractiveHighlight(scope) }
        }
        return highlight
    }

    private fun sampleRelease(highlight: InteractiveHighlight, monotonic: Boolean) {
        var previous = highlight.pressProgress
        var becameInvisible = false
        compose.runOnIdle { highlight.releasePress() }
        repeat(100) { frame ->
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                val progress = highlight.pressProgress
                if (monotonic) assertTrue("Release increased on frame $frame: $previous -> $progress", progress <= previous + .00001f)
                assertFalse("Highlight flashed again on frame $frame", becameInvisible && progress > 0f)
                if (progress <= 0f) becameInvisible = true
                previous = progress
            }
        }
        compose.runOnIdle {
            assertTrue("Release must become invisible", becameInvisible)
            assertEquals(0f, highlight.pressProgress, .00001f)
        }
    }
}
