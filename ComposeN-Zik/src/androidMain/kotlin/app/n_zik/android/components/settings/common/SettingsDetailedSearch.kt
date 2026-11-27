package app.n_zik.android.components.settings.common

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.lang.ref.WeakReference
import app.n_zik.android.components.search.normalizeSearchText
import app.n_zik.android.components.search.filterSearchHistory
import app.n_zik.android.R
import app.n_zik.android.components.settings.accounts.DiscordButtonField
import app.n_zik.android.components.settings.accounts.isYouTubeSyncBuild
import app.n_zik.android.components.settings.appearance.PlayerSongSwipeSetting
import app.n_zik.android.components.settings.isLastFmSettingsAvailable
import app.n_zik.android.components.settings.data.storageCleanupSearchLabels
import app.n_zik.android.components.settings.ui.GlassSearchOptions
import app.n_zik.android.components.settings.ui.GlassSearchParent
import app.n_zik.android.components.settings.ui.GlassSectionGroupTitles
import app.n_zik.android.components.settings.ui.defaultHomePageChoices
import app.n_zik.android.components.settings.ui.glassSearchOption
import app.n_zik.android.components.settings.ui.isGlassSearchLabelSupported
import app.n_zik.android.components.ui.screens.bridge.BridgeAutoStopTarget
import app.n_zik.android.components.ui.screens.bridge.BridgeSettingsTargetArgument
import app.n_zik.android.core.ondevice.hasDeviceAudioPermission
import app.n_zik.android.extensions.pip.supportsPictureInPicture
import app.n_zik.android.enums.PlayerType
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.updater.models.UpdateEndpoints
import app.n_zik.android.utils.isAtLeastAndroid12
import app.n_zik.android.utils.isAtLeastAndroid13
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext

val AllSettingsEntryTargets: List<SettingsEntryTarget> by lazy {
    val entriesByCard = (SettingsEntryTargets + EmbeddedSettingsEntryTargets + AboutSettingsEntryTargets +
        PrivacySettingsEntryTargets).groupBy { it.tab to it.cardTitleRes }
    val nestedByOpener = SettingsNestedEntryTargets.groupBy { it.nestedDestination?.openerId }
    val lastInterfaceCard = cardsForCategory("interface").last()
    buildList {
        OrganizedSettingsCards.forEach { card ->
            entriesByCard[card.tab to card.titleRes].orEmpty().forEach { entry ->
                add(entry)
                addAll(nestedByOpener[entry.id].orEmpty())
            }
            if (card == lastInterfaceCard) {
                addAll(GlassSearchOptions.map { it.target })
            }
        }
    }
}

private val settingsSearchSeparators = Regex("[&/／＆›]+")
private val settingsCountedLabelNumber = Regex("(?<!\\p{Nd})\\p{Nd}{1,4}(?!\\p{Nd})")
internal val portraitOnlySettingsSearchLabels = setOf(
    R.string.settings_lyrics_top_spacing_label,
    R.string.player_top_spacing_description,
    R.string.player_artwork_hidden_swipe_animation,
    R.string.settings_transition_slide_vertical,
    R.string.settings_transition_fade,
    R.string.settings_transition_scale,
    R.string.player_artwork_transition_circular,
    R.string.settings_player_artwork_carousel_label,
    R.string.settings_player_expanded_artwork_size_label,
)

internal fun normalizedSettingsText(text: String): String =
    normalizeSearchText(text.replace(settingsSearchSeparators, " "))

/** Matches reversed terms and word prefixes without combining terms from unrelated labels. */
internal fun matchesSettingsTerms(words: Set<String>, queryTerms: List<String>): Boolean {
    if (queryTerms.size < 2 || words.size < queryTerms.size) return false
    val remaining = words.toMutableSet()
    return queryTerms.all { term ->
        val word = if (term in remaining) term else
            remaining.firstOrNull { term.length >= 2 && it.startsWith(term) }
        word != null && remaining.remove(word)
    }
}

internal fun filterSettingsSearchHistory(queries: List<String>, input: String): List<String> =
    filterSearchHistory(queries, input, ::normalizedSettingsText)

/** Searches a configuration-specific index without resolving resources on each keystroke. */
fun searchSettingsEntries(context: Context, query: String): List<SettingsEntryTarget> =
    searchSettingsEntries(context, query, isYouTubeSyncBuild())

internal fun searchSettingsEntries(
    context: Context,
    query: String,
    profileShortcutIndex: SettingsEntrySearchIndex?,
    hasAudioPermission: Boolean? = null,
): List<SettingsEntryTarget> =
    searchSettingsEntries(
        context, query, isYouTubeSyncBuild(),
        profileShortcutIndex = profileShortcutIndex,
        hasAudioPermission = hasAudioPermission,
    )

