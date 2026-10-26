package app.n_zik.android.components.ui

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ChipsViewportTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun viewportReachesBothEdgesWhileKeepingInitialContentGutters() {
        lateinit var scroll: androidx.compose.foundation.ScrollState
        composeRule.setContent {
            val state = rememberScrollState()
            SideEffect { scroll = state }
            Box(Modifier.width(300.dp).testTag("outer").padding(horizontal = 16.dp)) {
                Row(Modifier.chipsViewport(16.dp).fillMaxWidth().testTag("viewport")
                    .horizontalScroll(state).padding(horizontal = 16.dp)) {
                    repeat(5) { Box(Modifier.size(80.dp, 40.dp).testTag("chip$it")) }
                }
            }
        }
        val outer = composeRule.onNodeWithTag("outer").fetchSemanticsNode().boundsInRoot
        val viewport = composeRule.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        assertEquals(outer.left, viewport.left, 0.5f)
        assertEquals(outer.right, viewport.right, 0.5f)
        val inset = composeRule.onNodeWithTag("chip0").fetchSemanticsNode().boundsInRoot.left - outer.left
        composeRule.runOnIdle { scroll.dispatchRawDelta(inset) }
        composeRule.waitForIdle()
        assertEquals(outer.left, composeRule.onNodeWithTag("chip0").fetchSemanticsNode().boundsInRoot.left, 0.5f)
        composeRule.runOnIdle { scroll.dispatchRawDelta(scroll.maxValue.toFloat()) }
        composeRule.waitForIdle()
        val last = composeRule.onNodeWithTag("chip4").fetchSemanticsNode().boundsInRoot
        assertEquals(inset, outer.right - last.right, 0.5f)
    }
}
