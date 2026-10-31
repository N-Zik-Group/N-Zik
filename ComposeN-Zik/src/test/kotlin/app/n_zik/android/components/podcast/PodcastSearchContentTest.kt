package app.n_zik.android.components.podcast

import app.n_zik.android.components.menu.playlist.OnlinePlaylistContent
import app.n_zik.android.components.menu.playlist.loadOnlinePlaylistContent
import app.n_zik.android.components.menu.playlist.onlinePlaylistShareUrl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastSearchContentTest {
    private val podcastId = "MPSPPLXK2KwV2tv0jwie5sRfW9Mrdz3gyZp9Vr"

    // JUnit requires a synchronous entry point for the suspend content loader.
    @Test fun `podcast menu fetches exact browse ID without generic playlist request`() = runBlocking {
        val expected = OnlinePlaylistContent("Podcast", "Author", null, emptyList())
        val result = loadOnlinePlaylistContent(podcastId,
            podcastLoader = { id -> assertEquals(podcastId, id); expected },
            playlistLoader = { error("Generic playlist API must not receive a podcast browse ID") })
        assertEquals(expected, result)
        assertEquals("https://music.youtube.com/playlist?list=$podcastId", onlinePlaylistShareUrl(podcastId))
    }

    // JUnit requires a synchronous entry point for the suspend content loader.
    @Test fun `podcast failure never falls back to invented playlist ID`() = runBlocking {
        assertNull(loadOnlinePlaylistContent(podcastId,
            podcastLoader = { null }, playlistLoader = { error("No playlist fallback") }))
    }

    // JUnit requires a synchronous entry point for the suspend content loader.
    @Test fun `ordinary playlist keeps generic loader and share URL`() = runBlocking {
        val expected = OnlinePlaylistContent("Playlist", null, null, emptyList())
        assertEquals(expected, loadOnlinePlaylistContent("PL123",
            podcastLoader = { error("No podcast request") },
            playlistLoader = { id -> assertEquals("PL123", id); expected }))
        assertEquals("https://music.youtube.com/playlist?list=PL123", onlinePlaylistShareUrl("PL123"))
    }
}
