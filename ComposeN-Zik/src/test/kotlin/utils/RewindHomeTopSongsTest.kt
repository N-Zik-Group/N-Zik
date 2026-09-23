package utils

import app.it.fast4x.rimusic.models.Event
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.core.database.ext.EventWithSong
import app.n_zik.android.components.ui.screens.rewind.topSongThumbnails
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract for the rewind home page's most-played artwork lookup: [topSongThumbnails] ranks
 * songs by play count (ties broken by song id, deterministic), keeps nulls for songs without
 * artwork, and honors the limit.
 */
class RewindHomeTopSongsTest {

    private val zone = ZoneId.systemDefault()

    private fun song(id: String, thumbnail: String?): Song =
        Song(
            id = id,
            title = "Title $id",
            durationText = "3:00",
            thumbnailUrl = thumbnail
        )

    private fun event(day: LocalDate, song: Song): EventWithSong =
        EventWithSong(
            event = Event(
                songId = song.id,
                timestamp = day.atStartOfDay().atZone(zone).toInstant().toEpochMilli(),
                playTime = 180_000L
            ),
            song = song
        )

    @Test
    fun ranksByPlayCountDescending() {
        val a = song("a", "http://img/a")
        val b = song("b", "http://img/b")
        val c = song("c", "http://img/c")
        val events = listOf(
            event(LocalDate.of(2026, 1, 2), a),
            event(LocalDate.of(2026, 1, 3), b),
            event(LocalDate.of(2026, 1, 4), b),
            event(LocalDate.of(2026, 1, 5), c),
            event(LocalDate.of(2026, 1, 6), c),
            event(LocalDate.of(2026, 1, 7), c)
        )
        assertEquals(listOf("http://img/c", "http://img/b", "http://img/a"), topSongThumbnails(events, 3, zone))
    }

    @Test
    fun tiesGoToTheSmallestSongId() {
        val z = song("z", "http://img/z")
        val a = song("a", "http://img/a")
        val events = listOf(
            event(LocalDate.of(2026, 1, 1), z),
            event(LocalDate.of(2026, 1, 2), a)
        )
        assertEquals(listOf("http://img/a", "http://img/z"), topSongThumbnails(events, 4, zone))
    }

    @Test
    fun limitsToFourAndKeepsNullThumbnails() {
        val events = (1..6).map { index ->
            event(
                LocalDate.of(2026, 2, index),
                song("s$index", if (index == 2) null else "http://img/$index")
            )
        }
        val result = topSongThumbnails(events, 4, zone)
        assertEquals(4, result.size)
        assertEquals(null, result[1])
    }
}
