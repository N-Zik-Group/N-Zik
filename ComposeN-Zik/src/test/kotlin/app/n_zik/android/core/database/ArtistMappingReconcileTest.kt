package app.n_zik.android.core.database

import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistMappingReconcileTest {

    private fun author(name: String, browseId: String? = null) =
        Innertube.Info<NavigationEndpoint.Endpoint.Browse>(
            name = name,
            endpoint = browseId?.let { NavigationEndpoint.Endpoint.Browse(browseId = it) }
        )

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
}
