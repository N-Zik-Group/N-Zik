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

    @Test fun `editable imports and ordinary remote entries keep their workflow`() {
        val imported = Playlist(id = 7, name = "My edited import", browseId = "MPSPPL123")
        assertEquals(PodcastBookmarkOperation.ExistingImport, podcastBookmarkOperation(imported))
        assertEquals(PodcastBookmarkOperation.ExistingImport,
            podcastBookmarkOperation(imported.copy(browseId = "PL123", isEditable = false, isYoutubePlaylist = true)))
        assertEquals(7L, imported.id)
        assertEquals("My edited import", imported.name)
    }

    @Test fun `old noneditable podcast remote bit does not trap its bookmark`() {
        val oldSearchBookmark = Playlist(name = "Podcast", browseId = "MPSPPL123",
            isEditable = false, isYoutubePlaylist = true)
        assertEquals(PodcastBookmarkOperation.Remove, podcastBookmarkOperation(oldSearchBookmark))
        assertEquals(PodcastBookmarkOperation.ExistingImport,
            podcastBookmarkOperation(oldSearchBookmark.copy(isEditable = true)))
    }
}
