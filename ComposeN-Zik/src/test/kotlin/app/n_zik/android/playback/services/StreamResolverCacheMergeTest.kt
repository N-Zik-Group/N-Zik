package app.n_zik.android.playback.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for [mergePageValue] — the single merge point of the artist and
 * album page caches (the upserts in [upsertSongInfo] and
 * `fetchAndSaveAlbumSongs`). Pure function, so no mocks are needed.
 *
 * Pins the I/O matrix of the "caches empty page no clobber" spec:
 * a fetched null/blank value is "no data" (the stored value survives a page
 * without a music header), while a non-blank fetch still wins over an
 * unmodified stored value, and a `modified:` stored value is always kept.
 */
class StreamResolverCacheMergeTest {

    @Test
    fun `null fetched artist name keeps the stored value`() {
        // Device scenario 2026-09-27 (video 5_rs12ThNLc, artist
        // UCa7ao1c-NUd8zB9kHlIErOw « Strike44 »): classic channel page without
        // a music header -> artistPage.name == null; the stored name must
        // survive instead of being clobbered to NULL.
        assertEquals("Strike44", mergePageValue("Strike44", null))
    }

    @Test
    fun `blank fetched artist name keeps the stored value`() {
        assertEquals("Strike44", mergePageValue("Strike44", ""))
        assertEquals("Strike44", mergePageValue("Strike44", "   "))
    }

    @Test
    fun `null fetched thumbnail keeps the stored value`() {
        // Same empty page: no musicImmersiveHeaderRenderer -> thumbnail null.
        assertEquals("https://stored/thumb", mergePageValue("https://stored/thumb", null))
    }

    @Test
    fun `non blank fetched value wins over an unmodified stored value`() {
        // NORMAL_MUSIC row: a real music page overwrites the stored name.
        assertEquals("Tanger", mergePageValue("tanger", "Tanger"))
    }

    @Test
    fun `modified stored value survives a non blank fetch`() {
        // MODIFIED_VALUE row: the manual rename is authoritative.
        assertEquals("modified:X", mergePageValue("modified:X", "Y"))
    }

    @Test
    fun `modified stored value survives a blank fetch`() {
        assertEquals("modified:X", mergePageValue("modified:X", null))
        assertEquals("modified:X", mergePageValue("modified:X", ""))
    }

    @Test
    fun `wiped stored name is restored from a non empty page`() {
        // RECOVERY row: the guard must not block a real page from repairing
        // a row whose name was previously erased.
        assertEquals("Strike44", mergePageValue(null, "Strike44"))
    }

    @Test
    fun `null stored value with a blank fetch stays null`() {
        // Nothing stored, nothing fetched: no value is invented.
        assertNull(mergePageValue(null, null))
        assertNull(mergePageValue(null, ""))
    }

    @Test
    fun `album empty page keeps stored title thumbnail authors and year`() {
        // EMPTY_PAGE_ALBUM row: albumPage.title null + blank authors + no
        // thumbnail + no year on the fetched page.
        assertEquals("Stored title", mergePageValue("Stored title", null))
        assertEquals("https://stored/thumb", mergePageValue("https://stored/thumb", null))
        assertEquals("Stored artists", mergePageValue("Stored artists", ""))
        assertEquals("2020", mergePageValue("2020", null))
        assertEquals("https://stored/share", mergePageValue("https://stored/share", null))
    }

    @Test
    fun `album non empty page still overwrites unmodified stored fields`() {
        assertEquals("Fetched title", mergePageValue("Stored title", "Fetched title"))
        assertEquals("Fetched artists", mergePageValue("Stored artists", "Fetched artists"))
        assertEquals("2021", mergePageValue("2020", "2021"))
    }
}
