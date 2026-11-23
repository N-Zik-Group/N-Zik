package app.n_zik.android.ui.screens.localplaylist

import org.junit.Assert.assertEquals
import org.junit.Test

import app.n_zik.android.models.Song

class LocalPlaylistSongProjectionTest {
    @Test
    fun `recommendations retain clamped positions and playlist rows win by id`() {
        val original = song("original", "Playlist title")
        val tail = song("tail", "Tail")
        val first = song("first", "First recommendation")
        val second = song("second", "Second recommendation")
        val last = song("last", "Last recommendation")

        val result = projectLocalPlaylistSongs(
            songs = listOf(original, original.copy(title = "Later duplicate"), tail),
            recommendations = linkedMapOf(
                original.copy(title = "Replacement") to 0,
                first to -4,
                second to 0,
                first.copy(title = "Duplicate recommendation") to 2,
                last to 100,
            ),
            query = "",
            onDeviceOnly = false,
            parentalControlEnabled = false,
        )

        assertEquals(listOf(second, first, original, tail, last), result)
    }

    @Test
    fun `on device ignores recommendations and keeps matching local rows`() {
        val local = song("local:1", "Local song")
        val remote = song("remote", "Local song remix")
        val suggestion = song("local:2", "Local suggestion")

        val result = projectLocalPlaylistSongs(
            songs = listOf(local, remote),
            recommendations = mapOf(suggestion to 0),
            query = "LOCAL",
            onDeviceOnly = true,
            parentalControlEnabled = false,
        )

        assertEquals(listOf(local), result)
    }

    @Test
    fun `parental filtering follows deduplication and search cleans both fields`() {
        val explicit = song("explicit", "e:Night")
        val artistMatch = song("artist", "Other", "modified:Night Crew")
        val titleMatch = song("title", "modified:Night sky")
        val uppercasePrefix = song("uppercase", "E:Night edit")

        val result = projectLocalPlaylistSongs(
            songs = listOf(explicit, explicit.copy(title = "Night"), artistMatch, titleMatch, uppercasePrefix),
            recommendations = emptyMap(),
            query = "nIgHt",
            onDeviceOnly = false,
            parentalControlEnabled = true,
        )

        assertEquals(listOf(artistMatch, titleMatch, uppercasePrefix), result)
        assertEquals(
            emptyList<Song>(),
            projectLocalPlaylistSongs(
                songs = listOf(artistMatch, titleMatch),
                recommendations = emptyMap(),
                query = "modified:",
                onDeviceOnly = false,
                parentalControlEnabled = false,
            ),
        )
    }

    private fun song(id: String, title: String, artist: String? = null): Song =
        Song.makePlaceholder(id).copy(title = title, artistsText = artist)
}
