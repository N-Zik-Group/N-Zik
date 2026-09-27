package app.n_zik.android.database

import app.n_zik.android.core.database.AlbumBookmarkState
import app.n_zik.android.core.database.AlbumTable
import app.n_zik.android.core.database.ArtistBookmarkState
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.BookmarkStateManager
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistBookmarkState
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.core.database.PlaylistTable
import app.n_zik.android.core.database.SongLikeState
import app.n_zik.android.core.database.SongPlaylistMapTable
import app.n_zik.android.core.database.SongTable
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests the SQL bind-parameter chunking of the reactive state managers
 * ([LikeStateManager], [PlaylistStateManager], [BookmarkStateManager]).
 *
 * Regression test: a single `WHERE id IN (:ids)` query fails with
 * `SQLiteException: too many SQL variables` as soon as the list exceeds
 * SQLITE_MAX_VARIABLE_NUMBER (999 on SQLite < 3.32 — the observed crash on
 * Android 10 devices with large libraries). The managers must therefore never
 * pass more than 500 IDs to a single DAO call.
 *
 * The DAOs are mocked (MockK — same convention as `ShufflerTest`): the SQLite
 * bundled with Robolectric (>= 3.32, limit 32766) cannot reproduce the
 * 999-parameter crash, so the chunk-size assertions on the recorded DAO calls
 * are the meaningful guard, together with the merged-map correctness checks.
 * The end-to-end counterpart against a real in-memory database lives in
 * [StateManagerChunkingRealDbTest].
 *
 * JUnit 4 test methods cannot be suspend, so the coroutine entry points use
 * runBlocking (same convention as the Room DAO tests).
 */
class StateManagerChunkingTest {

    @Before
    fun mockDatabase() {
        mockkObject(Database)
    }

    @After
    fun unmockDatabase() {
        unmockkObject(Database)
    }

    /**
     * Mock [SongTable] whose [SongTable.getLikeStatesForSongs] records every
     * chunk it receives and serves its results from a shared, mutable state —
     * so each chunk stays live (re-emitting when the state changes), exactly
     * like a Room invalidation flow.
     */
    private class LikeHarness(ids: List<String>) {
        val ids: List<String> = ids
        val calls = mutableListOf<List<String>>()
        val states = MutableStateFlow(
            ids.map { id -> SongLikeState(songId = id, likeState = null) }
        )
        val songTable: SongTable = mockk()

        init {
            coEvery { songTable.getLikeStatesForSongs(any()) } answers {
                val chunk = firstArg<List<String>>()
                calls += chunk
                val chunkSet = chunk.toHashSet()
                states.map { all -> all.filter { it.songId in chunkSet } }
            }
        }
    }

    // ---- LikeStateManager ----

    @Test
    fun `like states with 1500 ids never pass more than 500 to a single DAO call and merge into one complete map`() = runBlocking {
        val harness = LikeHarness((0 until 1500).map { "song_$it" })
        harness.states.value = harness.ids.map { id ->
            val i = id.removePrefix("song_").toInt()
            SongLikeState(songId = id, likeState = when (i % 3) { 0 -> true; 1 -> false; else -> null })
        }
        every { Database.songTable } returns harness.songTable

        val map = LikeStateManager.getLikeStates(harness.ids).first()

        assertEquals(1500, map.size)
        assertEquals(500, map.count { it.value == true })
        assertEquals(500, map.count { it.value == false })
        assertEquals(500, map.count { it.value == null })
        // the DAO was called in 500-ID chunks — never as a single 1500-parameter query
        assertEquals(3, harness.calls.size)
        assertTrue(harness.calls.all { it.size <= 500 })
        assertEquals(harness.ids.toSet(), harness.calls.flatten().toSet())
    }

    @Test
    fun `like states with exactly 500 ids use a single DAO call`() = runBlocking {
        val harness = LikeHarness((0 until 500).map { "song_$it" })
        every { Database.songTable } returns harness.songTable

        val map = LikeStateManager.getLikeStates(harness.ids).first()

        assertEquals(500, map.size)
        // pins the chunk boundary from below: a constant drift under 500 would yield 2 calls
        assertEquals(listOf(500), harness.calls.map { it.size })
    }

    @Test
    fun `like states with 501 ids use two DAO calls of 500 and 1`() = runBlocking {
        val harness = LikeHarness((0 until 501).map { "song_$it" })
        every { Database.songTable } returns harness.songTable

        val map = LikeStateManager.getLikeStates(harness.ids).first()

        assertEquals(501, map.size)
        // pins the chunk boundary from above: a constant drift over 500 would yield 1 call
        assertEquals(listOf(500, 1), harness.calls.map { it.size })
    }

