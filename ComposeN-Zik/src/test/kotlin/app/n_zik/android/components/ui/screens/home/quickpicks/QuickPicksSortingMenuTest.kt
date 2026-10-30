package app.n_zik.android.components.ui.screens.home.quickpicks

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.PlayEventsType
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.preferences
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
class QuickPicksSortingMenuTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `actual sorting menu preserves native badge source color shape and three glyphs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.preferences.edit().putBoolean(glassEnabledKey, false).commit()
        val accent = Color(0xFF0088CC)
        val menu = MenuState()
        val shape = RoundedCornerShape(8.dp)
        compose.setContent {
            CompositionLocalProvider(
                LocalMenuState provides menu,
                LocalAppearance provides Appearance(
                    colorPalette = ColorPalette.fallback.copy(accent = accent, background0 = Color.Black, background1 = Color.Black),
                    typography = Typography.fallback,
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
            ) {
                Box(Modifier.background(Color.Black)) {
                    if (menu.isDisplayed) menu.contentState.second()
                    else QuickPicksHeader(PlayEventsType.MostPlayed, {}, {}, {})
                }
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.sorting_order)).performClick()
        for (label in listOf(R.string.by_most_played_song, R.string.by_last_played_song, R.string.by_casual_played_song)) {
            val pixels = compose.onNodeWithText(context.getString(label), useUnmergedTree = true).onParent().captureToImage().toPixelMap()
            // Entry height is 32dp support plus 10dp padding on either side.
            val badge = pixels.height * 32 / 52
            val top = (pixels.height - badge) / 2
            val nativeBackground = pixels[badge + 2, top]
            val fill = accent.copy(alpha = .1f).compositeOver(nativeBackground).toArgb()
            assertEquals("32dp source badge fill", fill, pixels[badge / 2, top + badge / 8].toArgb())
            assertEquals("Rounded native corner remains outside badge", nativeBackground.toArgb(), pixels[0, top].toArgb())
            assertTrue("18dp glyph keeps theme accent", (0 until badge).any { x ->
                (0 until badge).any { y -> pixels[x, top + y].toArgb() == accent.toArgb() }
            })
        }
    }
}

