package app.n_zik.android.components.settings.appearance.preview

import android.content.ActivityNotFoundException
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import app.n_zik.android.components.ui.FullPageLoader
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.n_zik.android.core.coil.ImageCacheFactory
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.n_zik.android.R
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.colorPalette
import app.n_zik.android.components.artist.ArtistFollowButton
import app.n_zik.android.components.ui.screens.profiles.ProfileFaceAvatar
import app.n_zik.android.utils.FaceAvatar
import app.n_zik.android.components.AppPullToRefreshBox
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DefaultDialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.settings.SettingsDialogTabRow
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.LiquidButton
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassImageRim
import app.n_zik.android.components.glass.glassSettingsCardRim
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.usesGlassContour
import androidx.compose.material3.ButtonDefaults
import app.n_zik.android.components.navigation.Skeleton
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import app.n_zik.android.components.search.UnifiedSearchBar
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.ui.toggles.Switch
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.TransitionEffect
import app.n_zik.android.components.settings.common.SettingsCardSurface
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.ui.UiButton
import app.n_zik.android.components.ui.UiTextButton
import app.n_zik.android.components.ui.screens.home.GlassHomeChips
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.surfaceColor
import app.n_zik.android.typography
import app.n_zik.android.ui.components.themed.HeaderWithIcon
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.uiRoundnessShape
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

internal val ThemeStoreGutter = 12.dp

