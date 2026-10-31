package app.n_zik.android.components.onboarding

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultLightColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.glassActionButtonsKey
import app.n_zik.android.components.glass.glassFilmKey
import app.n_zik.android.components.glass.glassRimKey
import app.n_zik.android.components.glass.glassBlurKey
import app.n_zik.android.components.glass.glassVibrancyKey
import app.n_zik.android.components.glass.glassEnabledKey
import app.it.fast4x.rimusic.utils.preferences
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingPrimaryButtonTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var previousMaster: Boolean? = null

    @Before fun saveMasterPreference() {
        previousMaster = if (context.preferences.contains(glassEnabledKey))
            context.preferences.getBoolean(glassEnabledKey, false) else null
    }

    @After fun restorePreferences() {
        context.preferences.edit().apply {
            previousMaster?.let { putBoolean(glassEnabledKey, it) } ?: remove(glassEnabledKey)
        }.commit()
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `light onboarding primary actions use the accent foreground and retain clicks`() {
        // Native fallback isolates the reported accent-label mismatch from optical rendering.
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.preferences.edit().putBoolean(glassEnabledKey, false).commit()
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE)
            .edit().clear().putBoolean(glassActionButtonsKey, false).commit()
        val palette = DefaultLightColorPalette
        var actualForeground = Color.Unspecified
        var clicks = 0
        var ripple: RippleConfiguration? = null
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = palette,
                typography = typographyOf(palette.text, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                OnboardingPrimaryButton(onClick = { clicks++ }) {
                    actualForeground = LocalContentColor.current
                    ripple = LocalRippleConfiguration.current
                    Text("Log in")
                }
            }
        }
        compose.onNodeWithText("Log in").performClick()
        assertEquals(palette.onAccent, actualForeground)
        assertNotNull("Native OFF retains Material ripple", ripple)
        assertTrue(contrast(actualForeground, palette.accent) >
            contrast(palette.textSecondary, palette.accent) * 2f)
        assertEquals(1, clicks)
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `Glass ON light primary label contrasts with the actual rendered translucent surface`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        val prefs = context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean(glassActionButtonsKey, true)
            .putFloat(glassFilmKey, .5f).putFloat(glassRimKey, 0f)
            .putFloat(glassBlurKey, 0f).putFloat(glassVibrancyKey, 0f).commit()
        val palette = DefaultLightColorPalette
        var foreground = Color.Unspecified
        var ripple: RippleConfiguration? = RippleConfiguration()
        var clicks = 0
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = palette,
                typography = typographyOf(palette.text, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                Box(Modifier.background(palette.background0)) {
                    OnboardingPrimaryButton(onClick = { clicks++ }, modifier = Modifier.width(180.dp).testTag("action")) {
                        foreground = LocalContentColor.current
                    ripple = LocalRippleConfiguration.current
                        Text("Log in")
                    }
                }
            }
        }
        val pixels = compose.onNodeWithTag("action").captureToImage().toPixelMap()
        // Interior left of the centered label: neither glyphs nor the optical edge.
        val background = pixels[pixels.width / 6, pixels.height / 2]
        assertTrue("actual Glass surface must retain a translucent accent", background != palette.accent)
        assertTrue("label contrast was ${contrast(foreground, background)}",
            contrast(foreground, background) >= 4.5f)
        assertNull("Glass replaces Material ripple through PageButton", ripple)
        compose.onNodeWithTag("action").performTouchInput { click() }
        assertEquals("One physical tap invokes one action", 1, clicks)
        prefs.edit().clear().commit()
    }

    private fun contrast(first: Color, second: Color): Float {
        val light = maxOf(first.luminance(), second.luminance())
        val dark = minOf(first.luminance(), second.luminance())
        return (light + .05f) / (dark + .05f)
    }
}
