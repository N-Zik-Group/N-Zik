package app.n_zik.android.components.glass

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalFoundationApi::class)
class GlassButtonFeedbackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `shared feedback follows held pointer and native callbacks occur once`() {
        val source = MutableInteractionSource()
        var clicks = 0
        var holds = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(180.dp, 90.dp).background(Color.Black)) {
                Box(Modifier.size(120.dp, 48.dp).testTag("feedback")
                    .glassButtonFeedback(source, RectangleShape)
                    .background(Color.Black)
                    .combinedClickable(source, null, onClick = { clicks++ }, onLongClick = { holds++ }))
            }
        }
        val button = compose.onNodeWithTag("feedback")
        button.performTouchInput { down(Offset(12f, 24f)) }
        shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeBy(300)
        val before = button.captureToImage().toPixelMap()
        button.performTouchInput { moveTo(Offset(100f, 24f)) }
        compose.mainClock.advanceTimeByFrame()
        val after = button.captureToImage().toPixelMap()
        fun rightBrightness(pixels: androidx.compose.ui.graphics.PixelMap): Float =
            pixels[pixels.width * 4 / 5, pixels.height / 2].red
        assertTrue("The drawn highlight follows movement", rightBrightness(after) > rightBrightness(before))
        button.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { assertEquals(1, clicks); assertEquals(0, holds) }
        button.performTouchInput { longClick() }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { assertEquals(1, clicks); assertEquals(1, holds) }
    }

    @Test fun `feedback disabled preserves drawing and native click ownership`() {
        val source = MutableInteractionSource()
        var clicks = 0
        compose.setContent {
            Box(Modifier.size(48.dp).testTag("native")
                .glassButtonFeedback(source, RectangleShape, enabled = false)
                .background(Color.Black)
                .combinedClickable(source, null, onClick = { clicks++ }))
        }
        val native = compose.onNodeWithTag("native")
        native.performTouchInput { down(center) }
        val pixels = native.captureToImage().toPixelMap()
        assertEquals(Color.Black, pixels[pixels.width / 2, pixels.height / 2])
        native.performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun `release interrupts press immediately and cannot create a queued flash`() {
        lateinit var highlight: InteractiveHighlight
        compose.mainClock.autoAdvance = false
        compose.setContent { highlight = InteractiveHighlight(rememberCoroutineScope()) }
        compose.runOnIdle {
            highlight.startPress(Offset(10f, 10f))
            highlight.releasePress()
        }
        repeat(12) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { assertEquals(0f, highlight.pressProgress, .001f) }
        }
        compose.runOnIdle { highlight.startPress(Offset(10f, 10f)) }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle {
            assertTrue(highlight.pressProgress > .9f)
            highlight.movePress(Offset(25f, 15f))
            assertEquals(Offset(15f, 5f), highlight.offset)
            highlight.releasePress()
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { assertEquals(0f, highlight.pressProgress, .001f); assertEquals(Offset.Zero, highlight.offset) }
    }
}
