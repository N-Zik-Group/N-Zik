package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.components.ui.InlineCircularLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassInlineCircularLoaderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `all modes keep native spinner dimensions and indeterminate semantics`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette,
                    typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape),
                LocalGlassPreviewSettings provides GlassSettings(
                    quality = GlassQuality.Performance,
                    areaModes = mapOf(GlassArea.LoadingIndicators to mode.value),
                ),
            ) {
                Row {
                    Box(Modifier.testTag("adapted").background(Color.Black)) {
                        InlineCircularLoader(Modifier.padding(8.dp).size(48.dp), color = Color.Red, strokeWidth = 3.dp)
                    }
                    Box(Modifier.testTag("native").background(Color.Black)) {
                        CircularProgressIndicator(Modifier.padding(8.dp).size(48.dp), color = Color.Red, strokeWidth = 3.dp)
                    }
                }
            }
        }

        fun spinnerBounds(tag: String) = compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)
                and hasAnyAncestor(hasTestTag(tag)),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot

        for (selected in GlassAreaMode.entries) {
            compose.runOnIdle { mode.value = selected }
            val native = spinnerBounds("native")
            val adapted = spinnerBounds("adapted")
            assertEquals(selected.name, native.size, adapted.size)
            assertEquals(selected.name, native.top, adapted.top, 0f)
        }

        compose.runOnIdle { mode.value = GlassAreaMode.ContourOnly }
        compose.waitForIdle()
        fun rimPixels(tag: String): Int {
            val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            return (0 until pixels.height).sumOf { y ->
                (0 until pixels.width).count { x -> pixels[x, y].green > .1f }
            }
        }
        assertTrue("The Glass contour adds light pixels around the red arc", rimPixels("adapted") > rimPixels("native"))
    }

    @Test
    @Config(sdk = [32])
    fun `glass-only spinner remains visible on a uniform backdrop with and without film`() {
        val film = mutableStateOf(0f)
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            val palette = DefaultDarkColorPalette
            CompositionLocalProvider(
                LocalAppearance provides Appearance(palette,
                    typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape),
                LocalGlassPreviewSettings provides GlassSettings(
                    quality = GlassQuality.Balanced,
                    film = film.value,
                    areaModes = mapOf(GlassArea.LoadingIndicators to GlassAreaMode.GlassOnly),
                ),
            ) {
                Box(Modifier.size(64.dp).background(palette.background0).testTag("uniform"),
                    contentAlignment = Alignment.Center) {
                    InlineCircularLoader(color = Color.Red)
                }
            }
        }
        fun brightestArcPixel(value: Float): Float {
            compose.runOnIdle { film.value = value }
            val pixels = compose.onNodeWithTag("uniform").captureToImage().toPixelMap()
            var brightest = 0f
            var visible = 0
            val threshold = pixels[0, 0].red + .15f
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val red = pixels[x, y].red
                brightest = maxOf(brightest, red)
                if (red > threshold) visible++
            }
            assertTrue("Expected a visible red arc at film=$value", visible > 10)
            return brightest
        }
        val nativeRed = brightestArcPixel(0f)
        val filmRed = brightestArcPixel(.6f)
        assertTrue("The Glass film must soften the opaque red glyph ($filmRed vs $nativeRed)",
            filmRed < nativeRed - .1f)
    }
}
