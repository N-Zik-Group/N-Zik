package app.n_zik.android.components.settings.common

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.filterSearchHistory
import app.n_zik.android.components.search.rememberSearchHistory
import app.n_zik.android.components.settings.about.AboutSettingsContent
import app.n_zik.android.components.settings.accounts.AccountsSettingsContent
import app.n_zik.android.components.settings.ai.AIRecommendationSettingsContent
import app.n_zik.android.components.settings.appearance.AppearanceSettingsContent
import app.n_zik.android.components.settings.data.DataSettingsContent
import app.n_zik.android.components.settings.general.GeneralSettingsContent
import app.n_zik.android.components.settings.network.NetworkSettingsContent
import app.n_zik.android.components.settings.other.OtherSettingsContent
import app.n_zik.android.components.settings.privacy.PermissionUsageSettingsCard
import app.n_zik.android.components.settings.privacy.PrivacyRewindCard
import app.n_zik.android.components.settings.privacy.PrivacyScreenCaptureCard
import app.n_zik.android.components.settings.ui.UiSettingsContent
import app.n_zik.android.components.tab.Search
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

fun settingsCardTargetId(context: android.content.Context, tab: Int, titleRes: Int): String =
    "card.$tab.${context.resources.getResourceEntryName(titleRes)}"

