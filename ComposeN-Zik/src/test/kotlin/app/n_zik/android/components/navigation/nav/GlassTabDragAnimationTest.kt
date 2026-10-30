package app.n_zik.android.components.navigation.nav

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GlassTabDragAnimationTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var animation: GlassTabDragAnimation
    private val selections = mutableListOf<Int>()

    private fun setContent(motion: Boolean) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            animation = remember(scope) {
                GlassTabDragAnimation(
                    animationScope = scope,
                    motionAllowed = motion,
                    initialValue = 0f,
                    valueRange = 0f..3f,
                    visibilityThreshold = 0.001f,
                    initialScale = 1f,
                    pressedScale = 78f / 56f,
                    onDragStarted = {},
                    onDragStopped = {
                        val target = glassSettledTab(targetValue, 4)
                        selections += target
                        animateToValue(target.toFloat())
                    },
                    onDrag = { size, amount ->
                        updateValue(glassDraggedTab(targetValue, amount.x, size.width.toFloat(), 4, false))
                    },
                )
            }
            Box(Modifier.size(320.dp, 56.dp).testTag("selector").then(animation.modifier))
        }
    }

    @Test
    fun `press grows before drag and release selects target once then returns to resting size`() {
        setContent(motion = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("selector").performTouchInput { down(Offset(10f, center.y)) }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle {
            assertTrue(animation.pressProgress > 0.9f)
            assertTrue(animation.scaleX > 1.3f)
            assertTrue(animation.scaleY > 1.3f)
            assertTrue(selections.isEmpty())
        }
        compose.onNodeWithTag("selector").performTouchInput {
            moveBy(Offset(width / 4f * 1.6f, 0f))
        }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertTrue(selections.isEmpty()) }
        compose.onNodeWithTag("selector").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf(2), selections)
            assertEquals(2f, animation.value, 0.01f)
            assertEquals(1f, animation.scaleX, 0.01f)
            assertEquals(1f, animation.scaleY, 0.01f)
            assertEquals(0f, animation.pressProgress, 0.01f)
        }
    }

    @Test
    fun `reduced motion keeps drag selection with no expansion`() {
        setContent(motion = false)
        compose.onNodeWithTag("selector").performTouchInput {
            down(Offset(10f, center.y))
            moveBy(Offset(width / 4f * 1.6f, 0f))
        }
        compose.runOnIdle {
            assertEquals(1f, animation.scaleX, 0f)
            assertEquals(1f, animation.scaleY, 0f)
            assertEquals(0f, animation.pressProgress, 0f)
        }
        compose.onNodeWithTag("selector").performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(2), selections) }
    }
}
