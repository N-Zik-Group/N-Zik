package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.ui.screens.home.HomeTopMiniPlayerAnchorState
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
class MiniPlayerPlacementMotionTest {
    @get:Rule val compose = createComposeRule()

    private fun advanceFrames(count: Int) {
        repeat(count) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    @Test
    fun `top and bottom placements slide both ways without per-frame composition`() {
        val key = mutableStateOf("top")
        val target = mutableFloatStateOf(96f)
        var compositions = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val position = rememberMiniPlayerPlacementMotion(key.value)
            // MainActivity publishes discrete insets and their key in the same composition.
            val anchor = target.floatValue
            SideEffect { compositions++ }
            Box(Modifier.offset { IntOffset(0, position(anchor).roundToInt()) }
                .size(40.dp).testTag("mini"))
        }
        val mini = compose.onNodeWithTag("mini")
        assertEquals(96f, mini.fetchSemanticsNode().boundsInRoot.top, 0f)
        compose.runOnIdle { key.value = "bottom"; target.floatValue = 300f }
        advanceFrames(5)
        val downward = mini.fetchSemanticsNode().boundsInRoot.top
        assertTrue("Intermediate downward placement was $downward", downward in 97f..299f)
        val duringSlide = compositions
        advanceFrames(24)
        assertEquals(300f, mini.fetchSemanticsNode().boundsInRoot.top, 0f)
        assertEquals(duringSlide, compositions)
        compose.runOnIdle { key.value = "top"; target.floatValue = 96f }
        advanceFrames(5)
        val upward = mini.fetchSemanticsNode().boundsInRoot.top
        assertTrue("Intermediate upward placement was $upward", upward in 97f..299f)
        advanceFrames(24)
        assertEquals(96f, mini.fetchSemanticsNode().boundsInRoot.top, 0f)
    }

    @Test
    fun `navigation retarget starts at the currently rendered position`() {
        val motion = MiniPlayerPlacementMotion()
        assertEquals(100f, motion.position("home", 100f, 1f), 0f)
        assertEquals(100f, motion.position("settings", 600f, 0f), 0f)
        val interrupted = motion.position("settings", 600f, .4f)
        assertEquals(300f, interrupted, 0f)
        assertEquals(interrupted, motion.position("album", 160f, 0f), 0f)
        assertEquals(230f, motion.position("album", 160f, .5f), 0f)
        assertEquals(160f, motion.position("album", 160f, 1f), 0f)
    }

    @Test
    fun `scroll and squeeze deltas remain direct during and after a navigation slide`() {
        val motion = MiniPlayerPlacementMotion()
        motion.position("home", 100f, 1f)
        motion.position("album", 300f, 0f)
        val beforeScroll = motion.position("album", 300f, .5f)
        assertEquals(beforeScroll - 37f, motion.position("album", 263f, .5f), 0f)
        assertEquals(beforeScroll - 48f, motion.position("album", 252f, .5f), 0f)
        assertEquals(252f, motion.position("album", 252f, 1f), 0f)
        assertEquals(200f, motion.position("album", 200f, 1f), 0f)
        assertEquals(300f, motion.position("album", 300f, 1f), 0f)
    }

    @Test
    fun `late Home measurement slides atomically while subsequent scroll stays direct`() {
        val height = mutableStateOf(0.dp)
        val scroll = mutableStateOf(0.dp)
        val anchor = HomeTopMiniPlayerAnchorState(
            mutableStateOf<(() -> Dp)?>({ 96.dp }), mutableStateOf(8.dp),
        )
        anchor.attach(Any(), measuredHeight = { height.value }, scrollOffset = { scroll.value }) {
            (height.value + scroll.value).coerceIn(0.dp, height.value)
        }
        var currentProvider: (() -> Dp?)? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val key = anchor.placementKey()
            val topInset = remember(key) { anchor.miniTopInsetProvider() }
            val position = rememberMiniPlayerPlacementMotion(key)
            SideEffect { currentProvider = topInset }
            Box(Modifier.offset {
                IntOffset(0, position(requireNotNull(topInset()).value).roundToInt())
            }.size(40.dp).testTag("mini"))
        }
        val mini = compose.onNodeWithTag("mini")
        assertEquals(96f, mini.fetchSemanticsNode().boundsInRoot.top, 0f)
        compose.runOnIdle {
            height.value = 160.dp
            // Measurement must not leak into the old draw provider before the key recomposes.
            assertEquals(96.dp, requireNotNull(currentProvider).invoke())
        }
        advanceFrames(5)
        val intermediate = mini.fetchSemanticsNode().boundsInRoot.top
        assertTrue("Late measurement intermediate placement was $intermediate", intermediate in 97f..255f)
        advanceFrames(24)
        assertEquals(256f, mini.fetchSemanticsNode().boundsInRoot.top, 0f)
        compose.runOnIdle {
            scroll.value = (-40).dp
            assertEquals(216.dp, requireNotNull(currentProvider).invoke())
        }
        advanceFrames(1)
        assertEquals(216f, mini.fetchSemanticsNode().boundsInRoot.top, 0f)
        compose.runOnIdle {
            height.value = 200.dp
            assertEquals(216.dp, requireNotNull(currentProvider).invoke())
        }
        advanceFrames(5)
        val resized = mini.fetchSemanticsNode().boundsInRoot.top
        assertTrue("Scrolled resize intermediate placement was $resized", resized in 217f..255f)
        advanceFrames(24)
        assertEquals(256f, mini.fetchSemanticsNode().boundsInRoot.top, 0f)
    }

    @Test
    fun `stable placement scroll updates do not start a compose animation`() {
        val target = mutableFloatStateOf(300f)
        var compositions = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val position = rememberMiniPlayerPlacementMotion("home")
            SideEffect { compositions++ }
            Box(Modifier.offset { IntOffset(0, position(target.floatValue).roundToInt()) }
                .size(40.dp).testTag("mini"))
        }
        advanceFrames(25)
        val initialCompositions = compositions
        compose.runOnIdle { target.floatValue = 240f }
        advanceFrames(1)
        assertEquals(240f, compose.onNodeWithTag("mini").fetchSemanticsNode().boundsInRoot.top, 0f)
        assertEquals(initialCompositions, compositions)
    }
}