@Composable
internal fun ThemeStoreScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit,
    section: ThemeStoreSection? = null,
    creatorId: Long? = null,
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
        ThemeStoreLanding(title, rememberPageChromeContentPadding(PaddingValues(start = ThemeStoreGutter, end = ThemeStoreGutter, bottom = bottom)), section, creatorId,
            onOpenCreator = { id -> navController.navigate("${NavRoutes.themeStoreCreator.name}/$id") },
            onEditCreator = { navController.navigate(NavRoutes.themeStoreCreatorSettings.name) { launchSingleTop = true } },
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
private fun ThemeStoreLanding(title: String, padding: PaddingValues, section: ThemeStoreSection?, creatorId: Long?, onOpenCreator: (Long) -> Unit, onSeeAll: (ThemeStoreSection) -> Unit, onOpenTheme: (StoreTheme) -> Unit, onEditCreator: () -> Unit, onSubmitTheme: (String) -> Unit) {
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
    var tab by rememberSaveable { mutableStateOf(StoreTab.Explore) }
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
    val builtIn = remember(context) { bundledDefaultTheme(context) }
    val verifiedIds = rememberVerifiedCreatorIds(catalog, retry)
    var loading by remember { mutableStateOf(true) }
    var themes by remember { mutableStateOf<List<StoreTheme>?>(null) }
    var downloads by remember(library) { mutableStateOf(library.downloads()) }
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
    val creators by produceState<List<CreatorProfile>>(emptyList(), catalog, retry) {
        if (offline) return@produceState
        try {
            value = catalog.creators()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("ThemeStore").w(error, "Could not load creators")
        }
    }
    val glass = rememberGlassSettings().enabled
    var lastSync by remember(catalog) { mutableStateOf<Long?>(null) }

    LaunchedEffect(catalog, retry, library, owner) {
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
            if (getActiveProfile(context) == profileId) notifyThemeStoreAlerts(context, library, current.themes)
            launch {
                catalog.downloadAutomaticUpdates(context, library, current.themes) {
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
    val searchTerm = query.text.trim().removePrefix("@")
    val matchingCreators = if (section == null && searchTerm.isNotBlank()) authorIds.filter { id ->
        val profile = creators.firstOrNull { it.githubId == id }
        val login = profile?.login ?: byAuthor[id]?.firstOrNull()?.author?.login
        profile?.displayName?.contains(searchTerm, ignoreCase = true) == true ||
            login?.contains(searchTerm, ignoreCase = true) == true
    } else emptyList()
    val isPreviewSection = query.text.isBlank() && effectiveTag == null && selectedTags.isEmpty() && section == null
    val labels = availableTabs.map { stringResource(it.label) }

    val gridState = rememberLazyGridState()
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
            modifier = Modifier.fillMaxSize(),
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
                    lastSync?.let { StoreLastSync(it, Modifier.padding(bottom = 8.dp)) }
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
            if (creatorId != null) {
                val profile = creators.firstOrNull { it.githubId == creatorId }
                val authored = byAuthor[creatorId].orEmpty()
                if (loading && themes == null && profile == null) {
                    item(span = { GridItemSpan(maxLineSpan) }) { FullPageLoader() }
                    return@LazyVerticalGrid
                }
                val matchingThemes = authored.filter { it.title.contains(query.text.trim(), ignoreCase = true) }
                val author = profile?.let { StoreAuthor(it.githubId, it.login) } ?: authored.firstOrNull()?.author
                if (author != null) {
                    item(key = "creator-profile", span = { GridItemSpan(maxLineSpan) }) {
                        CreatorProfileHeader(
                            onEditProfile = onEditCreator.takeIf { author.githubId == ownCreatorId },
                            profile, author, authored.size, catalog, follows.any { it.githubId == creatorId },
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
                            StoreEmptyMessage(
                                if (authored.isEmpty()) R.string.theme_store_no_published_themes else R.string.theme_store_no_results,
                                Modifier.heightIn(min = emptyContentHeight),
                            )
                        }
                    }
                    items(matchingThemes, key = { it.themeId }) { theme ->
                        ThemeStoreCard(theme, catalog, glass, suspended = "${theme.themeId}:theme" in pendingReports, modifier = Modifier.animateItem(fadeInSpec = tween(200), fadeOutSpec = tween(150)), official = verifiedIds?.contains(theme.author.githubId) == true, showCatalogInfo = true, refreshVersion = retry, onWithdraw = if (theme.author.githubId == ownCreatorId) ({ withdrawalTheme = theme }) else null, onOpen = { onOpenTheme(theme) })
                    }
                }
                return@LazyVerticalGrid
            }
            if (section == ThemeStoreSection.Authors) {
                if (loading && authorIds.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { FullPageLoader() }
                    return@LazyVerticalGrid
                }
                items(authorIds, key = { "creator:$it" }, span = { GridItemSpan(maxLineSpan) }) { id ->
                    CreatorCard(
                        creators.firstOrNull { it.githubId == id }, byAuthor[id].orEmpty(), catalog,
                        followed = follows.any { it.githubId == id }, official = verifiedIds?.contains(id) == true, onFollow = toggleFollow,
                        publicationAlerts = publicationAlertsFor(id),
                        canSetPublicationAlerts = themes != null,
                        onTogglePublicationAlerts = { togglePublicationAlerts(id) },
                        onEditProfile = onEditCreator.takeIf { id == ownCreatorId },
                    ) { onOpenCreator(id) }
                }
                return@LazyVerticalGrid
            }
            if (shownTab != StoreTab.Explore && section == null) {
                if (shownTab == StoreTab.Downloaded || shownTab == StoreTab.Following) {
                    item(key = "store-tab-search", span = { GridItemSpan(maxLineSpan) }) {
                        val downloaded = shownTab == StoreTab.Downloaded
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
                    when {
                        themes == null -> item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(tabContentModifier.fillMaxWidth()) {
                                if (loading) {
                                    FullPageLoader()
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        SettingsDescription(stringResource(R.string.theme_store_catalog_unavailable), Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                        UiButton(onClick = { expireThemeStoreReads(); retry++ }) {
                                            androidx.compose.material3.Text(stringResource(R.string.common_action_retry))
                                        }
                                    }
                                }
                            }
                        }
                        filtered.isEmpty() && matchingCreators.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(tabContentModifier.fillMaxWidth()) {
                                StoreEmptyMessage(R.string.theme_store_no_results, Modifier.heightIn(min = emptyContentHeight))
                            }
                        }
                        else -> {
                            if (isPreviewSection) {
                                ThemeStoreSection.entries.filter { it != ThemeStoreSection.Authors && !it.downloaded }.forEach { category ->
                                    val matches = filtered.filter { category.includes(it, verifiedIds) }
                                    if (matches.isNotEmpty()) {
                                        item(key = "section:${category.routeKey}", span = { GridItemSpan(maxLineSpan) }) {
                                            Box(tabContentModifier.fillMaxWidth().padding(top = if (category == ThemeStoreSection.New) 0.dp else 20.dp)) {
                                                StoreSectionHeader(category.label) { onSeeAll(category) }
                                            }
                                        }
                                        items(matches.take(4), key = { "${category.routeKey}:${it.themeId}" }) { theme ->
                                            ThemeStoreCard(theme, catalog, glass, suspended = "${theme.themeId}:theme" in pendingReports, official = verifiedIds?.contains(theme.author.githubId) == true, modifier = tabContentModifier, showCatalogInfo = true, refreshVersion = retry, onOpen = { onOpenTheme(theme) })
                                        }
                                    }
                                }
                                if (authorIds.isNotEmpty()) {
                                    item(key = "section:authors", span = { GridItemSpan(maxLineSpan) }) {
                                        Box(tabContentModifier.fillMaxWidth().padding(top = 20.dp)) {
                                            StoreSectionHeader(R.string.theme_store_authors) { onSeeAll(ThemeStoreSection.Authors) }
                                        }
                                    }
                                    items(authorIds.take(4), key = { "creator:$it" }, span = { GridItemSpan(maxLineSpan) }) { id ->
                                        Box(tabContentModifier) {
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
                                if (section == null && filtered.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(tabContentModifier.fillMaxWidth()) {
                                        StoreSectionHeader(R.string.theme_store_results)
                                    }
                                }
                                items(filtered, key = { it.themeId }) { theme ->
                                    ThemeStoreCard(theme, catalog, glass, suspended = "${theme.themeId}:theme" in pendingReports, official = verifiedIds?.contains(theme.author.githubId) == true, modifier = tabContentModifier.animateItem(fadeInSpec = tween(200), fadeOutSpec = tween(150)), showCatalogInfo = true, refreshVersion = retry, onOpen = { onOpenTheme(theme) })
                                }
                            }
                        }
                    }
                }
                StoreTab.Downloaded -> {
                    val term = downloadQuery.text.trim()
                    val groups = (listOf(builtIn) + downloads).filter { saved ->
                        val themeTags = saved.theme?.tags.orEmpty().canonicalStoreTags()
                        saved.name.contains(term, ignoreCase = true) &&
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
                    val downloadedGroups = groups + (
                        ThemeStoreSection.DownloadedOfficial to
                            (groups[ThemeStoreSection.Stock].orEmpty() + groups[ThemeStoreSection.DownloadedOfficial].orEmpty())
                    )
                    val categories = ThemeStoreSection.entries.filter {
                        it.downloaded && (section == null || section == it) && !downloadedGroups[it].isNullOrEmpty()
                    }
                    if (categories.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(tabContentModifier.fillMaxWidth()) {
                                StoreEmptyMessage(R.string.theme_store_no_results, Modifier.heightIn(min = emptyContentHeight))
                            }
                        }
                    }
                    categories.forEach { category ->
                        val group = library.recentlySavedFirst(downloadedGroups.getValue(category).distinctBy { it.id }, themes.orEmpty())
                        if (section == null) {
                            item(key = "download-section-${category.routeKey}", span = { GridItemSpan(maxLineSpan) }) {
                                Box(tabContentModifier.fillMaxWidth()) {
                                    StoreSectionHeader(category.label) { onSeeAll(category) }
                                }
                            }
                        }
                        items(group, key = { "${category.routeKey}:${it.id}" }) { saved ->
                            val theme = saved.theme
                            if (theme == null) {
                                ThemeCardSurface(modifier = tabContentModifier) {
                                    Box(Modifier.padding(12.dp)) { StoreText(saved.name, marquee = true) }
                                }
                            } else {
                                val stock = saved.id == builtIn.id
                                ThemeStoreCard(
                                    theme, catalog, glass,
                                    official = stock || (!saved.imported && theme.author.githubId in verifiedIds.orEmpty()),
                                    showLocalCreator = stock || saved.imported,
                                    modifier = tabContentModifier,
                                    refreshVersion = retry,
                                    onOpen = { onOpenTheme(theme) },
                                )
                            }
                        }
                    }
                }
                StoreTab.Following -> {
                    val term = followingQuery.text.trim().removePrefix("@")
                    val visibleFollows = follows.filterNot { it.githubId == ownCreatorId }
                    val matching = visibleFollows.filter { author ->
                        author.login.contains(term, ignoreCase = true) ||
                            creatorDisplayNames[author.githubId]?.contains(term, ignoreCase = true) == true
                    }.sortedWith(compareBy(creatorNameCollator) { author ->
                        creatorDisplayNames[author.githubId] ?: author.login
                    })
                    if (matching.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(tabContentModifier.fillMaxWidth()) {
                                StoreEmptyMessage(if (visibleFollows.isEmpty()) R.string.theme_store_no_follows else R.string.theme_store_no_results, Modifier.heightIn(min = emptyContentHeight))
                            }
                        }
                    } else {
                        items(matching, key = { "author:${it.githubId}" }, span = { GridItemSpan(maxLineSpan) }) { author ->
                            val id = author.githubId
                            Box(tabContentModifier.fillMaxWidth()) {
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
                        ThemeStoreCreator(onEditCreator, retry, themes, downloads.filter { it.imported && it.theme != null }, onOpenCreator, onSubmitTheme)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreTagFilters(
    tags: List<String>,
    effectiveTag: String?,
    selectedTags: List<String>,
    onTag: (String?) -> Unit,
    onAdvanced: (List<String>) -> Unit,
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
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
                )
            } else {
                GlassHomeChips(
                    chips = listOf(null to stringResource(R.string.common_option_all)) +
                        tags.map { it to storeTagLabel(it) },
                    currentValue = effectiveTag,
                    onValueUpdate = onTag,
                    modifier = Modifier.weight(1f),
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { StoreText(storeTagLabel(filter), style = typography().xs) }
                    Switch(checked = filter in draft, onCheckedChange = { checked ->
                        draft = if (checked) draft + filter else draft - filter
                    })
                }
            }
        }
    }
}

@Composable
private fun StoreSectionHeader(label: Int, themeCount: Int? = null, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            if (themeCount == null) stringResource(label) else stringResource(label, themeCount),
            style = typography().xs.copy(color = colorPalette().text, fontWeight = FontWeight.SemiBold),
        )
        if (onSeeAll != null) {
            StoreSeeButton(R.string.theme_store_see_all, onSeeAll)
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
private fun ThemeStoreCreator(onEditCreator: () -> Unit, refreshVersion: Int, themes: List<StoreTheme>?, localThemes: List<SavedTheme>, onOpenCreator: (Long) -> Unit, onSubmitTheme: (String) -> Unit) {
    val ownId = signedInCreatorId()
    val offline by OfflineMode.rememberEnabled()
    val owned = themes.orEmpty().filter { it.author.githubId == ownId }
        .sortedByDescending { it.revision }.distinctBy { it.themeId }
    var editPicker by rememberSaveable { mutableStateOf(false) }
    var editLocalPicker by rememberSaveable { mutableStateOf(false) }
    Column {
        app.n_zik.android.components.settings.accounts.GitHubAccountCard(horizontalPadding = 0.dp, showAppInstallation = true)
        CreatorProfileSettingsEntry(onEditCreator) {
            if (ownId != null) {
                OtherSettingsEntry(
                    title = stringResource(R.string.theme_store_creator_my_themes),
                    text = themes?.count { it.author.githubId == ownId }?.let {
                        stringResource(R.string.theme_store_author_theme_count, it)
                    }.orEmpty(),
                    icon = R.drawable.ui,
                    isEnabled = !offline,
                    onClick = { onOpenCreator(ownId) },
                )
                OtherSettingsEntry(
                    title = stringResource(R.string.theme_store_creator_publish_theme),
                    text = stringResource(R.string.theme_store_publish_description),
                    icon = R.drawable.ui,
                    isEnabled = !offline,
                    onClick = { onSubmitTheme("new") },
                )
                OtherSettingsEntry(
                    title = stringResource(R.string.theme_store_creator_edit_theme),
                    text = stringResource(R.string.theme_store_edit_description),
                    icon = R.drawable.ui,
                    isEnabled = !offline && owned.isNotEmpty(),
                    onClick = { editPicker = true },
                )
            }
        }
        CreatorSubmissionsCard(refreshVersion)
        SettingsSectionCard(title = stringResource(R.string.theme_store_local_themes), icon = R.drawable.folder, horizontalPadding = 0.dp, content = {
            OtherSettingsEntry(
                title = stringResource(R.string.theme_store_create_local),
                text = stringResource(R.string.theme_store_create_local_description),
                icon = R.drawable.ui,
                onClick = { onSubmitTheme("local-new") },
            )
            OtherSettingsEntry(
                title = stringResource(R.string.theme_store_edit_local),
                text = stringResource(R.string.theme_store_edit_local_description),
                icon = R.drawable.ui,
                isEnabled = localThemes.isNotEmpty(),
                onClick = { editLocalPicker = true },
            )
        })
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

internal fun launchThemePicker(launch: () -> Unit) {
    try {
        launch()
    } catch (error: ActivityNotFoundException) {
        Toaster.e(R.string.common_error_no_document_handler)
    }
}

// Same portrait frame as the official catalogue assets.
internal const val StorePreviewAspectRatio = 780f / 1736f

internal data class StoreCardPreview(val file: File, val aspectRatio: Float)

@Composable
internal fun rememberStorePreview(theme: StoreTheme, preview: StorePreview?, catalog: ThemeStoreCatalog, refreshVersion: Int): StoreCardPreview? {
    return androidx.compose.runtime.key(catalog, preview?.sha256) {
        val cached = preview?.let { catalog.previewMetadata.get(it.sha256) }
        val image by produceState(cached, catalog, theme, preview, refreshVersion) {
            if (preview == null || cached != null) return@produceState
            val file = catalog.preview(theme, preview) ?: return@produceState
            value = withContext(NzikDispatchers.DATA) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    StoreCardPreview(file, bounds.outWidth.toFloat() / bounds.outHeight).also {
                        catalog.previewMetadata.put(preview.sha256, it)
                    }
                } else {
                    null
                }
            }
        }
        image
    }
}

@Composable
private fun ThemeStoreCard(
    theme: StoreTheme,
    catalog: ThemeStoreCatalog,
    glass: Boolean,
    official: Boolean,
    suspended: Boolean = false,
    showLocalCreator: Boolean = false,
    modifier: Modifier = Modifier,
    showCatalogInfo: Boolean = false,
    refreshVersion: Int = 0,
    onWithdraw: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
) {
    val blocked = suspended && !official
    val offline by OfflineMode.rememberEnabled()
    var followUp by rememberSaveable(theme.themeId) { mutableStateOf(false) }
    if (blocked && followUp && !offline) {
        ThemeReportDialog(theme, null) { followUp = false }
    }
    val publishedPreview = if (blocked) null else theme.playerPreview(glass)
    val open = onOpen.takeUnless { blocked }
    val preview = rememberStorePreview(theme, publishedPreview, catalog, refreshVersion)
    val context = LocalContext.current
    val appearanceSize by produceState<Int?>(null, catalog, theme, showCatalogInfo, refreshVersion) {
        value = if (showCatalogInfo) catalog.appearanceSize(theme) else null
    }
    val reviews by produceState<StoreReviewSummary?>(null, catalog, theme, showCatalogInfo, refreshVersion) {
        value = null
        if (showCatalogInfo) value = catalog.reviews(theme)
    }
    val imageDescription = publishedPreview?.caption
    ThemeCardSurface(modifier = modifier, onClick = open) {
        val image = preview
        Box(Modifier.fillMaxWidth().aspectRatio(StorePreviewAspectRatio), contentAlignment = Alignment.Center) {
            StorePreviewImage(image?.file, imageDescription, showImageRim = false, fitPortraitFrame = true)
        }
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = if (open == null) 10.dp else 0.dp)) {
            val author = if (showLocalCreator) localThemeCreatorName(theme) else theme.author.login
            ThemeCardIdentity(theme.title, author, official)
            if (blocked) {
                StoreText(stringResource(R.string.theme_store_report_theme_suspended), secondary = true)
            } else if (showCatalogInfo) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.weight(1f)) {
                        val review = reviews
                        if (review?.count == 0) {
                            StoreText(stringResource(R.string.theme_store_no_reviews), secondary = true, style = typography().xxs)
                        } else if (review != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.star_brilliant), null, Modifier.size(12.dp), tint = colorPalette().accent)
                                StoreText(stringResource(R.string.theme_store_rating_count, review.average, review.count), secondary = true, style = typography().xxs)
                            }
                        }
                    }
                    appearanceSize?.let {
                        StoreText(android.text.format.Formatter.formatShortFileSize(context, it.toLong()), secondary = true, style = typography().xxs)
                    }
                }
            }
        }
        if (blocked && !offline) {
            Box(Modifier.fillMaxWidth().padding(end = 4.dp, bottom = 10.dp), contentAlignment = Alignment.CenterEnd) {
                StoreSeeButton(R.string.theme_store_report_follow_up, onOpen = { followUp = true })
            }
        } else if (open != null) {
            StoreCardSeeAction(open, Modifier.padding(end = 4.dp, bottom = 10.dp), onWithdraw.takeUnless { offline })
        }
    }
}

