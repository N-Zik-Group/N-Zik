package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import kotlinx.coroutines.flow.collect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `master and action area off preserve every native button pixel and dimensions`() {
        val settings = mutableStateOf(GlassSettings(enabled = false))
        val enabled = mutableStateOf(true)
        compose.setContent {
            PageButtonAppearance {
                Column(Modifier.background(DefaultDarkColorPalette.background0)) {
                    val colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red, contentColor = Color.White,
                        disabledContainerColor = Color.Blue.copy(alpha = .4f),
                    )
                    val shape = RoundedCornerShape(12.dp)
                    Button({}, Modifier.size(160.dp, 48.dp).testTag("native"), enabled.value,
                        shape, colors, elevation = null) { Box(Modifier.size(16.dp).background(Color.Magenta)) }
                    PageButton({}, Modifier.size(160.dp, 48.dp).testTag("page"), enabled.value,
                        shape, colors, elevation = null, settings = settings.value) {
                        Box(Modifier.size(16.dp).background(Color.Magenta))
                    }
                    TextButton({}, Modifier.size(160.dp, 48.dp).testTag("nativeText"), enabled.value,
                        shape, colors) { Box(Modifier.size(16.dp).background(Color.Magenta)) }
                    PageTextButton({}, Modifier.size(160.dp, 48.dp).testTag("pageText"), enabled.value,
                        shape, colors, settings = settings.value) { Box(Modifier.size(16.dp).background(Color.Magenta)) }
                }
            }
        }
        listOf(
            GlassSettings(enabled = false),
            GlassSettings(actionButtons = false),
            GlassSettings(areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled)),
        ).forEach { off ->
            listOf(true, false).forEach { active ->
                compose.runOnIdle { settings.value = off; enabled.value = active }
                assertIdentical("native", "page")
                assertIdentical("nativeText", "pageText")
            }
        }
    }

    @Test fun `page role film uses safe canvas and keeps foreground sharp including disabled fill`() {
        val role = mutableStateOf(DefaultDarkColorPalette.accent)
        val enabled = mutableStateOf(true)
        val settings = GlassSettings(film = .5f, rim = 0f, blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f)
        val disabled = Color.Blue.copy(alpha = .4f)
        compose.setContent {
            PageButtonAppearance {
                val forbidden = rememberCanvasBackdrop { error("The page capture must never sample its own button") }
                CompositionLocalProvider(LocalGlassBackdrop provides forbidden) {
                    Box(Modifier.background(DefaultDarkColorPalette.background0), contentAlignment = Alignment.Center) {
                        PageButton({}, Modifier.size(160.dp, 48.dp).testTag("action"), enabled.value,
                            shape = RoundedCornerShape(12.dp), elevation = null,
                            colors = ButtonDefaults.buttonColors(containerColor = role.value, disabledContainerColor = disabled),
                            settings = settings) {
                            Box(Modifier.size(16.dp).background(Color.Magenta))
                        }
                    }
                }
            }
        }
        fun check(fill: Color) {
            val pixels = compose.onNodeWithTag("action").captureToImage().toPixelMap()
            val expected = fill.copy(alpha = fill.alpha * glassFilmOpacity(.5f, overlay = false))
                .compositeOver(DefaultDarkColorPalette.background0)
            assertColor(expected, pixels[pixels.width / 4, pixels.height / 2])
            assertColor(Color.Magenta, pixels[pixels.width / 2, pixels.height / 2])
        }
        listOf(DefaultDarkColorPalette.accent, DefaultDarkColorPalette.red, DefaultDarkColorPalette.background2)
            .forEach { fill ->
                compose.runOnIdle { role.value = fill }
                check(fill)
            }
        compose.runOnIdle { enabled.value = false }
        check(disabled)
    }

    @Test fun `glass page button retains disabled click guard and enabled callback`() {
        val enabled = mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            PageButtonAppearance {
                PageButton({ clicks++ }, Modifier.testTag("action"), enabled = enabled.value,
                    settings = GlassSettings(quality = GlassQuality.Performance)) {
                    Box(Modifier.size(16.dp))
                }
            }
        }
        compose.onNodeWithTag("action").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, clicks); enabled.value = true }
        compose.onNodeWithTag("action").assertIsEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun `native page button keeps parent ripple configuration and pressed native pixels`() {
        val interactions = MutableInteractionSource()
        val nativeRipple = RippleConfiguration(Color.Black)
        var observed: RippleConfiguration? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PageButtonAppearance {
                CompositionLocalProvider(LocalRippleConfiguration provides nativeRipple) {
                    Column(Modifier.background(DefaultDarkColorPalette.background0)) {
                        val colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6020A0))
                        Button({}, Modifier.size(160.dp, 48.dp).testTag("native"),
                            colors = colors, elevation = null, interactionSource = interactions) {
                            Box(Modifier.size(16.dp).background(Color.Magenta))
                        }
                        PageButton({}, Modifier.size(160.dp, 48.dp).testTag("page"),
                            colors = colors, elevation = null, interactionSource = interactions,
                            settings = GlassSettings(enabled = false)) {
                            val ripple = LocalRippleConfiguration.current
                            SideEffect { observed = ripple }
                            Box(Modifier.size(16.dp).background(Color.Magenta))
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertSame(nativeRipple, observed) }
        assertIdentical("native", "page")
        val press = PressInteraction.Press(Offset(80f, 24f))
        compose.runOnIdle { assertTrue(interactions.tryEmit(press)) }
        compose.mainClock.advanceTimeBy(800)
        // Native ripple layers are rasterized independently; compare flat interior samples.
        val nativePressed = compose.onNodeWithTag("native").captureToImage().toPixelMap()
        val pagePressed = compose.onNodeWithTag("page").captureToImage().toPixelMap()
        for (x in listOf(nativePressed.width / 4, nativePressed.width * 3 / 4)) {
            assertColor(nativePressed[x, nativePressed.height / 2], pagePressed[x, pagePressed.height / 2])
        }
        compose.runOnIdle { interactions.tryEmit(PressInteraction.Release(press)) }
        compose.mainClock.advanceTimeBy(1600)
        compose.runOnIdle { assertSame(nativeRipple, observed) }
    }

    @Test fun `global hold reaches native page button with action material off and restores native ripple`() {
        val source = MutableInteractionSource()
        val global = mutableStateOf(true)
        val nativeRipple = RippleConfiguration(Color.Black)
        var observed: RippleConfiguration? = nativeRipple
        val areaOff = GlassSettings(enabled = true, actionButtons = false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PageButtonAppearance {
                CompositionLocalProvider(LocalRippleConfiguration provides nativeRipple) {
                    GlassRippleProvider(GlassSettings(enabled = global.value)) {
                        Column {
                            val colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6020A0))
                            Button({}, Modifier.size(160.dp, 48.dp).testTag("global.native"),
                                colors = colors, elevation = null, interactionSource = source) {}
                            PageButton({}, Modifier.size(160.dp, 48.dp).testTag("global.page"),
                                colors = colors, elevation = null, interactionSource = source,
                                settings = areaOff) {
                                val ripple = LocalRippleConfiguration.current
                                SideEffect { observed = ripple }
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertNull("Explicit Glass halo disables Material's internal ripple", observed) }
        val node = compose.onNodeWithTag("global.page")
        val before = node.captureToImage().toPixelMap()
        val press = PressInteraction.Press(Offset(80f, 24f))
        compose.runOnIdle { assertTrue(source.tryEmit(press)) }
        compose.mainClock.advanceTimeBy(1200)
        val held = node.captureToImage().toPixelMap()
        val x = held.width / 2
        val y = held.height / 2
        assertTrue("Native material receives Glass white halo", held[x, y].red > before[x, y].red + .02f)
        assertTrue(held[x, y].green > before[x, y].green && held[x, y].blue > before[x, y].blue)
        compose.runOnIdle { source.tryEmit(PressInteraction.Release(press)); global.value = false }
        compose.mainClock.advanceTimeBy(1600)
        compose.runOnIdle { assertSame(nativeRipple, observed) }
        // Restored Material ripple layers may quantize the same color one 8-bit
        // step apart. Compare the complete capsule, including its antialiased edge.
        assertIdentical("global.native", "global.page", channelTolerance = 1f / 255f)
        val nativePress = PressInteraction.Press(Offset(80f, 24f))
        compose.runOnIdle { assertTrue(source.tryEmit(nativePress)) }
        compose.mainClock.advanceTimeBy(800)
        val native = compose.onNodeWithTag("global.native").captureToImage().toPixelMap()
        val restored = node.captureToImage().toPixelMap()
        for (sample in listOf(native.width / 4, native.width * 3 / 4)) {
            assertColor(native[sample, native.height / 2], restored[sample, restored.height / 2])
        }
        compose.runOnIdle { source.tryEmit(PressInteraction.Release(nativePress)) }
    }

    @Test fun `glass button press uses bounded white hold instead of inherited dark material ripple`() {
        assertGlassHold(textButton = false)
    }

    @Test fun `glass text button press uses bounded white hold instead of inherited dark material ripple`() {
        assertGlassHold(textButton = true)
    }

    @Test fun `glass page button pane and glyph follow the finger until physical up`() {
        assertFingerTracking(textButton = false)
    }

    @Test fun `glass page text button pane and glyph follow the finger until physical up`() {
        assertFingerTracking(textButton = true)
    }

    private fun assertFingerTracking(textButton: Boolean) {
        val source = MutableInteractionSource()
        var nativeCancels = 0
        var movementConsumed = false
        var clicks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LaunchedEffect(source) {
                source.interactions.collect { if (it is PressInteraction.Cancel) nativeCancels++ }
            }
            PageButtonAppearance {
                val modifier = Modifier.size(160.dp, 48.dp).testTag("tracking")
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                event.changes.firstOrNull { it.pressed && it.position != it.previousPosition }
                                    ?.let { movementConsumed = it.isConsumed }
                            }
                        }
                    }
                val settings = GlassSettings(quality = GlassQuality.Performance, rim = 0f, film = .5f)
                val colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF402060))
                Box(Modifier.background(DefaultDarkColorPalette.background0)) {
                    if (textButton) PageTextButton({ clicks++ }, modifier, colors = colors,
                        interactionSource = source, settings = settings) { Box(Modifier.size(12.dp).background(Color.Magenta)) }
                    else PageButton({ clicks++ }, modifier, colors = colors, elevation = null,
                        interactionSource = source, settings = settings) { Box(Modifier.size(12.dp).background(Color.Magenta)) }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val action = compose.onNodeWithTag("tracking")
        val original = action.captureToImage().toPixelMap()
        val left = Offset(original.width * .2f, original.height * .5f)
        val right = Offset(original.width * .8f, original.height * .5f)
        action.performTouchInput { down(left) }
        ShadowLooper.idleMainLooper(150, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeBy(800)
        val initial = action.captureToImage().toPixelMap()
        action.performTouchInput { moveTo(left + Offset(2f, 0f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertFalse("Highlight observer must not consume a small move", movementConsumed) }
        action.performTouchInput { moveTo(right) }
        compose.mainClock.advanceTimeByFrame()
        val moved = action.captureToImage().toPixelMap()
        fun glyphCenter(pixels: androidx.compose.ui.graphics.PixelMap): Float {
            val positions = (0 until pixels.height).flatMap { y ->
                (0 until pixels.width).filter { x ->
                    val pixel = pixels[x, y]
                    pixel.red > .8f && pixel.blue > .8f && pixel.green < .4f
                }
            }
            assertTrue("The actual foreground marker must remain visible", positions.isNotEmpty())
            return positions.average().toFloat()
        }
        assertTrue("The foreground must travel with the entire Glass pane",
            glyphCenter(moved) > glyphCenter(initial) + 1f)
        val xLeft = left.x.toInt()
        val xRight = right.x.toInt()
        val y = left.y.toInt()
        assertTrue("The initial light must be at the finger", initial[xLeft, y].red > initial[xRight, y].red + .03f)
        assertTrue("The same held light must follow across the actual button", moved[xRight, y].red > moved[xLeft, y].red + .03f)
        compose.runOnIdle {
            assertTrue("A fixed Glass button consumes its drag before an ancestor can scroll", movementConsumed)
            assertEquals("The retained drag cancels the sole native action", 1, nativeCancels)
        }
        compose.mainClock.advanceTimeBy(800)
        val nativeCanceled = action.captureToImage().toPixelMap()
        assertTrue("Native action cancellation must not release a physically held Glass pane",
            nativeCanceled[xRight, y].red > original[xRight, y].red + .03f)
        action.performTouchInput { moveTo(left) }
        compose.mainClock.advanceTimeByFrame()
        action.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1600)
        val released = action.captureToImage().toPixelMap()
        assertColor(original[xLeft, y], released[xLeft, y])
        compose.runOnIdle { assertEquals("Returning inside cannot revive a cancelled drag", 0, clicks) }
        action.performTouchInput { click() }
        compose.runOnIdle { assertEquals("A fresh tap still has exactly one action owner", 1, clicks) }
    }

    @Test fun `fixed glass button consumes a vertical drag before its parent can scroll`() {
        var clicks = 0
        var cancels = 0
        var scroll: androidx.compose.foundation.ScrollState? = null
        val source = MutableInteractionSource()
        compose.setContent {
            LaunchedEffect(source) {
                source.interactions.collect { if (it is PressInteraction.Cancel) cancels++ }
            }
            PageButtonAppearance {
                val state = rememberScrollState()
                SideEffect { scroll = state }
                Column(Modifier.size(180.dp, 120.dp).verticalScroll(state)) {
                    PageButton({ clicks++ }, Modifier.size(160.dp, 80.dp).testTag("scrollAction"),
                        interactionSource = source, settings = GlassSettings(quality = GlassQuality.Performance)) { }
                    Box(Modifier.size(160.dp, 500.dp))
                }
            }
        }
        val action = compose.onNodeWithTag("scrollAction")
        action.performTouchInput { down(center) }
        ShadowLooper.idleMainLooper(150, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeBy(200)
        action.performTouchInput { moveBy(Offset(0f, -100f)); up() }
        compose.runOnIdle {
            assertEquals(0, clicks)
            assertEquals(1, cancels)
            assertEquals("A drag held by a fixed Glass button cannot scroll its parent", 0, requireNotNull(scroll).value)
        }
    }

    private fun assertGlassHold(textButton: Boolean) {
        val interactions = MutableInteractionSource()
        val pressEvents = mutableListOf<String>()
        var observed: RippleConfiguration? = RippleConfiguration(Color.Black)
        var clicks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LaunchedEffect(interactions) {
                interactions.interactions.collect { pressEvents += it.javaClass.simpleName }
            }
            PageButtonAppearance {
                CompositionLocalProvider(LocalRippleConfiguration provides RippleConfiguration(Color.Black)) {
                    val colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6020A0))
                    val settings = GlassSettings(quality = GlassQuality.Performance, rim = 0f, film = .5f)
                    val foreground: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                        val ripple = LocalRippleConfiguration.current
                        SideEffect { observed = ripple }
                        Box(Modifier.size(16.dp).background(Color.Magenta))
                    }
                    Box(Modifier.background(DefaultDarkColorPalette.background0)) {
                        if (textButton) {
                            PageTextButton({ clicks++ }, Modifier.size(160.dp, 48.dp).testTag("action"),
                                colors = colors, interactionSource = interactions,
                                settings = settings, content = foreground)
                        } else {
                            PageButton({ clicks++ }, Modifier.size(160.dp, 48.dp).testTag("action"),
                                colors = colors, elevation = null, interactionSource = interactions,
                                settings = settings, content = foreground)
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertNull(observed) }
        val action = compose.onNodeWithTag("action")
        val original = action.captureToImage().toPixelMap()
        val bounds = action.fetchSemanticsNode().boundsInRoot
        action.performTouchInput { down(center) }
        // Material's delayed press uses the Android looper, separate from Compose frames.
        ShadowLooper.idleMainLooper(150, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(800)
        val held = action.captureToImage().toPixelMap()
        val before = original[original.width / 4, original.height / 2]
        val after = held[held.width / 4, held.height / 2]
        val diagnostic = "textButton=$textButton before=$before held=$after bounds=$bounds interactions=$pressEvents"
        assertTrue("Glass hold must lighten its film: $diagnostic", after.red > before.red + .02f)
        assertTrue("Glass hold must not darken its film: $diagnostic", after.green >= before.green && after.blue >= before.blue)
        assertTrue("Glass hold remains translucent: $diagnostic", after.red - before.red < .3f)
        val foreground = held[held.width / 2, held.height / 2]
        assertTrue("The sharp foreground keeps its saturated channels under the shared Plus highlight",
            foreground.red >= .98f && foreground.blue >= .98f && foreground.green <= .25f)
        assertEquals(bounds, action.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals(0, clicks) }
        action.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1600)
        compose.runOnIdle { assertEquals(1, clicks) }
        val restored = action.captureToImage().toPixelMap()
        assertColor(before, restored[restored.width / 4, restored.height / 2])
    }

    private fun assertIdentical(nativeTag: String, pageTag: String, channelTolerance: Float = 0f) {
        val native = compose.onNodeWithTag(nativeTag).captureToImage().toPixelMap()
        val page = compose.onNodeWithTag(pageTag).captureToImage().toPixelMap()
        assertEquals(native.width, page.width)
        assertEquals(native.height, page.height)
        for (y in 0 until native.height) for (x in 0 until native.width) {
            val expected = native[x, y]
            val actual = page[x, y]
            val message = "pixel $x,$y"
            assertEquals(message, expected.red, actual.red, channelTolerance)
            assertEquals(message, expected.green, actual.green, channelTolerance)
            assertEquals(message, expected.blue, actual.blue, channelTolerance)
            assertEquals(message, expected.alpha, actual.alpha, channelTolerance)
        }
    }

    private fun assertColor(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, .015f)
        assertEquals(expected.green, actual.green, .015f)
        assertEquals(expected.blue, actual.blue, .015f)
        assertEquals(expected.alpha, actual.alpha, .015f)
    }
}

@Composable
private fun PageButtonAppearance(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    CompositionLocalProvider(LocalAppearance provides Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
    ), content = content)
}
