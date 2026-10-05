package app.n_zik.android.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongAlbumMap
import app.it.fast4x.rimusic.models.SongArtistMap
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The contract §10.3 live feature through its real entry point: the [Database] getters hand out
 * the notifying proxies, so a write performed through them — the way every write in the app is
 * performed — reaches [Database.libraryWriteNotifier] with the table's kinds, and a Flow read
 * through the same getter never does. [DatabaseLibraryWritesTest] covers the proxy machinery with
 * fakes; this test proves the wiring itself, so the feature cannot ship dead while it is green.
 *
 * JUnit 4 + [RobolectricTestRunner] through the junit-vintage-engine (the `Room` in-memory
 * database needs an Android context). JUnit 4 test methods cannot be suspend, so the coroutine
 * entry points use runBlocking (same convention as the other real-DB tests).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseLibraryWritesRealDbTest {

    private lateinit var db: DatabaseInitializer

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        // Route [Database] at the in-memory database: the getters then wrap its real DAOs
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns db
    }

    @After
    fun closeDb() {
        unmockkObject(DatabaseInitializer.Companion)
        Database.libraryWriteNotifier = null
        db.close()
    }

    @Test
    fun `a write through the real getters notifies the table's kinds`() = runBlocking {
        val notified = mutableListOf<List<String>>()
        Database.libraryWriteNotifier = { kinds -> notified += kinds }

        Database.songTable.upsert(Song.makePlaceholder("s-1"))
        assertEquals(listOf(listOf("songs")), notified)

        // The map tables' foreign keys point at the parent rows (Room enforces them in-memory too):
        // seed them through the raw DAOs — not the tracked getters — so the setup stays invisible
        // to the notifier
        db.artistTable.insertIgnore(Artist(id = "a-1"))
        db.albumTable.insertIgnore(Album(id = "al-1"))
        assertEquals(listOf(listOf("songs")), notified)

        // The album/artist map tables: a standalone map write (the client-visible trackCount of an
        // album or artist changes) emits its own family
        Database.songArtistMapTable.insertIgnore(SongArtistMap(songId = "s-1", artistId = "a-1"))
        assertEquals(listOf(listOf("songs"), listOf("artists")), notified)

        Database.songAlbumMapTable.upsert(listOf(SongAlbumMap(songId = "s-1", albumId = "al-1", position = null)))
        assertEquals(listOf(listOf("songs"), listOf("artists"), listOf("albums")), notified)

        // A Flow read through the same getter never notifies
        assertEquals(true, Database.songTable.exists("s-1").first())
        assertEquals(3, notified.size)
    }
}
