package app.n_zik.android.components.settings.common

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.lang.ref.WeakReference
import java.text.Normalizer
import java.util.Locale

val AllSettingsEntryTargets: List<SettingsEntryTarget> by lazy {
    SettingsEntryTargets + EmbeddedSettingsEntryTargets + AboutSettingsEntryTargets + PrivacySettingsEntryTargets
}

private val combiningMarks = Regex("\\p{M}+")

private fun normalizedSettingsText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(combiningMarks, "").lowercase(Locale.ROOT).trim()

/** Searches a configuration-specific index without resolving resources on each keystroke. */
fun searchSettingsEntries(context: Context, query: String): List<SettingsEntryTarget> {
    if (query.isBlank()) return emptyList()
    return settingsEntrySearchIndex(context.resources).search(query)
}

internal class SettingsEntrySearchIndex(
    entries: List<SettingsEntryTarget>,
    descriptions: Map<String, Int>,
    resolveString: (Int) -> String,
) {
    private data class IndexedEntry(val target: SettingsEntryTarget, val label: String, val keywords: List<String>)

    private val entries: List<IndexedEntry>

    init {
        val strings = mutableMapOf<Int, String>()
        fun normalizedResource(id: Int) = strings.getOrPut(id) { normalizedSettingsText(resolveString(id)) }
        this.entries = entries.map { entry ->
            IndexedEntry(
                entry,
                normalizedResource(entry.labelRes),
                (entry.keywordRes + listOfNotNull(descriptions[entry.id])).map(::normalizedResource),
            )
        }
    }

    fun search(query: String): List<SettingsEntryTarget> {
        val needle = normalizedSettingsText(query)
        if (needle.isEmpty()) return emptyList()
        // Four stable buckets preserve the old score ordering without sorting every result set.
        val matches = Array(4) { mutableListOf<SettingsEntryTarget>() }
        entries.forEach { entry ->
            val label = entry.label
            val score = when {
                label == needle -> 0
                label.startsWith(needle) -> 1
                label.contains(needle) -> 2
                entry.keywords.any { it.contains(needle) } -> 3
                else -> return@forEach
            }
            matches[score].add(entry.target)
        }
        return matches.flatMap { it }.distinctBy { it.id }
    }
}

private var cachedResources: WeakReference<Resources>? = null
private var cachedConfiguration: Configuration? = null
private var cachedIndex: SettingsEntrySearchIndex? = null

@Synchronized
internal fun settingsEntrySearchIndex(resources: Resources): SettingsEntrySearchIndex {
    val previous = cachedIndex
    if (previous != null && cachedResources?.get() === resources && cachedConfiguration == resources.configuration) {
        return previous
    }
    return SettingsEntrySearchIndex(AllSettingsEntryTargets, SettingsEntryDescriptions, resources::getString).also {
        cachedResources = WeakReference(resources)
        // Resources mutates its Configuration in place when the app language changes.
        cachedConfiguration = Configuration(resources.configuration)
        cachedIndex = it
    }
}

/** Resolves the card containing an exact search target. */
fun SettingsEntryTarget.parentCard(): SettingsCardRegistryEntry =
    SettingsCards.first { it.tab == tab && it.titleRes == cardTitleRes }
