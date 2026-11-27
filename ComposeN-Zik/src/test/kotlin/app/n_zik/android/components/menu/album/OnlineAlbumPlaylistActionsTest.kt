package app.n_zik.android.components.menu.album

import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.models.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OnlineAlbumPlaylistActionsTest {
    @Test
    fun `settled partial tracks can be added with parental control applied`() {
        val clean = Song.makePlaceholder("clean")
        val explicit = Song.makePlaceholder("explicit").copy(title = EXPLICIT_PREFIX + "Explicit")

        val selected = onlineAlbumPickerSongs(
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

        assertEquals(listOf(cached), onlineAlbumPickerSongs(listOf(cached), listOf(partial), false))
    }
}
