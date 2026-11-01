package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.BackdropEffectScope
import app.n_zik.android.components.glass.backdrop.drawBackdrop
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
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
class GlassLiveModalCaptureTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test fun `plain live capture bypasses held and unchanged source records`() = verifyLiveCapture(false)
    @Test fun `optical live capture bypasses held and constant loop buckets`() = verifyLiveCapture(true)

    private fun verifyLiveCapture(optical: Boolean) {
        val live = mutableStateOf(true)
        val shown = mutableStateOf(true)
        var clockReader: (() -> Long)? = null
        var captures = 0
        var color = Color.Red
        val source = object : Backdrop {
            override val isCoordinatesDependent = false
            override val contentVersion = 1
            override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
                captures++
                drawRect(color)
            }
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(100.dp)) {
                if (shown.value) {
                  // Simulate entry.locals captured from a disposed parent menu.
                  CompositionLocalProvider(LocalGlassLiveFrame provides remember { { 0L } }) {
                    val clock = rememberGlassModalLiveFrame()
                    SideEffect { clockReader = clock }
                    val shape = remember { { RoundedCornerShape(0.dp) } }
                    val effects: BackdropEffectScope.() -> Unit = remember { {} }
                    val modifier = if (optical) Modifier.drawBackdrop(source, shape, effects,
                        highlight = null, shadow = null, frozen = { true }, loopBucket = { 0 },
                        liveFrame = if (live.value) clock else null)
                    else Modifier.drawPlainBackdrop(source, shape, effects, frozen = { true },
                        liveFrame = if (live.value) clock else null)
                    Box(Modifier.size(80.dp).then(modifier))
                  }
                }
            }
        }
        fun frame(): Color {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            return compose.onRoot().captureToImage().toPixelMap()[20, 20]
        }
        repeat(3) { frame() }
        val before = captures
        val firstTick = requireNotNull(clockReader).invoke()
        // No snapshot write and no changed contentVersion: only the modal draw clock is live.
        color = Color.Blue
        repeat(3) { assertTrue(frame().blue > .9f) }
        assertTrue("modal captures must not hold their settled-open image", captures > before)
        assertTrue("own clock advances despite zero animation scale and a stale inherited clock",
            requireNotNull(clockReader).invoke() > firstTick)
        compose.runOnIdle { live.value = false }
        repeat(3) { frame() }
        val cached = captures
        repeat(3) { frame() }
        assertEquals("ordinary controls retain their idle cache", cached, captures)
        compose.runOnIdle { live.value = true }
        repeat(3) { frame() }
        val active = captures
        assertTrue("live modal resumes refreshing", active > cached)
        compose.runOnIdle { shown.value = false }
        repeat(3) { frame() }
        assertEquals("disposing an actively refreshing modal stops its clock", active, captures)
    }
}
