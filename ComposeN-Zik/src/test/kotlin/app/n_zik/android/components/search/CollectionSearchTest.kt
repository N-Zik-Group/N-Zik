package app.n_zik.android.components.search

import app.n_zik.android.models.Song
import app.n_zik.innertube.Innertube
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionSearchTest {
    @Test
    fun `matches displayed title and artist with case insensitive input`() {
        val song = Song.makePlaceholder("collection-search").copy(title = "Stereo Love", artistsText = "Edward Maya")

        assertTrue(song.matchesCollectionSearch("  stereo  "))
        assertTrue(song.matchesCollectionSearch("MAYA"))
        assertTrue(song.matchesCollectionSearch(" "))
        assertFalse(song.matchesCollectionSearch("unrelated"))
    }

    @Test
    fun matchesDisplayedCardMetadata() {
        val album = Innertube.AlbumItem(
            info = Innertube.Info("El Viaje", null),
            authors = listOf(Innertube.Info("Luis Fonsi", null)),
            year = "2024",
            thumbnail = null,
        )
        val playlist = Innertube.PlaylistItem(
            info = Innertube.Info("Discoveries", null),
            channel = Innertube.Info("Ambient Zone", null),
            songCount = 12,
            isEditable = false,
            thumbnail = null,
        )
        assertTrue(album.matchesCollectionSearch("  fonsi  "))
        assertTrue(album.matchesCollectionSearch("2024"))
        assertTrue(playlist.matchesCollectionSearch("ambient"))
        assertFalse(playlist.matchesCollectionSearch("fonsi"))
    }
}
