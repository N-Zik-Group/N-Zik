@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.ui.text.ExperimentalTextApi::class)

package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.it.fast4x.rimusic.ui.screens.player.rememberPlayerSheetState
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.CustomBottomSheet
import app.n_zik.android.components.glass.GLASS_OPTICS_FADE_MILLIS
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.LocalGlassOpticsTransitionProgress
import app.n_zik.android.components.glass.LocalGlassUnderlayCovered
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.glassOpticsProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerSheetGlassIntegrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `real sheet keeps source and pixels continuous across endpoint and interrupted handoffs`() {
        compose.mainClock.autoAdvance = false
        lateinit var sheet: PlayerSheetState
        var transitionValue = 0f
        var sourceSamples = 0
        val scene = mutableStateOf(Color.Blue)
        val covered = mutableStateOf(false)
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                Typography.fallback, shape, shape, shape)) {
                sheet = rememberPlayerSheetState(0.dp, 200.dp, 60.dp)
                Box(Modifier.size(200.dp).background(Color.Red).testTag("scene")) {
                    CustomBottomSheet(sheet, collapsedContent = {}) {
                        val transition = requireNotNull(LocalPlayerSheetGlassTransition.current)
                        transitionValue = transition.value
                        val recording: ContentDrawScope.() -> Unit = remember {
                            { sourceSamples++; drawContent() }
                        }
                        val livePlayer = rememberLayerBackdrop(onDraw = recording)
                        val capture = playerGlassSourceCaptureActive(GlassSettings(), transition, true)
                        val source = rememberPlayerControlsGlassSource(LocalGlassContainerBackdrop.current,
                            livePlayer, transition)
                        Box(Modifier.size(200.dp).background(Color.Black)) {
                            Box(Modifier.size(200.dp)
                                .then(if (capture) Modifier.layerBackdrop(livePlayer) else Modifier)
                                .background(scene.value))
                            // Native content remains live throughout the optical handoff.
                            Box(Modifier.offset(130.dp, 30.dp).size(20.dp).background(scene.value).testTag("native"))
                            Box(Modifier.offset(20.dp, 30.dp).size(80.dp).background(Color.Black))
                            CompositionLocalProvider(LocalGlassOpticsTransitionProgress provides transition.value,
                                LocalGlassUnderlayCovered provides covered.value) {
                                glassOpticsProgress() // Same shared optical clock consumed by production material.
                                Box(Modifier.offset(20.dp, 30.dp).size(80.dp).testTag("pane")
                                    .glassMaterial(GlassSettings(film = 0f, blur = 0f, rim = 0f,
                                        vibrancy = 0f), shape, ownBackdrop = source,
                                        drawFilm = false, drawShadow = false))
                            }
                        }
                    }
                }
            }
        }
        fun advance(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
        fun position(p: Float) {
            compose.runOnIdle { sheet.snapTo(60.dp + 140.dp * p) }
            advance(16)
        }
        fun pixel(tag: String): Color = compose.onNodeWithTag(tag).captureToImage().toPixelMap().let {
            it[it.width / 2, it.height / 2]
        }
        position(.99f)
        val before = pixel("pane")
        position(1f)
        val bounds = compose.onNodeWithTag("pane").fetchSemanticsNode().boundsInRoot
        val endpoint = pixel("pane")
        assertTrue("Endpoint must not swap in the full blue reflection", abs(endpoint.blue - before.blue) < .2f)
        advance(16)
        val first = transitionValue
        advance(48)
        assertTrue(transitionValue > first && transitionValue < 1f)
        compose.runOnIdle { scene.value = Color.Green }
        advance(16)
        assertTrue("Native player pixels remain live during restoration", pixel("native").green > .9f)
        advance(GLASS_OPTICS_FADE_MILLIS + 32L)
        assertEquals(1f, transitionValue, .001f)
        assertTrue("Restored optics sample the new live scene", pixel("pane").green > .9f)
        assertEquals(bounds, compose.onNodeWithTag("pane").fetchSemanticsNode().boundsInRoot)

        position(.99f)
        advance(48)
        val reversing = transitionValue
        assertTrue(reversing in 0f..1f && reversing < 1f)
        position(1f)
        advance(16)
        assertTrue("Rapid reversal resumes from the in-flight value", transitionValue > 0f)
        advance(GLASS_OPTICS_FADE_MILLIS + 32L)
        assertEquals(1f, transitionValue, .001f)
        position(.99f)
        advance(GLASS_OPTICS_FADE_MILLIS + 32L)
        assertEquals(0f, transitionValue, .001f)
        pixel("pane")
        compose.runOnIdle { sourceSamples = 0; scene.value = Color.Yellow }
        // With no running animation, first deliver the snapshot notification, then
        // let its recomposition/draw run on the following frame. A single batched
        // clock jump does not guarantee both phases once the sheet has settled.
        repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        pixel("pane")
        assertEquals("Player reflection capture stops after the closing fade", 0, sourceSamples)
        val native = pixel("native")
        assertTrue("Native player remains live with sampling off: $native", native.red > .9f)
    }
}
