package app.n_zik.android.bridge.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrackMappingTest {

    @Test
    fun `like tri-state defaults to the isLiked projection and can be given explicitly`() {
        val liked = TrackMapping.track("vid", "Song", null, false, null, isLiked = true, isDownloaded = false)
        assertEquals(TrackLike.LIKED, liked.like)
        assertTrue(liked.isLiked)

        val neutral = TrackMapping.track("vid", "Song", null, false, null, isLiked = false, isDownloaded = false)
        assertEquals(TrackLike.NEUTRAL, neutral.like)
        assertFalse(neutral.isLiked)

        val disliked = TrackMapping.track("vid", "Song", null, false, null, isLiked = false, isDownloaded = false, like = TrackLike.DISLIKED)
        assertEquals(TrackLike.DISLIKED, disliked.like)
        assertFalse(disliked.isLiked)
    }

    @Test
    fun `the custom artwork flag follows the phone isCustomImage predicate`() {
        // Local custom artworks (the phone shows them with Crop)
        assertTrue(TrackMapping.isCustomArtwork("file:///storage/emulated/0/Music/cover.jpg"))
        assertTrue(TrackMapping.isCustomArtwork("https://host/app_covers/album123.png"))
        assertTrue(TrackMapping.isCustomArtwork("modified:videoId"))
        // Every other artwork (the phone shows it with FillHeight)
        assertFalse(TrackMapping.isCustomArtwork("https://i.ytimg.com/vi/vid/mqdefault.jpg"))
        assertFalse(TrackMapping.isCustomArtwork(null))
        assertFalse(TrackMapping.isCustomArtwork(""))
    }

    @Test
    fun `the track mapping carries the custom artwork flag from the artwork url`() {
        val custom = TrackMapping.track(
            "vid", "Song", null, hasArtwork = true, null,
            isLiked = false, isDownloaded = false,
            artworkUrl = "file:///cover.png",
        )
        assertTrue(custom.isCustomArtwork)

        val online = TrackMapping.track(
            "vid", "Song", null, hasArtwork = true, null,
            isLiked = false, isDownloaded = false,
            artworkUrl = "https://i.ytimg.com/vi/vid/mqdefault.jpg",
        )
        assertFalse(online.isCustomArtwork)
    }

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

    // Contract 1.3: isExplicit

    @Test
    fun `isExplicitTitle detects explicit titles, with nested prefixes or the explicit mark`() {
        assertTrue(TrackMapping.isExplicitTitle("e:Song"))
        assertTrue(TrackMapping.isExplicitTitle("pinned:e:Song"))
        assertTrue(TrackMapping.isExplicitTitle("🅴 Song"))
        assertFalse(TrackMapping.isExplicitTitle("Song"))
        assertFalse(TrackMapping.isExplicitTitle(""))
    }

    @Test
    fun `an explicit queue title is sent clean and flagged explicit`() {
        fun track(title: String) = TrackMapping.track(
            mediaId = "aaaaaaaaaaa", title = title, artist = "A", hasArtwork = false,
            durationText = null, isLiked = false, isDownloaded = false,
        )

        assertEquals("Song", track("e:Song").title)
        assertTrue(track("e:Song").isExplicit)
        assertEquals("Song", track("🅴 Song").title)
        assertTrue(track("🅴 Song").isExplicit)
        assertEquals("Song", track("Song").title)
        assertFalse(track("Song").isExplicit)
    }

    @Test
    fun `the isExplicit flag survives a non-explicit title`() {
        val track = TrackMapping.track(
            mediaId = "aaaaaaaaaaa", title = "Song", artist = "A", hasArtwork = false,
            durationText = null, isLiked = false, isDownloaded = false, isExplicit = true,
        )

        assertTrue(track.isExplicit)
        assertEquals("Song", track.title)
    }
}
