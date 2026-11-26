package app.n_zik.android.components.settings.appearance.preview

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics

import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.AppPullToRefreshBox
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DefaultDialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.settings.SettingsDialogTabRow
import app.n_zik.android.components.glass.GlassFloatingActions
import app.n_zik.android.components.glass.glassFloatingActionContent
import app.n_zik.android.components.glass.rememberGlassFloatingActionBackdrop
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.navigation.Skeleton
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.SearchEmptySpace
import app.n_zik.android.components.search.UnifiedSearchBar
import app.n_zik.android.components.settings.accounts.GitHubAccountCard
import app.n_zik.android.components.settings.common.LocalSettingsCardGroupTitle
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsScrollToTop
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.ui.UiButton
import app.n_zik.android.components.ui.screens.home.GlassHomeChips
import app.n_zik.android.components.ui.toggles.Switch
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.enums.TransitionEffect
import app.n_zik.android.typography
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.components.themed.HeaderWithIcon
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.transitionEffectKey

internal enum class ThemeStoreSection(val routeKey: String, val label: Int, val downloaded: Boolean = false) {
    New("new", R.string.theme_store_new),
    Official("official", R.string.theme_store_official_section),
    Glass("glass", R.string.theme_store_glass_section),
    Community("community", R.string.theme_store_community_section),
    Authors("authors", R.string.theme_store_authors),
    Stock("downloaded-stock", R.string.theme_store_downloaded_stock, downloaded = true),
    Imported("downloaded-imported", R.string.theme_store_downloaded_imported, downloaded = true),
    DownloadedOfficial("downloaded-official", R.string.theme_store_downloaded_official, downloaded = true),
    DownloadedStore("downloaded-store", R.string.theme_store_downloaded_store, downloaded = true);

    fun includes(theme: StoreTheme, verifiedIds: Set<Long>?): Boolean = when (this) {
        New, Authors -> true
        Stock, Imported, DownloadedOfficial, DownloadedStore -> false
        Official -> verifiedIds?.contains(theme.author.githubId) == true
        Glass -> "glass" in theme.tags.canonicalStoreTags()
        Community -> verifiedIds?.contains(theme.author.githubId) == false
    }
}

internal const val THEME_STORE_CREATOR_TAB = "creator"

internal val ThemeStoreGutter = 12.dp

@Composable
internal fun ThemeStoreScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit,
    section: ThemeStoreSection? = null,
    creatorId: Long? = null,
    startInCreatorTab: Boolean = false,
    creatorTargetKey: String? = null,
) {
    val title = stringResource(if (creatorId != null) R.string.theme_store_creator else section?.label ?: R.string.theme_store_title)
    val floating = NavigationBarPosition.BottomFloating.isCurrent()
    val bottom = Dimensions.navBarBottomPadding(floating) + if (floating) 10.dp else 15.dp
    Skeleton(
        navController = navController,
        topInsetHandledByContent = true,
        miniPlayer = miniPlayer,
        navBarContent = { item -> item(0, title, R.drawable.ui) },
    ) { _ ->
        ThemeStoreLanding(
            title = title,
            padding = rememberPageChromeContentPadding(PaddingValues(start = ThemeStoreGutter, end = ThemeStoreGutter, bottom = bottom)),
            section = section,
            creatorId = creatorId,
            initialTab = if (startInCreatorTab) StoreTab.Creator else StoreTab.Explore,
            creatorTarget = ThemeStoreCreatorTarget.entries.firstOrNull { it.routeKey == creatorTargetKey },
            onOpenCreator = { id -> navController.navigate("${NavRoutes.themeStoreCreator.name}/$id") },
            onEditCreator = { navController.navigate(NavRoutes.themeStoreCreatorSettings.name) { launchSingleTop = true } },
            onOpenCreatorProfileTarget = { target ->
                navController.navigate(creatorProfileSettingsRoute(target)) { launchSingleTop = true }
            },
            onSubmitTheme = { id -> navController.navigate("${NavRoutes.themeStoreSubmission.name}/$id") },
            onSeeAll = { target -> navController.navigate("${NavRoutes.themeStoreNew.name}?section=${target.routeKey}") },
            onOpenTheme = { theme -> navController.navigate("${NavRoutes.themeStoreDetail.name}/${theme.themeId}/${theme.revision}") },
        )
    }
}

private enum class StoreTab(val label: Int) {
    Explore(R.string.theme_store_explore),
    Downloaded(R.string.theme_store_downloaded),
    Following(R.string.theme_store_following),
    Creator(R.string.theme_store_creator),
}

