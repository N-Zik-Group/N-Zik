package app.n_zik.android.components.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchHistoryFilterTest {
    private val history = listOf("Radiohead", "Daft Punk", "Radio edit")

    @Test
    fun `blank input restores all entries in recency order`() {
        assertEquals(history, filterSearchHistory(history, "  "))
    }

    @Test
    fun `input matches substrings without case or surrounding whitespace`() {
        assertEquals(listOf("Radiohead", "Radio edit"), filterSearchHistory(history, "  RADIO  "))
    }

    @Test
    fun `no match hides history without deleting stored entries`() {
        assertEquals(emptyList<String>(), filterSearchHistory(history, "missing"))
        assertEquals(3, history.size)
    }
}
