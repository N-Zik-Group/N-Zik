package app.n_zik.android.components.navigation

import kotlin.math.roundToInt

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w400dp-h800dp")
class HomeScrollGestureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun songsFollowTheFingerInPortrait() = verifyGesture(grid = false, landscape = false)

    @Test fun libraryGridFollowsTheFingerInPortrait() = verifyGesture(grid = true, landscape = false)

    @Test
    @Config(qualifiers = "w800dp-h400dp-land")
    fun songsFollowTheFingerInLandscape() = verifyGesture(grid = false, landscape = true)

    @Test
    @Config(qualifiers = "w800dp-h400dp-land")
    fun libraryGridFollowsTheFingerInLandscape() = verifyGesture(grid = true, landscape = true)

    private fun advanceFrames() {
        repeat(2) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun verifyGesture(grid: Boolean, landscape: Boolean) {
        val headerHeight = 120f
        val headerOffset = mutableFloatStateOf(0f)
        var touchSlop = 0f
        val connection = object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Home's toolbar follows the shorter root header's progress, without consuming scroll.
                headerOffset.floatValue = (headerOffset.floatValue + consumed.y * 2f).coerceIn(-headerHeight, 0f)
                return Offset.Zero
            }
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val viewConfiguration = LocalViewConfiguration.current
                SideEffect { touchSlop = viewConfiguration.touchSlop }
                val padding = remember {
                    PageChromeContentPadding(
                        base = PaddingValues(0.dp),
                        topInset = { 24.dp },
                        railInsets = PaddingValues(0.dp),
                        playerInsets = PaddingValues(0.dp),
                        extraTop = { headerHeight.dp },
                    )
                }
                Box(
                    Modifier.size(if (landscape) 720.dp else 360.dp, if (landscape) 360.dp else 720.dp)
                        .testTag("scene")
                        .nestedScroll(connection)
                ) {
                    if (grid) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize().testTag("scroll"),
                            contentPadding = padding,
                        ) {
                            items(100) { index ->
                                Box(Modifier.fillMaxWidth().height(40.dp).testTag("item$index"))
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("scroll"),
                            contentPadding = padding,
                        ) {
                            items(100) { index ->
                                Box(Modifier.fillMaxWidth().height(40.dp).testTag("item$index"))
                            }
                        }
                    }
                    Box(
                        Modifier.offset { IntOffset(0, 24 + headerOffset.floatValue.roundToInt()) }
                            .fillMaxWidth().height(headerHeight.dp).testTag("toolbar")
                    )
                }
            }
        }
        val scene = compose.onNodeWithTag("scene")
        val rowTag = if (grid) "item2" else "item1"
        fun rowY() = compose.onNodeWithTag(rowTag).fetchSemanticsNode().positionInRoot.y
        val viewport = compose.onNodeWithTag("scroll").fetchSemanticsNode().boundsInRoot
        assertEquals("The fixture must use the requested orientation", landscape, viewport.width > viewport.height)
        var fingerY = viewport.height - 20f
        var pointerDown = false
        try {
            val primeDistance = compose.runOnIdle { touchSlop + 12f }
            scene.performTouchInput {
                down(Offset(180f, fingerY))
                moveTo(Offset(180f, fingerY - primeDistance))
            }
            pointerDown = true
            fingerY -= primeDistance
            advanceFrames()
            repeat(3) {
                val before = rowY()
                fingerY -= 12f
                scene.performTouchInput { moveTo(Offset(180f, fingerY)) }
                advanceFrames()
                assertEquals("A 12px drag must move the row by 12px while Home collapses", -12f, rowY() - before, 1f)
            }
            compose.runOnIdle {
                assertTrue("The gesture must exercise a partially collapsed toolbar", headerOffset.floatValue in -119f..-1f)
            }
            val toolbarBefore = compose.onNodeWithTag("toolbar").fetchSemanticsNode().positionInRoot.y
            // Cancel prevents a list fling, isolating the toolbar's remaining snap travel.
            scene.performTouchInput { cancel() }
            pointerDown = false
            advanceFrames()
            val stationaryRow = rowY()
            val snapStart = compose.runOnIdle { headerOffset.floatValue }
            repeat(4) { step ->
                compose.runOnIdle { headerOffset.floatValue = snapStart + (-headerHeight - snapStart) * (step + 1) / 4f }
                advanceFrames()
                assertEquals("Header snap frames must not move the list after the gesture", stationaryRow, rowY(), 0f)
            }
            assertTrue(compose.onNodeWithTag("toolbar").fetchSemanticsNode().positionInRoot.y < toolbarBefore)
            assertEquals(viewport, compose.onNodeWithTag("scroll").fetchSemanticsNode().boundsInRoot)
        } finally {
            try {
                if (pointerDown) scene.performTouchInput { cancel() }
                advanceFrames()
            } finally {
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
            }
        }
    }
}
