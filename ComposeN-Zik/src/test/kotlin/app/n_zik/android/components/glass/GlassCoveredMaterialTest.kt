package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
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
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
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
class GlassCoveredMaterialTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `covered material keeps scene capture live and restores fresh optics without moving`() {
        compose.mainClock.autoAdvance = false
        val settled = mutableStateOf(true)
        val source = mutableStateOf(Color.Red)
        var samples = 0
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                val recording: ContentDrawScope.() -> Unit = remember { { samples++; drawContent() } }
                val backdrop = rememberLayerBackdrop(onDraw = recording)
                Box(Modifier.testTag("scene").background(Color.Black)) {
                    Box(Modifier.size(80.dp)
                        .layerBackdrop(backdrop)
                        .background(source.value))
                    CompositionLocalProvider(LocalGlassUnderlayCovered provides !settled.value) {
                        Box(Modifier.size(80.dp).testTag("pane").glassMaterial(
                            GlassSettings(enabled = true, blur = 0f, rim = 0f, vibrancy = 0f), shape,
                            ownBackdrop = backdrop, drawFilm = false, drawShadow = false,
                        ))
                    }
                }
            }
        }
        fun center(): Color {
            // The inexpensive pane is transparent. Capture the composed siblings, not its isolated layer.
            val pane = compose.onNodeWithTag("pane").fetchSemanticsNode().boundsInRoot
            val scene = compose.onNodeWithTag("scene").fetchSemanticsNode().boundsInRoot
            val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
            val x = ((pane.center.x - scene.left) / scene.width * pixels.width).toInt()
            val y = ((pane.center.y - scene.top) / scene.height * pixels.height).toInt()
            return pixels[x, y]
        }
        fun awaitSourceComposition() {
            // The manual clock delivers the snapshot notification before the next frame recomposes.
            repeat(2) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
        }
        val bounds = compose.onNodeWithTag("pane").fetchSemanticsNode().boundsInRoot
        val initial = center()
        assertTrue("initial=$initial", initial.red > .9f)
        compose.runOnIdle { settled.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(GLASS_OPTICS_FADE_MILLIS.toLong() + 48)
        compose.waitForIdle()
        // Reset after the optical fade has reached the cheap endpoint.
        center()
        compose.runOnIdle { samples = 0; source.value = Color.Blue }
        awaitSourceComposition()
        val cheap = center()
        assertTrue("cheap=$cheap samples=$samples", cheap.blue > .9f)
        assertTrue("covered scene capture must stay fresh", samples > 0)
        assertEquals(bounds, compose.onNodeWithTag("pane").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { settled.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(GLASS_OPTICS_FADE_MILLIS.toLong() + 48)
        compose.waitForIdle()
        val restored = center()
        assertTrue("restored=$restored samples=$samples", restored.blue > .9f)
        assertTrue(samples > 0)
        compose.runOnIdle { source.value = Color.Green }
        awaitSourceComposition()
        val refreshed = center()
        assertTrue("refreshed=$refreshed samples=$samples", refreshed.green > .9f)
        assertEquals(bounds, compose.onNodeWithTag("pane").fetchSemanticsNode().boundsInRoot)
    }
}
