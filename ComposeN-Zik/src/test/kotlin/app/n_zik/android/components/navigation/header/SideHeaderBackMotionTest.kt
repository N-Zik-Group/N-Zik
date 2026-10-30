package app.n_zik.android.components.navigation.header

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
class SideHeaderBackMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun backMovesThePersistentActionsGraduallyInBothDirections() {
        val visible = mutableStateOf(false)
        var actionInstances = 0
        compose.setContent {
            Column {
                SideHeaderBackSlot(visible.value) { Box(Modifier.size(48.dp)) }
                DisposableEffect(Unit) {
                    actionInstances++
                    onDispose { }
                }
                Box(Modifier.size(48.dp, 96.dp).testTag("actions"))
            }
        }
        val resting = actionTop()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { visible.value = true; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100)
        val entering = actionTop()
        compose.mainClock.advanceTimeBy(400)
        val expanded = actionTop()
        assertTrue("actions move instead of jumping", entering > resting && entering < expanded)

        compose.runOnIdle { visible.value = false; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100)
        val leaving = actionTop()
        assertTrue("Back removal must also move actions", leaving > resting && leaving < expanded)
        compose.mainClock.advanceTimeBy(400)
        assertEquals(resting, actionTop(), .5f)
        assertEquals("profile/search must not be remounted", 1, actionInstances)
    }

    private fun actionTop() = compose.onNodeWithTag("actions").fetchSemanticsNode().boundsInRoot.top
}
