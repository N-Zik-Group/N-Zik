package app.n_zik.android.playback.services.automotive.models

import app.it.fast4x.rimusic.models.Song
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Issue #777 — pins the per-playlist scoping of [AutoSearchState.playlistSongsById]:
 * the songs of an opened online playlist must stay addressable by their own
 * playlist id, must not displace other playlists' songs, and must be cleared
 * with the rest of the search state.
 */
class AutoSearchStateTest {

    @AfterEach
    fun resetGlobalState() {
        // AutoSearchState is process-wide mutable state; keep tests isolated.
        AutoSearchState.clear()
    }

    @Test
    fun `playlistSongsById keeps songs scoped to their own playlist`() {
        val songsA = listOf(Song.makePlaceholder("a-1"), Song.makePlaceholder("a-2"))
        val songsB = listOf(Song.makePlaceholder("b-1"))

        AutoSearchState.playlistSongsById = AutoSearchState.playlistSongsById + ("OLAK-a" to songsA)
        AutoSearchState.playlistSongsById = AutoSearchState.playlistSongsById + ("OLAK-b" to songsB)

        // Opening playlist B must not displace playlist A's songs...
        assertEquals(songsA, AutoSearchState.playlistSongsById["OLAK-a"])
        // ...and resolving B must only return B's songs.
        assertEquals(songsB, AutoSearchState.playlistSongsById["OLAK-b"])
        assertNull(AutoSearchState.playlistSongsById["OLAK-missing"])
    }

    @Test
    fun `opening a second playlist does not contaminate the first`() {
        // The pre-fix global searchedSongs grew across playlists, so a queue
        // built for playlist B could start on a song of playlist A.
        AutoSearchState.playlistSongsById = AutoSearchState.playlistSongsById + ("OLAK-a" to listOf(Song.makePlaceholder("a-1")))
        AutoSearchState.playlistSongsById = AutoSearchState.playlistSongsById + ("OLAK-b" to listOf(Song.makePlaceholder("b-1"), Song.makePlaceholder("b-2")))

        val songsOfB = AutoSearchState.playlistSongsById["OLAK-b"].orEmpty()
        assertEquals(listOf("b-1", "b-2"), songsOfB.map { it.id })
        // A's songs are still reachable by their own key only.
        assertEquals(listOf("a-1"), AutoSearchState.playlistSongsById["OLAK-a"]?.map { it.id })
    }

    @Test
    fun `clear resets playlistSongsById and searchedSongs`() {
        AutoSearchState.playlistSongsById = mapOf("OLAK-a" to listOf(Song.makePlaceholder("a-1")))
        AutoSearchState.searchedSongs = listOf(Song.makePlaceholder("s-1"))

        AutoSearchState.clear()

        assertTrue(AutoSearchState.playlistSongsById.isEmpty())
        assertTrue(AutoSearchState.searchedSongs.isEmpty())
    }
}
