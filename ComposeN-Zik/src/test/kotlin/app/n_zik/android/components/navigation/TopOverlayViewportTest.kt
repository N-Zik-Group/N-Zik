package app.n_zik.android.components.navigation

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
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
import app.n_zik.android.components.player.topMiniPlayerStackReservePx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TopOverlayViewportTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeTopOverlayRevealsTheActualPage() = verifyOverlay(59f)
    @Test fun glassHeightTopOverlayRevealsTheActualPage() = verifyOverlay(64f)

    private fun verifyOverlay(navHeight: Float) {
        val fold = mutableFloatStateOf(0f)
        var pageClicks = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(300.dp).background(Color.Black).testTag("scene")) {
                    Box(Modifier.topOverlayViewport {
                        val miniReserve = topMiniPlayerStackReservePx(81f, 81f, navHeight, navHeight,
                            keepVisible = true, attachedMiniProgress = fold.floatValue)
                        topOverlayClearancePx(navHeight, miniReserve, fold.floatValue)
                    }.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(Color.Red)
                            .clickable { pageClicks++ }.testTag("page"))
                    }
                    Box(Modifier.fillMaxWidth().height(navHeight.dp)
                        .background(Color.Gray, RoundedCornerShape(24.dp)).testTag("nav"))
                }
            }
        }
        val scene = compose.onNodeWithTag("scene")
        val page = compose.onNodeWithTag("page")
        val origin = scene.fetchSemanticsNode().boundsInRoot.top
        assertEquals(navHeight + 81f, page.fetchSemanticsNode().boundsInRoot.top - origin, 0f)
        compose.runOnIdle { fold.floatValue = .5f }
        assertEquals(kotlin.math.round((navHeight + 40.5f) * .5f),
            page.fetchSemanticsNode().boundsInRoot.top - origin, 0f)
        compose.runOnIdle { fold.floatValue = 1f }
        assertEquals(origin, page.fetchSemanticsNode().boundsInRoot.top, 0f)
        assertEquals(300f, page.fetchSemanticsNode().boundsInRoot.height, 0f)
        val pixels = scene.captureToImage().toPixelMap()
        assertTrue(pixels[1, 1].red > .95f && pixels[1, 1].green < .05f)
        assertTrue(pixels[150, 20].green > .4f)
        scene.performTouchInput { click(Offset(1f, 1f)) }
        compose.runOnIdle { assertEquals(1, pageClicks) }
        compose.runOnIdle { fold.floatValue = 0f }
        assertEquals(navHeight + 81f, page.fetchSemanticsNode().boundsInRoot.top - origin, 0f)
    }

    @Test fun standaloneTopMiniAlsoReleasesItsViewportReserve() {
        assertEquals(81f, topOverlayClearancePx(0f, 81f, 0f), 0f)
        assertEquals(40.5f, topOverlayClearancePx(0f, 81f, .5f), 0f)
        assertEquals(0f, topOverlayClearancePx(0f, 81f, 1f), 0f)
    }

    @Test fun fullTopTabsKeepTheirClearanceWhileTheBottomMiniCompresses() {
        for (navHeight in listOf(59f, 64f)) {
            val clearance = topOverlayContentClearancePx(navHeight, 0f)
            assertEquals(navHeight, clearance, 0f)
            val padding = TopOverlayContentPadding({ clearance.dp }, PaddingValues(top = 96.dp, bottom = 32.dp))
            assertEquals((96f + navHeight).dp, padding.calculateTopPadding())
            assertEquals(32.dp, padding.calculateBottomPadding())
        }
    }

    @Test fun topTabsAndTopMiniKeepTheirExpandedClearanceInsideTheScroll() {
        for (navHeight in listOf(59f, 64f)) {
            assertEquals(navHeight + 81f, topOverlayContentClearancePx(navHeight, 81f), 0f)
        }
    }

    @Test fun standaloneTopMiniKeepsItsExpandedClearanceInsideTheScroll() {
        assertEquals(81f, topOverlayContentClearancePx(0f, 81f), 0f)
    }

    @Test fun fixedHeaderAndStatusClearanceStayInsideScrollPaddingForEveryTopStack() {
        for (navHeight in listOf(0f, 59f, 64f)) {
            for (miniReserve in listOf(0f, 81f)) {
                val inset = 88.dp + topOverlayContentClearancePx(navHeight, miniReserve).dp
                val padding = TopOverlayContentPadding({ inset }, PaddingValues(top = 24.dp, bottom = 32.dp))
                assertEquals((24f + 88f + navHeight + miniReserve).dp, padding.calculateTopPadding())
                assertEquals(32.dp, padding.calculateBottomPadding())
            }
        }
    }

    @Test fun scrollingInsetKeepsFirstRowClearThenRevealsContentBehindMini() {
        val inset = mutableFloatStateOf(140f)
        var pageCompositions = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                SideEffect { pageCompositions++ }
                val padding = remember { TopOverlayContentPadding({ inset.floatValue.dp }) }
                Box(Modifier.size(300.dp, 400.dp).background(Color.Black).testTag("scroll-scene")) {
                    LazyColumn(Modifier.fillMaxSize().testTag("scroll-list"), contentPadding = padding) {
                        items(30) { index ->
                            Box(Modifier.fillMaxWidth().height(60.dp).background(Color.Red).testTag("row-$index"))
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(59.dp).background(Color.Gray))
                    Box(Modifier.padding(top = 67.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth().height(65.dp).background(Color.Green, RoundedCornerShape(24.dp)))
                }
            }
        }
        val scene = compose.onNodeWithTag("scroll-scene")
        val list = compose.onNodeWithTag("scroll-list")
        val initialBounds = list.fetchSemanticsNode().boundsInRoot
        assertEquals(initialBounds.top + 140f, compose.onNodeWithTag("row-0").fetchSemanticsNode().boundsInRoot.top, 0f)
        val before = scene.captureToImage().toPixelMap()
        assertTrue(before[1, 85].red < .05f)
        val compositionsBefore = pageCompositions
        compose.runOnIdle { inset.floatValue = 100f }
        assertEquals(initialBounds.top + 100f, compose.onNodeWithTag("row-0").fetchSemanticsNode().boundsInRoot.top, 0f)
        assertEquals(initialBounds, list.fetchSemanticsNode().boundsInRoot)
        assertEquals(compositionsBefore, pageCompositions)
        list.performScrollToIndex(5)
        val after = scene.captureToImage().toPixelMap()
        assertTrue(after[1, 85].red > .95f && after[1, 85].green < .05f)
        assertTrue(after[150, 85].green > .95f)
        assertEquals(initialBounds, list.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun scrollingInsetPreservesExistingPagePadding() {
        val padding = TopOverlayContentPadding({ 81.dp }, PaddingValues(start = 12.dp, top = 96.dp, end = 24.dp, bottom = 32.dp))
        assertEquals(177.dp, padding.calculateTopPadding())
        assertEquals(32.dp, padding.calculateBottomPadding())
        assertEquals(12.dp, padding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(24.dp, padding.calculateRightPadding(LayoutDirection.Ltr))
        assertEquals(24.dp, padding.calculateLeftPadding(LayoutDirection.Rtl))
    }

}
