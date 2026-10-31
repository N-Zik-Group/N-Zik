package app.n_zik.android.components.navigation.header

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.GlassActionGroup
import app.n_zik.android.components.glass.GlassActionGroupItem
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassRippleProvider
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.headerGlassMotionVisualOutset
import org.junit.Assert.assertEquals
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
class HeaderGlassOverflowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `held capsule crosses header bottom without growing its background or status mask`() {
        val scroll = mutableFloatStateOf(0f)
        val glass = mutableStateOf(true)
        var clicks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = RoundedCornerShape(24.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                val settings = GlassSettings(enabled = glass.value, quality = GlassQuality.Performance, film = 1f)
                GlassRippleProvider(settings) {
                    Box(Modifier.size(240.dp).background(Color.White).testTag("scene")) {
                        Box(Modifier.headerScrollFade(24f, 64f, Color.Black, clipStatusAtRest = true,
                            motionVisualOutset = if (glass.value) headerGlassMotionVisualOutset() else 0.dp,
                        ) { scroll.floatValue }.size(240.dp, 88.dp)) {
                            GlassActionGroup(settings, GlassArea.Header, Modifier.offset(80.dp, 48.dp)) {
                                GlassActionGroupItem({ clicks++ }, Modifier.testTag("action"), nativeIndication = !glass.value) {
                                    Box(Modifier.size(20.dp).background(Color.Blue))
                                }
                                GlassActionGroupItem({}, nativeIndication = !glass.value) { }
                            }
                        }
                    }
                }
            }
        }
        val scene = compose.onNodeWithTag("scene")
        val action = compose.onNodeWithTag("action")
        val bounds = action.fetchSemanticsNode().boundsInRoot
        fun frame() = scene.captureToImage()
        fun assertMasked(image: ImageBitmap) {
            val pixels = image.toPixelMap()
            assertEquals("The Android status mask remains closed", Color.White, pixels[100, 12])
            assertEquals("The header background cannot grow into the page", Color.White, pixels[8, 100])
        }
        assertEquals(0, blueBelow(frame(), 88))
        for (offset in listOf(0f, -16f)) {
            compose.runOnIdle { scroll.floatValue = offset }
            action.performTouchInput { down(center); moveTo(center + Offset(0f, 500f), 100) }
            compose.mainClock.advanceTimeBy(700)
            val held = frame()
            assertTrue("Rendered held glyph must cross the moving header boundary at offset $offset",
                blueBelow(held, (88 + offset).toInt()) > 20)
            assertMasked(held)
            assertEquals("Only the drawing moves", bounds.translate(Offset(0f, offset)), action.fetchSemanticsNode().boundsInRoot)
            action.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1800)
            val released = frame()
            assertEquals(0, blueBelow(released, (88 + offset).toInt()))
            assertMasked(released)
        }
        compose.runOnIdle { scroll.floatValue = 0f }
        action.performTouchInput { down(center); moveTo(center + Offset(0f, 500f), 100) }
        compose.mainClock.advanceTimeBy(700)
        assertTrue(blueBelow(frame(), 88) > 20)
        compose.runOnIdle { glass.value = false }
        compose.mainClock.advanceTimeBy(1800)
        assertEquals("Turning Glass off cannot leave overflow pixels", 0, blueBelow(frame(), 88))
        action.performTouchInput { cancel() }
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    private fun blueBelow(image: ImageBitmap, boundary: Int): Int {
        val pixels = image.toPixelMap()
        var count = 0
        for (y in boundary until pixels.height) for (x in 0 until pixels.width) {
            val color = pixels[x, y]
            if (color.blue - color.red > .3f && color.blue - color.green > .3f) count++
        }
        return count
    }
}
