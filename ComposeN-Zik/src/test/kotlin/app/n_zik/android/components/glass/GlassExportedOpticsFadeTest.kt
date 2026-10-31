package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.BackdropEffectScope
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import app.n_zik.android.components.glass.backdrop.effects.blur
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassExportedOpticsFadeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `post effect fade does not recapture or recompile fixed blur and samples nothing at zero`() {
        val alpha = mutableStateOf(1f)
        val scene = mutableStateOf(Color.Red)
        var captures = 0
        var effectApplications = 0
        compose.setContent {
            val source = rememberLayerBackdrop()
            val shape: () -> Shape = remember { { RoundedCornerShape(8.dp) } }
            val effects: BackdropEffectScope.() -> Unit = remember {
                { effectApplications++; blur(8.dp.toPx()) }
            }
            val sample: DrawScope.(DrawScope.() -> Unit) -> Unit = remember {
                { draw -> captures++; draw() }
            }
            val opticalAlpha: () -> Float = remember { { alpha.value } }
            Box(Modifier.size(200.dp, 100.dp).background(Color.Black)) {
                Box(Modifier.size(200.dp, 100.dp).layerBackdrop(source).background(scene.value))
                Box(Modifier.offset(100.dp).size(100.dp).background(Color.Black))
                Box(Modifier.offset(100.dp).size(100.dp).testTag("capture")
                    .drawPlainBackdrop(source, shape, effects,
                        onDrawBackdrop = sample, opticsAlphaProvider = opticalAlpha))
            }
        }
        fun pixel(): Color = compose.onNodeWithTag("capture").captureToImage().toPixelMap()
            .let { it[it.width / 2, it.height / 2] }
        assertTrue(pixel().red > .95f)
        compose.runOnIdle { captures = 0; effectApplications = 0 }
        for (weight in listOf(.75f, .5f, .25f, 0f)) {
            compose.runOnIdle { alpha.value = weight }
            assertEquals("Processed pixels follow alpha=$weight", weight, pixel().red, .04f)
        }
        assertEquals("A fade must reuse the live source display list", 0, captures)
        assertEquals("A fade must not rebuild the blur chain", 0, effectApplications)
        // A changed source must not be sampled at zero. Restoration takes one
        // fresh recording, then resumes alpha-only compositing without retuning.
        compose.runOnIdle { scene.value = Color.Blue }
        assertEquals(0f, pixel().blue, .04f)
        assertEquals("The zero endpoint samples nothing even when its source changes", 0, captures)
        compose.runOnIdle { alpha.value = .5f }
        assertEquals(.5f, pixel().blue, .04f)
        assertEquals("Restoration captures the current source once", 1, captures)
        compose.runOnIdle { alpha.value = 1f }
        assertEquals(1f, pixel().blue, .04f)
        assertEquals("Alpha-only frames reuse the refreshed capture", 1, captures)
        assertEquals(0, effectApplications)
    }

    @Test fun `real exported material fades from zero while native content and source stay live`() {
        compose.mainClock.autoAdvance = false
        val covered = mutableStateOf(true)
        val scene = mutableStateOf(Color.Red)
        val native = mutableStateOf(Color.Yellow)
        var progress = -1f
        var exportedSource: LayerBackdrop? = null
        var sourceLayer: LayerBackdrop? = null
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            val settings = GlassSettings(blur = 8f, rim = 0f, vibrancy = 0f,
                depth = .4f, amount = .6f, film = .25f)
            val source = rememberLayerBackdrop()
            sourceLayer = source
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                Typography.fallback, shape, shape, shape), LocalGlassBackdrop provides source) {
                Box(Modifier.size(200.dp, 100.dp).background(Color.Black).testTag("scene")) {
                    Box(Modifier.size(200.dp, 100.dp).layerBackdrop(source).background(scene.value))
                    Box(Modifier.size(70.dp).background(Color.Black).glassMaterial(settings.copy(film = 0f), shape,
                        ownBackdrop = source, drawShadow = false))
                    Box(Modifier.offset(100.dp).size(100.dp).background(Color.Black))
                    CompositionLocalProvider(LocalGlassUnderlayCovered provides covered.value) {
                        val observedProgress = glassOpticsProgress()
                        SideEffect { progress = observedProgress }
                        GlassMaterialContainerScope(Modifier.offset(100.dp).size(100.dp),
                            material = { exported ->
                                exportedSource = exported
                                Modifier.glassMaterial(settings, shape, filmColor = Color.White,
                                    exportedBackdrop = exported, drawShadow = false)
                            }) { surface ->
                            Box(surface.testTag("material")) {
                                // The native child is deliberately outside the optical fade.
                                Box(Modifier.offset(65.dp, 15.dp).size(20.dp)
                                    .background(native.value).testTag("native"))
                                CompositionLocalProvider(LocalGlassUnderlayCovered provides false) {
                                    Box(Modifier.offset(15.dp, 15.dp).size(35.dp).testTag("export")
                                        // Isolate the export: without this base its transparent
                                        // film is composited over the parent's film a second time.
                                        .background(Color.Black)
                                        .glassMaterial(settings.copy(blur = 0f, film = 0f), shape,
                                            ownBackdrop = LocalGlassContainerBackdrop.current,
                                            drawShadow = false))
                                }
                            }
                        }
                    }
                }
            }
        }
        data class Frame(val parent: Color, val child: Color, val native: Color,
            val control: Color, val source: Color,
            val progress: Float,
            val sourceVersion: Int?, val sourceAttached: Boolean?,
            val exportVersion: Int?, val coordinatesAttached: Boolean?)
        fun snapshot(): Frame {
            // Capture one complete Android root draw. Capturing only a child can
            // replay its old display list before the exporting parent has drawn.
            val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
            fun at(xDp: Float, yDp: Float): Color = pixels[
                (pixels.width * xDp / 200f).toInt(), (pixels.height * yDp / 100f).toInt()]
            return Frame(at(175f, 75f), at(132f, 32f), at(175f, 25f),
                at(35f, 35f), at(90f, 90f), progress,
                sourceLayer?.contentVersion, sourceLayer?.layerCoordinates?.isAttached,
                exportedSource?.contentVersion, exportedSource?.layerCoordinates?.isAttached)
        }
        fun frame(ms: Long) {
            var remaining = ms
            // Deliver Android draws between animation ticks. A single batched
            // clock jump advances Compose, but can leave RenderEffect output on
            // the capture from before the jump until the next native frame.
            while (remaining > 0) {
                val step = minOf(16L, remaining)
                compose.mainClock.advanceTimeBy(step)
                compose.waitForIdle()
                // waitForIdle alone does not render a native Android frame
                // while the Compose test clock is paused.
                compose.onNodeWithTag("scene").captureToImage()
                remaining -= step
            }
        }
        compose.waitForIdle()
        val initial = snapshot()
        assertEquals(.25f, initial.child.red, .04f)
        assertTrue("The source is red before coverage changes: $initial", initial.source.red > .95f)
        assertTrue("The always-active optical control renders the source: $initial", initial.control.red > .95f)
        val bounds = compose.onNodeWithTag("material").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { covered.value = false }
        compose.waitForIdle()
        assertEquals("Restoration starts at cheap film", .25f, snapshot().child.red, .04f)
        frame(16)
        frame(16)
        val early = snapshot()
        frame(GLASS_OPTICS_FADE_MILLIS / 2L)
        val middle = snapshot()
        compose.runOnIdle { scene.value = Color.Blue; native.value = Color.Green }
        repeat(2) { frame(16) }
        val live = snapshot()
        frame(GLASS_OPTICS_FADE_MILLIS.toLong())
        val complete = snapshot()
        assertTrue("Export must show intermediate pixels, early=$early middle=$middle live=$live complete=$complete; " +
            "parent and child come from the same root draw",
            middle.child.red > .28f && middle.child.red < .98f && middle.child.red > early.child.red)
        assertEquals("Export follows its parent within the same Android draw", middle.parent.red,
            middle.child.red, .04f)
        assertTrue("Native children stay live during optical restoration: $live", live.native.green > .95f)
        assertTrue("The current source and always-active control are blue: $live",
            live.source.blue > .95f && live.control.blue > .95f)
        assertTrue("The fading export follows new source pixels: $live", live.child.blue > live.child.red)
        assertTrue("The restored export samples the live blue scene", complete.child.blue > .95f)
        assertEquals(bounds, compose.onNodeWithTag("material").fetchSemanticsNode().boundsInRoot)
    }
}
