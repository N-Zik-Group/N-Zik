package app.n_zik.android.components.glass

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassChipLongHoldGestureTest {
    @get:Rule val compose = createComposeRule()
    private val material = mutableStateOf(false)
    private val suggestion = mutableStateOf(false)
    private val generation = mutableStateOf(0)
    private var scroll: ScrollState? = null
    private var clicks = 0

    @Test fun `immediate chip drags scroll the row in native and Glass modes`() {
        fixture()
        for (glass in listOf(false, true)) for (keyword in listOf(false, true)) {
            reset(glass, keyword)
            compose.onNodeWithTag("chip").performTouchInput {
                down(center)
                moveTo(center - Offset(90f, 0f), delayMillis = 150)
                up()
            }
            compose.mainClock.advanceTimeBy(1000)
            compose.runOnIdle {
                assertTrue("Immediate swipe must scroll $glass/$keyword", requireNotNull(scroll).value > 0)
                assertEquals("Dragging must not invoke a chip", 0, clicks)
            }
        }
    }

    @Test fun `stationary long hold claims Glass chip motion while native chips keep row scrolling`() {
        fixture()
        for (glass in listOf(false, true)) for (keyword in listOf(false, true)) {
            reset(glass, keyword)
            val idle = capture()
            val chip = compose.onNodeWithTag("chip")
            chip.performTouchInput { down(center) }
            shadowOf(Looper.getMainLooper()).idleFor(800, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(800)
            val held = capture()
            chip.performTouchInput {
                advanceEventTime(800)
                moveTo(center - Offset(100f, 0f), delayMillis = 150)
            }
            compose.mainClock.advanceTimeBy(800)
            val dragged = capture()
            compose.runOnIdle {
                if (glass) assertEquals("Long-held Glass chip owns its drag", 0, requireNotNull(scroll).value)
                else assertTrue("Native long-held chip leaves scrolling native", requireNotNull(scroll).value > 0)
                assertEquals(0, clicks)
            }
            if (glass) {
                assertTrue("Held Glass content must follow the claimed pointer", abs(redCenter(dragged) - redCenter(held)) > .5f)
                assertTrue("Long hold must change the chip's rendered content", abs(redCenter(dragged) - redCenter(idle)) > .5f)
            }
            chip.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1500)
            compose.runOnIdle { assertEquals("A drag never becomes a late click", 0, clicks) }
            if (glass) assertEquals("Released pane returns to its original place", redCenter(idle), redCenter(capture()), .25f)
        }
    }

    private fun fixture() {
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            val config = GlassSettings(quality = GlassQuality.Performance,
                areaModes = mapOf(GlassArea.ActionButtons to
                    if (material.value) GlassAreaMode.GlassAndContour else GlassAreaMode.Disabled))
            CompositionLocalProvider(LocalGlassPreviewSettings provides config,
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape)) {
                GlassRippleProvider(config) {
                    key(generation.value) {
                        val state = rememberScrollState()
                        SideEffect { scroll = state }
                        Box(Modifier.size(240.dp, 110.dp).background(Color.Black).testTag("scene")) {
                            Row(Modifier.offset(24.dp, 24.dp).size(180.dp, 64.dp).horizontalScroll(state)) {
                                val label: @androidx.compose.runtime.Composable () -> Unit = {
                                    Box(Modifier.size(60.dp, 12.dp)) {
                                        Box(Modifier.offset(24.dp).size(12.dp).background(Color.Red))
                                    }
                                }
                                if (suggestion.value) GlassSuggestionChip({ clicks++ }, label,
                                    Modifier.testTag("chip"), shape = shape, settings = config,
                                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color.Black))
                                else GlassFilterChip(false, { clicks++ }, label,
                                    Modifier.testTag("chip"), shape = shape, settings = config,
                                    colors = FilterChipDefaults.filterChipColors(containerColor = Color.Black))
                                Box(Modifier.size(500.dp, 64.dp))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun reset(glass: Boolean, keyword: Boolean) {
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { material.value = glass; suggestion.value = keyword; generation.value++ }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun capture() = compose.onNodeWithTag("scene").captureToImage()
    private fun redCenter(image: ImageBitmap): Float {
        val p = image.toPixelMap(); var weight = 0.0; var total = 0.0
        for (y in 0 until p.height) for (x in 0 until p.width) {
            val c = p[x, y]; val w = (c.red - maxOf(c.green, c.blue)).coerceAtLeast(0f)
            weight += w; total += (x + .5) * w
        }
        assertTrue("Content marker remains rendered", weight > 5)
        return (total / weight).toFloat()
    }
}
