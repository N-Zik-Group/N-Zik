package app.n_zik.android.components.glass

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.ui.UiIconButton
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalFoundationApi::class)
class GlassGlobalInteractionsTest {
    @get:Rule val compose = createComposeRule()
    private val settings = mutableStateOf(GlassSettings(quality = GlassQuality.Performance))
    private val generation = mutableStateOf(0)
    private val shape = RoundedCornerShape(20.dp)

    @Test fun `ripple intensity is monotonic while Jelly is disabled`() {
        fixture()
        val energy = listOf(0f, .4f, 1f).map { strength ->
            val frame = held(GlassSettings(jellyEnabled = false, rippleIntensity = strength))
            difference(frame.first, frame.second)
        }
        assertEquals(0.0, energy[0], .001)
        assertTrue("Intermediate ripple must be visible", energy[1] > 20)
        assertTrue("Full ripple must be brighter", energy[2] > energy[1] * 1.5)
    }

    @Test fun `ripple size increases held halo area and remains inside the UI shape`() {
        fixture()
        val frames = listOf(.5f, 2f).map { size -> held(GlassSettings(jellyEnabled = false, rippleSize = size)) }
        assertTrue(difference(frames[1].first, frames[1].second) > difference(frames[0].first, frames[0].second) * 1.1)
        frames.forEach { (before, after) ->
            val a = before.toPixelMap(); val b = after.toPixelMap()
            // The parent scene is black. Rounded corners and space outside the pane stay black.
            listOf(Offset(0f, 0f), Offset(25f, 25f), Offset((b.width - 1).toFloat(), (b.height - 1).toFloat())).forEach { p ->
                assertEquals(a[p.x.toInt(), p.y.toInt()], b[p.x.toInt(), p.y.toInt()])
            }
        }
    }

    @Test fun `Jelly off and zero hold the same visible stationary ripple`() {
        fixture(marker = true)
        val off = held(GlassSettings(jellyEnabled = false))
        val zero = held(GlassSettings(jellyIntensity = 0f))
        assertTrue(difference(off.first, off.second) > 20)
        assertEquals(0.0, difference(off.second, zero.second), .001)
        assertEquals(redCenter(off.first), redCenter(off.second), .15f)
    }

    @Test fun `Jelly intensity scales actual standalone motion with Ripple disabled`() {
        fixture(marker = true)
        assertMotionCoefficients(group = false)
    }

    @Test fun `Jelly intensity moves the complete group including the distant glyph`() {
        fixture(group = true, marker = true)
        assertMotionCoefficients(group = true)
    }

