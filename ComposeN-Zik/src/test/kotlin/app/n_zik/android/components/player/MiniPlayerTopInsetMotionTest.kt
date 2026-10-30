package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MiniPlayerTopInsetMotionTest {
    @get:Rule val compose = createComposeRule()

    private fun advanceFrames(count: Int) {
        repeat(count) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    @Test
    fun `top navigation appearance and disappearance animate placement without recomposing every frame`() {
        val target = mutableFloatStateOf(96f)
        var compositions = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val inset = rememberAnimatedMiniPlayerTopInset(target.floatValue)
            SideEffect { compositions++ }
            Box(Modifier.offset { IntOffset(0, inset.value.roundToInt()) }.size(48.dp).testTag("mini"))
        }
        val mini = compose.onNodeWithTag("mini")
        assertEquals(96f, mini.fetchSemanticsNode().boundsInRoot.top, 0f)
        compose.runOnIdle { target.floatValue = 160f }
        advanceFrames(5)
        assertTrue(mini.fetchSemanticsNode().boundsInRoot.top in 97f..159f)
        val compositionsDuringEntrance = compositions
        advanceFrames(24)
        assertEquals(160f, mini.fetchSemanticsNode().boundsInRoot.top, 0f)
        assertEquals(compositionsDuringEntrance, compositions)
        compose.runOnIdle { target.floatValue = 96f }
        advanceFrames(5)
        assertTrue(mini.fetchSemanticsNode().boundsInRoot.top in 97f..159f)
        val compositionsDuringExit = compositions
        advanceFrames(24)
        assertEquals(96f, mini.fetchSemanticsNode().boundsInRoot.top, 0f)
        assertEquals(compositionsDuringExit, compositions)
    }
}
