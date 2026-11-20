package app.n_zik.android.core.database

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import app.n_zik.android.core.matching.SongMatchState
import app.n_zik.android.core.matching.SongMatchStatus
import app.n_zik.android.core.matching.reconcileSongMatch
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.Song

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ImportMatchReconciliationTest {
    private lateinit var database: DatabaseInitializer
    private val imports get() = database.importSongTable

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            DatabaseInitializer::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `playlist reconciliation preserves unresolved rows and leaves other candidates for global matching`() = runTest {
        val placeholder = ImportSong(id = 1, originalId = "unmatched-service-id", position = 0, playlistId = 101)
        val riplay = ImportSong(id = 2, originalId = "ABCDEFGHIJK", position = 1, playlistId = 101)
        val unknownDuration = ImportSong(id = 3, originalId = "DEFGHIJKLMN", position = 2, playlistId = 101)
        val absent = ImportSong(id = 4, originalId = "missing-service-id", position = 3, playlistId = 101)
        val other = ImportSong(id = 5, originalId = "another-placeholder", position = 0, playlistId = 202)
        val global = ImportSong(id = 6, originalId = "orphaned-import", position = 0)
        imports.insertAll(listOf(other, global, absent, unknownDuration, riplay, placeholder))
        database.songTable.upsert(
            listOf(
                Song.makePlaceholder(placeholder.originalId),
                Song.makePlaceholder(riplay.originalId).copy(durationText = "00:00"),
                Song.makePlaceholder(unknownDuration.originalId),
                Song.makePlaceholder(other.originalId),
            ),
        )

        assertEquals(listOf(placeholder, riplay), imports.reconcileMatches(101))
        assertEquals(listOf(1L, 2L, 5L, 6L), imports.getMatchRows(null).map { it.entry.id })
        assertEquals(listOf(placeholder, riplay, other), imports.reconcileMatches())
        assertEquals(listOf(1L, 2L, 5L), imports.getMatchRows(null).map { it.entry.id })
    }

    @Test
    fun `cancelled playlist reconciliation preserves the neighboring playlist and its songs`() = runTest {
        val source = Playlist(id = 101, name = "Source")
        val neighbor = Playlist(id = 202, name = "Neighbor")
        database.playlistTable.insertIgnore(source)
        database.playlistTable.insertIgnore(neighbor)
        val retained = Song.makePlaceholder("still-in-source")
        val removed = Song.makePlaceholder("removed-while-matching")
        database.songTable.upsert(listOf(retained, removed))
        database.songPlaylistMapTable.map(retained.id, source.id)
        database.songPlaylistMapTable.map(removed.id, neighbor.id)
        val sourceImport = ImportSong(originalId = "gone-source", position = 0, playlistId = source.id)
        val neighborImport = ImportSong(originalId = "gone-neighbor", position = 0, playlistId = neighbor.id)
        imports.insertAll(listOf(sourceImport, neighborImport))
        val result = SongMatchState("run", "playlist:${source.id}", SongMatchStatus.Cancelled, 2, playlistId = source.id)

        val completed = reconcileSongMatch(result) { imports.reconcileMatches(result.playlistId) }

        assertEquals(result, completed)
        assertEquals(listOf(neighborImport.originalId), imports.getMatchRows(null).map { it.entry.originalId })
        assertEquals(listOf(retained.id), database.songPlaylistMapTable.findSongsByIds(source.id, listOf(retained.id, removed.id)).map(Song::id))
    }

    @Test
    fun `a matched original ID retains the existing global deletion scope`() = runTest {
        val resolvedId = "BCDEFGHIJKL"
        val local = ImportSong(id = 1, originalId = resolvedId, position = 0, playlistId = 101)
        val duplicate = local.copy(id = 2, playlistId = 202)
        imports.insertAll(listOf(local, duplicate))
        database.songTable.upsert(Song.makePlaceholder(resolvedId).copy(durationText = "03:24"))

        assertEquals(emptyList<ImportSong>(), imports.reconcileMatches(101))
        assertEquals(emptyList<ImportMatchRow>(), imports.getMatchRows(null))
    }
}
