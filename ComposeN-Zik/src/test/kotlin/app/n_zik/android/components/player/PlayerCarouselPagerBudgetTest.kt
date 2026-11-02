package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import app.it.fast4x.rimusic.ui.screens.player.LaunchedEffectScrollToPage
import app.n_zik.android.components.player.slide.flingBehaviorFor
import app.n_zik.android.components.player.slide.landscapeCarouselDrag
import app.n_zik.android.components.player.slide.slideSettledPageEffect
import app.n_zik.android.enums.PlayerSwipeSensitivity
import app.n_zik.android.playback.services.PlayerServiceModern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerCarouselPagerBudgetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `Small landscape with minus50 spacing keeps a thousand track queue lazy`() {
        val activePages = mutableSetOf<Int>()
        val geometry = playerCarouselGeometry(400.dp, 300.dp, 90.dp, (-420).dp, true)
        lateinit var pager: PagerState
        compose.setContent {
            pager = rememberPagerState(initialPage = 500) { 1_000 }
            VerticalPager(
                state = pager,
                pageSize = PageSize.Fixed(geometry.pageSize),
                pageSpacing = geometry.pageSpacing,
                beyondViewportPageCount = 2,
                modifier = Modifier.size(400.dp, 300.dp),
            ) { page ->
                DisposableEffect(page) {
                    activePages.add(page)
                    onDispose { activePages.remove(page) }
                }
                Box(Modifier.fillMaxSize())
            }
        }
        compose.runOnIdle {
            assertEquals(500, pager.currentPage)
            assertLazyPageBudget(pager, activePages)
        }
    }

    @Test fun `short landscape stack keeps long queue lazy and selects a dragged page`() {
        val activePages = mutableSetOf<Int>()
        val playbackRequests = mutableListOf<Int>()
        val geometry = playerCarouselGeometry(400.dp, 160.dp, 100.dp, (-300).dp, true)
        val playingIndex = mutableStateOf(500)
        val player = mockk<Player>(relaxed = true)
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        every { binder.player } returns player
        every { player.currentMediaItemIndex } answers { playingIndex.value }
        var touchSlop = 0f
        lateinit var pager: PagerState
        compose.setContent {
            touchSlop = LocalViewConfiguration.current.touchSlop
            pager = rememberPagerState(initialPage = 500) { 1_000 }
            pager.LaunchedEffectScrollToPage(binder, 0, playingIndex.value, false)
            pager.slideSettledPageEffect(
                currentMediaItemIndex = { playingIndex.value },
                playAtIndex = { playingIndex.value = it; playbackRequests.add(it) },
            )
            val fling = flingBehaviorFor(pager)
            VerticalPager(
                state = pager,
                pageSize = PageSize.Fixed(geometry.pageSize),
                pageSpacing = geometry.pageSpacing,
                contentPadding = PaddingValues(0.dp),
                beyondViewportPageCount = 2,
                flingBehavior = fling,
                modifier = Modifier.size(400.dp, 160.dp)
                    .landscapeCarouselDrag(pager, PlayerSwipeSensitivity.Medium)
                    .testTag("carousel"),
            ) { page ->
                DisposableEffect(page) {
                    activePages.add(page)
                    onDispose { activePages.remove(page) }
                }
                Box(Modifier.fillMaxSize())
            }
        }
        compose.runOnIdle {
            assertEquals(500, pager.currentPage)
            assertTrue(playbackRequests.isEmpty())
            assertLazyPageBudget(pager, activePages)
        }
        val stridePx = (pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing).toFloat()
        val shortDragPx = touchSlop + 0.10f * maxOf(stridePx, PlayerSwipeSensitivity.Medium.threshold)
        compose.onNodeWithTag("carousel").performTouchInput {
            down(center)
            moveTo(center - Offset(0f, shortDragPx), delayMillis = 500)
        }
        compose.runOnIdle {
            assertEquals("A short drag stays on the playing page", 500, pager.currentPage)
            assertTrue(pager.currentPageOffsetFraction > 0f)
            assertTrue(playbackRequests.isEmpty())
        }
        compose.onNodeWithTag("carousel").performTouchInput { up() }
        compose.runOnIdle {
            assertEquals("Below the release threshold, return to the playing track", 500, pager.currentPage)
            assertEquals(0f, pager.currentPageOffsetFraction, 0.001f)
            assertTrue(playbackRequests.isEmpty())
        }
        compose.onNodeWithTag("carousel").performTouchInput { swipeUp(durationMillis = 500) }
        compose.runOnIdle {
            assertTrue("A deliberate drag must change the selected page", pager.currentPage > 500)
            assertEquals(pager.currentPage, pager.settledPage)
            assertEquals("Released page must be centered", 0f, pager.currentPageOffsetFraction, 0.001f)
            assertEquals(listOf(pager.settledPage), playbackRequests)
            assertLazyPageBudget(pager, activePages)
        }
    }

    @Test
    @Config(qualifiers = "w960dp-h480dp-land-xxhdpi")
    fun `Big landscape preset accepts a moderate swipe and centers before playing once`() {
        val geometry = playerCarouselGeometry(480.dp, 380.dp, 30.dp, (-90).dp, true)
        val playingIndex = mutableStateOf(2)
        val playbackRequests = mutableListOf<Int>()
        val playbackOffsets = mutableListOf<Float>()
        val player = mockk<Player>(relaxed = true)
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        every { binder.player } returns player
        every { player.currentMediaItemIndex } answers { playingIndex.value }
        var density = 0f
        var touchSlop = 0f
        lateinit var pager: PagerState
        compose.setContent {
            density = LocalDensity.current.density
            touchSlop = LocalViewConfiguration.current.touchSlop
            pager = rememberPagerState(initialPage = 2) { 5 }
            pager.LaunchedEffectScrollToPage(binder, 0, playingIndex.value, false)
            pager.slideSettledPageEffect(
                currentMediaItemIndex = { playingIndex.value },
                playAtIndex = {
                    playbackOffsets.add(pager.currentPageOffsetFraction)
                    playingIndex.value = it
                    playbackRequests.add(it)
                },
            )
            val fling = flingBehaviorFor(pager)
            VerticalPager(
                state = pager,
                pageSize = PageSize.Fixed(geometry.pageSize),
                pageSpacing = geometry.pageSpacing,
                contentPadding = PaddingValues(0.dp),
                beyondViewportPageCount = 2,
                flingBehavior = fling,
                modifier = Modifier.size(480.dp, 380.dp)
                    .landscapeCarouselDrag(pager, PlayerSwipeSensitivity.Medium)
                    .testTag("bigCarousel"),
            ) { Box(Modifier.fillMaxSize()) }
        }
        compose.runOnIdle {
            assertEquals(3f, density, .001f)
            assertEquals(926f, (pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing).toFloat(), 2f)
            assertTrue(playbackRequests.isEmpty())
        }
        val distance = touchSlop + .4f * (pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing)
        compose.onNodeWithTag("bigCarousel").performTouchInput {
            swipe(center, center - Offset(0f, distance), durationMillis = 300)
        }
        compose.runOnIdle {
            assertEquals(3, pager.currentPage)
            assertEquals(3, pager.settledPage)
            assertEquals(0f, pager.currentPageOffsetFraction, .001f)
            assertEquals(listOf(3), playbackRequests)
            assertTrue("Playback must wait for centering: $playbackOffsets",
                playbackOffsets.all { abs(it) <= .001f })
        }
    }

    @Test
    fun `landscape last cover settles and plays after dragging to the queue boundary`() {
        val geometry = playerCarouselGeometry(400.dp, 160.dp, 100.dp, (-300).dp, true)
        val playingIndex = mutableStateOf(1)
        val requests = mutableListOf<Int>()
        lateinit var pager: PagerState
        compose.setContent {
            pager = rememberPagerState(initialPage = 1) { 3 }
            pager.slideSettledPageEffect({ playingIndex.value }, {
                playingIndex.value = it
                requests.add(it)
            })
            VerticalPager(
                state = pager,
                pageSize = PageSize.Fixed(geometry.pageSize),
                pageSpacing = geometry.pageSpacing,
                contentPadding = PaddingValues(0.dp),
                flingBehavior = flingBehaviorFor(pager),
                modifier = Modifier.size(400.dp, 160.dp)
                    .landscapeCarouselDrag(pager, PlayerSwipeSensitivity.Medium)
                    .testTag("boundaryCarousel"),
            ) { Box(Modifier.fillMaxSize()) }
        }
        compose.onNodeWithTag("boundaryCarousel").performTouchInput { swipeUp(durationMillis = 400) }
        compose.runOnIdle {
            assertEquals(2, pager.settledPage)
            assertEquals(0f, pager.currentPageOffsetFraction, .001f)
            assertTrue(!pager.isScrollInProgress)
            assertEquals(listOf(2), requests)
            assertEquals(2, playingIndex.value)
        }
    }

    @Test
    fun `portrait lookahead to landscape rotation keeps measurement and swipe selection live`() {
        val landscape = mutableStateOf(false)
        val playingIndex = mutableStateOf(2)
        val requests = mutableListOf<Int>()
        lateinit var pager: PagerState
        var density = 0f
        compose.setContent {
            density = LocalDensity.current.density
            pager = rememberPlayerArtworkPagerState(landscape.value, playingIndex.value) { 8 }
            val content: @Composable () -> Unit = {
                BoxWithConstraints(Modifier.size(
                    if (landscape.value) 400.dp else 300.dp,
                    if (landscape.value) 160.dp else 500.dp,
                )) {
                    val geometry = playerCarouselGeometry(maxWidth, maxHeight, 40.dp, (-120).dp, true)
                    pager.slideSettledPageEffect({ playingIndex.value }, {
                        playingIndex.value = it
                        requests.add(it)
                    })
                    VerticalPager(
                        state = pager,
                        pageSize = PageSize.Fixed(geometry.pageSize),
                        pageSpacing = geometry.pageSpacing,
                        flingBehavior = flingBehaviorFor(pager),
                        modifier = Modifier.fillMaxSize().testTag("rotatingCarousel"),
                    ) { Box(Modifier.fillMaxSize()) }
                }
            }
            if (landscape.value) content() else LookaheadScope { content() }
        }
        compose.runOnIdle {
            assertEquals((300 * density).toInt(), pager.layoutInfo.pageSize)
            landscape.value = true
        }
        repeat(2) {
            compose.runOnIdle {
                assertEquals("Landscape must publish its own measurements", (160 * density).toInt(), pager.layoutInfo.pageSize)
                assertEquals(playingIndex.value, pager.currentPage)
                assertEquals(0f, pager.currentPageOffsetFraction, .001f)
            }
            val previousIndex = playingIndex.value
            compose.onNodeWithTag("rotatingCarousel").performTouchInput { swipeUp(durationMillis = 400) }
            compose.runOnIdle {
                assertTrue(pager.settledPage > previousIndex)
                assertEquals(pager.settledPage, playingIndex.value)
                assertEquals(0f, pager.currentPageOffsetFraction, .001f)
                assertEquals(it + 1, requests.size)
                landscape.value = false
            }
            compose.runOnIdle {
                assertEquals((300 * density).toInt(), pager.layoutInfo.pageSize)
                assertEquals(playingIndex.value, pager.currentPage)
                assertEquals(0f, pager.currentPageOffsetFraction, .001f)
                landscape.value = true
            }
        }
    }

    @Test
    fun `idle fractional page does not play until it is centered`() {
        val playbackRequests = mutableListOf<Int>()
        lateinit var pager: PagerState
        lateinit var scope: CoroutineScope
        compose.setContent {
            scope = rememberCoroutineScope()
            pager = rememberPagerState { 5 }
            pager.slideSettledPageEffect({ 0 }, { playbackRequests.add(it) })
            VerticalPager(state = pager, modifier = Modifier.size(240.dp, 320.dp)) {
                Box(Modifier.fillMaxSize())
            }
        }
        compose.runOnIdle { scope.launch { pager.scrollToPage(1, .2f) } }
        compose.runOnIdle {
            assertEquals(1, pager.settledPage)
            assertTrue(!pager.isScrollInProgress)
            assertEquals(.2f, pager.currentPageOffsetFraction, .001f)
            assertTrue("An idle but displaced page is not settled for playback", playbackRequests.isEmpty())
        }
        compose.runOnIdle { scope.launch { pager.scrollToPage(1) } }
        compose.runOnIdle {
            assertEquals(0f, pager.currentPageOffsetFraction, .001f)
            assertEquals(listOf(1), playbackRequests)
        }
    }

    @Test
    fun `synchronization centers an already selected playback index`() {
        val player = mockk<Player>(relaxed = true)
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        every { binder.player } returns player
        every { player.currentMediaItemIndex } returns 2
        lateinit var pager: PagerState
        compose.setContent {
            pager = rememberPagerState(initialPage = 2, initialPageOffsetFraction = .2f) { 5 }
            pager.LaunchedEffectScrollToPage(binder, 0, 2, false)
            VerticalPager(state = pager, modifier = Modifier.size(240.dp, 320.dp)) {
                Box(Modifier.fillMaxSize())
            }
        }
        compose.runOnIdle {
            assertEquals(2, pager.currentPage)
            assertEquals(0f, pager.currentPageOffsetFraction, .001f)
            assertTrue(!pager.isScrollInProgress)
        }
    }

    @Test
    fun `slow release honors high and low sensitivity through the real pager`() {
        val sensitivity = mutableStateOf(PlayerSwipeSensitivity.High)
        val requests = mutableListOf<Int>()
        lateinit var pager: PagerState
        lateinit var scope: CoroutineScope
        var touchSlop = 0f
        compose.setContent {
            scope = rememberCoroutineScope()
            touchSlop = LocalViewConfiguration.current.touchSlop
            pager = rememberPagerState(initialPage = 2) { 5 }
            pager.slideSettledPageEffect({ 2 }, { requests.add(it) })
            VerticalPager(
                state = pager,
                flingBehavior = flingBehaviorFor(pager, sensitivity = sensitivity.value),
                modifier = Modifier.size(240.dp, 320.dp).testTag("sensitiveCarousel"),
            ) { Box(Modifier.fillMaxSize()) }
        }
        fun dragAndRelease() {
            val distance = touchSlop + .27f * pager.layoutInfo.pageSize
            compose.onNodeWithTag("sensitiveCarousel").performTouchInput {
                down(center)
                moveTo(center - Offset(0f, distance), delayMillis = 500)
                // Send stationary samples so VelocityTracker observes the pause.
                repeat(5) { moveTo(center - Offset(0f, distance), delayMillis = 50) }
                up()
            }
        }
        dragAndRelease()
        compose.runOnIdle {
            assertEquals(3, pager.currentPage)
            assertEquals(0f, pager.currentPageOffsetFraction, .001f)
            assertEquals(listOf(3), requests)
            sensitivity.value = PlayerSwipeSensitivity.Low
            scope.launch { pager.scrollToPage(2) }
        }
        compose.runOnIdle { requests.clear() }
        dragAndRelease()
        compose.runOnIdle {
            assertEquals(2, pager.currentPage)
            assertEquals(0f, pager.currentPageOffsetFraction, .001f)
            assertTrue(requests.isEmpty())
        }
    }

    private fun assertLazyPageBudget(pager: PagerState, activePages: Set<Int>) {
        val layout = pager.layoutInfo
        val stride = layout.pageSize + layout.pageSpacing
        // Full pages overlap the viewport from either side; their origins can lie outside it.
        val visibleBudget = kotlin.math.ceil(
            (layout.viewportSize.height + layout.pageSize).toDouble() / stride,
        ).toInt() + 1
        assertTrue("Visible pages: ${layout.visiblePagesInfo.size}",
            layout.visiblePagesInfo.size <= visibleBudget)
        val mountedBudget = visibleBudget + 2 * layout.beyondViewportPageCount + 2
        assertTrue("Mounted pages: ${activePages.size}", activePages.size <= mountedBudget)
    }
}
