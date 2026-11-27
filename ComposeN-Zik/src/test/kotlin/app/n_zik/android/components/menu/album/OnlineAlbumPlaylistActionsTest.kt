package app.n_zik.android.components.menu.album

import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.components.menu.collectionMenuPickerSongs
import app.n_zik.android.components.menu.playlist.CollectionImportStatus
import app.n_zik.android.components.menu.playlist.canImportAlbumCollection
import app.n_zik.android.models.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnlineAlbumPlaylistActionsTest {
    @Test
    fun `only online albums can import the collection even if local tracks are ready`() {
        assertFalse(canImportAlbumCollection("LOCAL_ALBUM_unlinked", CollectionImportStatus.Ready))
        assertFalse(canImportAlbumCollection("LOCAL_ALBUM_DEVICE_group", CollectionImportStatus.Ready))
        assertTrue(canImportAlbumCollection("MPREb_source", CollectionImportStatus.Ready))
        assertTrue(canImportAlbumCollection("MPREb_source", CollectionImportStatus.Loading))
    }

    @Test
    fun `settled partial tracks can be added with parental control applied`() {
        val clean = Song.makePlaceholder("clean")
        val explicit = Song.makePlaceholder("explicit").copy(title = EXPLICIT_PREFIX + "Explicit")

        val selected = collectionMenuPickerSongs(
            completeOrStored = emptyList(),
            partial = listOf(clean, explicit),
            parentalControlEnabled = true,
        )

        assertEquals(listOf(clean), selected)
    }

    @Test
    fun `complete or cached album tracks take precedence over a partial fetch`() {
        val cached = Song.makePlaceholder("cached")
        val partial = Song.makePlaceholder("partial")

        assertEquals(listOf(cached), collectionMenuPickerSongs(listOf(cached), listOf(partial), false))
    }
}
