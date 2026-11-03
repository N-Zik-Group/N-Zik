package app.n_zik.android.components.glass

import java.io.File
import kotlin.math.abs

import android.graphics.Bitmap

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.ui.toggles.Switch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
class GlassSwitchTest {
    @get:Rule val compose = createComposeRule()
    private val settings = mutableStateOf(GlassSettings(quality = GlassQuality.Performance))
    private val roundness = mutableStateOf(12.dp)

    @Composable private fun Theme(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(roundness.value)
        CompositionLocalProvider(LocalGlassPreviewSettings provides settings.value,
            LocalAppearance provides Appearance(colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape)) { content() }
    }

    @Test @Config(sdk = [33])
    fun `API33 refractive switch renders the combined track and parent during interaction`() {
        // Robolectric's Skia rejects the highlight's layout(color) qualifier;
        // isolate the actual refraction shader and safe combined backdrop here.
        settings.value = GlassSettings(quality = GlassQuality.Quality, dispersion = true, rim = 0f)
        val checked = mutableStateOf(false)
        var samples = 0
        var renderer: GlassRenderer? = null
        compose.setContent { Theme {
            val source = rememberCanvasBackdrop { samples++; drawRect(Color.Red) }
            val selected = glassRenderer(settings.value)
            androidx.compose.runtime.SideEffect { renderer = selected }
            CompositionLocalProvider(LocalGlassContainerBackdrop provides source) {
                // Passive ownership isolates refraction from Robolectric's unsupported
                // interactive-highlight shader while retaining external transitions.
                Switch(checked.value, null, Modifier.testTag("switch"))
            }
        } }
        val node = compose.onNodeWithTag("switch")
        val resting = node.captureToImage()
        assertEquals(GlassRenderer.Refraction, renderer)
        assertTrue(samples > 0)
        compose.runOnIdle { samples = 0 }
        compose.runOnIdle { checked.value = true }
        compose.mainClock.advanceTimeBy(64)
        val pressed = node.captureToImage()
        assertTrue("The refractive path must resample its safe source", samples > 0)
        assertEquals(resting.width, pressed.width)
    }