@Composable
internal fun ThemeCardIdentity(title: String, author: String, official: Boolean) {
    StoreText(title, style = typography().xs.copy(fontWeight = FontWeight.SemiBold), marquee = true)
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f, fill = false)) {
            StoreText(author, secondary = true, style = typography().xxs, marquee = true)
        }
        OfficialCreatorBadge(official)
    }
}

@Composable
private fun StoreCardSeeAction(onOpen: () -> Unit, modifier: Modifier = Modifier, onWithdraw: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(modifier).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onWithdraw != null) {
            StoreIconAction(onClick = onWithdraw, size = 32.dp) {
                Icon(painterResource(R.drawable.trash), stringResource(R.string.theme_store_withdraw),
                    Modifier.size(16.dp), tint = colorPalette().text)
            }
        }
        StoreSeeButton(R.string.theme_store_see, onOpen)
    }
}

@Composable
private fun StoreSeeButton(label: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val shape = uiRoundnessShape()
    val settings = rememberGlassSettings()
    val height = 32.dp
    val content: @Composable () -> Unit = {
        BasicText(stringResource(label), style = typography().xxs.copy(color = colorPalette().text))
        Icon(painterResource(R.drawable.chevron_forward), null, Modifier.size(12.dp), tint = colorPalette().text)
    }
        if (settings.usesGlassMaterial(GlassArea.ActionButtons)) {
            val background = colorPalette().background0
            LiquidButton(
                onClick = onOpen,
                backdrop = rememberCanvasBackdrop { drawRect(background) },
                modifier = modifier,
                settings = settings.forArea(GlassArea.ActionButtons),
                contentHeight = height,
                contentPadding = 6.dp,
                filmColor = surfaceColor(colorPalette().background1),
                shape = shape,
            ) { content() }
        } else {
        UiTextButton(
            onClick = onOpen,
            modifier = modifier.height(height),
            shape = shape,
            colors = storeGlassButtonColors(),
            contentPadding = PaddingValues(horizontal = 6.dp),
        ) { content() }
        }
}

