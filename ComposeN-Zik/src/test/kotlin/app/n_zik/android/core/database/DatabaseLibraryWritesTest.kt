package app.n_zik.android.core.database

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract §10.3 (since 1.7.3, feature `library.live`): the [Database] library-write proxies —
 * the changed entity per table, the write methods they report (every name is a real DAO method,
 * no read-only one), and the proxy's behavior (delegates unchanged, notifies per write, never on
 * a Flow read, silent and safe when no listener is registered).
 */
class DatabaseLibraryWritesTest {

    @Test
    fun `the changed entity per table matches the contract kinds`() {
        assertEquals(listOf("songs"), Database.libraryTableKinds[SongTable::class.java])
        assertEquals(listOf("albums"), Database.libraryTableKinds[AlbumTable::class.java])
        assertEquals(listOf("artists"), Database.libraryTableKinds[ArtistTable::class.java])
        assertEquals(listOf("playlists"), Database.libraryTableKinds[PlaylistTable::class.java])
        assertEquals(listOf("songs"), Database.libraryTableKinds[FormatTable::class.java])
        // A playlist membership change also reloads the open playlist's track list (a songs detail)
        assertEquals(listOf("playlists", "songs"), Database.libraryTableKinds[SongPlaylistMapTable::class.java])
        assertEquals(listOf("albums"), Database.libraryTableKinds[SongAlbumMapTable::class.java])
        assertEquals(listOf("artists"), Database.libraryTableKinds[SongArtistMapTable::class.java])
        assertEquals(listOf("songs", "artists", "albums"), Database.libraryTableKinds[EventTable::class.java])
        // Exactly the library tables are wrapped
        assertEquals(9, Database.libraryTableKinds.size)
        assertEquals(9, Database.libraryTableWrites.size)
    }

    @Test
    fun `every reported write is a real DAO method`() {
        for ((daoClass, writes) in Database.libraryTableWrites) {
            val declared = daoClass.declaredMethods.map { it.name }.toSet()
            for (name in writes) {
                assertTrue(name in declared, "$name is declared on ${daoClass.simpleName}")
            }
        }
    }

    @Test
    fun `read only methods stay outside the write sets`() {
        val reads = mapOf(
            SongTable::class.java to setOf("all", "findById", "findByIdDirect", "countById", "exists", "isLiked", "getLikeStatesForSongs", "sortAll", "artistSongs"),
            AlbumTable::class.java to setOf("all", "allBookmarked", "findById", "findBySongId", "isBookmarked", "sortBookmarked"),
            ArtistTable::class.java to setOf("allFollowing", "findById", "findByName", "getTopSongsByArtist", "sortFollowing"),
            PlaylistTable::class.java to setOf("allSongs", "findByBrowseId", "getAll", "exists", "sortPreviews"),
            FormatTable::class.java to setOf("allWithSongs", "findBySongId", "findContentLengthOf", "sortAllWithSongs"),
            SongPlaylistMapTable::class.java to setOf("allSongsOf", "findById", "findPositionOf", "isMapped", "getMaxPosition", "countSongsInPlaylist", "sortSongs"),
            SongAlbumMapTable::class.java to setOf("allSongsOf", "allSongsOfDirect", "albumsOfArtistDirect", "getTopSongsOfDirect", "findAlbumOf", "findAlbumsOfDirect", "findPositionOf", "songCountsDirect"),
            SongArtistMapTable::class.java to setOf("allSongsBy", "allSongsByDirect", "findArtistsOf", "findArtistsOfDirect", "allPairsDirect", "pairsBySongIdDirect", "songCountsDirect"),
            EventTable::class.java to setOf("countAll", "allWithSong", "findSongsMostPlayedBetween", "getPlayCount", "getAllPlayCounts"),
        )
        for ((daoClass, readNames) in reads) {
            val writes = Database.libraryTableWrites.getValue(daoClass)
            for (name in readNames) {
                assertFalse(name in writes, "$name is a read on ${daoClass.simpleName}")
            }
        }
    }

    // --- The proxy itself ---

    private interface ProbeDao {
        fun likeState(id: String, state: Boolean?): Int
        fun likeState(id: String): Flow<Boolean?>
        fun readCount(): Int
    }

