package app.n_zik.android.components.player.lyrics

import android.app.Application
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import app.n_zik.lrclib.models.Track
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class LyricsTrackSearchMenuTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `editing keeps the same sheet and only explicit searches retry until reopened`() {
        val menuState = MenuState()
        val requests = mutableListOf<Pair<String, String>>()
        val context = RuntimeEnvironment.getApplication()
        val searchLabel = context.getString(R.string.search_title)
        val cancelLabel = context.getString(R.string.common_action_cancel)
        fun open() {
            menuState.display {
                LyricsTrackSelector(
                    mediaId = "song",
                    lyricsType = LyricsType.Unsynced,
                    initialTitle = "Original title",
                    initialArtistName = "Artist",
                    onSearch = { _, _ -> },
                    showProviderMessages = false,
                    searchTracks = { artist, title ->
                        requests += artist to title
                        listOf(Track(
                            id = 1,
                            name = title,
                            trackName = title,
                            artistName = artist,
                            albumName = "Album",
                            tDuration = JsonPrimitive(15),
                            instrumental = false,
                            plainLyrics = "Words",
                            syncedLyrics = null,
                        ))
                    },
                )
            }
        }
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
                ),
                LocalMenuState provides menuState,
            ) {
                if (menuState.isDisplayed) menuState.contentState.second()
            }
        }

        compose.runOnIdle { open() }
        compose.onNodeWithText("Artist - Original title").assertIsDisplayed()
        compose.onAllNodesWithText(cancelLabel).assertCountEquals(1)
        compose.onNode(hasSetTextAction() and hasText("Original title")).performTextReplacement("Revised title")
        compose.runOnIdle {
            assertEquals(listOf("Artist" to "Original title"), requests)
            assertFalse(menuState.hasPrevious)
        }
        compose.onNodeWithContentDescription(searchLabel).performClick()
        compose.onNodeWithText("Artist - Revised title").assertIsDisplayed()

        compose.runOnIdle { menuState.hide() }
        compose.runOnIdle { open() }
        compose.onNodeWithText("Artist - Original title").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                listOf("Artist" to "Original title", "Artist" to "Revised title", "Artist" to "Original title"),
                requests,
            )
        }
    }
}