    @Test
    fun `like states re-emit the merged map when a state changes in a later chunk`() = runBlocking {
        val harness = LikeHarness((0 until 1500).map { "song_$it" })
        every { Database.songTable } returns harness.songTable

        val firstEmission = CompletableDeferred<Map<String, Boolean?>>()
        val secondEmission = CompletableDeferred<Map<String, Boolean?>>()
        val job = launch {
            LikeStateManager.getLikeStates(harness.ids)
                .take(2)
                .collect { m ->
                    if (!firstEmission.isCompleted) firstEmission.complete(m) else secondEmission.complete(m)
                }
        }
        val m1 = withTimeout(5_000) { firstEmission.await() }
        assertEquals(1500, m1.size)
        assertEquals(null, m1["song_700"])

        // flip the state of an id living in the 2nd chunk (indices 500..999)
        harness.states.value = harness.states.value.map {
            if (it.songId == "song_700") it.copy(likeState = true) else it
        }

        val m2 = withTimeout(5_000) { secondEmission.await() }
        assertEquals(true, m2["song_700"])
        // the re-emitted map must stay complete, not partial
        assertEquals(1500, m2.size)
        job.cancel()
    }

    @Test
    fun `like states with an empty list emit an empty map without any DAO call`() = runBlocking {
        val harness = LikeHarness(emptyList())
        every { Database.songTable } returns harness.songTable

        val map = LikeStateManager.getLikeStates(emptyList()).first()

        assertEquals(emptyMap<String, Boolean?>(), map)
        assertTrue(harness.calls.isEmpty())
    }

    // ---- PlaylistStateManager ----

    @Test
    fun `playlist states chunk the DAO calls and keep every requested id in the map`() = runBlocking {
        val ids = (0 until 1500).map { "song_$it" }
        val calls = mutableListOf<List<String>>()
        // 300 songs are actually mapped, spread across all three chunks
        val mappedSet = (0 until 300).map { "song_${it * 5}" }.toHashSet()
        val dao = mockk<SongPlaylistMapTable>()
        coEvery { dao.songsInPlaylists(any()) } answers {
            val chunk = firstArg<List<String>>()
            calls += chunk
            flowOf(chunk.filter { it in mappedSet })
        }

        val map = PlaylistStateManager.getPlaylistStates(ids, dao).first()

        // every requested id is present (explicit falses recomputed on the full list)
        assertEquals(1500, map.size)
        assertEquals(300, map.count { it.value })
        assertEquals(1200, map.count { !it.value })
        assertEquals(3, calls.size)
        assertTrue(calls.all { it.size <= 500 })
    }

    @Test
    fun `playlist states re-emit the merged map when a mapping appears in a later chunk`() = runBlocking {
        val ids = (0 until 1500).map { "song_$it" }
        val mapped = MutableStateFlow(setOf("song_10")) // initially in the 1st chunk only
        val dao = mockk<SongPlaylistMapTable>()
        coEvery { dao.songsInPlaylists(any()) } answers {
            val chunk = firstArg<List<String>>()
            val chunkSet = chunk.toHashSet()
            mapped.map { m -> m.intersect(chunkSet).toList() }
        }

        val firstEmission = CompletableDeferred<Map<String, Boolean>>()
        val secondEmission = CompletableDeferred<Map<String, Boolean>>()
        val job = launch {
            PlaylistStateManager.getPlaylistStates(ids, dao)
                .take(2)
                .collect { m ->
                    if (!firstEmission.isCompleted) firstEmission.complete(m) else secondEmission.complete(m)
                }
        }
        val m1 = withTimeout(5_000) { firstEmission.await() }
        assertEquals(1500, m1.size)
        assertEquals(false, m1["song_700"])

        // a song from the 2nd chunk (indices 500..999) gets mapped
        mapped.value = mapped.value + "song_700"

        val m2 = withTimeout(5_000) { secondEmission.await() }
        assertEquals(true, m2["song_700"])
        // the re-emitted map must stay complete, not partial
        assertEquals(1500, m2.size)
        job.cancel()
    }

