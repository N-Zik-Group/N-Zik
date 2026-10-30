package app.n_zik.android.components.ui

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.glass.glassEnabledKey
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
class LeadingIconBadgeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `Settings badge keeps themed accent and roundness inside either existing icon slot`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.preferences.edit().putBoolean(glassEnabledKey, false).commit()
        var slot by mutableStateOf(20.dp)
        var dark by mutableStateOf(true)
        val shape = RoundedCornerShape(8.dp)
        val accent = Color(0xFF0088CC)
        compose.setContent {
            val background = if (dark) Color.Black else Color.White
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = ColorPalette.fallback.copy(accent = accent),
                typography = Typography.fallback,
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                Box(Modifier.size(48.dp).background(background).testTag("host"),
                    contentAlignment = Alignment.Center) {
                    LeadingIconBadge(R.drawable.search, "Search",
                        Modifier.size(slot).testTag("slot"))
                }
            }
        }
        for (isDark in listOf(true, false)) {
            for (size in listOf(20.dp, 24.dp)) {
                compose.runOnIdle { dark = isDark; slot = size }
                compose.onNodeWithTag("slot").assertWidthIsEqualTo(size).assertHeightIsEqualTo(size)
                compose.onNodeWithContentDescription("Search").assertExists()
                val pixels = compose.onNodeWithTag("host").captureToImage().toPixelMap()
                val background = if (isDark) Color.Black else Color.White
                val badge = accent.copy(alpha = .1f).compositeOver(background)
                // The badge extends to 32dp around both the search and menu icon slots.
                val x = pixels.width / 2
                val top = pixels.height / 6
                assertEquals(badge.toArgb(), pixels[x, top + 2].toArgb())
                assertEquals(background.toArgb(), pixels[top + 1, top + 1].toArgb())
                assertTrue("Icon retains the Settings accent tint", (0 until pixels.width).any { px ->
                    (0 until pixels.height).any { py -> pixels[px, py].toArgb() == accent.toArgb() }
                })
            }
        }
    }
}
