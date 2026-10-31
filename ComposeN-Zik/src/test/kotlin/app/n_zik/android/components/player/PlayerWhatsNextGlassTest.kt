package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassContextArea
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
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
class PlayerWhatsNextGlassTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `landscape square cover and native carousel viewport end above unchanged toolbar bottom`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val stack = mutableStateOf(false)
        val upcoming = mutableStateOf(false)
        val nativeHeight = mutableStateOf(24.dp)
        val height = mutableStateOf(360.dp)
        val showArtwork = mutableStateOf(true)
        compose.setContent {
            AppearanceProvider {
                val settings = GlassSettings(areaModes = mapOf(GlassArea.ActionButtons to mode.value))
                val geometry = remember { PlayerTransportGeometry() }
                val toolbarLayout = rememberPlayerActionToolbarLayout()
                CompositionLocalProvider(LocalPlayerTransportGeometry provides geometry) {
                    Box(Modifier.requiredWidth(640.dp).requiredHeight(height.value)
                        .playerTransportGeometryHost().testTag("art.background")) {
                        PlayerLandscapeColumns(contentInsets = WindowInsets(left = 8, top = 24, right = 16, bottom = 12),
                            showArtworkColumn = showArtwork.value,
                            artwork = {
                                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    if (stack.value) {
                                        val carousel = playerCarouselGeometry(maxWidth, maxHeight, 20.dp, (-100).dp, true)
                                        VerticalPager(rememberPagerState(initialPage = 1) { 3 },
                                            pageSize = PageSize.Fixed(carousel.pageSize),
                                            pageSpacing = carousel.pageSpacing,
                                            contentPadding = PaddingValues(
                                                top = (maxHeight - carousel.pageSize) / 2,
                                                bottom = (maxHeight - carousel.pageSize) / 2 + (-carousel.pageSpacing).coerceAtLeast(0.dp)),
                                            beyondViewportPageCount = 2,
                                            modifier = Modifier.testTag("art.pager")) { page ->
                                            Box(Modifier.size(carousel.pageSize).padding(carousel.coverPadding).testTag("art.card.$page"))
                                        }
                                    } else Box(Modifier.size(minOf(maxWidth, maxHeight)).testTag("art.square"))
                                }
                            },
                            controls = {
                                Box(Modifier.weight(1f).fillMaxWidth().testTag("art.controls"))
                                Box(Modifier.fillMaxWidth().requiredHeight(toolbarLayout.hostHeight(
                                    if (upcoming.value) 90.dp + playerWhatsNextGlassExtraHeight(settings) else 50.dp))) {
                                    PlayerActionPanel(toolbarLayout, landscape = true) {
                                        if (upcoming.value) PlayerWhatsNextGlassSurface(Color.Black, settings, true, WindowInsets(0)) { surface ->
                                            Box(surface.fillMaxWidth().height(40.dp))
                                        }
                                        PlayerActionToolbar(listOf("a", "b").map { PlayerActionSlot(it, nativeHeight.value) {} },
                                            true, settings = settings, layout = toolbarLayout, horizontalInsets = WindowInsets(0), landscape = true)
                                    }
                                }
                            })
                    }
                }
            }
        }
        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        for (size in listOf(240.dp, 360.dp)) for (native in listOf(24.dp, 40.dp)) for (next in GlassAreaMode.entries) {
            compose.runOnIdle { height.value = size; nativeHeight.value = native; mode.value = next; upcoming.value = false; stack.value = false }
            val tag = if (next.hasMaterial) "player.actionToolbar.material" else "player.actionToolbar.native"
            val defaultToolbar = bounds(tag)
            for (queue in listOf(false, true)) {
                compose.runOnIdle { upcoming.value = queue; stack.value = false }
                val controls = bounds("art.controls")
                for (carousel in listOf(false, true)) {
                    compose.runOnIdle { stack.value = carousel }
                    val toolbar = bounds(tag)
                    val viewport = bounds("player.landscapeArtwork.viewport")
                    val cover = bounds(if (carousel) "art.card.1" else "art.square")
                    assertEquals("Artwork viewport ends at toolbar bottom", toolbar.bottom, viewport.bottom, 1f)
                    assertTrue("Selected cover stays above toolbar bottom", cover.bottom <= toolbar.bottom + 1f)
                    assertEquals("Cover keeps its square proportions", cover.width, cover.height, 1f)
                    if (carousel) assertTrue("Native pager clips the visible stack at the fitted viewport", bounds("art.pager").bottom <= toolbar.bottom + 1f)
                    assertEquals("Artwork mode cannot move controls", controls, bounds("art.controls"))
                    assertEquals("Artwork and Next cannot move the toolbar", defaultToolbar, toolbar)
                }
            }
        }
        val background = bounds("art.background")
        compose.runOnIdle { showArtwork.value = false }
        compose.onNodeWithTag("player.landscapeArtwork.viewport", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("Full-background preset keeps the whole background", background, bounds("art.background"))
    }

    @Test fun `landscape information including overflowing rail stays four dp below safe top without moving toolbar`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val railMode = mutableStateOf(GlassAreaMode.Disabled)
        val artwork = mutableStateOf(true)
        val upcoming = mutableStateOf(false)
        val nativeHeight = mutableStateOf(24.dp)
        val overflow = mutableStateOf(0.dp)
        compose.setContent {
            AppearanceProvider {
                val settings = GlassSettings(areaModes = mapOf(GlassArea.ActionButtons to mode.value),
                    contextModes = mapOf(
                        GlassContextArea(GlassScope.PlayerHeader, GlassArea.ActionButtons) to mode.value,
                        GlassContextArea(GlassScope.PlayerInformation, GlassArea.ActionButtons) to railMode.value,
                    ))
                val geometry = remember { PlayerTransportGeometry() }
                val layout = rememberPlayerActionToolbarLayout()
                CompositionLocalProvider(LocalPlayerTransportGeometry provides geometry) {
                    Box(Modifier.requiredWidth(640.dp).requiredHeight(360.dp).playerTransportGeometryHost().testTag("margin.background")) {
                        PlayerLandscapeColumns(contentInsets = WindowInsets(left = 8, top = 24, right = 16, bottom = 12),
                            showArtworkColumn = artwork.value,
                            artwork = { Box(Modifier.fillMaxWidth().height(40.dp)) },
                            controls = {
                                PlayerInformationGlassBlock(Modifier.playerLandscapeControlsMargin(settings).weight(1f),
                                    settings, landscape = true) {
                                    PlayerInformationGlassRegion("info") {
                                        Box(Modifier.fillMaxWidth().height(60.dp).testTag("margin.nativeInfo"))
                                        if (railMode.value.hasMaterial || railMode.value.hasContour)
                                            Box(Modifier.fillMaxWidth().height(60.dp).offset(y = -overflow.value)
                                                .playerInformationGlassVisualBounds().testTag("margin.overflowingRail"))
                                    }
                                }
                                Box(Modifier.fillMaxWidth().requiredHeight(layout.hostHeight(
                                    if (upcoming.value) 90.dp + playerWhatsNextGlassExtraHeight(settings) else 50.dp))) {
                                    PlayerActionPanel(layout, landscape = true) {
                                        if (upcoming.value) PlayerWhatsNextGlassSurface(Color.Black, settings, true, WindowInsets(0)) { surface ->
                                            Box(surface.fillMaxWidth().height(40.dp))
                                        }
                                        PlayerActionToolbar(listOf("a", "b").map { PlayerActionSlot(it, nativeHeight.value) {} },
                                            true, settings = settings, layout = layout, horizontalInsets = WindowInsets(0), landscape = true)
                                    }
                                }
                            })
                    }
                }
            }
        }
        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val scopes = GlassAreaMode.entries.map { it to it } + listOf(
            GlassAreaMode.Disabled to GlassAreaMode.GlassOnly,
            GlassAreaMode.Disabled to GlassAreaMode.ContourOnly,
        )
        for (split in listOf(true, false)) for (height in listOf(24.dp, 40.dp)) for ((next, rail) in scopes) {
            compose.runOnIdle {
                artwork.value = split; nativeHeight.value = height; mode.value = next; railMode.value = rail; upcoming.value = false
            }
            val tag = if (next.hasMaterial) "player.actionToolbar.material" else "player.actionToolbar.native"
            val defaultToolbar = bounds(tag)
            for (queue in listOf(false, true)) for (outset in listOf(0.dp, 6.dp, 18.dp)) {
                compose.runOnIdle { upcoming.value = queue; overflow.value = outset }
                val background = bounds("margin.background")
                val toolbar = bounds(tag)
                val headerVisible = next.hasMaterial || next.hasContour
                val info = bounds(if (headerVisible) "player.informationBlock.surface"
                    else if (rail.hasMaterial || rail.hasContour) "margin.overflowingRail" else "margin.nativeInfo")
                if (!headerVisible) compose.onNodeWithTag("player.informationBlock.surface", useUnmergedTree = true).assertDoesNotExist()
                val topGap = info.top - (background.top + 24f)
                assertEquals("Minimal safe margin split=$split height=$height header=$next rail=$rail next=$queue outset=$outset",
                    with(compose.density) { 4.dp.toPx() }, topGap, 1f)
                assertTrue("Information plate stays inside safe top", topGap >= 0f)
                assertEquals("Lower toolbar remains unchanged", defaultToolbar, toolbar)
            }
        }
    }

    @Test fun `landscape upcoming songs preserve the measured default toolbar baseline in every mode`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val upcoming = mutableStateOf(false)
        val nativeHeight = mutableStateOf(24.dp)
        compose.setContent {
            AppearanceProvider {
                val settings = GlassSettings(areaModes = mapOf(GlassArea.ActionButtons to mode.value))
                val layout = rememberPlayerActionToolbarLayout()
                Box(Modifier.requiredWidth(320.dp).requiredHeight(240.dp)) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().requiredHeight(
                        layout.hostHeight(if (upcoming.value) 90.dp + playerWhatsNextGlassExtraHeight(settings) else 50.dp)
                    ).testTag("panel.host")) {
                        PlayerActionPanel(layout, landscape = true) {
                            if (upcoming.value) PlayerWhatsNextGlassSurface(Color.Black, settings, true, WindowInsets(0)) { surface ->
                                Box(surface.fillMaxWidth().height(40.dp))
                            }
                            PlayerActionToolbar(listOf("a", "b").map { PlayerActionSlot(it, nativeHeight.value) {} },
                                true, settings = settings, layout = layout, horizontalInsets = WindowInsets(0), landscape = true)
                        }
                    }
                }
            }
        }
        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        for (height in listOf(24.dp, 40.dp)) for (next in GlassAreaMode.entries) {
            compose.runOnIdle { nativeHeight.value = height; mode.value = next; upcoming.value = false }
            val tag = if (next.hasMaterial) "player.actionToolbar.material" else "player.actionToolbar.native"
            val default = bounds(tag)
            val host = bounds("panel.host")
            assertEquals("Default remains centered for $height/$next", host.center.y, default.center.y, 1f)
            compose.runOnIdle { upcoming.value = true }
            assertEquals("Upcoming keeps the exact default baseline for $height/$next", default, bounds(tag))
            val upper = bounds("player.whatsNext.surface")
            assertTrue("Upcoming plate fits entirely inside its expanded host", upper.top >= bounds("panel.host").top)
            assertTrue("Upcoming plate clears the toolbar", upper.bottom < bounds(tag).top)
        }
    }

    @Test fun `only material adds plate breathing room while native modes retain geometry and single callback`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        var clicks = 0
        compose.setContent {
            AppearanceProvider {
                val settings = GlassSettings(areaModes = mapOf(GlassArea.ActionButtons to mode.value))
                Box(Modifier.requiredWidth(320.dp).testTag("parent")) {
                    PlayerWhatsNextGlassSurface(Color.Black, settings, landscape = false, horizontalInsets = WindowInsets(0)) { surface ->
                        Row(surface.padding(horizontal = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            PlayerWhatsNextAction(R.drawable.chevron_forward, Color.White, { clicks++ }, settings = settings)
                            Box(Modifier.weight(1f).height(40.dp).testTag("queue"))
                        }
                    }
                }
            }
        }
        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val parent = bounds("parent")
        val row = bounds("player.whatsNext.surface")
        assertEquals(parent.width * PlayerControlsWidthFraction, row.width, 1f)
        assertEquals(parent.center.x, row.center.x, 1f)
        val native = listOf("player.whatsNext.surface", "queue", "player.whatsNext.action").map(::bounds)
        GlassAreaMode.entries.forEach { next ->
            compose.runOnIdle { mode.value = next }
            val inset = with(compose.density) { if (next.hasMaterial) 6.dp.toPx() else 0f }
            val plate = native[0]
            val queue = native[1]
            val slot = native[2]
            val extraButtonWidth = with(compose.density) { if (next.hasMaterial) 15.dp.toPx() else 0f }
            val expectedSlot = if (next.hasMaterial) Rect(slot.left + inset, plate.top + inset,
                slot.right + inset + extraButtonWidth, plate.bottom + inset)
                else slot
            val expected = listOf(plate.copy(bottom = plate.bottom + inset * 2),
                queue.copy(left = queue.left + inset + extraButtonWidth, right = queue.right - inset,
                    top = queue.top + inset, bottom = queue.bottom + inset), expectedSlot)
            assertEquals(next.name, expected, listOf("player.whatsNext.surface", "queue", "player.whatsNext.action").map(::bounds))
            val canvas = compose.onNodeWithTag("player.whatsNext.action.canvas", useUnmergedTree = true)
            val canvasSize = if (next.hasMaterial) 40.dp else 25.dp
            canvas.assertWidthIsEqualTo(canvasSize).assertHeightIsEqualTo(canvasSize)
            if (next.hasMaterial) {
                val button = canvas.fetchSemanticsNode().boundsInRoot
                val pane = bounds("player.whatsNext.surface")
                assertTrue("Button has top breathing room", button.top >= pane.top + inset)
                assertTrue("Button has bottom breathing room", button.bottom <= pane.bottom - inset)
                assertTrue("Button clears the leading rim", button.left >= pane.left + inset)
            }
            compose.onAllNodes(hasClickAction()).assertCountEquals(1)
            canvas.performTouchInput { click() }
        }
        assertEquals(GlassAreaMode.entries.size, clicks)
    }

    @Test fun `glass plate leaves horizontal pager drag to its native owner without clicking cards`() {
        lateinit var pager: PagerState
        var cardClicks = 0
        var actionClicks = 0
        compose.setContent {
            AppearanceProvider {
                pager = rememberPagerState { 5 }
                val settings = GlassSettings()
                Box(Modifier.requiredWidth(320.dp).testTag("parent")) {
                    PlayerWhatsNextGlassSurface(Color.Black, settings, landscape = false, horizontalInsets = WindowInsets(0)) { surface ->
                        Row(surface.padding(horizontal = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            PlayerWhatsNextAction(R.drawable.play, Color.White, { actionClicks++ }, settings = settings)
                            HorizontalPager(pager, Modifier.weight(1f).height(40.dp).testTag("queue")) { index ->
                                Box(Modifier.fillMaxWidth().height(40.dp).background(Color.Blue)
                                    .clickable { cardClicks++ }.testTag("card.$index"))
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("queue").performTouchInput { swipeLeft() }
        compose.runOnIdle {
            assertTrue("Native pager must receive the drag", pager.currentPage > 0)
            assertEquals(0, cardClicks)
            assertEquals(0, actionClicks)
        }
        compose.onNodeWithTag("card.${pager.currentPage}").performTouchInput { click() }
        assertEquals(1, cardClicks)
    }

    @Test fun `upcoming plate shares toolbar edges in portrait landscape and every mode`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val landscape = mutableStateOf(false)
        compose.setContent {
            AppearanceProvider {
                val settings = GlassSettings(areaModes = mapOf(GlassArea.ActionButtons to mode.value))
                val insets = WindowInsets(left = 8.dp, right = 16.dp)
                Column(Modifier.requiredWidth(320.dp)) {
                    PlayerWhatsNextGlassSurface(Color.Black, settings, landscape.value, insets) { surface ->
                        Box(surface.fillMaxWidth().height(40.dp))
                    }
                    PlayerActionToolbar(listOf(PlayerActionSlot("a") {}, PlayerActionSlot("b") {}),
                        true, settings = settings, horizontalInsets = insets, landscape = landscape.value)
                }
            }
        }
        for (wide in listOf(false, true)) {
            for (next in GlassAreaMode.entries) {
                compose.runOnIdle { landscape.value = wide; mode.value = next }
                val upcoming = compose.onNodeWithTag("player.whatsNext.surface", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val toolbar = compose.onNodeWithTag(if (next.hasMaterial) "player.actionToolbar.material"
                    else "player.actionToolbar.native", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertEquals("Left edge $wide/$next", toolbar.left, upcoming.left, .01f)
                assertEquals("Right edge $wide/$next", toolbar.right, upcoming.right, .01f)
                assertEquals("Only material adds breathing room", with(compose.density) {
                    (if (next.hasMaterial) 52.dp else 40.dp).toPx()
                }, upcoming.height, .01f)
                assertTrue("Toolbar starts below the upcoming plate", toolbar.top >= upcoming.bottom)
            }
        }
    }

    @Test fun `full material navigation slot keeps six dp between button and next artwork`() {
        compose.setContent {
            AppearanceProvider {
                val settings = GlassSettings()
                Box(Modifier.requiredWidth(320.dp)) {
                    PlayerWhatsNextGlassSurface(Color.Black, settings, false, WindowInsets(0)) { surface ->
                        Row(surface.padding(horizontal = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            PlayerWhatsNextNavigation(isCurrent = false, settings = settings) {
                                PlayerWhatsNextAction(R.drawable.play, Color.White, {}, settings = settings)
                            }
                            Box(Modifier.weight(1f).height(40.dp).testTag("next.artwork"))
                        }
                    }
                }
            }
        }
        val button = compose.onNodeWithTag("player.whatsNext.action.canvas", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val artwork = compose.onNodeWithTag("next.artwork").fetchSemanticsNode().boundsInRoot
        assertEquals(with(compose.density) { 6.dp.toPx() }, artwork.left - button.right, .01f)
        compose.onNodeWithTag("player.whatsNext.action", useUnmergedTree = true)
            .assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
    }

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(12.dp)
        val scene = rememberCanvasBackdrop { drawRect(Color.Red) }
        CompositionLocalProvider(LocalGlassContainerBackdrop provides scene,
            LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            ), content = content)
    }
}