    @Test fun `global clickable uses bounded halo and preserves explicit null and native identities`() {
        val native = ripple(color = Color.Red)
        val configuration = RippleConfiguration(Color.Red)
        var observed: Indication? = null
        var observedConfiguration: RippleConfiguration? = null
        var explicitNull: Indication? = native
        compose.setContent {
            CompositionLocalProvider(LocalIndication provides native, LocalRippleConfiguration provides configuration) {
                GlassRippleProvider(settings.value) {
                    observed = LocalIndication.current
                    observedConfiguration = LocalRippleConfiguration.current
                    explicitNull = glassGlobalIndication(null)
                    Box(Modifier.size(120.dp, 48.dp).background(Color.Black).testTag("target")
                        .clickable(onClick = {}))
                }
            }
        }
        for (s in listOf(GlassSettings(enabled = false), GlassSettings(rippleEnabled = false), GlassSettings(rippleIntensity = 0f))) {
            compose.runOnIdle { settings.value = s }
            compose.runOnIdle {
                assertSame(native, observed)
                assertSame(configuration, observedConfiguration)
                assertNull(explicitNull)
            }
        }
        compose.runOnIdle { settings.value = GlassSettings(jellyEnabled = false) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val before = compose.onNodeWithTag("target").captureToImage()
        press()
        val after = compose.onNodeWithTag("target").captureToImage()
        assertTrue(difference(before, after) > 20)
        compose.onNodeWithTag("target").performTouchInput { up() }
    }

    @Test fun `LiquidButton keeps one native click long click and cancellation with independent switches`() {
        var clicks = 0; var holds = 0
        fixture(content = {
            LiquidButton({ clicks++ }, null, Modifier.testTag("target"), onLongClick = { holds++ },
                settings = settings.value, contentPadding = 0.dp, shape = shape) { Box(Modifier.size(100.dp, 20.dp)) }
        })
        for (s in listOf(GlassSettings(rippleEnabled = false), GlassSettings(jellyEnabled = false))) {
            compose.runOnIdle { settings.value = s }
            compose.onNodeWithTag("target").performTouchInput { click() }
            compose.onNodeWithTag("target").performTouchInput { longClick() }
            compose.onNodeWithTag("target").performTouchInput { down(center); moveTo(Offset(-150f, -150f)); up() }
        }
        compose.runOnIdle { assertEquals(2, clicks); assertEquals(2, holds) }
    }

    @Test fun `UiIconButton and LiquidButton disabled controls never invoke callbacks`() {
        var clicks = 0
        fixture(content = {
            Row {
                UiIconButton({ clicks++ }, Modifier.testTag("icon"), enabled = false) { Box(Modifier.size(20.dp)) }
                LiquidButton({ clicks++ }, null, Modifier.testTag("liquid"), enabled = false,
                    settings = settings.value) { Box(Modifier.size(20.dp)) }
                UiIconButton({ clicks++ }, Modifier.testTag("enabled")) { Box(Modifier.size(20.dp)) }
            }
        })
        for (s in listOf(GlassSettings(rippleEnabled = false), GlassSettings(jellyEnabled = false))) {
            compose.runOnIdle { settings.value = s }
            listOf("icon", "liquid", "enabled").forEach { compose.onNodeWithTag(it).performTouchInput { click() } }
        }
        compose.runOnIdle { assertEquals(2, clicks) }
    }

    private fun fixture(group: Boolean = false, marker: Boolean = false, content: (@Composable () -> Unit)? = null) {
        compose.setContent {
            CompositionLocalProvider(
                LocalGlassPreviewSettings provides settings.value,
                LocalAppearance provides Appearance(DefaultDarkColorPalette,
                    typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape),
            ) {
                GlassRippleProvider(settings.value) {
                    key(generation.value) {
                        Box(Modifier.size(240.dp, 110.dp).background(Color.Black).testTag("scene")) {
                            if (content != null) content()
                            else if (group) {
                                GlassActionGroup(settings.value, GlassArea.ActionButtons,
                                    modifier = Modifier.padding(start = 24.dp, top = 24.dp)) {
                                    GlassActionGroupItem({}, Modifier.testTag("neighbor")) { marker() }
                                    GlassActionGroupItem({}, Modifier.testTag("target")) { Box(Modifier.size(10.dp)) }
                                }
                            } else {
                                LiquidButton({}, null,
                                    Modifier.offset(24.dp, 24.dp).size(160.dp, 48.dp).testTag("target"),
                                    settings = settings.value, shape = shape, contentPadding = 0.dp,
                                    surfaceColor = Color.Black, flat = true) {
                                    Box(Modifier.size(160.dp, 48.dp)) {
                                        if (marker) Box(Modifier.offset(12.dp, 12.dp)) { marker() }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Composable private fun marker() { Box(Modifier.size(10.dp, 20.dp).background(Color.Red)) }

    private fun held(s: GlassSettings, drag: Boolean = false): Pair<ImageBitmap, ImageBitmap> {
        // Each case gets a fresh press state and settles before freezing the animation clock.
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { settings.value = s; generation.value++ }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val before = compose.onNodeWithTag("scene").captureToImage()
        press()
        if (drag) {
            compose.onNodeWithTag("target").performTouchInput { moveTo(center + Offset(60f, 0f)) }
            compose.mainClock.advanceTimeBy(800)
        }
        val after = compose.onNodeWithTag("scene").captureToImage()
        compose.onNodeWithTag("target").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1500)
        return before to after
    }

    private fun press() {
        compose.onNodeWithTag("target").performTouchInput { down(center) }
        shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeBy(800)
    }

    private fun assertMotionCoefficients(group: Boolean) {
        val displacement = listOf(0f, .5f, 1f).map { strength ->
            val frame = held(GlassSettings(rippleEnabled = false, jellyIntensity = strength), drag = true)
            abs(redCenter(frame.second) - redCenter(frame.first))
        }
        assertEquals("Zero Jelly keeps glyph stationary", 0f, displacement[0], .15f)
        assertTrue("Intermediate ${if (group) "group" else "button"} must move", displacement[1] > .2f)
        assertTrue("Full Jelly must move more", displacement[2] > displacement[1] * 1.3f)
    }

    private fun redCenter(image: ImageBitmap): Float {
        val p = image.toPixelMap(); var weight = 0.0; var total = 0.0
        for (y in 0 until p.height) for (x in 0 until p.width) {
            val c = p[x, y]; val w = (c.red - maxOf(c.green, c.blue)).coerceAtLeast(0f)
            weight += w; total += (x + .5) * w
        }
        assertTrue("Fixture must contain a red glyph", weight > 10)
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
