package app.n_zik.android.components.menu.playlist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OnlinePlaylistMenuTitleTest {
    @Test
    fun `valid saved title takes priority and invalid saved metadata cannot hide the source title`() {
        val fallback = "Unknown Playlist"

        assertEquals("Saved title", onlinePlaylistMenuTitle("  Saved title  ", "Remote title", false, fallback))
        assertEquals("Remote title", onlinePlaylistMenuTitle(" null ", "Remote title", false, fallback))
        assertEquals("Remote title", onlinePlaylistMenuTitle(null, "Remote title", true, fallback))
    }

    @Test
    fun `missing title stays empty during loading and uses the collection fallback afterwards`() {
        assertEquals("", onlinePlaylistMenuTitle(null, null, true, "Unknown Playlist"))
        assertEquals("Unknown Playlist", onlinePlaylistMenuTitle(" ", "NULL", false, "Unknown Playlist"))
        assertEquals("Unknown Podcast", onlinePlaylistMenuTitle(null, null, false, "Unknown Podcast"))
    }
}
