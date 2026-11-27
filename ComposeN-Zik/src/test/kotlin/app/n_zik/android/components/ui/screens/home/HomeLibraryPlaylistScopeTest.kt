package app.n_zik.android.components.ui.screens.home

import app.n_zik.android.PINNED_PREFIX
import app.n_zik.android.core.database.HomeParentSong
import app.n_zik.android.enums.PlaylistsType
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.models.Song
import app.n_zik.android.core.rewind.RewindPlaylists
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        PlaylistsType.YTPlaylist, YouTubeLibraryFilter.Podcasts, "", true, true,
        RewindPlaylists.Filter.All,
    )

    @Test fun `unselected delete targets contain only visible podcast rows and leave hidden local rows out`() {
        val targets = homeLibraryActionTargets(podcastScope.visible(catalog), emptyList(), selectionActive = false)
        assertEquals(listOf(1L, 2L), targets.map { it.playlist.id })
        assertTrue(targets.none { it.playlist.id == local.playlist.id || it.playlist.id == remote.playlist.id })
    }

    @Test fun `playback and export load only visible current-search playlist targets`() = runBlocking {
        val visible = podcastScope.copy(query = "SCIENCE").visible(catalog)
        val targets = homeLibraryActionTargets(visible, emptyList(), selectionActive = false)
        val requestedIds = mutableListOf<Long>()
        val songs = loadHomeParentSongs(targets.map { it.playlist.id }, load = { ids ->
            requestedIds.addAll(ids)
            ids.map { HomeParentSong(it.toString(), Song.makePlaceholder("episode-$it")) }
        })
        assertEquals(listOf(1L), requestedIds)
        assertEquals(listOf("episode-1"), songs.map { it.id })
    }

    @Test fun `hidden explicit selections neither act on hidden rows nor fall back to all visible rows`() {
        val visible = podcastScope.visible(catalog)
        assertTrue(homeLibraryActionTargets(visible, emptyList(), selectionActive = true).isEmpty())
        assertTrue(homeLibraryActionTargets(visible, listOf(local), selectionActive = true).isEmpty())
        assertEquals(listOf(2L), homeLibraryActionTargets(visible, listOf(otherPodcast, local), selectionActive = true).map { it.playlist.id })
        assertEquals(listOf(1L), homeLibraryActionTargets(visible, listOf(podcast), selectionActive = true).map { it.playlist.id })
    }

    @Test fun `empty rows allow opening the picker from unfiltered All without a selection`() {
        val all = podcastScope.copy(type = PlaylistsType.Playlist)
        val emptyPlaylist = local.copy(songCount = 0)
        val hasTracks = all.visible(listOf(emptyPlaylist)).any { it.songCount > 0 }

        assertTrue(all.canOpenPlaylistPicker(hasTracks, selectionActive = false))
        assertFalse(all.canOpenPlaylistPicker(hasTracks, selectionActive = true))
        assertFalse(all.copy(query = "Private").canOpenPlaylistPicker(hasTracks, selectionActive = false))
        assertFalse(podcastScope.canOpenPlaylistPicker(hasTracks, selectionActive = false))
    }

    @Test fun `song loading deduplicates shared episodes while retaining visible order`() = runBlocking {
        val songs = loadHomeParentSongs(listOf(2L, 1L), load = { ids ->
            ids.sorted().flatMap { id ->
                listOf(
                    HomeParentSong(id.toString(), Song.makePlaceholder("shared")),
                    HomeParentSong(id.toString(), Song.makePlaceholder("episode-$id")),
                )
            }
        })
        assertEquals(listOf("shared", "episode-2", "episode-1"), songs.map { it.id })
    }

    @Test fun `all includes pinned and rewind while their dedicated tabs filter`() {
        val month = preview(5, RewindPlaylists.monthlyName(2026, 10))
        val year = preview(6, RewindPlaylists.yearlyName(2026))
        val pinned = preview(7, "${PINNED_PREFIX}Pinned")
        val rows = catalog + month + year + pinned
        val all = podcastScope.copy(type = PlaylistsType.Playlist)
        assertEquals(rows, all.visible(rows))
        assertEquals(listOf(pinned), all.copy(type = PlaylistsType.PinnedPlaylist).visible(rows))
        assertEquals(listOf(year), all.copy(type = PlaylistsType.MonthlyPlaylist,
            rewindFilter = RewindPlaylists.Filter.Year).visible(rows))
        assertEquals(listOf(month), all.copy(type = PlaylistsType.MonthlyPlaylist,
            monthlyEnabled = true, yearlyEnabled = false).visible(rows))
    }

    @Test fun `on device filters remote playlists while retaining pinned and search rules`() {
        val remotePlaylist = remote.copy(playlist = remote.playlist.copy(isYoutubePlaylist = true))
        val pinned = preview(7, "${PINNED_PREFIX}Local pinned")
        val scope = podcastScope.copy(type = PlaylistsType.OnDevice, query = "local")
        assertEquals(listOf(local, pinned), scope.visible(listOf(remotePlaylist, local, pinned)))
        assertTrue(scope.copy(query = "science").visible(listOf(remotePlaylist, local)).isEmpty())
    }

    @Test fun `localized playlist labels drive the search and the visible action targets`() {
        val october = preview(5, RewindPlaylists.monthlyName(2026, 10))
        val september = preview(6, RewindPlaylists.monthlyName(2026, 9))
        val scope = podcastScope.copy(type = PlaylistsType.MonthlyPlaylist, query = " OCTOBRE 2026 ")
        val localizedNames = mapOf(
            october.playlist.name to "Rétrospective octobre 2026",
            september.playlist.name to "Rétrospective septembre 2026",
        )
        val matches = listOf(october, september).filter { scope.matchesName(it.playlist.name, localizedNames::getValue) }
        assertEquals(listOf(october), matches)
        assertEquals(listOf(october), homeLibraryActionTargets(matches, listOf(october, september), selectionActive = true))
    }
}
