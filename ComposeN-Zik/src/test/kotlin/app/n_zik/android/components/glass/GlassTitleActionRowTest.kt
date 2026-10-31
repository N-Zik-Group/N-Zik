package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.themed.Title
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
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassTitleActionRowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `actual Title preserves a whole held capsule above its following opaque sibling`() {
        val enabled = mutableStateOf(true)
        var titleClicks = 0
        var actionClicks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = RoundedCornerShape(24.dp)
            val settings = GlassSettings(enabled = enabled.value, quality = GlassQuality.Performance)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            ), LocalGlassPreviewSettings provides settings) {
                GlassRippleProvider(settings) {
                    Column(Modifier.size(360.dp, 200.dp).background(Color.Black).testTag("scene")) {
                        Title("sza", Modifier.testTag("title"), verticalPadding = 4.dp,
                            onClick = { titleClicks++ }, trailingIcon = {
                                GlassActionGroup(settings, GlassArea.ActionButtons) {
                                    GlassActionGroupItem({ actionClicks++ }, Modifier.testTag("action"),
                                        nativeIndication = !enabled.value) {
                                        Box(Modifier.size(20.dp).background(Color.Blue))
                                    }
                                    GlassActionGroupItem({}, nativeIndication = !enabled.value) {
                                        Box(Modifier.size(20.dp).background(Color.Green))
                                    }
                                }
                            })
                        // The old Title clip and ordinary sibling order both cut the drag here.
                        Box(Modifier.fillMaxWidth().height(120.dp).background(Color.Black))
                    }
                }
            }
        }
        val scene = compose.onNodeWithTag("scene")
        val title = compose.onNodeWithTag("title")
        val action = compose.onNodeWithTag("action")
        val titleBounds = title.fetchSemanticsNode().boundsInRoot
        val actionBounds = action.fetchSemanticsNode().boundsInRoot
        fun blueBounds(): androidx.compose.ui.geometry.Rect {
            val pixels = scene.captureToImage().toPixelMap()
            val positions = buildList {
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    val pixel = pixels[x, y]
                    if (pixel.blue > .8f && pixel.red < .4f && pixel.green < .4f) add(x to y)
                }
            }
            assertTrue("Actual blue glyph must be visible", positions.isNotEmpty())
            return androidx.compose.ui.geometry.Rect(positions.minOf { it.first }.toFloat(), positions.minOf { it.second }.toFloat(),
                (positions.maxOf { it.first } + 1).toFloat(), (positions.maxOf { it.second } + 1).toFloat())
        }
        val resting = blueBounds()
        action.performTouchInput { down(center); moveTo(center + Offset(0f, 500f), 100) }
        compose.mainClock.advanceTimeBy(700)
        val held = blueBounds()
        assertTrue("The complete held glyph crosses the Title and opaque sibling boundary", held.top > resting.top && held.bottom > titleBounds.bottom + 4f)
        assertTrue("The lower half must remain visible, not just a sliver", held.height >= resting.height)
        assertEquals(titleBounds, title.fetchSemanticsNode().boundsInRoot)
        assertEquals(actionBounds, action.fetchSemanticsNode().boundsInRoot)
        action.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1800)
        assertEquals(resting, blueBounds())
        action.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1800)
        compose.runOnIdle { assertEquals(1, actionClicks); assertEquals(0, titleClicks) }
        // Blank title space still invokes only the original title callback.
        title.performTouchInput { click(Offset(20f, center.y)) }
        compose.mainClock.advanceTimeBy(1800)
        compose.runOnIdle { assertEquals(1, titleClicks) }
        compose.runOnIdle { enabled.value = false }
        val off = blueBounds()
        action.performTouchInput { down(center); moveTo(center + Offset(0f, 500f), 100) }
        compose.mainClock.advanceTimeBy(700)
        assertEquals("Native OFF drawing retains its original mask and geometry", off, blueBounds())
        action.performTouchInput { cancel() }
    }
}