@Composable
internal fun rememberSettingsAudioPermission(context: Context): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var granted by remember(context) { mutableStateOf(context.hasDeviceAudioPermission()) }
    DisposableEffect(context, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = context.hasDeviceAudioPermission()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return granted
}

internal fun searchSettingsEntries(
    context: Context,
    query: String,
    youtubeSyncSupported: Boolean,
    lastFmSupported: Boolean = isLastFmSettingsAvailable(),
    profileShortcutIndex: SettingsEntrySearchIndex? = null,
    hasAudioPermission: Boolean? = null,
): List<SettingsEntryTarget> {
    if (query.isBlank()) return emptyList()
    val resources = context.resources
    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activeSwipeSetting = PlayerSongSwipeSetting.forLayout(
        context.preferences.getEnum(playerTypeKey, PlayerType.Essential),
        isLandscape,
    )
    val homePageLabels = defaultHomePageChoices(context).map { resources.getString(it.textId) }
    val currentChoiceLabels = mapOf("ui.user_interface.default_page" to homePageLabels)
    return settingsEntrySearchIndex(resources).search(query, profileShortcutIndex, currentChoiceLabels) { id, count ->
        resources.getQuantityString(id, count, count)
    }.filter { result ->
        when {
            !youtubeSyncSupported && result.requiresYouTubeSyncBuild() -> false
            !lastFmSupported && result.cardTitleRes == R.string.settings_lastfm_title -> false
            isLandscape && result.labelRes in portraitOnlySettingsSearchLabels -> false
            result.id.startsWith("general.picture_in_picture.") &&
                result.id != "general.picture_in_picture.settings_enable_pip" -> supportsPictureInPicture(context)
            result.cardTitleRes == R.string.glass_effect_title -> isGlassSearchLabelSupported(context, result.labelRes)
            result.id == LocalMusicPermissionTarget -> !(hasAudioPermission ?: context.hasDeviceAudioPermission())
            PlayerSongSwipeSetting.entries.any { it.target == result.id } -> result.id == activeSwipeSetting.target
            else -> true
        }
    }
}

