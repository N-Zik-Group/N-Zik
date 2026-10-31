package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
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
import app.n_zik.android.R
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
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
class GlassOverlayIconButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `overlay buttons sample their artwork and actual local background without sampling controls or player color`() {
        val scrim = mutableStateOf(Color.Transparent)
        val sceneColor = mutableStateOf(Color.Transparent)
        val playerColor = mutableStateOf(Color.Red)
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            val artwork = rememberLayerBackdrop()
            val unrelatedPlayer = rememberCanvasBackdrop { drawRect(playerColor.value) }
            val optics = GlassSettings(quality = GlassQuality.Balanced, film = 0f, rim = 0f,
                blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape),
                // The source owner and its buttons share the same explicit optical configuration.
                LocalGlassPreviewSettings provides optics,
                LocalGlassBackdrop provides unrelatedPlayer,
                LocalGlassContainerBackdrop provides unrelatedPlayer) {
                Box(Modifier.size(160.dp, 100.dp).background(playerColor.value)) {
                    Box(Modifier.matchParentSize().layerBackdrop(artwork).background(Color.Cyan))
                    CompositionLocalProvider(LocalGlassOverlayArtworkBackdrop provides artwork) {
                        GlassOverlayScene(scrim.value, Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
                            scene = { Box(Modifier.matchParentSize().background(sceneColor.value)) }) {
                            // Same scene position as the live button: a clean export never contains its magenta glyph.
                            Box(Modifier.size(40.dp).testTag("source.sample")
                                .glassMaterial(optics, shape, ownBackdrop = LocalGlassContainerBackdrop.current,
                                    drawShadow = false))
                            GlassOverlayIconButton({}, R.drawable.settings, Color.Magenta,
                                Modifier.size(32.dp).testTag("scene.action"), settings = optics)
                        }
                    }
                }
            }
        }
        for (background in listOf(Color.Transparent, Color.Black.copy(alpha = .6f), Color.Blue)) {
            compose.runOnIdle { scrim.value = background; sceneColor.value = Color.Transparent }
            compose.mainClock.advanceTimeByFrame()
            val expected = background.compositeOver(Color.Cyan)
            repeat(2) {
                val source = compose.onNodeWithTag("source.sample").captureToImage().toPixelMap()
                val button = compose.onNodeWithTag("scene.action").captureToImage().toPixelMap()
                for (actual in listOf(source[source.width / 2, source.height / 2], button[button.width / 2, 2])) {
                    assertEquals(expected.red, actual.red, .04f)
                    assertEquals(expected.green, actual.green, .04f)
                    assertEquals(expected.blue, actual.blue, .04f)
                }
                compose.runOnIdle { playerColor.value = Color.Green }
                compose.mainClock.advanceTimeByFrame()
            }
        }
        compose.runOnIdle { sceneColor.value = Color.Yellow }
        compose.mainClock.advanceTimeByFrame()
        val source = compose.onNodeWithTag("source.sample").captureToImage().toPixelMap()
        val actual = source[source.width / 2, source.height / 2]
        assertEquals("The local visualizer scene must update below utilities", 1f, actual.red, .04f)
        assertEquals(1f, actual.green, .04f)
        assertEquals(0f, actual.blue, .04f)
    }

    @Test fun `overlay actions keep their slots and a held drag cannot activate or close the overlay`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        var clicks = 0
        var closes = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape)) {
                Box(Modifier.size(180.dp, 100.dp).background(Color.Black)
                    .testTag("overlay").clickable { closes++ }.padding(20.dp)) {
                    GlassOverlayIconButton({ clicks++ }, R.drawable.arrow_left, Color.White,
                        Modifier.size(32.dp).testTag("arrow"),
                        settings = GlassSettings(quality = GlassQuality.Performance,
                            areaModes = mapOf(GlassArea.ActionButtons to mode.value)))
                }
            }
        }
        // The native clickable child merges the seat into the clickable overlay's tree.
        val action = compose.onNodeWithTag("arrow", useUnmergedTree = true)
        var expectedClicks = 0
        for (areaMode in listOf(GlassAreaMode.Disabled, GlassAreaMode.ContourOnly, GlassAreaMode.GlassAndContour)) {
            compose.runOnIdle { mode.value = areaMode }
            // Replacing the native action with LiquidButton must finish before its first down.
            repeat(2) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
            action.assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(32.dp)
            action.performTouchInput { click() }
            compose.mainClock.advanceTimeBy(1600)
            expectedClicks++
            compose.runOnIdle {
                assertEquals("The $areaMode action receives exactly one native tap", expectedClicks, clicks)
                assertEquals(0, closes)
            }
        }
        compose.runOnIdle { assertEquals(3, clicks); assertEquals(0, closes) }
        val resting = compose.onNodeWithTag("overlay").captureToImage().toPixelMap()
        val bounds = action.fetchSemanticsNode().boundsInRoot
        action.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        action.performTouchInput { moveTo(center + Offset(60f, 0f), delayMillis = 200) }
        compose.mainClock.advanceTimeBy(500)
        val held = compose.onNodeWithTag("overlay").captureToImage().toPixelMap()
        assertEquals("Glass motion must not move the callback's hit slot", bounds, action.fetchSemanticsNode().boundsInRoot)
        fun glyphCenter(pixels: androidx.compose.ui.graphics.PixelMap): Float {
            val positions = (0 until pixels.height).flatMap { y -> (0 until pixels.width).filter { x ->
                val color = pixels[x, y]
                color.red > .8f && color.green > .8f && color.blue > .8f
            } }
            assertTrue("The arrow glyph must remain visible", positions.isNotEmpty())
            return positions.average().toFloat()
        }
        assertTrue("The actual arrow glyph must travel with its material", glyphCenter(held) > glyphCenter(resting) + 1f)
        action.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1600)
        compose.runOnIdle { assertEquals("A dragged arrow is not a tap", 3, clicks); assertEquals(0, closes) }
        action.performTouchInput { click() }
        compose.runOnIdle { assertEquals("The original arrow action remains the sole tap callback", 4, clicks); assertEquals(0, closes) }
    }
}
