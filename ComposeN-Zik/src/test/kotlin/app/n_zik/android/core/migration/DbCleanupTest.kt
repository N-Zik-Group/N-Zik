package app.n_zik.android.core.migration

import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongArtistMap
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.SongArtistMapTable
import app.n_zik.android.core.database.SongTable
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the polluted artist↔song link cleanup ([DbCleanup]):
 * stale = artist name not present in the song's stored artist list; custom
 * (`modified:`) names are untouchable; the run is idempotent and re-runs on
 * every launch.
 */
class DbCleanupTest {

    private val songTable = mockk<SongTable>(relaxed = true)
    private val artistTable = mockk<ArtistTable>(relaxed = true)
    private val mapTable = mockk<SongArtistMapTable>(relaxed = true)

    private fun song(id: String, artistsText: String?) =
        Song(id = id, title = "Song $id", artistsText = artistsText, durationText = null, thumbnailUrl = null)

    // ---- decision rule (pure) ----

    @Test
    fun artistNotInSongListIsSuspicious() {
        assertTrue(DbCleanup.isSuspiciousArtistLink("E.T", "Tanchiky, siromaru"))
    }

    @Test
    fun artistInSongListIsNotSuspicious() {
        assertFalse(DbCleanup.isSuspiciousArtistLink("Tanchiky", "Tanchiky, siromaru"))
        assertFalse(DbCleanup.isSuspiciousArtistLink("siromaru", "Tanchiky, siromaru"))
    }

    @Test
    fun comparisonIsCaseInsensitive() {
        assertFalse(DbCleanup.isSuspiciousArtistLink("tanchiky", "Tanchiky, siromaru"))
    }

    @Test
    fun artistNameCarryingDelimiterMatchesRawList() {
        // "COOL&CREATE" contains the "&" delimiter: the whole-phrase match must
        // recognize the exact name as one phrase, not break it into parts
        // (device capture 2026-09-26: the link was removed although the names were identical)
        assertFalse(DbCleanup.isSuspiciousArtistLink("COOL&CREATE", "COOL&CREATE"))
        assertFalse(DbCleanup.isSuspiciousArtistLink("cool&create", "COOL&CREATE"))
    }

    @Test
    fun phraseMatchOnlyShieldsWholePhrases() {
        // Neither a whole-phrase match nor a contained fragment -> still stale
        assertTrue(DbCleanup.isSuspiciousArtistLink("CREATE BAND", "COOL&CREATE"))
        assertTrue(DbCleanup.isSuspiciousArtistLink("E.T", "Tanchiky, siromaru"))
    }

    @Test
    fun artistNameContainingConjunctionSubstringIsKept() {
        // The old split matched the localized conjunction word ("and") as a substring
        // inside "Grand", destroying "Grand Corps Malade" into junk parts and removing
        // the link on every launch (device capture 2026-09-26 12:02). The whole-phrase
        // match keeps it, in both positions of the list.
        assertFalse(DbCleanup.isSuspiciousArtistLink("Grand Corps Malade", "Grand Corps Malade, Kimberose"))
        assertFalse(DbCleanup.isSuspiciousArtistLink("Kimberose", "Grand Corps Malade, Kimberose"))
    }

    @Test
    fun conjunctionFragmentsAreStillRemoved() {
        // The junk parts the old split produced out of "Grand Corps Malade" must
        // still be judged stale - the phrase ends are pinned against letters.
        assertTrue(DbCleanup.isSuspiciousArtistLink("G", "Grand Corps Malade, Kimberose"))
        assertTrue(DbCleanup.isSuspiciousArtistLink("r", "Grand Corps Malade, Kimberose"))
        assertTrue(DbCleanup.isSuspiciousArtistLink("and", "Grand Corps Malade, Kimberose"))
    }

    @Test
    fun cjkNamesMatchWithoutWordBoundaries() {
        // CJK characters carry no \b word boundary in Java regex; the letter-only
        // anchors let CJK names match at string start and before a space.
        assertFalse(DbCleanup.isSuspiciousArtistLink("雨良", "雨良 Amala"))
        assertFalse(DbCleanup.isSuspiciousArtistLink("Amala", "雨良 Amala"))
    }

    @Test
    fun nameThatIsAPrefixOfAnotherNameIsStillRemoved() {
        // The trailing letter pin stops partial names from matching inside longer names.
        assertTrue(DbCleanup.isSuspiciousArtistLink("Mik", "MIKA"))
        assertTrue(DbCleanup.isSuspiciousArtistLink("Tanchik", "Tanchiky, siromaru"))
    }

    @Test
    fun accentedNameMatchesWholeAndRejectsFragment() {
        // \p{L} anchors cover accented letters: the full name matches, but a
        // prefix ending before an accented letter is still a fragment.
        assertFalse(DbCleanup.isSuspiciousArtistLink("Grégoire", "Grégoire, X"))
        assertTrue(DbCleanup.isSuspiciousArtistLink("gr", "Grégoire, X"))
    }

    @Test
    fun customArtistNameIsNeverTouched() {
        assertFalse(DbCleanup.isSuspiciousArtistLink("modified:Whatever", "Tanchiky, siromaru"))
    }

    @Test
    fun customSongArtistTextIsNeverTouched() {
        assertFalse(DbCleanup.isSuspiciousArtistLink("E.T", "modified:My Custom List"))
    }

    @Test
    fun blankSongArtistTextKeepsEveryLink() {
        assertFalse(DbCleanup.isSuspiciousArtistLink("E.T", ""))
        assertFalse(DbCleanup.isSuspiciousArtistLink("E.T", null))
    }

