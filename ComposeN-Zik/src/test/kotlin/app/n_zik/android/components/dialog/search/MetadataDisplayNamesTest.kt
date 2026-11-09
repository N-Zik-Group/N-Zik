package app.n_zik.android.components.dialog.search

import android.app.Application
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.dialog.album.ChangeAlbumBrowseIdDialog
import app.n_zik.android.components.dialog.artist.ChangeArtistBrowseIdDialog
import app.n_zik.android.components.dialog.playlist.ChangePlaylistBrowseIdDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class MetadataDisplayNamesTest {
    @get:Rule val compose = createComposeRule()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, content = content)
        }
        compose.mainClock.advanceTimeBy(400)
    }

    @Test
    fun `failed match rows hide title and author markers while retaining source song`() {
        val song = Song("id", "modified:e:Title: live", "modified:Artist", "3:00", null)
        show { MatchResultsDialog(0, 1, failedSongs = listOf(song), onDismiss = {}) }

        compose.onNodeWithText("Title: live - Artist").assertIsDisplayed()
        compose.onNodeWithText("modified:", substring = true).assertDoesNotExist()
        assertEquals("modified:e:Title: live", song.title)
        assertEquals("modified:Artist", song.artistsText)
    }

    @Test
    fun `artist browse search prefill hides the internal name marker`() {
        val artist = Artist("artist-id", name = "modified:Artist: collective")
        show { ChangeArtistBrowseIdDialog { artist }.DialogBody() }
        compose.onNodeWithText("Artist: collective").assertIsDisplayed()
        compose.onNodeWithText("modified:", substring = true).assertDoesNotExist()
        assertEquals("modified:Artist: collective", artist.name)
    }

    @Test
    fun `album browse search prefill hides the internal title marker`() {
        val album = Album("album-id", title = "modified:Album: live")
        show { ChangeAlbumBrowseIdDialog { album }.DialogBody() }
        compose.onNodeWithText("Album: live").assertIsDisplayed()
        compose.onNodeWithText("modified:", substring = true).assertDoesNotExist()
        assertEquals("modified:Album: live", album.title)
    }

    @Test
    fun `playlist browse search prefill hides stacked internal markers`() {
        val playlist = Playlist(1, "pinned:modified:Playlist: live")
        show { ChangePlaylistBrowseIdDialog { playlist }.DialogBody() }
        compose.onNodeWithText("Playlist: live").assertIsDisplayed()
        compose.onNodeWithText("modified:", substring = true).assertDoesNotExist()
        assertEquals("pinned:modified:Playlist: live", playlist.name)
    }
}
