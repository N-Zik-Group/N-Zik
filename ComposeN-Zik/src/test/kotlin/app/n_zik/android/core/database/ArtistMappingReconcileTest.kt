package app.n_zik.android.core.database

import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.ArtistConjunctions
import it.fast4x.innertube.models.NavigationEndpoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArtistMappingReconcileTest {

    private val conjunctionsBackup = ArtistConjunctions.conjunctions

    private fun author(name: String?, browseId: String? = null) =
        Innertube.Info<NavigationEndpoint.Endpoint.Browse>(
            name = name,
            endpoint = browseId?.let { NavigationEndpoint.Endpoint.Browse(browseId = it) }
        )

    @Before
    fun setup() {
        // Deterministic regardless of the locale wiring done at app startup
        ArtistConjunctions.conjunctions = listOf("and")
    }

    @After
    fun tearDown() {
        ArtistConjunctions.conjunctions = conjunctionsBackup
    }

    @Test
    fun completeListWithBrowseIdsIsReconcilable() {
        val authors = listOf(author("Tanchiky", "UC_A"), author("siromaru", "UC_B"))
        assertTrue(ArtistMappingReconcile.isCompleteAuthorList(listOf("Tanchiky", "siromaru"), authors))
    }

    @Test
    fun emptyNamesNeverReconciles() {
        assertFalse(ArtistMappingReconcile.isCompleteAuthorList(emptyList(), listOf(author("A", "UC_A"))))
    }

    @Test
    fun nullAuthorsNeverReconciles() {
        assertFalse(ArtistMappingReconcile.isCompleteAuthorList(listOf("A"), null))
    }

    @Test
    fun namesOnlyListWithoutBrowseIdsNeverReconciles() {
        val authors = listOf(author("A"), author("B"))
        assertFalse(ArtistMappingReconcile.isCompleteAuthorList(listOf("A", "B"), authors))
    }

    @Test
    fun partialListWithOneMissingBrowseIdNeverReconciles() {
        val authors = listOf(author("A", "UC_A"), author("B"))
        assertFalse(ArtistMappingReconcile.isCompleteAuthorList(listOf("A", "B"), authors))
    }

    @Test
    fun nameNotPresentInAuthorsNeverReconciles() {
        val authors = listOf(author("A", "UC_A"))
        assertFalse(ArtistMappingReconcile.isCompleteAuthorList(listOf("A", "B"), authors))
    }

    // region parseAuthorEntries

    @Test
    fun parseAuthorEntriesTrimsAndNormalizesNbsp() {
        // leading/trailing ASCII whitespace is trimmed, the inner NBSP is
        // normalized to a plain space
        val parsed = ArtistMappingReconcile.parseAuthorEntries(
            listOf(author("A\u00a0B", "UC_A"), author(" \tC\u00a0D ", "UC_B"))
        )
        assertEquals(listOf("A B" to "UC_A", "C D" to "UC_B"), parsed)
    }

    @Test
    fun parseAuthorEntriesSkipsSeparatorOnlyEntries() {
        val parsed = ArtistMappingReconcile.parseAuthorEntries(
            listOf(
                author("&", "UC_SEP1"),
                author(",", "UC_SEP2"),
                author("&&", "UC_SEP3"),
                author("A", "UC_A")
            )
        )
        assertEquals(listOf("A" to "UC_A"), parsed)
    }

    @Test
    fun parseAuthorEntriesSkipsStandaloneConjunctions() {
        val parsed = ArtistMappingReconcile.parseAuthorEntries(
            listOf(author("and", "UC_C1"), author("And", "UC_C2"), author("A", "UC_A"))
        )
        assertEquals(listOf("A" to "UC_A"), parsed)
    }

    @Test
    fun parseAuthorEntriesSkipsBlankAndMissingNames() {
        val parsed = ArtistMappingReconcile.parseAuthorEntries(
            listOf(
                author("   ", "UC_BLANK"),
                author(null, "UC_NULL"),
                author("A", "UC_A")
            )
        )
        assertEquals(listOf("A" to "UC_A"), parsed)
    }

    @Test
    fun parseAuthorEntriesKeepsNullBrowseIdWhenTheEntryHasNoEndpoint() {
        val parsed = ArtistMappingReconcile.parseAuthorEntries(listOf(author("LocalArtist")))
        assertEquals(listOf("LocalArtist" to null), parsed)
    }

    @Test
    fun parseAuthorEntriesKeepsEntryNamesWhole() {
        // one author entry = one artist: the entry name is never split
        val parsed = ArtistMappingReconcile.parseAuthorEntries(
            listOf(author("Bigflo & OLi", "UC_BIGFLO"))
        )
        assertEquals(listOf("Bigflo & OLi" to "UC_BIGFLO"), parsed)
    }

    @Test
    fun parseAuthorEntriesHandlesNullAuthors() {
        // explicit type: Kotlin cannot infer emptyList's T from the platform-typed
        // JUnit assertEquals(Object, Object) parameter
        assertEquals(
            emptyList<Pair<String, String?>>(),
            ArtistMappingReconcile.parseAuthorEntries(null)
        )
    }

    // endregion
}
