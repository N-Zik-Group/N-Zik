package app.n_zik.android.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Event
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongArtistMap
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.EventTable
import app.n_zik.android.core.database.ext.SongListeningStat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

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

    // ---- Rewind (GH-275) windowed queries ----

    @Test
    fun `eventsBetween returns only the events inside the inclusive window`() = runBlocking {
        insertSong("song_A")
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 2_999L, playTime = 1_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_000L, playTime = 2_000L)) // lower bound, inclusive
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_500L, playTime = 3_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 4_000L, playTime = 4_000L)) // upper bound, inclusive
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 4_001L, playTime = 5_000L))

        val events = eventDao.eventsBetween(from = 3_000L, to = 4_000L).first()
        assertEquals(3, events.size)
        assertTrue(events.all { it.timestamp in 3_000L..4_000L })
    }

    @Test
    fun `findSongListeningStatsBetween aggregates windowed stats and excludes disliked songs`() = runBlocking {
        insertSong("song_A")
        db.songTable.upsert(Song.makePlaceholder("song_B").copy(likedAt = -1L)) // disliked
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_100L, playTime = 30_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_200L, playTime = 30_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 2_000L, playTime = 30_000L)) // outside the window
        eventDao.insertIgnore(Event(songId = "song_B", timestamp = 3_300L, playTime = 90_000L))

        val stats: List<SongListeningStat> = eventDao.findSongListeningStatsBetween(from = 3_000L, to = 4_000L).first()
        assertEquals(listOf("song_A"), stats.map { it.song.id })
        val a = stats.first()
        assertEquals(2L, a.playCount)
        assertEquals(60_000L, a.playTimeMs)
    }

    @Test
    fun `findSongListeningStatsBetween ranks by play count then play time then id`() = runBlocking {
        insertSong("song_A")
        insertSong("song_B")
        insertSong("song_C")
        // C: most plays; A and B tie on plays → A wins on play time
        eventDao.insertIgnore(Event(songId = "song_C", timestamp = 3_100L, playTime = 10_000L))
        eventDao.insertIgnore(Event(songId = "song_C", timestamp = 3_200L, playTime = 10_000L))
        eventDao.insertIgnore(Event(songId = "song_C", timestamp = 3_300L, playTime = 10_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_400L, playTime = 50_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_500L, playTime = 50_000L))
        eventDao.insertIgnore(Event(songId = "song_B", timestamp = 3_600L, playTime = 30_000L))
        eventDao.insertIgnore(Event(songId = "song_B", timestamp = 3_700L, playTime = 30_000L))

        val stats = eventDao.findSongListeningStatsBetween(from = 3_000L, to = 4_000L).first()
        assertEquals(listOf("song_C", "song_A", "song_B"), stats.map { it.song.id })
    }

    private fun midnight(date: LocalDate): Long =
        date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `rewindYearTotals groups by local calendar year newest first`() = runBlocking {
        insertSong("song_A")
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = midnight(LocalDate.of(2025, 6, 15)), playTime = 60_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = midnight(LocalDate.of(2025, 8, 15)), playTime = 90_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = midnight(LocalDate.of(2026, 1, 15)), playTime = 30_000L))

        val totals = eventDao.rewindYearTotals().first()
        assertEquals(listOf(2026, 2025), totals.map { it.year })
        val y2025 = totals.first { it.year == 2025 }
        assertEquals(2, y2025.plays)
        assertEquals(150_000L, y2025.playTimeMs)
        val y2026 = totals.first { it.year == 2026 }
        assertEquals(1, y2026.plays)
        assertEquals(30_000L, y2026.playTimeMs)
    }

    @Test
    fun `rewindYearMonthTotals groups by year and month newest first`() = runBlocking {
        insertSong("song_A")
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = midnight(LocalDate.of(2025, 12, 20)), playTime = 60_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = midnight(LocalDate.of(2025, 6, 15)), playTime = 30_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = midnight(LocalDate.of(2025, 6, 25)), playTime = 30_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = midnight(LocalDate.of(2026, 3, 10)), playTime = 45_000L))

        val totals = eventDao.rewindYearMonthTotals().first()
        assertEquals(listOf(2026 to 3, 2025 to 12, 2025 to 6), totals.map { it.year to it.month })
        val june2025 = totals.first { it.year == 2025 && it.month == 6 }
        assertEquals(2, june2025.plays)
        assertEquals(60_000L, june2025.playTimeMs)
        val march2026 = totals.first { it.year == 2026 && it.month == 3 }
        assertEquals(1, march2026.plays)
        assertEquals(45_000L, march2026.playTimeMs)
    }

    @Test
    fun `countDistinctArtistsBetween counts distinct artists played in the window`() = runBlocking {
        insertSong("song_A")
        insertSong("song_B")
        db.artistTable.insertIgnore(Artist(id = "artist_A"))
        db.artistTable.insertIgnore(Artist(id = "artist_B"))
        db.artistTable.insertIgnore(Artist(id = "artist_disliked", dislikedAt = 0L))
        db.songArtistMapTable.insertIgnore(SongArtistMap(songId = "song_A", artistId = "artist_A"))
        db.songArtistMapTable.insertIgnore(SongArtistMap(songId = "song_B", artistId = "artist_B"))
        db.songArtistMapTable.insertIgnore(SongArtistMap(songId = "song_A", artistId = "artist_disliked"))

        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_500L, playTime = 10_000L))
        // song_B is only played outside the queried window
        eventDao.insertIgnore(Event(songId = "song_B", timestamp = 500L, playTime = 5_000L))

        val count = eventDao.countDistinctArtistsBetween(from = 3_000L, to = 4_000L).first()

        // artist_B (via song_B) is outside the window; the disliked artist is excluded
        assertEquals(1, count)
    }

    @Test
    fun `countDistinctAlbumsBetween counts distinct albums played in the window`() = runBlocking {
        insertSong("song_A")
        insertSong("song_B")
        db.albumTable.insertIgnore(Album(id = "album_A"))
        db.albumTable.insertIgnore(Album(id = "album_disliked", dislikedAt = 0L))
        db.songAlbumMapTable.map(songId = "song_A", albumId = "album_A")
        db.songAlbumMapTable.map(songId = "song_A", albumId = "album_disliked")
        db.songAlbumMapTable.map(songId = "song_B", albumId = "album_A")

        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_500L, playTime = 10_000L))
        eventDao.insertIgnore(Event(songId = "song_B", timestamp = 500L, playTime = 5_000L))

        val count = eventDao.countDistinctAlbumsBetween(from = 3_000L, to = 4_000L).first()

        // song_B's only play is outside the window; the disliked album is excluded
        assertEquals(1, count)
    }

    @Test
    fun `countDistinctPlaylistsBetween counts distinct playlists played in the window`() = runBlocking {
        insertSong("song_A")
        insertSong("song_B")
        db.playlistTable.insertIgnore(Playlist(id = 1L, name = "P1"))
        db.songPlaylistMapTable.map(songId = "song_A", playlistId = 1L)
        db.songPlaylistMapTable.map(songId = "song_B", playlistId = 1L)

        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_500L, playTime = 10_000L))
        eventDao.insertIgnore(Event(songId = "song_B", timestamp = 500L, playTime = 5_000L))

        val count = eventDao.countDistinctPlaylistsBetween(from = 3_000L, to = 4_000L).first()

        // song_B's only play is outside the window
        assertEquals(1, count)
    }

    // ---- Rewind (GH-275, re-review) windowed stats queries ----

    @Test
    fun `findArtistListeningStatsBetween aggregates windowed stats and excludes disliked artists`() = runBlocking {
        insertSong("song_A")
        insertSong("song_B")
        db.artistTable.insertIgnore(Artist(id = "artist_A"))
        db.artistTable.insertIgnore(Artist(id = "artist_B", dislikedAt = 0L))
        db.songArtistMapTable.insertIgnore(SongArtistMap(songId = "song_A", artistId = "artist_A"))
        db.songArtistMapTable.insertIgnore(SongArtistMap(songId = "song_B", artistId = "artist_B"))

        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_100L, playTime = 60_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_200L, playTime = 90_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 2_000L, playTime = 30_000L)) // outside the window
        eventDao.insertIgnore(Event(songId = "song_B", timestamp = 3_300L, playTime = 900_000L)) // disliked artist

        val stats = eventDao.findArtistListeningStatsBetween(from = 3_000L, to = 4_000L).first()

        assertEquals("disliked artists must not rank in the windowed stats", listOf("artist_A"), stats.map { it.artist.id })
        assertEquals(150_000L, stats.first().playTimeMs)
        assertEquals(1L, stats.first().songCount)
    }

    @Test
    fun `findArtistListeningStatsBetween ranks by play time then song count and honors limit`() = runBlocking {
        insertSong("song_A")
        insertSong("song_B")
        insertSong("song_C")
        insertSong("song_D")
        db.artistTable.insertIgnore(Artist(id = "artist_A"))
        db.artistTable.insertIgnore(Artist(id = "artist_B"))
        db.artistTable.insertIgnore(Artist(id = "artist_C"))
        db.songArtistMapTable.insertIgnore(SongArtistMap(songId = "song_A", artistId = "artist_A"))
        db.songArtistMapTable.insertIgnore(SongArtistMap(songId = "song_B", artistId = "artist_B"))
        db.songArtistMapTable.insertIgnore(SongArtistMap(songId = "song_D", artistId = "artist_B"))
        db.songArtistMapTable.insertIgnore(SongArtistMap(songId = "song_C", artistId = "artist_C"))

        // artist_B ties artist_A on total time but with two distinct songs → ranks above
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_100L, playTime = 120_000L))
        eventDao.insertIgnore(Event(songId = "song_B", timestamp = 3_200L, playTime = 60_000L))
        eventDao.insertIgnore(Event(songId = "song_D", timestamp = 3_300L, playTime = 60_000L))
        eventDao.insertIgnore(Event(songId = "song_C", timestamp = 3_400L, playTime = 30_000L))

        val stats = eventDao.findArtistListeningStatsBetween(from = 3_000L, to = 4_000L, limit = 2).first()

        assertEquals(listOf("artist_B", "artist_A"), stats.map { it.artist.id })
    }

    @Test
    fun `findAlbumListeningStatsBetween aggregates windowed stats and excludes disliked albums`() = runBlocking {
        insertSong("song_A")
        insertSong("song_B")
        db.albumTable.insertIgnore(Album(id = "album_A"))
        db.albumTable.insertIgnore(Album(id = "album_B", dislikedAt = 0L))
        db.songAlbumMapTable.map(songId = "song_A", albumId = "album_A")
        db.songAlbumMapTable.map(songId = "song_B", albumId = "album_B")

        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_100L, playTime = 60_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_200L, playTime = 90_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 2_000L, playTime = 30_000L)) // outside the window
        eventDao.insertIgnore(Event(songId = "song_B", timestamp = 3_300L, playTime = 900_000L)) // disliked album

        val stats = eventDao.findAlbumListeningStatsBetween(from = 3_000L, to = 4_000L).first()

        assertEquals("disliked albums must not rank in the windowed stats", listOf("album_A"), stats.map { it.album.id })
        assertEquals(150_000L, stats.first().playTimeMs)
        assertEquals(1L, stats.first().songCount)
    }

    @Test
    fun `findPlaylistListeningStatsBetween aggregates windowed stats and ranks by play time`() = runBlocking {
        insertSong("song_A")
        insertSong("song_B")
        db.playlistTable.insertIgnore(Playlist(id = 1L, name = "P_A"))
        db.playlistTable.insertIgnore(Playlist(id = 2L, name = "P_B"))
        db.songPlaylistMapTable.map(songId = "song_A", playlistId = 1L)
        db.songPlaylistMapTable.map(songId = "song_A", playlistId = 2L)
        db.songPlaylistMapTable.map(songId = "song_B", playlistId = 2L)

        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_100L, playTime = 60_000L))
        eventDao.insertIgnore(Event(songId = "song_A", timestamp = 3_200L, playTime = 90_000L)) // in both playlists
        eventDao.insertIgnore(Event(songId = "song_B", timestamp = 3_300L, playTime = 30_000L)) // playlist 2 only
        eventDao.insertIgnore(Event(songId = "song_B", timestamp = 2_000L, playTime = 30_000L)) // outside the window

        val stats = eventDao.findPlaylistListeningStatsBetween(from = 3_000L, to = 4_000L).first()

        // P_B: 60s + 90s + 30s = 180s ; P_A: 60s + 90s = 150s
        assertEquals(listOf(2L, 1L), stats.map { it.playlist.playlist.id })
        assertEquals(180_000L, stats.first().playTimeMs)
    }
}
