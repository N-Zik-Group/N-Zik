package app.n_zik.android.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Event
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.EventTable
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
 * Tests Room DAO [EventTable] using Robolectric (JUnit 4 runner) with an in-memory database.
 *
 * Regression tests for issue #786: statistics header aggregates must reflect the full
 * period of listening data and must not be limited by the "Max number of items" setting.
 *
 * Note: Event has a FK to Song → we must insert Song placeholders before inserting events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EventTableTest {

    private lateinit var db: DatabaseInitializer
    private lateinit var eventDao: EventTable

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        eventDao = db.eventTable
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun insertSong(id: String) {
        db.songTable.upsert(Song.makePlaceholder(id))
    }

    @Test
    fun `countDistinctSongsPlayedBetween counts distinct songs in the period only`() = runBlocking {
        insertSong("song_A")
        insertSong("song_B")
        insertSong("song_C")

        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_500L, playTime = 10_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_600L, playTime = 20_000L))
        eventDao.insertIgnore(Event(songId = "song_B", timestamp = 3_700L, playTime = 15_000L))
        // Outside the queried period [3_000, 4_000]
        eventDao.insertIgnore(Event(songId = "song_C", timestamp = 500L, playTime = 5_000L))

        val count = eventDao.countDistinctSongsPlayedBetween(from = 3_000L, to = 4_000L).first()
        // song_C is excluded even though it was played
        assertEquals(2, count)
    }

    @Test
    fun `countDistinctSongsPlayedBetween counts distinct songs not total events`() = runBlocking {
        insertSong("song_A")
        insertSong("song_B")

        // 5 events across 2 distinct songs, all inside the period
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 1_000L, playTime = 10_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 1_100L, playTime = 10_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 1_200L, playTime = 10_000L))
        eventDao.insertIgnore(Event(songId = "song_B", timestamp = 1_300L, playTime = 10_000L))
        eventDao.insertIgnore(Event(songId = "song_B", timestamp = 1_400L, playTime = 10_000L))

        val count = eventDao.countDistinctSongsPlayedBetween(from = 0L, to = 2_000L).first()
        assertEquals(2, count)
    }

    @Test
    fun `countDistinctSongsPlayedBetween returns zero when no events in the period`() = runBlocking {
        insertSong("song_A")
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 100L, playTime = 10_000L))

        val count = eventDao.countDistinctSongsPlayedBetween(from = 1_000L, to = 2_000L).first()
        assertEquals(0, count)
    }

    @Test
    fun `getTotalPlayTimeBetween sums all events without any limit`() = runBlocking {
        // More songs than any "Max number of items" setting could cover
        val from = 1_000L
        val to = 2_000L
        repeat(30) { i ->
            insertSong("song_$i")
            eventDao.insertIgnore(Event(songId = "song_$i", timestamp = from + i, playTime = 1_000L))
        }

        val total = eventDao.getTotalPlayTimeBetween(from = from, to = to).first()
        assertEquals(30_000L, total)
    }
}
