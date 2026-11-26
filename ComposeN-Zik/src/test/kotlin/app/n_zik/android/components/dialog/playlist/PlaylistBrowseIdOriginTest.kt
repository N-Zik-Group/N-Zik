package app.n_zik.android.components.dialog.playlist

import app.n_zik.android.models.Playlist
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaylistBrowseIdOriginTest {
    @Test
    fun `changing and unlinking retain the original playlist ID`() {
        val previous = mapOf("LOCAL_Playlist_uuid" to "modified:VLPLnew", "modified:VLPLnew" to "VLPLoriginal")

        assertEquals(PlaylistBrowseIdOrigin("VLPLoriginal"), originalPlaylistBrowseId("LOCAL_Playlist_uuid", previous::get))
    }

    @Test
    fun `a previously local playlist can restore its absent link`() {
        assertEquals(PlaylistBrowseIdOrigin(null), originalPlaylistBrowseId("modified:VLPLnew") { "" })
    }

    @Test
    fun `unrecorded and circular histories provide no original ID`() {
        assertNull(originalPlaylistBrowseId("LOCAL_Playlist_unknown") { null })
        assertNull(originalPlaylistBrowseId("modified:VLPLloop") { it })
    }

    @Test
    fun `only a real playlist ID or an unlinked YouTube origin can be edited`() {
        val item = Playlist(name = "Collection")
        val origin = PlaylistBrowseIdOrigin("VLPLoriginal")
        val unlinked = item.copy(browseId = "LOCAL_Playlist_uuid")

        assertTrue(canChangePlaylistBrowseId(item.copy(browseId = "VLPLlinked")))
        assertTrue(canChangePlaylistBrowseId(unlinked) { origin })
        assertFalse(canChangePlaylistBrowseId(unlinked) { PlaylistBrowseIdOrigin(null) })
        assertFalse(canChangePlaylistBrowseId(unlinked) { PlaylistBrowseIdOrigin("MPSPpodcast") })
        assertFalse(canChangePlaylistBrowseId(unlinked) { null })
        assertFalse(canChangePlaylistBrowseId(item) { origin })
    }

    @Test
    fun `a recorded origin takes precedence over the legacy chain`() {
        val unlinked = "LOCAL_Playlist_uuid"
        assertEquals(PlaylistBrowseIdOrigin("VLPLoriginal"), recordedPlaylistBrowseIdOrigin(unlinked, "VLPLoriginal") { error("Unexpected key: $it") })
        assertEquals(PlaylistBrowseIdOrigin(null), recordedPlaylistBrowseIdOrigin(unlinked, "") { error("Unexpected key: $it") })
        assertEquals(PlaylistBrowseIdOrigin("VLPLlegacy"), recordedPlaylistBrowseIdOrigin(unlinked, null) { "VLPLlegacy" })
    }
}
