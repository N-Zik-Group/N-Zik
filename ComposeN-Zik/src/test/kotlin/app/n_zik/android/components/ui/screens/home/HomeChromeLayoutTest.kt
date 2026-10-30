package app.n_zik.android.components.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.components.navigation.LocalSideRailContentInsets
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class HomeChromeLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun songsClearLeftRailAndKeepContinuousToolbarScroll() = verifyHome(isGrid = false)

    @Test fun libraryGridsClearRightRailAndKeepContinuousToolbarScroll() = verifyHome(isGrid = true)

    @Test fun topMiniFollowsHomeToolbarWithoutMovingTheListViewport() = verifyHome(isGrid = false, hasTopMini = true)

    private fun advanceFrames(count: Int) {
        repeat(count) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun verifyHome(isGrid: Boolean, hasTopMini: Boolean = false) {
        val toolbarOffset = mutableFloatStateOf(0f)
        val toolbarHeight = mutableIntStateOf(112)
        val miniBase = mutableStateOf(88.dp)
        val miniAnchor = HomeTopMiniPlayerAnchorState(
            mutableStateOf<(() -> Dp)?>(if (hasTopMini) { { miniBase.value } } else null),
            mutableStateOf(8.dp),
        )
        var touchSlop = 0f
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalHomeTopMiniPlayerAnchor provides if (hasTopMini) miniAnchor else null,
                LocalSideRailContentInsets provides WindowInsets(
                    left = if (isGrid) 0 else 64,
                    right = if (isGrid) 64 else 0,
                ),
                LocalPlayerAwareWindowInsets provides WindowInsets(bottom = 160),
            ) {
                val configuration = LocalViewConfiguration.current
                SideEffect { touchSlop = configuration.touchSlop }
                val topInset = remember { { if (hasTopMini) 168.dp else 88.dp } }
                val padding = rememberPageChromeContentPadding(
                    base = PaddingValues(top = 112.dp, bottom = 100.dp),
                    topInsetProvider = topInset,
                )
                val connection = rememberCollapsibleHeaderConnection(112, toolbarOffset, enabled = true)
                Box(Modifier.size(360.dp, 720.dp).testTag("root").nestedScroll(connection)) {
                    if (isGrid) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize().testTag("viewport"),
                            contentPadding = padding,
                        ) {
                            items(60, key = { it }, contentType = { "home-item" }) { HomeItem(it) }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("viewport"),
                            contentPadding = padding,
                        ) {
                            items(60, key = { it }, contentType = { "home-item" }) { HomeItem(it) }
                        }
                    }
                    HomeCollapsingHeader(
                        headerOffset = toolbarOffset,
                        headerHeight = toolbarHeight,
                        topContentInset = topInset,
                        backgroundColor = Color.Transparent,
                        shape = RoundedCornerShape(0.dp),
                    ) {
                        Column {
                            Box(Modifier.fillMaxWidth().height(56.dp).testTag("title-count"))
                            Box(Modifier.fillMaxWidth().height(56.dp).testTag("actions"))
                        }
                    }
                    if (hasTopMini) {
                        Box(Modifier.offset { IntOffset(0, (miniAnchor.miniTopInset() ?: miniBase.value).roundToPx()) }
                            .fillMaxWidth().height(72.dp).testTag("mini"))
                    }
                }
            }
        }
        val root = compose.onNodeWithTag("root")
        val viewport = compose.onNodeWithTag("viewport")
        val initialBounds = viewport.fetchSemanticsNode().boundsInRoot
        assertEquals(root.fetchSemanticsNode().boundsInRoot, initialBounds)
        val title = compose.onNodeWithTag("title-count").fetchSemanticsNode().boundsInRoot
        assertEquals(initialBounds.left + if (isGrid) 0f else 64f, title.left, 0f)
        assertEquals(initialBounds.right - if (isGrid) 64f else 0f, title.right, 0f)
        assertEquals(initialBounds.top + if (hasTopMini) 80f else 88f, title.top, 0f)
        if (hasTopMini) assertEquals(initialBounds.top + 200f, compose.onNodeWithTag("mini").fetchSemanticsNode().boundsInRoot.top, 0f)
        val firstItem = compose.onNodeWithTag("item0").fetchSemanticsNode().boundsInRoot
        assertEquals(title.left, firstItem.left, 0f)
        assertEquals(initialBounds.top + if (hasTopMini) 280f else 200f, firstItem.top, 0f)
        viewport.performScrollToIndex(59)
        assertTrue(compose.onNodeWithTag("item59").fetchSemanticsNode().boundsInRoot.bottom <= initialBounds.bottom - 160f)
        viewport.performScrollToIndex(0)

        compose.mainClock.autoAdvance = false
        var fingerY = 650f
        fun itemY(): Float = compose.onNodeWithTag("item4").fetchSemanticsNode().positionInRoot.y
        try {
            root.performTouchInput {
                down(Offset(180f, fingerY))
                moveTo(Offset(180f, fingerY - touchSlop - 20f))
            }
            fingerY -= touchSlop + 20f
            advanceFrames(2)
            repeat(4) {
                val before = itemY()
                fingerY -= 30f
                root.performTouchInput { moveTo(Offset(180f, fingerY)) }
                advanceFrames(2)
                assertEquals(-30f, itemY() - before, 2f)
            }
            assertEquals(-112f, toolbarOffset.floatValue, 0f)
            assertEquals(initialBounds, viewport.fetchSemanticsNode().boundsInRoot)
            val stationaryY = itemY()
            if (hasTopMini) {
                assertEquals(initialBounds.top + 88f, compose.onNodeWithTag("mini").fetchSemanticsNode().boundsInRoot.top, 0f)
                compose.runOnIdle { miniBase.value = 32.dp }
                advanceFrames(2)
                assertEquals(initialBounds.top + 32f, compose.onNodeWithTag("mini").fetchSemanticsNode().boundsInRoot.top, 0f)
                assertEquals(stationaryY, itemY(), 0f)
                compose.runOnIdle { miniBase.value = 88.dp }
                advanceFrames(2)
            }
            advanceFrames(24)
            assertEquals(stationaryY, itemY(), 0f)
            repeat(4) {
                val before = itemY()
                fingerY += 30f
                root.performTouchInput { moveTo(Offset(180f, fingerY)) }
                advanceFrames(2)
                assertEquals(30f, itemY() - before, 2f)
            }
            assertEquals(0f, toolbarOffset.floatValue, 0f)
            assertEquals(initialBounds, viewport.fetchSemanticsNode().boundsInRoot)
        } finally {
            root.performTouchInput { advanceEventTime(100); up() }
            advanceFrames(4)
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
        }
    }

    @Composable
    private fun HomeItem(index: Int) {
        Box(Modifier.fillMaxWidth().height(48.dp).testTag("item$index"))
    }
}