    @Test
    fun blankArtistNameKeepsEveryLink() {
        assertFalse(DbCleanup.isSuspiciousArtistLink(null, "Tanchiky, siromaru"))
        assertFalse(DbCleanup.isSuspiciousArtistLink("", "Tanchiky, siromaru"))
    }

    // ---- core (orchestration) ----

    @Test
    fun emptyDatabaseRemovesNothing() = runBlocking {
        every { mapTable.allPairsDirect() } returns emptyList()

        val removed = DbCleanup.runClean(songTable, artistTable, mapTable)

        assertEquals(0, removed)
        verify(exactly = 0) { songTable.all() }
        verify(exactly = 0) { mapTable.deletePairDirect(any(), any()) }
    }

    @Test
    fun cleanDatabaseRemovesNothing() = runBlocking {
        every { mapTable.allPairsDirect() } returns listOf(
            SongArtistMap("s1", "UC_T"), SongArtistMap("s1", "UC_S")
        )
        every { songTable.all() } returns flowOf(listOf(song("s1", "Tanchiky, siromaru")))
        every { artistTable.findByIdDirect("UC_T") } returns Artist(id = "UC_T", name = "Tanchiky")
        every { artistTable.findByIdDirect("UC_S") } returns Artist(id = "UC_S", name = "siromaru")

        val removed = DbCleanup.runClean(songTable, artistTable, mapTable)

        assertEquals(0, removed)
        verify(exactly = 0) { mapTable.deletePairDirect(any(), any()) }
    }

    @Test
    fun staleLinkIsRemovedWhileRealOnesAreKept() = runBlocking {
        every { mapTable.allPairsDirect() } returns listOf(
            SongArtistMap("s1", "UC_T"), SongArtistMap("s1", "UC_ET")
        )
        every { songTable.all() } returns flowOf(listOf(song("s1", "Tanchiky, siromaru")))
        every { artistTable.findByIdDirect("UC_T") } returns Artist(id = "UC_T", name = "Tanchiky")
        every { artistTable.findByIdDirect("UC_ET") } returns Artist(id = "UC_ET", name = "E.T")

        val removed = DbCleanup.runClean(songTable, artistTable, mapTable)

        assertEquals(1, removed)
        verify(exactly = 1) { mapTable.deletePairDirect("s1", "UC_ET") }
        verify(exactly = 0) { mapTable.deletePairDirect("s1", "UC_T") }
    }

    @Test
    fun linkOfSongMissingFromSongTableIsKept() = runBlocking {
        every { mapTable.allPairsDirect() } returns listOf(SongArtistMap("s2", "UC_X"))
        every { songTable.all() } returns flowOf(listOf(song("s1", "Tanchiky")))

        val removed = DbCleanup.runClean(songTable, artistTable, mapTable)

        assertEquals(0, removed)
        verify(exactly = 0) { mapTable.deletePairDirect(any(), any()) }
    }

    // ---- diagnostics logging (I/O matrix rows: sweep with removals / clean sweep) ----

    private class DbCleanupCaptureTree(val captured: MutableList<String>) : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (tag == "DbCleanup") captured += message
        }
    }

    @Test
    fun removedStaleLinkLogsPerPairDetail() {
        val captured = mutableListOf<String>()
        val captureTree = DbCleanupCaptureTree(captured)
        Timber.plant(captureTree)
        try {
            runBlocking {
                every { mapTable.allPairsDirect() } returns listOf(
                    SongArtistMap("s1", "UC_T"), SongArtistMap("s1", "UC_ET")
                )
                every { songTable.all() } returns flowOf(listOf(song("s1", "Tanchiky, siromaru")))
                every { artistTable.findByIdDirect("UC_T") } returns Artist(id = "UC_T", name = "Tanchiky")
                every { artistTable.findByIdDirect("UC_ET") } returns Artist(id = "UC_ET", name = "E.T")

                val removed = DbCleanup.runClean(songTable, artistTable, mapTable)

                assertEquals(1, removed)
                // per-pair detail: song id + title, removed artist name, stored artistsText
                assertTrue(
                    "expected per-pair detail log, got: $captured",
                    captured.any {
                        it.startsWith("stale link removed: song=s1") &&
                            it.contains("\"Song s1\"") &&
                            it.contains("artist=\"E.T\"") &&
                            it.contains("artistsText=\"Tanchiky, siromaru\"")
                    }
                )
                // existing summary line still present
                assertTrue(captured.any { it == "Artist link cleanup: removed 1 stale link(s)" })
            }
        } finally {
            Timber.uproot(captureTree)
        }
    }

    @Test
    fun cleanDatabaseLogsSummaryOnlyWithoutPerPairLines() {
        val captured = mutableListOf<String>()
        val captureTree = DbCleanupCaptureTree(captured)
        Timber.plant(captureTree)
        try {
            runBlocking {
                every { mapTable.allPairsDirect() } returns listOf(
                    SongArtistMap("s1", "UC_T"), SongArtistMap("s1", "UC_S")
                )
                every { songTable.all() } returns flowOf(listOf(song("s1", "Tanchiky, siromaru")))
                every { artistTable.findByIdDirect("UC_T") } returns Artist(id = "UC_T", name = "Tanchiky")
                every { artistTable.findByIdDirect("UC_S") } returns Artist(id = "UC_S", name = "siromaru")

                val removed = DbCleanup.runClean(songTable, artistTable, mapTable)

                assertEquals(0, removed)
                assertTrue(captured.none { it.startsWith("stale link removed:") })
                assertTrue(captured.any { it == "Artist link cleanup: removed 0 stale link(s)" })
            }
        } finally {
            Timber.uproot(captureTree)
        }
    }
}
