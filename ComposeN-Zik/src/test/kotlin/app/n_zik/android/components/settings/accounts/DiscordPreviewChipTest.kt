package app.n_zik.android.components.settings.accounts

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
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
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassTintSource
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.LocalSurfaceBackdropColor
import app.n_zik.android.components.glass.SurfaceForegroundContent
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscordPreviewChipTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `real preview chips resolve own film and preserve chromatic and native labels inside glass card`() {
        val whiteFilm = GlassSettings(source = GlassTintSource.Custom,
            customColor = Color.White.toArgb(), tintStrength = 1f, film = 1f,
            quality = GlassQuality.Performance, rim = 0f, blur = 0f)
        val settings = mutableStateOf(whiteFilm)
        var clicks = 0
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
                LocalContentColor provides DefaultDarkColorPalette.text,
                LocalGlassPreviewSettings provides settings.value,
                LocalSurfaceBackdropColor provides Color.Black,
            ) {
                Box(Modifier.background(Color.White)) {
                    // A real outer material's ink must not recolor a native inner chip.
                    SurfaceForegroundContent(Color.White) {
                        Column {
                            DiscordPreviewChip("Selected mode", Color.Blue, Color.White, { clicks++ },
                                Modifier.testTag("selected"), settings.value)
                            DiscordPreviewChip("Preview link", Color.Blue.copy(alpha = .15f), Color.White, {},
                                settings = settings.value)
                            DiscordPreviewChip("Unselected mode", Color.Blue.copy(alpha = .15f), Color.Blue, {},
                                settings = settings.value)
                        }
                    }
                }
            }
        }
        fun assertInk(label: String, expected: Color) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                assertTrue(it(layouts))
            }
            assertEquals(expected, layouts.single().layoutInput.style.color)
        }
        assertInk("Selected mode", Color.Black)
        assertInk("Preview link", Color.Black)
        assertInk("Unselected mode", Color.Blue)
        val pixels = compose.onNodeWithTag("selected").captureToImage().toPixelMap()
        var darkPixels = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val pixel = pixels[x, y]
            if (pixel.red < .1f && pixel.green < .1f && pixel.blue < .1f) darkPixels++
        }
        assertTrue("Selected label must render visibly on its opaque white film", darkPixels > 3)
        compose.onNodeWithText("Selected mode").performClick()
        compose.runOnIdle {
            settings.value = whiteFilm.copy(areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled))
        }
        assertInk("Selected mode", Color.White)
        assertInk("Preview link", Color.White)
        assertInk("Unselected mode", Color.Blue)
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
