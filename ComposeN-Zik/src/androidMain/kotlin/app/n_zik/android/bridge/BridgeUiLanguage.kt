package app.n_zik.android.bridge

import android.content.Context
import app.it.fast4x.rimusic.enums.Languages
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.languageAppKey
import app.it.fast4x.rimusic.utils.preferences
import java.util.Locale

/**
 * The phone's effective UI language served by `GET /api/v1/meta` (contract §5, `ui.language`,
 * since 1.9.0).
 *
 * Mirror guarantee: the bridge reads the language the way the phone's own UI reads it — the active
 * profile's [preferences], `getEnum(languageAppKey, …)` — so the served language equals the
 * displayed one. There is one brief pre-existing window right after a phone language change, while
 * the activity recreation is gated: the preference updates ahead of the displayed locale, so the
 * mirror holds against the preference store, and against the displayed locale outside that window.
 * An explicit choice serves its BCP-47 code; [Languages.System] serves the phone's
 * resolved OS locale (the app context's configuration carries the OS locale, and the phone's own
 * `System` display is that same locale, so the mirror holds). An undetermined primary locale is
 * served as `null`, never the tag `und`; any failure degrades to `null` (the phone's language is
 * optional on the wire, and a client before or after 1.9.0 tolerates its absence).
 */
internal object BridgeUiLanguage {

    /** The phone's effective UI language as a BCP-47 tag, `null` when it cannot be determined. */
    fun effective(context: Context): String? = runCatching {
        when (val language = context.preferences.getEnum(languageAppKey, Languages.System)) {
            Languages.System -> appLocaleTag(context)
            else -> language.code
        }
    }.getOrNull()

    /**
     * The phone's resolved locale for [Languages.System]: the primary locale of the app context's
     * `resources.configuration.locales` (a `LocaleList`; an empty one falls back to the JVM
     * default locale). An undetermined primary locale reads as `null` — the tag `und` is never
     * served.
     */
    private fun appLocaleTag(context: Context): String? {
        val locales = context.resources.configuration.locales
        val primary = if (locales.isEmpty()) Locale.getDefault() else locales.get(0)
        val tag = primary.toLanguageTag()
        return tag.takeIf { tag.isNotBlank() && !tag.equals("und", ignoreCase = true) && !tag.startsWith("und-") }
    }
}
