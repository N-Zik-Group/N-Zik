package app.n_zik.android.components.ui

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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.glassChipBackground
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
class IconBadgeBackgroundTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `icon containers keep native pixels and render exactly one shared Glass fill and rim`() {
        val wrapped = mutableStateOf(false)
        val enabled = mutableStateOf(false)
        val dark = mutableStateOf(true)
        val forbiddenPage = object : Backdrop {
            override val isCoordinatesDependent = false
            override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?) { error("Icon sampled its own page") }
        }
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            val palette = ColorPalette.fallback.copy(
                background0 = if (dark.value) Color.Black else Color.White)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = palette, typography = Typography.fallback,
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape),
                LocalGlassBackdrop provides forbiddenPage) {
                val fill = Color.Cyan.copy(alpha = .1f)
                val settings = GlassSettings(enabled = enabled.value, blur = 0f, vibrancy = 0f)
                Box(Modifier.size(32.dp).testTag("badge").then(
                    if (wrapped.value) Modifier.iconBadgeBackground(fill, shape, settings)
                    else if (enabled.value) Modifier.glassChipBackground(fill, shape, settings)
                    else Modifier.background(fill, shape)))
            }
        }
        for (isDark in listOf(true, false)) {
            for (glass in listOf(false, true)) {
                compose.runOnIdle { dark.value = isDark; enabled.value = glass; wrapped.value = false }
                val expected = compose.onNodeWithTag("badge").captureToImage().toPixelMap()
                compose.runOnIdle { wrapped.value = true }
                val actual = compose.onNodeWithTag("badge").captureToImage().toPixelMap()
                assertEquals(expected.width, actual.width)
                assertEquals(expected.height, actual.height)
                for (y in 0 until actual.height) for (x in 0 until actual.width) {
                    assertEquals("glass=$glass dark=$isDark at $x,$y", expected[x, y], actual[x, y])
                }
                if (glass) assertTrue("Glass includes a visible rim", (0 until actual.width).any { x ->
                    actual[x, 1] != actual[actual.width / 2, actual.height / 2]
                })
            }
        }
    }
}
