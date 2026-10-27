package app.it.fast4x.rimusic.ui.screens.history

import app.n_zik.android.core.database.*

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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import app.n_zik.android.components.Sort
import app.it.fast4x.rimusic.utils.Preference
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.n_zik.android.appContext
import app.it.fast4x.compose.persist.persist
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.requests.HistoryPage
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.HistoryType
import app.n_zik.android.enums.HistorySortOrder
import app.it.fast4x.rimusic.models.Event
import app.n_zik.android.thumbnailShape
import app.it.fast4x.rimusic.ui.components.ButtonsRow
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.components.themed.Loader
import app.it.fast4x.rimusic.ui.components.themed.Title
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.utils.enableYouTubeSyncKey
import app.it.fast4x.rimusic.utils.syncImportHistoryKey
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.forcePlay
import app.it.fast4x.rimusic.utils.historyTypeKey
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
import app.it.fast4x.rimusic.enums.SortOrder
import app.n_zik.android.LocalDownloadStatesMap
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.menu.song.SongItemMenu
import app.it.fast4x.rimusic.enums.DownloadedStateMedia
import app.it.fast4x.rimusic.enums.PlaylistSwipeAction
import app.it.fast4x.rimusic.utils.playlistSwipeLeftActionKey
import app.it.fast4x.rimusic.utils.playlistSwipeRightActionKey
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.download.utils.MyDownloadHelper
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.components.OfflineContent
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.it.fast4x.rimusic.utils.historySortMenuOrderKey
import androidx.compose.foundation.text.BasicText
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.typography
import java.time.ZoneId
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.LocalDate

