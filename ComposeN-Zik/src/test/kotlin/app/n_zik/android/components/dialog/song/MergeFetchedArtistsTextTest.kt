package app.n_zik.android.components.dialog.song

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-function tests for [mergeFetchedArtistsText] - the Update action's
 * artistsText merge: a blank fetched value is "no data" (the stored value
 * survives), a non-blank fetch overwrites an unmodified stored value, and a
 * `modified:` stored value stays authoritative.
 */
class MergeFetchedArtistsTextTest {

    @Test
    fun blankFetchedValueKeepsTheStoredValue() {
        // The video queue fetch returns an empty author list (channel byline
        // filtered out): fetched artistsText is null or "".
        assertEquals("Stored", mergeFetchedArtistsText("song_1", "Stored", null))
        assertEquals("Stored", mergeFetchedArtistsText("song_1", "Stored", ""))
        assertEquals("Stored", mergeFetchedArtistsText("song_1", "Stored", "   "))
    }

    @Test
    fun blankStoredValueWithNonBlankFetchedTakesTheFetchedValue() {
        assertEquals("Fetched", mergeFetchedArtistsText("song_1", null, "Fetched"))
        assertEquals("Fetched", mergeFetchedArtistsText("song_1", "  ", "Fetched"))
    }

    @Test
    fun bothBlankYieldsWhateverTheContractYields() {
        // Nothing stored, nothing fetched: the contract returns the fetched
        // value through retainIfModified - null for a null fetch...
        assertNull(mergeFetchedArtistsText("song_1", null, null))
        // ...and the (blank) fetched value when the stored one is blank or
        // null (retainIfModified returns the fetched value, and the elvis
        // only rescues a null, not a blank). No data is lost either way:
        // both sides carry no artists.
        assertEquals("", mergeFetchedArtistsText("song_1", "  ", ""))
        assertEquals("", mergeFetchedArtistsText("song_1", null, ""))
    }

    @Test
    fun modifiedStoredValueIsAuthoritativeOverAnyFetch() {
        assertEquals("modified:X", mergeFetchedArtistsText("song_1", "modified:X", "Fetched"))
    }

    @Test
    fun unmodifiedStoredValueIsReplacedByNonBlankFetchedValue() {
        assertEquals("Fetched", mergeFetchedArtistsText("song_1", "Stored", "Fetched"))
    }
}
