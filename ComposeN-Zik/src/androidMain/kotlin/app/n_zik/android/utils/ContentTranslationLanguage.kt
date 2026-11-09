package app.n_zik.android.utils

import app.it.fast4x.rimusic.enums.Languages
import dev.rebelonion.translator.Language
import java.util.Locale

internal val Languages.contentTranslationTarget: Language?
    get() = when (this) {
        Languages.System, Languages.Bashkir, Languages.Interlingua -> null
        else -> translatorLanguage
    }

internal val contentTranslationChoices = Languages.entries.filter {
    it == Languages.System || it.contentTranslationTarget != null
}

internal fun contentTranslationLanguage(locale: Locale): Languages = when (locale.language) {
    "zh" -> when (locale.script) {
        "Hans" -> Languages.ChineseSimplified
        "Hant" -> Languages.ChineseTraditional
        else -> if (locale.country in setOf("TW", "HK", "MO")) Languages.ChineseTraditional else Languages.ChineseSimplified
    }
    "pt" -> if (locale.country == "BR") Languages.PortugueseBrazilian else Languages.Portuguese
    "sr" -> if (locale.script == "Latn" || (locale.script.isEmpty() && locale.country == "CS")) {
        Languages.SerbianLatin
    } else Languages.SerbianCyrillic
    "he", "iw" -> Languages.Hebrew
    "id", "in" -> Languages.Indonesian
    "nb", "nn" -> Languages.Norwegian
    else -> Languages.entries.firstOrNull { it.code == locale.language } ?: Languages.English
}
