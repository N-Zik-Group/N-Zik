package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.ui.screens.profiles.ProfileCardSurface
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
class GlassProfileCardsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `profile surface preserves original native pixels clicks and dimensions and owns its backdrop`() {
        val settings = mutableStateOf(GlassSettings(enabled = false, tintStrength = 0f, film = .5f,
            blur = 0f, vibrancy = 0f, settingsCardRims = false))
        val shape = RoundedCornerShape(16.dp)
        val showOriginal = mutableStateOf(true)
        val palette = DefaultDarkColorPalette
        var clicks = 0
        val forbiddenCapture = object : Backdrop {
            override val isCoordinatesDependent = false
            override fun DrawScope.drawBackdrop(
                density: Density,
                coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?,
            ) { error("A profile card must not capture the page containing itself") }
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = palette, typography = Typography.fallback,
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
                LocalGlassBackdrop provides forbiddenCapture,
            ) {
                Box(Modifier.background(palette.background0)) {
                    if (!showOriginal.value) ProfileCardSurface(
                        modifier = Modifier.size(120.dp, 80.dp).testTag("profile"),
                        interactionModifier = Modifier.clip(shape).clickable { clicks++ },
                        settings = settings.value,
                    ) { Box(Modifier.size(24.dp).background(Color.Yellow)) }
                    // The original Profiles card, retained as the pixel reference.
                    else Card(
                        modifier = Modifier.size(120.dp, 80.dp).testTag("original")
                            .shadow(4.dp, shape, spotColor = palette.accent.copy(alpha = .2f))
                            .clip(shape).clickable {},
                        shape = shape,
                        colors = CardDefaults.cardColors(containerColor = palette.background1),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) { Box(Modifier.size(24.dp).background(Color.Yellow)) }
                }
            }
        }
        // Compare in one screen slot so native edge coverage has the same rounding.
        val original = compose.onNodeWithTag("original").captureToImage().toPixelMap()
        compose.runOnIdle { showOriginal.value = false }
        val profile = compose.onNodeWithTag("profile")
        profile.assertWidthIsEqualTo(120.dp).assertHeightIsEqualTo(80.dp)
        fun capture() = profile.captureToImage().toPixelMap()
        fun assertOriginalPixels() {
            val actual = capture()
            for (y in 0 until original.height) for (x in 0 until original.width) {
                assertEquals("pixel $x,$y", original[x, y], actual[x, y])
            }
        }
        assertOriginalPixels()
        profile.performClick()
        compose.runOnIdle {
            assertEquals(1, clicks)
            settings.value = settings.value.copy(enabled = true,
                areaModes = mapOf(GlassArea.SettingsCards to GlassAreaMode.Disabled))
        }
        assertOriginalPixels()
        listOf(GlassAreaMode.GlassOnly, GlassAreaMode.GlassAndContour).forEach { mode ->
            compose.runOnIdle {
                settings.value = settings.value.copy(areaModes = mapOf(GlassArea.SettingsCards to mode))
            }
            profile.assertWidthIsEqualTo(120.dp).assertHeightIsEqualTo(80.dp)
            val pixels = capture()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            val expected = palette.background1.copy(alpha = .5f).compositeOver(palette.background0)
            assertEquals(expected.red, center.red, .02f)
            assertEquals(expected.green, center.green, .02f)
            assertEquals(expected.blue, center.blue, .02f)
            assertEquals(Color.Yellow, pixels[pixels.width / 8, pixels.height / 8])
            profile.performClick()
        }
        compose.runOnIdle { assertEquals(3, clicks) }
    }
}
