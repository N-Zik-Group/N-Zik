package app.n_zik.android.components.ui

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.onboarding.OnboardingActionCard
import app.n_zik.android.components.ui.screens.listentogether.SettingIcon
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
class SharedIconSupportTest {
    @get:Rule val compose = createComposeRule()

    private val accent = Color(0xFF0088CC)

    @Test
    fun `onboarding card retains its native icon support and glyph`() {
        content {
            OnboardingActionCard(R.drawable.search, "Title", "Description", Modifier.testTag("card"))
        }
        val pixels = compose.onNodeWithTag("card").captureToImage().toPixelMap()
        val fill = accent.copy(alpha = .1f).compositeOver(Color.Black).toArgb()
        assertTrue("Actual onboarding icon support retains its native fill",
            (0 until pixels.width).any { x ->
                (0 until pixels.height).any { y -> pixels[x, y].toArgb() == fill }
            })
        assertTrue("Actual onboarding glyph retains the accent tint",
            (0 until pixels.width).any { x ->
                (0 until pixels.height).any { y -> pixels[x, y].toArgb() == accent.toArgb() }
            })
    }

    @Test
    fun `listen together shared menu icon retains native size fill and glyph`() {
        content {
            Box(Modifier.background(Color.Black).testTag("badge")) {
                SettingIcon(R.drawable.search, accent)
            }
        }
        compose.onNodeWithTag("badge").assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(32.dp)
        val pixels = compose.onNodeWithTag("badge").captureToImage().toPixelMap()
        assertEquals(accent.copy(alpha = .1f).compositeOver(Color.Black).toArgb(),
            pixels[pixels.width / 2, pixels.height / 8].toArgb())
        assertTrue("Actual shared menu glyph retains the accent tint",
            (0 until pixels.width).any { x ->
                (0 until pixels.height).any { y -> pixels[x, y].toArgb() == accent.toArgb() }
            })
    }

    private fun content(block: @Composable () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.preferences.edit().putBoolean(glassEnabledKey, false).commit()
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = ColorPalette.fallback.copy(accent = accent, background1 = Color.Black),
                typography = Typography.fallback, thumbnailShape = shape,
                uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) { block() }
        }
    }
}