@OptIn(ExperimentalAnimationApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SettingsCategoryPage(categoryId: String, navController: NavController, target: String = "", parentTarget: String = "") {
    val category = OrganizedSettingsCategories.firstOrNull { it.id == categoryId } ?: return
    val context = LocalContext.current
    val search = Search(initialVisible = true)
    val contentSearch = Search(initialVisible = false)
    val searchFocus = rememberSettingsSearchFocus(search.isFocused, search.inputValue.isBlank())
    val registry = remember(categoryId) { SettingsTargetRegistry() }
    DisposableEffect(registry, target) {
        onDispose {
            registry.nestedTarget?.nestedDestination?.let { settingsNestedDialog(it.dialogId)?.hideDialog() }
            registry.nestedTarget = null
        }
    }
    val scroll = rememberLazyListState()
    var hasPinnedFooter by remember(categoryId) { mutableStateOf(false) }
    val overscroll = rememberOverscrollEffect()
    val showMessage = LocalSettingsSearchMessage.current
    val unavailableMessage = stringResource(R.string.settings_target_unavailable)
    val historyPage = "settings_category_$categoryId"
    val paused by rememberPreference(pauseSearchHistoryKey, false)
    val history = rememberSearchHistory(historyPage, paused)
    fun recordQuery(query: String = search.inputValue) {
        history.value = SearchHistoryStore.record(context.preferences, historyPage, query)
    }
    val cards = remember(categoryId) { cardsForCategory(categoryId) }
    val matches = if (search.inputValue.isBlank()) cards else searchSettingsCards(context, search.inputValue).filter { it.categoryId() == categoryId }
    val entryMatches = searchSettingsEntries(context, search.inputValue)
        .filter { it.parentCard().categoryId() == categoryId }
    val sourceTabs = remember(categoryId) {
        val preferred = when (categoryId) {
            "player" -> listOf(2, 0)
            "privacy" -> listOf(9, 4, 3)
            "data" -> listOf(7, 4)
            else -> cards.map { it.tab }.distinct()
        }
        preferred.filter { tab -> cards.any { it.tab == tab } }
    }
    val slots = remember(categoryId) { sourceTabs.associateWith { SettingsCardSlots() } }
    val rows = sourceTabs.flatMap { tab ->
        val selected = matches.filter { it.tab == tab && it.titleRes != R.string.settings_reset }.map { it.titleRes }.toSet()
        val titles = slots.getValue(tab).titles
        val ordered = if (titles.isEmpty()) {
            cards.filter { it.tab == tab && it.titleRes != R.string.settings_reset }.map { it.titleRes } +
                if (tab in listOf(0, 2, 6)) listOf(SettingsSourceFooter) else emptyList()
        } else titles.toList()
        ordered.filter { it in selected }.map { tab to it }
    }
    val latestRows by rememberUpdatedState(rows)
    val resetScopes = matches.filter { it.titleRes == R.string.settings_reset }.map { it.tab }
    SettingsSearchEmptyFeedback(search.inputValue, matches.isEmpty() && entryMatches.isEmpty())
    LaunchedEffect(target, parentTarget) {
        if (target.isBlank()) return@LaunchedEffect
        // Two layout frames include children and their initial conditional composition.
        withFrameNanos { }
        withFrameNanos { }
        val requestedTarget = when (target) {
            "appearance.player_presets_and_layout.icon_like_button" -> "appearance.timeline_and_controls.icon_like_button"
            "network.network_status.image_quality_format" -> "network.quality.image_quality_format"
            "network.network_status.audio_quality_format" -> "network.quality.audio_quality_format"
            "network.network_status.download_quality_format" -> "embedded.network.download_quality_format"
            "card.6.network_status" -> "card.6.quality"
            "card.6.settings_download_status" -> "card.6.settings_download_quality"
            else -> target
        }
        val entry = AllSettingsEntryTargets.firstOrNull { it.id == requestedTarget }
        val cardId = entry?.let { settingsCardTargetId(context, it.tab, it.cardTitleRes) }
            ?: requestedTarget.takeIf { it.startsWith("card.") }
            ?: parentTarget
        val row = latestRows.indexOfFirst { (tab, title) ->
            title != SettingsSourceFooter && settingsCardTargetId(context, tab, title) == cardId
        }
        val item = when {
            resetScopeForTarget(requestedTarget) != null || resetScopeForTarget(parentTarget) != null -> latestRows.size + 1
            row >= 0 -> row + 1
            else -> null
        }
        if (item != null) {
            // Lazy cards must be composed before exact-row availability can be evaluated.
            scroll.scrollToItem(item)
            withFrameNanos { }
            withFrameNanos { }
        }
        val nested = entry?.nestedDestination
        if (nested != null) {
            val opener = withTimeoutOrNull(1500) {
                snapshotFlow { registry.targets[nested.openerId] }.first { it != null }
            }
            if (opener != null) {
                registry.nestedTarget = entry
                settingsNestedDialog(nested.dialogId)?.showDialog()
                withTimeoutOrNull(1500) {
                    snapshotFlow { registry.targets[requestedTarget] }.first { it != null }
                }
            }
        }
        val unavailable = !registry.targets.containsKey(requestedTarget)
        if (unavailable) {
            if (nested != null) {
                settingsNestedDialog(nested.dialogId)?.hideDialog()
                registry.nestedTarget = null
            }
            showMessage(unavailableMessage)
        }
        val resolved = if (!unavailable) requestedTarget else parentTarget
        withFrameNanos { }
        registry.targets[resolved]?.let {
            it.bringIntoView()
            registry.blink(resolved)
        }
    }
    CompositionLocalProvider(LocalSettingsTargets provides registry) {
        Column(Modifier.testTag("settings.category.$categoryId").background(colorPalette().background0).fillMaxWidth().fillMaxHeight()) {
            // One source composition owns shared restart state and singleton dialogs.
            sourceTabs.forEach { tab ->
                val selection = cards.filter { it.tab == tab }.map { it.titleRes }.toSet() - R.string.settings_reset
                if (selection.isEmpty()) return@forEach
                CompositionLocalProvider(
                    LocalSettingsCardSlots provides slots.getValue(tab),
                    LocalSettingsResetScope provides (SettingsCategories.firstOrNull { it.tab == tab }?.labelRes ?: R.string.settings_category_privacy),
                ) {
                    SettingsSourceContent(
                        tab, contentSearch, navController, selection,
                        { title -> Modifier.settingsSearchTarget(settingsCardTargetId(context, tab, title)) },
                        normalizePreferences = target.isBlank(),
                    )
                }
            }
            LazyColumn(
                state = scroll,
                contentPadding = rememberSettingsContentPadding(
                    base = PaddingValues(bottom = Dimensions.bottomSpacer),
                    bottomInsetHandledByFooter = hasPinnedFooter,
                ),
                overscrollEffect = if (scroll.canScrollForward || scroll.canScrollBackward) overscroll else null,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("settings.category.list.$categoryId"),
            ) {
                item(key = "header", contentType = "header") {
                    Column {
                        settingsSearchTitle(searchFocus, category.labelRes, category.iconRes)
                        settingsSearchDescription(searchFocus) {
                            SettingsDescription(
                                text = stringResource(category.descriptionRes),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (categoryId != "about") {
                            search.SearchBar(this, barPadding = searchFocus.barPadding, onSearch = { recordQuery() })
                            SearchHistorySection(
                                visible = search.isFocused && filterSearchHistory(history.value, search.inputValue).isNotEmpty(),
                                queries = filterSearchHistory(history.value, search.inputValue),
                                onQueryClick = { search.input = TextFieldValue(it); recordQuery(it); search.isFocused = false },
                                onQueryDelete = { history.value = SearchHistoryStore.delete(context.preferences, historyPage, it) },
                                onQueryDeleteAll = { history.value = SearchHistoryStore.deleteAll(context.preferences, historyPage) },
                            )
                        }
                    }
                }
                items(entryMatches, key = { "entry:${it.id}" }, contentType = { "entry" }) { entry ->
                    Column(Modifier.settingsSearchContentVisibility(searchFocus.contentAlpha)) {
                        OtherSettingsEntry(
                            title = entry.searchResultTitle(context),
                            text = entry.locationDescription(context),
                            icon = entry.parentCard().iconRes,
                            modifier = Modifier.testTag("settings.category.entry.${entry.id}"),
                            onClick = {
                                recordQuery()
                                navController.navigate(entry.destinationRoute(context))
                            },
                        )
                    }
                }
                items(rows, key = { (tab, title) -> "$tab:$title" }, contentType = { "card" }) { (tab, title) ->
                    CompositionLocalProvider(
                        LocalSettingsResetScope provides (SettingsCategories.firstOrNull { it.tab == tab }?.labelRes ?: R.string.settings_category_privacy),
                    ) {
                        Column(Modifier.settingsSearchContentVisibility(searchFocus.contentAlpha)) {
                            slots.getValue(tab).content[title]?.value?.invoke()
                        }
                    }
                }
                if (resetScopes.isNotEmpty()) {
                    item(key = "reset", contentType = "reset") {
                        Column(Modifier.settingsSearchContentVisibility(searchFocus.contentAlpha)) {
                            SettingsResetCard(resetScopes, target) { tab ->
                                CompositionLocalProvider(
                                    LocalSettingsCardSlots provides null,
                                    LocalSettingsResetScope provides SettingsCategories.first { it.tab == tab }.labelRes,
                                ) {
                                    SettingsSourceContent(
                                        tab, contentSearch, navController, setOf(R.string.settings_reset),
                                        { title -> Modifier.settingsSearchTarget(settingsCardTargetId(context, tab, title)) },
                                        normalizePreferences = false,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            SettingsPinnedFooter(
                bottomSpacer = Dimensions.bottomSpacer,
                contentPadding = rememberPageChromeContentPadding(
                    base = PaddingValues(bottom = Dimensions.bottomSpacer),
                    topInsetProvider = { 0.dp },
                ),
                onVisibilityChanged = { hasPinnedFooter = it },
            ) {
                sourceTabs.forEach { tab ->
                    CompositionLocalProvider(
                        LocalSettingsResetScope provides (SettingsCategories.firstOrNull { it.tab == tab }?.labelRes ?: R.string.settings_category_privacy),
                    ) {
                        slots.getValue(tab).content[SettingsSourceFooter]?.value?.invoke()
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalAnimationApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SettingsSourceContent(
    tab: Int,
    search: Search,
    navController: NavController,
    selection: Set<Int>,
    decorate: @Composable (Int) -> Modifier,
    normalizePreferences: Boolean,
) {
    when (tab) {
        0 -> GeneralSettingsContent(search, selectedCards = selection, cardModifier = decorate)
        1 -> UiSettingsContent(search, navController, selectedCards = selection, cardModifier = decorate)
        2 -> AppearanceSettingsContent(search, navController, selectedCards = selection, cardModifier = decorate, normalizePreferences = normalizePreferences)
        3 -> AIRecommendationSettingsContent(search, navController, selectedCards = selection, cardModifier = decorate)
        4 -> DataSettingsContent(search, navController, selectedCards = selection, cardModifier = decorate)
        5 -> AccountsSettingsContent(search, navController, selectedCards = selection, cardModifier = decorate)
        6 -> NetworkSettingsContent(search, navController, selectedCards = selection, cardModifier = decorate)
        7 -> OtherSettingsContent(search, selectedCards = selection, cardModifier = decorate)
        8 -> AboutSettingsContent(selectedCards = selection, cardModifier = decorate)
        9 -> {
            if (R.string.privacy_screen_protection in selection) SettingsCardSlot(R.string.privacy_screen_protection, modifier = { decorate(R.string.privacy_screen_protection) }) { PrivacyScreenCaptureCard() }
            if (R.string.permission_usage_journal in selection) SettingsCardSlot(R.string.permission_usage_journal, modifier = { decorate(R.string.permission_usage_journal) }) { PermissionUsageSettingsCard() }
            if (R.string.rw_settings_master in selection) SettingsCardSlot(R.string.rw_settings_master, modifier = { decorate(R.string.rw_settings_master) }) { PrivacyRewindCard() }
        }
    }
}