@Composable
private fun ThemeStoreLanding(
    title: String,
    padding: PaddingValues,
    section: ThemeStoreSection?,
    creatorId: Long?,
    initialTab: StoreTab,
    creatorTarget: ThemeStoreCreatorTarget?,
    onOpenCreator: (Long) -> Unit,
    onSeeAll: (ThemeStoreSection) -> Unit,
    onOpenTheme: (StoreTheme) -> Unit,
    onEditCreator: () -> Unit,
    onOpenCreatorProfileTarget: (ThemeStoreCreatorTarget) -> Unit,
    onSubmitTheme: (String) -> Unit,
) {
    val context = LocalContext.current
    val ownCreatorId = signedInCreatorId()
    val profileId = getActiveProfile(context)
    val preferences = remember(context, profileId) { context.preferences }
    val owner = remember(context, preferences, profileId) { ProfileWorkOwner.capture(context)?.takeIf { it.id == profileId } }
    val library = remember(preferences) { ThemeStoreLibrary(preferences) }
    val offline by OfflineMode.rememberEnabled()
    var withdrawalTheme by remember { mutableStateOf<StoreTheme?>(null) }
    withdrawalTheme?.takeIf { !offline && it.author.githubId == ownCreatorId }?.let {
        ThemeWithdrawalDialog(it, profileId) { withdrawalTheme = null }
    }
    val catalog = remember(context, offline) { themeStoreCatalog(context, offline) }
    val availableTabs = if (offline) listOf(StoreTab.Downloaded, StoreTab.Creator) else StoreTab.entries
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    var openedCreatorProfileTarget by rememberSaveable(creatorTarget) { mutableStateOf(false) }
    LaunchedEffect(tab, creatorTarget, ownCreatorId) {
        if (tab == StoreTab.Creator && ownCreatorId != null &&
            creatorTarget?.opensProfileEditor == true && !openedCreatorProfileTarget
        ) {
            openedCreatorProfileTarget = true
            onOpenCreatorProfileTarget(creatorTarget)
        }
    }
    val activeTab = tab.takeIf { it in availableTabs } ?: StoreTab.Downloaded
    val transitionEffect by rememberPreference(transitionEffectKey, TransitionEffect.Fade)
    var shownTab by remember(offline) { mutableStateOf(activeTab) }
    val tabOpacity = remember { Animatable(1f) }
    LaunchedEffect(activeTab, transitionEffect) {
        if (transitionEffect == TransitionEffect.None) {
            shownTab = activeTab
            tabOpacity.snapTo(1f)
        } else {
            if (shownTab != activeTab) {
                tabOpacity.animateTo(0f, tween(120))
                shownTab = activeTab
            }
            tabOpacity.animateTo(1f, tween(230))
        }
    }
    val tabContentModifier = Modifier.graphicsLayer { alpha = tabOpacity.value }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var downloadQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var followingQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var advancedTags by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var downloadedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var downloadedAdvancedTags by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var retry by remember { mutableIntStateOf(0) }
    val pendingReports = rememberPendingThemeStoreReports(retry)
    val builtIn = checkNotNull(rememberBundledDefaultTheme(retry))
    val verifiedIds = rememberVerifiedCreatorIds(catalog, retry)
    var loading by remember { mutableStateOf(true) }
    var themes by remember { mutableStateOf<List<StoreTheme>?>(null) }
    var downloads by remember(library) { mutableStateOf(library.downloads()) }
    val downloadQueue = rememberThemeStoreDownloadQueue(profileId)
    val pendingDownloads by remember(downloadQueue) { downloadQueue?.activeDownloads ?: kotlinx.coroutines.flow.flowOf(emptySet<String>()) }
        .collectAsStateWithLifecycle(initialValue = emptySet(), context = NzikDispatchers.DATA)
    val downloadedIds = remember(downloads) { downloads.mapTo(mutableSetOf(), SavedTheme::id) }
    fun downloadControl(theme: StoreTheme): ThemeCardDownload {
        val id = "${theme.themeId}:${theme.revision}"
        return ThemeCardDownload(id in downloadedIds, id in pendingDownloads,
            downloadQueue?.takeIf { !offline && theme.supportAt(BuildConfig.VERSION_CODE) == StoreSupport.Supported }
                ?.let { queue -> { queue.enqueue(theme) } })
    }
    var follows by remember(library) { mutableStateOf(library.follows()) }
    var publicationSettingVersion by remember(library) { mutableIntStateOf(0) }
    DisposableEffect(preferences, library) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith("themeStore.publications.")) publicationSettingVersion++
            follows = library.follows()
            if (key == null || key.startsWith("themeStore.download.")) downloads = library.downloads()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val toggleFollow: (StoreAuthor) -> Unit = { author ->
        if (author.githubId != ownCreatorId) {
            val followed = library.follows().any { it.githubId == author.githubId }
            library.setFollowed(author, !followed)
            follows = library.follows()
        }
    }

    fun togglePublicationAlerts(id: Long) {
        if (id == ownCreatorId) return
        val current = themes ?: return
        library.setPublicationsEnabled(id, !library.publicationsEnabled(id), current)
    }
    val publicationAlertsFor: (Long) -> Boolean = remember(library, publicationSettingVersion) {
        { id -> library.publicationsEnabled(id) }
    }
    var loadedCreators by remember(catalog) { mutableStateOf<List<CreatorProfile>?>(null) }
    var creatorsLoading by remember(catalog) { mutableStateOf(!offline) }
    var creatorsFailed by remember(catalog) { mutableStateOf(false) }
    val creators = loadedCreators.orEmpty()
    LaunchedEffect(catalog, retry, offline) {
        if (offline) {
            creatorsLoading = false
            return@LaunchedEffect
        }
        creatorsLoading = true
        creatorsFailed = false
        try {
            loadedCreators = catalog.creators()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            creatorsFailed = true
            Timber.tag("ThemeStore").w(error, "Could not load creators")
        } finally {
            creatorsLoading = false
        }
    }
    val glass = rememberGlassSettings().enabled
    var lastSync by remember(catalog) { mutableStateOf<Long?>(null) }

    LaunchedEffect(catalog, retry, library, owner, offline) {
        loading = true
        if (themes == null) catalog.cachedState()?.let {
            themes = it.themes
            lastSync = it.syncedAt
        }
        if (offline) {
            loading = false
            return@LaunchedEffect
        }
        try {
            val current = catalog.refreshState()
            themes = current.themes
            lastSync = current.syncedAt
            catalog.bindRestoredDownloads(library, current.themes) {
                owner != null && ProfileWorkOwner.isBound(context, owner) && !OfflineMode.enabled(context)
            }
            if (getActiveProfile(context) == profileId) notifyThemeStoreAlerts(context, library, current.themes, profileId)
            launch {
                catalog.downloadAutomaticUpdates(context, library, current.themes, profileId) {
                    owner != null && ProfileWorkOwner.isBound(context, owner) && !OfflineMode.enabled(context)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("ThemeStore").w(error, "Could not refresh theme catalog")
            Toaster.e(R.string.theme_store_catalog_unavailable)
        } finally {
            loading = false
        }
    }

    val tags = themes.orEmpty().flatMap { it.tags }.canonicalStoreTags()
    val effectiveTag = tag?.takeIf { it in tags }
    val selectedTags = advancedTags.filter { it in tags }
    val newestFirst = remember(themes) { themes.orEmpty().mostRecentlyPublishedFirst() }
    val filtered = newestFirst.filterStoreThemes(query.text, effectiveTag)
        .filter { it.tags.canonicalStoreTags().containsAll(selectedTags) && (section?.includes(it, verifiedIds) ?: true) }
    val downloadedTags = (listOf(builtIn) + downloads).flatMap { it.theme?.tags.orEmpty() }.canonicalStoreTags()
    val effectiveDownloadedTag = downloadedTag?.takeIf { it in downloadedTags }
    val selectedDownloadedTags = downloadedAdvancedTags.filter { it in downloadedTags }
    val byAuthor = themes.orEmpty().groupBy { it.author.githubId }
    val creatorDisplayNames = remember(creators) { creators.associate { it.githubId to it.displayName } }
    val locale = LocalConfiguration.current.locales[0]
    val creatorNameCollator = remember(locale) { java.text.Collator.getInstance(locale) }
    val authorIds = (creators.map { it.githubId } + byAuthor.keys).distinct()
    val searchTerm = storeAuthorSearchTerm(query.text)
    val matchingCreators = if ((section == null || section == ThemeStoreSection.Authors) && searchTerm.isNotBlank()) authorIds.filter { id ->
        val profile = creators.firstOrNull { it.githubId == id }
        val login = profile?.login ?: byAuthor[id]?.firstOrNull()?.author?.login
        profile?.displayName?.matchesStoreSearchTerm(searchTerm) == true ||
            login?.matchesStoreSearchTerm(searchTerm) == true
    } else emptyList()
    val hasExploreQuery = storeExploreSearchTerm(query.text).isNotEmpty()
    val isPreviewSection = !hasExploreQuery && effectiveTag == null && selectedTags.isEmpty() && section == null
    val labels = availableTabs.map { stringResource(it.label) }

    val gridState = rememberLazyGridState()
    val floatingBackdrop = rememberGlassFloatingActionBackdrop()
    val density = LocalDensity.current
    val emptyContentHeight by remember(gridState, shownTab, density, creatorId) {
        derivedStateOf {
            val layout = gridState.layoutInfo
            val header = layout.visibleItemsInfo.firstOrNull { it.key == "store-header" }
            val search = layout.visibleItemsInfo.firstOrNull { it.key == "store-search" }
            val last = when {
                creatorId != null -> layout.visibleItemsInfo.firstOrNull { it.key == "creator-profile" }
                shownTab == StoreTab.Explore -> search
                else -> header
            }
            val height = if (last == null) 0 else {
                layout.viewportEndOffset - layout.afterContentPadding -
                    (last.offset.y + last.size.height + layout.mainAxisItemSpacing)
            }
            with(density) { height.coerceAtLeast(0).toDp() }
        }
    }

    AppPullToRefreshBox(
        isRefreshing = loading && retry > 0,
        onRefresh = {
            if (!loading) {
                expireThemeStoreReads()
                downloads = library.downloads()
                follows = library.follows()
                retry++
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().glassFloatingActionContent(floatingBackdrop),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "store-header", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(bottom = 8.dp)) {
                    HeaderWithIcon(
                        title = title,
                        iconId = R.drawable.color_palette,
                        enabled = false,
                        modifier = Modifier,
                        showBetaBadge = creatorId == null && section == null,
                        onClick = {},
                    )
                    lastSync?.let {
                        StoreLastSync(
                            it,
                            Modifier.padding(bottom = 8.dp),
                            shimmer = activeTab != StoreTab.Creator && forceLoadingShimmerPreview(),
                        )
                    }
                    if (section == null && creatorId == null) SettingsDialogTabRow(
                        labels = labels,
                        selectedTabIndex = availableTabs.indexOf(activeTab),
                        equalWidthWhenFitting = true,
                        fittingLabelPadding = 8.dp,
                        followNestedTarget = false,
                        containerTestTag = "themeStoreTabs",
                    ) { tab = availableTabs[it] }
                }
            }
            if (offline && (creatorId != null || (section != null && !section.downloaded))) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    StoreEmptyMessage(R.string.theme_store_offline_unavailable, Modifier.heightIn(min = emptyContentHeight))
                }
                return@LazyVerticalGrid
            }
            val creatorsPending = creatorsLoading && loadedCreators == null
            val themesPending = loading && themes == null
            val retryCatalog = { expireThemeStoreReads(); retry++; Unit }
            if (creatorId != null) {
                val profile = creators.firstOrNull { it.githubId == creatorId }
                val authored = byAuthor[creatorId].orEmpty()
                val term = storeSearchTerm(query.text)
                val matchingThemes = authored.filter { it.title.matchesStoreSearchTerm(term) }
                val author = profile?.let { StoreAuthor(it.githubId, it.login) } ?: authored.firstOrNull()?.author
                if (author != null) {
                    item(key = "creator-profile", span = { GridItemSpan(maxLineSpan) }) {
                        CreatorProfileHeader(
                            onEditProfile = onEditCreator.takeIf { author.githubId == ownCreatorId },
                            profile = profile,
                            author = author,
                            themeCount = authored.size.takeIf { themes != null },
                            catalog = catalog,
                            followed = follows.any { it.githubId == creatorId },
                            themeCountPending = themesPending,
                            query = query,
                            onQueryChanged = { query = it },
                            official = verifiedIds?.contains(creatorId) == true,
                            publicationAlerts = publicationAlertsFor(creatorId),
                            canSetPublicationAlerts = themes != null,
                            onTogglePublicationAlerts = { togglePublicationAlerts(creatorId) },
                        ) {
                            toggleFollow(author)
                        }
                    }
                    if (themes != null && matchingThemes.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            if (query.text.isNotBlank()) {
                                SearchEmptyFeedback(query.text, empty = true, loading = loading || forceLoadingShimmerPreview())
                                SearchEmptySpace(Modifier.heightIn(min = emptyContentHeight))
                            } else {
                                StoreEmptyMessage(R.string.theme_store_no_published_themes, Modifier.heightIn(min = emptyContentHeight))
                            }
                        }
                    }
                    if (!loading && themes == null) {
                        item(key = "creator-theme-error", span = { GridItemSpan(maxLineSpan) }) {
                            StoreCatalogFailure(retryCatalog)
                        }
                    }
                    items(matchingThemes, key = { it.themeId }) { theme ->
                        ThemeStoreCard(theme, catalog, glass, suspended = "${theme.themeId}:theme" in pendingReports, modifier = Modifier.animateItem(fadeInSpec = tween(200), fadeOutSpec = tween(150)), official = verifiedIds?.contains(theme.author.githubId) == true, showCatalogInfo = true, refreshVersion = retry, download = downloadControl(theme), onWithdraw = if (theme.author.githubId == ownCreatorId) ({ withdrawalTheme = theme }) else null, onOpen = { onOpenTheme(theme) })
                    }
                }
                if (author == null) {
                    item(key = "creator-profile-status", span = { GridItemSpan(maxLineSpan) }) {
                        when {
                            themesPending || creatorsPending -> Column {
                                CreatorProfilePlaceholder()
                                UnifiedSearchBar(
                                    input = query,
                                    onInputChanged = { query = it },
                                    placeholderRes = R.string.theme_store_search_creator_themes,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    onClear = { query = TextFieldValue() },
                                )
                            }
                            creatorsFailed || themes == null -> StoreCatalogFailure(retryCatalog)
                            else -> StoreEmptyMessage(
                                R.string.theme_store_no_published_themes,
                                Modifier.heightIn(min = emptyContentHeight),
                            )
                        }
                    }
                }
                if (themesPending) {
                    items(4, key = { "creator-theme-loading:$it" }, contentType = { "theme-placeholder" }) {
                        ThemeStoreCardPlaceholder()
                    }
                }
                return@LazyVerticalGrid
            }
            if (section == ThemeStoreSection.Authors) {
                item(key = "store-search", span = { GridItemSpan(maxLineSpan) }) {
                    UnifiedSearchBar(
                        input = query,
                        onInputChanged = { query = it },
                        placeholderRes = R.string.search_title,
                        modifier = Modifier.fillMaxWidth(),
                        onClear = { query = TextFieldValue() },
                        textFieldTestTag = "themeStoreAuthorsSearch",
                    )
                }
                val visibleAuthors = if (searchTerm.isBlank()) authorIds else matchingCreators
                if (visibleAuthors.isEmpty()) {
                    if (themesPending || creatorsPending) {
                        items(5, key = { "creator-loading:$it" }, span = { GridItemSpan(maxLineSpan) }, contentType = { "creator-placeholder" }) {
                            CreatorCardPlaceholder()
                        }
                    } else {
                        item(key = "creator-list-status", span = { GridItemSpan(maxLineSpan) }) {
                            when {
                                creatorsFailed || themes == null -> StoreCatalogFailure(retryCatalog)
                                searchTerm.isNotBlank() -> {
                                    SearchEmptyFeedback(query.text, empty = true, loading = forceLoadingShimmerPreview())
                                    SearchEmptySpace(Modifier.heightIn(min = emptyContentHeight))
                                }
                                else -> StoreEmptyMessage(R.string.theme_store_no_results, Modifier.heightIn(min = emptyContentHeight))
                            }
                        }
                    }
                }
                items(visibleAuthors, key = { "creator:$it" }, span = { GridItemSpan(maxLineSpan) }) { id ->
                    Box(Modifier.animateItem()) {
                        CreatorCard(
                            creators.firstOrNull { it.githubId == id }, byAuthor[id].orEmpty(), catalog,
                            followed = follows.any { it.githubId == id }, official = verifiedIds?.contains(id) == true, onFollow = toggleFollow,
                            publicationAlerts = publicationAlertsFor(id),
                            canSetPublicationAlerts = themes != null,
                            onTogglePublicationAlerts = { togglePublicationAlerts(id) },
                            onEditProfile = onEditCreator.takeIf { id == ownCreatorId },
                        ) { onOpenCreator(id) }
                    }
                }
                return@LazyVerticalGrid
            }
            if (section?.downloaded == true ||
                (section == null && (shownTab == StoreTab.Downloaded || shownTab == StoreTab.Following))
            ) {
                item(key = "store-tab-search", span = { GridItemSpan(maxLineSpan) }) {
                    val downloaded = section?.downloaded == true || shownTab == StoreTab.Downloaded
                    Column(tabContentModifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        UnifiedSearchBar(
                            input = if (downloaded) downloadQuery else followingQuery,
                            onInputChanged = { if (downloaded) downloadQuery = it else followingQuery = it },
                            placeholderRes = if (downloaded) R.string.theme_store_search_downloaded else R.string.theme_store_search_following,
                            modifier = Modifier.fillMaxWidth(),
                            onClear = { if (downloaded) downloadQuery = TextFieldValue() else followingQuery = TextFieldValue() },
                            textFieldTestTag = if (downloaded) "themeStoreDownloadedSearch" else "themeStoreFollowingSearch",
                        )
                        if (downloaded) {
                            StoreTagFilters(downloadedTags, effectiveDownloadedTag, selectedDownloadedTags, { downloadedTag = it }) {
                                downloadedAdvancedTags = it
                                downloadedTag = null
                            }
                        }
                    }
                }
            }
            when (if (section?.downloaded == true) StoreTab.Downloaded else shownTab) {
                StoreTab.Explore -> {
                    item(key = "store-search", span = { GridItemSpan(maxLineSpan) }) {
                        Box(tabContentModifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                UnifiedSearchBar(
                                    input = query,
                                    onInputChanged = { query = it },
                                    placeholderRes = R.string.theme_store_search,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClear = { query = TextFieldValue() },
                                    textFieldTestTag = "themeStoreSearch",
                                )
                                StoreTagFilters(tags, effectiveTag, selectedTags, { tag = it }) {
                                    advancedTags = it
                                    tag = null
                                }
                            }
                        }
                    }
                    if (themes == null) {
                        if (loading) {
                            items(4, key = { "theme-loading:$it" }) { ThemeStoreCardPlaceholder(tabContentModifier) }
                        } else item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(tabContentModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                SettingsDescription(stringResource(R.string.theme_store_catalog_unavailable), Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                UiButton(onClick = { expireThemeStoreReads(); retry++ }) {
                                    androidx.compose.material3.Text(stringResource(R.string.common_action_retry))
                                }
                            }
                        }
                    }
                    val hasResults = filtered.isNotEmpty() || matchingCreators.isNotEmpty() ||
                        (isPreviewSection && authorIds.isNotEmpty())
                    when {
                        !hasResults && themes != null -> item(key = "store-search-empty", span = { GridItemSpan(maxLineSpan) }) {
                            Box(tabContentModifier.fillMaxWidth()) {
                                if (hasExploreQuery) {
                                    SearchEmptyFeedback(query.text, empty = true, loading = loading || creatorsPending || forceLoadingShimmerPreview())
                                    SearchEmptySpace(Modifier.heightIn(min = emptyContentHeight))
                                } else {
                                    StoreEmptyMessage(R.string.theme_store_no_results, Modifier.heightIn(min = emptyContentHeight))
                                }
                            }
                        }
                        hasResults -> {
                            if (isPreviewSection) {
                                ThemeStoreSection.entries.filter { it != ThemeStoreSection.Authors && !it.downloaded }.forEach { category ->
                                    val matches = filtered.filter { category.includes(it, verifiedIds) }
                                    if (matches.isNotEmpty()) {
                                        item(key = "section:${category.routeKey}", span = { GridItemSpan(maxLineSpan) }) {
                                            Box(tabContentModifier.animateItem().fillMaxWidth().padding(top = if (category == ThemeStoreSection.New) 0.dp else 20.dp)) {
                                                StoreSectionHeader(category.label) { onSeeAll(category) }
                                            }
                                        }
                                        items(matches.take(4), key = { "${category.routeKey}:${it.themeId}" }) { theme ->
                                            ThemeStoreCard(theme, catalog, glass, suspended = "${theme.themeId}:theme" in pendingReports, official = verifiedIds?.contains(theme.author.githubId) == true, modifier = tabContentModifier.animateItem(), showCatalogInfo = true, refreshVersion = retry, download = downloadControl(theme), onOpen = { onOpenTheme(theme) })
                                        }
                                    }
                                }
                                if (authorIds.isNotEmpty()) {
                                    item(key = "section:authors", span = { GridItemSpan(maxLineSpan) }) {
                                        Box(tabContentModifier.animateItem().fillMaxWidth().padding(top = 20.dp)) {
                                            StoreSectionHeader(R.string.theme_store_authors) { onSeeAll(ThemeStoreSection.Authors) }
                                        }
                                    }
                                    items(authorIds.take(4), key = { "creator:$it" }, span = { GridItemSpan(maxLineSpan) }) { id ->
                                        Box(tabContentModifier.animateItem()) {
                                            CreatorCard(
                                                creators.firstOrNull { it.githubId == id }, byAuthor[id].orEmpty(), catalog,
                                                followed = follows.any { it.githubId == id }, official = verifiedIds?.contains(id) == true, onFollow = toggleFollow,
                                                publicationAlerts = publicationAlertsFor(id),
                                                canSetPublicationAlerts = themes != null,
                                                onTogglePublicationAlerts = { togglePublicationAlerts(id) },
                                                onEditProfile = onEditCreator.takeIf { id == ownCreatorId },
                                            ) { onOpenCreator(id) }
                                        }
                                    }
                                }
                            } else {
                                if (matchingCreators.isNotEmpty()) {
                                    item(key = "search-creators", span = { GridItemSpan(maxLineSpan) }) {
                                        Box(tabContentModifier.animateItem(fadeInSpec = tween(200), fadeOutSpec = tween(150))) {
                                            StoreSectionHeader(R.string.theme_store_authors)
                                        }
                                    }
                                    items(matchingCreators, key = { "creator-result:$it" }, span = { GridItemSpan(maxLineSpan) }) { id ->
                                        Box(tabContentModifier.animateItem(fadeInSpec = tween(200), fadeOutSpec = tween(150))) {
                                            CreatorCard(
                                                creators.firstOrNull { it.githubId == id }, byAuthor[id].orEmpty(), catalog,
                                                followed = follows.any { it.githubId == id },
                                                official = verifiedIds?.contains(id) == true,
                                                onFollow = toggleFollow,
                                                publicationAlerts = publicationAlertsFor(id),
                                                canSetPublicationAlerts = themes != null,
                                                onTogglePublicationAlerts = { togglePublicationAlerts(id) },
                                                onEditProfile = onEditCreator.takeIf { id == ownCreatorId },
                                            ) { onOpenCreator(id) }
                                        }
                                    }
                                }
                                if (section == null && filtered.isNotEmpty()) item(key = "theme-search-results", span = { GridItemSpan(maxLineSpan) }) {
                                    Box(tabContentModifier.animateItem().fillMaxWidth()) {
                                        StoreSectionHeader(R.string.theme_store_results)
                                    }
                                }
                                items(filtered, key = { it.themeId }) { theme ->
                                    ThemeStoreCard(theme, catalog, glass, suspended = "${theme.themeId}:theme" in pendingReports, official = verifiedIds?.contains(theme.author.githubId) == true, modifier = tabContentModifier.animateItem(fadeInSpec = tween(200), fadeOutSpec = tween(150)), showCatalogInfo = true, refreshVersion = retry, download = downloadControl(theme), onOpen = { onOpenTheme(theme) })
                                }
                            }
                        }
                        else -> Unit
                    }
                }
                StoreTab.Downloaded -> {
                    val term = storeSearchTerm(downloadQuery.text)
                    val groups = (listOf(builtIn) + downloads).filter { saved ->
                        val themeTags = saved.theme?.tags.orEmpty().canonicalStoreTags()
                        saved.name.matchesStoreSearchTerm(term) &&
                            (effectiveDownloadedTag == null || effectiveDownloadedTag in themeTags) &&
                            themeTags.containsAll(selectedDownloadedTags)
                    }.groupBy { saved ->
                        when {
                            saved.id == builtIn.id -> ThemeStoreSection.Stock
                            saved.imported || saved.theme == null -> ThemeStoreSection.Imported
                            saved.theme.author.githubId in verifiedIds.orEmpty() -> ThemeStoreSection.DownloadedOfficial
                            else -> ThemeStoreSection.DownloadedStore
                        }
                    }
                    val downloadedGroups = groups
                    val categories = ThemeStoreSection.entries.filter {
                        it.downloaded && (section == null || section == it) && !downloadedGroups[it].isNullOrEmpty()
                    }
                    if (categories.isEmpty()) {
                        item(key = "downloaded-search-empty", span = { GridItemSpan(maxLineSpan) }) {
                            Box(tabContentModifier.fillMaxWidth()) {
                                if (downloadQuery.text.isNotBlank()) {
                                    SearchEmptyFeedback(downloadQuery.text, empty = true, loading = forceLoadingShimmerPreview())
                                    SearchEmptySpace(Modifier.heightIn(min = emptyContentHeight))
                                } else {
                                    StoreEmptyMessage(R.string.theme_store_no_results, Modifier.heightIn(min = emptyContentHeight))
                                }
                            }
                        }
                    }
                    categories.forEach { category ->
                        val group = library.recentlySavedFirst(downloadedGroups.getValue(category).distinctBy { it.id }, themes.orEmpty())
                        if (section == null) {
                            item(key = "download-section-${category.routeKey}", span = { GridItemSpan(maxLineSpan) }) {
                                Box(tabContentModifier.animateItem().fillMaxWidth()) {
                                    StoreSectionHeader(category.label) { onSeeAll(category) }
                                }
                            }
                        }
                        items(group, key = { "${category.routeKey}:${it.id}" }) { saved ->
                            val theme = saved.theme
                            if (theme == null) {
                                ThemeCardSurface(modifier = tabContentModifier.animateItem()) {
                                    Box(Modifier.padding(12.dp)) {
                                        if (forceLoadingShimmerPreview()) StoreLoadingLine(saved.name, typography().s)
                                        else StoreText(saved.name, marquee = true)
                                    }
                                }
                            } else {
                                val stock = saved.id == builtIn.id
                                ThemeStoreCard(
                                    theme, catalog, glass,
                                    official = stock || (!saved.imported && theme.author.githubId in verifiedIds.orEmpty()),
                                    showLocalCreator = stock || saved.imported,
                                    modifier = tabContentModifier.animateItem(),
                                    refreshVersion = retry,
                                    onOpen = { onOpenTheme(theme) },
                                )
                            }
                        }
                    }
                }
                StoreTab.Following -> {
                    val term = storeAuthorSearchTerm(followingQuery.text)
                    val visibleFollows = follows.filterNot { it.githubId == ownCreatorId }
                    val matching = visibleFollows.filter { author ->
                        author.login.matchesStoreSearchTerm(term) ||
                            creatorDisplayNames[author.githubId]?.matchesStoreSearchTerm(term) == true
                    }.sortedWith(compareBy(creatorNameCollator) { author ->
                        creatorDisplayNames[author.githubId] ?: author.login
                    })
                    if (matching.isEmpty()) {
                        item(key = "following-search-empty", span = { GridItemSpan(maxLineSpan) }) {
                            Box(tabContentModifier.fillMaxWidth()) {
                                if (term.isNotBlank()) {
                                    SearchEmptyFeedback(followingQuery.text, empty = true, loading = creatorsPending || forceLoadingShimmerPreview())
                                    SearchEmptySpace(Modifier.heightIn(min = emptyContentHeight))
                                } else {
                                    StoreEmptyMessage(R.string.theme_store_no_follows, Modifier.heightIn(min = emptyContentHeight))
                                }
                            }
                        }
                    } else {
                        items(matching, key = { "author:${it.githubId}" }, span = { GridItemSpan(maxLineSpan) }) { author ->
                            val id = author.githubId
                            Box(tabContentModifier.animateItem().fillMaxWidth()) {
                                CreatorCard(
                                    creators.firstOrNull { it.githubId == id }, byAuthor[id].orEmpty(), catalog,
                                    followed = true,
                                    official = verifiedIds?.contains(id) == true,
                                    onFollow = toggleFollow,
                                    publicationAlerts = publicationAlertsFor(id),
                                    canSetPublicationAlerts = themes != null,
                                    onTogglePublicationAlerts = { togglePublicationAlerts(id) },
                                    fallbackAuthor = author,
                                ) { onOpenCreator(id) }
                            }
                        }
                    }
                }
                StoreTab.Creator -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(tabContentModifier.fillMaxWidth()) {
                        ThemeStoreCreator(onEditCreator, retry, themes, themesPending, creators, creatorsPending, creatorsFailed, downloads.filter { it.imported && it.theme != null }, onOpenCreator, onSubmitTheme, creatorTarget)
                    }
                }
            }
        }
        GlassFloatingActions(floatingBackdrop) {
            SettingsScrollToTop(gridState, modifier = Modifier.testTag("themeStore.scrollToTop"))
        }
    }
}

