package app.n_zik.android.components.navigation

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SideNavigationOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun leftRailOverlaysFullWidthPageAndTracksHeaderDuringMeasure() = verifyRail(false)
    @Test fun rightRailOverlaysFullWidthPageAndTracksHeaderDuringMeasure() = verifyRail(true)

    @Test fun railContentPaddingKeepsBothEndpointsReachableWithoutNarrowingTheRow() {
        val side = mutableIntStateOf(0)
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalSideRailContentInsets provides WindowInsets(
                    left = if (side.intValue == 1) 64 else 0,
                    right = if (side.intValue == 2) 64 else 0,
                ),
            ) {
                LazyRow(Modifier.size(300.dp, 80.dp).testTag("scroll-row"),
                    contentPadding = LocalSideRailContentInsets.current.asPaddingValues()) {
                    items(10) { index -> Box(Modifier.size(80.dp).testTag("item-$index")) }
                }
            }
        }
        val row = compose.onNodeWithTag("scroll-row")
        val initialBounds = row.fetchSemanticsNode().boundsInRoot
        for (position in 0..2) {
            compose.runOnIdle { side.intValue = position }
            row.performScrollToIndex(0)
            val first = compose.onNodeWithTag("item-0").fetchSemanticsNode().boundsInRoot
            assertTrue(first.left >= initialBounds.left + if (position == 1) 64f else 0f)
            row.performScrollToIndex(9)
            val last = compose.onNodeWithTag("item-9").fetchSemanticsNode().boundsInRoot
            assertTrue(last.right <= initialBounds.right - if (position == 2) 64f else 0f)
            assertEquals(initialBounds, row.fetchSemanticsNode().boundsInRoot)
        }
    }

    private fun verifyRail(onRight: Boolean) {
        val top = mutableIntStateOf(88)
        val bottom = mutableIntStateOf(40)
        var compositions = 0
        var pageClicks = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                SideEffect { compositions++ }
                Box(Modifier.size(300.dp, 500.dp).testTag("scene")) {
                    Box(Modifier.fillMaxSize().background(Color.Red)
                        .clickable { pageClicks++ }.testTag("page"))
                    SideNavigationOverlay(
                        onRight = onRight,
                        railWidthPx = 64,
                        topInsetPx = { top.intValue },
                        bottomInsetPx = { bottom.intValue },
                        leftInsetPx = { 12 },
                        rightInsetPx = { 20 },
                        modifier = Modifier.fillMaxSize().testTag("overlay"),
                    ) {
                        Box(Modifier.fillMaxSize().background(Color.Gray).testTag("rail")) {
                            Box(Modifier.align(Alignment.Center).size(64.dp, 200.dp).testTag("tabs"))
                        }
                    }
                }
            }
        }
        val scene = compose.onNodeWithTag("scene")
        val sceneBounds = scene.fetchSemanticsNode().boundsInRoot
        val page = compose.onNodeWithTag("page")
        val initialPage = page.fetchSemanticsNode().boundsInRoot
        val rail = compose.onNodeWithTag("rail")
        assertEquals(sceneBounds, initialPage)
        assertEquals(sceneBounds, compose.onNodeWithTag("overlay").fetchSemanticsNode().boundsInRoot)
        assertEquals(sceneBounds.left + if (onRight) 216f else 12f, rail.fetchSemanticsNode().boundsInRoot.left, 0f)
        assertEquals(sceneBounds.top + 88f, rail.fetchSemanticsNode().boundsInRoot.top, 0f)
        assertEquals(372f, rail.fetchSemanticsNode().boundsInRoot.height, 0f)
        val tabs = compose.onNodeWithTag("tabs")
        assertEquals(sceneBounds.top + 174f, tabs.fetchSemanticsNode().boundsInRoot.top, 0f)
        val count = compositions
        compose.runOnIdle { top.intValue = 24; bottom.intValue = 80 }
        assertEquals(sceneBounds.top + 24f, rail.fetchSemanticsNode().boundsInRoot.top, 0f)
        assertEquals(396f, rail.fetchSemanticsNode().boundsInRoot.height, 0f)
        assertEquals(sceneBounds.top + 122f, tabs.fetchSemanticsNode().boundsInRoot.top, 0f)
        assertEquals(initialPage, page.fetchSemanticsNode().boundsInRoot)
        assertEquals(count, compositions)
        val pixels = scene.captureToImage().toPixelMap()
        val railX = if (onRight) 240 else 40
        assertTrue(pixels[railX, 20].red > .95f)
        scene.performTouchInput { click(Offset(railX.toFloat(), 20f)) }
        compose.runOnIdle { assertEquals(1, pageClicks) }
        compose.runOnIdle { top.intValue = 260; bottom.intValue = 100 }
        assertEquals(sceneBounds.top + 260f, rail.fetchSemanticsNode().boundsInRoot.top, 0f)
        assertEquals(140f, rail.fetchSemanticsNode().boundsInRoot.height, 0f)
    }
}
