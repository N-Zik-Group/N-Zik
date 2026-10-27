package app.n_zik.android.components.settings.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.utils.lastUpdateCheckKey
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.secondary
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.SearchResultsViewport
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.search.UnifiedSearchBar
import app.n_zik.android.components.search.filterSearchHistory
import app.n_zik.android.components.search.rememberSearchHistory
import app.n_zik.android.surfaceColor
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.updater.services.Updater
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global settings entry page: always-visible search bar plus, for an empty
 * query, the items grouped into the registry's titleless group cards (card 1 =
 * About + Update, card 2 = the 8 categories - the grouping is registry data
 * only, the page never reorders rows; every row is a single line: icon +
 * label + chevron, no displayed description); a non-empty query shows the
 * card-level results instead, with the Update item indexed too. Host-agnostic:
 * no dependency on the 9-tab bottom bar nor on [Skeleton].
 *
 * Settings-only focus behavior: focusing the search bar fades out and
 * collapses the title (layout removal, so the bar rises to the title's
 * level); the group cards do a plain alpha fade in place (no layout
 * movement); search results stay fully visible.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SettingsGlobalPage(
    onItemSelected: (SettingsPageItem) -> Unit,
    onCardSelected: (tab: Int, focus: String) -> Unit,
    onOrganizedCardSelected: ((SettingsCardRegistryEntry) -> Unit)? = null,
    onEntrySelected: ((SettingsEntryTarget) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scrollState = rememberLazyListState()

    var searchInput by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val query = searchInput.text
    val contentAlpha by animateFloatAsState(
        targetValue = if (isSearchFocused && query.isBlank()) 0f else 1f,
        animationSpec = tween(250),
        label = "settingsGlobalPageContentAlpha"
    )
    val configuration = LocalConfiguration.current
    val itemResults = remember(context, configuration, query) {
        searchSettingsPageItems(context, query)
    }
    val cardResults = remember(context, configuration, query) {
        if (query.isBlank()) emptyList() else
            SettingsCards.filter { context.getString(it.titleRes).contains(query.trim(), true) }
    }
    val entryResults = remember(context, configuration, query) { searchSettingsEntries(context, query) }
    val categoryResults = remember(context, configuration, query) {
        if (query.isBlank()) emptyList() else OrganizedSettingsCategories.filter {
            context.getString(it.labelRes).contains(query.trim(), true)
        }
    }
    val noResults = query.isNotBlank() && itemResults.isEmpty() && cardResults.isEmpty() && entryResults.isEmpty() && categoryResults.isEmpty()
    SettingsSearchEmptyFeedback(query, noResults)
    LaunchedEffect(noResults) {
        if (noResults) scrollState.scrollToItem(0)
    }

    // This page's own filter history (per-page store), paused by the "pause
    // search history" setting like the search screens
    val isHistoryPaused by rememberPreference(pauseSearchHistoryKey, false)
    val history = rememberSearchHistory(SearchHistoryStore.PAGE_SETTINGS_GLOBAL, isHistoryPaused)
    // Keep only saved searches matching the current filter while focused.
    val historyMatches = filterSearchHistory(history.value, query)
    val historyVisible = isSearchFocused && historyMatches.isNotEmpty()

    fun recordSearchQuery(query: String = searchInput.text) {
        history.value = SearchHistoryStore.record(context.preferences, SearchHistoryStore.PAGE_SETTINGS_GLOBAL, query)
    }

    SearchResultsViewport {
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .background(colorPalette().background0)
                .fillMaxWidth()
                .testTag("settings_global_list")
        ) {
            item(key = "search_controls", contentType = "search_controls") {
                Column {
                    AnimatedVisibility(
                        visible = !isSearchFocused,
                        enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                        exit = shrinkVertically(tween(250)) + fadeOut(tween(250)),
                    ) {
                        HeaderWithIcon(
                            title = stringResource(R.string.settings_global_title),
                            iconId = R.drawable.settings,
                            enabled = false,
                            showIcon = true,
                            modifier = Modifier,
                            onClick = {}
                        )
                    }

                    SettingsGlobalSearchBar(
                        input = searchInput,
                        onInputChanged = { searchInput = it },
                        onFocused = { isSearchFocused = it },
                        onSearch = { recordSearchQuery() },
                        // Match Online Search and the per-page search coordinators.
                        modifier = Modifier.padding(SettingsSearchBarPadding)
                    )

                    SearchHistorySection(
                        visible = historyVisible,
                        queries = historyMatches,
                        onQueryClick = { searchInput = TextFieldValue(it); recordSearchQuery(it); isSearchFocused = false },
                        onQueryDelete = {
                            history.value = SearchHistoryStore.delete(
                                context.preferences,
                                SearchHistoryStore.PAGE_SETTINGS_GLOBAL,
                                it
                            )
                        },
                        onQueryDeleteAll = {
                            history.value = SearchHistoryStore.deleteAll(
                                context.preferences,
                                SearchHistoryStore.PAGE_SETTINGS_GLOBAL
                            )
                        }
                    )
                }
            }

            if (query.isBlank()) {
                // Plain alpha fade (no layout removal): the page content disappears
                // without moving or collapsing, the bar rise comes from the title.
                item(key = "groups", contentType = "groups") {
                    Column(modifier = Modifier.settingsSearchContentVisibility(contentAlpha)) {
                        SettingsPageGroups.forEach { group ->
                            GlobalPageCard(
                                testTag = "settings_global_group_${group.group}",
                                content = {
                                    SettingsPageItems
                                        .filter { it.group == group.group }
                                        .forEach { item ->
                                            GlobalPageItemRow(item = item, onItemSelected = onItemSelected)
                                        }
                                }
                            )
                        }
                    }
                }
            } else if (itemResults.isEmpty() && cardResults.isEmpty() && entryResults.isEmpty() && categoryResults.isEmpty()) {
                // Empty-result feedback is a debounced toast; keep the page clear.
            } else {
                items(entryResults, key = { "entry_${it.id}" }, contentType = { "entry" }) { result ->
                    val card = result.parentCard()
                    val category = OrganizedSettingsCategories.first { it.id == card.categoryId() }
                    GlobalPageCard(testTag = "settings_result_entry_${result.id}") {
                        OtherSettingsEntry(
                            title = stringResource(result.labelRes),
                            text = "${stringResource(category.labelRes)} › ${card.displayTitle(context)}",
                            icon = card.iconRes,
                            modifier = Modifier.clip(uiRoundnessShape()).testTag("settings_global_entry_${result.id}"),
                            onClick = {
                                recordSearchQuery()
                                onEntrySelected?.invoke(result) ?: onCardSelected(card.tab, card.focusId)
                            },
                        )
                    }
                }
                items(categoryResults, key = { "category_${it.id}" }, contentType = { "category" }) { category ->
                    val item = SettingsPageItems.first { it.categoryId == category.id }
                    GlobalPageCard(testTag = "settings_result_category_${category.id}") {
                        GlobalPageItemRow(item = item, onItemSelected = { recordSearchQuery(); onItemSelected(it) })
                    }
                }
                items(itemResults, key = { "item_${it.labelRes}" }, contentType = { "item" }) { item ->
                    GlobalPageCard(testTag = "settings_result_card_update") {
                        UpdateRow(
                            item = item,
                            onItemSelected = { recordSearchQuery(); onItemSelected(it) },
                            testTag = "settings_global_result_update",
                        )
                    }
                }
                items(cardResults, key = { "card_${it.tab}_${it.titleRes}" }, contentType = { "card" }) { card ->
                    val section = OrganizedSettingsCategories.first { it.id == card.categoryId() }
                    GlobalPageCard(testTag = "settings_result_card_${card.tab}_${card.titleRes}") {
                    OtherSettingsEntry(
                        title = card.displayTitle(context),
                        text = stringResource(section.labelRes),
                        icon = card.iconRes,
                        modifier = Modifier.clip(uiRoundnessShape()).testTag("settings_global_result_${card.tab}_${card.titleRes}"),
                        onClick = { recordSearchQuery(); onOrganizedCardSelected?.invoke(card) ?: onCardSelected(card.tab, card.focusId) }
                    )
                    }
                }
            }

            item(key = "bottom_space", contentType = "bottom_space") {
                Spacer(modifier = Modifier.height(Dimensions.bottomSpacer).testTag("settings_global_bottom_space"))
            }
        }
    }
}

