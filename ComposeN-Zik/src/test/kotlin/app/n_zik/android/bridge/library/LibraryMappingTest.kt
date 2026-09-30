package app.n_zik.android.bridge.library

import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.bridge.state.TrackSource
import app.n_zik.android.core.database.PlaylistSongRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryMappingTest {

    private fun song(id: String, likedAt: Long? = null, thumbnailUrl: String? = "https://i.ytimg.com/vi/x/hqdefault.jpg") =
        Song(
            id = id,
            title = "modified:e:Title $id",
            artistsText = "pinned:Artist",
            durationText = "3:32",
            thumbnailUrl = thumbnailUrl,
            likedAt = likedAt,
            totalPlayTimeMs = 5_000L,
        )

    @Test
    fun `disliked songs are hidden, liked and neutral ones are listed`() {
        val songs = listOf(song("neutral"), song("liked", likedAt = 1_000L), song("disliked", likedAt = -1L))

        val listed = LibraryMapping.librarySongs(songs, downloaded = emptySet())

        assertEquals(listOf("neutral", "liked"), listed.map { it.track.id })
        assertEquals(5_000L, listed[0].totalPlayTimeMs)
    }

    @Test
    fun `isLiked only for likedAt above zero`() {
        assertTrue(LibraryMapping.track(song("a", likedAt = 1L), emptySet()).isLiked)
        assertFalse(LibraryMapping.track(song("b", likedAt = null), emptySet()).isLiked)
        assertFalse(LibraryMapping.track(song("c", likedAt = 0L), emptySet()).isLiked)
        assertFalse(LibraryMapping.track(song("d", likedAt = -5L), emptySet()).isLiked)
    }

    @Test
    fun `track carries cleaned texts, duration, download state and artwork presence`() {
        val track = LibraryMapping.track(song("vid"), downloaded = setOf("vid"))

        assertEquals("Title vid", track.title)
        assertEquals("Artist", track.artists)
        assertEquals(212_000L, track.durationMs)
        assertTrue(track.isDownloaded)
        assertTrue(track.hasArtwork)
        assertEquals(TrackSource.ONLINE, track.source)

        val bare = LibraryMapping.track(song("local:3", thumbnailUrl = null).copy(artistsText = null), setOf("local:3"))
        assertNull(bare.artists)
        assertFalse(bare.hasArtwork)
        assertFalse(bare.isDownloaded)
        assertEquals(TrackSource.LOCAL, bare.source)
        assertFalse(LibraryMapping.track(song("x", thumbnailUrl = "modified:"), emptySet()).hasArtwork)
    }

    @Test
    fun `disliked albums and artists are hidden and names are cleaned`() {
        val albums = LibraryMapping.albums(
            listOf(
                Album(id = "a1", title = "modified:Album", authorsText = "pinned:Band", year = "2021", thumbnailUrl = "https://x/y"),
                Album(id = "a2", title = "Gone", dislikedAt = 1L),
                Album(id = "a3", title = null, thumbnailUrl = "ftp://nope"),
            ),
            songCounts = mapOf("a1" to 4),
        )
        assertEquals(listOf("a1", "a3"), albums.map { it.id })
        assertEquals("Album", albums[0].title)
        assertEquals("Band", albums[0].artists)
        assertEquals("2021", albums[0].year)
        assertEquals(4, albums[0].trackCount)
        assertTrue(albums[0].hasArtwork)
        assertEquals("", albums[1].title)
        assertNull(albums[1].artists)
        assertEquals(0, albums[1].trackCount)
        assertFalse(albums[1].hasArtwork)

        val artists = LibraryMapping.artists(
            listOf(Artist(id = "u1", name = "pinned:Singer", thumbnailUrl = "content://a/1"), Artist(id = "u2", name = "No", dislikedAt = 3L)),
            songCounts = mapOf("u1" to 2, "u2" to 9),
        )
        assertEquals(listOf("u1"), artists.map { it.id })
        assertEquals("Singer", artists[0].name)
        assertEquals(2, artists[0].trackCount)
        assertTrue(artists[0].hasArtwork)
    }

    @Test
    fun `every playlist is listed with its cleaned display name and first artwork track`() {
        val artwork = LibraryMapping.artworkTracks(
            listOf(PlaylistSongRef(1L, "s2"), PlaylistSongRef(1L, "s5"), PlaylistSongRef(3L, "s9"))
        )
        assertEquals(mapOf(1L to "s2", 3L to "s9"), artwork)

        val playlists = LibraryMapping.playlists(
            listOf(
                PlaylistPreview(Playlist(id = 1L, name = "pinned:Road"), songCount = 2),
                PlaylistPreview(Playlist(id = 2L, name = "rewind-yearly:2025"), songCount = 0),
            ),
            artwork,
        ) { name -> if (name.startsWith("rewind-")) "Rewind 2025" else name }

        assertEquals(listOf("1", "2"), playlists.map { it.id })
        assertEquals("Road", playlists[0].name)
        assertEquals(2, playlists[0].trackCount)
        assertEquals("s2", playlists[0].artworkTrackId)
        assertEquals("Rewind 2025", playlists[1].name)
        assertNull(playlists[1].artworkTrackId)
    }
}
