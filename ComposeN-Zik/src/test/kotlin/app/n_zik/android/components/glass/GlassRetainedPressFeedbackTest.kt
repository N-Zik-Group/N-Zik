package app.n_zik.android.components.glass

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
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
class GlassRetainedPressFeedbackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `retained feedback survives consumed drag until actual up and cancel`() {
        val source = MutableInteractionSource()
        var clicks = 0
        var holds = 0
        var nativeCancels = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LaunchedEffect(source) {
                source.interactions.collect { if (it is PressInteraction.Cancel) nativeCancels++ }
            }
            Box(Modifier.size(180.dp).background(Color.Black)) {
                Box(Modifier.size(80.dp).testTag("button")
                    .retainGlassButtonGesture()
                    .glassButtonFeedback(source, RectangleShape, retainPressUntilUp = true)
                    .background(Color.Black)
                    .combinedClickable(source, null, onClick = { clicks++ }, onLongClick = { holds++ }))
            }
        }
        val button = compose.onNodeWithTag("button")
        button.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1500)
        button.performTouchInput { longClick() }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { assertEquals(1, clicks); assertEquals(1, holds) }
        for (cancelled in listOf(false, true)) {
            val previousCancels = nativeCancels
            button.performTouchInput { down(center) }
            shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(250)
            button.performTouchInput { moveTo(center + Offset(180f, 40f), 100) }
            compose.mainClock.advanceTimeBy(1800)
            compose.runOnIdle {
                assertEquals("The native action really was cancelled by consumed drag", previousCancels + 1, nativeCancels)
            }
            val held = button.captureToImage().toPixelMap()
            assertTrue("Consumed drag must retain the visible press while the finger is down",
                held[held.width / 2, held.height / 2].red > .03f)
            button.performTouchInput { if (cancelled) cancel() else up() }
            compose.mainClock.advanceTimeBy(1800)
            val released = button.captureToImage().toPixelMap()
            assertEquals(0f, released[released.width / 2, released.height / 2].red, .005f)
        }
        compose.runOnIdle { assertEquals(1, clicks); assertEquals(1, holds) }
    }
}
