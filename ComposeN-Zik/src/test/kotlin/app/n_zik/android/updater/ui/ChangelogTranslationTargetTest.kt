package app.n_zik.android.updater.ui

import app.it.fast4x.rimusic.enums.Languages
import app.n_zik.android.utils.contentTranslationLanguage
import dev.rebelonion.translator.Language
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChangelogTranslationTargetTest {
    @Test
    fun `unavailable changelog languages do not substitute another language`() {
        assertNull(Languages.Bashkir.changelogTranslationTarget)
        assertNull(Languages.Interlingua.changelogTranslationTarget)
    }

    @Test
    fun `system translation follows script and region`() {
        assertEquals(Languages.ChineseSimplified, contentTranslationLanguage(Locale.forLanguageTag("zh-Hans-TW")))
        assertEquals(Languages.ChineseTraditional, contentTranslationLanguage(Locale.forLanguageTag("zh-Hant")))
        assertEquals(Languages.PortugueseBrazilian, contentTranslationLanguage(Locale.forLanguageTag("pt-Latn-BR")))
        assertEquals(Languages.SerbianLatin, contentTranslationLanguage(Locale.forLanguageTag("sr-Latn-RS")))
        assertEquals(Languages.Hebrew, contentTranslationLanguage(Locale.forLanguageTag("he-IL")))
    }

    @Test
    fun `supported languages keep their translator target`() {
        assertEquals(Language.FRENCH, Languages.French.changelogTranslationTarget)
        assertEquals(Language.ENGLISH, Languages.English.changelogTranslationTarget)
    }
}
