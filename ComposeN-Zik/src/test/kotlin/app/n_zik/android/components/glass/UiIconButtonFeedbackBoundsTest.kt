package app.n_zik.android.components.glass

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.Shape
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
import org.junit.Assert.assertEquals
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
class UiIconButtonFeedbackBoundsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `native pixels dimensions and bounded halos match for all icon and page button sizes`() {
        val global = mutableStateOf(false)
        val radius = mutableStateOf(0)
        val explicitSize = mutableStateOf(0)
        val family = mutableStateOf(0)
        val wrapped = mutableStateOf(false)
        val generation = mutableStateOf(0)
        var clicks = 0
        compose.setContent {
            val shape = RoundedCornerShape(radius.value.dp)
            val settings = GlassSettings(enabled = global.value, jellyEnabled = false,
                areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled,
                    GlassArea.DialogButtons to if (radius.value == 8) GlassAreaMode.ContourOnly else GlassAreaMode.Disabled))
            AppearanceFixture(shape, settings) {
                Box(Modifier.size(100.dp, 80.dp).background(Color.Black).testTag("canvas"), Alignment.Center) {
                    // Recreate each control: Android RippleDrawable has its own looper clock.
                    // Compare at the same absolute coordinates to avoid raster AA phase changes.
                    key(generation.value, wrapped.value) {
                        val dimensions = if (explicitSize.value == 0) Modifier else Modifier.size(explicitSize.value.dp)
                        Control(family.value, wrapped.value, dimensions.testTag("action"), shape, settings) { clicks++ }
                    }
                }
            }
        }
        for (active in listOf(false, true)) for (kind in 0..4)
            for (corner in listOf(0, 8, 48)) for (size in listOf(0, 40, 48)) {
                compose.mainClock.autoAdvance = true
                compose.runOnIdle {
                    global.value = active; family.value = kind; radius.value = corner
                    explicitSize.value = size; wrapped.value = false; generation.value++
                }
                settle()
                val canvas = compose.onNodeWithTag("canvas")
                val native = canvas.captureToImage().toPixelMap()
                val nativeBounds = compose.onNodeWithTag("action").fetchSemanticsNode().boundsInRoot
                compose.runOnIdle { wrapped.value = true }
                settle()
                val idle = canvas.captureToImage().toPixelMap()
                val action = compose.onNodeWithTag("action")
                val description = "family=$kind global=$active radius=$corner size=$size"
                assertIdentical(native, idle, description)
                assertEquals("Native control bounds: $description", nativeBounds, action.fetchSemanticsNode().boundsInRoot)
                compose.mainClock.autoAdvance = false
                action.performTouchInput { down(center) }
                shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
                compose.mainClock.advanceTimeBy(350)
                if (active) {
                    val held = canvas.captureToImage().toPixelMap()
                    var changed = 0
                    val visualHeight = (if (size == 0) 40 else size) * held.height / 80f
                    val top = (held.height - visualHeight) / 2f
                    for (y in 0 until held.height) for (x in 0 until held.width) {
                        if (held[x, y] != idle[x, y]) {
                            changed++
                            assertTrue("Halo escaped ${visualHeight}px visible height: $description at $x,$y",
                                y + .5f >= top && y + .5f < top + visualHeight)
                            assertTrue("Halo escaped actual native fill and shape: $description at $x,$y " +
                                "native=${native[x, y]} idle=${idle[x, y]} held=${held[x, y]}",
                                native[x, y] != Color.Black)
                        }
                    }
                    assertTrue("Actual global halo must appear: $description", changed > 0)
                }
                action.performTouchInput { up() }
                shadowOf(Looper.getMainLooper()).idleFor(1600, TimeUnit.MILLISECONDS)
                compose.mainClock.advanceTimeBy(1600)
                if (active) assertIdentical(idle, canvas.captureToImage().toPixelMap(), "Released $description")
            }
        compose.runOnIdle { assertEquals("Only Material owns each click", 90, clicks) }
    }

    @Test fun `compact row placement exactly matches native controls for default and explicit sizes`() {
        val global = mutableStateOf(false)
        val family = mutableStateOf(0)
        val wrapped = mutableStateOf(false)
        val generation = mutableStateOf(0)
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            val settings = GlassSettings(enabled = global.value,
                areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled,
                    GlassArea.DialogButtons to GlassAreaMode.ContourOnly))
            AppearanceFixture(shape, settings) {
                key(generation.value, wrapped.value) {
                    Row(Modifier.size(220.dp, 64.dp).background(Color.Black).testTag("row")) {
                        for (size in listOf(0, 42, 48)) {
                            val dimensions = (if (size == 0) Modifier else Modifier.size(size.dp))
                                .testTag("control.$size")
                            Control(family.value, wrapped.value, dimensions, shape, settings) {}
                        }
                        Box(Modifier.size(4.dp).background(Color.Green).testTag("after"))
                    }
                }
            }
        }
        for (active in listOf(false, true)) for (kind in 0..4) {
            compose.runOnIdle { global.value = active; family.value = kind; wrapped.value = false; generation.value++ }
            settle()
            val row = compose.onNodeWithTag("row")
            val tags = listOf("control.0", "control.42", "control.48", "after")
            val expectedBounds = tags.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
            val expected = row.captureToImage().toPixelMap()
            compose.runOnIdle { wrapped.value = true }
            settle()
            tags.forEachIndexed { index, tag ->
                assertEquals("Position and dimensions family=$kind master=$active $tag", expectedBounds[index],
                    compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)
            }
            assertIdentical(expected, row.captureToImage().toPixelMap(), "Compact row family=$kind master=$active")
        }
    }

    private fun settle() {
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
    }

    private fun assertIdentical(expected: PixelMap, actual: PixelMap, description: String) {
        assertEquals("Width $description", expected.width, actual.width)
        assertEquals("Height $description", expected.height, actual.height)
        for (y in 0 until expected.height) for (x in 0 until expected.width) {
            assertEquals("$description at $x,$y", expected[x, y], actual[x, y])
        }
    }
}