    @Test fun `touch exploration keeps thumb opacity stable during a held press`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val shadow = org.robolectric.Shadows.shadowOf(manager)
        shadow.setTouchExplorationEnabled(true)
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent { Theme { Switch(false, {}, Modifier.testTag("switch")) } }
            val node = compose.onNodeWithTag("switch")
            fun thumb(): Color = node.captureToImage().toPixelMap().let { pixels ->
                pixels[(pixels.width * 22f / 64f).toInt(), pixels.height / 2]
            }
            val resting = thumb()
            node.performTouchInput { down(center) }
            compose.mainClock.advanceTimeByFrame()
            assertEquals(resting, thumb())
            compose.mainClock.advanceTimeBy(240)
            assertEquals("Exploration must not animate thumb opacity", resting, thumb())
            node.performTouchInput { cancel() }
        } finally {
            shadow.setTouchExplorationEnabled(false)
        }
    }

    @Test fun `passive transitions and direct release keep official enlargement until arrival`() {
        val checked = mutableStateOf(false)
        val passive = mutableStateOf(true)
        settings.value = settings.value.copy(rippleEnabled = false)
        compose.mainClock.autoAdvance = false
        compose.setContent { Theme {
            Box(Modifier.size(128.dp, 96.dp).background(Color.Black).testTag("motion-stage"), contentAlignment = Alignment.Center) {
                Switch(checked.value, if (passive.value) null else { value: Boolean -> checked.value = value }, Modifier.testTag("switch"))
            }
        } }
        compose.mainClock.advanceTimeBy(400)
        fun outsideTrack(frame: String? = null): Int {
            val image = compose.onNodeWithTag("motion-stage").captureToImage()
            if (frame != null) {
                val directory = File("build/test-artifacts/glass-switch").apply { mkdirs() }
                File(directory, "$frame.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            val pixels = image.toPixelMap()
            val halfHeight = pixels.height * 14f / 96f
            return (0 until pixels.height).sumOf { y ->
                if (abs(y - pixels.height / 2f) <= halfHeight + 1f) 0
                else (0 until pixels.width).count { x -> pixels[x, y].red > .05f }
            }
        }
        compose.runOnIdle { checked.value = true }
        val frames = (1..12).map { index ->
            compose.mainClock.advanceTimeBy(32)
            outsideTrack("passive-$index")
        }
        assertTrue("A row-owned change must enlarge the passive thumb: $frames", frames.any { it > 0 })
        compose.mainClock.advanceTimeBy(600)
        assertEquals(0, outsideTrack())
        compose.runOnIdle { checked.value = false; passive.value = false }
        compose.mainClock.advanceTimeBy(600)
        val node = compose.onNodeWithTag("switch")
        node.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(240)
        node.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(64)
        assertTrue("Release must preserve expansion while the position travels", outsideTrack() > 0)
        compose.mainClock.advanceTimeBy(600)
        assertEquals(0, outsideTrack())
    }

    @Test fun `vertical swipe beginning on a switch scrolls its parent without changing the switch`() {
        val scroll = ScrollState(0)
        var calls = 0
        compose.setContent { Theme {
            Column(Modifier.size(160.dp, 120.dp).verticalScroll(scroll)) {
                Switch(false, { calls++ }, Modifier.testTag("scroll-switch"))
                Spacer(Modifier.size(160.dp, 320.dp))
            }
        } }
        compose.onNodeWithTag("scroll-switch").performTouchInput {
            swipe(Offset(width / 2f, height * .9f), Offset(width / 2f, height * .1f))
        }
        compose.runOnIdle {
            assertTrue("The parent must consume the vertical movement", scroll.value > 0)
            assertEquals(0, calls)
        }
    }

    @Test fun `direct toggle clicks and drags emit once while cancellation and vertical swipes do not`() {
        val checked = mutableStateOf(false)
        var calls = 0
        compose.setContent { Theme {
            Switch(checked.value, { checked.value = it; calls++ }, Modifier.testTag("switch"))
        } }
        val node = compose.onNodeWithTag("switch")
        node.assertIsOff().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        node.performClick().assertIsOn()
        assertEquals(1, calls)
        node.performTouchInput { swipe(Offset(width * .8f, height / 2f), Offset(width * .1f, height / 2f)) }
        node.assertIsOff()
        assertEquals(2, calls)
        node.performTouchInput { swipe(Offset(width / 2f, height * .9f), Offset(width / 2f, height * .1f)) }
        assertEquals(2, calls)
        node.performTouchInput { down(center); moveBy(Offset(30f, 0f)); cancel() }
        node.assertIsOff()
        assertEquals(2, calls)
    }

    @Test fun `controlled owners may reject a drag and update checked during it in either renderer`() {
        val checked = mutableStateOf(false)
        var calls = 0
        compose.setContent { Theme { Switch(checked.value, { calls++ }, Modifier.testTag("switch"),
            checkedThumbColor = Color.White, uncheckedThumbColor = Color.White) } }
        val node = compose.onNodeWithTag("switch")
        fun pixels() = node.captureToImage().toPixelMap().let { map ->
            List(map.height) { y -> List(map.width) { x -> map[x, y] } }
        }
        listOf(true, false).forEach { glass ->
            compose.runOnIdle { settings.value = settings.value.copy(enabled = glass); checked.value = false }
            val resting = pixels()
            val before = calls
            node.performTouchInput { swipe(Offset(width * .1f, height / 2f), Offset(width * .9f, height / 2f)) }
            node.assertIsOff()
            assertEquals(before + 1, calls)
            assertEquals("Rejected request must return to its owner's endpoint", resting, pixels())
            node.performTouchInput { down(center); moveBy(Offset(40f, 0f)) }
            compose.runOnIdle { checked.value = true }
            node.performTouchInput { up() }
            node.assertIsOn()
            assertEquals("External matching endpoint must not generate a duplicate request", before + 1, calls)
        }
    }

    @Test fun `layout direction change cancels active drag and restores authoritative endpoint`() {
        val direction = mutableStateOf(LayoutDirection.Ltr)
        var calls = 0
        compose.setContent { Theme { CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
            Switch(false, { calls++ }, Modifier.testTag("switch"))
        } } }
        val node = compose.onNodeWithTag("switch")
        node.performTouchInput { down(center); moveBy(Offset(40f, 0f)) }
        compose.runOnIdle { direction.value = LayoutDirection.Rtl }
        node.performTouchInput { cancel() }
        node.assertIsOff()
        assertEquals(0, calls)
        node.performTouchInput { swipe(Offset(width * .9f, height / 2f), Offset(width * .1f, height / 2f)) }
        assertEquals("A new drag must start from authoritative OFF", 1, calls)
    }

    @Test fun `RTL reverses dragging and disabled switch emits no changes`() {
        val checked = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        var calls = 0
        compose.setContent { Theme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Switch(checked.value, { checked.value = it; calls++ }, Modifier.testTag("switch"), enabled = enabled.value)
            }
        } }
        val node = compose.onNodeWithTag("switch")
        node.performTouchInput { swipe(Offset(width * .9f, height / 2f), Offset(width * .1f, height / 2f)) }
        node.assertIsOn()
        assertEquals(1, calls)
        compose.runOnIdle { enabled.value = false }
        node.assertIsNotEnabled().performClick()
        node.performTouchInput { swipe(Offset(width * .1f, height / 2f), Offset(width * .9f, height / 2f)) }
        node.assertIsOn()
        assertEquals(1, calls)
    }

    @Test fun `passive switch leaves gestures and accessibility to its owner row`() {
        val checked = mutableStateOf(false)
        var calls = 0
        compose.setContent { Theme {
            Box(Modifier.size(160.dp, 64.dp).testTag("row").clickable { checked.value = !checked.value; calls++ }) {
                Switch(checked.value, null, Modifier.testTag("switch"))
            }
        } }
        assertTrue(compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
            .fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("row").performTouchInput { click(Offset(20f, 16f)) }
        assertTrue(checked.value)
        assertEquals(1, calls)
    }

    @Test fun `classic and contour switches support drag RTL and cancellation without duplicate actions`() {
        val checked = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val enabled = mutableStateOf(true)
        settings.value = settings.value.copy(enabled = false)
        var calls = 0
        compose.setContent { Theme {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                Switch(checked.value, { checked.value = it; calls++ }, Modifier.testTag("switch"), enabled = enabled.value)
            }
        } }
        val node = compose.onNodeWithTag("switch")
        node.performTouchInput { swipe(Offset(width * .1f, height / 2f), Offset(width * .9f, height / 2f)) }
        node.assertIsOn()
        assertEquals(1, calls)
        node.performTouchInput { swipe(Offset(width / 2f, height * .9f), Offset(width / 2f, height * .1f)) }
        node.performTouchInput { down(center); moveBy(Offset(-30f, 0f)); cancel() }
        assertEquals(1, calls)
        compose.runOnIdle {
            direction.value = LayoutDirection.Rtl
            settings.value = settings.value.copy(enabled = true, areaModes = mapOf(GlassArea.Switches to GlassAreaMode.ContourOnly))
        }
        node.performTouchInput { swipe(Offset(width * .1f, height / 2f), Offset(width * .9f, height / 2f)) }
        node.assertIsOff()
        assertEquals(2, calls)
        compose.runOnIdle { enabled.value = false }
        node.performClick()
        assertEquals(2, calls)
    }

    @Test fun `native pixels show official white capsule and press overflow beyond the track and card`() {
        settings.value = settings.value.copy(actionButtons = false, rippleEnabled = false)
        val checked = mutableStateOf(false)
        compose.setContent { Theme {
            Box(Modifier.size(128.dp, 96.dp).background(Color.Black).testTag("stage"), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    GlassControlOverflowSurface(Modifier.size(64.dp, 28.dp), CircleShape,
                        surface = { Box(Modifier.matchParentSize().background(Color.Blue)) },
                        content = { Switch(checked.value, { checked.value = it }, Modifier.testTag("switch"),
                            checkedTrackColor = Color.Red, uncheckedTrackColor = Color.Gray) })
                }
            }
        } }
        val node = compose.onNodeWithTag("switch")
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val stage = compose.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
        fun image(name: String? = null): androidx.compose.ui.graphics.PixelMap {
            val image = compose.onNodeWithTag("stage").captureToImage()
            if (name != null) {
                val directory = File("build/test-artifacts/glass-switch").apply { mkdirs() }
                File(directory, "$name.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            return image.toPixelMap()
        }
        val off = image("off")
        fun trackPixel(pixels: androidx.compose.ui.graphics.PixelMap, fraction: Float): Color {
            val x = ((bounds.left - stage.left + bounds.width * fraction) / stage.width * pixels.width).toInt()
            val y = ((bounds.center.y - stage.top) / stage.height * pixels.height).toInt()
            return pixels[x, y]
        }
        assertEquals(DefaultDarkColorPalette.red, trackPixel(off, .9f))
        fun whiteBounds(): Pair<Int, Int> {
            val pixels = image()
            val points = buildList {
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    val color = pixels[x, y]
                    if (color.red > .98f && color.green > .98f && color.blue > .98f) add(x to y)
                }
            }
            return (points.maxOf { it.first } - points.minOf { it.first }) to
                (points.maxOf { it.second } - points.minOf { it.second })
        }
        val white = whiteBounds()
        assertEquals("The official thumb is 40 by 24", 40f / 24f, white.first.toFloat() / white.second, .1f)
        node.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(240)
        val pressed = image("press")
        val top = ((bounds.top - stage.top) / stage.height * pressed.height).toInt()
        val bottom = ((bounds.bottom - stage.top) / stage.height * pressed.height).toInt()
        var outside = 0
        for (y in 0 until pressed.height) for (x in 0 until pressed.width) {
            if ((y < top || y >= bottom) && pressed[x, y] != off[x, y]) outside++
        }
        assertTrue("Pressed thumb must paint outside the 28dp track/card", outside > 0)
        assertEquals(bounds, node.fetchSemanticsNode().boundsInRoot)
        node.performTouchInput { cancel() }
        node.performClick()
        assertTrue(checked.value)
        assertEquals(white, whiteBounds())
        assertEquals(DefaultDarkColorPalette.accent, trackPixel(image("on"), .1f))
        assertEquals(64f / 28f, bounds.width / bounds.height, .01f)
    }

    @Test fun `four switch modes separate material and contour without changing the business state`() {
        var calls = 0
        compose.setContent { Theme { Switch(true, { calls++ }, Modifier.testTag("switch")) } }
        fun pixels() = compose.onNodeWithTag("switch").captureToImage().toPixelMap().let { map ->
            List(map.height) { y -> List(map.width) { x -> map[x, y] } }
        }
        val frames = GlassAreaMode.entries.associateWith { mode ->
            compose.runOnIdle { settings.value = settings.value.copy(areaModes = mapOf(GlassArea.Switches to mode)) }
            pixels()
        }
        assertNotEquals(frames[GlassAreaMode.Disabled], frames[GlassAreaMode.ContourOnly])
        assertNotEquals(frames[GlassAreaMode.GlassOnly], frames[GlassAreaMode.GlassAndContour])
        assertNotEquals(frames[GlassAreaMode.Disabled], frames[GlassAreaMode.GlassOnly])
        assertEquals(0, calls)
    }

    @Test fun `contour keeps the liquid geometry without sampling while material modes sample safely`() {
        settings.value = settings.value.copy(quality = GlassQuality.Balanced,
            areaModes = mapOf(GlassArea.Switches to GlassAreaMode.ContourOnly))
        var samples = 0
        compose.setContent { Theme {
            val source = rememberCanvasBackdrop { samples++; drawRect(Color.Red) }
            CompositionLocalProvider(LocalGlassContainerBackdrop provides source) {
                Switch(false, {}, Modifier.testTag("switch"))
            }
        } }
        val node = compose.onNodeWithTag("switch")
        val bounds = node.fetchSemanticsNode().boundsInRoot
        node.captureToImage()
        assertEquals(0, samples)
        listOf(GlassAreaMode.GlassOnly, GlassAreaMode.GlassAndContour).forEach { mode ->
            compose.runOnIdle { samples = 0; settings.value = settings.value.copy(areaModes = mapOf(GlassArea.Switches to mode)) }
            node.captureToImage()
            assertTrue(samples > 0)
            assertEquals(bounds, node.fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test fun `grabbing an interrupted transition starts at the displayed position in both renderers`() {
        val checked = mutableStateOf(false)
        settings.value = settings.value.copy(jellyEnabled = false, rippleEnabled = false)
        compose.mainClock.autoAdvance = false
        compose.setContent { Theme {
            Box(Modifier.size(128.dp, 96.dp).background(Color.Black).testTag("grab-stage"), contentAlignment = Alignment.Center) {
                Switch(checked.value, { checked.value = it }, Modifier.testTag("switch"),
                    checkedThumbColor = Color.White, uncheckedThumbColor = Color.White)
            }
        } }
        val node = compose.onNodeWithTag("switch")
        fun center(name: String): Float {
            val image = compose.onNodeWithTag("grab-stage").captureToImage()
            val directory = File("build/test-artifacts/glass-switch").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            val pixels = image.toPixelMap()
            val thumbPixels = buildList {
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    val color = pixels[x, y]
                    val minimum = minOf(color.red, color.green, color.blue)
                    val maximum = maxOf(color.red, color.green, color.blue)
                    if (minimum > .6f && maximum - minimum < .04f) add(x)
                }
            }
            assertTrue("The stage must contain the thumb, including its native pressed tint: $name", thumbPixels.isNotEmpty())
            return (thumbPixels.min() + thumbPixels.max()) / 2f
        }
        listOf(true, false).forEach { glass ->
            compose.runOnIdle { checked.value = false; settings.value = settings.value.copy(enabled = glass) }
            compose.mainClock.advanceTimeBy(400)
            compose.runOnIdle { checked.value = true }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(50)
            val before = center("grab-$glass-before")
            node.performTouchInput { down(center); moveBy(Offset(10f, 0f)) }
            compose.mainClock.advanceTimeByFrame()
            val after = center("grab-$glass-after")
            assertTrue("Grab must not jump to the checked endpoint, glass=$glass: $before -> $after", abs(after - before) < 8f)
            node.performTouchInput { cancel() }
            compose.mainClock.advanceTimeBy(400)
        }
    }

    @Test fun `ripple uses configured square and rounded UI bounds without duplicating thumb motion`() {
        settings.value = settings.value.copy(jellyEnabled = false, rippleSize = 2f)
        roundness.value = 0.dp
        compose.setContent { Theme {
            Box(Modifier.background(Color.Black)) { Switch(false, {}, Modifier.testTag("switch")) }
        } }
        val node = compose.onNodeWithTag("switch")
        node.performTouchInput { down(Offset(width * .9f, height / 2f)) }
        compose.mainClock.advanceTimeBy(300)
        fun corner(): Color = node.captureToImage().toPixelMap().let { it[it.width - 2, it.height / 2 + 12] }
        val square = corner()
        compose.runOnIdle { roundness.value = 14.dp }
        val rounded = corner()
        assertTrue("Square ripple must reach its corner; rounded ripple must clip it", square.red > rounded.red + .02f)
        node.performTouchInput { cancel() }
    }

    @Test fun `shared ripple coefficient changes the held feedback while jelly can be disabled independently`() {
        settings.value = settings.value.copy(jellyEnabled = false, rippleEnabled = false)
        compose.setContent { Theme { Switch(false, {}, Modifier.testTag("switch")) } }
        val node = compose.onNodeWithTag("switch")
        fun pixels() = node.captureToImage().toPixelMap().let { map ->
            List(map.height) { y -> List(map.width) { x -> map[x, y] } }
        }
        val resting = pixels()
        node.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(240)
        assertEquals(resting, pixels())
        node.performTouchInput { cancel() }
        compose.runOnIdle { settings.value = settings.value.copy(rippleEnabled = true) }
        node.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(240)
        assertNotEquals(resting, pixels())
        node.performTouchInput { cancel() }
    }

    @Test fun `shared master and dedicated preference change rendering without business callbacks`() {
        settings.value = settings.value.copy(source = GlassTintSource.Custom, customColor = 0xFF000000.toInt())
        var calls = 0
        compose.setContent { Theme {
            Switch(true, { calls++ }, Modifier.testTag("switch"), checkedThumbColor = Color.White,
                checkedTrackColor = Color.Red)
        } }
        fun pixels() = compose.onNodeWithTag("switch").captureToImage().toPixelMap().let { map ->
            List(map.height) { y -> List(map.width) { x -> map[x, y] } }
        }
        val glass = pixels()
        compose.runOnIdle { settings.value = settings.value.copy(areaModes = mapOf(GlassArea.Switches to GlassAreaMode.Disabled)) }
        val classic = pixels()
        assertNotEquals(glass, classic)
        compose.runOnIdle { settings.value = settings.value.copy(areaModes = emptyMap(), enabled = false) }
        assertEquals(classic, pixels())
        compose.runOnIdle { settings.value = settings.value.copy(enabled = true, actionButtons = false) }
        assertEquals(glass, pixels())
        compose.runOnIdle { settings.value = settings.value.copy(film = .1f) }
        assertNotEquals(glass, pixels())
        assertEquals(0, calls)
    }
}
