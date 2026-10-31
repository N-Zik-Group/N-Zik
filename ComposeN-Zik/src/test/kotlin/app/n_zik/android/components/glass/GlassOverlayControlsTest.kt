package app.n_zik.android.components.glass

import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasClickAction
import app.n_zik.android.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.player.LocalPlayerTransportGeometry
import app.n_zik.android.components.player.PlayerTransportGeometry
import app.n_zik.android.components.player.playerTransportGeometryHost
import app.n_zik.android.components.player.PlayerInformationGlassBlock
import app.n_zik.android.components.player.PlayerInformationGlassRegion
import app.n_zik.android.components.player.PlayerQueueDurationGlass
import app.n_zik.android.components.player.PlayerInfoGlassRail
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.player.PlayerInformationVerticalPadding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w900dp-h900dp")
class GlassOverlayControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `utilities clear edges and settings stays at trailing corner in both orientations`() {
        var landscape by mutableStateOf(false)
        var thumbnail by mutableStateOf(true)
        var circular by mutableStateOf(false)
        compose.setContent {
            Box(Modifier.size(if (landscape) 800.dp else 360.dp, if (landscape) 360.dp else 800.dp)
                .testTag("viewport")) {
                GlassOverlayControls(thumbnail, circular, Modifier.align(Alignment.BottomCenter), cornerSeatSize = 32.dp, centerContent = {
                    Box(Modifier.size(32.dp).testTag("previous"))
                    Box(Modifier.size(32.dp).testTag("page"))
                    Box(Modifier.size(32.dp).testTag("next"))
                }) {
                    Box(Modifier.size(32.dp).testTag("settings"))
                }
            }
        }
        for (wide in listOf(false, true)) for (shown in listOf(false, true)) for (round in listOf(false, true)) {
            compose.runOnIdle { landscape = wide; thumbnail = shown; circular = round }
            val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
            val gear = compose.onNodeWithTag("settings").fetchSemanticsNode().boundsInRoot
            val previous = compose.onNodeWithTag("previous").fetchSemanticsNode().boundsInRoot
            val next = compose.onNodeWithTag("next").fetchSemanticsNode().boundsInRoot
            val density = viewport.width / if (wide) 800f else 360f
            val bottom = overlayControlsBottomInset(shown, round).value
            assertEquals(10f * density, viewport.right - gear.right, .5f)
            assertEquals((bottom + 9f) * density, viewport.bottom - gear.bottom, .5f)
            assertEquals(previous.center.y, gear.center.y, .5f)
            assertEquals(viewport.center.x, (previous.left + next.right) / 2f, .5f)
            assertTrue("Page navigation must leave independent room for corner settings", next.right < gear.left)
        }
    }

    @Test fun `narrow row keeps centered navigation separate from corner settings`() {
        compose.setContent {
            GlassOverlayControls(true, false, Modifier.size(180.dp, 50.dp), centerContent = {
                Box(Modifier.size(32.dp).testTag("narrow.prev"))
                Box(Modifier.size(32.dp))
                Box(Modifier.size(32.dp).testTag("narrow.next"))
            }) { Box(Modifier.size(32.dp).testTag("narrow.gear")) }
        }
        val next = compose.onNodeWithTag("narrow.next").fetchSemanticsNode().boundsInRoot
        val gear = compose.onNodeWithTag("narrow.gear").fetchSemanticsNode().boundsInRoot
        val gap = with(compose.density) { 8.dp.toPx() }
        assertTrue("Narrow layout must keep navigation outside the settings slot", gear.left - next.right >= gap - .5f)
    }

    @Test fun `absent thumbnail uses the actual host edge without a saved circular cover reserve`() {
        assertEquals(0.dp, overlayControlsBottomInset(false, false))
        assertEquals(16.dp, overlayControlsBottomInset(true, true))
        assertEquals(overlayControlsBottomInset(false, false), overlayControlsBottomInset(false, true))
    }

    @Test fun `real information plate remains separated from Android canvas for normal and expanded regions`() {
        var glass by mutableStateOf(true)
        var expanded by mutableStateOf(false)
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            val settings = GlassSettings(enabled = glass)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette,
                    typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape),
                LocalGlassPreviewSettings provides settings,
                LocalPlayerTransportGeometry provides remember { PlayerTransportGeometry() },
            ) {
                Column(Modifier.size(360.dp, 700.dp).playerTransportGeometryHost()) {
                    Box(Modifier.size(360.dp, if (expanded) 320.dp else 260.dp)) {
                        GlassOverlayScene(Color.Transparent,
                            Modifier.fillMaxSize().glassOverlayCanvasViewport(settings).testTag("real.info.overlay"), scene = {
                                AndroidView(factory = { OverlayCanvasView(it) },
                                    modifier = Modifier.fillMaxSize().testTag("real.info.canvas"))
                            }) {
                            Box(Modifier.align(Alignment.BottomEnd).size(32.dp).testTag("real.info.settings"))
                        }
                    }
                    PlayerInformationGlassBlock(settings = settings) {
                        Column {
                            PlayerInformationGlassRegion("queueDuration") {
                                PlayerQueueDurationGlass { Box(Modifier.size(60.dp, 15.dp)) }
                            }
                            Spacer(Modifier.height(8.dp))
                            PlayerInformationGlassRegion("info") {
                                PlayerInfoGlassRail(true) { Box(Modifier.size(240.dp, if (expanded) 70.dp else 42.dp)) }
                            }
                            PlayerInformationGlassRegion("timeline", vertical = true) {
                                Box(Modifier.size(240.dp, if (expanded) 60.dp else 35.dp))
                            }
                        }
                    }
                    BasicText("Player controller")
                }
            }
        }
        for (wide in listOf(false, true)) for (enabled in listOf(true, false, true)) {
            compose.runOnIdle { expanded = wide; glass = enabled }
            compose.waitForIdle()
            val canvas = compose.onNodeWithTag("real.info.canvas").fetchSemanticsNode().boundsInRoot
            assertTrue(canvas.height > 0f)
            val overlay = compose.onNodeWithTag("real.info.overlay").fetchSemanticsNode().boundsInRoot
            val background = compose.onNodeWithTag("glass.overlay.scene.background").fetchSemanticsNode().boundsInRoot
            val settings = compose.onNodeWithTag("real.info.settings").fetchSemanticsNode().boundsInRoot
            assertEquals("Background and Android canvas share complete overlay bounds", background, canvas)
            assertEquals("Controls follow the component bottom", canvas.bottom, settings.bottom, .5f)
            assertEquals("The visible component shrinks with its scene", canvas.bottom, overlay.bottom, .5f)
            if (enabled) {
                val surface = compose.onNodeWithTag("player.informationBlock.surface").fetchSemanticsNode().boundsInRoot
                val gap = with(compose.density) { PlayerInformationVerticalPadding.toPx() }
                assertTrue("Real plate must clear the rendered AndroidView: $surface vs $canvas", surface.top - canvas.bottom >= gap - 1f)
            } else {
                val expected = with(compose.density) { (if (wide) 320.dp else 260.dp).toPx() }
                assertEquals(expected, canvas.height, .5f)
            }
        }
    }

    @Test fun `canvas reserve stays stable across all area modes and clears when the master is off`() {
        GlassAreaMode.entries.forEach { mode ->
            val settings = GlassSettings(areaModes = mapOf(GlassArea.ActionButtons to mode))
            assertEquals(PlayerInformationVerticalPadding * 2, overlayCanvasBottomReserve(settings))
            assertEquals(0.dp, overlayCanvasBottomReserve(settings.copy(enabled = false)))
        }
    }


    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `real overlay scene draws Android canvas and lyrics in coverless measured host above controller`() {
        var glass by mutableStateOf(true)
        var thumbnail by mutableStateOf(false)
        compose.setContent {
            val settings = GlassSettings(enabled = glass)
            Column(Modifier.size(360.dp, 600.dp).background(Color.White)) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxSize()) {
                    // With no cover, this is the only host child, matching the real Player branch.
                    if (thumbnail) Box(Modifier.size(260.dp).background(Color.Green))
                    GlassOverlayHost(active = true, settings = settings, modifier = Modifier.testTag("overlay.host")) {
                        GlassOverlayScene(Color.Transparent,
                            Modifier.fillMaxSize().glassOverlayCanvasViewport(settings, showThumbnail = thumbnail).testTag("overlay.scene"), scene = {
                                AndroidView(factory = { context -> OverlayCanvasView(context) },
                                    modifier = Modifier.fillMaxSize().testTag("visualizer.canvas"))
                                Image(ColorPainter(Color.Magenta), "Cover content",
                                    Modifier.size(24.dp).testTag("scene.image"))
                                BasicText("Visible lyrics", style = TextStyle(color = Color.Black),
                                    modifier = Modifier.align(Alignment.Center).testTag("lyrics.content"))
                            }) {
                            BasicText("Settings", modifier = Modifier.align(Alignment.BottomEnd))
                        }
                    }
                }
                Box(Modifier.size(360.dp, 80.dp)) { BasicText("Metadata") }
                Box(Modifier.size(360.dp, 60.dp)) { BasicText("Controller") }
            }
        }
        for (enabled in listOf(true, false, true)) for (cover in listOf(false, true)) {
            compose.runOnIdle { glass = enabled; thumbnail = cover }
            compose.onNodeWithText("Visible lyrics").assertIsDisplayed()
            compose.onNodeWithTag("scene.image").assertIsDisplayed()
            compose.onNodeWithText("Controller").assertIsDisplayed()
            val scene = compose.onNodeWithTag("overlay.scene").fetchSemanticsNode().boundsInRoot
            val canvas = compose.onNodeWithTag("visualizer.canvas").fetchSemanticsNode().boundsInRoot
            assertTrue("Scene must measure even when no cover sibling is present", scene.height > 0f)
            val expectedComponentHeight = with(compose.density) {
                (if (enabled && !cover) 440.dp else 460.dp).toPx()
            }
            assertEquals("A cover must not gain an arbitrary inner hole", expectedComponentHeight, scene.height, .5f)
            assertTrue("The AndroidView must receive positive drawing bounds", canvas.height > 0f)
            val pixels = compose.onNodeWithTag("overlay.scene").captureToImage().toPixelMap()
            val drawn = pixels[pixels.width / 2, pixels.height / 4]
            assertTrue("Visualizer's real View canvas must remain visible", drawn.green > .8f && drawn.blue > .8f && drawn.red < .2f)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `native Lyrics and Visualizer seats keep equal visible rim margins with and without cover`() {
        var glass by mutableStateOf(false)
        var cover by mutableStateOf(false)
        var visualizer by mutableStateOf(false)
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape)) {
                Box(Modifier.size(300.dp, 120.dp).background(Color.Black).testTag("corner.viewport")) {
                    GlassOverlayControls(cover, false, Modifier.align(Alignment.BottomCenter),
                        cornerSeatSize = if (visualizer) 32.dp else 44.dp) {
                        GlassOverlayIconButton({}, R.drawable.settings, Color.White,
                            modifier = if (visualizer) Modifier.size(32.dp) else Modifier.padding(4.dp),
                            iconSize = 24.dp, contentPadding = if (visualizer) 0.dp else 6.dp,
                            settings = GlassSettings(enabled = glass, quality = GlassQuality.Performance))
                    }
                }
            }
        }
        for (enabled in listOf(false, true)) for (shown in listOf(false, true)) for (vis in listOf(false, true)) {
            compose.runOnIdle { glass = enabled; cover = shown; visualizer = vis }
            val viewport = compose.onNodeWithTag("corner.viewport").fetchSemanticsNode().boundsInRoot
            val pane = compose.onNode(hasClickAction()).fetchSemanticsNode().boundsInRoot
            val six = with(compose.density) { 6.dp.toPx() }
            assertEquals("Both native seats preserve the same pane margin", six, viewport.right - pane.right, .5f)
            assertEquals(with(compose.density) { 40.dp.toPx() }, pane.width, .5f)
            val pixels = compose.onNodeWithTag("corner.viewport").captureToImage().toPixelMap()
            val strip = with(compose.density) { 5.dp.roundToPx() }
            for (x in pixels.width - strip until pixels.width) for (y in 0 until pixels.height) {
                val color = pixels[x, y]
                assertTrue("The pane rim cannot touch the viewport edge", maxOf(color.red, color.green, color.blue) < .25f)
            }
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `Lyrics and both Visualizer clips keep coverless canvas corners square in native and Glass`() {
        var glass by mutableStateOf(false)
        var cover by mutableStateOf(false)
        var visualizerWrapper by mutableStateOf(false)
        var shapeIndex by mutableStateOf(0)
        val shapes = listOf(RectangleShape, RoundedCornerShape(4.dp), RoundedCornerShape(16.dp),
            RoundedCornerShape(32.dp), CircleShape)
        compose.setContent {
            val settings = GlassSettings(enabled = glass)
            val shape = overlayViewportShape(cover, shapes[shapeIndex])
            Box(Modifier.size(140.dp).background(Color.Blue).testTag("shape.viewport")) {
                // Visualizer has an outer views wrapper as well as the renderer clip.
                // Lyrics applies the same policy at its single scene clip.
                Box(Modifier.size(120.dp).then(if (visualizerWrapper) Modifier.clip(shape) else Modifier)) {
                    GlassOverlayScene(Color.Cyan,
                        Modifier.fillMaxSize().glassOverlayCanvasViewport(settings, showThumbnail = cover).clip(shape),
                        scene = { AndroidView(factory = { OverlayCanvasView(it) }, modifier = Modifier.fillMaxSize()) }) {}
                }
            }
        }
        for (enabled in listOf(false, true)) for (shown in listOf(false, true))
            for (wrapper in listOf(false, true)) for (index in shapes.indices) {
                compose.runOnIdle { glass = enabled; cover = shown; visualizerWrapper = wrapper; shapeIndex = index }
                val pixels = compose.onNodeWithTag("shape.viewport").captureToImage().toPixelMap()
                val corner = pixels[0, 0]
                val center = pixels[pixels.width / 3, pixels.height / 3]
                assertTrue("The real scene and AndroidView remain rendered", center.green > .8f && center.blue > .8f)
                if (!shown || index == 0) {
                    assertTrue("Coverless canvases preserve their complete rectangular corner", corner.green > .8f && corner.blue > .8f)
                } else {
                    assertTrue("Artwork overlays preserve the selected thumbnail crop", corner.green < .2f && corner.blue > .8f)
                }
            }
    }

    @Test fun `inactive overlay leaves the real cover clickable before opening and after closing`() {
        var glass by mutableStateOf(true)
        var active by mutableStateOf(false)
        var coverClicks = 0
        compose.setContent {
            BoxWithConstraints(Modifier.size(300.dp, 300.dp)) {
                Image(ColorPainter(Color.Blue), "Actual cover", Modifier.fillMaxSize()
                    .testTag("clickable.cover").clickable { coverClicks++; active = true })
                GlassOverlayHost(active, GlassSettings(enabled = glass),
                    Modifier.testTag("active.host").clickable { active = false }) {
                    GlassOverlayScene(Color.Black, Modifier.fillMaxSize().testTag("active.canvas"),
                        scene = { BasicText("Overlay content") }) {}
                }
            }
        }
        for (enabled in listOf(true, false)) {
            compose.runOnIdle { glass = enabled; active = false; coverClicks = 0 }
            compose.onNodeWithTag("active.host").assertDoesNotExist()
            compose.onNodeWithTag("active.canvas", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("clickable.cover").performTouchInput { click() }
            compose.runOnIdle { assertEquals(1, coverClicks); assertTrue("Cover opens overlay", active) }
            val opened = compose.onNodeWithTag("active.canvas", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("Opened canvas has positive measured bounds: $opened", opened.width > 0f && opened.height > 0f)
            compose.onNodeWithTag("active.canvas", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("active.host").performClick()
            compose.onNodeWithTag("active.host").assertDoesNotExist()
            compose.onNodeWithTag("active.canvas", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("clickable.cover").performTouchInput { click() }
            compose.runOnIdle { assertEquals(2, coverClicks) }
        }
    }


    private class OverlayCanvasView(context: Context) : View(context) {
        override fun onDraw(canvas: Canvas) { canvas.drawColor(android.graphics.Color.CYAN) }
    }

}
