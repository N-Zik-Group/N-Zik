package app.n_zik.android.models

import app.n_zik.android.MODIFIED_PREFIX
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaylistTest {

    @Test
    fun `purely local playlist cannot be bookmarked`() {
        assertFalse(Playlist(name = "My mix").canBeBookmarked())
    }

    @Test
    fun `playlist linked to YouTube Music can be bookmarked`() {
        assertTrue(Playlist(name = "Imported", browseId = "VLPLabc123").canBeBookmarked())
        assertTrue(Playlist(name = "Imported", browseId = "PLabc123").canBeBookmarked())
    }

    @Test
    fun `locally modified playlist cannot be bookmarked`() {
        assertFalse(Playlist(name = "Edited", browseId = "${MODIFIED_PREFIX}PLabc123").canBeBookmarked())
    }

    @Test
    fun `stray youtube flag does not make a local playlist bookmarkable`() {
        // Older versions let users bookmark local playlists, which set this flag with no browseId
        assertFalse(Playlist(name = "My mix", isYoutubePlaylist = true).canBeBookmarked())
    }
}
