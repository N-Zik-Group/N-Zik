package app.n_zik.android.ui.screens.history

import app.n_zik.android.components.history.historyArtistGroups
import app.n_zik.android.components.history.historyTitleGroups

import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import app.n_zik.android.components.navigation.TopOverlayContentPadding
import app.n_zik.android.components.navigation.LocalSideRailContentInsets
import androidx.compose.ui.unit.Dp


import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import app.n_zik.android.components.Sort
import app.n_zik.android.components.glass.GlassToolbarRow
import app.n_zik.android.components.glass.PageButton
import app.n_zik.android.components.glass.glassChipBackground
import app.n_zik.android.utils.Preference
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastDistinctBy
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.compose.persist.persist
import app.n_zik.innertube.YtMusic
import app.n_zik.innertube.requests.HistoryPage
import app.n_zik.android.core.database.Database
import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.n_zik.android.enums.HistoryType
import app.n_zik.android.enums.HistorySortOrder
import app.n_zik.android.thumbnailShape
import app.n_zik.android.ui.components.ButtonsRow
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.components.SwipeablePlaylistItem
import app.n_zik.android.ui.components.themed.HeaderWithIcon
import app.n_zik.android.ui.items.SongItemPlaceholders
import app.n_zik.android.ui.items.SongItemPlaceholder
import app.n_zik.android.utils.shimmerEffect
import app.n_zik.android.ui.components.themed.Title
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.components.ui.screens.home.quickpicks.QuickPicksAccountIdentity
import app.n_zik.android.components.ui.screens.home.quickpicks.currentQuickPicksAccountIdentity
import app.n_zik.android.utils.encryptedPreferencesUpdateTrigger
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.syncImportHistoryKey
import app.n_zik.android.utils.useLoginForBrowseKey
import app.n_zik.android.utils.addNext
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.enqueue
import app.n_zik.android.utils.forcePlay
import app.n_zik.android.utils.historyTypeKey
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.pauseSearchHistoryKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.search.filterSearchHistory
import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.SearchEmptySpace
import app.n_zik.android.components.search.SearchResultsViewport
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.rememberSearchHistory
import app.n_zik.android.components.settings.common.rememberSettingsSearchFocus
import app.n_zik.android.components.settings.common.settingsSearchDescription
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.LocalDownloadStatesMap
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.menu.song.SongItemMenu
import app.n_zik.android.enums.DownloadedStateMedia
import app.n_zik.android.enums.PlaylistSwipeAction
import app.n_zik.android.utils.playlistSwipeLeftActionKey
import app.n_zik.android.utils.playlistSwipeRightActionKey
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.download.utils.MyDownloadHelper
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.components.OfflineContent
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.utils.historySortMenuOrderKey
import androidx.compose.foundation.text.BasicText
import app.n_zik.android.utils.preferences
import app.n_zik.android.typography
import java.time.ZoneId
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.LocalDate
import timber.log.Timber

