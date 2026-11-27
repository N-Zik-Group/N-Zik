package app.n_zik.android.components.settings.common

import app.n_zik.android.components.ui.horizontalLayoutOutset
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.settings.appearance.preview.ThemeStoreCreatorTarget
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassFloatingActions
import app.n_zik.android.components.glass.glassFloatingActionContent
import app.n_zik.android.components.glass.rememberGlassFloatingActionBackdrop
import app.n_zik.android.components.navigation.header.LocalHeaderProfileFace
import app.n_zik.android.components.navigation.header.rememberHeaderProfileFace
import app.n_zik.android.components.ui.screens.profiles.ProfileCardAvatar
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.utils.lastUpdateCheckKey
import app.n_zik.android.utils.pauseSearchHistoryKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.enums.PlayerType
import app.n_zik.android.utils.secondary
import app.n_zik.android.utils.semiBold
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.ui.header.HeaderBadgeBox
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.SearchResultsViewport
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.search.UnifiedSearchBar
import app.n_zik.android.components.search.rememberSearchHistory
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.updater.services.Updater
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Searchable settings hub; an empty query displays the registry groups. */
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
    val overscroll = rememberOverscrollEffect()
    val floatingBackdrop = rememberGlassFloatingActionBackdrop()

    var searchInput by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val query = searchInput.text
    val contentAlpha by animateFloatAsState(
        targetValue = if (isSearchFocused && query.isBlank()) 0f else 1f,
        animationSpec = tween(250),
        label = "settingsGlobalPageContentAlpha"
    )
    val configuration = LocalConfiguration.current
    val ambiguousCategoryIds = remember(context, configuration) { ambiguousSettingsCategoryIds(context) }
    val itemResults = remember(context, configuration, query) {
        searchSettingsPageItems(context, query)
    }
    val normalizedQuery = normalizedSettingsText(query)
    val exactItems = itemResults.filter { it.hasPrioritySearchMatch(context, normalizedQuery) }
    val otherItems = itemResults - exactItems.toSet()
    val playerType by rememberPreference(playerTypeKey, PlayerType.Essential)
    val entryResults = remember(context, configuration, query, playerType) {
        searchSettingsEntries(context, query)
    }
    val categoryResults = remember(context, configuration, query) {
        searchSettingsCategories(context, query)
    }
    val cardResults = remember(context, configuration, query, entryResults, categoryResults) {
        val resetScopes = entryResults.filter { it.labelRes == R.string.settings_reset }.map { it.tab }.toSet()
        searchSettingsCardResults(context, query).filterNot { card ->
            (card.titleRes == R.string.settings_reset && card.tab in resetScopes) ||
                card.duplicatesCategoryResult(context, categoryResults)
        }
    }
    val (exactCategories, otherCategories) = categoryResults.partition {
        normalizedSettingsText(context.getString(it.labelRes)) == normalizedQuery
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
    val historyMatches = filterSettingsSearchHistory(history.value, query)
    val historyVisible = isSearchFocused && historyMatches.isNotEmpty()

    fun recordSearchQuery(query: String = searchInput.text) {
        history.value = SearchHistoryStore.record(context.preferences, SearchHistoryStore.PAGE_SETTINGS_GLOBAL, query)
    }

    // Keep drawing behind navigation/player overlays; only the final content gets clearance.
    SearchResultsViewport {
        LazyColumn(
            state = scrollState,
            contentPadding = rememberSettingsContentPadding(PaddingValues(bottom = Dimensions.bottomSpacer)),
            overscrollEffect = if (scrollState.canScrollForward || scrollState.canScrollBackward) overscroll else null,
            modifier = Modifier
                .glassFloatingActionContent(floatingBackdrop)
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
                        SettingsPageTitle(stringResource(R.string.settings_global_title), R.drawable.settings)
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
                        allQueries = history.value,
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
                // Keep group state composed while releasing its height after the focus fade.
                item(key = "groups", contentType = "groups") {
                    Column(modifier = Modifier.animateItem().settingsSearchContentVisibility(contentAlpha)) {
                        SettingsPageGroups.forEach { group ->
                            SettingsResultCard(
                                testTag = "settings_global_group_${group.group}",
                                content = {
                                    SettingsPageItems
                                        .filter { it.group == group.group }
                                        .forEach { item ->
                                            if (item.categoryId == "profiles") {
                                                SettingsProfileShortcut(item, onItemSelected)
                                            } else {
                                                GlobalPageItemRow(
                                                    item = item,
                                                    onItemSelected = onItemSelected,
                                                    showDescription = item.categoryId in ambiguousCategoryIds,
                                                )
                                            }
                                        }
                                }
                            )
                        }
                    }
                }
            } else if (itemResults.isEmpty() && cardResults.isEmpty() && entryResults.isEmpty() && categoryResults.isEmpty()) {
                // Empty-result feedback is a debounced toast; keep the page clear.
            } else {
                items(exactItems, key = { "item_${it.labelRes}" }, contentType = { "item" }) { item ->
                    SettingsResultCard(testTag = "settings_result_item_${item.route}", modifier = Modifier.animateItem()) {
                        GlobalPageItemRow(
                            item = item,
                            onItemSelected = { recordSearchQuery(); onItemSelected(it) },
                            testTag = if (item.isUpdaterSettingsItem()) "settings_global_result_update" else "settings_global_result_${item.route}",
                            showSearchLocation = true,
                        )
                    }
                }
                items(exactCategories, key = { "category_${it.id}" }, contentType = { "category" }) { category ->
                    val item = SettingsPageItems.first { it.categoryId == category.id }
                    SettingsResultCard(testTag = "settings_result_category_${category.id}", modifier = Modifier.animateItem()) {
                        GlobalPageItemRow(
                            item = item,
                            onItemSelected = { recordSearchQuery(); onItemSelected(it) },
                            showDescription = category.id in ambiguousCategoryIds,
                            showSearchLocation = true,
                        )
                    }
                }
                items(entryResults, key = { "entry_${it.id}" }, contentType = { "entry" }) { result ->
                    SettingsEntryResultRow(
                        result = result,
                        surfaceTag = "settings_result_entry_${result.id}",
                        rowTag = "settings_global_entry_${result.id}",
                        modifier = Modifier.animateItem(),
                        onClick = {
                            recordSearchQuery()
                            val card = result.parentCard()
                            onEntrySelected?.invoke(result) ?: onCardSelected(card.tab, card.focusId)
                        },
                    )
                }
                items(otherItems, key = { "item_${it.labelRes}" }, contentType = { "item" }) { item ->
                    SettingsResultCard(testTag = "settings_result_item_${item.route}", modifier = Modifier.animateItem()) {
                        GlobalPageItemRow(
                            item = item,
                            onItemSelected = { recordSearchQuery(); onItemSelected(it) },
                            testTag = if (item.isUpdaterSettingsItem()) "settings_global_result_update" else "settings_global_result_${item.route}",
                            showSearchLocation = true,
                        )
                    }
                }
                items(otherCategories, key = { "category_${it.id}" }, contentType = { "category" }) { category ->
                    val item = SettingsPageItems.first { it.categoryId == category.id }
                    SettingsResultCard(testTag = "settings_result_category_${category.id}", modifier = Modifier.animateItem()) {
                        GlobalPageItemRow(
                            item = item,
                            onItemSelected = { recordSearchQuery(); onItemSelected(it) },
                            showDescription = category.id in ambiguousCategoryIds,
                            showSearchLocation = true,
                        )
                    }
                }
                items(cardResults, key = { "card_${it.tab}_${it.titleRes}" }, contentType = { "card" }) { card ->
                    SettingsCardResultRow(
                        card = card,
                        surfaceTag = "settings_result_card_${card.tab}_${card.titleRes}",
                        rowTag = "settings_global_result_${card.tab}_${card.titleRes}",
                        modifier = Modifier.animateItem(),
                        onClick = { recordSearchQuery(); onOrganizedCardSelected?.invoke(card) ?: onCardSelected(card.tab, card.focusId) },
                    )
                }
            }

        }
        GlassFloatingActions(floatingBackdrop) {
            SettingsScrollToTop(
                lazyListState = scrollState,
                modifier = Modifier.testTag("settings.global.scrollToTop"),
            )
        }
    }
}

