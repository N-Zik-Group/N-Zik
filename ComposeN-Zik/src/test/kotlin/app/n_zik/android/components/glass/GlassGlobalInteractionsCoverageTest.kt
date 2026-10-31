package app.n_zik.android.components.glass

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.ui.UiIconButton
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Integration coverage for independent policy propagation beyond standalone controls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassGlobalInteractionsCoverageTest {
    @get:Rule val compose = createComposeRule()
    private enum class Surface { Group, List, Standalone, Icon }
    private val settings = mutableStateOf(GlassSettings(quality = GlassQuality.Performance))
    private val generation = mutableStateOf(0)
    private val source = MutableInteractionSource()
    private val shape = RoundedCornerShape(16.dp)

    @Test fun `group held halo independently follows global intensity and size`() {
        fixture(Surface.Group)
        assertRippleCoefficients()
    }

    @Test fun `ordinary lazy list held halo independently follows global intensity and size`() {
        fixture(Surface.List)
        assertRippleCoefficients(nativeFallback = true)
    }

    @Test fun `standalone intermediate Jelly moves content while Ripple remains visible`() {
        fixture(Surface.Standalone)
        assertJellyWithRipple()
    }

    @Test fun `whole group intermediate Jelly moves distant content while Ripple remains visible`() {
        fixture(Surface.Group)
        assertJellyWithRipple()
    }

    @Test fun `shared Material icon feedback retains independent focus and hover state layers`() {
        fixture(Surface.Icon)
        compose.mainClock.autoAdvance = false
        val before = capture()
        val focus = FocusInteraction.Focus()
        emit(focus)
        assertTrue("Keyboard focus needs visible feedback", difference(before, capture()) > 5)
        val hover = HoverInteraction.Enter()
        emit(hover)
        emit(FocusInteraction.Unfocus(focus))
        assertTrue("Hover must survive releasing another state", difference(before, capture()) > 5)
        emit(HoverInteraction.Exit(hover))
        assertEquals("Exiting both state layers restores idle", 0.0, difference(before, capture()), .05)
        // A fresh focus cycle must not leave a retained or queued state layer.
        val next = FocusInteraction.Focus()
        emit(next)
        emit(FocusInteraction.Unfocus(next))
        assertEquals(0.0, difference(before, capture()), .05)
    }

    private fun fixture(surface: Surface) {
        compose.setContent {
            CompositionLocalProvider(
                LocalGlassPreviewSettings provides settings.value,
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
            ) {
                GlassRippleProvider(settings.value) {
                    key(generation.value) {
                        Box(Modifier.size(240.dp, 110.dp).background(Color.Black).testTag("scene")) {
                            when (surface) {
                                Surface.Group -> GlassActionGroup(settings.value, GlassArea.ActionButtons,
                                    modifier = Modifier.offset(24.dp, 24.dp)) {
                                    GlassActionGroupItem({}, Modifier.testTag("neighbor")) {
                                        Box(Modifier.size(10.dp, 20.dp).background(Color.Red))
                                    }
                                    GlassActionGroupItem({}, Modifier.testTag("target")) { Box(Modifier.size(10.dp)) }
                                }
                                Surface.List -> LazyColumn(Modifier.offset(24.dp, 24.dp).size(180.dp, 80.dp)) {
                                    items(3) { index ->
                                        Box(Modifier.size(180.dp, 40.dp).background(Color.Black)
                                            .testTag(if (index == 0) "target" else "row.$index")
                                            .clickable(indication = LocalIndication.current,
                                                interactionSource = remember { MutableInteractionSource() }, onClick = {}))
                                    }
                                }
                                Surface.Standalone -> LiquidButton({}, null,
                                    Modifier.offset(24.dp, 24.dp).size(160.dp, 48.dp).testTag("target"),
                                    settings = settings.value, shape = shape, contentPadding = 0.dp,
                                    surfaceColor = Color.Black, flat = true) {
                                    Box(Modifier.size(160.dp, 48.dp)) {
                                        Box(Modifier.offset(12.dp, 12.dp).size(10.dp, 20.dp).background(Color.Red))
                                    }
                                }
                                Surface.Icon -> UiIconButton({}, Modifier.offset(24.dp, 24.dp).testTag("target"),
                                    interactionSource = source, shape = shape) {
                                    Box(Modifier.size(12.dp).background(Color.White))
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertRippleCoefficients(nativeFallback: Boolean = false) {
        // Zero intentionally restores the native indication on ordinary list rows.
        val strengths = if (nativeFallback) listOf(.4f, 1f) else listOf(0f, .4f, 1f)
        val alphaEnergy = strengths.map { intensity ->
            val (idle, held) = held(base().copy(jellyEnabled = false, rippleIntensity = intensity))
            difference(idle, held)
        }
        if (!nativeFallback) assertEquals(0.0, alphaEnergy[0], .05)
        val partial = alphaEnergy[alphaEnergy.lastIndex - 1]
        assertTrue("Partial alpha must reach this consumer", partial > 10)
        assertTrue("Full alpha must be brighter", alphaEnergy.last() > partial * 1.5)
        val sizeEnergy = listOf(.5f, 2f).map { radius ->
            val (idle, held) = held(base().copy(jellyEnabled = false, rippleSize = radius))
            difference(idle, held)
        }
        assertTrue("Larger radius must cover more of this consumer", sizeEnergy[1] > sizeEnergy[0] * 1.05)
    }

    private fun assertJellyWithRipple() {
        val displacement = listOf(0f, .5f, 1f).map { strength ->
            val s = base().copy(rippleEnabled = true, jellyIntensity = strength)
            val (idle, pressed) = held(s, drag = true)
            assertTrue("Ripple stays visible for every Jelly coefficient", difference(idle, pressed) > 10)
            abs(redCenter(pressed) - redCenter(idle))
        }
        assertEquals("Zero Jelly keeps content stationary", 0f, displacement[0], .2f)
        assertTrue("Intermediate Jelly still moves content", displacement[1] > .2f)
        assertTrue("Full Jelly moves content more", displacement[2] > displacement[1] * 1.3f)
    }

    private fun base() = GlassSettings(quality = GlassQuality.Performance)

    private fun held(s: GlassSettings, drag: Boolean = false): Pair<ImageBitmap, ImageBitmap> {
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { settings.value = s; generation.value++ }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val before = capture()
        val action = compose.onNodeWithTag("target")
        action.performTouchInput { down(center) }
        shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeBy(800)
        if (drag) {
            action.performTouchInput { moveTo(center + Offset(60f, 0f)) }
            compose.mainClock.advanceTimeBy(800)
        }
        val after = capture()
        action.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1500)
        return before to after
    }

    private fun capture() = compose.onNodeWithTag("scene").captureToImage()
    private fun emit(event: Interaction) {
        compose.runOnIdle { assertTrue(source.tryEmit(event)) }
        compose.mainClock.advanceTimeBy(1000)
    }

    private fun redCenter(image: ImageBitmap): Float {
        val p = image.toPixelMap(); var weight = 0.0; var total = 0.0
        for (y in 0 until p.height) for (x in 0 until p.width) {
            val c = p[x, y]; val w = (c.red - maxOf(c.green, c.blue)).coerceAtLeast(0f)
            weight += w; total += (x + .5) * w
        }
        assertTrue("Fixture must include the red content marker", weight > 10)
        return (total / weight).toFloat()
    }

    private fun difference(a: ImageBitmap, b: ImageBitmap): Double {
        val before = a.toPixelMap(); val after = b.toPixelMap(); var total = 0.0
        assertEquals(before.width, after.width); assertEquals(before.height, after.height)
        for (y in 0 until before.height) for (x in 0 until before.width) {
            val p = before[x, y]; val q = after[x, y]
            total += maxOf(abs(p.red - q.red), abs(p.green - q.green), abs(p.blue - q.blue))
        }
        return total
    }
}
