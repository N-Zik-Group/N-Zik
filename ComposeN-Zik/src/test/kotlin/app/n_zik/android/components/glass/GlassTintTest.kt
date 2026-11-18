package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.FontType
import app.n_zik.android.components.player.ArtworkDisplayColors
import app.n_zik.android.components.player.ArtworkDisplayResult
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultLightColorPalette
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GlassTintTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `glass theme handoff waits for artwork then distinguishes a missing cover from a disabled source`() {
        val current = ArtworkDisplayColors(null, null, Color.Red, null, null, true, true)
        assertNull(resolvedGlassArtworkTint(ArtworkDisplayResult(null, resolved = false), enabled = true))
        assertNull(resolvedGlassArtworkTint(ArtworkDisplayResult(current, resolved = false), enabled = true))
        assertEquals(GlassArtworkTint(Color.Red, 1f),
            resolvedGlassArtworkTint(ArtworkDisplayResult(current, resolved = true), enabled = true))
        assertEquals(GlassArtworkTint(), resolvedGlassArtworkTint(ArtworkDisplayResult(null, resolved = true), enabled = true))
        assertEquals(GlassArtworkTint(), resolvedGlassArtworkTint(ArtworkDisplayResult(current, resolved = false), enabled = false))
    }

    @Test fun `neutral glass ink advances through a light dark fade without an intermediate polarity flash`() {
        val lightSurface = Color(.97f, .97f, .97f)
        val darkSurface = Color(.13f, .13f, .13f)
        val lightInk = Color(.88f, .88f, .88f)
        val frames = (0..120).map { index ->
            val darkness = index / 120f
            val surface = lerp(lightSurface, darkSurface, darkness)
            glassPaneForeground(lerp(darkSurface, lightInk, darkness), surface, TextContrast, darkness)
        }
        assertTrue(contrastAgainst(frames.first(), lightSurface) >= TextContrast)
        assertTrue(contrastAgainst(frames.last(), darkSurface) >= TextContrast)
        assertTrue(frames.zipWithNext().all { (a, b) -> b.red >= a.red && b.red - a.red < .035f })
    }

    @Test fun `artwork contribution fades coherently into the live theme fallback`() {
        val from = GlassArtworkTint(Color.White, darkness = 1f)
        val fallback = GlassArtworkTint()
        val halfway = from.lerpTo(fallback, .5f)
        assertEquals(.5f, halfway.color!!.alpha, .01f)
        assertEquals(.5f, halfway.darkness, 0f)
        assertEquals(.5f, halfway.luminance, .01f)
        assertEquals(halfway, halfway.lerpTo(GlassArtworkTint(Color.Red), 0f))
        assertEquals(fallback, from.lerpTo(fallback, 1f))
    }

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

    @Test fun `a gray artwork tint gradually restores the colored glass base`() {
        val base = Color(0xFF23232A)
        val neutral = blendGlassTint(base, Color(0xFF808080), .6f)
        val nearlyNeutral = blendGlassTint(base, Color(0xFF818080), .6f)
        assertTrue(kotlin.math.abs(nearlyNeutral.blue - neutral.blue) < .008f)
        assertTrue(kotlin.math.abs(nearlyNeutral.red - neutral.red) < .008f)
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
    @Test fun `dark artwork policy reaches native glass roles in light theme without changing custom sources`() {
        val source = mutableStateOf(GlassTintSource.Artwork)
        val artworkDarkness = mutableStateOf(1f)
        val effectiveArtwork = Color(0xFF242424)
        var surface = Color.Unspecified
        var native = Color.Unspecified
        var darkness = 0f
        compose.setContent {
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = DefaultLightColorPalette,
                    typography = typographyOf(Color.Black, true, false, FontType.Rubik),
                    thumbnailShape = RoundedCornerShape(12.dp),
                    uiRoundnessShape = RoundedCornerShape(12.dp),
                    artistThumbnailShape = RoundedCornerShape(12.dp),
                ),
                LocalGlassArtworkColor provides effectiveArtwork,
                LocalGlassMenuHost provides androidx.compose.runtime.remember { GlassMenuHostState() }.also {
                    it.artworkTint = GlassArtworkTint(effectiveArtwork, darkness = artworkDarkness.value)
                },
            ) {
                val settings = GlassSettings(source = source.value, customColor = Color.Red.toArgb(), tintStrength = .5f)
                surface = glassSurfaceTint(settings)
                native = glassNativeSurfaceTint(settings, Color.White.copy(alpha = .7f))
                darkness = glassSurfaceDarkness(settings)
            }
        }
        var darkSurface = Color.Unspecified
        var darkNative = Color.Unspecified
        compose.runOnIdle {
            assertEquals(1f, darkness, 0f)
            org.junit.Assert.assertTrue(surface.red < .5f)
            org.junit.Assert.assertTrue(native.red < .5f)
            assertEquals(.7f, native.alpha, .01f)
            darkSurface = surface
            darkNative = native
            artworkDarkness.value = .5f
        }
        compose.runOnIdle {
            assertEquals(.5f, darkness, 0f)
            org.junit.Assert.assertTrue(surface.red > darkSurface.red)
            org.junit.Assert.assertTrue(native.red > darkNative.red)
            assertEquals(.7f, native.alpha, .01f)
            source.value = GlassTintSource.Custom
        }
        compose.runOnIdle {
            assertEquals(0f, darkness, 0f)
            artworkDarkness.value = 0f
        }
        compose.runOnIdle {
            assertEquals(blendGlassTint(Color.White.copy(alpha = .7f), Color.Red, .5f).copy(alpha = .7f), native)
        }
    }

}
