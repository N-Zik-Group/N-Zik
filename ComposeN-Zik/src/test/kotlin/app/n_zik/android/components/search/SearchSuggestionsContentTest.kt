package app.n_zik.android.components.search

import io.mockk.mockk
import it.fast4x.innertube.Innertube
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchSuggestionsContentTest {
    private val empty = Innertube.SearchSuggestions(emptyList(), null, null, null, null, null)

    @Test
    fun `empty response has no heading but queries and recommended items do`() {
        assertFalse(empty.hasContent())
        assertTrue(empty.copy(queries = listOf("query")).hasContent())
        assertTrue(empty.copy(recommendedSong = mockk()).hasContent())
        assertTrue(empty.copy(recommendedAlbum = mockk()).hasContent())
        assertTrue(empty.copy(recommendedArtist = mockk()).hasContent())
        assertTrue(empty.copy(recommendedPlaylist = mockk()).hasContent())
        assertTrue(empty.copy(recommendedVideo = mockk()).hasContent())
    }
}
