package app.n_zik.android.bridge.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrackMappingTest {

    @Test
    fun `durationText in m-ss and h-mm-ss converts to milliseconds`() {
        assertEquals(212_000L, TrackMapping.durationTextToMs("3:32"))
        assertEquals(212_000L, TrackMapping.durationTextToMs("03:32"))
        assertEquals(3_723_000L, TrackMapping.durationTextToMs("1:02:03"))
        assertEquals(4_500_000L, TrackMapping.durationTextToMs("75:00"))
    }

    @Test
    fun `missing, malformed or zero durationText is null`() {
        assertNull(TrackMapping.durationTextToMs(null))
        assertNull(TrackMapping.durationTextToMs(""))
        assertNull(TrackMapping.durationTextToMs("abc"))
        assertNull(TrackMapping.durationTextToMs("212"))
        assertNull(TrackMapping.durationTextToMs("1:75"))
        assertNull(TrackMapping.durationTextToMs("1:61:00"))
        assertNull(TrackMapping.durationTextToMs("-1:00"))
        assertNull(TrackMapping.durationTextToMs("00:00"))
    }

    @Test
    fun `local prefix gives a local track that is never reported downloaded`() {
        val track = TrackMapping.track(
            mediaId = "local:42",
            title = "Song",
            artist = "Artist",
            hasArtwork = false,
            durationText = "2:00",
            isLiked = true,
            isDownloaded = true,
        )

        assertEquals("local:42", track.id)
        assertEquals(TrackSource.LOCAL, track.source)
        assertFalse(track.isDownloaded)
        assertTrue(track.isLiked)
        assertEquals(120_000L, track.durationMs)
    }

    @Test
    fun `online track keeps its flags and never has a null title`() {
        val track = TrackMapping.track(
            mediaId = "dQw4w9WgXcQ",
            title = null,
            artist = "null",
            hasArtwork = true,
            durationText = null,
            isLiked = false,
            isDownloaded = true,
        )

        assertEquals("dQw4w9WgXcQ", track.id)
        assertEquals(TrackSource.ONLINE, track.source)
        assertEquals("", track.title)
        assertNull(track.artists)
        assertNull(track.durationMs)
        assertTrue(track.isDownloaded)
        assertTrue(track.hasArtwork)
    }

    @Test
    fun `media id path prefix is dropped like MediaItem asSong`() {
        assertEquals("dQw4w9WgXcQ", TrackMapping.trackIdOf("prefix/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", TrackMapping.trackIdOf("prefix/dQw4w9WgXcQ/"))
    }

    @Test
    fun `the player's duration fills a missing durationText, the text wins otherwise`() {
        fun track(durationText: String?, playerDurationMs: Long?) = TrackMapping.track(
            mediaId = "aaaaaaaaaaa", title = "T", artist = "A", hasArtwork = false, durationText = durationText,
            isLiked = false, isDownloaded = false, playerDurationMs = playerDurationMs,
        )

        assertEquals(222_000L, track(durationText = null, playerDurationMs = 222_000L).durationMs)
        assertEquals(222_000L, track(durationText = "00:00", playerDurationMs = 222_000L).durationMs)
        assertEquals(185_000L, track(durationText = "3:05", playerDurationMs = 222_000L).durationMs)
        assertEquals(null, track(durationText = null, playerDurationMs = null).durationMs)
        assertEquals(null, track(durationText = null, playerDurationMs = 0L).durationMs)
    }
}