    private class FakeProbe : ProbeDao {
        var likeWrites = 0
        var likeFlowReads = 0
        var countReads = 0

        override fun likeState(id: String, state: Boolean?): Int { likeWrites++; return 1 }
        override fun likeState(id: String): Flow<Boolean?> { likeFlowReads++; return flowOf(true) }
        override fun readCount(): Int { countReads++; return 3 }
    }

    @Test
    fun `the proxy delegates unchanged and notifies once per write`() {
        val fake = FakeProbe()
        val notified = mutableListOf<List<String>>()
        Database.libraryWriteNotifier = { kinds -> notified += kinds }

        val probe = Database.libraryWrites(fake, ProbeDao::class.java, listOf("songs"), setOf("likeState"))
        try {
            assertEquals(1, probe.likeState("id", true))
            assertEquals(1, fake.likeWrites)
            assertEquals(listOf(listOf("songs")), notified)

            // The Flow read shares the write's name: it never notifies
            assertNotNull(probe.likeState("id"))
            assertEquals(1, fake.likeFlowReads)
            assertEquals(1, notified.size)

            // A method outside the write set never notifies
            assertEquals(3, probe.readCount())
            assertEquals(1, fake.countReads)
            assertEquals(1, notified.size)
        } finally {
            Database.libraryWriteNotifier = null
        }
    }

    private interface SuspendProbeDao {
        suspend fun likeState(id: String, state: Boolean?): Int
    }

    private class FakeSuspendProbe : SuspendProbeDao {
        var likeWrites = 0

        override suspend fun likeState(id: String, state: Boolean?): Int {
            delay(100) // a real suspension: the caller's continuation is not resumed yet
            likeWrites++
            return 1
        }
    }

    @Test
    fun `a suspend write notifies on completion, not at its first return`() = runTest {
        val fake = FakeSuspendProbe()
        val notified = mutableListOf<List<String>>()
        Database.libraryWriteNotifier = { kinds -> notified += kinds }
        try {
            val probe = Database.libraryWrites(fake, SuspendProbeDao::class.java, listOf("songs"), setOf("likeState"))

            // The bare `invoke` of a suspend write returns COROUTINE_SUSPENDED: the notification
            // must wait for the write to actually finish
            launch(UnconfinedTestDispatcher(testScheduler)) {
                assertEquals(1, probe.likeState("id", true))
            }
            runCurrent()
            assertEquals(0, fake.likeWrites, "the write is still in flight")
            assertEquals(emptyList<List<String>>(), notified, "no notification before the write completes")

            advanceUntilIdle()
            assertEquals(1, fake.likeWrites)
            assertEquals(listOf(listOf("songs")), notified)
        } finally {
            Database.libraryWriteNotifier = null
        }
    }

    @Test
    fun `a failed suspend write notifies nothing`() = runTest {
        val notified = mutableListOf<List<String>>()
        Database.libraryWriteNotifier = { kinds -> notified += kinds }
        val failing = object : SuspendProbeDao {
            override suspend fun likeState(id: String, state: Boolean?): Int = throw IllegalStateException("boom")
        }
        try {
            val probe = Database.libraryWrites(failing, SuspendProbeDao::class.java, listOf("songs"), setOf("likeState"))
            assertTrue(runCatching { probe.likeState("id", true) }.isFailure, "the write's failure reaches the caller")
            assertEquals(emptyList<List<String>>(), notified, "a failed write changed nothing")
        } finally {
            Database.libraryWriteNotifier = null
        }
    }

    @Test
    fun `a write is silent without a listener and a throwing listener does not break the call`() {
        val fake = FakeProbe()
        val probe = Database.libraryWrites(fake, ProbeDao::class.java, listOf("songs"), setOf("likeState"))

        Database.libraryWriteNotifier = null
        assertEquals(1, probe.likeState("id", false))
        assertEquals(1, fake.likeWrites)

        Database.libraryWriteNotifier = { throw IllegalStateException("boom") }
        try {
            assertEquals(1, probe.likeState("id", true))
            assertEquals(2, fake.likeWrites)
        } finally {
            Database.libraryWriteNotifier = null
        }
    }
}
