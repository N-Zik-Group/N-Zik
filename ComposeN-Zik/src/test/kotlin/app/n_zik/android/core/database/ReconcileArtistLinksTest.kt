package app.n_zik.android.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongArtistMap
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests [Database.reconcileArtistLinks] against an in-memory Room database.
 * Uses JUnit 4 annotations (@Before / @After) because Robolectric's @RunWith
 * is a JUnit 4 concept.
 *
 * [Database.reconcileArtistLinks] is unconditional by design - its contract is
 * "complete list only": the caller must gate on
 * [app.n_zik.android.core.database.ArtistMappingReconcile.isCompleteAuthorList]
 * before calling. These tests cover the replace-and-recount mechanics
 * (delete stale links, upsert the fresh artists, re-map, return the dropped
 * count). The gate itself is covered by the pure
 * `shouldReconcileAuthors` tests in `UpdateSongDialogTest`.
 *
 * SongArtistMap has FKs to both Song and Artist, so a Song row must exist
 * before any link; the artist rows are created by reconcile's own upsert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReconcileArtistLinksTest {

    private lateinit var db: DatabaseInitializer

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        // Redirect the lazy singleton to the in-memory database so the real
        // [Database.reconcileArtistLinks] body runs its real code against the
        // DAOs under test (the Database object itself stays unmocked).
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns db
    }

    @After
    fun closeDb() {
        unmockkAll()
        db.close()
    }

    /** Insert a minimal Song so that the FK constraint on SongArtistMap is satisfied. */
    private fun insertSong(id: String) {
        db.songTable.upsert(Song.makePlaceholder(id))
    }

    /** Insert an artist row so that the FK constraint on SongArtistMap is satisfied. */
    private fun insertArtist(id: String, name: String) {
        db.artistTable.upsert(Artist(id = id, name = name))
    }

    private fun insertLink(songId: String, artistId: String) {
        db.songArtistMapTable.insertIgnore(SongArtistMap(songId, artistId))
    }

    private fun linksOf(songId: String): Set<SongArtistMap> =
        db.songArtistMapTable.pairsBySongIdDirect(songId).toSet()

    @Test
    fun completeListReplacesStaleLinksAndReturnsDroppedCount() = runTest {
        insertSong("song_1")
        insertArtist("OLD_A", "Old A")
        insertArtist("OLD_B", "Old B")
        insertLink("song_1", "OLD_A")
        insertLink("song_1", "OLD_B")

        val dropped = Database.reconcileArtistLinks(
            "song_1",
            listOf(Artist(id = "NEW_A", name = "New A"), Artist(id = "NEW_B", name = "New B"))
        )

        assertEquals(2, dropped)
        assertEquals(
            setOf(SongArtistMap("song_1", "NEW_A"), SongArtistMap("song_1", "NEW_B")),
            linksOf("song_1")
        )
    }

    @Test
    fun orphanSongGetsLinksCreatedWithZeroDropped() = runTest {
        insertSong("song_1")

        val dropped = Database.reconcileArtistLinks(
            "song_1",
            listOf(Artist(id = "NEW_A", name = "New A"))
        )

        assertEquals(0, dropped)
        assertEquals(setOf(SongArtistMap("song_1", "NEW_A")), linksOf("song_1"))
        // reconcile upserts the artists itself, so the link's FK is satisfied
        assertEquals(Artist(id = "NEW_A", name = "New A"), db.artistTable.findByIdDirect("NEW_A"))
    }

    @Test
    fun existingFreshArtistRowIsKeptAndLinked() = runTest {
        insertSong("song_1")
        insertArtist("OLD_A", "Old A")
        insertLink("song_1", "OLD_A")
        // The fresh artist already has a row: reconcile's upsert is
        // insert-or-ignore, so the stored name must not be clobbered.
        insertArtist("NEW_A", "Stored Fresh Name")

        val dropped = Database.reconcileArtistLinks(
            "song_1",
            listOf(Artist(id = "NEW_A", name = "Different Fresh Name"))
        )

        assertEquals(1, dropped)
        assertEquals(setOf(SongArtistMap("song_1", "NEW_A")), linksOf("song_1"))
        assertEquals("Stored Fresh Name", db.artistTable.findByIdDirect("NEW_A")?.name)
    }
}
