package app.n_zik.android.utils

import androidx.core.os.LocaleListCompat
import app.it.fast4x.rimusic.enums.Languages

/**
 * Builds the locale list applied to the app for the selected app language.
 *
 * [Languages.System] maps to an empty list — AppCompat interprets it as "reset to the
 * system locale". Every other language maps to its BCP-47 tag (e.g. "zh-CN", "pt-BR").
 */
internal fun localeListForAppLanguage(language: Languages): LocaleListCompat =
    if (language == Languages.System) {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(language.code)
    }
