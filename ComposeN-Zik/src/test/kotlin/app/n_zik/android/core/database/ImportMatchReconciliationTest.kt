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
