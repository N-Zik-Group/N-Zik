package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GlassTintTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `neutral sources stay neutral and full strength preserves actual source colour`() {
        val base = Color(0xFF23232A)
        listOf(Color.Black, Color.White, Color.Gray).forEach { source ->
            val tint = blendGlassTint(base, source, .6f)
            assertEquals(tint.red, tint.green, .0001f)
            assertEquals(tint.red, tint.blue, .0001f)
            assertEquals(source, blendGlassTint(base, source, 1f))
        }
        val pastel = Color(0xFFB0C8D0)
        assertEquals(pastel, blendGlassTint(base, pastel, 1f))
        assertEquals(base, blendGlassTint(base, Color.Unspecified, 1f))
        assertEquals(base, blendGlassTint(base, Color.Red, 0f))
    }

    @Test fun `enum sources follow their own live theme artwork and system colours`() {
        val accent = mutableStateOf(Color(0xFF9EAFBC))
        val artwork = mutableStateOf(Color.White)
        val source = mutableStateOf(GlassTintSource.ThemeAccent)
        var observed = Color.Unspecified
        compose.setContent {
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = DefaultDarkColorPalette.copy(accent = accent.value),
                    typography = typographyOf(Color.White, true, false, FontType.Rubik),
                    thumbnailShape = RoundedCornerShape(12.dp),
                    uiRoundnessShape = RoundedCornerShape(12.dp),
                    artistThumbnailShape = RoundedCornerShape(12.dp),
                ),
                LocalGlassArtworkColor provides artwork.value,
            ) {
                observed = glassTint(GlassSettings(source = source.value))
            }
        }
        compose.runOnIdle {
            assertEquals(accent.value, observed)
            accent.value = Color(0xFF607080)
        }
        compose.runOnIdle {
            assertEquals(accent.value, observed)
            source.value = GlassTintSource.Artwork
        }
        compose.runOnIdle {
            assertEquals(Color.White, observed)
            artwork.value = Color.Black
        }
        compose.runOnIdle {
            assertEquals(Color.Black, observed)
            source.value = GlassTintSource.SystemAccent
        }
        compose.runOnIdle {
            val context = ApplicationProvider.getApplicationContext<Application>()
            assertEquals(Color(context.getColor(android.R.color.system_accent1_400)), observed)
        }
    }
}
