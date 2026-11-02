package app.n_zik.android.components.player

import android.app.Application
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.ui.screens.player.LaunchedEffectScrollToPage
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.components.player.slide.slideSettledPageEffect
import io.mockk.every
import io.mockk.mockk
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerCarouselMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w800dp-h1200dp")
    fun `mounted portrait spacing changes preserve the selected cover center`() =
        verifyLiveSpacingChanges(500, 800)

    @Test
    @Config(qualifiers = "w800dp-h1200dp")
    fun `mounted landscape spacing changes preserve the selected cover center`() =
        verifyLiveSpacingChanges(300, 300)

    private fun verifyLiveSpacingChanges(height: Int, referenceHeight: Int) {
        val setting = mutableStateOf(0)
        val activePages = mutableSetOf<Int>()
        val playbackRequests = mutableListOf<Int>()
        lateinit var pager: PagerState
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val spacing by animatePlayerCarouselDp(
                (setting.value * .01f * referenceHeight - 270).dp, 400.dp, "liveSpacing")
            val geometry = animatePlayerCarouselGeometry(400.dp, height.dp, 90.dp, spacing, true)
            pager = rememberPagerState(initialPage = 500) { 1_000 }
            pager.slideSettledPageEffect({ 500 }, { playbackRequests.add(it) })
            VerticalPager(
                state = pager,
                pageSize = PageSize.Fixed(geometry.pageSize),
                pageSpacing = geometry.pageSpacing,
                contentPadding = PaddingValues(vertical = (height.dp - geometry.pageSize) / 2),
                beyondViewportPageCount = 2,
                modifier = Modifier.size(400.dp, height.dp).testTag("liveViewport"),
            ) { page ->
                DisposableEffect(page) {
                    activePages.add(page)
                    onDispose { activePages.remove(page) }
                }
                Box(Modifier.fillMaxSize().testTag("liveCard.$page"))
            }
        }
        compose.waitForIdle()
        val originalPager = pager
        var minus27Stride = 0
        for (nextSetting in listOf(-27, -40, -50, 50, 0)) {
            compose.runOnIdle { setting.value = nextSetting }
            repeat(20) {
                compose.mainClock.advanceTimeByFrame()
                val viewport = compose.onNodeWithTag("liveViewport").fetchSemanticsNode().boundsInRoot
                val cover = compose.onNodeWithTag("liveCard.500").fetchSemanticsNode().boundsInRoot
                assertEquals("Horizontal center at spacing $nextSetting", viewport.center.x, cover.center.x, 1f)
                assertEquals("Vertical center at spacing $nextSetting", viewport.center.y, cover.center.y, 1f)
                compose.runOnIdle {
                    assertSame("Settings must update the mounted pager", originalPager, pager)
                    assertEquals(500, pager.currentPage)
                    assertEquals(0f, pager.currentPageOffsetFraction, .001f)
                    assertTrue("Spacing changes must not change playback", playbackRequests.isEmpty())
                    // Lazy reuse retains the largest measured set across spacing changes.
                    // Bound that set using the minimum stride and full overlapping page extent.
                    val pageSize = minOf(400, height)
                    val overlapBudget = kotlin.math.ceil(
                        (height + pageSize) / (pageSize / 8.0),
                    ).toInt() + 1
                    val pageBudget = overlapBudget + 2 * pager.layoutInfo.beyondViewportPageCount + 2
                    assertTrue("Mounted pages at spacing $nextSetting: ${activePages.size}", activePages.size <= pageBudget)
                }
            }
            compose.runOnIdle {
                val stride = pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing
                if (nextSetting == -27) minus27Stride = stride
                if (nextSetting == -50) {
                    assertTrue("Below -27 the mounted layout must still visibly change",
                        minus27Stride - stride >= 5)
                }
            }
        }
    }

    @Test fun `Small landscape minimum spacing keeps the page budget throughout expansion and collapse`() {
        val expanded = mutableStateOf(false)
        var geometry = playerCarouselGeometry(400.dp, 300.dp, 5.dp, 10.dp, false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val padding by animatePlayerCarouselDp(if (expanded.value) 90.dp else 5.dp, 400.dp, "budgetPadding")
            val spacing by animatePlayerCarouselDp(if (expanded.value) (-420).dp else 10.dp, 400.dp, "budgetSpacing")
            geometry = animatePlayerCarouselGeometry(400.dp, 300.dp, padding, spacing, expanded.value)
        }
        for (target in listOf(true, false)) {
            compose.runOnIdle { expanded.value = target }
            compose.waitForIdle()
            repeat(20) {
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    assertTrue("Intermediate page stride must stay bounded: $geometry",
                        geometry.pageSize + geometry.pageSpacing >= geometry.pageSize / 8)
                }
            }
        }
    }

    @Test
    fun `short stack formula transitions continuously in both expansion directions`() {
        val stacked = mutableStateOf(false)
        var geometry = playerCarouselGeometry(400.dp, 240.dp, 15.dp, 10.dp, false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            geometry = animatePlayerCarouselGeometry(400.dp, 240.dp, 15.dp, 10.dp, stacked.value)
        }
        for (expanded in listOf(true, false)) {
            compose.runOnIdle { stacked.value = expanded }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(80)
            compose.runOnIdle {
                assertTrue("Stack padding must interpolate: $geometry", geometry.coverPadding > 0.dp && geometry.coverPadding < 15.dp)
                assertEquals(240.dp, geometry.pageSize)
                assertTrue(geometry.pageSize + geometry.pageSpacing > 0.dp)
            }
            compose.mainClock.advanceTimeBy(300)
            compose.runOnIdle {
                assertEquals(if (expanded) 0.dp else 15.dp, geometry.coverPadding)
                assertEquals(if (expanded) 246f else 250f, (geometry.pageSize + geometry.pageSpacing).value, .001f)
            }
        }
    }

    @Test
    fun `resize applies current geometry immediately while preserving selected page`() = verifyResize(false)

    @Test
    fun `stacked negative spacing stays centered through expansion and both resize directions`() = verifyResize(true)

    private fun verifyResize(stacked: Boolean) {
        val landscape = mutableStateOf(false)
        val expanded = mutableStateOf(false)
        val playbackRequests = mutableListOf<Int>()
        val player = mockk<Player>(relaxed = true)
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        every { binder.player } returns player
        every { player.currentMediaItemIndex } returns 2
        lateinit var pager: PagerState
        var paddingValue = 0.dp
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BoxWithConstraints(Modifier.size(
                if (landscape.value) 400.dp else 240.dp,
                if (landscape.value) 240.dp else if (expanded.value) 440.dp else 400.dp,
            ).testTag("viewport")) {
                val padding by animatePlayerCarouselDp(
                    if (expanded.value) 40.dp else 20.dp, maxWidth, "testPadding")
                paddingValue = padding
                val geometry = playerCarouselGeometry(maxWidth, maxHeight, padding,
                    if (stacked) (-180).dp else 10.dp, stacked)
                pager = rememberPagerState(initialPage = 2) { 5 }
                if (landscape.value) {
                    pager.LaunchedEffectScrollToPage(binder, 0, 2, false)
                    pager.slideSettledPageEffect({ 2 }, { playbackRequests.add(it) })
                } else {
                    pager.LaunchedEffectScrollToPage(binder, 0, 2, false)
                    pager.slideSettledPageEffect({ 2 }, { playbackRequests.add(it) })
                }
                VerticalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    pageSize = PageSize.Fixed(geometry.pageSize),
                    pageSpacing = geometry.pageSpacing,
                    contentPadding = PaddingValues(
                        top = (maxHeight - geometry.pageSize) / 2,
                        bottom = (maxHeight - geometry.pageSize) / 2 + (-geometry.pageSpacing).coerceAtLeast(0.dp)),
                ) { page -> Box(Modifier.fillMaxSize().testTag("card.$page")) }
            }
        }
        compose.onNodeWithTag("viewport").fetchSemanticsNode()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals("Initial padding", 20.dp, paddingValue) }
        compose.runOnIdle { expanded.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { assertTrue("Intermediate padding after 112ms: $paddingValue",
            paddingValue > 20.dp && paddingValue < 40.dp) }
        for (wide in listOf(true, false)) {
            compose.runOnIdle { landscape.value = wide }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
            val card = compose.onNodeWithTag("card.2").fetchSemanticsNode().boundsInRoot
            assertEquals(viewport.center.y, card.center.y, 1f)
            compose.runOnIdle {
                assertEquals(2, pager.currentPage)
                assertEquals(40.dp, paddingValue)
                assertTrue("Rotation must not issue playback commands: $playbackRequests", playbackRequests.isEmpty())
            }
        }
    }

    @Test
    @androidx.annotation.OptIn(UnstableApi::class)
    fun `initial playback index snaps immediately and subsequent track change animates`() {
        val index = mutableStateOf(2)
        val player = mockk<Player>(relaxed = true)
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        every { binder.player } returns player
        every { player.currentMediaItemIndex } answers { index.value }
        lateinit var pager: PagerState
        compose.mainClock.autoAdvance = false
        compose.setContent {
            pager = rememberPagerState(initialPage = 0) { 5 }
            pager.LaunchedEffectScrollToPage(binder, 0, index.value, false)
            VerticalPager(state = pager, modifier = Modifier.size(240.dp, 400.dp).testTag("syncPager")) {
                Box(Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithTag("syncPager").fetchSemanticsNode()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(2, pager.currentPage)
            assertTrue(!pager.isScrollInProgress)
            index.value = 4
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("A later foreground change should animate", pager.isScrollInProgress) }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(4, pager.currentPage)
            assertTrue(!pager.isScrollInProgress)
        }
    }
}
