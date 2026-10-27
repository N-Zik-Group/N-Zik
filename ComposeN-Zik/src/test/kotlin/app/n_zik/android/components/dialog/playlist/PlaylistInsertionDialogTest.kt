package app.n_zik.android.components.dialog.playlist

import android.app.Application
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.R
import app.n_zik.android.utils.playlist.PlaylistInsertionPosition
import app.n_zik.android.utils.playlist.PlaylistInsertionPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class PlaylistInsertionDialogTest {
    @get:Rule val composeRule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun settleComposition() {
        composeRule.runOnUiThread { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
    }

    @After fun cleanup() {
        setActiveProfile("default", context)
    }

    @Test fun `actual dialog saves selection and restores it on reopen without changing display sorting`() {
        setActiveProfile("insertion-dialog-test", context)
        context.preferences.edit().clear()
            .putString("PlaylistSongsSortBy_901", "Title").commit()
        val playlist = Playlist(id = 901, name = "Local")
        val preferences = PlaylistInsertionPreferences(context)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                val dialog = PlaylistInsertionDialog { playlist }
                Button(onClick = dialog::onShortClick) { Text("Open insertion") }
                dialog.Render()
            }
        }
        composeRule.onNodeWithText("Open insertion").performClick()
        settleComposition()
        val beginning = context.getString(R.string.playlist_insertion_beginning)
        val end = context.getString(R.string.playlist_insertion_end)
        composeRule.onNode(isSelected() and hasAnySibling(hasText(end)), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(beginning).performClick()
        settleComposition()
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
        assertEquals(PlaylistInsertionPosition.Beginning, preferences.get(playlist.id))
        assertEquals(PlaylistInsertionPosition.End, preferences.get(902))
        assertEquals("Title", context.preferences.getString("PlaylistSongsSortBy_901", null))
        composeRule.onNodeWithText("Open insertion").performClick()
        settleComposition()
        composeRule.onNode(isSelected() and hasAnySibling(hasText(beginning)), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(end).performClick()
        settleComposition()
        assertEquals(PlaylistInsertionPosition.End, preferences.get(playlist.id))
        composeRule.onNodeWithText("Open insertion").performClick()
        settleComposition()
        composeRule.onNode(isSelected() and hasAnySibling(hasText(end)), useUnmergedTree = true).assertExists()
    }
}
