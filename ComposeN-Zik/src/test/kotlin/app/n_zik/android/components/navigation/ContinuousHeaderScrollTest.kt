package app.n_zik.android.components.navigation

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.n_zik.android.components.navigation.dock.FloatingDockState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class ContinuousHeaderScrollTest {
    @get:Rule val compose = createComposeRule()

    private fun advanceFrames(count: Int) {
        repeat(count) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    @Test fun fingerDisplacementMatchesRowsBehindMovingHeaderWithoutTopTabs() {
        assertHeaderDisplacement(hasTopTabs = false)
    }

    @Test fun fingerDisplacementMatchesRowsBehindMovingHeaderWithTopTabs() {
        assertHeaderDisplacement(hasTopTabs = true)
    }

    private fun assertHeaderDisplacement(hasTopTabs: Boolean) {
        val list = LazyListState()
        val headerOffset = mutableFloatStateOf(0f)
        val dock = FloatingDockState()
        val connection = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                headerOffset.floatValue = (headerOffset.floatValue + available.y).coerceIn(-64f, 0f)
                dock.onScroll(available.y, 50f)
                return Offset.Zero
            }
        }
        var touchSlopPx = 0f
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val viewConfiguration = LocalViewConfiguration.current
                SideEffect { touchSlopPx = viewConfiguration.touchSlop }
                val fold = animateFloatAsState(dock.foldTarget, tween(320), label = "displacementMiniFold")
                val padding = remember {
                    TopOverlayContentPadding({ 88.dp + topOverlayContentClearancePx(if (hasTopTabs) 64f else 0f, 81f).dp })
                }
                Box(Modifier.size(360.dp, 720.dp).testTag("displacementRoot").nestedScroll(connection)) {
                    NavHost(rememberNavController(), startDestination = "home", modifier = Modifier.fillMaxSize()) {
                        composable("home") {
                            LazyColumn(Modifier.fillMaxSize().testTag("displacementList"), state = list, contentPadding = padding) {
                                items(100) { index ->
                                    Box(Modifier.fillMaxWidth().height(40.dp).testTag("row$index"))
                                }
                            }
                        }
                    }
                    Box(Modifier.offset { IntOffset(0, headerOffset.floatValue.roundToInt()) }
                        .fillMaxWidth().height(88.dp).testTag("movingHeader"))
                    if (hasTopTabs) Box(Modifier.offset {
                        IntOffset(0, appHeaderOverlayTopPx(64f, 24f, headerOffset.floatValue).roundToInt())
                    }.fillMaxWidth().height(64.dp).testTag("movingTopTabs"))
                    Box(Modifier.offset {
                        IntOffset(16, (appHeaderOverlayTopPx(64f, 24f, headerOffset.floatValue) + if (hasTopTabs) 64f else 0f).roundToInt())
                    }.fillMaxWidth().height((65f - 20f * fold.value).dp).testTag("squeezingTopMini"))
                }
            }
        }
        val root = compose.onNodeWithTag("displacementRoot")
        fun rowY(): Float = compose.onNodeWithTag("row10").fetchSemanticsNode().positionInRoot.y
        val initialViewport = compose.onNodeWithTag("displacementList").fetchSemanticsNode().boundsInRoot
        val initialTitleY = compose.onNodeWithTag("row0").fetchSemanticsNode().positionInRoot.y
        assertEquals(initialViewport.top + 88f + 81f + if (hasTopTabs) 64f else 0f, initialTitleY, 0f)
        // Establish dragging past touch slop, then compare actual rendered row movement
        // with each pointer delta. The header overlay moves without resizing the scroll owner.
        var fingerY = 650f
        var gestureFailure: Throwable? = null
        try {
            // LocalDensity controls layout; the host ViewConfiguration still supplies
            // physical touch slop. Leave only 20px of actual page travel before measuring.
            val primeDistance = compose.runOnIdle { touchSlopPx + 20f }
            root.performTouchInput { down(Offset(180f, fingerY)); moveTo(Offset(180f, fingerY - primeDistance)) }
            fingerY -= primeDistance
            advanceFrames(2)
            compose.runOnIdle {
                assertTrue("Displacement checks require an established drag", list.isScrollInProgress)
            }
            repeat(5) {
                val before = rowY()
                fingerY -= 30f
                root.performTouchInput { moveTo(Offset(180f, fingerY)) }
                advanceFrames(2)
                assertEquals("Rows must follow an upward finger while the overlay hides", -30f, rowY() - before, 2f)
            }
            assertEquals(initialViewport, compose.onNodeWithTag("displacementList").fetchSemanticsNode().boundsInRoot)
            compose.runOnIdle { assertEquals(-64f, headerOffset.floatValue, 0f) }
            assertEquals(initialViewport.top - 64f, compose.onNodeWithTag("movingHeader").fetchSemanticsNode().positionInRoot.y, 0f)
            if (hasTopTabs) assertEquals(initialViewport.top + 24f,
                compose.onNodeWithTag("movingTopTabs").fetchSemanticsNode().positionInRoot.y, 0f)
            compose.runOnIdle { assertEquals(1f, dock.foldTarget, 0f) }
            val squeezingHeight = compose.onNodeWithTag("squeezingTopMini").fetchSemanticsNode().boundsInRoot.height
            assertTrue("Exact row displacement must be checked while Top mini is animating", squeezingHeight > 45f && squeezingHeight < 65f)
            val beforeStationaryFold = rowY()
            advanceFrames(24)
            assertEquals("Rows must not move while the same finger is stationary and Top mini finishes folding", beforeStationaryFold, rowY(), 0f)
            repeat(5) {
                val before = rowY()
                fingerY += 30f
                root.performTouchInput { moveTo(Offset(180f, fingerY)) }
                advanceFrames(2)
                assertEquals("Rows must follow the same finger on reverse", 30f, rowY() - before, 2f)
            }
            assertEquals(initialViewport, compose.onNodeWithTag("displacementList").fetchSemanticsNode().boundsInRoot)
            compose.runOnIdle { assertEquals(0f, headerOffset.floatValue, 0f) }
            compose.runOnIdle { assertEquals(0f, dock.foldTarget, 0f) }
            assertEquals(initialViewport.top, compose.onNodeWithTag("movingHeader").fetchSemanticsNode().positionInRoot.y, 0f)
            if (hasTopTabs) assertEquals(initialViewport.top + 88f,
                compose.onNodeWithTag("movingTopTabs").fetchSemanticsNode().positionInRoot.y, 0f)
            val beforeStationaryUnfold = rowY()
            advanceFrames(24)
            assertEquals("Rows must stay still while Top mini finishes unfolding without lifting", beforeStationaryUnfold, rowY(), 0f)
        } catch (failure: Throwable) {
            gestureFailure = failure
            throw failure
        } finally {
            try {
                // Complete the drag while nested-scroll dispatchers are still attached.
                // A frozen frame clock otherwise defers drag cancellation until activity teardown.
                try {
                    root.performTouchInput { advanceEventTime(100); up() }
                    advanceFrames(4)
                } finally {
                    compose.mainClock.autoAdvance = true
                    compose.waitForIdle()
                }
            } catch (cleanupFailure: Throwable) {
                val originalFailure = gestureFailure
                if (originalFailure != null) originalFailure.addSuppressed(cleanupFailure)
                else throw cleanupFailure
            }
        }
    }

    @Test fun topOverlayAnchorStopsAtStatusBarWhileContentClearanceRemainsExpanded() {
        assertEquals(88f, appHeaderOverlayTopPx(64f, 24f, 0f), 0f)
        assertEquals(56f, appHeaderOverlayTopPx(64f, 24f, -32f), 0f)
        assertEquals(24f, appHeaderOverlayTopPx(64f, 24f, -64f), 0f)
        assertEquals(24f, appHeaderOverlayTopPx(64f, 24f, -160f), 0f)
        assertEquals(88f, appHeaderOverlayTopPx(64f, 24f, 100f), 0f)
        val padding = TopOverlayContentPadding({ 88.dp })
        assertEquals(88.dp, padding.calculateTopPadding())
    }

    @Test fun oneFingerKeepsScrollingBehindMovingHeaderAcrossMiniFoldThenReverses() {
        val dock = FloatingDockState()
        val list = LazyListState()
        val headerOffset = mutableFloatStateOf(0f)
        val connection = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Both overlays observe the drag while leaving its entire delta to the list.
                dock.onScroll(available.y, 50f)
                headerOffset.floatValue = (headerOffset.floatValue + available.y).coerceIn(-64f, 0f)
                return Offset.Zero
            }
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val fold = animateFloatAsState(dock.foldTarget, tween(320), label = "miniFold")
                val padding = remember {
                    TopOverlayContentPadding({ 88.dp + topOverlayContentClearancePx(64f, 81f).dp })
                }
                Box(Modifier.size(360.dp, 720.dp).testTag("gestureRoot").nestedScroll(connection)) {
                    NavHost(rememberNavController(), startDestination = "home", modifier = Modifier.fillMaxSize()) {
                        composable("home") {
                            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = padding) {
                                items(100) { Box(Modifier.fillMaxWidth().height(40.dp)) }
                            }
                        }
                    }
                    Box(Modifier.offset { IntOffset(0, headerOffset.floatValue.roundToInt()) }
                        .fillMaxWidth().height(88.dp).testTag("foldMovingHeader"))
                }
            }
        }
        val root = compose.onNodeWithTag("gestureRoot")
        fun position(): Int = list.firstVisibleItemIndex * 40 + list.firstVisibleItemScrollOffset

        // All batches belong to one DOWN; layout and fold animations run while the finger stays down.
        root.performTouchInput { down(Offset(180f, 650f)); moveTo(Offset(180f, 540f)) }
        advanceFrames(24)
        compose.runOnIdle {
            assertEquals(-64f, headerOffset.floatValue, 0f)
            assertEquals(1f, dock.foldTarget, 0f)
            assertTrue(list.isScrollInProgress)
        }
        val afterThreshold = compose.runOnIdle { position() }
        root.performTouchInput { moveTo(Offset(180f, 390f)) }
        advanceFrames(2)
        val afterMoreScroll = compose.runOnIdle { position() }
        assertTrue("List must advance without another DOWN after both thresholds", afterMoreScroll > afterThreshold)

        root.performTouchInput { moveTo(Offset(180f, 550f)) }
        advanceFrames(24)
        compose.runOnIdle {
            assertEquals(0f, headerOffset.floatValue, 0f)
            assertEquals(0f, dock.foldTarget, 0f)
            assertTrue(list.isScrollInProgress)
            assertTrue("Same finger must also scroll back", position() < afterMoreScroll)
        }
        root.performTouchInput { up() }
        advanceFrames(4)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }
}
