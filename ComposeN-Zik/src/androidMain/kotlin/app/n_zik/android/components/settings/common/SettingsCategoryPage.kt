package app.n_zik.android.components.settings.common

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.testTag
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.search.rememberSearchHistory
import app.n_zik.android.components.search.filterSearchHistory
import app.n_zik.android.components.search.SearchNoResults
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.settings.about.AboutSettingsContent
import app.n_zik.android.components.settings.accounts.AccountsSettingsContent
import app.n_zik.android.components.settings.ai.AIRecommendationSettingsContent
import app.n_zik.android.components.settings.appearance.AppearanceSettingsContent
import app.n_zik.android.components.settings.data.DataSettingsContent
import app.n_zik.android.components.settings.general.GeneralSettingsContent
import app.n_zik.android.components.settings.network.NetworkSettingsContent
import app.n_zik.android.components.settings.other.OtherSettingsContent
import app.n_zik.android.components.settings.ui.UiSettingsContent
import app.n_zik.android.components.tab.Search
import kotlinx.coroutines.delay

fun settingsCardTargetId(context: android.content.Context, tab: Int, titleRes: Int): String =
    "card.$tab.${context.resources.getResourceEntryName(titleRes)}"

@OptIn(ExperimentalAnimationApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SettingsCategoryPage(categoryId: String, navController: NavController, target: String = "", parentTarget: String = "") {
    val category = OrganizedSettingsCategories.firstOrNull { it.id == categoryId } ?: return
    val context = LocalContext.current
    val search = Search(initialVisible = true)
    val searchFocus = rememberSettingsSearchFocus(search.isFocused, search.inputValue.isBlank())
    val registry = remember(categoryId) { SettingsTargetRegistry() }
    val scroll = rememberScrollState()
    var unavailable by remember(target) { mutableStateOf(false) }
    val historyPage = "settings_category_$categoryId"
    val paused by rememberPreference(pauseSearchHistoryKey, false)
    val history = rememberSearchHistory(historyPage, paused)
    fun recordQuery(query: String = search.inputValue) {
        history.value = SearchHistoryStore.record(context.preferences, historyPage, query)
    }
    val cards = remember(categoryId) { cardsForCategory(categoryId) }
    val matches = if (search.inputValue.isBlank()) cards else searchSettingsCards(context, search.inputValue).filter { it.categoryId() == categoryId }
    val selected = matches.groupBy { it.tab }.mapValues { (_, cards) -> cards.map { it.titleRes }.toSet() }
    val sourceTabs = remember(categoryId) { cards.map { it.tab }.distinct() }
    LaunchedEffect(target, parentTarget) {
        if (target.isBlank()) return@LaunchedEffect
        // Two layout frames include children and their initial conditional composition.
        withFrameNanos { }
        withFrameNanos { }
        unavailable = !registry.targets.containsKey(target)
        val resolved = if (!unavailable) target else parentTarget
        withFrameNanos { }
        registry.targets[resolved]?.bringIntoView()
        registry.highlighted = resolved
        delay(1800)
        registry.highlighted = null
    }
    CompositionLocalProvider(LocalSettingsTargets provides registry) {
        Column(Modifier.testTag("settings.category.$categoryId").background(colorPalette().background0).fillMaxWidth().verticalScroll(scroll)) {
            settingsSearchTitle(searchFocus, category.labelRes, category.iconRes)
            search.SearchBar(this, barPadding = searchFocus.barPadding, onSearch = { recordQuery() })
            SearchHistorySection(
                visible = search.isFocused && history.value.isNotEmpty(),
                queries = filterSearchHistory(history.value, search.inputValue),
                onQueryClick = { search.input = TextFieldValue(it); recordQuery(it); search.isFocused = false },
                onQueryDelete = { history.value = SearchHistoryStore.delete(context.preferences, historyPage, it) },
                onQueryDeleteAll = { history.value = SearchHistoryStore.deleteAll(context.preferences, historyPage) },
            )
            if (unavailable) Text(stringResource(R.string.settings_target_unavailable), modifier = Modifier.testTag("settings.target.unavailable"))
            if (matches.isEmpty()) SearchNoResults(fillRemainingViewport = true)
            sourceTabs.forEach { tab ->
                val selection = selected[tab] ?: emptySet()
                val decorate: @Composable (Int) -> Modifier = { title ->
                    Modifier.settingsSearchTarget(settingsCardTargetId(context, tab, title))
                }
                CompositionLocalProvider(LocalSettingsResetScope provides SettingsCategories.first { it.tab == tab }.labelRes) {
                    when (tab) {
                        0 -> GeneralSettingsContent(search, selectedCards = selection, cardModifier = decorate)
                        1 -> UiSettingsContent(search, navController, selectedCards = selection, cardModifier = decorate)
                        2 -> AppearanceSettingsContent(search, navController, selectedCards = selection, cardModifier = decorate, normalizePreferences = target.isBlank())
                        3 -> AIRecommendationSettingsContent(search, navController, selectedCards = selection, cardModifier = decorate)
                        4 -> DataSettingsContent(search, navController, selectedCards = selection, cardModifier = decorate)
                        5 -> AccountsSettingsContent(search, navController, selectedCards = selection, cardModifier = decorate)
                        6 -> NetworkSettingsContent(search, navController, selectedCards = selection, cardModifier = decorate)
                        7 -> OtherSettingsContent(search, selectedCards = selection, cardModifier = decorate)
                        8 -> AboutSettingsContent(selectedCards = selection, cardModifier = decorate)
                    }
                }
            }
            Spacer(Modifier.height(Dimensions.bottomSpacer))
        }
    }
}
