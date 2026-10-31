package app.n_zik.android.components.settings.common

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import java.lang.ref.WeakReference
import java.text.Normalizer
import java.util.Locale
import app.n_zik.android.R
import app.n_zik.android.components.settings.ui.GlassSearchOptions
import app.n_zik.android.components.settings.ui.GlassSearchParent
import app.n_zik.android.components.settings.ui.glassSearchOption

val AllSettingsEntryTargets: List<SettingsEntryTarget> by lazy {
    SettingsEntryTargets + EmbeddedSettingsEntryTargets + AboutSettingsEntryTargets +
        PrivacySettingsEntryTargets + GlassSearchOptions.map { it.target } + SettingsNestedEntryTargets
}

private val combiningMarks = Regex("\\p{M}+")
private val settingsWhitespace = Regex("\\s+")

private fun normalizedSettingsText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(combiningMarks, "").lowercase(Locale.ROOT).trim().replace(settingsWhitespace, " ")

/** Searches a configuration-specific index without resolving resources on each keystroke. */
fun searchSettingsEntries(context: Context, query: String): List<SettingsEntryTarget> {
    if (query.isBlank()) return emptyList()
    return settingsEntrySearchIndex(context.resources).search(query)
}

internal class SettingsEntrySearchIndex(
    entries: List<SettingsEntryTarget>,
    descriptions: Map<String, Int>,
    choiceLabels: ((Int) -> String) -> Map<String, List<String>> = { emptyMap() },
    resolveString: (Int) -> String,
) {
    private data class IndexedEntry(
        val target: SettingsEntryTarget,
        val label: String,
        val keywords: List<String>,
        val choices: List<String>,
    )

    private val entries: List<IndexedEntry>

    init {
        val strings = mutableMapOf<Int, String>()
        fun resource(id: Int) = strings.getOrPut(id) { resolveString(id) }
        val normalizedStrings = mutableMapOf<Int, String>()
        fun normalizedResource(id: Int) = normalizedStrings.getOrPut(id) { normalizedSettingsText(resource(id)) }
        val choices = choiceLabels(::resource)
        this.entries = entries.map { entry ->
            IndexedEntry(
                entry,
                normalizedResource(entry.labelRes),
                (entry.keywordRes + listOfNotNull(descriptions[entry.id])).map(::normalizedResource),
                choices[entry.id].orEmpty().map(::normalizedSettingsText).distinct(),
            )
        }
    }

    fun search(query: String): List<SettingsEntryTarget> {
        val needle = normalizedSettingsText(query)
        if (needle.isEmpty()) return emptyList()
        val tokens = needle.split(' ')
        // Keep the original four ranks; available choices are a lower-priority fallback.
        val matches = Array(5) { mutableListOf<SettingsEntryTarget>() }
        entries.forEach { entry ->
            val label = entry.label
            val score = when {
                label == needle -> 0
                label.startsWith(needle) -> 1
                label.contains(needle) -> 2
                entry.keywords.any { it.contains(needle) } -> 3
                entry.choices.any { choice ->
                    choice.contains(needle) || tokens.all { label.contains(it) || choice.contains(it) }
                } -> 4
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
    return SettingsEntrySearchIndex(
        AllSettingsEntryTargets,
        SettingsEntryDescriptions,
        { resolve -> settingsEntryChoiceLabels(resolve, resources.configuration.locales[0]) },
        resources::getString,
    ).also {
        cachedResources = WeakReference(resources)
        // Resources mutates its Configuration in place when the app language changes.
        cachedConfiguration = Configuration(resources.configuration)
        cachedIndex = it
    }
}

/** Resolves the card containing an exact search target. */
fun SettingsEntryTarget.parentCard(): SettingsCardRegistryEntry =
    if (id.startsWith("glass.")) GlassSearchParent else
        SettingsCards.first { it.tab == tab && it.titleRes == cardTitleRes }

/** Localized location shown before navigation, using the same parent and subgroup as routing. */
fun SettingsEntryTarget.locationDescription(context: Context): String {
    val card = parentCard()
    val category = OrganizedSettingsCategories.first { it.id == card.categoryId() }
    val nested = nestedDestination
    val opener = nested?.let { destination -> AllSettingsEntryTargets.firstOrNull { it.id == destination.openerId } }
    return listOfNotNull(context.getString(R.string.settings), context.getString(category.labelRes).takeUnless { id.startsWith("glass.") }, card.displayTitle(context),
        glassSearchOption(id)?.let { context.getString(it.sectionTitle) },
        opener?.let { context.getString(it.labelRes) },
        nested?.sectionTitleRes?.let(context::getString))
        .distinct().joinToString(" › ")
}

/** Uses the same destination for global and category results, including dedicated settings pages. */
fun SettingsEntryTarget.destinationRoute(context: Context): String {
    if (id.startsWith("glass.")) return "$SETTINGS_GLASS_ROUTE?target=${Uri.encode(id)}"
    val card = parentCard()
    val parent = Uri.encode(settingsCardTargetId(context, card.tab, card.titleRes))
    return "settingsCategory/${card.categoryId()}?target=${Uri.encode(id)}&parent=$parent"
}

/** Distinguishes appearance policies from functional options with the same destination label. */
fun SettingsEntryTarget.searchResultTitle(context: Context): String {
    val label = context.getString(labelRes)
    return if (id.startsWith("glass.")) "${context.getString(R.string.liquid_glass)} › $label" else label
}
