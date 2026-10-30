package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.backdrop.Backdrop
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassHistoryStatisticsSurfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `inline accent surfaces retain fill and bounds and never sample themselves`() {
        val mode = mutableStateOf(GlassAreaMode.GlassOnly)
        val enabled = mutableStateOf(true)
        val accent = mutableStateOf(Color(0xFF279C74))
        val page = DefaultDarkColorPalette.background0
        val forbiddenCapture = object : Backdrop {
            override val isCoordinatesDependent = false
            override fun DrawScope.drawBackdrop(
                density: Density,
                coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?,
            ) { error("Inline History and Statistics surfaces must not sample their own page") }
        }
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
                LocalGlassBackdrop provides forbiddenCapture,
            ) {
                Box(Modifier.size(240.dp, 64.dp).background(page).testTag("surface")) {
                    Box(Modifier.size(240.dp, 64.dp).glassChipBackground(
                        color = accent.value, shape = shape,
                        settings = GlassSettings(
                            enabled = enabled.value, tintStrength = 0f, film = .5f,
                            blur = 0f, vibrancy = 0f,
                            areaModes = mapOf(GlassArea.ActionButtons to mode.value),
                        ),
                    ))
                }
            }
        }
        fun verify(expected: Color) {
            val node = compose.onNodeWithTag("surface")
            node.assertWidthIsEqualTo(240.dp).assertHeightIsEqualTo(64.dp)
            val pixels = node.captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            assertEquals(expected.red, center.red, .02f)
            assertEquals(expected.green, center.green, .02f)
            assertEquals(expected.blue, center.blue, .02f)
            assertEquals(page, pixels[0, 0])
        }
        verify(accent.value.copy(alpha = .5f).compositeOver(page))
        compose.runOnIdle { accent.value = Color(0xFF6E4CB8) }
        verify(accent.value.copy(alpha = .5f).compositeOver(page))
        compose.runOnIdle { mode.value = GlassAreaMode.GlassAndContour }
        verify(accent.value.copy(alpha = .5f).compositeOver(page))
        compose.runOnIdle { mode.value = GlassAreaMode.ContourOnly }
        verify(accent.value)
        compose.runOnIdle { mode.value = GlassAreaMode.Disabled }
        verify(accent.value)
        compose.runOnIdle { mode.value = GlassAreaMode.GlassOnly; enabled.value = false }
        verify(accent.value)
    }
}
