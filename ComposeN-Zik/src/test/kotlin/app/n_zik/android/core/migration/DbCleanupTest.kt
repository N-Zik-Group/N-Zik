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
}