/**
 * Titleless group card of the global page: a plain rounded block holding the
 * group's rows (spec: cards without title/header). Same container look as
 * [SettingsSectionCard].
 */
@Composable
private fun GlobalPageCard(
    testTag: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag(testTag)
                .shadow(
                    elevation = 4.dp,
                    shape = uiRoundnessShape(),
                    spotColor = colorPalette().accent.copy(alpha = 0.2f)
                ),
            shape = uiRoundnessShape(),
            colors = CardDefaults.cardColors(
                containerColor = surfaceColor(colorPalette().background1)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** One row of a group card (single line, no displayed description): a category row, or the Update row with its trailing state. */
@Composable
private fun GlobalPageItemRow(
    item: SettingsPageItem,
    onItemSelected: (SettingsPageItem) -> Unit,
) {
    if (item.route == null) {
        OtherSettingsEntry(
            title = stringResource(item.labelRes),
            text = "",
            icon = item.iconRes,
            modifier = Modifier.clip(uiRoundnessShape()).testTag("settings_global_category_${item.categoryId ?: item.tab}"),
            onClick = { onItemSelected(item) }
        )
    } else {
        UpdateRow(item = item, onItemSelected = onItemSelected)
    }
}

/**
 * Update row: trailing status on the right on auto-update builds; on builds
 * without auto-update the row is greyed, non-clickable and carries the
 * explanation on the right, marqueeing instead of wrapping when it overflows
 * the row (same condition and message as the About tab's greyed card).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpdateRow(
    item: SettingsPageItem,
    onItemSelected: (SettingsPageItem) -> Unit,
    testTag: String = "settings_global_update",
) {
    val autoUpdate = hasAutoUpdate()
    OtherSettingsEntry(
        title = stringResource(item.labelRes),
        text = "",
        icon = item.iconRes,
        modifier = Modifier.testTag(testTag),
        isEnabled = autoUpdate,
        onClick = { onItemSelected(item) },
        trailingContent = {
            if (autoUpdate) {
                UpdateRowTrailingStatus()
            } else {
                BasicText(
                    text = stringResource(R.string.description_app_not_installed_by_apk),
                    style = typography().xxs.secondary,
                    modifier = Modifier
                        .widthIn(max = 120.dp)
                        .testTag("settings_global_update_blocked_message")
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )
            }
        }
    )
}

/**
 * Trailing status of the Update row (auto-update builds): the same info the
 * About tab's removed update block showed - the release status plus the last
 * check - joined on one compact line, separated by a middle dot. Marquees
 * instead of wrapping when it overflows the row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpdateRowTrailingStatus() {
    val release = Updater.githubRelease
    val hasUpdate = release != null &&
        Updater.isVersionNewer(release.tagName, BuildConfig.VERSION_NAME)
    val status = stringResource(
        when {
            release == null -> R.string.audio_quality_format_unknown
            hasUpdate -> R.string.update_available
            else -> R.string.up_to_date
        }
    )
    val lastCheckTime by rememberPreference(lastUpdateCheckKey, 0L)
    val context = LocalContext.current
    val lastCheck = if (lastCheckTime > 0) {
        val sdf = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
        context.getString(R.string.last_check, sdf.format(Date(lastCheckTime)))
    } else {
        context.getString(R.string.never_checked)
    }
    BasicText(
        text = "$status \u00B7 $lastCheck",
        style = typography().xxs.secondary,
        modifier = Modifier
            .widthIn(max = 150.dp)
            .testTag("settings_global_update_status")
            .basicMarquee(iterations = Int.MAX_VALUE)
    )
}

/**
 * Always-visible search bar of the global settings page, rendered by the
 * unified app-wide [UnifiedSearchBar] (design reference: Android settings
 * search); the page owns the input state, no coupling with the per-tab
 * [Search] class.
 */
@Composable
internal fun SettingsGlobalSearchBar(
    input: TextFieldValue,
    onInputChanged: (TextFieldValue) -> Unit,
    onFocused: (Boolean) -> Unit = {},
    onSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    UnifiedSearchBar(
        input = input,
        onInputChanged = onInputChanged,
        placeholderRes = R.string.settings_global_search_hint,
        modifier = modifier,
        baseSurface = colorPalette().background1,
        onClear = { onInputChanged(TextFieldValue("")) },
        onFocused = onFocused,
        onSearch = onSearch,
        textFieldTestTag = "settings_global_search",
    )
}
