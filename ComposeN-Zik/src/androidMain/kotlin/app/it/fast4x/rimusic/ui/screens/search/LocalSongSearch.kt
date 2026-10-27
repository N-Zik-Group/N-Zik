package app.it.fast4x.rimusic.ui.screens.search

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import app.n_zik.android.core.database.*

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.playback.services.isLocal
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.it.fast4x.rimusic.ui.components.themed.Header
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.search.SearchBarBubble
import app.n_zik.android.components.search.filterSearchHistory
import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.SearchEmptySpace
import app.n_zik.android.components.search.SearchResultsViewport
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.UnifiedSearchBar
import app.n_zik.android.components.search.rememberSearchHistory
import app.n_zik.android.components.settings.common.rememberSettingsSearchFocus
import app.n_zik.android.components.settings.common.settingsSearchContentVisibility
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.px
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.getDownloadState
import app.it.fast4x.rimusic.utils.isDownloadedSong
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import app.n_zik.android.R
import app.n_zik.android.components.menu.song.SongItemMenu
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.LocalDownloadStatesMap
import app.it.fast4x.rimusic.enums.DownloadedStateMedia
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
    val queryResults by remember(textFieldValue.text) {
        Database.songTable
                .findAllTitleArtistContains( textFieldValue.text )
                .distinctUntilChanged()
                .map { textFieldValue.text to it }
    }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)
    val items = queryResults?.takeIf { it.first == textFieldValue.text }?.second.orEmpty()
    val noSearchResults = textFieldValue.text.isNotBlank() && queryResults?.first == textFieldValue.text && items.isEmpty()
    SearchEmptyFeedback(textFieldValue.text, noSearchResults, queryResults?.first != textFieldValue.text)

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

    var downloadState by remember {
        mutableStateOf(Download.STATE_STOPPED)
    }
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val songIds = remember(items) { items.map { it.id } }
    val likeStatesMap by remember(songIds) {
        LikeStateManager.getLikeStates(songIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
    val playlistStatesMap by remember(songIds) {
        PlaylistStateManager.getPlaylistStates(songIds)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                // 16dp horizontal on the bar only: the list below stays
                // full-width so the rows touch the screen edges
                Header(
                    modifier = Modifier
                        .zIndex(1f)
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    titleContent = {
                        UnifiedSearchBar(
                            input = textFieldValue,
                            onInputChanged = onTextFieldValueChanged,
                            placeholderRes = R.string.search,
                            actionBubbles = actionBubbles,
                            onClear = { onTextFieldValueChanged(TextFieldValue("")) },
                            onSearch = { recordSearchQuery() },
                            onFocused = { focused ->
                                barFocused = focused
                                Timber.tag(TAG).d("search bar focused=$focused")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    actionsContent = {},
                )

                Spacer(
                    modifier = Modifier
                        .height(16.dp)
                )

                LazyColumn(
                    state = lazyListState,
                    // Bottom inset only: the top inset is already applied by the
                    // Skeleton scaffold
                    contentPadding = LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Bottom).asPaddingValues(),
                    // The wipe (below) is applied per song item so the search
                    // history section stays visible while the results fade
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    // Search history (this tab's own store): tapping a row
                    // runs a local search on it, the trash deletes one entry
                    // (long press: all of them). Nothing collapses above the
                    // bar on this screen, so "bar at the top" is reached as
                    // soon as the bar is focused. The block slides down out
                    // of the bar's bottom edge (the expanding top-anchored
                    // clip masks anything above the bar), so it reads as
                    // emerging from the bar while the content below is pushed
                    // down; the exit shrinks back into the bar
                    item(key = "search_history") {
                        SearchHistorySection(
                            visible = historyVisible,
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


                    if (noSearchResults) {
                        item(key = "search_no_results") {
                            SearchEmptySpace(fillRemainingViewport = true)
                        }
                    }
                    items(
                        items = items,
                        key = Song::id,
                    ) { song ->
                        val isLocal by remember { derivedStateOf { song.asMediaItem.isLocal } }
                        downloadState = getDownloadState(song.asMediaItem.mediaId)
                        val isDownloaded = if (!isLocal) isDownloadedSong(song.asMediaItem.mediaId) else true
                        Column(Modifier.settingsSearchContentVisibility(searchFocus.contentAlpha).animateItem()) {
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
            }
        } // CompositionLocalProvider

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}

private const val TAG = "LocalSongSearch"
