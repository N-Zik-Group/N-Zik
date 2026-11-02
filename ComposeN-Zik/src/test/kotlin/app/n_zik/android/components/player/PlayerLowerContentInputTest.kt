package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w500dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerLowerContentInputTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var inputModeManager: InputModeManager

    @Test fun `keyboard focus brings each overflowing action into the lower viewport`() {
        val first = FocusRequester()
        render(first)
        val viewport = compose.onNodeWithTag("input.viewport").getUnclippedBoundsInRoot()
        assertTrue(compose.onNodeWithTag("input.last").getUnclippedBoundsInRoot().top >= viewport.bottom)
        compose.runOnIdle { assertTrue(inputModeManager.requestInputMode(InputMode.Keyboard)) }
        compose.waitForIdle()
        compose.runOnIdle { first.requestFocus() }
        compose.onNodeWithTag("input.first").assertIsFocused()

        compose.onRoot().performKeyInput { pressKey(Key.Tab) }
        compose.onNodeWithTag("input.last").assertIsFocused()
        val lastBounds = compose.onNodeWithTag("input.last").getUnclippedBoundsInRoot()
        assertTrue("Focused last action must clear viewport top: $lastBounds in $viewport", lastBounds.top >= viewport.top)
        assertTrue("Focused last action must fit viewport bottom: $lastBounds in $viewport", lastBounds.bottom <= viewport.bottom)

        compose.onRoot().performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.Tab)
            keyUp(Key.ShiftLeft)
        }
        compose.onNodeWithTag("input.first").assertIsFocused()
        val firstBounds = compose.onNodeWithTag("input.first").getUnclippedBoundsInRoot()
        assertTrue("Focused first action must clear viewport top: $firstBounds in $viewport", firstBounds.top >= viewport.top)
        assertTrue("Focused first action must fit viewport bottom: $firstBounds in $viewport", firstBounds.bottom <= viewport.bottom)
    }

    @Test fun `clipped lower actions cannot receive pointer clicks outside the viewport`() {
        var firstClicks = 0
        var lastClicks = 0
        render(FocusRequester(), onFirst = { firstClicks++ }, onLast = { lastClicks++ })
        val root = compose.onNodeWithTag("input.root")
        val density = compose.density.density
        val pixels = root.captureToImage().toPixelMap()
        assertEquals("The lower action is visually clipped", Color.Black,
            pixels[(160 * density).toInt(), (340 * density).toInt()])

        root.performTouchInput { click(Offset(160 * density, 100 * density)) }
        compose.runOnIdle { assertEquals(1, firstClicks) }
        root.performTouchInput { click(Offset(160 * density, 340 * density)) }
        compose.runOnIdle { assertEquals("Invisible action must not receive a click", 0, lastClicks) }

        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, 1_000f * density)
        }
        root.performTouchInput { click(Offset(160 * density, 180 * density)) }
        compose.runOnIdle { assertEquals("The revealed action remains clickable", 1, lastClicks) }
    }

    private fun render(first: FocusRequester, onFirst: () -> Unit = {}, onLast: () -> Unit = {}) {
        compose.setContent {
            val manager = LocalInputModeManager.current
            SideEffect { inputModeManager = manager }
            Column(Modifier.size(320.dp, 400.dp).background(Color.Black).testTag("input.root")) {
                Spacer(Modifier.height(60.dp))
                Box(Modifier.size(320.dp, 160.dp).testTag("input.viewport")) {
                    PlayerLowerContentViewport {
                        Box(Modifier.size(320.dp, 80.dp).testTag("input.first")
                            .focusRequester(first).background(Color.Blue).clickable(onClick = onFirst))
                        Spacer(Modifier.height(160.dp))
                        Box(Modifier.size(320.dp, 80.dp).testTag("input.last")
                            .background(Color.Red).clickable(onClick = onLast))
                    }
                }
            }
        }
    }
}
