package app.n_zik.android.components.navigation

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.time.Duration
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class ScreenEdgeFloatingActionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `distance clears whole window before first measurement and from high dragged anchors`() {
        assertEquals(832f, floatingActionScreenExitDistance(800f, null, 32f), 0f)
        assertEquals(612f, floatingActionScreenExitDistance(800f, 220f, 32f), 0f)
        assertEquals(132f, floatingActionScreenExitDistance(800f, 700f, 32f), 0f)
    }

    @Test fun `arrow enters and exits past the window without clipping at its own slot`() {
        verifyWindowTravel(bottomClearance = 140, dragY = 0f)
    }

    @Test fun `footer reserve and saved upward drag still travel beyond the window edge`() {
        verifyWindowTravel(bottomClearance = 260, dragY = -120f)
    }

    private fun advanceFrame() {
        composeRule.mainClock.advanceTimeByFrame()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun advanceTime(millis: Long) {
        composeRule.mainClock.advanceTimeBy(millis)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }

    private fun verifyWindowTravel(bottomClearance: Int, dragY: Float) {
        val visible = mutableStateOf(false)
        val offsetX = mutableStateOf(-30f)
        val offsetY = mutableStateOf(dragY)
        var coordinates: LayoutCoordinates? = null
        var windowBottom = 0f
        var clicks = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            windowBottom = LocalWindowInfo.current.containerSize.height.toFloat()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = bottomClearance.dp)) {
                    ScreenEdgeFloatingAction(visible.value, offsetX, offsetY) { motion ->
                        Box(motion.size(62.dp).background(Color.Red).testTag("arrow")
                            .onPlaced { coordinates = it }.clickable { clicks++ })
                    }
                }
            }
        }
        composeRule.runOnIdle { visible.value = true }
        advanceFrame()
        advanceFrame()
        composeRule.runOnIdle {
            assertTrue("entry begins beyond the actual window", requireNotNull(coordinates).localToWindow(Offset.Zero).y >= windowBottom)
        }
        advanceTime(180)
        var enteringTop = 0f
        composeRule.runOnIdle {
            val bounds = requireNotNull(coordinates).boundsInWindow()
            enteringTop = bounds.top
            assertTrue("the live action remains drawn while outside its resting slot", bounds.height > 50f)
        }
        advanceTime(350)
        var restingTop = 0f
        composeRule.runOnIdle {
            restingTop = requireNotNull(coordinates).localToWindow(Offset.Zero).y
            assertTrue("entry travels more than the button height", enteringTop > restingTop + 62f)
            assertEquals("animation must preserve saved drag", dragY, offsetY.value, 0f)
            assertEquals(-30f, offsetX.value, 0f)
        }
        composeRule.onNodeWithTag("arrow").performClick()
        composeRule.runOnIdle { assertEquals(1, clicks); visible.value = false }
        advanceFrame()
        advanceFrame()
        advanceTime(150)
        composeRule.runOnIdle {
            val bounds = requireNotNull(coordinates).boundsInWindow()
            assertTrue("exit stays visible after leaving its own slot", bounds.top > restingTop + 62f && bounds.height > 50f)
        }
        advanceTime(300)
        composeRule.runOnIdle {
            assertTrue("exit reaches below the window before disposal", requireNotNull(coordinates).localToWindow(Offset.Zero).y >= windowBottom)
        }
        advanceTime(100)
        composeRule.onNodeWithTag("arrow").assertDoesNotExist()
    }
}