@Composable
private fun storeGlassButtonColors() = ButtonDefaults.textButtonColors(
    containerColor = if (rememberGlassSettings().let {
        it.usesGlassMaterial(GlassArea.ActionButtons) || it.usesGlassContour(GlassArea.ActionButtons)
    }) surfaceColor(colorPalette().background1) else Color.Transparent,
)

@Composable
internal fun ThemeCardSurface(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val palette = colorPalette()
    val shape = uiRoundnessShape()
    SettingsCardSurface(
        modifier = modifier.fillMaxWidth().glassSettingsCardRim(shape),
        shape = shape,
        containerColor = surfaceColor(palette.background1),
        backgroundColor = palette.background0,
        shadowColor = palette.background0,
        foregroundRim = true,
    ) {
        Column(Modifier.fillMaxWidth().clip(shape).then(
            if (onClick == null) Modifier else Modifier.clickable(onClick = onClick),
        )) { content() }
    }
}

@Composable
internal fun rememberVerifiedCreatorIds(catalog: ThemeStoreCatalog, refreshVersion: Int): Set<Long>? {
    val ids by produceState<Set<Long>?>(null, catalog, refreshVersion) {
        try {
            value = catalog.verifiedCreatorIds()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("ThemeStore").w(error, "Could not load verified creators")
        }
    }
    return ids
}

