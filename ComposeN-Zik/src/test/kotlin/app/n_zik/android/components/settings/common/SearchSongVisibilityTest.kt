package app.n_zik.android.components.settings.common

import android.app.Application
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.components.SongItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalFoundationApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SearchSongVisibilityTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun hiddenSongRetainsItsSpaceWithoutPlaybackMenuOrAccessibilityActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.preferences.edit().putBoolean(disableScrollingTextKey, true).commit()
        val focused = mutableStateOf(false)
        val query = mutableStateOf("")
        var plays = 0
        var menus = 0
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                val focus = rememberSettingsSearchFocus(focused.value, query.value.isBlank())
                Column {
                    Text("Saved query", Modifier.testTag("history"))
                    Box(Modifier.testTag("songSpace")) {
                        Column(Modifier.settingsSearchContentVisibility(focus.contentAlpha)) {
                            SongItem(
                                song = Song("local:1", "Library song", durationText = "1:00", thumbnailUrl = null),
                                isLiked = false,
                                inPlaylist = false,
                                showThumbnail = false,
                                onClick = { plays++ },
                                onLongClick = { menus++ },
                            )
                        }
                    }
                }
            }
        }
        val bounds = composeRule.onNodeWithTag("songSpace").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithText("Library song").performClick()
        composeRule.runOnIdle { assertEquals(1, plays); focused.value = true }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithText("Library song").assertDoesNotExist()
        composeRule.onNodeWithTag("history").assertIsDisplayed()
        assertEquals(bounds, composeRule.onNodeWithTag("songSpace").fetchSemanticsNode().boundsInRoot)
        composeRule.onRoot().performTouchInput {
            click(bounds.center)
            longClick(bounds.center)
        }
        composeRule.runOnIdle {
            assertEquals(1, plays)
            assertEquals(0, menus)
            query.value = "Library"
        }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithText("Library song").performClick()
        composeRule.onNodeWithText("Library song").performTouchInput { longClick() }
        composeRule.runOnIdle { assertEquals(2, plays); assertEquals(1, menus) }
    }
}
