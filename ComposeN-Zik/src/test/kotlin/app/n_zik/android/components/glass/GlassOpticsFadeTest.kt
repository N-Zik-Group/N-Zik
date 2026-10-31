package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
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
class GlassOpticsFadeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `coverage fades live source both ways keeps film and bounds and stops sampling at zero`() {
        compose.mainClock.autoAdvance = false
        val covered = mutableStateOf(false)
        val source = mutableStateOf(Color.Red)
        var samples = 0
        var weight = 1f
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                val layer = rememberLayerBackdrop()
                val sampled = rememberBackdrop(layer) { draw -> samples++; draw() }
                Box(Modifier.size(160.dp, 80.dp).background(Color.Black).testTag("scene")) {
                    Box(Modifier.size(160.dp, 80.dp).layerBackdrop(layer).background(source.value))
                    Box(Modifier.offset(x = 80.dp).size(80.dp).background(Color.Black))
                    CompositionLocalProvider(LocalGlassUnderlayCovered provides covered.value) {
                        val progress = glassOpticsProgress()
                        SideEffect { weight = progress }
                        Box(Modifier.offset(x = 80.dp).size(80.dp).testTag("pane").glassMaterial(
                            GlassSettings(enabled = true, blur = 0f, depth = 0f, amount = 0f,
                                rim = 0f, vibrancy = 0f, film = .25f), shape,
                            ownBackdrop = sampled, filmColor = Color.White, drawShadow = false,
                        ))
                    }
                }
            }
        }
        fun center(): Color {
            val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
            return pixels[pixels.width * 3 / 4, pixels.height / 2]
        }
        fun step(millis: Long) {
            var remaining = millis
            while (remaining > 0) {
                val frame = minOf(16L, remaining)
                compose.mainClock.advanceTimeBy(frame)
                compose.waitForIdle()
                compose.onNodeWithTag("scene").captureToImage()
                remaining -= frame
            }
        }
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("pane").fetchSemanticsNode().boundsInRoot
        assertTrue(center().red > .95f)
        compose.runOnIdle { covered.value = true }
        step(16)
        step(GLASS_OPTICS_FADE_MILLIS / 2L)
        val fading = center()
        assertTrue("weight=$weight", weight > 0f && weight < 1f)
        assertTrue("fading=$fading", fading.red > .26f && fading.red < .99f)
        assertEquals(.25f, fading.blue, .03f)
        step(GLASS_OPTICS_FADE_MILLIS + 32L)
        val cheap = center()
        assertEquals(0f, weight, .001f)
        assertEquals(.25f, cheap.red, .03f)
        assertEquals(.25f, cheap.blue, .03f)
        compose.runOnIdle { samples = 0; source.value = Color.Blue }
        step(32)
        center()
        assertEquals(0, samples)
        compose.runOnIdle { covered.value = false }
        step(16)
        step(GLASS_OPTICS_FADE_MILLIS / 2L)
        val returning = center()
        assertTrue("returning=$returning weight=$weight", returning.blue > .26f && returning.blue < .99f)
        assertEquals(.25f, returning.red, .03f)
        step(GLASS_OPTICS_FADE_MILLIS + 32L)
        assertTrue(center().blue > .95f)
        assertTrue(samples > 0)
        assertEquals(bounds, compose.onNodeWithTag("pane").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun `rapid budget reversal continues from current strength and covered initial state costs zero`() {
        compose.mainClock.autoAdvance = false
        val enabled = mutableStateOf(false)
        var weight = -1f
        compose.setContent {
            val progress = glassOpticsProgress(enabled.value)
            SideEffect { weight = progress }
        }
        compose.waitForIdle()
        assertEquals(0f, weight, 0f)
        compose.runOnIdle { enabled.value = true }
        // Deliver the changed target before advancing the animation clock.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(48)
        compose.waitForIdle()
        val partial = weight
        assertTrue("partial=$partial", partial > 0f && partial < 1f)
        compose.runOnIdle { enabled.value = false }
        compose.waitForIdle()
        assertEquals(partial, weight, .001f)
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertTrue("reversal must remain continuous: before=$partial after=$weight", weight > 0f && weight < 1f)
        compose.mainClock.advanceTimeBy(GLASS_OPTICS_FADE_MILLIS + 32L)
        compose.waitForIdle()
        assertEquals(0f, weight, .001f)
    }
}