@Composable
internal fun OfficialCreatorBadge(official: Boolean) {
    if (!official) return
    Icon(
        painter = painterResource(R.drawable.shield_checkmark),
        contentDescription = stringResource(R.string.theme_store_official),
        tint = colorPalette().accent,
        modifier = Modifier.size(14.dp).clip(uiRoundnessShape()).clickable {
            Toaster.i(R.string.theme_store_official)
        },
    )
}

@Composable
internal fun StorePreviewImage(
    file: File?,
    description: String?,
    showImageRim: Boolean = true,
    fitPortraitFrame: Boolean = false,
) {
    val fitImage = fitPortraitFrame && file != null
    ImageCacheFactory.LocalAsyncImage(
        model = file,
        cacheKey = file?.absolutePath,
        contentDescription = description,
        modifier = Modifier.fillMaxSize().then(if (fitImage) Modifier.background(Color.Black) else Modifier),
        contentScale = if (fitImage) ContentScale.Fit else ContentScale.Crop,
        shape = if (showImageRim) uiRoundnessShape() else null,
    )
}

@Composable
internal fun StoreLastSync(timestamp: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val date = java.util.Date(timestamp)
    BasicText(
        text = stringResource(
            R.string.theme_store_last_sync,
            android.text.format.DateFormat.getDateFormat(context).format(date),
            android.text.format.DateFormat.getTimeFormat(context).format(date),
        ),
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        style = typography().xxs.copy(color = colorPalette().textSecondary, textAlign = TextAlign.Center),
    )
}

