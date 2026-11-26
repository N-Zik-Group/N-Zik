package app.n_zik.android.ui.screens.search

import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.components.settings.appearance.preview.ThemePreviewDemo
import androidx.compose.ui.unit.Dp

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape


import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.search.SearchBarBubble
import app.n_zik.android.components.search.filterSearchHistory
import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.SearchEmptySpace
import app.n_zik.android.components.search.SearchResultsViewport
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.UnifiedSearchBar
import app.n_zik.android.components.settings.common.SettingsSearchBarPadding
import app.n_zik.android.components.search.rememberSearchHistory
import app.n_zik.android.components.settings.common.rememberSettingsSearchFocus
import app.n_zik.android.components.settings.common.settingsSearchContentVisibility
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.items.SongItemPlaceholders
import app.n_zik.android.ui.items.SongItemPlaceholder
import app.n_zik.android.ui.styling.px
import app.n_zik.android.utils.pauseSearchHistoryKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import app.n_zik.android.R
import app.n_zik.android.components.menu.song.SongItemMenu
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.LocalDownloadStatesMap
import app.n_zik.android.enums.DownloadedStateMedia
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import timber.log.Timber

@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun LocalSongSearch(
    topContentInsetProvider: () -> Dp,
    navController: NavController,
    textFieldValue: TextFieldValue,
    onTextFieldValueChanged: (TextFieldValue) -> Unit,
    actionBubbles: List<SearchBarBubble>,
    onAction1: () -> Unit,
    onAction2: () -> Unit,
    onAction3: () -> Unit,
    onAction4: () -> Unit,
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val context = LocalContext.current

    // Keyed by the current text: without a key the remember keeps the first
    // flow, so the query stays frozen on the initial (empty) text and typing
    // never updates the results
    val preview = LocalThemePreview.current
    val queryResults by remember(textFieldValue.text, preview) {
        if (preview != null) kotlinx.coroutines.flow.flowOf(textFieldValue.text to ThemePreviewDemo.songs.filter {
            it.title.contains(textFieldValue.text, ignoreCase = true)
        }) else Database.songTable
                .findAllTitleArtistContainsLiteral(textFieldValue.text)
                .distinctUntilChanged()
                .map { textFieldValue.text to it }
    }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)
    val forceShimmer = forceLoadingShimmerPreview()
    val queryPending = queryResults?.first != textFieldValue.text
    val items = queryResults?.takeIf { !queryPending }?.second.orEmpty()
    val noSearchResults = textFieldValue.text.isNotBlank() && !queryPending && !forceShimmer && items.isEmpty()
    SearchEmptyFeedback(textFieldValue.text, noSearchResults, queryPending || forceShimmer)

    // Saved searches: this tab's own store (not shared with the other
    // screens); paused by the "pause search history" setting
    val isHistoryPaused by rememberPreference(pauseSearchHistoryKey, false)
    val history = rememberSearchHistory(SearchHistoryStore.PAGE_LOCAL, isHistoryPaused)

    var barFocused by remember { mutableStateOf(false) }
    // Same wipe as the settings pages: the content fades out while the bar is
    // focused with a blank query; results stay visible while typing
    val searchFocus = rememberSettingsSearchFocus(barFocused, textFieldValue.text.isBlank())

    // Debug-only diagnostics to pin down why the section stays hidden at
    // runtime (focus never reported vs. empty per-screen store)
    val historyMatches = filterSearchHistory(history.value, textFieldValue.text)
    val historyVisible = barFocused && historyMatches.isNotEmpty()
    LaunchedEffect(historyVisible) {
        Timber.tag(TAG).d(
            "history section visible=$historyVisible (barFocused=$barFocused, entries=${history.value.size})"
        )
    }


    // Live filtering does not persist a query until the user submits it.
    fun recordSearchQuery(input: String = textFieldValue.text) {
        val query = input.trim()
        if (query.isEmpty() || isHistoryPaused) return
        history.value = SearchHistoryStore.record(context.preferences, SearchHistoryStore.PAGE_LOCAL, query)
        Timber.tag(TAG).d("recorded local search '$query' (entries=${history.value.size})")
    }

    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailSizePx = thumbnailSizeDp.px

    val lazyListState = rememberLazyListState()

    // Bring the saved-search section back into view on focus.
    LaunchedEffect(barFocused) {
        if (barFocused) {
            lazyListState.scrollToItem(0, 0)
        }
        }

    val songIds = remember(items) { items.map { it.id } }
    val likeStatesMap by remember(songIds) {
        if (preview != null) kotlinx.coroutines.flow.flowOf(emptyMap()) else LikeStateManager.getLikeStates(songIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
    val playlistStatesMap by remember(songIds) {
        if (preview != null) kotlinx.coroutines.flow.flowOf(emptyMap()) else PlaylistStateManager.getPlaylistStates(songIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

    // Download state cache
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
            items.associate { song ->
                song.id to when {
                    song.id in downloadedIds -> DownloadedStateMedia.DOWNLOADED
                    else -> DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
                }
            }
        }
    }

    SearchResultsViewport(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        CompositionLocalProvider(LocalDownloadStatesMap provides downloadStatesMap) {
            LazyColumn(
                state = lazyListState,
                contentPadding = rememberSearchTabContentPadding(topContentInsetProvider),
                // The wipe (below) is applied per song item so the search
                // history section stays visible while the results fade
                modifier = Modifier
                    .fillMaxSize()
            ) {
                item(key = "search_controls", contentType = "search_controls") {
                    Column {
                        SearchTabHeader(R.string.library_title, R.drawable.library)
                        UnifiedSearchBar(
                            input = textFieldValue,
                            onInputChanged = onTextFieldValueChanged,
                            placeholderRes = R.string.search_title,
                            actionBubbles = actionBubbles,
                            onClear = { onTextFieldValueChanged(TextFieldValue("")) },
                            onSearch = { recordSearchQuery() },
                            onFocused = { focused ->
                                barFocused = focused
                                Timber.tag(TAG).d("search bar focused=$focused")
                            },
                            modifier = Modifier.fillMaxWidth().padding(SettingsSearchBarPadding),
                        )
                    }
                }
                item(key = "search_history") {
                    SearchHistorySection(
                        visible = !forceShimmer && historyVisible,
                        queries = historyMatches,
                        onQueryClick = { onTextFieldValueChanged(TextFieldValue(it)); recordSearchQuery(it) },
                        onQueryDelete = {
                            history.value = SearchHistoryStore.delete(context.preferences, SearchHistoryStore.PAGE_LOCAL, it)
                        },
                        onQueryDeleteAll = {
                            history.value = SearchHistoryStore.deleteAll(context.preferences, SearchHistoryStore.PAGE_LOCAL)
                        },
                    )
                }


                if ((forceShimmer && items.isEmpty()) || (queryPending && textFieldValue.text.isNotBlank())) {
                    item(key = "search_song_loading") { SongItemPlaceholders(count = 4) }
                } else if (noSearchResults) {
                    item(key = "search_no_results") {
                        SearchEmptySpace(fillRemainingViewport = true)
                    }
                }
                items(
                    items = items,
                    key = Song::id,
                ) { song ->
                    if (forceShimmer) {
                        SongItemPlaceholder(title = song.cleanTitle(), artist = song.cleanArtistsText())
                        return@items
                    }
                    Column(Modifier.settingsSearchContentVisibility(searchFocus.contentAlpha, retainSpace = true).animateItem()) {
                        SongItem(
                            song = song,
                            isLiked = likeStatesMap[song.id],
                            inPlaylist = playlistStatesMap[song.id],
                            navController = navController,
                            modifier = Modifier
                                .clip(uiRoundnessShape()),
                            onLongClick = {
                                menuState.display {
                                    SongItemMenu(
                                        navController = navController,
                                        song = song
                                    ).MenuComponent()
                                }
                            },
                            onClick = { recordSearchQuery(); binder?.startRadio( song ) }
                        )
                    }
                }
            }
        } // CompositionLocalProvider

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}

private const val TAG = "LocalSongSearch"
