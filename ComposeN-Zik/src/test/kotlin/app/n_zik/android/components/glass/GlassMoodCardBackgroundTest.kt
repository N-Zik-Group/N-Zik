package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.backdrop.Backdrop
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
// API 32 exercises real blur/film/rims without Robolectric's unsupported lens AGSL.
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassMoodCardBackgroundTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `mood modes preserve original fill and stripe without sampling their page`() {
        var mode by mutableStateOf(GlassAreaMode.GlassOnly)
        var fill by mutableStateOf(Color.Blue)
        val page = Color.Black
        val forbiddenCapture = object : Backdrop {
            override val isCoordinatesDependent = false
            override fun DrawScope.drawBackdrop(
                density: Density,
                coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?,
            ) { error("Mood cards must not capture themselves through the page") }
        }
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = DefaultDarkColorPalette,
                    typography = typographyOf(Color.White, true, false, FontType.Rubik),
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
                LocalGlassBackdrop provides forbiddenCapture,
            ) {
                Box(Modifier.size(160.dp, 56.dp).background(page).testTag("tile")) {
                    Box(Modifier.size(160.dp, 56.dp).clip(shape).glassMoodCardBackground(
                        backgroundColor = page, cardColor = fill, shape = shape,
                        settings = GlassSettings(
                            enabled = true, film = .5f, tintStrength = 0f, blur = 0f,
                            vibrancy = 0f, areaModes = mapOf(GlassArea.MoodCards to mode),
                        ),
                    )) {
                        Box(Modifier.width(5.dp).fillMaxHeight().background(Color.Red))
                    }
                }
            }
        }
        fun verify(expectedFill: Color) {
            val pixels = compose.onNodeWithTag("tile").captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            assertEquals(expectedFill.red, center.red, .02f)
            assertEquals(expectedFill.green, center.green, .02f)
            assertEquals(expectedFill.blue, center.blue, .02f)
            assertEquals(Color.Red, pixels[with(compose.density) { 2.dp.roundToPx() }, pixels.height / 2])
            assertEquals(page, pixels[0, 0])
        }
        verify(fill.copy(alpha = .5f).compositeOver(page))
        compose.runOnIdle { fill = Color.Green }
        verify(fill.copy(alpha = .5f).compositeOver(page))
        compose.runOnIdle { mode = GlassAreaMode.GlassAndContour }
        verify(fill.copy(alpha = .5f).compositeOver(page))
        compose.runOnIdle { mode = GlassAreaMode.ContourOnly }
        verify(fill)
        compose.runOnIdle { mode = GlassAreaMode.Disabled }
        verify(fill)
    }
}
