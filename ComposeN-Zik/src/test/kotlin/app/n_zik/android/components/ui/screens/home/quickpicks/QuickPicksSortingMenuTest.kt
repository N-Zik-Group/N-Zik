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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.PlayEventsType
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.R
import app.n_zik.android.components.glass.glassEnabledKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        context.preferences.edit().putBoolean(glassEnabledKey, false)
            .putString(menuStyleKey, MenuStyle.List.name).commit()
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
        compose.onNodeWithContentDescription(context.getString(R.string.library_sort_order_title)).performClick()
        compose.onNodeWithText(context.getString(R.string.home_sort_most_played_song_label)).assertIsSelected()
        for (label in listOf(R.string.home_sort_most_played_song_label, R.string.home_sort_last_played_song_label, R.string.home_sort_random_label)) {
            val pixels = compose.onNodeWithText(context.getString(label), useUnmergedTree = true).onParent().captureToImage().toPixelMap()
            // Entry height is 32dp support plus 10dp padding on either side.
            val badge = pixels.height * 32 / 52
            val top = (pixels.height - badge) / 2
            val badgeLeft = pixels.height * 12 / 52
            val nativeBackground = pixels[pixels.width * 2 / 3, top]
            val badgeAlpha = if (label == R.string.home_sort_most_played_song_label) .2f else .1f
            val fill = accent.copy(alpha = badgeAlpha).compositeOver(nativeBackground).toArgb()
            val actualFill = pixels[badgeLeft + badge / 2, top + badge / 8].toArgb()
            assertTrue("32dp source badge fill", (0..24 step 8).all { shift ->
                kotlin.math.abs(((fill ushr shift) and 255) - ((actualFill ushr shift) and 255)) <= 1
            })
            assertEquals("Rounded native corner remains outside badge", nativeBackground.toArgb(), pixels[badgeLeft, top].toArgb())
            assertTrue("18dp glyph keeps theme accent", (badgeLeft until badgeLeft + badge).any { x ->
                (0 until badge).any { y -> pixels[x, top + y].toArgb() == accent.toArgb() }
            })
        }
    }

    @Test
    fun `grid sorting shows the active choice and applies the new choice`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.preferences.edit().putBoolean(glassEnabledKey, false)
            .putString(menuStyleKey, MenuStyle.Grid.name).commit()
        val menu = MenuState()
        val shape = RoundedCornerShape(8.dp)
        var selection = PlayEventsType.LastPlayed
        compose.setContent {
            CompositionLocalProvider(
                LocalMenuState provides menu,
                LocalAppearance provides Appearance(
                    colorPalette = ColorPalette.fallback,
                    typography = Typography.fallback,
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
            ) {
                if (menu.isDisplayed) menu.contentState.second()
                else QuickPicksHeader(selection, { selection = it }, {}, {})
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.library_sort_order_title)).performClick()
        val mostPlayed = compose.onNodeWithText(context.getString(R.string.home_sort_most_played_song_label))
        val lastPlayed = compose.onNodeWithText(context.getString(R.string.home_sort_last_played_song_label))
        lastPlayed.assertIsSelected()
        mostPlayed.assertIsNotSelected()
        assertTrue(lastPlayed.fetchSemanticsNode().boundsInRoot.left > mostPlayed.fetchSemanticsNode().boundsInRoot.left)
        mostPlayed.performClick()

        compose.runOnIdle {
            assertEquals(PlayEventsType.MostPlayed, selection)
            assertFalse(menu.isDisplayed)
        }
    }
}

