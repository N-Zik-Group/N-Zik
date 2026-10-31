package app.n_zik.android.components.navigation.nav

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
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
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.GlassSettings

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
                    onDragCancelled = { updateValue(0f) },
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

    @Test fun `cancelled moved gesture restores committed target without selecting`() {
        setContent(motion = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("selector").performTouchInput {
            down(Offset(10f, center.y))
            moveBy(Offset(width / 2f, 0f))
        }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertTrue(animation.targetValue > 1f) }
        compose.onNodeWithTag("selector").performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("Cancellation must not commit a tab", selections.isEmpty())
            assertEquals(0f, animation.value, .01f)
            assertEquals(0f, animation.pressProgress, .01f)
        }
    }

    @Test fun `previous release cannot shrink a new held press`() {
        setContent(motion = true)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { animation.press() }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle {
            assertTrue(animation.scaleX > 1.3f)
            // Release has a next-frame barrier; re-press before it resumes.
            animation.release()
            animation.press()
        }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle {
            assertTrue("The newer pointer still owns expansion", animation.scaleX > 1.3f)
            assertTrue(animation.scaleY > 1.3f)
            assertTrue(animation.pressProgress > .9f)
            animation.release()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1f, animation.scaleX, .01f)
            assertEquals(0f, animation.pressProgress, .01f)
        }
    }

    @Test fun `real navigation tabs never dispatch the tentative selection on cancellation`() {
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                Typography.fallback, shape, shape, shape)) {
                GlassNavigationTabs(selectedIndex = 0, indices = listOf(0, 1, 2, 3),
                    onTabChanged = { selections += it }, settings = GlassSettings(enabled = true),
                    vertical = false, modifier = Modifier.size(320.dp, 64.dp).testTag("tabs")) {
                    repeat(4) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("tabs").performTouchInput {
            // The active selector initially occupies the first quarter.
            down(Offset(width / 8f, center.y))
            moveBy(Offset(width / 2f, 0f))
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("tabs").performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("The real callback must stay silent", selections.isEmpty()) }
        // A completed gesture still commits through the same production callback.
        compose.onNodeWithTag("tabs").performTouchInput {
            down(Offset(width / 8f, center.y))
            moveBy(Offset(width / 2f, 0f))
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(2), selections) }
    }
}
