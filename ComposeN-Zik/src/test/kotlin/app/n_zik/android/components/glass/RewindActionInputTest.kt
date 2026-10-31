package app.n_zik.android.components.glass

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.ui.screens.rewind.rewindCaptureMode
import app.n_zik.android.components.ui.screens.rewind.rewindShareCaptureActive
import app.n_zik.android.components.ui.screens.rewind.slides.RewindSeeMorePill
import app.n_zik.android.components.ui.screens.rewind.slides.RewindShareSlideButton
import app.n_zik.android.components.ui.screens.rewind.slides.rewindColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RewindActionInputTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `actual live Rewind pill follows UI zero eight and forty eight corners in native modes and preserves export shape`() {
        val radius = mutableStateOf(0.dp)
        val master = mutableStateOf(false)
        compose.setContent {
            val shape = RoundedCornerShape(BoundedCornerSize(radius.value, .15f))
            val appearance = Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape)
            val settings = GlassSettings(enabled = master.value,
                areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled))
            CompositionLocalProvider(LocalAppearance provides appearance, LocalGlassPreviewSettings provides settings) {
                Column(Modifier.background(Color.Black)) {
                    RewindSeeMorePill("MORE", {}, Modifier.size(120.dp, 40.dp).testTag("actual.pill"))
                    val expected = if (rewindCaptureMode.value || rewindShareCaptureActive.value)
                        RoundedCornerShape(100.dp) else shape
                    Box(Modifier.size(120.dp, 40.dp).testTag("expected.pill")
                        .background(rewindColors.value.cream.copy(alpha = .10f), expected)
                        .border(1.dp, rewindColors.value.cream.copy(alpha = .45f), expected))
                }
            }
        }
        fun assertEdges() {
            val actual = compose.onNodeWithTag("actual.pill").captureToImage().toPixelMap()
            val expected = compose.onNodeWithTag("expected.pill").captureToImage().toPixelMap()
            val edge = with(compose.density) { 3.dp.roundToPx() }
            for (y in 0 until actual.height) for (x in 0 until actual.width) {
                if (x < edge || y < edge || x >= actual.width - edge || y >= actual.height - edge)
                    assertEquals("Actual action edge at $x,$y radius=${radius.value} master=${master.value}", expected[x, y], actual[x, y])
            }
        }
        try {
            for (value in listOf(0.dp, 8.dp, 48.dp)) for (enabled in listOf(false, true)) {
                compose.runOnIdle { radius.value = value; master.value = enabled }
                assertEdges()
            }
            compose.runOnIdle { rewindCaptureMode.value = true }
            assertEdges()
            compose.runOnIdle { rewindCaptureMode.value = false; rewindShareCaptureActive.value = true }
            assertEdges()
        } finally {
            compose.runOnIdle { rewindCaptureMode.value = false; rewindShareCaptureActive.value = false }
        }
    }

    @Test fun `native explicit null stays quiet while nonnull Rewind action receives global feedback and material owns its feedback`() {
        compose.mainClock.autoAdvance = false
        val master = mutableStateOf(false)
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            val appearance = Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape)
            val settings = GlassSettings(enabled = master.value, quality = GlassQuality.Performance,
                areaModes = mapOf(GlassArea.ActionButtons to mode.value))
            CompositionLocalProvider(LocalAppearance provides appearance, LocalGlassPreviewSettings provides settings) {
                GlassRippleProvider(settings) {
                    Column(Modifier.background(Color.Black)) {
                        RewindSeeMorePill("MORE", {}, Modifier.size(120.dp, 40.dp).testTag("null.pill"))
                        RewindShareSlideButton(Color.White, {}, Modifier.testTag("nonnull.share"))
                    }
                }
            }
        }
        fun image(tag: String) = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        fun held(tag: String): Pair<PixelMap, PixelMap> {
            val before = image(tag)
            compose.onNodeWithTag(tag).performTouchInput { down(center) }
            shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(400)
            val during = image(tag)
            compose.onNodeWithTag(tag).performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1600)
            return before to during
        }
        for (enabled in listOf(false, true)) {
            compose.runOnIdle { master.value = enabled }
            val (before, during) = held("null.pill")
            assertTrue("An explicitly absent native indication stays absent with master=$enabled", samePixels(before, during))
        }
        val (before, during) = held("nonnull.share")
        assertFalse("A native nonnull action still receives the global highlight", samePixels(before, during))
        compose.runOnIdle { mode.value = GlassAreaMode.GlassOnly }
        val (materialBefore, materialDuring) = held("null.pill")
        assertFalse("Glass material owns feedback even when its native fallback has none", samePixels(materialBefore, materialDuring))
    }

    private fun samePixels(first: PixelMap, second: PixelMap): Boolean =
        first.width == second.width && first.height == second.height &&
            (0 until first.height).all { y -> (0 until first.width).all { x -> first[x, y] == second[x, y] } }
}