@Composable
internal fun StoreTagFilters(
    tags: List<String>,
    effectiveTag: String?,
    selectedTags: List<String>,
    onTag: (String?) -> Unit,
    onAdvanced: (List<String>) -> Unit,
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val shimmer = forceLoadingShimmerPreview()
    if (tags.isNotEmpty()) {
        val filtersActive = selectedTags.isNotEmpty()
        val filterColor = if (filtersActive) colorPalette().red else colorPalette().accent
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (filtersActive) {
                GlassHomeChips(
                    chips = listOf(true to stringResource(R.string.theme_store_filtered)),
                    currentValue = true,
                    onValueUpdate = { showFilters = true },
                    modifier = Modifier.weight(1f),
                    shimmer = shimmer,
                )
            } else {
                GlassHomeChips(
                    chips = listOf(null to stringResource(R.string.common_option_all)) +
                        tags.map { it to storeTagLabel(it) },
                    currentValue = effectiveTag,
                    onValueUpdate = onTag,
                    modifier = Modifier.weight(1f),
                    shimmer = shimmer,
                )
            }
            StoreIconAction(
                onClick = { showFilters = true },
                modifier = Modifier.padding(start = 6.dp),
                size = 32.dp,
                border = BorderStroke(
                    1.dp,
                    filterColor.copy(alpha = if (filtersActive) 1f else 0.35f),
                ),
            ) {
                Icon(
                    painterResource(R.drawable.settings),
                    contentDescription = stringResource(R.string.theme_store_filters),
                    tint = colorPalette().text,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    if (showFilters) {
        var draft by remember { mutableStateOf((selectedTags + listOfNotNull(effectiveTag)).distinct()) }
        DefaultDialog(
            title = stringResource(R.string.theme_store_filters),
            onDismiss = { showFilters = false },
            footer = {
                ButtonRow(listOf(
                    DialogButton(stringResource(R.string.common_action_cancel), { showFilters = false }, danger = true),
                    DialogButton(stringResource(R.string.common_action_apply), {
                        onAdvanced(draft)
                        showFilters = false
                    }, primary = true),
                ))
            },
        ) {
            tags.forEach { filter ->
                val checked = filter in draft
                val onCheckedChange: (Boolean) -> Unit = { selected ->
                    draft = if (selected) draft + filter else draft - filter
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { StoreText(storeTagLabel(filter), style = typography().xs) }
                    Box(Modifier.clearAndSetSemantics { hideFromAccessibility() }) {
                        Switch(checked = checked, onCheckedChange = onCheckedChange)
                    }
                }
            }
        }
    }
}

@Composable
internal fun StoreSectionHeader(label: Int, themeCount: Int? = null, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val title = if (themeCount == null) stringResource(label) else stringResource(label, themeCount)
        val style = typography().xs.copy(color = colorPalette().text, fontWeight = FontWeight.SemiBold)
        if (themeCount != null && forceLoadingShimmerPreview()) StoreLoadingLine(title, style)
        else BasicText(title, style = style, modifier = Modifier.semantics { heading() })
        if (onSeeAll != null) {
            StoreSeeButton(R.string.theme_store_see_all, onSeeAll)
        }
    }
}

@Composable
private fun StoreCatalogFailure(onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SettingsDescription(stringResource(R.string.theme_store_catalog_unavailable), textAlign = TextAlign.Center)
        UiButton(onClick = onRetry) {
            androidx.compose.material3.Text(stringResource(R.string.common_action_retry))
        }
    }
}

@Composable
private fun StoreEmptyMessage(textRes: Int, modifier: Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SettingsDescription(stringResource(textRes), Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ThemeStoreCreator(
    onEditCreator: () -> Unit,
    refreshVersion: Int,
    themes: List<StoreTheme>?,
    themesPending: Boolean,
    creators: List<CreatorProfile>,
    creatorsPending: Boolean,
    creatorsFailed: Boolean,
    localThemes: List<SavedTheme>,
    onOpenCreator: (Long) -> Unit,
    onSubmitTheme: (String) -> Unit,
    target: ThemeStoreCreatorTarget?,
) {
    val ownId = signedInCreatorId()
    val visibleTarget = target.visibleTarget(hasCreatorAccount = ownId != null)
    val canPublish = creators.any { it.githubId == ownId }
    val offline by OfflineMode.rememberEnabled()
    val owned = themes.orEmpty().filter { it.author.githubId == ownId }
        .sortedByDescending { it.revision }.distinctBy { it.themeId }
    var editPicker by rememberSaveable { mutableStateOf(false) }
    var editLocalPicker by rememberSaveable { mutableStateOf(false) }
    Column {
        CompositionLocalProvider(LocalSettingsCardGroupTitle provides R.string.theme_store_group_publishing_access) {
            Column(creatorTargetModifier(visibleTarget, ThemeStoreCreatorTarget.PublishingAccess)) {
                if (target != visibleTarget) {
                    SettingsDescription(stringResource(R.string.theme_store_creator_action_sign_in_required))
                }
                GitHubAccountCard(horizontalPadding = 0.dp, showAppInstallation = true)
            }
        }
        CreatorProfileSettingsEntry(onEditCreator, modifier = creatorTargetModifier(visibleTarget, ThemeStoreCreatorTarget.Profile)) {
            if (ownId != null) {
                OtherSettingsEntry(
                    modifier = creatorTargetModifier(visibleTarget, ThemeStoreCreatorTarget.MyThemes),
                    title = stringResource(R.string.theme_store_creator_my_themes),
                    text = themes?.count { it.author.githubId == ownId }?.let {
                        stringResource(R.string.theme_store_author_theme_count, it)
                    }.orEmpty(),
                    icon = R.drawable.ui,
                    isEnabled = !offline,
                    descriptionLoading = !offline && themesPending,
                    onClick = { onOpenCreator(ownId) },
                )
                OtherSettingsEntry(
                    modifier = creatorTargetModifier(visibleTarget, ThemeStoreCreatorTarget.Publish),
                    title = stringResource(R.string.theme_store_creator_publish_theme),
                    text = when {
                        canPublish -> stringResource(R.string.theme_store_publish_description)
                        creatorsFailed && !offline -> stringResource(R.string.theme_store_catalog_unavailable)
                        else -> stringResource(R.string.theme_store_published_creator_required)
                    },
                    icon = R.drawable.ui,
                    isEnabled = !offline && canPublish,
                    descriptionLoading = !offline && creatorsPending,
                    onClick = { if (canPublish) onSubmitTheme("new") },
                )
                OtherSettingsEntry(
                    modifier = creatorTargetModifier(visibleTarget, ThemeStoreCreatorTarget.EditPublished),
                    title = stringResource(R.string.theme_store_creator_edit_theme),
                    text = stringResource(R.string.theme_store_edit_description),
                    icon = R.drawable.ui,
                    isEnabled = !offline && owned.isNotEmpty(),
                    onClick = { editPicker = true },
                )
            }
        }
        Box(creatorTargetModifier(visibleTarget, ThemeStoreCreatorTarget.Submissions)) {
            CreatorSubmissionsCard(refreshVersion)
        }
        SettingsSectionCard(
            title = stringResource(R.string.theme_store_local_themes),
            icon = R.drawable.folder,
            horizontalPadding = 0.dp,
            groupTitleRes = R.string.theme_store_group_local_editing,
            content = {
                OtherSettingsEntry(
                    modifier = creatorTargetModifier(visibleTarget, ThemeStoreCreatorTarget.CreateLocal),
                    title = stringResource(R.string.theme_store_create_local),
                    text = stringResource(R.string.theme_store_create_local_description),
                    icon = R.drawable.ui,
                    onClick = { onSubmitTheme("local-new") },
                )
                OtherSettingsEntry(
                    modifier = creatorTargetModifier(visibleTarget, ThemeStoreCreatorTarget.EditLocal),
                    title = stringResource(R.string.theme_store_edit_local),
                    text = stringResource(R.string.theme_store_edit_local_description),
                    icon = R.drawable.ui,
                    isEnabled = localThemes.isNotEmpty(),
                    onClick = { editLocalPicker = true },
                )
            },
        )
    }
    if (editLocalPicker) DefaultDialog(
        title = stringResource(R.string.theme_store_edit_local),
        onDismiss = { editLocalPicker = false },
        footer = {
            ButtonRow(listOf(DialogButton(stringResource(R.string.common_action_close), { editLocalPicker = false }, danger = true)))
        },
    ) {
        localThemes.forEach { local ->
            val theme = checkNotNull(local.theme)
            OtherSettingsEntry(local.name, theme.description, R.drawable.ui, onClick = {
                editLocalPicker = false
                onSubmitTheme("local:${local.id}")
            })
        }
    }
    if (editPicker) DefaultDialog(
        title = stringResource(R.string.theme_store_creator_edit_theme),
        onDismiss = { editPicker = false },
        footer = {
            ButtonRow(listOf(DialogButton(stringResource(R.string.common_action_close), { editPicker = false }, danger = true)))
        },
    ) {
        owned.forEach { theme ->
            val revision = stringResource(R.string.theme_store_revision, theme.revision)
            OtherSettingsEntry(theme.title, revision, R.drawable.ui, onClick = {
                editPicker = false
                onSubmitTheme(theme.themeId)
            })
        }
    }
}
