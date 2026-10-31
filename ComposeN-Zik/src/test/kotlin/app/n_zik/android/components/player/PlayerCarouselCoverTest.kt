package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.enums.ThumbnailType
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
class PlayerCarouselCoverTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `full cover landscape keeps controls full width inside the same system safe area`() {
        assertEquals(false, playerLandscapeArtworkColumnVisible(PlayerType.Modern, ThumbnailType.Essential, false))
        assertTrue(playerLandscapeArtworkColumnVisible(PlayerType.Modern, ThumbnailType.Modern, false))
        assertTrue(playerLandscapeArtworkColumnVisible(PlayerType.Modern, ThumbnailType.Essential, true))
        assertTrue(playerLandscapeArtworkColumnVisible(PlayerType.Essential, ThumbnailType.Essential, false))
        compose.setContent {
            Box(Modifier.size(300.dp, 260.dp).testTag("full-cover-background")) {
                PlayerLandscapeColumns(
                    showArtworkColumn = false,
                    contentInsets = WindowInsets(left = 8, top = 24, right = 16, bottom = 12),
                    artwork = { Box(Modifier.fillMaxSize().testTag("unused-artwork-column")) },
                    controls = { Box(Modifier.fillMaxSize().testTag("full-cover-controls")) },
                )
            }
        }
        compose.onNodeWithTag("unused-artwork-column").assertDoesNotExist()
        val background = compose.onNodeWithTag("full-cover-background").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("full-cover-controls").fetchSemanticsNode().boundsInRoot
        assertEquals(background.left + 8f, controls.left, 1f)
        assertEquals(background.right - 16f, controls.right, 1f)
        assertEquals(background.top + 24f, controls.top, 1f)
        assertEquals(background.bottom - 12f, controls.bottom, 1f)
    }

    @Test fun `landscape safe area matches system insets once while background stays full size`() {
        val alreadyReservedTop = mutableStateOf(false)
        val insets = WindowInsets(left = 8, top = 24, right = 16, bottom = 12)
        compose.setContent {
            Box(Modifier.size(300.dp, 260.dp).testTag("full-background")) {
                Box(if (alreadyReservedTop.value) Modifier.windowInsetsPadding(WindowInsets(top = 24)) else Modifier) {
                    PlayerLandscapeColumns(
                        contentInsets = insets,
                        artwork = { Box(Modifier.fillMaxSize().windowInsetsPadding(insets).testTag("safe-art")) },
                        controls = { Box(Modifier.fillMaxSize().windowInsetsPadding(insets).testTag("safe-controls")) },
                    )
                }
            }
        }
        val background = compose.onNodeWithTag("full-background").fetchSemanticsNode().boundsInRoot
        for (reserved in listOf(false, true)) {
            compose.runOnIdle { alreadyReservedTop.value = reserved }
            val artwork = compose.onNodeWithTag("safe-art").fetchSemanticsNode().boundsInRoot
            val controls = compose.onNodeWithTag("safe-controls").fetchSemanticsNode().boundsInRoot
            assertEquals(background.left + 8f, artwork.left, 1f)
            assertEquals(background.top + 24f, artwork.top, 1f)
            assertEquals(artwork.top, controls.top, 1f)
            assertEquals(background.right - 16f, controls.right, 1f)
            assertEquals(background.bottom - 12f, artwork.bottom, 1f)
            assertEquals(artwork.bottom, controls.bottom, 1f)
            assertEquals(background, compose.onNodeWithTag("full-background").fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test fun `artwork and controls stay mounted and fixed while cover overlays change in both orientations`() {
        val landscape = mutableStateOf(false)
        val mode = mutableStateOf(0)
        var artMounts = 0
        var artDisposals = 0
        var controlMounts = 0
        var controlDisposals = 0
        compose.setContent {
            val artwork: @Composable () -> Unit = {
                PlayerCoverOverlayHost(
                    visibility = PlayerCoverOverlayVisibility(mode.value == 1, mode.value == 2),
                    modifier = Modifier.size(180.dp).testTag("stable-art"),
                    artwork = {
                        DisposableEffect(Unit) { artMounts++; onDispose { artDisposals++ } }
                        Box(Modifier.fillMaxSize())
                    },
                    lyrics = { Box(Modifier.fillMaxSize()) },
                    visualizer = { Box(Modifier.fillMaxSize()) },
                )
            }
            val controls: @Composable () -> Unit = {
                DisposableEffect(Unit) { controlMounts++; onDispose { controlDisposals++ } }
                Box(Modifier.size(100.dp).testTag("stable-controls"))
            }
            Box(Modifier.size(400.dp, 340.dp)) {
                if (landscape.value) PlayerLandscapeColumns(
                    contentInsets = WindowInsets(left = 0, top = 0, right = 0, bottom = 0), artwork = { artwork() }, controls = { controls() })
                else Column { artwork(); controls() }
            }
        }
        for (horizontal in listOf(false, true)) {
            compose.runOnIdle { landscape.value = horizontal; mode.value = 0 }
            compose.waitForIdle()
            val art = compose.onNodeWithTag("stable-art").fetchSemanticsNode().boundsInRoot
            val controls = compose.onNodeWithTag("stable-controls").fetchSemanticsNode().boundsInRoot
            val mounts = listOf(artMounts, artDisposals, controlMounts, controlDisposals)
            for (selected in 1..2) {
                compose.runOnIdle { mode.value = selected }
                assertEquals(art, compose.onNodeWithTag("stable-art").fetchSemanticsNode().boundsInRoot)
                assertEquals(controls, compose.onNodeWithTag("stable-controls").fetchSemanticsNode().boundsInRoot)
                compose.runOnIdle { assertEquals(mounts, listOf(artMounts, artDisposals, controlMounts, controlDisposals)) }
            }
        }
    }

    @Test fun `carousel forces overlays onto card without changing noncarousel preferences`() {
        val stack = playerCoverOverlayPolicy(true, true, false, false)
        assertTrue(stack.lyricsOnCover)
        assertTrue(stack.visualizerOnCover)
        assertTrue(playerCoverArtworkVisible(true, true, stack.lyricsOnCover, false, stack.visualizerOnCover))
        assertTrue(playerCoverArtworkVisible(true, false, stack.lyricsOnCover, true, stack.visualizerOnCover))
        assertEquals(false, playerStandaloneVisualizerVisible(true, true, stack.visualizerOnCover))
        assertEquals(PlayerCoverOverlayVisibility(true, true), playerCoverOverlayVisibility(
            1, 1, "playing", "playing", true, stack.lyricsOnCover, true, stack.visualizerOnCover, false))
        assertEquals(PlayerCoverOverlayPolicy(false, false), playerCoverOverlayPolicy(false, true, false, false))
        assertEquals(PlayerCoverOverlayPolicy(false, false), playerCoverOverlayPolicy(true, false, false, false))
    }

    @Test fun `front card overlay preserves bounds and adjacent artwork pages`() {
        val mode = mutableStateOf(0)
        compose.setContent {
            val policy = playerCoverOverlayPolicy(true, true, false, false)
            Box(Modifier.size(300.dp, 360.dp)) {
                for (page in 0..2) {
                    PlayerCoverOverlayHost(
                        visibility = playerCoverOverlayVisibility(page, 1, "song$page", "song1",
                            mode.value == 1, policy.lyricsOnCover, mode.value == 2, policy.visualizerOnCover, false),
                        modifier = Modifier.size(220.dp).offset { IntOffset(0, (page - 1) * 30) }
                            .testTag("stack-card-$page"),
                        artwork = { Box(Modifier.fillMaxSize().testTag("stack-art-$page")) },
                        lyrics = {
                            assertTrue(LocalPlayerCoverOverlay.current)
                            Box(Modifier.fillMaxSize().testTag("stack-lyrics"))
                        },
                        visualizer = { Box(Modifier.fillMaxSize().testTag("stack-vis")) },
                    )
                }
            }
        }
        val initial = (0..2).map { compose.onNodeWithTag("stack-card-$it").fetchSemanticsNode().boundsInRoot }
        for (selected in 1..2) {
            compose.runOnIdle { mode.value = selected }
            for (page in 0..2) {
                assertEquals(initial[page], compose.onNodeWithTag("stack-card-$page").fetchSemanticsNode().boundsInRoot)
                compose.onNodeWithTag("stack-art-$page", useUnmergedTree = true).assertExists()
            }
            val overlay = compose.onNodeWithTag(if (selected == 1) "stack-lyrics" else "stack-vis")
                .fetchSemanticsNode().boundsInRoot
            assertEquals(initial[1], overlay)
        }
    }

    @Test fun `landscape left column keeps carousel pages in a vertical stack`() {
        compose.setContent {
            Box(Modifier.size(600.dp, 360.dp)) {
                PlayerLandscapeColumns(
                    artwork = {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxSize()) {
                            val geometry = playerCarouselGeometry(maxWidth, maxHeight, 20.dp, (-100).dp, true)
                            VerticalPager(
                                state = rememberPagerState(initialPage = 1) { 3 },
                                pageSize = PageSize.Fixed(geometry.pageSize),
                                pageSpacing = geometry.pageSpacing,
                                beyondViewportPageCount = 2,
                                contentPadding = PaddingValues(
                                    top = (maxHeight - geometry.pageSize) / 2,
                                    bottom = (maxHeight - geometry.pageSize) / 2 +
                                        (-geometry.pageSpacing).coerceAtLeast(0.dp)),
                            ) { page ->
                                PlayerCoverOverlayHost(
                                    visibility = PlayerCoverOverlayVisibility(false, false),
                                    modifier = Modifier.size(geometry.pageSize).padding(geometry.coverPadding)
                                        .testTag("vertical-card-$page"),
                                    artwork = { Box(Modifier.fillMaxSize()) },
                                    lyrics = {}, visualizer = {},
                                )
                            }
                        }
                    },
                    controls = { Box(Modifier.fillMaxSize().testTag("stack-right-controls")) },
                )
            }
        }
        val pages = (0..2).map { compose.onNodeWithTag("vertical-card-$it").fetchSemanticsNode().boundsInRoot }
        val controls = compose.onNodeWithTag("stack-right-controls").fetchSemanticsNode().boundsInRoot
        assertTrue(pages[0].center.y < pages[1].center.y)
        assertTrue(pages[1].center.y < pages[2].center.y)
        assertEquals(pages[0].center.x, pages[1].center.x, 1f)
        assertEquals(pages[1].center.x, pages[2].center.x, 1f)
        pages.forEach { assertTrue(it.right <= controls.left) }
    }

    @Test fun `landscape reserves left artwork and right controls through overlay changes`() {
        val visibility = mutableStateOf(PlayerCoverOverlayVisibility(false, false))
        compose.setContent {
            Box(Modifier.size(800.dp, 360.dp)) {
                PlayerLandscapeColumns(
                    artwork = {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxSize().testTag("left"),
                            contentAlignment = Alignment.Center) {
                            PlayerCoverOverlayHost(
                                visibility = visibility.value,
                                modifier = Modifier.size(250.dp).testTag("landscape-cover"),
                                artwork = { Box(Modifier.fillMaxSize()) },
                                lyrics = { Box(Modifier.fillMaxSize().testTag("landscape-lyrics")) },
                                visualizer = { Box(Modifier.fillMaxSize().testTag("landscape-vis")) },
                            )
                        }
                    },
                    controls = { Box(Modifier.fillMaxSize().testTag("right-controls")) },
                )
            }
        }
        for (mode in listOf(PlayerCoverOverlayVisibility(false, false),
            PlayerCoverOverlayVisibility(true, false), PlayerCoverOverlayVisibility(false, true))) {
            compose.runOnIdle { visibility.value = mode }
            val left = compose.onNodeWithTag("left").fetchSemanticsNode().boundsInRoot
            val cover = compose.onNodeWithTag("landscape-cover").fetchSemanticsNode().boundsInRoot
            val controls = compose.onNodeWithTag("right-controls").fetchSemanticsNode().boundsInRoot
            assertEquals(left.width, controls.width, 1f)
            assertEquals(left.top, controls.top, 1f)
            assertEquals(left.height, controls.height, 1f)
            assertTrue(cover.right <= controls.left)
            assertTrue(cover.width > left.width * .5f)
            assertTrue(cover.height > left.height * .5f)
        }
    }

    @Test fun `landscape visualizer falls back when artwork is hidden regardless of cover preference`() {
        assertTrue(playerStandaloneVisualizerVisible(false, true, true))
        assertTrue(playerStandaloneVisualizerVisible(false, true, false))
        assertTrue(playerStandaloneVisualizerVisible(true, true, false))
        assertEquals(false, playerStandaloneVisualizerVisible(true, true, true))
        assertEquals(false, playerStandaloneVisualizerVisible(false, false, true))
    }

    @Test fun `both carousel orientations keep artwork only for enabled thumbnail modes`() {
        assertTrue(playerCoverArtworkVisible(true, false, false, false, false))
        assertTrue(playerCoverArtworkVisible(true, true, true, false, false))
        assertTrue(playerCoverArtworkVisible(true, false, false, true, true))
        assertEquals(false, playerCoverArtworkVisible(true, true, false, false, true))
        assertEquals(false, playerCoverArtworkVisible(true, false, true, true, false))
        assertEquals(false, playerCoverArtworkVisible(false, true, true, true, true))
    }

    @Test fun `only playing page with matching item receives enabled cover overlays`() {
        fun visibility(page: Int = 2, id: String? = "playing", lyrics: Boolean = true,
            visualizer: Boolean = true, error: Boolean = false) = playerCoverOverlayVisibility(
                page, 2, id, "playing", true, lyrics, true, visualizer, error)
        assertEquals(PlayerCoverOverlayVisibility(true, true), visibility())
        assertEquals(PlayerCoverOverlayVisibility(false, false), visibility(page = 1))
        assertEquals(PlayerCoverOverlayVisibility(false, false), visibility(id = "previous"))
        assertEquals(PlayerCoverOverlayVisibility(false, false), visibility(id = null))
        assertEquals(PlayerCoverOverlayVisibility(false, true), visibility(lyrics = false))
        assertEquals(PlayerCoverOverlayVisibility(true, false), visibility(visualizer = false))
        assertEquals(PlayerCoverOverlayVisibility(false, true), visibility(error = true))
        assertEquals(PlayerCoverOverlayVisibility(false, false), playerCoverOverlayVisibility(
            2, 2, "playing", "playing", false, true, false, true, false))
    }

    @Test fun `overlay mounting follows prefs and active page without blocking parent gestures`() {
        val visibility = mutableStateOf(PlayerCoverOverlayVisibility(true, false))
        var taps = 0
        var drag = 0f
        compose.setContent {
            Box(Modifier.size(220.dp).draggable(
                rememberDraggableState { drag += it }, Orientation.Vertical)) {
                PlayerCoverOverlayHost(
                    visibility = visibility.value,
                    modifier = Modifier.fillMaxSize().testTag("cover").clickable { taps++ },
                    artwork = { Box(Modifier.fillMaxSize().testTag("artwork")) },
                    lyrics = { Box(Modifier.fillMaxSize().testTag("lyrics")) },
                    visualizer = { Box(Modifier.fillMaxSize().testTag("visualizer")) },
                )
            }
        }
        compose.onNodeWithTag("lyrics", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("visualizer", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("cover").performClick()
        compose.runOnIdle { assertEquals(1, taps) }
        compose.onNodeWithTag("cover").performTouchInput {
            swipe(center, center + Offset(0f, 70f), 400)
        }
        compose.runOnIdle {
            assertTrue(drag > 0f)
            visibility.value = PlayerCoverOverlayVisibility(false, true)
        }
        compose.onNodeWithTag("lyrics", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("visualizer", useUnmergedTree = true).assertExists()
        compose.runOnIdle { visibility.value = PlayerCoverOverlayVisibility(false, false) }
        compose.onNodeWithTag("visualizer", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("artwork", useUnmergedTree = true).assertExists()
    }
}