    @Test
    fun `playlist states with an empty list emit an empty map without any DAO call`() = runBlocking {
        val calls = mutableListOf<List<String>>()
        val dao = mockk<SongPlaylistMapTable>()
        coEvery { dao.songsInPlaylists(any()) } answers {
            calls += firstArg<List<String>>()
            flowOf(emptyList())
        }

        val map = PlaylistStateManager.getPlaylistStates(emptyList(), dao).first()

        assertEquals(emptyMap<String, Boolean>(), map)
        assertTrue(calls.isEmpty())
    }

    // ---- BookmarkStateManager ----

    @Test
    fun `artist bookmark states chunk the DAO calls and merge into one complete map`() = runBlocking {
        val ids = (0 until 1500).map { "artist_$it" }
        val calls = mutableListOf<List<String>>()
        val dao = mockk<ArtistTable>()
        coEvery { dao.getBookmarkStatesForArtists(any()) } answers {
            val chunk = firstArg<List<String>>()
            calls += chunk
            flowOf(chunk.map { id -> ArtistBookmarkState(artistId = id, bookmarkState = null) })
        }
        every { Database.artistTable } returns dao

        val map = BookmarkStateManager.getArtistBookmarkStates(ids).first()

        assertEquals(1500, map.size)
        assertTrue(map.values.all { it == null })
        assertEquals(3, calls.size)
        assertTrue(calls.all { it.size <= 500 })
    }

    @Test
    fun `artist bookmark states re-emit the merged map when a bookmark changes in a later chunk`() = runBlocking {
        val ids = (0 until 1500).map { "artist_$it" }
        val states = MutableStateFlow(ids.map { id -> ArtistBookmarkState(artistId = id, bookmarkState = null) })
        val dao = mockk<ArtistTable>()
        coEvery { dao.getBookmarkStatesForArtists(any()) } answers {
            val chunk = firstArg<List<String>>()
            val chunkSet = chunk.toHashSet()
            states.map { all -> all.filter { it.artistId in chunkSet } }
        }
        every { Database.artistTable } returns dao

        val firstEmission = CompletableDeferred<Map<String, Boolean?>>()
        val secondEmission = CompletableDeferred<Map<String, Boolean?>>()
        val job = launch {
            BookmarkStateManager.getArtistBookmarkStates(ids)
                .take(2)
                .collect { m ->
                    if (!firstEmission.isCompleted) firstEmission.complete(m) else secondEmission.complete(m)
                }
        }
        val m1 = withTimeout(5_000) { firstEmission.await() }
        assertEquals(1500, m1.size)
        assertEquals(null, m1["artist_700"])

        // bookmark an id living in the 2nd chunk (indices 500..999)
        states.value = states.value.map {
            if (it.artistId == "artist_700") it.copy(bookmarkState = true) else it
        }

        val m2 = withTimeout(5_000) { secondEmission.await() }
        assertEquals(true, m2["artist_700"])
        // the re-emitted map must stay complete, not partial
        assertEquals(1500, m2.size)
        job.cancel()
    }

    @Test
    fun `album bookmark states chunk the DAO calls and merge into one complete map`() = runBlocking {
        val ids = (0 until 1500).map { "album_$it" }
        val calls = mutableListOf<List<String>>()
        val dao = mockk<AlbumTable>()
        coEvery { dao.getBookmarkStatesForAlbums(any()) } answers {
            val chunk = firstArg<List<String>>()
            calls += chunk
            flowOf(chunk.map { id -> AlbumBookmarkState(albumId = id, bookmarkState = null) })
        }
        every { Database.albumTable } returns dao

        val map = BookmarkStateManager.getAlbumBookmarkStates(ids).first()

        assertEquals(1500, map.size)
        assertTrue(map.values.all { it == null })
        assertEquals(3, calls.size)
        assertTrue(calls.all { it.size <= 500 })
    }

    @Test
    fun `playlist bookmark states chunk the DAO calls and merge into one complete map`() = runBlocking {
        val ids = (0 until 1500).map { "PL${it}_browse" }
        val calls = mutableListOf<List<String>>()
        val dao = mockk<PlaylistTable>()
        coEvery { dao.getBookmarkStatesForPlaylists(any()) } answers {
            val chunk = firstArg<List<String>>()
            calls += chunk
            flowOf(chunk.map { id -> PlaylistBookmarkState(browseId = id, isYoutubePlaylist = true) })
        }
        every { Database.playlistTable } returns dao

        val map = BookmarkStateManager.getPlaylistBookmarkStates(ids).first()

        assertEquals(1500, map.size)
        assertTrue(map.values.all { it })
        assertEquals(3, calls.size)
        assertTrue(calls.all { it.size <= 500 })
    }
}
