package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlayerMeasuredForegroundTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `measured labels and glyphs share displayed progress and finish with readable ink`() {
        val progress = mutableStateOf(0f)
        val surface = mutableStateOf(Color.White)
        val shape = RoundedCornerShape(16.dp)
        val palette = DefaultDarkColorPalette.copy(text = Color.White, iconButtonPlayer = Color.White)
        val appearance = Appearance(palette, Typography.fallback, shape, shape, shape)
        var text = Color.Unspecified
        var glyph = Color.Unspecified
        var custom = Color.Unspecified
        compose.setContent {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalForegroundSourcePalette provides palette,
                LocalPlayerForegroundProgress provides progress.value,
            ) {
                SurfaceForegroundContent(surface.value, preferMeasuredText = true) {
                    val currentText = colorPalette().text
                    val currentGlyph = playerControlGlyphColor(currentText)
                    val customGlyph = playerControlGlyphColor(Color.Red)
                    SideEffect { text = currentText; glyph = currentGlyph; custom = customGlyph }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(Color.White, text)
            assertEquals(text, glyph)
            progress.value = .5f
        }
        compose.runOnIdle {
            assertTrue(text != Color.White && text != Color.Black)
            assertEquals(text, glyph)
            assertEquals(Color.Red, custom)
            // A new sample does not discard the current shared progress.
            surface.value = Color(0xFFF0F0F0)
        }
        compose.runOnIdle {
            assertTrue(text != Color.White && text != Color.Black)
            assertEquals(text, glyph)
            progress.value = 1f
        }
        compose.runOnIdle {
            assertEquals(Color.Black, text)
            assertTrue(contrastAgainst(text, surface.value) >= TextContrast)
            assertTrue(contrastAgainst(glyph, surface.value) >= IconContrast)
        }
    }

    @Test
    fun `rapid retarget begins at the measured ink already displayed`() {
        val frame = PlayerMeasuredInkFrame()
        frame.resolve(Color.White, Color.Black, TextContrast, 1f)
        frame.resolve(Color.White, Color.White, TextContrast, 0f)
        val middle = frame.resolve(Color.White, Color.White, TextContrast, .5f)
        val restarted = frame.resolve(Color.White, Color.Black, TextContrast, 0f)
        assertEquals(middle, restarted)
        assertEquals(Color.White, frame.resolve(Color.White, Color.Black, TextContrast, 1f))
    }

    @Test
    fun `measured endpoint retains native behavior outside a palette fade`() {
        assertEquals(Color.White, measuredPlayerForeground(Color.Black, Color.Black, TextContrast))
        assertEquals(Color.Black, PlayerMeasuredInkFrame().resolve(Color.White, Color.White, TextContrast, 1f))
        assertEquals(Color.Red, PlayerMeasuredInkFrame().resolve(Color.Red, Color.White, TextContrast, .5f))
        assertEquals(Color.Transparent,
            PlayerMeasuredInkFrame().resolve(Color.Transparent, Color.White, TextContrast, .5f))
    }
}
