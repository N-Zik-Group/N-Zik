package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class PlayerCoveredPageTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `covered page stops drawing and taps while preserving scroll and remembered composition`() {
        var covered by mutableStateOf(false)
        var drawColor by mutableStateOf(Color.Red)
        var draws = 0
        var clicks = 0
        var mountedCovered = false
        lateinit var remembered: Any
        lateinit var scroll: ScrollState
        compose.setContent {
            Box(Modifier.size(100.dp).testTag("scene")) {
                // No glass settings/provider: native player has the same covered-page policy.
                PlayerCoveredPage(covered) {
                    remembered = remember { Any() }
                    scroll = rememberScrollState(initial = 40)
                    mountedCovered = LocalPlayerCoveredPage.current
                    Column(Modifier.size(100.dp).verticalScroll(scroll)) {
                        Box(Modifier.size(100.dp)
                            .drawBehind { draws++; drawRect(drawColor) }
                            .clickable { clicks++ })
                        Box(Modifier.height(400.dp))
                    }
                }
            }
        }
        fun render() { compose.onNodeWithTag("scene").captureToImage() }
        render()
        val original = remembered
        val originalScroll = scroll
        val scrollPosition = scroll.value
        var before = 0
        compose.runOnIdle { covered = true }
        render()
        compose.runOnIdle {
            before = draws
            assertTrue(mountedCovered)
            drawColor = Color.Blue
        }
        repeat(3) { render() }
        compose.onNodeWithTag("scene").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals("an unplaced Home cannot redraw behind the player", before, draws)
            assertEquals("covered page cannot receive a tap", 0, clicks)
            assertSame(original, remembered)
            assertSame(originalScroll, scroll)
            assertEquals(scrollPosition, scroll.value)
            // isExpanded becomes false at the start of collapse, before exposure.
            covered = false
        }
        render()
        compose.runOnIdle {
            assertFalse(mountedCovered)
            assertTrue("page draws again before the transition exposes it", draws > before)
            assertSame(original, remembered)
            assertEquals(scrollPosition, scroll.value)
        }
        compose.onNodeWithTag("scene").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
