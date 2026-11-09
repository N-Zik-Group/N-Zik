package app.n_zik.android.updater.ui

import app.it.fast4x.rimusic.enums.Languages
import dev.rebelonion.translator.Language
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
    fun `supported languages keep their translator target`() {
        assertEquals(Language.FRENCH, Languages.French.changelogTranslationTarget)
        assertEquals(Language.ENGLISH, Languages.English.changelogTranslationTarget)
    }
}
