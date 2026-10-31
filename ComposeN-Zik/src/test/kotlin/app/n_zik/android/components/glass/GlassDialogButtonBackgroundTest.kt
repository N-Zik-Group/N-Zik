package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.surfaceColor
import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassDialogButtonBackgroundTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(sdk = [32])
    fun `fixed dialog action retains a dragged gesture away from scrolling and closing ancestors`() {
        var scroll: ScrollState? = null
        var clicks = 0
        var closes = 0
        compose.setContent {
            val state = rememberScrollState()
            SideEffect { scroll = state }
            CompositionLocalProvider(LocalGlassPreviewSettings provides GlassSettings(quality = GlassQuality.Performance)) {
                Box(Modifier.size(220.dp, 180.dp).clickable { closes++ }) {
                    Column(Modifier.verticalScroll(state)) {
                        GlassDialogOutlinedButton({ clicks++ }, Modifier.size(160.dp, 48.dp).testTag("fixed.action")) {
                            Box(Modifier.size(12.dp).background(Color.Magenta))
                        }
                        Box(Modifier.size(220.dp, 600.dp))
                    }
                }
            }
        }
        compose.onNodeWithTag("fixed.action").performTouchInput {
            down(center)
            moveTo(center - Offset(0f, 120f), delayMillis = 200)
            up()
        }
        compose.runOnIdle {
            assertEquals("A fixed Glass drag cannot scroll its ancestor", 0, requireNotNull(scroll).value)
            assertEquals("Dragging cancels the native button action", 0, clicks)
            assertEquals("Dragging cannot activate the overlay close action", 0, closes)
        }
    }

    @Test @Config(sdk = [32])
    fun `dialog gallery and both icon presentations retain one action and disabled controls stay still`() {
        val enabled = mutableStateOf(true)
        val clicks = IntArray(3)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalGlassPreviewSettings provides GlassSettings(quality = GlassQuality.Performance),
                LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape)) {
                Column(Modifier.background(Color.Black)) {
                    GlassDialogOutlinedButton({ clicks[0]++ },
                        Modifier.size(160.dp, 48.dp).testTag("gallery")
                            .glassDialogButtonBackground(Color.Blue, shape), enabled = enabled.value) {
                        Box(Modifier.size(12.dp).background(Color.Magenta))
                    }
                    GlassDialogIconButton({ clicks[1]++ },
                        Modifier.size(48.dp).testTag("material.icon")
                            .glassDialogButtonBackground(Color.Blue, shape), enabled = enabled.value) {
                        Box(Modifier.size(12.dp).background(Color.Magenta))
                    }
                    GlassDialogIconButton({ clicks[2]++ }, R.drawable.play, Color.White,
                        Modifier.size(32.dp).testTag("image.icon")
                            .glassDialogButtonBackground(Color.Blue, shape), enabled = enabled.value)
                }
            }
        }
        val tags = listOf("gallery", "material.icon", "image.icon")
        tags.forEach { tag -> compose.onNodeWithTag(tag).performTouchInput { click() } }
        compose.mainClock.advanceTimeBy(1600)
        compose.runOnIdle { assertTrue(clicks.all { it == 1 }); enabled.value = false }
        // Start the disabled transition after recomposition and placement have committed.
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(1600)
        compose.waitForIdle()
        tags.forEach { tag ->
            val node = compose.onNodeWithTag(tag)
            val before = node.captureToImage().toPixelMap()
            node.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(800)
            val held = node.captureToImage().toPixelMap()
            for (y in 0 until held.height) for (x in 0 until held.width) {
                assertEquals("Disabled $tag must neither move nor highlight", before[x, y], held[x, y])
            }
            node.performTouchInput { up() }
        }
        compose.runOnIdle { assertTrue("Every wrapper retains exactly one native action owner", clicks.all { it == 1 }) }
    }

    @Test fun `native outline returns only when dialog button material is disabled`() {
        val original = GlassSettings(enabled = true, dialogs = true, dialogButtons = true)
        val config = mutableStateOf(original)
        val border = BorderStroke(1.dp, Color.Red)
        var actual: BorderStroke? = border
        compose.setContent {
            actual = glassDialogButtonBorder(border, settings = config.value)
        }
        compose.runOnIdle { assertNull(actual) }
        listOf(
            original.copy(enabled = false),
            original.copy(dialogs = false),
            original.copy(dialogButtons = false),
        ).forEach { disabled ->
            compose.runOnIdle { config.value = disabled }
            compose.runOnIdle { assertSame(border, actual) }
        }
        listOf(original.copy(rim = 0f), original.copy(quality = GlassQuality.Performance))
            .forEach { active ->
                compose.runOnIdle { config.value = active }
                compose.runOnIdle { assertNull(actual) }
            }
    }

    @Test fun `role films preserve sharp content and area toggles restore exact original fills`() {
        val config = mutableStateOf(GlassSettings(enabled = true, film = .5f, rim = 0f,
            blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f))
        val role = mutableStateOf(DefaultDarkColorPalette.accent)
        val hosted = mutableStateOf(false)
        val host = GlassMenuHostState()
        var originalSurface = Color.Unspecified
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            val view = LocalView.current
            val forbidden = rememberCanvasBackdrop { error("Never sample the containing window") }
            val safeCapture = rememberCanvasBackdrop { drawRect(Color.Cyan) }
            val isHosted = hosted.value
            SideEffect {
                host.dialogs.windowRoot = if (isHosted) view.rootView else null
                host.overlayBackdrop = safeCapture
            }
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = DefaultDarkColorPalette,
                    typography = typographyOf(Color.White, true, false, FontType.Rubik),
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
                LocalGlassMenuHost provides host,
                LocalGlassBackdrop provides forbidden,
            ) {
                originalSurface = surfaceColor(DefaultDarkColorPalette.background1)
                Box(Modifier.size(160.dp, 64.dp).background(originalSurface), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(160.dp, 64.dp).testTag("action")
                        .glassDialogButtonBackground(role.value, shape, settings = config.value),
                        contentAlignment = Alignment.Center) {
                        Box(Modifier.size(16.dp).background(Color.Magenta))
                    }
                }
            }
        }
        fun check(expected: Color) {
            val pixels = compose.onNodeWithTag("action").captureToImage().toPixelMap()
            assertColor(expected, pixels[pixels.width / 4, pixels.height / 2])
            assertColor(Color.Magenta, pixels[pixels.width / 2, pixels.height / 2])
        }
        val original = config.value
        listOf(DefaultDarkColorPalette.accent, DefaultDarkColorPalette.red, DefaultDarkColorPalette.background2)
            .forEach { fill ->
                compose.runOnIdle { role.value = fill; config.value = original }
                check(fill.copy(alpha = glassFilmOpacity(.5f, overlay = true)).compositeOver(originalSurface))
                listOf(original.copy(enabled = false), original.copy(dialogs = false),
                    original.copy(dialogButtons = false)).forEach { disabled ->
                    compose.runOnIdle { config.value = disabled }
                    check(fill)
                }
            }
        compose.runOnIdle { hosted.value = true; config.value = original }
        check(role.value.copy(alpha = glassFilmOpacity(.5f, overlay = true)).compositeOver(Color.Cyan))
        compose.runOnIdle { config.value = original.copy(quality = GlassQuality.Performance) }
        check(role.value.copy(alpha = glassFilmOpacity(.5f, overlay = true, fallback = true))
            .compositeOver(originalSurface))
    }

    private fun assertColor(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, .015f)
        assertEquals(expected.green, actual.green, .015f)
        assertEquals(expected.blue, actual.blue, .015f)
        assertEquals(expected.alpha, actual.alpha, .015f)
    }
}
