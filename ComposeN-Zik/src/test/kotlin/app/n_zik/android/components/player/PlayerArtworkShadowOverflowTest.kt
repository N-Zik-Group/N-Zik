package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.it.fast4x.rimusic.enums.ThumbnailType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import app.n_zik.android.components.player.slide.itemZIndexFor
import app.n_zik.android.components.player.slide.playerThumbnailCover
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class PlayerArtworkShadowOverflowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `information Glass samples rear artwork replay with live color alpha and fade but excludes foreground`() {
        var image by mutableStateOf(Color.Red)
        var opacity by mutableStateOf(1f)
        var fade by mutableStateOf(false)
        var showProbe by mutableStateOf(false)
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            val overflow = rememberPlayerArtworkShadowOverflow()
            val background = rememberCanvasBackdrop { drawRect(Color.Blue) }
            val source = rememberPlayerArtworkControlsBackdrop(background, overflow, enabled = true)
            val settings = GlassSettings(enabled = true, blur = 0f, film = 0f, rim = 0f,
                depth = 0f, amount = 0f, vibrancy = 0f)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides settings,
                LocalGlassContainerBackdrop provides source,
                LocalGlassBackdrop provides source,
            ) {
                Box(Modifier.size(240.dp, 280.dp).background(Color.Blue).testTag("glass.rear.scene")) {
                    Box(Modifier.size(240.dp).playerArtworkShadowOverflow(overflow)) {
                        Box(Modifier.align(Alignment.Center).size(100.dp)
                            .playerArtworkOverflowViewport(overflow).clipToBounds()
                            .playerArtworkOverflowFade(overflow, fade, .25f)) {
                            Box(Modifier.align(Alignment.Center).requiredSize(200.dp)
                                .graphicsLayer { alpha = opacity }
                                .registerPlayerArtworkShadow(overflow, RoundedCornerShape(0.dp),
                                    alpha = { opacity }, zIndex = { 0f }, shadow = false)
                                .drawBehind { drawRect(image) })
                        }
                        PlayerInformationGlassBlock(
                            modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-40).dp),
                            settings = settings, landscape = false,
                        ) {
                            PlayerInformationGlassRegion("info") {
                                Box(Modifier.size(120.dp, 20.dp), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(4.dp).background(Color.Magenta))
                                }
                            }
                        }
                    }
                    if (showProbe) Box(Modifier.size(240.dp).testTag("glass.rear.source")
                        .drawPlainBackdrop(source, { RectangleShape }, effects = {}))
                }
            }
        }
        fun assertColor(message: String, expected: Color, actual: Color) {
            assertEquals("$message red", expected.red, actual.red, .03f)
            assertEquals("$message green", expected.green, actual.green, .03f)
            assertEquals("$message blue", expected.blue, actual.blue, .03f)
        }
        for ((color, alpha, faded) in listOf(Triple(Color.Red, 1f, false),
            Triple(Color.Green, .5f, false), Triple(Color.Green, .5f, true))) {
            compose.runOnIdle { image = color; opacity = alpha; fade = faded; showProbe = false }
            val scene = compose.onNodeWithTag("glass.rear.scene").captureToImage().toPixelMap()
            val behind = scene[35, 185]
            assertTrue("The real rear card must paint below the pager", behind.red > .05f || behind.green > .05f)
            assertColor("Information material must sample the same rear pixels", behind, scene[120, 185])
            assertColor("Foreground information stays above its material", Color.Magenta, scene[120, 190])
            assertColor("Replay remains outside navigation", Color.Blue, scene[120, 260])
            compose.runOnIdle { showProbe = true }
            val source = compose.onNodeWithTag("glass.rear.source").captureToImage().toPixelMap()
            assertColor("The source excludes foreground information", scene[35, 190], source[120, 190])
            assertColor("The source excludes the selected image inside the pager", Color.Blue, source[120, 120])
        }
    }

    @Test fun `rear image pixels overflow both pager edges behind foreground and above navigation`() =
        exerciseOverflow(offscreen = false)

    @Test fun `offscreen pager fading cannot crop rear image overflow`() =
        exerciseOverflow(offscreen = true)

    @Test fun `a scrolling carousel replays colored images with their current scale and alpha`() =
        exerciseOverflow(offscreen = true, initialOffset = .25f)

    @Test fun `cached foreground preserves initial live replacement and alpha of overflow images`() {
        var image by mutableStateOf(Color.Red)
        var opacity by mutableStateOf(1f)
        compose.setContent {
            val overflow = rememberPlayerArtworkShadowOverflow()
            Box(Modifier.size(200.dp).background(Color.White).testTag("cached.scene")) {
                Box(Modifier.fillMaxSize().playerArtworkShadowOverflow(overflow)) {
                    Box(Modifier.align(Alignment.Center).size(100.dp)
                        .playerArtworkOverflowViewport(overflow).clipToBounds()) {
                        Box(Modifier.align(Alignment.Center).requiredSize(160.dp)
                            .graphicsLayer { alpha = opacity }
                            .registerPlayerArtworkShadow(
                                overflow = overflow,
                                shape = RoundedCornerShape(0.dp),
                                alpha = { opacity },
                                zIndex = { 0f },
                                shadow = false,
                            ).drawBehind { drawRect(image) })
                    }
                }
            }
        }
        compose.waitForIdle()
        fun pixels() = compose.onNodeWithTag("cached.scene").captureToImage().toPixelMap()
        assertEquals("Initial overflow must already contain the loaded image", Color.Red, pixels()[100, 30])
        compose.runOnIdle { opacity = .5f }
        compose.waitForIdle()
        val faded = pixels()[100, 30]
        assertEquals(1f, faded.red, .02f)
        assertEquals(.5f, faded.green, .02f)
        compose.runOnIdle { image = Color.Green }
        compose.waitForIdle()
        val updated = pixels()
        assertEquals("A loaded replacement must update overflow without a pager movement", 1f, updated[100, 30].green, .02f)
        assertEquals(.5f, updated[100, 30].red, .02f)
        val visible = updated[100, 100]
        val overflowPixel = updated[100, 30]
        // Separate alpha compositing paths may round an 8-bit channel differently.
        val channelTolerance = 1f / 255f + 1e-6f
        assertEquals("The visible card and overflow use the same current red", visible.red, overflowPixel.red, channelTolerance)
        assertEquals("The visible card and overflow use the same current green", visible.green, overflowPixel.green, channelTolerance)
        assertEquals("The visible card and overflow use the same current blue", visible.blue, overflowPixel.blue, channelTolerance)
        assertEquals("The visible card and overflow use the same current alpha", visible.alpha, overflowPixel.alpha, channelTolerance)
    }

    @Test fun `fade stays continuous through both pager boundaries after movement and responds to zero and disabled`() {
        lateinit var pager: PagerState
        lateinit var scope: CoroutineScope
        var strength by mutableStateOf(.25f)
        var enabled by mutableStateOf(true)
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            val overflow = rememberPlayerArtworkShadowOverflow()
            pager = rememberPagerState(initialPage = 2) { 7 }
            scope = rememberCoroutineScope()
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides GlassSettings(enabled = false),
            ) {
                Box(Modifier.size(320.dp, 500.dp).background(Color.White).testTag("scene")) {
                    Box(Modifier.size(320.dp, 360.dp).playerArtworkShadowOverflow(overflow)) {
                        Box(Modifier.offset(80.dp, 10.dp).size(160.dp, 10.dp).background(Color.Magenta))
                        Box(Modifier.offset(80.dp, 340.dp).size(160.dp, 10.dp).background(Color.Magenta))
                        Box(Modifier.align(Alignment.Center).size(300.dp).playerArtworkOverflowViewport(overflow)) {
                            VerticalPager(
                                state = pager,
                                modifier = Modifier.fillMaxSize().playerArtworkOverflowFade(overflow, enabled, strength),
                                pageSize = PageSize.Fixed(300.dp),
                                pageSpacing = (-240).dp,
                                contentPadding = PaddingValues(bottom = 240.dp),
                                beyondViewportPageCount = 2,
                            ) { page ->
                                Box(Modifier.playerThumbnailCover(
                                    pagerState = pager,
                                    page = page,
                                    padding = 0.dp,
                                    carousel = true,
                                    scaleAlways = false,
                                    scaleStart = .9f,
                                    thumbnailType = ThumbnailType.Modern,
                                    showCoverThumbnailAnimation = false,
                                    onTap = {},
                                    onLongClick = {},
                                    shadowOverflow = overflow,
                                ).zIndex(itemZIndexFor(page, pager.currentPage)).background(Color.Blue)
                                    .testTag("card.$page"))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        fun screenshot() = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
        fun assertContinuous(pixels: PixelMap) {
            // Sample the same image on either side, excluding the unrelated foreground labels.
            for ((outside, inside) in listOf(29 to 30, 330 to 329)) {
                assertTrue("The fade must not restart opaque at the pager boundary",
                    abs(pixels[160, outside].red - pixels[160, inside].red) < .08f)
                assertTrue("The boundary must be visibly faded", pixels[160, outside].red > .35f)
            }
            assertTrue("The upper stack continues fading towards the header", pixels[160, 1].red > pixels[160, 25].red)
            assertTrue("The lower stack continues fading towards information", pixels[160, 358].red > pixels[160, 335].red)
            assertTrue("The selected cover center remains opaque", pixels[160, 180].red < .02f)
            assertEquals(Color.Magenta, pixels[160, 15])
            assertEquals(Color.Magenta, pixels[160, 345])
            for (y in 360 until 500) for (x in 0 until 320) assertEquals(Color.White, pixels[x, y])
        }
        assertContinuous(screenshot())
        compose.runOnIdle { scope.launch { pager.scrollToPage(3, .25f) } }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(3, pager.currentPage) }
        assertContinuous(screenshot())
        val front = compose.onNodeWithTag("card.3").fetchSemanticsNode().boundsInRoot
        assertEquals("Movement and fading must preserve the configured cover size", 273f, front.width, .5f)
        compose.runOnIdle { strength = 0f }
        compose.waitForIdle()
        val zero = screenshot()
        assertTrue("Zero restores unfaded rear artwork", zero[160, 29].red < .35f)
        compose.runOnIdle { strength = .25f; enabled = false }
        compose.waitForIdle()
        val disabled = screenshot()
        for (y in listOf(1, 25, 29, 30, 180, 329, 330, 335, 358)) {
            assertEquals("Disabled and zero must have identical rendering", zero[160, y], disabled[160, y])
        }
    }

    @Test fun `noncarousel resize retains visible shadows without empty card outlines behind controls`() {
        var viewportSize by mutableStateOf(300.dp)
        lateinit var overflow: PlayerArtworkShadowOverflow
        lateinit var pager: PagerState
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            overflow = rememberPlayerArtworkShadowOverflow()
            pager = rememberPagerState(initialPage = 4) { 12 }
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides GlassSettings(enabled = false),
            ) {
                Box(Modifier.size(320.dp, 700.dp).background(Color.White).testTag("resized.scene")) {
                    Box(Modifier.fillMaxSize().playerArtworkShadowOverflow(overflow)) {
                        Box(Modifier.offset(10.dp, 80.dp).size(viewportSize)
                            .playerArtworkOverflowViewport(overflow)) {
                            VerticalPager(
                                state = pager,
                                modifier = Modifier.fillMaxSize(),
                                pageSize = PageSize.Fixed(viewportSize),
                                pageSpacing = 60.dp - viewportSize,
                                contentPadding = PaddingValues(bottom = viewportSize - 60.dp),
                                beyondViewportPageCount = 2,
                            ) { page ->
                                Box(Modifier.playerThumbnailCover(
                                    pagerState = pager,
                                    page = page,
                                    padding = 0.dp,
                                    carousel = false,
                                    scaleAlways = false,
                                    scaleStart = .9f,
                                    thumbnailType = ThumbnailType.Modern,
                                    showCoverThumbnailAnimation = false,
                                    onTap = {},
                                    onLongClick = {},
                                    shadowOverflow = overflow,
                                ).zIndex(itemZIndexFor(page, pager.currentPage)).background(Color.Blue))
                            }
                        }
                    }
                }
            }
        }
        for (height in listOf(300, 160, 300, 160)) {
            compose.runOnIdle { viewportSize = height.dp }
            compose.waitForIdle()
            compose.runOnIdle {
                val visible = pager.layoutInfo.visiblePagesInfo.size
                assertTrue("The regression includes retained beyond-viewport pages", overflow.shadows.size > visible)
                assertEquals("Only currently visible pages may publish shadow frames after resize",
                    visible, overflow.shadows.values.mapNotNull { it() }.size)
            }
            val pixels = compose.onNodeWithTag("resized.scene").captureToImage().toPixelMap()
            assertEquals("The selected artwork remains visible", Color.Blue, pixels[height / 2 + 10, height / 2 + 80])
            val viewportBottom = 80 + height
            var nearShadowPixels = 0
            var distantShadowPixels = 0
            for (y in viewportBottom until 700) for (x in 0 until 320) {
                val pixel = pixels[x, y]
                if (pixel.red < .98f) {
                    if (y < viewportBottom + 32) nearShadowPixels++ else distantShadowPixels++
                }
            }
            assertTrue("Real shadow blur still escapes the pager", nearShadowPixels > 10)
            assertEquals("Hidden noncarousel image bodies cannot leave empty shadow cards behind controls",
                0, distantShadowPixels)
        }
    }

    @Test fun `invalid empty and fully scrolled page geometry cannot paint overflow`() {
        val viewport = Rect(0f, 0f, 100f, 100f)
        assertFalse(artworkShadowIntersectsViewport(Rect(Float.NaN, 0f, 100f, 100f), viewport))
        assertFalse(artworkShadowIntersectsViewport(Rect(0f, 101f, 100f, 201f), viewport))
        assertFalse(artworkShadowIntersectsViewport(Rect(0f, 0f, 0f, 100f), viewport))
        assertTrue(artworkShadowIntersectsViewport(Rect(0f, -50f, 100f, 50f), viewport))
    }

    private fun exerciseOverflow(offscreen: Boolean, initialOffset: Float = 0f) {
        var backgroundClicks = 0
        var artworkClicks = 0
        lateinit var sceneCoordinates: LayoutCoordinates
        lateinit var frontCoordinates: LayoutCoordinates
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            val overflow = rememberPlayerArtworkShadowOverflow()
            val pager = rememberPagerState(initialPage = 2, initialPageOffsetFraction = initialOffset) { 5 }
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides GlassSettings(enabled = false),
            ) {
                Box(Modifier.size(320.dp, 500.dp).background(Color.White)
                    .clickable { backgroundClicks++ }.testTag("scene")
                    .onGloballyPositioned { sceneCoordinates = it }) {
                    Box(Modifier.size(320.dp, 360.dp).playerArtworkShadowOverflow(overflow)) {
                        // These precede the pager in draw order. The replay must still be behind them.
                        Box(Modifier.offset(80.dp, 10.dp).size(160.dp, 10.dp).background(Color.Magenta))
                        Box(Modifier.offset(80.dp, 340.dp).size(160.dp, 10.dp).background(Color.Magenta))
                        Box(Modifier.align(Alignment.Center).size(300.dp)
                            .playerArtworkOverflowViewport(overflow).testTag("viewport")) {
                            VerticalPager(
                                state = pager,
                                modifier = Modifier.fillMaxSize().then(if (offscreen)
                                    Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                    else Modifier),
                                pageSize = PageSize.Fixed(300.dp),
                                pageSpacing = (-240).dp,
                                contentPadding = PaddingValues(bottom = 240.dp),
                                beyondViewportPageCount = 2,
                            ) { page ->
                                Box(Modifier.playerThumbnailCover(
                                    pagerState = pager,
                                    page = page,
                                    padding = 0.dp,
                                    carousel = true,
                                    scaleAlways = false,
                                    scaleStart = .9f,
                                    thumbnailType = ThumbnailType.Modern,
                                    showCoverThumbnailAnimation = false,
                                    onTap = { artworkClicks++ },
                                    onLongClick = {},
                                    shadowOverflow = overflow,
                                ).zIndex(itemZIndexFor(page, pager.currentPage))
                                    .background(listOf(Color.Yellow, Color.Red, Color.Blue, Color.Green, Color.Cyan)[page])
                                    .testTag("card.$page")
                                    .onGloballyPositioned { if (page == 2) frontCoordinates = it })
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val scene = compose.onNodeWithTag("scene")
        val sceneOrigin = scene.fetchSemanticsNode().boundsInRoot.topLeft
        val front = compose.onNodeWithTag("card.2").fetchSemanticsNode().boundsInRoot.translate(-sceneOrigin)
        val viewport = compose.onNodeWithTag("viewport", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.translate(-sceneOrigin)
        val fullFront = sceneCoordinates.localBoundingBoxOf(frontCoordinates, clipBounds = false)
        assertEquals("Overflow must not shrink the configured image", 280f * (1f - .1f * initialOffset), fullFront.width, .5f)
        assertEquals("The complete transformed image stays square", fullFront.width, fullFront.height, .5f)
        // Semantics report the pager-clipped image, which loses 1.5 px at offset .25.
        val visibleFront = fullFront.intersect(viewport)
        assertEquals("Visible semantics retain the image width", visibleFront.width, front.width, .5f)
        assertEquals("Visible semantics follow pager clipping", visibleFront.height, front.height, .5f)
        val pixels = scene.captureToImage().toPixelMap()
        if (initialOffset > 0f) {
            assertTrue("The scrolling selected image crosses the pager edge", fullFront.top < viewport.top)
            val selectedOverflow = pixels[160, viewport.top.toInt() - 1]
            assertTrue("The selected image is replayed beyond its clipped semantics",
                selectedOverflow.blue > .9f && selectedOverflow.red < .15f)
        }
        var upperRearPixels = 0
        var lowerRearPixels = 0
        for (y in 0 until 500) for (x in 0 until 320) {
            val pixel = pixels[x, y]
            if (y < viewport.top && pixel.red > .6f && pixel.green < .35f && pixel.blue < .35f) upperRearPixels++
            if (y > viewport.bottom && pixel.green > .6f && pixel.red < .35f && pixel.blue < .35f) lowerRearPixels++
            if (y >= 360) assertEquals("Player clipping must protect every navigation pixel", Color.White, pixel)
        }
        assertTrue("Actual red rear cover pixels must escape the top of the pager", upperRearPixels > 100)
        assertTrue("Actual green rear cover pixels must escape the bottom of the pager", lowerRearPixels > 100)
        assertEquals("The header stays above the rear artwork", Color.Magenta, pixels[160, 15])
        assertEquals("The information stays above the rear artwork", Color.Magenta, pixels[160, 345])
        val frontPixel = pixels[160, front.center.y.toInt()]
        assertTrue("The selected image remains above the rear images", frontPixel.blue > .9f && frontPixel.red < .15f)
        // A point beyond the rounded top-left outline cannot become square image paint.
        val rearLeft = if (initialOffset == 0f) 34 else 37
        assertFalse("The rear image retains its rounded clip", pixels[rearLeft, 0].red > .6f && pixels[rearLeft, 0].green < .2f)
        scene.performTouchInput { click(Offset(160f, 25f)) }
        compose.runOnIdle {
            assertEquals("Replayed artwork has no independent click target", 1, backgroundClicks)
            assertEquals(0, artworkClicks)
        }
        scene.performTouchInput { click(Offset(160f, front.center.y)) }
        compose.runOnIdle { assertEquals(1, artworkClicks) }
    }
}
