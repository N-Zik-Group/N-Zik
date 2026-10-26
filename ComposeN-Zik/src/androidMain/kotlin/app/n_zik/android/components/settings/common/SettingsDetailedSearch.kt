package app.n_zik.android.components.settings.common

import android.content.Context
import java.text.Normalizer
import java.util.Locale

val AllSettingsEntryTargets: List<SettingsEntryTarget>
    get() = SettingsEntryTargets + EmbeddedSettingsEntryTargets + AboutSettingsEntryTargets

private fun normalizedSettingsText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()

fun searchSettingsEntries(context: Context, query: String): List<SettingsEntryTarget> {
    val needle = normalizedSettingsText(query)
    if (needle.isEmpty()) return emptyList()
    return AllSettingsEntryTargets.mapNotNull { entry ->
        val label = normalizedSettingsText(context.getString(entry.labelRes))
        val score = when {
            label == needle -> 0
            label.startsWith(needle) -> 1
            label.contains(needle) -> 2
            entry.keywordRes.any { normalizedSettingsText(context.getString(it)).contains(needle) } -> 3
            else -> return@mapNotNull null
        }
        entry to score
    }.sortedBy { it.second }.map { it.first }.distinctBy { it.id }
}

fun SettingsEntryTarget.parentCard(): SettingsCardRegistryEntry =
    SettingsCards.first { it.tab == tab && it.titleRes == cardTitleRes }
