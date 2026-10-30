package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.backdrop.BackdropEffectScope
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import app.n_zik.android.components.glass.backdrop.effects.colorControls
import org.junit.Assert.assertEquals
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
class GlassBackdropInvalidationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `observed effect change schedules one repaint without recomposition or layout`() {
        val saturation = mutableFloatStateOf(1f)
        val repaint = mutableIntStateOf(0)
        var captures = 0
        var paints = 0
        var compositions = 0
        var observedSaturation = Float.NaN
        compose.mainClock.autoAdvance = false
        compose.setContent {
            compositions++
            val source: DrawScope.() -> Unit = remember {
                { captures++; drawRect(Color.Red) }
            }
            val backdrop = rememberCanvasBackdrop(source)
            val shape = remember { { RoundedCornerShape(12.dp) } }
            // Saturation changes no geometry or effect padding. After the first
            // size-dependent draw it is read only by the effect observer.
            val effects: BackdropEffectScope.() -> Unit = remember {
                {
                    observedSaturation = saturation.floatValue
                    colorControls(saturation = observedSaturation)
                }
            }
            val surface: DrawScope.() -> Unit = remember { { repaint.intValue; paints++ } }
            Box(Modifier.size(160.dp, 64.dp).drawPlainBackdrop(
                backdrop = backdrop, shape = shape, effects = effects,
                onDrawSurface = surface,
            ))
        }
        fun settleFrame() {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            // Native Robolectric records draw callbacks when the surface is captured.
            compose.onRoot().captureToImage()
        }
        repeat(3) { settleFrame() }
        // Drop the first draw's incidental saturation read from updateEffects:
        // this draw has the same size, so only the dedicated observer retains it.
        compose.runOnIdle { repaint.intValue++ }
        repeat(3) { settleFrame() }
        val initialCaptures = captures
        val initialPaints = paints
        val initialCompositions = compositions
        assertTrue("the initial surface must be drawn", initialPaints > 0)
        compose.runOnIdle { saturation.floatValue = .4f }
        repeat(3) { settleFrame() }
        compose.runOnIdle {
            assertEquals("effect-only state must not recompose the surface", initialCompositions, compositions)
            assertTrue("effect observer must request a surface repaint: paints=$initialPaints->$paints, " +
                "captures=$initialCaptures->$captures, last saturation=$observedSaturation", paints > initialPaints)
            assertTrue("dirty canvas capture must be refreshed", captures > initialCaptures)
        }
        val settledPaints = paints
        val settledCaptures = captures
        repeat(3) { settleFrame() }
        compose.runOnIdle {
            assertEquals("invalidation must not start a perpetual redraw", settledPaints, paints)
            assertEquals("a static canvas must stop being captured", settledCaptures, captures)
        }
    }
}
