package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassNativeSurfaceTintTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `native fill retains coverage and none retains its original colour`() {
        val base = Color(0x80391D47)
        val settings = mutableStateOf(GlassSettings(source = GlassTintSource.None))
        var observed = Color.Unspecified
        var transparent = Color.Unspecified
        compose.setContent {
            observed = glassNativeSurfaceTint(settings.value, base)
            transparent = glassNativeSurfaceTint(settings.value, Color.Transparent)
        }
        compose.runOnIdle {
            assertEquals(base, observed)
            settings.value = settings.value.copy(source = GlassTintSource.Custom,
                customColor = 0xFFFF0000.toInt(), tintStrength = .6f)
        }
        compose.runOnIdle {
            assertEquals(lerp(base, Color.Red, .6f).copy(alpha = base.alpha), observed)
            assertEquals(Color.Transparent, transparent)
            settings.value = settings.value.copy(tintStrength = 0f)
        }
        compose.runOnIdle {
            assertEquals(base, observed)
            settings.value = settings.value.copy(tintStrength = 1f)
        }
        compose.runOnIdle { assertEquals(Color.Red.copy(alpha = base.alpha), observed) }
    }

    @Test fun `explicit native films receive global tint and default film is tinted once`() {
        val base = Color(0xFF391D47)
        val settings = GlassSettings(enabled = true, source = GlassTintSource.Custom,
            customColor = 0xFFFF0000.toInt(), tintStrength = .6f, film = 1f, rim = 0f)
        var defaultFilm = Color.Unspecified
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                defaultFilm = glassSurfaceTint(settings)
                Row(Modifier.background(Color.Black)) {
                    Box(Modifier.size(64.dp).testTag("native").glassMaterial(
                        settings, shape, filmColor = base,
                    ))
                    Box(Modifier.size(64.dp).testTag("default").glassMaterial(settings, shape))
                }
            }
        }
        fun assertFilm(tag: String, expected: Color) {
            val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            val actual = pixels[pixels.width / 2, pixels.height / 2]
            assertEquals(expected.red, actual.red, .01f)
            assertEquals(expected.green, actual.green, .01f)
            assertEquals(expected.blue, actual.blue, .01f)
        }
        assertFilm("native", lerp(base, Color.Red, .6f))
        assertFilm("default", defaultFilm)
    }
}