@Composable
internal fun StoreText(text: String, secondary: Boolean = false, style: TextStyle = typography().s, marquee: Boolean = false) {
    val palette = colorPalette()
    BasicText(
        text = text,
        modifier = if (marquee) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
        style = style.copy(color = if (secondary) palette.textSecondary else palette.text),
        maxLines = 1,
        overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

@Composable
internal fun storeTagLabel(tag: String): String = when (canonicalStoreTag(tag)) {
    "stock" -> stringResource(R.string.theme_store_tag_stock)
    "built-in" -> stringResource(R.string.theme_store_tag_builtin)
    "artwork" -> stringResource(R.string.theme_store_tag_artwork)
    "dark" -> stringResource(R.string.theme_store_tag_dark)
    "blur" -> stringResource(R.string.theme_store_tag_blur)
    "carousel" -> stringResource(R.string.theme_store_tag_carousel)
    "coverless" -> stringResource(R.string.theme_store_tag_coverless)
    "audio-waves" -> stringResource(R.string.theme_store_tag_waves)
    "animated-gradient" -> stringResource(R.string.theme_store_tag_animated)
    "gradient" -> stringResource(R.string.theme_store_tag_gradient)
    else -> canonicalStoreTag(tag).replace('-', ' ').replaceFirstChar { it.titlecase() }
}


@Composable
private fun CreatorImage(catalog: ThemeStoreCatalog, profile: CreatorProfile?, media: CreatorMedia?, modifier: Modifier, contentScale: ContentScale = ContentScale.FillBounds, fallback: @Composable () -> Unit) {
    val image by produceState<Any?>(null, catalog, profile, media) {
        value = null
        if (profile != null && media != null) {
            try {
                value = catalog.creatorImage(profile, media)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag("ThemeStore").w(error, "Could not load creator image")
            }
        }
    }
    var loaded by remember(image) { mutableStateOf(false) }
    if (!loaded) fallback()
    // Older CDN responses can still reference the original banner with transparent margins.
    val imageModifier = if (contentScale == ContentScale.FillBounds && media?.asset?.sha256 == "21fc3f12b97e7c9b2a16aa87fac5becbe313542db5bcddfb0d887f8470970c84") {
        modifier.graphicsLayer {
            scaleX = 1200f / 1100f
            scaleY = 600f / 500f
        }
    } else if (media?.asset?.sha256 == "faff0a7d85ebff353f68b2a5dd139477522d6d338eed8c6a5fd66dabd2590412") {
        // The published artwork includes transparent rounded corners. Crop them before applying the UI shape.
        modifier.graphicsLayer {
            scaleX = 1.1f
            scaleY = 1.1f
        }
    } else modifier
    if (image != null) {
        ImageCacheFactory.LocalAsyncImage(
            model = image,
            contentDescription = null,
            modifier = imageModifier,
            contentScale = contentScale,
            fallback = null,
            shape = null,
            onSuccess = { loaded = true },
        )
    }
}

@Composable
private fun CreatorCard(
    profile: CreatorProfile?,
    themes: List<StoreTheme>,
    catalog: ThemeStoreCatalog,
    followed: Boolean,
    official: Boolean,
    onFollow: (StoreAuthor) -> Unit,
    publicationAlerts: Boolean,
    canSetPublicationAlerts: Boolean,
    onTogglePublicationAlerts: () -> Unit,
    fallbackAuthor: StoreAuthor? = null,
    onEditProfile: (() -> Unit)? = null,
    onOpen: () -> Unit,
) {
    val author = profile?.let { StoreAuthor(it.githubId, it.login) } ?: themes.firstOrNull()?.author ?: fallbackAuthor ?: return
    val name = profile?.displayName ?: author.login
    val ownProfile = signedInCreatorId() == author.githubId
    ThemeCardSurface {
        Box {
            Box(Modifier.matchParentSize().clip(uiRoundnessShape())) {
                CreatorImage(catalog, profile, profile?.banner, Modifier.matchParentSize(), ContentScale.Crop) {
                    CreatorDemoBanner(author.githubId, Modifier.matchParentSize())
                }
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.70f)))
            }
            Box(Modifier.matchParentSize().clip(uiRoundnessShape()).clickable(onClick = onOpen))
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CreatorAvatar(profile, author, catalog, 36.dp)
                Column(Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f, fill = false)) {
                            BasicText(name, Modifier.basicMarquee(iterations = Int.MAX_VALUE), style = typography().s.copy(color = Color.White), maxLines = 1)
                        }
                        OfficialCreatorBadge(official)
                    }
                    BasicText(
                        stringResource(R.string.theme_store_author_theme_count, themes.size),
                        style = typography().xxs.copy(color = Color.White.copy(alpha = 0.7f)),
                    )
                }
                if (onEditProfile != null) {
                    app.n_zik.android.components.glass.PageButton(onClick = onEditProfile) {
                        StoreText(stringResource(R.string.theme_store_edit_creator))
                    }
                } else if (!ownProfile) {
                    ArtistFollowButton(likeState = true.takeIf { followed }, onClick = { onFollow(author) })
                    CreatorPublicationBell(publicationAlerts, canSetPublicationAlerts, onTogglePublicationAlerts)
                }
                Icon(painterResource(R.drawable.chevron_forward), null, Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
internal fun localThemeCreatorName(theme: StoreTheme): String = theme.localCreatorName
    ?: if (theme.author.githubId > 0) theme.author.login
    else app.n_zik.android.components.navigation.header.rememberHeaderProfileFace().name

@Composable
internal fun CreatorAvatar(
    profile: CreatorProfile?,
    author: StoreAuthor,
    catalog: ThemeStoreCatalog,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val name = profile?.displayName ?: author.login
    val image by produceState<Any?>(null, catalog, profile) {
        value = null
        if (profile != null) {
            try {
                value = catalog.creatorImage(profile, profile.avatar)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag("ThemeStore").w(error, "Could not load creator avatar")
            }
        }
    }
    val offline by OfflineMode.rememberEnabled()
    val source = when (val model = image) {
        is File -> model.path
        is String -> model.takeUnless { offline }
        else -> if (profile == null && !offline) "https://avatars.githubusercontent.com/u/${author.githubId}?s=256" else null
    }
    ProfileFaceAvatar(
        avatar = source?.let { FaceAvatar.Photo(it) } ?: FaceAvatar.Initials(name),
        faceName = name,
        size = size,
        modifier = modifier.glassImageRim(artistThumbnailShape()).clip(artistThumbnailShape()),
    )
}

@Composable
internal fun CreatorDemoBanner(githubId: Long, modifier: Modifier = Modifier.fillMaxSize()) {
    val context = LocalContext.current.applicationContext
    val offline by OfflineMode.rememberEnabled()
    val banner by produceState<File?>(null, context, githubId, offline) {
        value = try {
            ThemePreviewAssets.banner(context, githubId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("ThemeStore").d(error, "Demo creator banner unavailable")
            null
        }
    }
    Box(modifier.background(colorPalette().background1)) {
        banner?.let { asset ->
            ImageCacheFactory.LocalAsyncImage(
                model = asset, contentDescription = null, modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop, shape = null,
            )
        }
    }
}

@Composable
private fun CreatorProfileHeader(
    onEditProfile: (() -> Unit)?,
    profile: CreatorProfile?,
    author: StoreAuthor,
    themeCount: Int,
    catalog: ThemeStoreCatalog,
    followed: Boolean,
    query: TextFieldValue,
    onQueryChanged: (TextFieldValue) -> Unit,
    official: Boolean,
    publicationAlerts: Boolean,
    canSetPublicationAlerts: Boolean,
    onTogglePublicationAlerts: () -> Unit,
    onFollow: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val bannerHeight = maxWidth / 2.2f
            val bannerShape = uiRoundnessShape()
            Box(Modifier.fillMaxWidth().height(bannerHeight).glassImageRim(bannerShape).clip(bannerShape)) {
                CreatorImage(catalog, profile, profile?.banner, Modifier.fillMaxSize()) {
                    CreatorDemoBanner(author.githubId)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = bannerHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Box(Modifier.size(70.dp).offset(y = (-28).dp).background(colorPalette().background0, artistThumbnailShape()),
                contentAlignment = Alignment.Center) {
                CreatorAvatar(profile, author, catalog, 66.dp)
            }
            Column(Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f, fill = false)) { StoreText(profile?.displayName ?: author.login, style = typography().m, marquee = true) }
                    OfficialCreatorBadge(official)
                }
                StoreText("@${author.login}", secondary = true, style = typography().xs)
            }
            if (onEditProfile != null) {
                app.n_zik.android.components.glass.PageButton(onClick = onEditProfile) {
                    StoreText(stringResource(R.string.theme_store_edit_creator))
                }
            } else {
                ArtistFollowButton(likeState = true.takeIf { followed }, onClick = onFollow)
                CreatorPublicationBell(publicationAlerts, canSetPublicationAlerts, onTogglePublicationAlerts)
            }
            }
        }
        profile?.bio?.takeIf { it.isNotBlank() }?.let {
            SettingsDescription(it)
        }
        UnifiedSearchBar(
            input = query,
            onInputChanged = onQueryChanged,
            placeholderRes = R.string.theme_store_search_creator_themes,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            onClear = { onQueryChanged(TextFieldValue()) },
        )
        Box(Modifier.padding(top = 8.dp)) {
            StoreSectionHeader(R.string.theme_store_published_theme_count, themeCount = themeCount)
        }
    }
}


