package app.n_zik.android.components.glass

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import kotlin.math.abs
import kotlin.math.hypot
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
class GlassGroupFeedbackClipTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `real dice play settings halo stays in pressed cell and outer capsule at every roundness`() {
        val resolver = ApplicationProvider.getApplicationContext<Application>().contentResolver
        val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val roundness = mutableStateOf(0f)
        val native = mutableStateOf(false)
        val vertical = mutableStateOf(false)
        try {
            // Isolate the halo from whole-capsule motion. Separate group pixel tests exercise
            // held motion with the same mask inside the moving host.
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            compose.setContent {
                val shape = RoundedCornerShape(BoundedCornerSize(roundness.value.dp, .4f))
                val settings = GlassSettings(enabled = true, quality = GlassQuality.Performance)
                CompositionLocalProvider(LocalAppearance provides Appearance(
                    DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
                )) {
                    GlassRippleProvider(settings) {
                        Box(Modifier.size(240.dp, 220.dp).background(Color.Black).testTag("scene")) {
                            val cells: @Composable () -> Unit = {
                                listOf(R.drawable.dice, R.drawable.play, R.drawable.settings).forEachIndexed { index, icon ->
                                    GlassActionGroupItem({}, Modifier.testTag("cell.$index"), nativeIndication = native.value) {
                                        Icon(painterResource(icon), null, Modifier.size(GlassActionIconSize),
                                            tint = if (index == 0) Color.Blue else Color.White)
                                    }
                                }
                            }
                            val placement = Modifier.padding(start = 40.dp, top = 32.dp).testTag("group")
                            if (vertical.value) GlassVerticalActionGroup(settings, GlassArea.ActionButtons,
                                placement, actionCount = 3) { cells() }
                            else GlassActionGroup(settings, GlassArea.ActionButtons,
                                placement, separatorBefore = setOf(2)) { cells() }
                        }
                    }
                }
            }
            for (isVertical in listOf(false, true)) for (radius in listOf(0f, 8f, 48f)) for (useNative in listOf(false, true)) {
                compose.mainClock.autoAdvance = true
                compose.runOnIdle { roundness.value = radius; native.value = useNative; vertical.value = isVertical }
                compose.waitForIdle()
                compose.mainClock.autoAdvance = false
                val first = compose.onNodeWithTag("cell.0")
                val last = compose.onNodeWithTag("cell.2")
                val cell = first.fetchSemanticsNode().boundsInRoot
                last.fetchSemanticsNode()
                val group = compose.onNodeWithTag("group").fetchSemanticsNode().boundsInRoot
                val scene = compose.onNodeWithTag("scene")
                val before = scene.captureToImage().toPixelMap()
                if (radius == 48f) {
                    // At 48dp the UI's 40% corner includes this point; a 50% capsule excludes it.
                    val x = (group.left + 11f).toInt()
                    val y = (group.top + 1f).toInt()
                    assertTrue("Outer $isVertical skin follows UI roundness rather than a pill", before[x, y].red > .01f)
                }
                first.performTouchInput { down(Offset(10f, 12f)) }
                compose.mainClock.advanceTimeBy(400)
                val after = scene.captureToImage().toPixelMap()
                val r = radius.coerceAtMost(minOf(group.width, group.height) * .4f)
                var inside = 0
                var leaks = 0
                var neighborLeaks = 0
                for (y in 0 until after.height) for (x in 0 until after.width) {
                    val a = before[x, y]
                    val b = after[x, y]
                    if (maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)) <= .025f) continue
                    val px = x + .5f
                    val py = y + .5f
                    if (if (isVertical) py >= cell.bottom + 1f && py < group.bottom - 1f
                        else px >= cell.right + 1f && px < group.right - 1f) neighborLeaks++
                    val dx = maxOf(group.left + r - px, 0f, px - (group.right - r))
                    val dy = maxOf(group.top + r - py, 0f, py - (group.bottom - r))
                    val outside = px < group.left - 1f || px > group.right + 1f ||
                        py < group.top - 1f || py > group.bottom + 1f || hypot(dx, dy) > r + 1f
                    if (outside) leaks++ else inside++
                }
                assertTrue("Real halo paints inside radius=$radius native=$useNative", inside > 15)
                assertEquals("Halo cannot escape outer capsule radius=$radius native=$useNative", 0, leaks)
                assertEquals("Halo cannot reach another cell radius=$radius native=$useNative", 0, neighborLeaks)
                first.performTouchInput { cancel() }
                compose.mainClock.advanceTimeBy(1600)
            }
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { roundness.value = 8f; native.value = false; vertical.value = false }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            val first = compose.onNodeWithTag("cell.0")
            val originalSlot = first.fetchSemanticsNode().boundsInRoot
            first.performTouchInput { down(center); moveTo(center + Offset(0f, 500f), 100) }
            compose.mainClock.advanceTimeBy(700)
            val dragged = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
            var blueBelow = 0
            for (y in (originalSlot.bottom + 2f).toInt() until dragged.height) for (x in 0 until dragged.width) {
                val color = dragged[x, y]
                if (color.blue > .8f && color.red < .4f && color.green < .4f) blueBelow++
            }
            assertTrue("The complete moving capsule carries the real dice glyph beyond its old slot", blueBelow > 10)
            assertEquals("The inner halo mask cannot move the action's hit slot", originalSlot,
                first.fetchSemanticsNode().boundsInRoot)
            first.performTouchInput { cancel() }
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        }
    }
}