@Composable
private fun SettingsProfileShortcut(
    profiles: SettingsPageItem,
    onItemSelected: (SettingsPageItem) -> Unit,
) {
    val face = LocalHeaderProfileFace.current ?: rememberHeaderProfileFace()
    val outset = LocalSettingsCardRowOutset.current
    Row(
        modifier = Modifier
            .horizontalLayoutOutset(outset)
            .fillMaxWidth()
            .clip(uiRoundnessShape())
            .clickable(role = Role.Button) { onItemSelected(profiles) }
            .testTag("settings_global_profile_open")
            .padding(horizontal = settingsCardRowHorizontalPadding(outset), vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileCardAvatar(face, GlassArea.Images, Modifier.clearAndSetSemantics {}, slotSize = 32.dp)
        Column(Modifier.weight(1f).padding(end = 2.dp)) {
            BasicText(
                text = face.name,
                style = typography().s.semiBold.copy(color = colorPalette().text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = stringResource(profiles.labelRes),
                style = typography().xs.secondary,
            )
        }
        Icon(
            painter = painterResource(R.drawable.chevron_forward),
            contentDescription = null,
            tint = colorPalette().textSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Renders a category or dedicated destination, with a trailing status for Update. */
@Composable
internal fun GlobalPageItemRow(
    item: SettingsPageItem,
    onItemSelected: (SettingsPageItem) -> Unit,
    testTag: String = if (item.isUpdaterSettingsItem()) "settings_global_update" else "settings_global_category_${item.categoryId ?: item.route ?: item.tab}",
    showDescription: Boolean = false,
    showSearchLocation: Boolean = false,
) {
    val location = if (showSearchLocation) settingsPageSearchLocation(item) else ""
    if (!item.isUpdaterSettingsItem()) {
        val betaTag = when (item) {
            SettingsGlassItem -> "settings.glass.beta"
            SettingsThemeStoreItem -> "settings.theme_store.beta"
            else -> null
        }
        OtherSettingsEntry(
            title = stringResource(item.labelRes),
            text = when {
                showSearchLocation && showDescription -> "$location · ${stringResource(item.descriptionRes)}"
                showSearchLocation -> location
                showDescription -> stringResource(item.descriptionRes)
                item == SettingsThemeStoreCreatorItem -> stringResource(R.string.theme_store_title)
                else -> ""
            },
            icon = item.iconRes,
            modifier = Modifier.clip(uiRoundnessShape()).testTag(testTag),
            onClick = { onItemSelected(item) },
            titlePrefix = betaTag?.let { tag -> {
                HeaderBadgeBox(stringResource(R.string.updates_build_beta_label), colorPalette().accent,
                    modifier = Modifier.testTag(tag),
                    glassSettings = app.n_zik.android.components.glass.rememberGlassSettings())
            } },
        )
    } else {
        UpdateRow(item = item, onItemSelected = onItemSelected, testTag = testTag, location = location)
    }
}

@Composable
private fun settingsPageSearchLocation(item: SettingsPageItem): String {
    val root = stringResource(R.string.settings_title)
    if (item.isThemeStoreCreatorPage()) {
        val targetKey = item.route?.let(Uri::parse)?.getQueryParameter("target")
        val group = when (ThemeStoreCreatorTarget.entries.firstOrNull { it.routeKey == targetKey }) {
            ThemeStoreCreatorTarget.PublishingAccess, ThemeStoreCreatorTarget.GitHubInstallation ->
                R.string.theme_store_group_publishing_access
            ThemeStoreCreatorTarget.Profile -> R.string.theme_store_creator_profile
            ThemeStoreCreatorTarget.PublicInformation, ThemeStoreCreatorTarget.PublishProfile ->
                R.string.theme_store_edit_creator
            ThemeStoreCreatorTarget.Publish, ThemeStoreCreatorTarget.EditPublished,
            ThemeStoreCreatorTarget.MyThemes -> R.string.theme_store_group_theme_publication
            ThemeStoreCreatorTarget.Submissions -> R.string.theme_store_group_review_status
            ThemeStoreCreatorTarget.CreateLocal, ThemeStoreCreatorTarget.EditLocal -> R.string.theme_store_local_themes
            null -> null
        }
        val parentGroup = R.string.theme_store_group_local_editing.takeIf { group == R.string.theme_store_local_themes }
        return listOfNotNull(root, stringResource(R.string.theme_store_title),
            stringResource(SettingsThemeStoreCreatorItem.labelRes), parentGroup?.let { stringResource(it) }, group?.let { stringResource(it) })
            .joinToString(" › ")
    }
    return when {
        item == SettingsGlassItem || item == SettingsThemeStoreItem ->
            "$root › ${stringResource(R.string.settings_category_interface)}"
        item.isUpdaterSettingsItem() && item.labelRes == R.string.updates_changelog_search_label ->
            "$root › ${stringResource(R.string.common_action_update)}"
        item.isUpdaterSettingsItem() && item != SettingsUpdateItem ->
            "$root › ${stringResource(R.string.common_action_update)} › ${stringResource(R.string.common_menu_label)}"
        else -> root
    }
}

/** Opens release notes on every build and shows the appropriate updater status. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpdateRow(
    item: SettingsPageItem,
    onItemSelected: (SettingsPageItem) -> Unit,
    testTag: String = "settings_global_update",
    location: String = "",
) {
    val autoUpdate = hasAutoUpdate()
    OtherSettingsEntry(
        title = stringResource(item.labelRes),
        text = location,
        icon = item.iconRes,
        modifier = Modifier.testTag(testTag),
        onClick = { onItemSelected(item) },
        trailingContent = {
            if (autoUpdate) {
                UpdateRowTrailingStatus()
            } else {
                BasicText(
                    text = stringResource(R.string.updates_manual_update_install_source_message),
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

/** Release status and last-check date, constrained to the trailing column. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpdateRowTrailingStatus() {
    val release = Updater.githubRelease
    val hasUpdate = release != null &&
        Updater.isVersionNewer(release.tagName, BuildConfig.VERSION_NAME)
    val status = stringResource(
        when {
            release == null -> R.string.audio_quality_format_unknown
            hasUpdate -> R.string.updates_available
            else -> R.string.updates_up_to_date
        }
    )
    val lastCheckTime by rememberPreference(lastUpdateCheckKey, 0L)
    val context = LocalContext.current
    val lastCheck = if (lastCheckTime > 0) {
        val sdf = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
        context.getString(R.string.updates_last_check_format, sdf.format(Date(lastCheckTime)))
    } else {
        context.getString(R.string.updates_never_checked)
    }
    BasicText(
        text = stringResource(R.string.updates_status_and_last_check, status, lastCheck),
        style = typography().xxs.secondary,
        modifier = Modifier
            .widthIn(max = 150.dp)
            .testTag("settings_global_update_status")
            .basicMarquee(iterations = Int.MAX_VALUE)
    )
}

/** Global search uses the same focus and Back behavior as other settings searches. */
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
