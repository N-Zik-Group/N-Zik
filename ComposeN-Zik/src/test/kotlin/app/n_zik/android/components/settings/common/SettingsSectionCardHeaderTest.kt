package app.n_zik.android.components.settings.common

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassRippleProvider
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsSectionCardHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `native header preserves the full icon through expansion and bounded ripple`() = checkHeader(false)
    @Test fun `Glass header preserves the full icon through expansion and bounded ripple`() = checkHeader(true)

    private fun checkHeader(glass: Boolean) {
        val expanded = mutableStateOf(false)
        val settings = GlassSettings(enabled = glass, quality = GlassQuality.Performance)
        val summary = "Menus, sheets, dialogs and temporary messages."
        compose.setContent {
            val shape = RoundedCornerShape(28.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides settings,
            ) {
                GlassRippleProvider(settings) {
                    Column(Modifier.width(320.dp).background(DefaultDarkColorPalette.background0)) {
                        SettingsSectionCard(
                            title = "Windows and messages", icon = R.drawable.sort_grid,
                            description = summary, expanded = expanded.value,
                            headerModifier = Modifier.testTag("header"),
                            onHeaderClick = { expanded.value = !expanded.value },
                            content = { Box(Modifier.fillMaxWidth().height(120.dp)) },
                        )
                    }
                }
            }
        }
        val icon = compose.onNodeWithTag("settings.section.header.icon", useUnmergedTree = true)
        val header = compose.onNodeWithTag("header")
        val accent = DefaultDarkColorPalette.accent
        fun mask(): List<Boolean> {
            val pixels = icon.captureToImage().toPixelMap()
            return List(pixels.width * pixels.height) { index ->
                val pixel = pixels[index % pixels.width, index / pixels.width]
                abs(pixel.red - accent.red) < .035f && abs(pixel.green - accent.green) < .035f &&
                    abs(pixel.blue - accent.blue) < .035f && pixel.alpha > .95f
            }
        }
        val baseline = mask()
        assertTrue("The full four-cell glyph must be present", baseline.count { it } > 40)
        compose.onNodeWithText(summary, useUnmergedTree = true).assertIsDisplayed()
        val beforeRipple = header.captureToImage().toPixelMap()
        compose.mainClock.autoAdvance = false
        try {
            header.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(96)
            compose.waitForIdle()
            assertEquals("Ripple must not mask icon content", baseline, mask())
            val duringRipple = header.captureToImage().toPixelMap()
            // This corner is outside the rounded click skin; the visible card below
            // must stay unchanged while the same header still owns its click target.
            repeat(3) { y -> repeat(3) { dx ->
                val x = beforeRipple.width - 1 - dx
                assertEquals(beforeRipple[x, y], duringRipple[x, y])
            } }
            header.performTouchInput { up() }
            repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
            assertEquals("Opening must retain the complete icon", baseline, mask())
            compose.onNodeWithText(summary, useUnmergedTree = true).assertIsDisplayed()
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            assertEquals("Expanded header must retain the complete icon", baseline, mask())
            header.performClick()
            repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
            compose.mainClock.advanceTimeBy(64)
            compose.waitForIdle()
            assertEquals("Closing must retain the complete icon", baseline, mask())
            compose.onNodeWithText(summary, useUnmergedTree = true).assertIsDisplayed()
            compose.mainClock.advanceTimeBy(300)
            compose.waitForIdle()
            assertEquals("Collapsed header must retain the complete icon", baseline, mask())
            assertTrue(!expanded.value)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