@Composable
internal fun CreatorPublicationBell(enabled: Boolean, available: Boolean, onToggle: () -> Unit) {
    StoreIconAction(available, onToggle) {
        Icon(
            painterResource(if (enabled) R.drawable.notification1 else R.drawable.notification2),
            stringResource(if (enabled) R.string.theme_store_disable_publication_alerts else R.string.theme_store_enable_publication_alerts),
            Modifier.size(20.dp),
            tint = colorPalette().text,
        )
    }
}

@Composable
internal fun StoreIconAction(
    available: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    border: BorderStroke = BorderStroke(1.dp, colorPalette().accent),
    glyph: @Composable () -> Unit,
) {
    val settings = rememberGlassSettings()
    if (settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        val background = colorPalette().background0
        LiquidButton(
            onClick = onClick,
            backdrop = rememberCanvasBackdrop { drawRect(background) },
            modifier = modifier.size(size),
            settings = settings.forArea(GlassArea.ActionButtons),
            enabled = available,
            contentHeight = size,
            contentPadding = 0.dp,
            filmColor = surfaceColor(colorPalette().background1),
        ) { glyph() }
        return
    }
    UiTextButton(
        onClick = onClick,
        enabled = available,
        modifier = modifier.size(size),
        colors = storeGlassButtonColors(),
        border = border,
        contentPadding = PaddingValues(0.dp),
    ) { glyph() }
}