@Composable
private fun AppearanceFixture(shape: Shape, settings: GlassSettings, content: @Composable () -> Unit) {
    val appearance = Appearance(colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
        thumbnailShape = shape, artistThumbnailShape = shape, uiRoundnessShape = shape)
    CompositionLocalProvider(LocalAppearance provides appearance, LocalGlassPreviewSettings provides settings) {
        GlassRippleProvider(settings, content)
    }
}

@Composable
private fun Control(family: Int, wrapped: Boolean, modifier: Modifier, shape: Shape,
    settings: GlassSettings, onClick: () -> Unit) {
    val glyph: @Composable () -> Unit = { Box(Modifier.size(12.dp).background(Color.Magenta)) }
    if (family == 0 || family == 3) {
        val colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Red)
        if (wrapped && family == 3) GlassDialogIconButton(onClick, modifier, colors = colors, content = glyph)
        else if (wrapped) UiIconButton(onClick, modifier, colors = colors, shape = shape, content = glyph)
        else IconButton({}, modifier, colors = colors, shape = shape, content = glyph)
    } else {
        val colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
        val rowGlyph: @Composable RowScope.() -> Unit = { glyph() }
        if (family == 1) {
            if (wrapped) PageButton(onClick, modifier, shape = shape, colors = colors,
                elevation = null, settings = settings, content = rowGlyph)
            else Button({}, modifier, shape = shape, colors = colors, elevation = null, content = rowGlyph)
        } else if (family == 2) {
            if (wrapped) PageTextButton(onClick, modifier, shape = shape, colors = colors,
                settings = settings, content = rowGlyph)
            else TextButton({}, modifier, shape = shape, colors = colors, content = rowGlyph)
        } else {
            if (wrapped) GlassDialogOutlinedButton(onClick, modifier, colors = colors,
                border = null, content = rowGlyph)
            else OutlinedButton({}, modifier, shape = shape, colors = colors, border = null, content = rowGlyph)
        }
    }
}
