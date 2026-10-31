package app.n_zik.android.components.glass

import android.app.Application
import android.os.Looper
import java.util.concurrent.TimeUnit
import org.robolectric.Shadows.shadowOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
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
class GlassChipFeedbackBoundsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `native and glass chip halos use the visible shape rather than the touch spacer`() {
        val radius = mutableStateOf(0)
        val material = mutableStateOf(false)
        var clicks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = RoundedCornerShape(radius.value.dp)
            val appearance = Appearance(colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = shape, artistThumbnailShape = shape, uiRoundnessShape = shape)
            val settings = GlassSettings(quality = GlassQuality.Performance, jellyEnabled = false,
                areaModes = mapOf(GlassArea.ActionButtons to
                    if (material.value) GlassAreaMode.GlassAndContour else GlassAreaMode.Disabled))
            CompositionLocalProvider(LocalAppearance provides appearance, LocalGlassPreviewSettings provides settings) {
                GlassRippleProvider(settings) {
                    Row(Modifier.size(240.dp, 64.dp).background(Color.Black).clipToBounds().testTag("row")) {
                        Box(Modifier.size(120.dp, 64.dp), contentAlignment = Alignment.Center) {
                            GlassFilterChip(false, { clicks++ },
                                label = { Box(Modifier.size(40.dp, 12.dp).background(Color.Red)) },
                                modifier = Modifier.testTag("filter"), shape = shape, settings = settings,
                                colors = FilterChipDefaults.filterChipColors(containerColor = Color.Black))
                        }
                        Box(Modifier.size(120.dp, 64.dp), contentAlignment = Alignment.Center) {
                            GlassSuggestionChip({ clicks++ },
                                label = { Box(Modifier.size(40.dp, 12.dp).background(Color.Red)) },
                                modifier = Modifier.testTag("suggestion"), settings = settings,
                                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color.Black))
                        }
                    }
                }
            }
        }
        for (glass in listOf(false, true)) for (corner in listOf(0, 8, 48)) {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { material.value = glass; radius.value = corner }
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(160)
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            for (tag in listOf("filter", "suggestion")) {
                val row = compose.onNodeWithTag("row")
                val chip = compose.onNodeWithTag(tag)
                val bounds = chip.fetchSemanticsNode().boundsInRoot
                val idle = row.captureToImage().toPixelMap()
                chip.performTouchInput { down(center) }
                compose.mainClock.advanceTimeBy(350)
                val held = row.captureToImage().toPixelMap()
                val rowBounds = row.fetchSemanticsNode().boundsInRoot
                val scale = held.height / rowBounds.height
                val middleY = ((bounds.center.y - rowBounds.top) * scale).toInt()
                val left = ((bounds.left - rowBounds.left) * scale).toInt()
                val right = ((bounds.right - rowBounds.left) * scale).toInt()
                val visualHalfHeight = (16f * held.height / 64f).toInt()
                var changed = 0
                for (y in 0 until held.height) for (x in 0 until held.width) {
                    if (held[x, y] != idle[x, y]) {
                        changed++
                        assertTrue("Halo escaped 32dp visual surface: $glass/$corner/$tag at $x,$y",
                            y >= middleY - visualHalfHeight && y < middleY + visualHalfHeight)
                        assertTrue("Halo must not paint its neighboring chip", x >= left && x < right)
                        val radiusPx = (corner * held.height / 64f).coerceAtMost(visualHalfHeight.toFloat())
                        val nearestX = (x + .5f).coerceIn(left + radiusPx, right - radiusPx)
                        val nearestY = (y + .5f).coerceIn(middleY - visualHalfHeight + radiusPx,
                            middleY + visualHalfHeight - radiusPx)
                        val dx = x + .5f - nearestX
                        val dy = y + .5f - nearestY
                        assertTrue("Halo must follow UI shape $glass/$corner/$tag at $x,$y; bounds=$bounds left=$left right=$right middleY=$middleY halfHeight=$visualHalfHeight radius=$radiusPx dx=$dx dy=$dy",
                            dx * dx + dy * dy <= (radiusPx + 1f) * (radiusPx + 1f))
                    }
                }
                assertTrue("Held chip must show its bounded halo: $glass/$corner/$tag", changed > 0)
                chip.performTouchInput { up() }
                compose.mainClock.advanceTimeBy(1500)
            }
        }
        compose.runOnIdle { assertEquals(12, clicks) }
    }
}
