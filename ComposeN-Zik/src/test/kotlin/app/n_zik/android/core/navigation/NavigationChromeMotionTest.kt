package app.n_zik.android.core.navigation

import android.app.Application
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NavigationChromeMotionTest {
    @get:Rule val compose = createComposeRule()

    private fun advanceUiFrames(durationMillis: Long) {
        repeat(((durationMillis + 15) / 16).toInt()) {
            compose.runOnUiThread { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    @Test fun sharedGeometryFinishesAt350msWithoutPerFrameRootComposition() {
        val expanded = mutableStateOf(false)
        var compositions = 0
        compose.setContent {
            val padding = animateDpAsState(
                if (expanded.value) 100.dp else 0.dp,
                navigationChromeTween(),
                label = "navigationGeometry",
            )
            SideEffect { compositions++ }
            Box(Modifier.offset { IntOffset(padding.value.roundToPx(), 0) }.size(1.dp).testTag("geometry"))
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { expanded.value = true }
        advanceUiFrames(16)
        advanceUiFrames(16)
        var baseline = 0
        compose.runOnIdle { baseline = compositions }
        advanceUiFrames(176)
        compose.runOnIdle { assertEquals(baseline, compositions) }
        advanceUiFrames(192)
        compose.onNodeWithTag("geometry").assertLeftPositionInRootIsEqualTo(100.dp)
        compose.runOnIdle { assertEquals(baseline, compositions) }
    }
}
