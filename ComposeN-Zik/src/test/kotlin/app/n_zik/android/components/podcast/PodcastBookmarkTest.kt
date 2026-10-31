package app.n_zik.android.components.podcast

import app.it.fast4x.rimusic.models.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PodcastBookmarkTest {
    @Test fun `podcast and playlist browse IDs remain exact local snapshot identities`() {
        for (browseId in listOf("MPSPPL123", "VLPL123", "PL123")) {
            val saved = podcastBookmarkPlaylist(browseId, "Podcast title")
            assertEquals(browseId, saved.browseId)
            assertEquals("Podcast title", saved.name)
            assertFalse(saved.isEditable)
            assertFalse(saved.isYoutubePlaylist)
            assertEquals(PodcastBookmarkOperation.Remove, podcastBookmarkOperation(saved))
        }
        assertEquals(PodcastBookmarkOperation.Create, podcastBookmarkOperation(null))
    }

    @Test fun `existing editable imports and remote entries open the import workflow without deletion`() {
        val imported = Playlist(id = 7, name = "My edited import", browseId = "MPSPPL123")
        assertEquals(PodcastBookmarkOperation.ExistingImport, podcastBookmarkOperation(imported))
        assertEquals(PodcastBookmarkOperation.ExistingImport,
            podcastBookmarkOperation(imported.copy(isEditable = false, isYoutubePlaylist = true)))
        assertEquals(7L, imported.id)
        assertEquals("My edited import", imported.name)
    }
}
