package app.n_zik.android.components.navigation.header

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
class HeaderBackSlotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun glassBackStaysAtItsAnchorWhileTheTitleMakesRoom() {
        val visible = mutableStateOf(false)
        compose.setContent {
            Row {
                HeaderBackSlot(visible.value, glass = true) {
                    Box(Modifier.padding(end = 12.dp)) {
                        Box(Modifier.size(40.dp).testTag("back"))
                    }
                }
                Box(Modifier.size(40.dp).testTag("logo"))
            }
        }
        val origin = bounds("logo").left
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { visible.value = true; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100)
        val enteringBack = bounds("back")
        val enteringLogo = bounds("logo").left
        compose.mainClock.advanceTimeBy(400)
        val settledBack = bounds("back")
        val settledLogo = bounds("logo").left
        assertEquals(origin, enteringBack.left, .5f)
        assertEquals(settledBack.left, enteringBack.left, .5f)
        assertTrue(enteringLogo > origin && enteringLogo < settledLogo)
        assertTrue("Spacing is outside the Back surface", settledLogo > settledBack.right)
        compose.runOnIdle { visible.value = false; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100)
        assertEquals(settledBack.left, bounds("back").left, .5f)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("back").assertDoesNotExist()
        assertEquals(origin, bounds("logo").left, .5f)
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
}
