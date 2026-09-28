package app.n_zik.android.utils

import app.it.fast4x.rimusic.enums.Languages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppLanguageLocaleTest {

    @Test
    fun `system language should map to an empty locale list`() {
        val locales = localeListForAppLanguage(Languages.System)
        assertTrue(locales.isEmpty)
    }

    @Test
    fun `french should map to the fr tag`() {
        assertSingleTag(Languages.French, "fr")
    }

    @Test
    fun `chinese simplified should map to the zh-CN tag`() {
        assertSingleTag(Languages.ChineseSimplified, "zh-CN")
    }

    @Test
    fun `portuguese brazilian should map to the pt-BR tag`() {
        assertSingleTag(Languages.PortugueseBrazilian, "pt-BR")
    }

    private fun assertSingleTag(language: Languages, expectedTag: String) {
        val locales = localeListForAppLanguage(language)
        assertEquals(1, locales.size())
        val first = checkNotNull(locales.get(0)) { "expected exactly one locale for $language" }
        assertEquals(expectedTag, first.toLanguageTag())
    }

    @Test
    fun `every non-system language should map to a non-empty locale list`() {
        Languages.values().filter { it != Languages.System }.forEach { language ->
            val locales = localeListForAppLanguage(language)
            val first = checkNotNull(locales.get(0)) { "expected a non-empty locale list for $language" }
            assertTrue(first.toLanguageTag().isNotBlank(), "expected a non-blank tag for $language")
        }
    }
}
