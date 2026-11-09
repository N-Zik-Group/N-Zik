package app.n_zik.android.components.song

import app.it.fast4x.rimusic.models.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SongDisplayMetadataTest {
    private val online = Song("track", "Old title", "Old artist", "3:45", "old-cover")

    @Test
    fun `stored edits replace online metadata without losing enriched duration or statistics`() {
        val stored = online.copy(title = "Edited", artistsText = "Edited artist", durationText = null,
            thumbnailUrl = "file:///custom.jpg", likedAt = 42, totalPlayTimeMs = 900).apply { playCount = 7 }
        val actual = resolveSongDisplayMetadata(online, stored)
        assertEquals("Edited", actual.title)
        assertEquals("Edited artist", actual.artistsText)
        assertEquals("file:///custom.jpg", actual.thumbnailUrl)
        assertEquals("3:45", actual.durationText)
        assertEquals(42L, actual.likedAt)
        assertEquals(900L, actual.totalPlayTimeMs)
        assertEquals(7, actual.playCount)
    }

    @Test
    fun `reset values do not resurrect incoming artwork or artist`() {
        val reset = online.copy(title = "Canonical", artistsText = null, thumbnailUrl = null)
        val actual = resolveSongDisplayMetadata(online, reset)
        assertEquals("Canonical", actual.title)
        assertNull(actual.artistsText)
        assertNull(actual.thumbnailUrl)
    }

    @Test
    fun `a reused row cannot render a previous song while the next subscription starts`() {
        assertSame(online, resolveSongDisplayMetadata(online, online.copy(id = "other", title = "Wrong")))
        assertSame(online, resolveSongDisplayMetadata(online, null))
    }

    @Test
    fun `stored duration takes precedence over remote duration`() {
        val stored = online.copy(durationText = "4:10")
        assertSame(stored, resolveSongDisplayMetadata(online, stored))
    }
}