internal class SettingsEntrySearchIndex(
    entries: List<SettingsEntryTarget>,
    descriptions: Map<String, Int>,
    choiceLabels: ((Int) -> String) -> Map<String, List<String>> = { emptyMap() },
    scopeTitles: (SettingsEntryTarget) -> List<Int> = { listOfNotNull(it.searchScopeTitleRes()) },
    resolveString: (Int) -> String,
) {
    private data class IndexedEntry(
        val target: SettingsEntryTarget,
        val labels: List<String>,
        val labelTerms: List<Set<String>>,
        val keywords: List<String>,
        val choices: List<String>,
        val scopes: List<String>,
        val scopeTerms: List<Set<String>>,
        val qualifiedLabels: List<String>,
        val qualifiedTerms: List<Set<String>>,
    )

    private val entries: List<IndexedEntry>

    init {
        val strings = mutableMapOf<Int, String>()
        fun resource(id: Int) = strings.getOrPut(id) { resolveString(id) }
        val normalizedStrings = mutableMapOf<Int, String>()
        fun normalizedResource(id: Int) = normalizedStrings.getOrPut(id) { normalizedSettingsText(resource(id)) }
        val choices = choiceLabels(::resource)
        this.entries = entries.map { entry ->
            val buttonField = DiscordButtonField.forTarget(entry.id)
            val labels = if (entry.id == AboutAuthorSettingsTarget) listOf(
                normalizedSettingsText(aboutAuthorSearchLabel(::resource)),
                normalizedSettingsText(UpdateEndpoints.REAL_OWNER),
            ).distinct() else listOfNotNull(
                if (entry.displayLabel != null || entry.labelFormatRes != null) {
                    normalizedSettingsText(entry.searchLabel(::resource))
                } else normalizedResource(entry.labelRes),
                buttonField?.let { normalizedSettingsText(it.title(::resource)) },
                buttonField?.let { normalizedSettingsText(it.title(::resource, separator = " ")) },
            ).distinct()
            val scopes = scopeTitles(entry).map(::normalizedResource).filter { it.isNotEmpty() && it !in labels }.distinct()
            val indexedChoices = if (entry.id.startsWith("glass.")) {
                glassSearchOption(entry.id)?.choiceRes.orEmpty().map(::normalizedResource)
            } else {
                choices[entry.id].orEmpty().map(::normalizedSettingsText)
            }
            IndexedEntry(
                entry,
                labels,
                labels.map { it.split(' ').toSet() },
                (entry.keywordRes + listOfNotNull(descriptions[entry.id]) + storageCleanupSearchLabels(entry.id))
                    .map(::normalizedResource),
                indexedChoices.distinct(),
                scopes,
                scopes.map { it.split(' ').toSet() },
                scopes.flatMap { scope -> labels.flatMap { listOf("$scope $it", "$it $scope") } },
                scopes.flatMap { scope -> labels.map { "$scope $it".split(' ').toSet() } },
            )
        }
    }

    fun search(query: String, resolveCountedLabel: ((Int, Int) -> String)? = null): List<SettingsEntryTarget> =
        search(query, additionalIndex = null, resolveCountedLabel = resolveCountedLabel)

    fun search(
        query: String,
        additionalIndex: SettingsEntrySearchIndex?,
        currentChoiceLabels: Map<String, List<String>> = emptyMap(),
        resolveCountedLabel: ((Int, Int) -> String)? = null,
    ): List<SettingsEntryTarget> {
        val needle = normalizedSettingsText(query)
        if (needle.isEmpty()) return emptyList()
        val tokens = needle.split(' ').sortedByDescending(String::length)
        val count = settingsCountedLabelNumber.find(needle)?.value?.takeUnless { it == needle }?.toIntOrNull()
        // Keep the original four ranks; available choices are a lower-priority fallback.
        val matches = Array(5) { mutableListOf<SettingsEntryTarget>() }
        (entries.asSequence() + additionalIndex?.entries.orEmpty().asSequence()).forEach { entry ->
            val labels = entry.labels
            val countedLabel = if (count != null && resolveCountedLabel != null) {
                entry.target.countedLabelRes?.let { normalizedSettingsText(resolveCountedLabel(it, count)) }
            } else null
            val score = when {
                needle in labels || countedLabel == needle -> 0
                labels.any { it.startsWith(needle) } || countedLabel?.startsWith(needle) == true -> 1
                labels.any { it.contains(needle) } || entry.labelTerms.any { matchesSettingsTerms(it, tokens) } -> 2
                tokens.size > 1 && entry.scopes.none { it.contains(needle) } &&
                    entry.scopeTerms.none { matchesSettingsTerms(it, tokens) } &&
                    (entry.qualifiedLabels.any { it.contains(needle) } || entry.qualifiedTerms.any { matchesSettingsTerms(it, tokens) }) -> 3
                entry.keywords.any { keyword -> keyword.contains(needle) || labels.any { "$it $keyword" == needle } } -> 3
                (currentChoiceLabels[entry.target.id]?.map(::normalizedSettingsText) ?: entry.choices).any { choice ->
                    choice.contains(needle) || labels.any { label -> tokens.all { label.contains(it) || choice.contains(it) } } ||
                        (entry.target.cardTitleRes == R.string.glass_effect_title && entry.scopeTerms.any { scope ->
                            tokens.any { it in scope && !choice.contains(it) } &&
                                tokens.any { it !in scope && choice.contains(it) } &&
                                tokens.all { it in scope || choice.contains(it) }
                        })
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
        AllSettingsEntryTargets.filter {
            when (it.id) {
                "general.app_behavior.close_app_with_back_button" -> isAtLeastAndroid13
                "general.picture_in_picture.settings_enable_pip_auto" -> isAtLeastAndroid12
                else -> true
            }
        },
        SettingsEntryDescriptions,
        choiceLabels = { resolve ->
            settingsEntryChoiceLabels(resolve, resources.configuration.locales[0]) { id, count ->
                resources.getQuantityString(id, count, count)
            }
        },
        scopeTitles = SettingsEntryTarget::qualifiedSearchTitleResources,
        resolveString = resources::getString,
    ).also {
        cachedResources = WeakReference(resources)
        // Resources mutates its Configuration in place when the app language changes.
        cachedConfiguration = Configuration(resources.configuration)
        cachedIndex = it
    }
}

/** Resolves the card containing an exact search target. */
fun SettingsEntryTarget.parentCard(searchCategoryId: String? = null): SettingsCardRegistryEntry = when {
    id.startsWith("glass.") -> GlassSearchParent
    id == SettingsResetDownloadsTarget && searchCategoryId == "system" ->
        SettingsCards.first { it.tab == tab && it.titleRes == R.string.settings_reset }
    else -> SettingsCards.first { it.tab == tab && it.titleRes == cardTitleRes }
}

/** Localized location shown before navigation, using the same parent and subgroup as routing. */
fun SettingsEntryTarget.locationDescription(context: Context, searchCategoryId: String? = null): String {
    val card = parentCard(searchCategoryId)
    val category = OrganizedSettingsCategories.first { it.id == card.categoryId() }
    val glassSection = glassSearchOption(id)?.sectionTitle
    val nested = nestedDestination
    val opener = nested?.let { destination -> AllSettingsEntryTargets.firstOrNull { it.id == destination.openerId } }
    if (id in setOf(SettingsResetDownloadsTarget, SettingsResetAllTarget, SettingsResetChooserTarget,
            SettingsResetGlassTarget, SettingsResetListenTogetherTarget, SettingsResetPlaybackTarget, SettingsResetPrivacyTarget)
    ) {
        return listOf(R.string.settings_title, category.labelRes, R.string.settings_reset)
            .joinToString(" › ", transform = context::getString)
    }
    return listOfNotNull(context.getString(R.string.settings_title), context.getString(category.labelRes).takeUnless { id.startsWith("glass.") }, card.displayTitle(context),
        card.groupTitleRes?.let(context::getString),
        glassSection?.let(GlassSectionGroupTitles::get)?.let(context::getString),
        glassSection?.let(context::getString),
        innerCardTitleRes?.let(context::getString),
        groupTitleRes?.let(context::getString),
        paletteTitleRes?.let(context::getString),
        opener?.let { context.getString(it.labelRes) },
        nested?.sectionTitleRes?.let(context::getString))
        .distinct().joinToString(" › ")
}

/** Uses the same destination for global and category results, including dedicated settings pages. */
fun SettingsEntryTarget.destinationRoute(context: Context, searchCategoryId: String? = null): String {
    if (id.startsWith("glass.")) return "$SETTINGS_GLASS_ROUTE?target=${Uri.encode(id)}"
    if (BridgeAutoStopTarget.entries.any { it.id == id }) {
        return "${NavRoutes.bridgeServer.name}?$BridgeSettingsTargetArgument=${Uri.encode(id)}"
    }
    val card = parentCard(searchCategoryId)
    val parent = Uri.encode(settingsCardTargetId(context, card.tab, card.titleRes))
    return "settingsCategory/${card.categoryId()}?target=${Uri.encode(id)}&parent=$parent"
}

/** Qualifies labels shared by multiple search targets. */
fun SettingsEntryTarget.searchResultTitle(context: Context): String {
    if (labelRes == R.string.settings_reset) return parentCard().displayTitle(context)
    if (id == AboutAuthorSettingsTarget) return aboutAuthorSearchLabel(context::getString)
    val label = DiscordButtonField.forTarget(id)?.title(context::getString) ?: searchLabel(context::getString)
    return searchScopeTitleRes()?.let { "${context.getString(it)} › $label" } ?: label
}

private fun SettingsEntryTarget.searchLabel(resolveString: (Int) -> String): String {
    val label = displayLabel ?: resolveString(labelRes)
    return labelFormatRes?.let { resolveString(it).format(label) } ?: label
}

private fun SettingsEntryTarget.searchScopeTitleRes(): Int? =
    when {
        id.startsWith("glass.") -> R.string.glass_effect_title
        id in setOf(SettingsResetListenTogetherTarget, SettingsResetPlaybackTarget, SettingsResetPrivacyTarget) -> R.string.settings_reset
        labelRes == R.string.github_app_installation -> R.string.common_brand_github
        else -> paletteTitleRes ?: nestedDestination?.sectionTitleRes
    }

private fun SettingsEntryTarget.qualifiedSearchTitleResources(): List<Int> {
    val card = parentCard()
    val opener = nestedDestination?.openerId?.let { id -> settingsEntriesById[id] }
    val glassSectionTitle = glassSearchOption(id)?.sectionTitle
    return listOfNotNull(
        searchScopeTitleRes(),
        card.titleRes,
        card.groupTitleRes,
        innerCardTitleRes,
        groupTitleRes,
        glassSectionTitle,
        glassSectionTitle?.let(GlassSectionGroupTitles::get),
        opener?.labelRes,
        nestedDestination?.sectionTitleRes,
    ).distinct()
}

private val settingsEntriesById by lazy { AllSettingsEntryTargets.associateBy { it.id } }

@Composable
internal fun rememberProfileShortcutSettingsIndex(context: Context): SettingsEntrySearchIndex? {
    val configuration = LocalConfiguration.current
    return produceState<SettingsEntrySearchIndex?>(null, context, configuration) {
        value = withContext(NzikDispatchers.DATA) {
            SettingsEntrySearchIndex(
                entries = profileShortcutSettingsTargets(context),
                descriptions = emptyMap(),
                scopeTitles = SettingsEntryTarget::qualifiedSearchTitleResources,
                resolveString = context::getString,
            )
        }
    }.value
}

internal suspend fun findSettingsEntryTarget(context: Context, id: String): SettingsEntryTarget? =
    settingsEntriesById[id] ?: if (isProfileShortcutSettingsTarget(id)) {
        withContext(NzikDispatchers.DATA) {
            profileShortcutSettingsTargets(context).firstOrNull { it.id == id }
        }
    } else null
