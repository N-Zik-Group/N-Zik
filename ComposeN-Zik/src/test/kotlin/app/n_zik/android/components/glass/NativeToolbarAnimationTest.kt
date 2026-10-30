package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeToolbarAnimationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `fresh native button instances keep one toolbar and use latest callbacks`() {
        val generation = mutableStateOf(0)
        var clickedGeneration = -1
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val value = generation.value
            val controls = List(2) { index -> object : Button {
                @Composable override fun ToolBarButton() {
                    Box(Modifier.size(40.dp).testTag("native.$index")
                        .clickable { clickedGeneration = value })
                }
            } }
            Box(Modifier.width(200.dp)) { TabToolBar.Buttons(controls) }
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        repeat(3) { value ->
            compose.runOnIdle { generation.value = value + 1 }
            compose.mainClock.advanceTimeByFrame()
            // An identity based transition retains both outgoing and incoming controls.
            compose.onAllNodes(hasClickAction()).assertCountEquals(2)
            compose.onNodeWithTag("native.0").performClick()
            compose.runOnIdle { assertEquals(value + 1, clickedGeneration) }
        }
    }
}
