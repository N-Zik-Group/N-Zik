package app.n_zik.android.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.models.Song
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.LyricsTable
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.models.Lyrics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests Room DAO [LyricsTable] using Robolectric (JUnit 4 runner) with an in-memory database.
 * Uses JUnit 4 annotations (@Before / @After) because Robolectric's @RunWith is a JUnit 4 concept.
 *
 * Note: Lyrics has a FK to Song → we must insert a Song placeholder before inserting Lyrics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LyricsTableTest {

    private lateinit var db: DatabaseInitializer
    private lateinit var lyricsDao: LyricsTable

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        lyricsDao = db.lyricsTable
    }

    @After
    fun closeDb() {
        db.close()
    }

    /** Insert a minimal Song so that the FK constraint on Lyrics is satisfied. */
    private fun insertSong(id: String) {
        db.songTable.upsert(Song.makePlaceholder(id))
    }

    @Test
    fun `upsert and retrieve 3 distinct modes for the same song`() = runBlocking {
        val mediaId = "song_123"
        insertSong(mediaId)

        lyricsDao.upsert(Lyrics(songId = mediaId, type = LyricsType.Karaoke.name, data = "karaoke data"))
        lyricsDao.upsert(Lyrics(songId = mediaId, type = LyricsType.Synced.name, data = "sync data"))
        lyricsDao.upsert(Lyrics(songId = mediaId, type = LyricsType.Unsynced.name, data = "unsync data"))

        val allLyrics = lyricsDao.findAllBySongId(mediaId).first()
        assertEquals(3, allLyrics.size)

        val karaoke = allLyrics.find { it.type == LyricsType.Karaoke.name }
        assertEquals("karaoke data", karaoke?.data)

        val sync = allLyrics.find { it.type == LyricsType.Synced.name }
        assertEquals("sync data", sync?.data)

        val unsync = allLyrics.find { it.type == LyricsType.Unsynced.name }
        assertEquals("unsync data", unsync?.data)
    }

    @Test
    fun `upsert overwrites existing data for the same songId and type`() = runBlocking {
        val mediaId = "song_123"
        insertSong(mediaId)

        lyricsDao.upsert(Lyrics(songId = mediaId, type = LyricsType.Karaoke.name, data = "initial data"))

        var lyrics = lyricsDao.findBySongIdAndType(mediaId, LyricsType.Karaoke.name).first()
        assertEquals("initial data", lyrics?.data)

        lyricsDao.upsert(Lyrics(songId = mediaId, type = LyricsType.Karaoke.name, data = "updated data"))

        lyrics = lyricsDao.findBySongIdAndType(mediaId, LyricsType.Karaoke.name).first()
        assertEquals("updated data", lyrics?.data)

        // Vérifier qu'il n'y a toujours qu'une seule ligne Karaoke
        val allLyrics = lyricsDao.findAllBySongId(mediaId).first()
        assertEquals(1, allLyrics.size)
    }

    @Test
    fun `isEdited defaults to false and survives a round trip`() = runBlocking {
        val mediaId = "song_123"
        insertSong(mediaId)

        lyricsDao.upsert(Lyrics(songId = mediaId, type = LyricsType.Karaoke.name, data = "fetched"))
        lyricsDao.upsert(Lyrics(songId = mediaId, type = LyricsType.Synced.name, data = "mine", isEdited = true))

        val karaoke = lyricsDao.findBySongIdAndType(mediaId, LyricsType.Karaoke.name).first()
        val synced = lyricsDao.findBySongIdAndType(mediaId, LyricsType.Synced.name).first()
        assertEquals(false, karaoke?.isEdited)
        assertEquals(true, synced?.isEdited)

        // The "fetch lyrics again" action rewrites the row with the default flag.
        lyricsDao.upsert(Lyrics(songId = mediaId, type = LyricsType.Synced.name, data = null))
        val reset = lyricsDao.findBySongIdAndType(mediaId, LyricsType.Synced.name).first()
        assertEquals(false, reset?.isEdited)
        assertNull(reset?.data)
    }

    @Test
    fun `lastFetchedAt defaults to null and survives a round trip`() = runBlocking {
        val mediaId = "song_123"
        insertSong(mediaId)

        lyricsDao.upsert(Lyrics(songId = mediaId, type = LyricsType.Karaoke.name, data = "fetched"))
        val unstamped = lyricsDao.findBySongIdAndType(mediaId, LyricsType.Karaoke.name).first()
        assertNull(unstamped?.lastFetchedAt)

        val stamp = 1_700_000_000_000L
        lyricsDao.upsert(Lyrics(songId = mediaId, type = LyricsType.Karaoke.name, data = "refetched", lastFetchedAt = stamp))
        val stamped = lyricsDao.findBySongIdAndType(mediaId, LyricsType.Karaoke.name).first()
        assertEquals(stamp, stamped?.lastFetchedAt)
        assertEquals("refetched", stamped?.data)

        // The "fetch lyrics again" reset rewrites the row without a stamp.
        lyricsDao.upsert(Lyrics(songId = mediaId, type = LyricsType.Karaoke.name, data = null))
        val reset = lyricsDao.findBySongIdAndType(mediaId, LyricsType.Karaoke.name).first()
        assertNull(reset?.lastFetchedAt)
        assertNull(reset?.data)
    }

    @Test
    fun `findAllBySongId does not return lyrics for other songs`() = runBlocking {
        insertSong("song_A")
        insertSong("song_B")

        lyricsDao.upsert(Lyrics(songId = "song_A", type = LyricsType.Karaoke.name, data = "A"))
        lyricsDao.upsert(Lyrics(songId = "song_B", type = LyricsType.Karaoke.name, data = "B"))

        val resultsForA = lyricsDao.findAllBySongId("song_A").first()
        assertEquals(1, resultsForA.size)
        assertEquals("A", resultsForA.first().data)
    }
}
