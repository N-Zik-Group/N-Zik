package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.PlayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w500dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerGlassBudgetLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `only visible single artwork uses full width priority while both Modern pager modes keep their budget`() {
        for (type in PlayerType.entries) {
            assertEquals(type == PlayerType.Essential,
                playerPortraitPrioritizesArtwork(true, type))
            assertEquals(false, playerPortraitPrioritizesArtwork(false, type))
        }
    }

    @Test
    fun `carousel ignores artwork priority and retains natural lower height when expanded or collapsed`() {
        val expanded = mutableStateOf(false)
        compose.setContent {
            PlayerGlassBudgetLayout(enabled = false, expandedCarousel = expanded.value,
                reserveLowerContent = true, prioritizeArtwork = true, informationGap = 8.dp,
                modifier = Modifier.requiredSize(400.dp, 900.dp),
                header = { Box(Modifier.size(400.dp, 50.dp)) },
                artwork = {
                    Box((if (expanded.value) Modifier.weight(1f) else Modifier.size(400.dp))
                        .fillMaxWidth().testTag("carousel.artworkSlot"))
                }, information = {
                    PlayerPortraitInformationLayout(
                        artworkPrioritized = playerPortraitPrioritizesArtwork(true, PlayerType.Modern),
                        weightedControls = playerPortraitUsesWeightedInformation(expanded.value, false,
                            PlayerType.Modern, true, true), above = {}, controls = {
                            Box(Modifier.size(400.dp, 120.dp).testTag("carousel.controls"))
                        }, below = { Box(Modifier.size(400.dp, 50.dp).testTag("carousel.actions")) })
                })
        }
        for (isExpanded in listOf(false, true, false)) {
            compose.runOnIdle { expanded.value = isExpanded }
            val slot = compose.onNodeWithTag("carousel.artworkSlot").fetchSemanticsNode().boundsInRoot
            val controls = compose.onNodeWithTag("carousel.controls").fetchSemanticsNode().boundsInRoot
            val actions = compose.onNodeWithTag("carousel.actions").fetchSemanticsNode().boundsInRoot
            assertEquals("Original carousel artwork slot is retained", (if (isExpanded) 680f else 400f) *
                compose.density.density, slot.height, 1f)
            assertEquals("Controller is not expanded to the surplus artwork budget", 120f *
                compose.density.density, controls.height, 1f)
            assertEquals("Natural lower grouping remains directly adjacent", controls.bottom, actions.top, 1f)
            assertEquals("Actions retain their original bottom anchor", 900f * compose.density.density, actions.bottom, 1f)
        }
    }

    @Test
    fun `cover priority changes retain artwork overlay and information composition lifetime`() {
        val priority = mutableStateOf(true)
        val reserve = mutableStateOf(false)
        var artworkMounts = 0
        var artworkDisposals = 0
        var controlsMounts = 0
        var controlsDisposals = 0
        compose.setContent {
            PlayerGlassBudgetLayout(enabled = reserve.value, reserveLowerContent = reserve.value,
                expandedCarousel = reserve.value, prioritizeArtwork = priority.value,
                modifier = Modifier.requiredSize(400.dp, 900.dp),
                header = { Box(Modifier.size(400.dp, 50.dp)) },
                artwork = {
                    DisposableEffect(Unit) {
                        artworkMounts++
                        onDispose { artworkDisposals++ }
                    }
                    Box(Modifier.size(400.dp))
                }, information = {
                    PlayerPortraitInformationLayout(artworkPrioritized = priority.value,
                        weightedControls = priority.value, above = {}, controls = {
                            DisposableEffect(Unit) {
                                controlsMounts++
                                onDispose { controlsDisposals++ }
                            }
                            Box(Modifier.size(400.dp, 120.dp))
                        }, below = { Box(Modifier.size(400.dp, 50.dp)) })
                })
        }
        for (artworkPriority in listOf(true, false, true, false, true)) {
            compose.runOnIdle { priority.value = artworkPriority; reserve.value = !reserve.value }
            compose.runOnIdle {
                assertEquals("Artwork keeps the host animation state", 1, artworkMounts)
                assertEquals("Artwork exit is not dropped by allocator replacement", 0, artworkDisposals)
                assertEquals("Controller animations keep their remembered state", 1, controlsMounts)
                assertEquals("Information is not remounted", 0, controlsDisposals)
            }
        }
    }

    @Test
    fun `Glass information gap spends only surplus after full artwork and natural controls fit`() {
        val height = mutableStateOf(620.dp)
        val gap = mutableStateOf(0.dp)
        compose.setContent {
            PlayerGlassBudgetLayout(enabled = false, prioritizeArtwork = true, informationGap = gap.value,
                modifier = Modifier.requiredSize(400.dp, height.value),
                header = { Box(Modifier.size(400.dp, 50.dp)) },
                artwork = { Box(Modifier.size(400.dp).testTag("gap.cover")) },
                information = {
                    PlayerPortraitInformationLayout(true, true, above = {}, controls = {
                        Box(Modifier.size(400.dp, 120.dp).testTag("gap.controls"))
                    }, below = { Box(Modifier.size(400.dp, 50.dp).testTag("gap.actions")) })
                })
        }
        for (availableHeight in listOf(620.dp, 628.dp, 640.dp, 619.dp)) for (requestedGap in listOf(0.dp, 8.dp, 0.dp)) {
            compose.runOnIdle { height.value = availableHeight; gap.value = requestedGap }
            val cover = compose.onNodeWithTag("gap.cover").fetchSemanticsNode().boundsInRoot
            val controls = compose.onNodeWithTag("gap.controls").fetchSemanticsNode().boundsInRoot
            val expectedGap = minOf(requestedGap, (availableHeight - 620.dp).coerceAtLeast(0.dp))
            val expectedCover = minOf(400.dp, availableHeight - 220.dp)
            assertEquals("External gap uses only spare height", expectedGap.value * compose.density.density,
                controls.top - cover.bottom, 1f)
            assertEquals("Gap does not reduce the selected artwork", expectedCover.value * compose.density.density,
                cover.height, 1f)
            assertEquals("Actions remain anchored", availableHeight.value * compose.density.density,
                compose.onNodeWithTag("gap.actions").fetchSemanticsNode().boundsInRoot.bottom, 1f)
        }
    }

    @Test
    fun `compact portrait yields cover height to actual optional bottom content`() {
        val nextTrack = mutableStateOf(false)
        compose.setContent {
            PlayerGlassBudgetLayout(enabled = true, modifier = Modifier.requiredSize(400.dp, 600.dp),
                header = { Box(Modifier.size(400.dp, 50.dp)) },
                artwork = { Box(Modifier.size(400.dp).testTag("cover")) },
                information = {
                    Column {
                        Box(Modifier.size(400.dp, 220.dp).testTag("controls"))
                        if (nextTrack.value) Box(Modifier.size(400.dp, 40.dp).testTag("next"))
                        Box(Modifier.size(400.dp, 56.dp).testTag("actions"))
                    }
                })
        }
        val density = compose.density.density
        val firstCover = compose.onNodeWithTag("cover").fetchSemanticsNode().boundsInRoot
        assertEquals(274f * density, firstCover.height, 1f)
        compose.runOnIdle { nextTrack.value = true }
        val cover = compose.onNodeWithTag("cover").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("controls").fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithTag("next").fetchSemanticsNode().boundsInRoot
        val actions = compose.onNodeWithTag("actions").fetchSemanticsNode().boundsInRoot
        assertEquals(234f * density, cover.height, 1f)
        assertEquals(220f * density, controls.height, 1f)
        assertTrue(cover.bottom <= controls.top + 1f)
        assertTrue(controls.bottom <= next.top + 1f)
        assertTrue(next.bottom <= actions.top + 1f)
        assertEquals(600f * density, actions.bottom, 1f)
    }

    @Test
    fun `tall native carousel keeps the selected square cover size without Glass`() {
        compose.setContent {
            PlayerGlassBudgetLayout(
                enabled = false, expandedCarousel = true,
                modifier = Modifier.requiredSize(400.dp, 800.dp),
                header = { Box(Modifier.size(400.dp, 50.dp)) },
                artwork = { Box(Modifier.size(400.dp).testTag("cover")) },
                information = { Box(Modifier.size(400.dp, 250.dp)) })
        }
        val cover = compose.onNodeWithTag("cover").fetchSemanticsNode().boundsInRoot
        assertEquals(400f * compose.density.density, cover.height, 1f)
    }

    @Test
    fun `full cover preset paints to bottom of loose sheet and anchors lower content there`() {
        compose.setContent {
            Box(Modifier.requiredSize(400.dp, 800.dp).background(Color.Black)) {
                PlayerGlassBudgetLayout(
                    enabled = true,
                    modifier = Modifier.fillMaxWidth().background(Color.Magenta).testTag("player"),
                    header = { Box(Modifier.size(400.dp, 50.dp)) },
                    artwork = { Box(Modifier.size(400.dp).testTag("preset.cover")) },
                    information = { Box(Modifier.size(400.dp, 250.dp).testTag("preset.lower")) })
            }
        }
        val player = compose.onNodeWithTag("player").fetchSemanticsNode().boundsInRoot
        val lower = compose.onNodeWithTag("preset.lower").fetchSemanticsNode().boundsInRoot
        val cover = compose.onNodeWithTag("preset.cover").fetchSemanticsNode().boundsInRoot
        val density = compose.density.density
        assertEquals(800f * density, player.height, 1f)
        assertEquals(player.bottom, lower.bottom, 1f)
        assertEquals(400f * density, cover.height, 1f)
        val pixels = compose.onNodeWithTag("player").captureToImage().toPixelMap()
        assertEquals(Color.Magenta, pixels[(50f * density).toInt(), (790f * density).toInt()])
    }

    @Test
    fun `reserveLowerContent anchors lower content without Glass so expand never switches layout modes`() {
        compose.setContent {
            PlayerGlassBudgetLayout(
                enabled = false, expandedCarousel = false, reserveLowerContent = true,
                modifier = Modifier.requiredSize(400.dp, 800.dp),
                header = { Box(Modifier.size(400.dp, 50.dp)) },
                artwork = { Box(Modifier.size(400.dp).testTag("cover")) },
                information = { Box(Modifier.size(400.dp, 250.dp).testTag("lower")) })
        }
        val density = compose.density.density
        val lower = compose.onNodeWithTag("lower").fetchSemanticsNode().boundsInRoot
        val cover = compose.onNodeWithTag("cover").fetchSemanticsNode().boundsInRoot
        assertEquals(800f * density, lower.bottom, 1f)
        assertEquals(400f * density, cover.height, 1f)
        assertEquals(50f * density, cover.top, 1f)
    }

    @Test
    fun `lower overflow scrolls every optional row into reach without crossing the header`() {
        compose.setContent {
            PlayerGlassBudgetLayout(
                enabled = false, reserveLowerContent = true,
                modifier = Modifier.requiredSize(400.dp, 220.dp),
                header = { Box(Modifier.size(400.dp, 50.dp).testTag("overflow.header")) },
                artwork = { Box(Modifier.size(400.dp).testTag("overflow.cover")) },
                information = {
                    Box(Modifier.size(400.dp, 200.dp).testTag("overflow.information"))
                    Box(Modifier.size(400.dp, 100.dp).testTag("overflow.actions"))
                })
        }
        val density = compose.density.density
        val header = compose.onNodeWithTag("overflow.header").fetchSemanticsNode().boundsInRoot
        assertEquals(50f * density, header.bottom, 1f)
        assertEquals(50f * density,
            compose.onNodeWithTag("overflow.information").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, 1_000f * density)
        }
        val actions = compose.onNodeWithTag("overflow.actions").fetchSemanticsNode().boundsInRoot
        assertEquals(220f * density, actions.bottom, 1f)
        assertEquals(100f * density, actions.height, 1f)
        assertTrue(actions.top >= header.bottom)
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, -1_000f * density)
        }
        assertEquals(50f * density,
            compose.onNodeWithTag("overflow.information").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test
    fun `unbounded portrait uses a finite natural artwork slot in compact and flexible modes`() {
        val expand = mutableStateOf(false)
        compose.setContent {
            Column(Modifier.requiredSize(400.dp, 900.dp).verticalScroll(rememberScrollState())) {
                PlayerGlassBudgetLayout(enabled = false, prioritizeArtwork = true,
                    expandInformation = expand.value, modifier = Modifier.width(400.dp).testTag("unbounded.player"),
                    header = { Box(Modifier.size(400.dp, 50.dp)) },
                    artwork = { Box(Modifier.weight(1f).fillMaxWidth().testTag("unbounded.artwork")) },
                    information = { Box(Modifier.size(400.dp, 170.dp).testTag("unbounded.lower")) })
            }
        }
        for (expanded in listOf(false, true, false)) {
            compose.runOnIdle { expand.value = expanded }
            val player = compose.onNodeWithTag("unbounded.player").fetchSemanticsNode().boundsInRoot
            val artwork = compose.onNodeWithTag("unbounded.artwork").fetchSemanticsNode().boundsInRoot
            val lower = compose.onNodeWithTag("unbounded.lower").fetchSemanticsNode().boundsInRoot
            assertEquals(620f * compose.density.density, player.height, 1f)
            assertEquals(400f * compose.density.density, artwork.height, 1f)
            assertEquals(artwork.bottom, lower.top, 1f)
            assertEquals(player.bottom, lower.bottom, 1f)
        }
    }

    @Test
    fun `upward and downward touch gestures move overflowing lower rows in the expected direction`() {
        compose.setContent {
            PlayerGlassBudgetLayout(enabled = false, reserveLowerContent = true,
                modifier = Modifier.requiredSize(400.dp, 220.dp),
                header = { Box(Modifier.size(400.dp, 50.dp)) }, artwork = {},
                information = {
                    Box(Modifier.size(400.dp, 200.dp).testTag("touch.information"))
                    Box(Modifier.size(400.dp, 100.dp).testTag("touch.actions"))
                })
        }
        val viewport = compose.onNode(hasScrollAction())
        repeat(2) { viewport.performTouchInput { swipeUp(durationMillis = 500) } }
        val actions = compose.onNodeWithTag("touch.actions").fetchSemanticsNode().boundsInRoot
        assertEquals(220f * compose.density.density, actions.bottom, 1f)
        assertEquals(100f * compose.density.density, actions.height, 1f)
        repeat(2) { viewport.performTouchInput { swipeDown(durationMillis = 500) } }
        assertEquals(50f * compose.density.density,
            compose.onNodeWithTag("touch.information").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test
    fun `scroll fallback starts only beyond exact available lower height and resets when it fits`() {
        val lowerHeight = mutableStateOf(250.dp)
        compose.setContent {
            PlayerGlassBudgetLayout(
                enabled = true,
                modifier = Modifier.requiredSize(400.dp, 300.dp),
                header = { Box(Modifier.size(400.dp, 50.dp)) },
                artwork = { Box(Modifier.size(400.dp)) },
                information = { Box(Modifier.size(400.dp, lowerHeight.value).testTag("threshold.lower")) })
        }
        compose.onNode(hasScrollAction()).assertDoesNotExist()
        compose.runOnIdle { lowerHeight.value = 251.dp }
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, 10f * compose.density.density)
        }
        compose.runOnIdle { lowerHeight.value = 249.dp }
        compose.onNode(hasScrollAction()).assertDoesNotExist()
        val lower = compose.onNodeWithTag("threshold.lower").fetchSemanticsNode().boundsInRoot
        assertEquals(300f * compose.density.density, lower.bottom, 1f)
        assertEquals(249f * compose.density.density, lower.height, 1f)
    }
}
