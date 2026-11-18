package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.artist.ArtistFollowButton
import app.n_zik.android.components.navigation.header.HeaderIcon
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassCapturedControlForegroundTest {
    @get:Rule val compose = createComposeRule()
    private val whiteFilm = GlassSettings(source = GlassTintSource.Custom,
        customColor = Color.White.toArgb(), tintStrength = 1f, film = 1f,
        quality = GlassQuality.Performance, rim = 0f, blur = 0f)

    @Composable
    private fun Host(settings: GlassSettings, content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(0.dp)
        CompositionLocalProvider(
            LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
            LocalContentColor provides DefaultDarkColorPalette.text,
            LocalGlassPreviewSettings provides settings,
            LocalSurfaceBackdropColor provides Color.Black,
        ) { Box(Modifier.background(Color.White)) { content() } }
    }

    @Test fun `header back resolves captured neutral tint on white film and preserves native and chromatic ink`() {
        val settings = mutableStateOf(whiteFilm)
        val tint = mutableStateOf(DefaultDarkColorPalette.text)
        var clicks = 0
        compose.setContent {
            Host(settings.value) {
                HeaderIcon(R.drawable.chevron_back, tint = tint.value,
                    modifier = Modifier.testTag("back"), onClick = { clicks++ })
            }
        }
        fun assertGlyph(expected: Color) {
            val pixels = compose.onNodeWithTag("back").captureToImage().toPixelMap()
            var matches = 0
            for (y in pixels.height / 4 until pixels.height * 3 / 4)
                for (x in pixels.width / 4 until pixels.width * 3 / 4) {
                    val pixel = pixels[x, y]
                    if (kotlin.math.abs(pixel.red - expected.red) < .025f &&
                        kotlin.math.abs(pixel.green - expected.green) < .025f &&
                        kotlin.math.abs(pixel.blue - expected.blue) < .025f) matches++
                }
            assertTrue("Expected visible $expected back glyph", matches > 2)
        }
        assertGlyph(Color.Black)
        compose.onNodeWithTag("back").performClick()
        compose.runOnIdle { tint.value = Color.Blue }
        assertGlyph(Color.Blue)
        compose.runOnIdle {
            tint.value = DefaultDarkColorPalette.text
            settings.value = whiteFilm.copy(areaModes = mapOf(GlassArea.Header to GlassAreaMode.Disabled))
        }
        assertGlyph(DefaultDarkColorPalette.text)
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun `follow label resolves captured neutral ink using its own backdrop and preserves native ink and callbacks`() {
        val settings = mutableStateOf(whiteFilm)
        var clicks = 0
        val label = RuntimeEnvironment.getApplication().getString(R.string.artist_action_follow)
        compose.setContent {
            Host(settings.value) {
                ArtistFollowButton(null, { clicks++ }, Modifier.testTag("follow"), settings.value)
            }
        }
        fun assertInk(expected: Color) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                assertTrue(it(layouts))
            }
            assertEquals(expected, layouts.single().layoutInput.style.color)
        }
        assertInk(Color.Black)
        compose.onNodeWithText(label).performClick()
        compose.runOnIdle {
            settings.value = whiteFilm.copy(areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled))
        }
        assertInk(DefaultDarkColorPalette.text)
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
