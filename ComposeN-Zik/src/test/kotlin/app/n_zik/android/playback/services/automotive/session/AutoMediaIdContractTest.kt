package app.n_zik.android.playback.services.automotive.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Issue #777 — pins the mediaId contract between the Android Auto browse detail
 * handlers (which emit selection ids) and [AutoSessionCallback]'s queue
 * resolution (which consumes them):
 *
 *   "{prefix}/{containerId}/{songId}"            detail-page songs
 *   "{prefix}/{containerId}/{section}/{songId}"  artist section songs
 *   "PLAYLIST_SHUFFLE/{playlistId}"              playlist shuffle items
 *
 * Before the fix the detail handlers emitted the bare prefix ("artist/{songId}"),
 * which the resolver indexed at segment 2 — an IndexOutOfBoundsException swallowed
 * by runCatching, yielding an empty queue and the "Unable to perform the selection"
 * error on the head unit. The consumer-side behavior (queue + start index) is
 * pinned separately by `AutoSessionCallbackSelectionTest`.
 */
class AutoMediaIdContractTest {

    // -- playlistShuffle builder + parser ------------------------------------

    @Test
    fun `playlistShuffle emits the PLAYLIST_SHUFFLE prefix with the playlist id`() {
        assertEquals("PLAYLIST_SHUFFLE/123", AutoMediaIdContract.playlistShuffle("123"))
        assertEquals("PLAYLIST_SHUFFLE/OLAK5uy_h8pQ", AutoMediaIdContract.playlistShuffle("OLAK5uy_h8pQ"))
    }

    @Test
    fun `parsePlaylistShuffle round-trips numeric and browse ids`() {
        assertEquals("123", AutoMediaIdContract.parsePlaylistShuffle(AutoMediaIdContract.playlistShuffle("123")))
        assertEquals(
            "OLAK5uy_h8pQ",
            AutoMediaIdContract.parsePlaylistShuffle(AutoMediaIdContract.playlistShuffle("OLAK5uy_h8pQ"))
        )
    }

    @Test
    fun `parsePlaylistShuffle rejects malformed ids`() {
        assertNull(AutoMediaIdContract.parsePlaylistShuffle(null))
        assertNull(AutoMediaIdContract.parsePlaylistShuffle(""))
        // The legacy 1-segment id used to throw IndexOutOfBoundsException in the resolver.
        assertNull(AutoMediaIdContract.parsePlaylistShuffle("PLAYLIST_SHUFFLE"))
        assertNull(AutoMediaIdContract.parsePlaylistShuffle("PLAYLIST_SHUFFLE/"))
        assertNull(AutoMediaIdContract.parsePlaylistShuffle("PLAYLIST_SHUFFLE/123/extra"))
        assertNull(AutoMediaIdContract.parsePlaylistShuffle("PLAYLIST/123"))
    }

    // -- detail-page song parsing ---------------------------------------------

    @Test
    fun `parseSongSelection parses a three-segment detail-page song id`() {
        assertEquals(
            AutoMediaIdContract.ParsedSongSelection(containerId = "UCFromExample", section = null, songId = "song-1"),
            AutoMediaIdContract.parseSongSelection("artist/UCFromExample/song-1", "artist")
        )
    }

    @Test
    fun `parseSongSelection parses a four-segment artist section song id`() {
        assertEquals(
            AutoMediaIdContract.ParsedSongSelection(containerId = "UCFromExample", section = "Top%20songs", songId = "song-1"),
            AutoMediaIdContract.parseSongSelection("artist/UCFromExample/Top%20songs/song-1", "artist")
        )
    }

    @Test
    fun `parseSongSelection parses album and playlist detail song ids`() {
        assertEquals(
            AutoMediaIdContract.ParsedSongSelection(containerId = "MRKxYz", section = null, songId = "track-3"),
            AutoMediaIdContract.parseSongSelection("album/MRKxYz/track-3", "album")
        )
        // Pseudo-playlist ids (favorites, etc.) parse like real ids.
        assertEquals(
            AutoMediaIdContract.ParsedSongSelection(containerId = "FAVORITES", section = null, songId = "song-9"),
            AutoMediaIdContract.parseSongSelection("playlist/FAVORITES/song-9", "playlist")
        )
    }

    @Test
    fun `parseSongSelection rejects the legacy two-segment shape that broke issue 777`() {
        // The pre-fix handlers emitted "artist/{songId}" — the resolver used to
        // index segment 2 out of a two-segment id and throw.
        assertNull(AutoMediaIdContract.parseSongSelection("artist/song-1", "artist"))
    }

    @Test
    fun `parseSongSelection rejects wrong prefix or wrong depth`() {
        assertNull(AutoMediaIdContract.parseSongSelection("album/UCFromExample/song-1", "artist"))
        assertNull(AutoMediaIdContract.parseSongSelection("artist/UCFromExample/song-1/extra/segment", "artist"))
    }

    @Test
    fun `parseSongSelection rejects empty segments`() {
        assertNull(AutoMediaIdContract.parseSongSelection("artist//song-1", "artist"))
        assertNull(AutoMediaIdContract.parseSongSelection("artist/UCFromExample/", "artist"))
        // An empty section segment must not be mistaken for a real section.
        assertNull(AutoMediaIdContract.parseSongSelection("artist/UCFromExample//song-1", "artist"))
    }
}
