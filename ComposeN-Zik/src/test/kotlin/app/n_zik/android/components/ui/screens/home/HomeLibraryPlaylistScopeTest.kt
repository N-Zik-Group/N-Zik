package app.n_zik.android.components.ui.screens.home

import app.it.fast4x.rimusic.PINNED_PREFIX
import app.it.fast4x.rimusic.enums.PlaylistsType
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.core.rewind.RewindPlaylists
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// runBlocking bridges synchronous JUnit tests to the suspend song-loader contract.
class HomeLibraryPlaylistScopeTest {
    private fun preview(id: Long, name: String, browseId: String? = null): PlaylistPreview =
        PlaylistPreview(Playlist(id = id, name = name, browseId = browseId), songCount = 1)

    private val podcast = preview(1, "Science podcast", "MPSPPL123")
    private val otherPodcast = preview(2, "Arts podcast", "MPSPPL456")
    private val remote = preview(3, "Science playlist", "VLPL789")
    private val local = preview(4, "Private local playlist")
    private val catalog = listOf(podcast, otherPodcast, remote, local)
    private val podcastScope = HomeLibraryPlaylistScope(
        PlaylistsType.YTPlaylist, YouTubeLibraryFilter.Podcasts, "", true, true, true, true,
        RewindPlaylists.Filter.All,
    )

    @Test fun `unselected delete targets contain only visible podcast rows and leave hidden local rows out`() {
        val targets = homeLibraryActionTargets(podcastScope.visible(catalog), emptyList())
        assertEquals(listOf(1L, 2L), targets.map { it.playlist.id })
        assertTrue(targets.none { it.playlist.id == local.playlist.id || it.playlist.id == remote.playlist.id })
    }

    @Test fun `playback and export load only visible current-search playlist targets`() = runBlocking {
        val visible = podcastScope.copy(query = "SCIENCE").visible(catalog)
        val targets = homeLibraryActionTargets(visible, emptyList())
        val requestedIds = mutableListOf<Long>()
        val songs = loadHomeLibraryActionSongs(targets) { id ->
            requestedIds.add(id)
            listOf(Song.makePlaceholder("episode-$id"))
        }
        assertEquals(listOf(1L), requestedIds)
        assertEquals(listOf("episode-1"), songs.map { it.id })
    }

    @Test fun `hidden explicit selections neither act on hidden rows nor fall back to all visible rows`() {
        val visible = podcastScope.visible(catalog)
        assertTrue(homeLibraryActionTargets(visible, listOf(local)).isEmpty())
        assertEquals(listOf(2L), homeLibraryActionTargets(visible, listOf(otherPodcast, local)).map { it.playlist.id })
        assertEquals(listOf(1L), homeLibraryActionTargets(visible, listOf(podcast)).map { it.playlist.id })
    }

    @Test fun `song loading deduplicates shared episodes while retaining visible order`() = runBlocking {
        val songs = loadHomeLibraryActionSongs(podcastScope.visible(catalog)) { id ->
            listOf(Song.makePlaceholder("shared"), Song.makePlaceholder("episode-$id"))
        }
        assertEquals(listOf("shared", "episode-1", "episode-2"), songs.map { it.id })
    }

    @Test fun `all pinned and rewind predicates retain their original visibility rules`() {
        val month = preview(5, RewindPlaylists.monthlyName(2026, 10))
        val year = preview(6, RewindPlaylists.yearlyName(2026))
        val pinned = preview(7, "${PINNED_PREFIX}Pinned")
        val rows = catalog + month + year + pinned
        val all = podcastScope.copy(type = PlaylistsType.Playlist, showRewind = false, showPinned = false)
        assertEquals(catalog, all.visible(rows))
        assertEquals(listOf(pinned), all.copy(type = PlaylistsType.PinnedPlaylist).visible(rows))
        assertEquals(listOf(year), all.copy(type = PlaylistsType.MonthlyPlaylist,
            rewindFilter = RewindPlaylists.Filter.Year).visible(rows))
        assertEquals(listOf(month), all.copy(type = PlaylistsType.MonthlyPlaylist,
            monthlyEnabled = true, yearlyEnabled = false).visible(rows))
    }
}
