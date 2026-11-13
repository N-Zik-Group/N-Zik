package app.n_zik.android.components.menu

import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EntityMenuSnapshotTest {
    @Test
    fun `retained collection actions read committed entity metadata and nullable resets`() {
        val album = EntityMenuSnapshot(Album(id = "album", title = "Old", thumbnailUrl = "old"), "owner", Album::id)
        val artist = EntityMenuSnapshot(Artist(id = "artist", name = "Old"), "owner", Artist::id)
        val playlist = EntityMenuSnapshot(Playlist(id = 7, name = "Old"), "owner", Playlist::id)
        val retainedAlbum = { album.value }
        val retainedArtist = { artist.value }
        val retainedPlaylist = { playlist.value }
        album.accept(album.value.copy(title = "New", thumbnailUrl = null), "owner")
        artist.accept(artist.value.copy(name = "New"), "owner")
        playlist.accept(playlist.value.copy(name = "New", isAutoSync = true), "owner")
        assertEquals("New", retainedAlbum().title)
        assertNull(retainedAlbum().thumbnailUrl)
        assertEquals("New", retainedArtist().name)
        assertEquals(Playlist(id = 7, name = "New", isAutoSync = true), retainedPlaylist())
        album.accept(Album(id = "other", title = "Wrong"), "owner")
        playlist.accept(Playlist(id = 7, name = "Wrong"), "other")
        artist.accept(null, "owner")
        assertEquals("New", retainedAlbum().title)
        assertEquals("New", retainedPlaylist().name)
        assertEquals("New", retainedArtist().name)
    }

    @Test
    fun `saved metadata overlay preserves complete provider order duplicates and missing tracks`() {
        val first = Song("first", "Old", durationText = "1:00", thumbnailUrl = null)
        val second = Song("second", "Other", durationText = "2:00", thumbnailUrl = null)
        val updated = first.copy(title = "New", artistsText = "Artist")
        val unrelated = second.copy(id = "unrelated")
        assertEquals(listOf(updated, second, updated), overlayMenuSongs(listOf(first, second, first), listOf(updated, unrelated)))
        assertNull(overlayMenuSongs(null, listOf(updated)))
        assertEquals(emptyList<Song>(), overlayMenuSongs(emptyList(), listOf(updated)))
    }
}