@kotlin.OptIn(ExperimentalTextApi::class)
@OptIn(UnstableApi::class)
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HistoryList(
    navController: NavController
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

    var isCoreSyncEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isCoreSyncEnabled = withContext(NzikDispatchers.DATA) {
            appContext().encryptedPreferences.getBoolean(enableYouTubeSyncKey, false)
        }
    }

    val loadedEvents by remember(sort.sortBy, sort.sortOrder, parentalControlEnabled) {
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
                                    eventDate.isEqual(today) -> context.getString(R.string.today)
                                    eventDate.isEqual(yesterday) -> context.getString(R.string.yesterday)
                                    daysBetween in 2..7 -> context.getString(R.string.x_days_ago, daysBetween.toInt())
                                    daysBetween in 8..14 -> context.getString(R.string.last_week)
                                    eventDate.year == today.year && eventDate.monthValue == today.monthValue -> context.getString(R.string.this_month)
                                    eventDate.year == today.year -> SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(event.event.timestamp)).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                                    eventDate.year == today.year - 1 -> context.getString(R.string.last_year)
                                    else -> SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(event.event.timestamp)).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                                }
                            }
                        }
                        HistorySortOrder.ALPHABETICAL -> {
                            sorted.sortedBy { it.song.title }.groupBy { it.song.title.firstOrNull()?.uppercase() ?: "#" }
                        }
                        HistorySortOrder.ARTIST -> {
                            sorted.sortedBy { it.song.artistsText }.groupBy { it.song.artistsText?.split(",")?.first()?.trim() ?: "Unknown" }
                        }
                    }
                }
    }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)
    val events = loadedEvents.orEmpty()

    val buttonsList = mutableListOf(HistoryType.History to stringResource(R.string.history))
    val syncImportHistory by rememberPreference(syncImportHistoryKey, false)
    if (isCoreSyncEnabled && syncImportHistory) {
        buttonsList += HistoryType.YTMHistory to stringResource(R.string.yt_history)
    }

    var historyType by rememberPreference(historyTypeKey, HistoryType.History)

    var isLocalLoading by remember { mutableStateOf(true) }
    var isYTMLoading by remember { mutableStateOf(false) }

    LaunchedEffect(events) {
        if (events.isNotEmpty()) {
            isLocalLoading = false
        }
    }

    LaunchedEffect(Unit) {
        delay(1500)
        isLocalLoading = false
    }

    var historyPage by persist<Result<HistoryPage>>("home/history/pageResult")
    LaunchedEffect(historyType) {
        if (historyType == HistoryType.YTMHistory && isYouTubeSyncEnabled()) {
            isYTMLoading = true
            historyPage = withContext(NzikDispatchers.DATA) { YtMusic.getHistory(setLogin = true) }
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
        historyPage?.getOrNull()?.sections.orEmpty().map { section ->
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
    ) {
        // Collapses while the bar is focused so the bar rises to the very
        // top, like the settings pages (settingsSearchTitle behavior)
        AnimatedVisibility(
            visible = searchFocus.titleVisible,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(250)) + fadeOut(tween(250)),
        ) {
            HeaderWithIcon(
                title = stringResource(R.string.history),
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
                    onValueUpdate = { historyType = it },
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier.padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sort.ToolBarButton()
                }
            }
        }

        search.SearchBar(this@Column, onSearch = { recordHistoryQuery() })

        val isLoading = when (historyType) {
            HistoryType.History -> isLocalLoading && events.isEmpty()
            HistoryType.YTMHistory -> isYTMLoading || (historyPage == null && isYouTubeSyncEnabled())
        }

        SearchEmptyFeedback(
            query = search.inputValue,
            empty = noSearchResults,
            loading = isLoading || when (historyType) {
                HistoryType.History -> loadedEvents == null
                HistoryType.YTMHistory -> historyPage?.isSuccess != true || historyPage?.getOrNull() == null
            }
        )

        // Wiped with a plain alpha while the bar is focused with a blank
        // query, like the settings pages; the wipe is applied per content
        // block so the search history section in the list keeps its full
        // opacity
        Column(
            modifier = Modifier
                .weight(1f)
        ) {
            if (isLoading) {
                if (historyType == HistoryType.YTMHistory && !isOnline) {
                    OfflineContent(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .graphicsLayer { alpha = searchFocus.contentAlpha }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .graphicsLayer { alpha = searchFocus.contentAlpha },
                        contentAlignment = Alignment.Center
                    ) {
                        Loader()
                    }
                }
            } else if (
                (search.inputValue.isBlank() && historyMatches.isEmpty() && historyType == HistoryType.History && events.isEmpty()) ||
                (search.inputValue.isBlank() && historyMatches.isEmpty() && historyType == HistoryType.YTMHistory && historyPage?.getOrNull()?.sections.isNullOrEmpty())
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = searchFocus.contentAlpha },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = stringResource(R.string.no_items),
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
                    contentPadding = LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.End).asPaddingValues(),
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
                    item(key = "search_history") {
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


                    if (noSearchResults) {
                        item(key = "search_no_results") {
                            SearchEmptySpace(fillRemainingViewport = true)
                        }
                    }
                    if (historyType == HistoryType.History) {
                        filteredEvents.forEach { (headerStr, details) ->
                            stickyHeader {
                                Title(
                                    title = headerStr,
                                    modifier = Modifier
                                        .graphicsLayer { alpha = searchFocus.contentAlpha }
                                        // 16dp horizontal keeps the banner's edges on the
                                        // search bar's; Title's own 12dp inner padding
                                        // keeps the text inset
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .background(
                                            color = colorPalette().accent,
                                            shape = thumbnailShape()
                                        )
                                )
                            }

                            items(
                                items = details,
                                key = { it.event.id }
                            ) { event ->
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
                                Title(
                                    title = sectionTitle,
                                    modifier = Modifier
                                        .graphicsLayer { alpha = searchFocus.contentAlpha }
                                        // 16dp horizontal keeps the banner's edges on the
                                        // search bar's; Title's own 12dp inner padding
                                        // keeps the text inset
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .background(
                                            color = colorPalette().accent,
                                            shape = thumbnailShape()
                                        )
                                )
                            }

                            items(
                                items = sectionSongs,
                                key = { it.mediaId }
                            ) { mediaItem ->
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