@kotlin.OptIn(ExperimentalTextApi::class)
@OptIn(UnstableApi::class)
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HistoryList(
    navController: NavController,
    topContentInsetProvider: () -> Dp,
) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val isOnline by NetworkQualityHelper.isNetworkAvailableComposable(context)
    val lazyListState = rememberLazyListState()

    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val search = Search(lazyListState, initialVisible = true)

    // Same focus behavior as the settings pages: the chips collapse and the
    // content fades so the bar rises to the title's level; the search history
    // section keeps its full opacity so past queries stay tappable
    val searchFocus = rememberSettingsSearchFocus(search.isFocused, search.inputValue.isBlank())

    // Saved searches: this screen's own store (not shared with the search
    // tabs), paused by the "pause search history" setting
    val isHistoryPaused by rememberPreference(pauseSearchHistoryKey, false)
    val savedSearches = rememberSearchHistory(SearchHistoryStore.PAGE_HISTORY, isHistoryPaused)
    val historyMatches = filterSearchHistory(savedSearches.value, search.inputValue)

    // Record only an explicit search submission.
    fun recordHistoryQuery(input: String = search.inputValue) {
        val query = input.trim()
        if (query.isEmpty() || isHistoryPaused) return
        savedSearches.value =
            SearchHistoryStore.record(context.preferences, SearchHistoryStore.PAGE_HISTORY, query)
    }

    // Keep the history item reachable when the results list was scrolled.
    LaunchedEffect(search.isFocused) {
        if (search.isFocused) {
            lazyListState.scrollToItem(0, 0)
        }
        }
    LaunchedEffect(search.isVisible) {
        if (!search.isVisible) search.isVisible = true
    }


    val sort = Sort<HistorySortOrder>(Preference.HISTORY_SORT_BY, Preference.HISTORY_SORT_ORDER, historySortMenuOrderKey, "hist")

    var localHistoryRetry by remember { mutableIntStateOf(0) }
    val eventResults = remember(sort.sortBy, sort.sortOrder, parentalControlEnabled) {
        Database.eventTable
                .allWithSong()
                .distinctUntilChanged()
                .map { list ->
                    val filtered = list.filter { !parentalControlEnabled || !it.song.title.startsWith( EXPLICIT_PREFIX, true ) }
                    val sorted = when (sort.sortOrder) {
                        SortOrder.Ascending -> filtered
                        SortOrder.Descending -> filtered.reversed()
                    }
                    when (sort.sortBy) {
                        HistorySortOrder.DATE -> {
                            val today = LocalDate.now()
                            val yesterday = today.minusDays(1)
                            sorted.groupBy { event ->
                                val eventDate = Instant.ofEpochMilli(event.event.timestamp)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                val daysBetween = ChronoUnit.DAYS.between(eventDate, today)
                                when {
                                    eventDate.isEqual(today) -> context.getString(R.string.calendar_today)
                                    eventDate.isEqual(yesterday) -> context.getString(R.string.calendar_yesterday)
                                    daysBetween in 2..7 -> context.getString(R.string.calendar_days_ago, daysBetween.toInt())
                                    daysBetween in 8..14 -> context.getString(R.string.calendar_last_week)
                                    eventDate.year == today.year && eventDate.monthValue == today.monthValue -> context.getString(R.string.calendar_this_month)
                                    eventDate.year == today.year -> SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(event.event.timestamp)).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                                    eventDate.year == today.year - 1 -> context.getString(R.string.calendar_last_year)
                                    else -> SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(event.event.timestamp)).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                                }
                            }
                        }
                        HistorySortOrder.ALPHABETICAL -> {
                            historyTitleGroups(sorted)
                        }
                        HistorySortOrder.ARTIST -> {
                            historyArtistGroups(sorted)
                        }
                    }
                }
                .map { Result.success(it) }
                .catch { error ->
                    Timber.tag("History").w(error, "Unable to read listening history")
                    emit(Result.failure(error))
                }
    }
    val loadedEvents by key(localHistoryRetry) {
        eventResults.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)
    }
    val events = loadedEvents?.getOrNull().orEmpty()

    val buttonsList = mutableListOf(HistoryType.History to stringResource(R.string.library_history_title))
    val syncImportHistory by rememberPreference(syncImportHistoryKey, false)
    val useLoginForBrowse by rememberPreference(useLoginForBrowseKey, true)
    val sessionGeneration = encryptedPreferencesUpdateTrigger
    val profileId = getActiveProfile(context)
    val onlineHistoryOwner = remember(profileId, sessionGeneration, useLoginForBrowse, syncImportHistory) {
        if (syncImportHistory && isYouTubeSyncEnabled()) {
            YouTubeHistoryOwner(profileId, currentQuickPicksAccountIdentity())
        } else null
    }
    if (onlineHistoryOwner != null) {
        buttonsList += HistoryType.YTMHistory to stringResource(R.string.sync_youtube_history_title)
    }

    var selectedHistoryType by rememberPreference(historyTypeKey, HistoryType.History)
    val historyType = if (onlineHistoryOwner == null) HistoryType.History else selectedHistoryType
    var isYTMLoading by remember(onlineHistoryOwner) { mutableStateOf(false) }
    var didYTMRefreshFail by remember(onlineHistoryOwner) { mutableStateOf(false) }
    var youtubeHistoryRetry by remember { mutableIntStateOf(0) }

    var cachedHistory by persist<CachedYouTubeHistory>("home/history/accountPage")
    val historyPage = cachedHistory?.takeIf { it.owner == onlineHistoryOwner }?.page
    LaunchedEffect(historyType, onlineHistoryOwner, sessionGeneration, youtubeHistoryRetry) {
        val owner = onlineHistoryOwner ?: return@LaunchedEffect
        if (historyType != HistoryType.YTMHistory) return@LaunchedEffect

        fun ownerStillCurrent(): Boolean =
            owner.profileId == getActiveProfile(context) && owner.account == currentQuickPicksAccountIdentity() &&
                isYouTubeSyncEnabled()

        isYTMLoading = true
        try {
            val result = withContext(NzikDispatchers.DATA) {
                if (!ownerStillCurrent()) return@withContext null
                YtMusic.getHistory(setLogin = true)
            } ?: return@LaunchedEffect
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (sessionGeneration != encryptedPreferencesUpdateTrigger || !ownerStillCurrent()) return@LaunchedEffect
            result.getOrNull()?.let { cachedHistory = CachedYouTubeHistory(owner, it) }
            didYTMRefreshFail = result.isFailure
        } finally {
            isYTMLoading = false
        }
    }

    val filteredEvents = remember(events, search.inputValue) {
        events.mapValues { (_, details) ->
            details.fastDistinctBy { it.song.id }.filter { event ->
                event.song.title.contains(search.inputValue, ignoreCase = true) ||
                    event.song.artistsText.orEmpty().contains(search.inputValue, ignoreCase = true)
            }
        }.filterValues { it.isNotEmpty() }
    }
    val filteredHistorySections = remember(historyPage, search.inputValue, parentalControlEnabled) {
        historyPage?.sections.orEmpty().map { section ->
            section.title to section.songs.map { it.asMediaItem }.filter { mediaItem ->
                mediaItem.mediaId.isNotEmpty() &&
                    (!parentalControlEnabled || mediaItem.mediaMetadata.title?.startsWith(EXPLICIT_PREFIX, true) != true) &&
                    ((mediaItem.mediaMetadata.title ?: "").contains(search.inputValue, ignoreCase = true) ||
                        (mediaItem.mediaMetadata.artist ?: "").contains(search.inputValue, ignoreCase = true))
            }
        }.filter { (_, songs) -> songs.isNotEmpty() }
    }
    val noSearchResults = search.inputValue.isNotBlank() && when (historyType) {
        HistoryType.History -> filteredEvents.isEmpty()
        HistoryType.YTMHistory -> filteredHistorySections.isEmpty()
    }

    SearchResultsViewport {
    Column (
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
            .padding(TopOverlayContentPadding(topContentInsetProvider))
    ) {
        Column(Modifier.padding(LocalSideRailContentInsets.current.asPaddingValues())) {
            // Collapses while the bar is focused so the bar rises to the very
            // top, like the settings pages (settingsSearchTitle behavior)
            AnimatedVisibility(
                visible = searchFocus.titleVisible,
                enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                exit = shrinkVertically(tween(250)) + fadeOut(tween(250)),
            ) {
                HeaderWithIcon(
                    title = stringResource(R.string.library_history_title),
                    iconId = R.drawable.history,
                    enabled = false,
                    showIcon = false,
                    modifier = Modifier,
                    onClick = {}
                )
            }

            // Collapses while the bar is focused, letting the bar rise to the
            // title's level (settingsSearchDescription behavior). 16dp keeps the
            // chip row aligned with the search bar's edges (same on the home tabs)
            settingsSearchDescription(searchFocus) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ButtonsRow(
                        chips = buttonsList,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp),
                        currentValue = historyType,
                        onValueUpdate = { selectedHistoryType = it },
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        modifier = Modifier.padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassToolbarRow(actionCount = 1) {
                            sort.ToolBarButton()
                        }
                    }
                }
            }

            search.SearchBar(this@Column, onSearch = { recordHistoryQuery() })
        }

        val forceShimmer = forceLoadingShimmerPreview()
        val isLoading = forceShimmer || when (historyType) {
            HistoryType.History -> loadedEvents == null
            HistoryType.YTMHistory -> historyPage == null && (isYTMLoading || !didYTMRefreshFail)
        }
        val loadFailed = when (historyType) {
            HistoryType.History -> loadedEvents?.isFailure == true
            HistoryType.YTMHistory -> didYTMRefreshFail && !isYTMLoading
        }
        val showKnownHistoryShimmer = forceShimmer && when (historyType) {
            HistoryType.History -> filteredEvents.isNotEmpty()
            HistoryType.YTMHistory -> filteredHistorySections.isNotEmpty()
        }
        val historyFailureWithoutCache = loadFailed &&
            (historyType == HistoryType.History || historyPage == null)
        val showRetry = loadFailed && !forceShimmer && !(search.isFocused && search.inputValue.isBlank())
        val savedSearchSection: @Composable () -> Unit = {
            SearchHistorySection(
                visible = search.isFocused && historyMatches.isNotEmpty(),
                queries = historyMatches,
                onQueryClick = { search.input = TextFieldValue(it); recordHistoryQuery(it); search.isFocused = false },
                onQueryDelete = {
                    savedSearches.value = SearchHistoryStore.delete(context.preferences, SearchHistoryStore.PAGE_HISTORY, it)
                },
                onQueryDeleteAll = {
                    savedSearches.value = SearchHistoryStore.deleteAll(context.preferences, SearchHistoryStore.PAGE_HISTORY)
                },
            )
        }
        val failureContent: @Composable (Modifier) -> Unit = { modifier ->
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BasicText(
                    text = stringResource(if (historyType == HistoryType.YTMHistory && !isOnline)
                        R.string.common_load_failed_no_connection else R.string.common_load_failed),
                    style = typography().m.semiBold.copy(color = colorPalette().textSecondary),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                PageButton(onClick = {
                    if (historyType == HistoryType.History) localHistoryRetry++ else youtubeHistoryRetry++
                }) {
                    BasicText(stringResource(R.string.common_action_retry), style = typography().s)
                }
            }
        }

        SearchEmptyFeedback(
            query = search.inputValue,
            empty = noSearchResults,
            loading = isLoading || loadFailed
        )

        // Wiped with a plain alpha while the bar is focused with a blank
        // query, like the settings pages; the wipe is applied per content
        // block so the search history section in the list keeps its full
        // opacity
        Column(
            modifier = Modifier
                .weight(1f)
        ) {
            if (isLoading && !showKnownHistoryShimmer) {
                if (historyType == HistoryType.YTMHistory && !isOnline && !forceShimmer) {
                    OfflineContent(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                        .padding(rememberPageChromeContentPadding(topInsetProvider = { 0.dp }))
                            .graphicsLayer { alpha = searchFocus.contentAlpha }
                    )
                } else {
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = rememberPageChromeContentPadding(topInsetProvider = { 0.dp }),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .graphicsLayer { if (!forceShimmer) alpha = searchFocus.contentAlpha },
                    ) {
                        stickyHeader {
                            HistorySectionTitle(
                                title = stringResource(R.string.calendar_today),
                                shimmer = true,
                            )
                        }
                        item { SongItemPlaceholders(count = 4) }
                    }
                }
            } else if (historyFailureWithoutCache) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = rememberPageChromeContentPadding(topInsetProvider = { 0.dp }),
                ) {
                    item(key = "search_history") { savedSearchSection() }
                    if (showRetry) item(key = "history_error") {
                        failureContent(Modifier.fillParentMaxSize())
                    }
                }
            } else if (!loadFailed && (
                (search.inputValue.isBlank() && historyMatches.isEmpty() && historyType == HistoryType.History && events.isEmpty()) ||
                (search.inputValue.isBlank() && historyMatches.isEmpty() && historyType == HistoryType.YTMHistory && historyPage?.sections.isNullOrEmpty())
            )) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(rememberPageChromeContentPadding(topInsetProvider = { 0.dp }))
                        .graphicsLayer { alpha = searchFocus.contentAlpha },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = stringResource(R.string.common_empty_items),
                        style = typography().m.semiBold.copy(
                            color = colorPalette().textSecondary
                        )
                    )
                }
            } else {
                val allHistorySongIds = remember(events) {
                    events.values.flatten().map { it.song.id }.distinct()
                }
                val likeStatesMap by remember(allHistorySongIds) {
                    LikeStateManager.getLikeStates(allHistorySongIds)
                }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
                val playlistStatesMap by remember(allHistorySongIds) {
                    PlaylistStateManager.getPlaylistStates(allHistorySongIds)
                }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

                val allHistorySongs = remember(events) { events.values.flatten().map { it.song } }
                val downloadsMapState by MyDownloadHelper.downloads.collectAsStateWithLifecycle(initialValue = MyDownloadHelper.downloads.value, context = NzikDispatchers.DATA)
                val downloadedIds by remember {
                    derivedStateOf {
                        downloadsMapState.values
                            .filter { it.state == Download.STATE_COMPLETED }
                            .mapTo(HashSet()) { it.request.id }
                    }
                }
                val downloadStatesMap by remember {
                    derivedStateOf {
                        allHistorySongs.associate { song ->
                            song.id to if (song.id in downloadedIds) DownloadedStateMedia.DOWNLOADED else DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
                        }
                    }
                }

                // Hoisted swipe action preferences
                val playlistSwipeLeftAction by rememberPreference(playlistSwipeLeftActionKey, PlaylistSwipeAction.Favourite)
                val playlistSwipeRightAction by rememberPreference(playlistSwipeRightActionKey, PlaylistSwipeAction.PlayNext)

                CompositionLocalProvider(LocalDownloadStatesMap provides downloadStatesMap) {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = rememberPageChromeContentPadding(topInsetProvider = { 0.dp }),
                    modifier = Modifier
                        .background(colorPalette().background0)
                        .fillMaxSize()
                ) {
                    // Search history (this screen's own store): tapping a row
                    // applies it as the query, the trash deletes one entry
                    // (long press: all of them). Pops in as soon as the bar is
                    // focused (same moment as the bar itself, like the other
                    // screens): the block slides down out of the bar's bottom
                    // edge (the expanding top-anchored clip masks anything
                    // above the bar), so it reads as emerging from the bar
                    // while the content below is pushed down; the exit shrinks
                    // back into the bar; the page content wipe never touches it
                    item(key = "search_history") { savedSearchSection() }
                    if (showRetry) item(key = "history_error") {
                        failureContent(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    }


                    if (noSearchResults && !loadFailed) {
                        item(key = "search_no_results") {
                            SearchEmptySpace(fillRemainingViewport = true)
                        }
                    }
                    if (historyType == HistoryType.History) {
                        filteredEvents.forEach { (headerStr, details) ->
                            stickyHeader {
                                HistorySectionTitle(
                                    title = headerStr ?: stringResource(R.string.common_unknown),
                                    modifier = Modifier.graphicsLayer { if (!forceShimmer) alpha = searchFocus.contentAlpha },
                                    shimmer = forceShimmer,
                                )
                            }

                            items(
                                items = details,
                                key = { it.event.id }
                            ) { event ->
                                if (forceShimmer) {
                                    SongItemPlaceholder(title = event.song.title, artist = event.song.cleanArtistsText())
                                    return@items
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer { alpha = searchFocus.contentAlpha }
                                ) {
                                    SwipeablePlaylistItem(
                                        mediaItem = event.song.asMediaItem,
                                        onPlayNext = {
                                            binder?.player?.addNext(event.song.asMediaItem)
                                        },
                                        onEnqueue = {
                                            binder?.player?.enqueue(event.song.asMediaItem)
                                        },
                                        downloadStateParam = downloadsMapState[event.song.id]?.state ?: Download.STATE_STOPPED,
                                        downloadedStateMediaParam = downloadStatesMap[event.song.id] ?: DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED,
                                        swipeLeftActionParam = playlistSwipeLeftAction,
                                        swipeRightActionParam = playlistSwipeRightAction
                                    ) {
                                        SongItem(
                                            song = event.song,
                                            isLiked = likeStatesMap[event.song.id],
                                            inPlaylist = playlistStatesMap[event.song.id],
                                            navController = navController,
                                            modifier = Modifier,

                                            onClick = {
                                                recordHistoryQuery()
                                                binder?.player?.forcePlay(event.song.asMediaItem)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (historyType == HistoryType.YTMHistory) {
                        filteredHistorySections.forEach { (sectionTitle, sectionSongs) ->
                            stickyHeader {
                                HistorySectionTitle(
                                    title = sectionTitle,
                                    modifier = Modifier.graphicsLayer { if (!forceShimmer) alpha = searchFocus.contentAlpha },
                                    shimmer = forceShimmer,
                                )
                            }

                            items(
                                items = sectionSongs,
                                key = { it.mediaId }
                            ) { mediaItem ->
                                if (forceShimmer) {
                                    val song = mediaItem.asSong
                                    SongItemPlaceholder(title = song.title, artist = song.cleanArtistsText())
                                    return@items
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer { alpha = searchFocus.contentAlpha }
                                ) {
                                    SwipeablePlaylistItem(
                                        mediaItem = mediaItem,
                                        onPlayNext = {
                                            binder?.player?.addNext(mediaItem)
                                        },
                                        onEnqueue = {
                                            binder?.player?.enqueue(mediaItem)
                                        },
                                        downloadStateParam = downloadsMapState[mediaItem.mediaId]?.state ?: Download.STATE_STOPPED,
                                        downloadedStateMediaParam = downloadStatesMap[mediaItem.mediaId] ?: DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED,
                                        swipeLeftActionParam = playlistSwipeLeftAction,
                                        swipeRightActionParam = playlistSwipeRightAction
                                    ) {
                                        SongItem(
                                            song = mediaItem.asSong,
                                            isLiked = likeStatesMap[mediaItem.asSong.id],
                                            inPlaylist = playlistStatesMap[mediaItem.asSong.id],
                                            navController = navController,
                                            modifier = Modifier,

                                            onClick = {
                                                recordHistoryQuery()
                                                binder?.player?.forcePlay(mediaItem)
                                            },
                                            onLongClick = {
                                                menuState.display {
                                                    SongItemMenu(
                                                        navController = navController,
                                                        song = mediaItem.asSong
                                                    ).MenuComponent()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
}

private data class YouTubeHistoryOwner(val profileId: String, val account: QuickPicksAccountIdentity)

private data class CachedYouTubeHistory(val owner: YouTubeHistoryOwner, val page: HistoryPage)

@Composable
private fun HistorySectionTitle(title: String, modifier: Modifier = Modifier, shimmer: Boolean = false) {
    val shape = thumbnailShape()
    Title(
        title = title,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (shimmer) Modifier.clip(shape).shimmerEffect().drawWithContent {}.clearAndSetSemantics {}
                else Modifier.glassChipBackground(color = colorPalette().accent, shape = shape)
            ),
    )
}
