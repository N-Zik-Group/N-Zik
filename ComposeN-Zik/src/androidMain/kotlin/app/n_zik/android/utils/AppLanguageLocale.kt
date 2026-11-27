package app.n_zik.android.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import app.n_zik.android.core.database.Database
import app.n_zik.android.enums.Languages

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

/** Resolves the system's per-app locale override to the closest supported picker option. */
internal fun appLanguageForLocales(locales: LocaleListCompat): Languages? {
    val locale = locales[0] ?: return Languages.System
    val available = Languages.entries.filter { it != Languages.System }
    available.firstOrNull { localeListForAppLanguage(it)[0] == locale }?.let { return it }

    return when (locale.language) {
        "zh" -> if (locale.script == "Hant" ||
            (locale.script.isEmpty() && locale.country in setOf("TW", "HK", "MO"))
        ) Languages.ChineseTraditional else Languages.ChineseSimplified
        "sr" -> if (locale.script == "Latn" || (locale.script.isEmpty() && locale.country == "CS")) {
            Languages.SerbianLatin
        } else {
            Languages.SerbianCyrillic
        }
        "pt" -> if (locale.country == "BR") Languages.PortugueseBrazilian else Languages.Portuguese
        "nb", "nn" -> Languages.Norwegian
        else -> available.firstOrNull {
            val supported = localeListForAppLanguage(it)[0]
            supported?.language == locale.language && supported.country.isEmpty()
        }
    }
}

private fun pendingImportedLanguageKey(profile: String) = "pendingImportedAppLanguage_$profile"

internal fun Context.deferImportedAppLanguage(profile: String, language: Languages) {
    check(profilePreferences.edit().putString(pendingImportedLanguageKey(profile), language.name).commit()) {
        "Could not save the imported language for the next launch"
    }
}

internal fun Context.applyDeferredImportedAppLanguage() {
    val key = pendingImportedLanguageKey(getActiveProfile(this))
    val pendingLanguage = profilePreferences.getString(key, null) ?: return
    if (!shouldRecreateActivity(Database.isClosed)) return
    val language = Languages.entries.firstOrNull { it.name == pendingLanguage }
    if (language != null && preferences.all[languageAppKey] == pendingLanguage) {
        val locales = localeListForAppLanguage(language)
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
    profilePreferences.edit { remove(key) }
}
