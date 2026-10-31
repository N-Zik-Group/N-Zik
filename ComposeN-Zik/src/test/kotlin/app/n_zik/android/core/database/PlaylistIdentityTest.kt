package app.n_zik.android.core.database

import app.it.fast4x.rimusic.models.Playlist
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistIdentityTest {
    private val podcastId = "MPSPPLXK2KwV2tv0jwie5sRfW9Mrdz3gyZp9Vr"

    @Test fun `reported unsaved podcast retains YouTube provenance`() {
        assertTrue(isYouTubePlaylistSource(podcastId))
        assertFalse(isPlaylistBookmarked(null))
        assertTrue(isYouTubePlaylistSource("PL123", remoteSource = true))
        assertFalse(isYouTubePlaylistSource(null))
        assertFalse(isYouTubePlaylistSource("SPOTIFY_IMPORT"))
    }

    @Test fun `podcast snapshot is bookmarked with remote bit false`() {
        val saved = Playlist(name = "Podcast", browseId = podcastId,
            isEditable = false, isYoutubePlaylist = false)
        assertTrue(isPlaylistBookmarked(saved))
        assertTrue(PlaylistBookmarkState(podcastId, false).isBookmarked())
    }

    @Test fun `ordinary playlist remote flag keeps its existing contract`() {
        for (id in listOf("PL123", "VLPL123")) {
            val saved = Playlist(name = "Playlist", browseId = id, isYoutubePlaylist = false)
            assertFalse(isPlaylistBookmarked(saved))
            assertTrue(isPlaylistBookmarked(saved.copy(isYoutubePlaylist = true)))
        }
        assertFalse(PlaylistBookmarkState("PL123", false).isBookmarked())
        assertTrue(PlaylistBookmarkState("PL123", true).isBookmarked())
        assertTrue(isYouTubePlaylistSource("VLPL123"))
    }

    @Test fun `local provider and generated IDs never expose YouTube bookmark or sync controls`() {
        val localIds = listOf(null, "", " ", "my-local-playlist", "d89355db-0ea7-470a-9a0b-02c7875eac00",
            "SPOTIFY_IMPORT", "SPOTIFY_IMPORT_HOMESONGS_42", "RIPLAY_IMPORT", "RIPLAY_IMPORT_123",
            "PIPED_IMPORT", "piped:abc", "rewind-2026", "monthly:2026-10", "MPS", "VL")
        for (id in localIds) {
            val local = Playlist(name = "Local", browseId = id, isYoutubePlaylist = true, isEditable = false)
            assertFalse("bookmark $id", canBookmarkPlaylist(local))
            assertFalse("sync $id", canRefreshPlaylist(local))
            assertFalse("state $id", isPlaylistBookmarked(local))
            assertFalse("lock $id", isReadOnlyPlaylistSource(local))
        }
    }

    @Test fun `linked YouTube imports podcasts and account lists use distinct capabilities`() {
        for (id in listOf("PL123", "VLPL123", "RD123", "OLAK5uy_123")) {
            val imported = Playlist(name = "Imported", browseId = id, isEditable = true)
            assertTrue("bookmark $id", canBookmarkPlaylist(imported))
            assertTrue("sync $id", canRefreshPlaylist(imported))
            assertFalse(isReadOnlyPlaylistSource(imported))
        }
        for (id in listOf("LM", "SE", "VLLM", "VLSE", "modified:PL123")) {
            val linked = Playlist(name = "Linked", browseId = id)
            assertFalse("bookmark $id", canBookmarkPlaylist(linked))
            assertTrue("sync $id", canRefreshPlaylist(linked))
        }
        val podcast = Playlist(name = "Podcast", browseId = podcastId, isEditable = false)
        assertTrue(canBookmarkPlaylist(podcast))
        assertTrue(canRefreshPlaylist(podcast))
        assertTrue(isPlaylistBookmarked(podcast))
        assertTrue(isReadOnlyPlaylistSource(podcast))
        assertFalse(canRefreshPlaylist(podcast.copy(isEditable = true)))
        assertFalse(isReadOnlyPlaylistSource(podcast.copy(isEditable = true)))
        val modifiedPodcast = podcast.copy(browseId = "modified:$podcastId")
        assertFalse(canBookmarkPlaylist(modifiedPodcast))
        assertFalse(canRefreshPlaylist(modifiedPodcast))
    }
}
