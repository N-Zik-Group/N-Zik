package app.n_zik.android.components.search

import app.n_zik.android.components.settings.common.filterSettingsSearchHistory
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
    fun `history matching folds accents and repeated spaces like settings search`() {
        assertEquals(listOf("Écran  d’accueil"), filterSearchHistory(listOf("Écran  d’accueil", "Radio"), "  ecran d’accueil "))
    }

    @Test
    fun `matching preserves Bengali vowel signs and folds dotted and dotless Turkish I`() {
        assertEquals(listOf("কাল"), filterSearchHistory(listOf("কাল", "কল"), "কাল"))
        assertEquals(listOf("Işık"), filterSearchHistory(listOf("Işık", "Ses"), "ışık"))
        assertEquals(listOf("Işık"), filterSearchHistory(listOf("Işık", "Ses"), "ISIK"))
    }

    @Test
    fun `matching folds Greek tonos and Russian yo without folding short i`() {
        assertEquals(listOf("Ήχος"), filterSearchHistory(listOf("Ήχος", "Εφέ"), "ηχος"))
        assertEquals(listOf("Ёлка"), filterSearchHistory(listOf("Ёлка", "сосна"), "елка"))
        assertEquals(listOf("бой"), filterSearchHistory(listOf("бой", "бои"), "бой"))
        assertEquals(listOf("бои"), filterSearchHistory(listOf("бой", "бои"), "бои"))
    }

    @Test
    fun `matching treats typographic apostrophes and nonbreaking spaces like keyboard punctuation`() {
        val titles = listOf("Écran d’accueil\u00a0:\u202fInterface", "Памʼять", "Écran de verrouillage")
        assertEquals(listOf(titles[0]), filterSearchHistory(titles, "  ecran d'accueil : interface  "))
        assertEquals(listOf(titles[1]), filterSearchHistory(titles, "Пам'ять"))
    }

    @Test
    fun `only settings history treats settings separators as interchangeable spaces`() {
        val settings = listOf("Maintenance & Extensions", "Material › Rendering", "AI / Quick Picks")
        assertEquals(listOf(settings[0]), filterSettingsSearchHistory(settings, "maintenance extensions"))
        assertEquals(listOf(settings[1]), filterSettingsSearchHistory(settings, "material rendering"))
        assertEquals(listOf(settings[2]), filterSettingsSearchHistory(settings, "ai quick picks"))
        assertEquals(emptyList<String>(), filterSearchHistory(settings, "maintenance extensions"))
    }

    @Test
    fun `no match hides history without deleting stored entries`() {
        assertEquals(emptyList<String>(), filterSearchHistory(history, "missing"))
        assertEquals(3, history.size)
    }
}
