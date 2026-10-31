package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class GlassGroupPressMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `retained horizontal group follows a cancelled action until physical up`() = checkRetained(vertical = false)

    @Test fun `retained vertical group follows a cancelled action until physical cancel`() = checkRetained(vertical = true)

    private fun checkRetained(vertical: Boolean) {
        var motion: GlassGroupPressMotion? = null
        var clicks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            GlassActionGroupMotionHost(enabled = true, modifier = Modifier.testTag("group")) {
                motion = LocalGlassGroupPressMotion.current
                val source = remember { MutableInteractionSource() }
                val action: @androidx.compose.runtime.Composable () -> Unit = {
                    Box(Modifier.size(48.dp).testTag("button")
                        .glassGroupPressMotion(source)
                        .retainGlassButtonGesture()
                        .background(Color.Red)
                        .clickable(source, null, onClick = { clicks++ }))
                    Box(Modifier.size(48.dp).background(Color.Blue))
                }
                if (vertical) Column { action() } else Row { action() }
            }
        }
        val button = compose.onNodeWithTag("button")
        val bounds = button.fetchSemanticsNode().boundsInRoot
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(500)
        button.performTouchInput { moveTo(center + Offset(150f, 100f), 100) }
        compose.mainClock.advanceTimeBy(1800)
        compose.runOnIdle {
            val current = requireNotNull(motion)
            assertTrue("Native cancellation must not release the held group", current.highlight.pressProgress > .99f)
            val frame = glassButtonMotion(if (vertical) Size(48f, 96f) else Size(96f, 48f), 4f, current.highlight)
            assertTrue(frame.x > 1f && frame.y > 1f)
            assertTrue(frame.scaleX > 1f && frame.scaleY > 1f)
        }
        assertEquals(bounds, button.fetchSemanticsNode().boundsInRoot)
        button.performTouchInput { if (vertical) cancel() else up() }
        compose.mainClock.advanceTimeBy(1800)
        compose.runOnIdle {
            assertEquals(0f, requireNotNull(motion).highlight.pressProgress, .001f)
            assertEquals(Offset.Zero, requireNotNull(motion).highlight.offset)
            assertEquals(0, clicks)
        }
    }

    @Test fun `blank group space and disabled actions cannot start the spring`() {
        var motion: GlassGroupPressMotion? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            GlassActionGroupMotionHost(enabled = true) {
                motion = LocalGlassGroupPressMotion.current
                Row {
                    val source = remember { MutableInteractionSource() }
                    Box(Modifier.size(48.dp).testTag("disabled")
                        .glassGroupPressMotion(source, enabled = false)
                        .clickable(source, null, enabled = false, onClick = {}))
                    Box(Modifier.size(48.dp).testTag("blank"))
                }
            }
        }
        for (tag in listOf("disabled", "blank")) {
            compose.onNodeWithTag(tag).performTouchInput { down(center); moveTo(center + Offset(20f, 5f)) }
            compose.mainClock.advanceTimeBy(500)
            compose.runOnIdle { assertEquals(0f, requireNotNull(motion).highlight.pressProgress, 0f) }
            compose.onNodeWithTag(tag).performTouchInput { up() }
        }
    }

    @Test fun `nested material host shares one spring and disabled host exposes none`() {
        var outer: GlassGroupPressMotion? = null
        var inner: GlassGroupPressMotion? = null
        var disabled: GlassGroupPressMotion? = null
        compose.setContent {
            CompositionLocalProvider(LocalGlassGroupPressMotion provides null) {
                GlassActionGroupMotionHost(enabled = true) {
                    outer = LocalGlassGroupPressMotion.current
                    GlassActionGroupMotionHost(enabled = true) { inner = LocalGlassGroupPressMotion.current }
                    GlassActionGroupMotionHost(enabled = false) { disabled = LocalGlassGroupPressMotion.current }
                }
            }
        }
        compose.runOnIdle {
            assertTrue(outer != null && outer === inner)
            assertNull(disabled)
        }
    }
}
