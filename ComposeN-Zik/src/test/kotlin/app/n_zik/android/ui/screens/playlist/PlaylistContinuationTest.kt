package app.n_zik.android.ui.screens.playlist

import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.requests.PlaylistContinuationPage
import app.n_zik.innertube.requests.PlaylistPage
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaylistContinuationTest {
    @Test
    fun `failed continuation keeps existing songs and resumes the same token`() = runTest {
        val initial = firstPage()
        val partial = loadPlaylistContinuations(initial, requestNextPage = { token ->
            when (token) {
                "second" -> Result.success(PlaylistContinuationPage(listOf(song("B")), "third"))
                "third" -> Result.failure(IOException("disconnected"))
                else -> error("Unexpected token: $token")
            }
        })

        assertEquals(listOf("A", "B"), partial.songs.map { it.key })
        assertEquals("third", partial.songsContinuation)
        assertEquals(listOf("A"), initial.songs.map { it.key })

        val complete = loadPlaylistContinuations(partial, requestNextPage = { token ->
            assertEquals("third", token)
            Result.success(PlaylistContinuationPage(listOf(song("B"), song("C")), null))
        })

        assertEquals(listOf("A", "B", "C"), complete.songs.map { it.key })
        assertNull(complete.songsContinuation)
    }

    @Test
    fun `repeated token stays incomplete without fetching forever`() = runTest {
        var requests = 0
        val partial = loadPlaylistContinuations(firstPage(), requestNextPage = { token ->
            requests++
            Result.success(PlaylistContinuationPage(listOf(song("B")), token))
        })

        assertEquals(1, requests)
        assertEquals(listOf("A", "B"), partial.songs.map { it.key })
        assertEquals("second", partial.songsContinuation)
    }

    private fun firstPage() = PlaylistPage(
        playlist = Innertube.PlaylistItem(info = null, channel = null, songCount = null, isEditable = null, thumbnail = null),
        description = null,
        songs = listOf(song("A")),
        songsContinuation = "second",
        continuation = null,
    )

    private fun song(id: String) = Innertube.SongItem(
        info = Innertube.Info(name = id, endpoint = NavigationEndpoint.Endpoint.Watch(videoId = id)),
        authors = null,
        album = null,
        durationText = null,
        thumbnail = null,
        explicit = false,
    )
}
