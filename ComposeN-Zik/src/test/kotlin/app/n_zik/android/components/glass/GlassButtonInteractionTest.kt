package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GlassButtonInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `observing a press preserves one click and dragging outside cancels the click`() {
        var clicks = 0
        compose.setContent {
            val scope = rememberCoroutineScope()
            val highlight = remember(scope) { InteractiveHighlight(scope) }
            Box(
                Modifier.size(48.dp).testTag("glass.button")
                    .clickable { clicks++ }
                    .then(highlight.modifier)
                    .then(highlight.gestureModifier),
            )
        }
        compose.onNodeWithTag("glass.button").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.onNodeWithTag("glass.button").performTouchInput {
            down(center)
            moveTo(center + Offset(200f, 0f))
            up()
        }
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
