package app.n_zik.android.ui.screens.localplaylist

import app.n_zik.android.models.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalPlaylistActionSongsTest {
    @Test
    fun localFilterScopesBulkActionsAndDoesNotReplaceHiddenSelections() {
        val remote = Song.makePlaceholder("remote")
        val local = Song.makePlaceholder("local:1")
        val hiddenLocal = Song.makePlaceholder("local:2")
        val visible = listOf(remote, local)

        assertEquals(listOf(local), localPlaylistActionSongs(visible, emptyList(), true))
        assertEquals(listOf(local), localPlaylistActionSongs(visible, listOf(remote, local), true))
        assertEquals(emptyList<Song>(), localPlaylistActionSongs(visible, listOf(remote, hiddenLocal), true))
        assertEquals(listOf(remote), localPlaylistActionSongs(visible, listOf(remote), false))
    }
}
