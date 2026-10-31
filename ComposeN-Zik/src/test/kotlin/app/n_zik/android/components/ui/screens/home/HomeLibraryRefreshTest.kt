package app.n_zik.android.components.ui.screens.home

import app.it.fast4x.rimusic.enums.PlaylistsType
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.n_zik.android.core.rewind.RewindPlaylists
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// runBlocking bridges JUnit's synchronous entrypoints to the suspend refresh dispatcher.
class HomeLibraryRefreshTest {
    private fun preview(id: Long, browseId: String?, editable: Boolean = false): PlaylistPreview =
        PlaylistPreview(Playlist(id, "Playlist $id", browseId, isEditable = editable), 1)

    private val podcast = preview(1, "MPSPPLXK2KwV2tv0jwie5sRfW9Mrdz3gyZp9Vr")
    private val otherPodcast = preview(2, "MPSPPLsecond")
    private val playlist = preview(3, "VLPLordinary")
    private val local = preview(4, null)
    private val scope = HomeLibraryPlaylistScope(PlaylistsType.YTPlaylist, YouTubeLibraryFilter.Podcasts,
        "", true, true, true, true, RewindPlaylists.Filter.All)

    @Test fun `podcast-only pull refresh loads visible exact IDs without starting ordinary service`() = runBlocking {
        val visible = scope.copy(query = "Playlist 1").visible(listOf(podcast, otherPodcast, playlist, local))
        val refreshed = mutableListOf<String?>()
        val result = refreshHomeLibraryTargets(homeLibraryActionTargets(visible, emptyList()),
            dispatchPlaylists = { error("Podcast-only filter must not start the ordinary service") },
            refreshPodcast = { row -> refreshed.add(row.browseId); true })
        assertEquals(listOf(podcast.playlist.browseId), refreshed)
        assertEquals(HomeLibraryRefreshResult(1, 0), result)
    }

    @Test fun `mixed visible targets split podcast loader and exact ordinary service IDs`() = runBlocking {
        val serviceIds = mutableListOf<List<Long>>()
        val refreshed = mutableListOf<Long>()
        val visible = scope.copy(youtubeFilter = YouTubeLibraryFilter.All)
            .visible(listOf(podcast, otherPodcast, playlist, local))
        val result = refreshHomeLibraryTargets(visible,
            dispatchPlaylists = { serviceIds.add(it) },
            refreshPodcast = { row -> refreshed.add(row.id); true })
        assertEquals(listOf(listOf(3L)), serviceIds)
        assertEquals(listOf(1L, 2L), refreshed)
        assertEquals(HomeLibraryRefreshResult(2, 0), result)
    }

    @Test fun `empty and unsupported targets never dispatch an empty all-playlists request`() = runBlocking {
        for (targets in listOf(emptyList(), listOf(local, preview(5, "PIPED_IMPORT"),
            preview(6, "MPSPPLedited", editable = true), preview(7, "modified:MPSPPLcopy")))) {
            val result = refreshHomeLibraryTargets(targets,
                dispatchPlaylists = { error("Unsupported or empty IDs must not dispatch") },
                refreshPodcast = { error("Protected or local rows must not refresh") })
            assertEquals(HomeLibraryRefreshResult(0, 0), result)
        }
    }

    @Test fun `failed podcast does not prevent other visible podcasts from refreshing`() = runBlocking {
        val refreshed = mutableListOf<Long>()
        val result = refreshHomeLibraryTargets(listOf(podcast, otherPodcast),
            dispatchPlaylists = { error("No ordinary rows") },
            refreshPodcast = { row -> refreshed.add(row.id); row.id != podcast.playlist.id })
        assertEquals(listOf(1L, 2L), refreshed)
        assertEquals(HomeLibraryRefreshResult(1, 1), result)
        assertTrue(refreshed.none { it == local.playlist.id })
    }
}
