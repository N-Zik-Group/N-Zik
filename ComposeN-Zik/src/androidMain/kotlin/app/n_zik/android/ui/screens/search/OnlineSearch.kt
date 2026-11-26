package app.n_zik.android.ui.screens.search

import app.n_zik.android.components.glass.glassGlobalIndication

import androidx.compose.ui.unit.Dp

import app.n_zik.android.components.search.SearchSuggestionEditAction
import app.n_zik.android.components.navigation.header.HeaderNativeActionIconSize

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape


import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.searchSuggestionsWithItems
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.n_zik.android.components.search.SearchBarBubble
import app.n_zik.android.components.search.hasContent
import app.n_zik.android.components.search.filterSearchHistory
import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.SearchEmptySpace
import app.n_zik.android.components.search.SearchNoResults
import app.n_zik.android.components.search.SearchResultsViewport
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.UnifiedSearchBar
import app.n_zik.android.components.settings.common.SettingsSearchBarPadding
import app.n_zik.android.components.search.rememberSearchHistory
import app.n_zik.android.components.navigation.onlinePlaylistRoute
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.SearchDisplayOrder
import app.n_zik.android.typography
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.n_zik.android.components.menu.artist.OnlineArtistItemMenu
import app.n_zik.android.components.menu.playlist.OnlinePlaylistItemMenu
import app.n_zik.android.components.menu.video.VideoItemMenu
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.ui.components.themed.TitleMiniSection
import app.n_zik.android.ui.components.themed.ItemTextPlaceholder
import app.n_zik.android.ui.items.AlbumItem
import app.n_zik.android.ui.items.AlbumItemPlaceholder
import app.n_zik.android.ui.items.ArtistItem
import app.n_zik.android.ui.items.ArtistItemPlaceholder
import app.n_zik.android.ui.items.PlaylistItem
import app.n_zik.android.ui.items.PlaylistItemPlaceholder
import app.n_zik.android.ui.items.SongItemPlaceholder
import app.n_zik.android.core.database.artistEntryNames
import app.n_zik.android.cleanPrefix
import app.n_zik.android.ui.items.VideoItem
import app.n_zik.android.ui.items.VideoItemPlaceholder
import app.n_zik.android.components.SongItem
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.px
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.forcePlay
import app.n_zik.android.utils.pauseSearchHistoryKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.searchDisplayOrderKey
import app.n_zik.android.utils.secondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.utils.coroutines.NzikDispatchers

@UnstableApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalTextApi
@Composable
fun OnlineSearch(
    topContentInsetProvider: () -> Dp,
    navController: NavController,
    textFieldValue: TextFieldValue,
    onTextFieldValueChanged: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    actionBubbles: List<SearchBarBubble>,
) {
    // Settings
    val isHistoryPaused by rememberPreference( pauseSearchHistoryKey, false )
    val searchDisplayOrder by rememberPreference( searchDisplayOrderKey, SearchDisplayOrder.SuggestionsFirst )

    // Saved searches: this tab's own store (not shared with the other screens)
    val context = LocalContext.current
    val history = rememberSearchHistory( SearchHistoryStore.PAGE_ONLINE, isHistoryPaused )
    val historyMatches = filterSearchHistory(history.value, textFieldValue.text)

    // Opening the online tab should land the user in the search (keyboard up)
    val barFocusRequester = remember { FocusRequester() }

    // Record only explicit submissions; fetching suggestions never persists input.
    fun recordOnlineQuery(input: String = textFieldValue.text) {
        val query = input.trim()
        if( query.isEmpty() || isHistoryPaused ) return
        history.value = SearchHistoryStore.record( context.preferences, SearchHistoryStore.PAGE_ONLINE, query )
    }

    val suggestionQuery = textFieldValue.text
    var suggestionResponse by remember {
        mutableStateOf<Pair<String, Result<Innertube.SearchSuggestions>?>?>(null)
    }
    LaunchedEffect(suggestionQuery) {
        if (suggestionQuery.isBlank()) {
            suggestionResponse = null
        } else {
            delay(200)
            val result = withContext(NzikDispatchers.DATA) {
                Innertube.searchSuggestionsWithItems(input = suggestionQuery)
            }
            suggestionResponse = suggestionQuery to result
        }
    }
    val suggestionsPending = suggestionQuery.isNotBlank() && suggestionResponse?.first != suggestionQuery
    val suggestionsResult = suggestionResponse?.second?.takeIf {
        suggestionQuery.isNotBlank() && (!suggestionsPending || it.isSuccess)
    }

    /*
    val playlistId = remember(textFieldValue.text) {
        val isPlaylistUrl = listOf(
            "https://www.youtube.com/playlist?",
            "https://youtube.com/playlist?",
            "https://music.youtube.com/playlist?",
            "https://m.youtube.com/playlist?"
        ).any(textFieldValue.text::startsWith)

        if (isPlaylistUrl) textFieldValue.text.toUri().getQueryParameter("list") else null
    }
    */

    val forceShimmer = forceLoadingShimmerPreview()
    val showSuggestionPlaceholders = suggestionsPending &&
        suggestionsResult?.getOrNull()?.hasContent() != true
    SearchEmptyFeedback(
        query = textFieldValue.text,
        empty = suggestionsResult?.getOrNull()?.hasContent() == false && historyMatches.isEmpty(),
        loading = forceShimmer || suggestionsPending
    )

    val rippleIndication = glassGlobalIndication(ripple(bounded = false))

    val coroutineScope = rememberCoroutineScope()

    val lazyListState = rememberLazyListState()
    suspend fun focusSearchBar() {
        lazyListState.scrollToItem(0)
        withFrameNanos { }
        barFocusRequester.requestFocus()
    }

    LaunchedEffect(Unit) { focusSearchBar() }

    val searchHistoryContent: @Composable () -> Unit = {
        SearchHistorySection(
            visible = historyMatches.isNotEmpty(),
            queries = historyMatches,
            allQueries = history.value,
            onQueryClick = { query ->
                recordOnlineQuery(query)
                onSearch(query)
            },
            onQueryDelete = {
                history.value = SearchHistoryStore.delete(context.preferences, SearchHistoryStore.PAGE_ONLINE, it)
            },
            onQueryDeleteAll = {
                history.value = SearchHistoryStore.deleteAll(context.preferences, SearchHistoryStore.PAGE_ONLINE)
            },
            onQueryEdit = { query ->
                onTextFieldValueChanged(TextFieldValue(query, selection = TextRange(query.length)))
                coroutineScope.launch { focusSearchBar() }
            },
            shimmer = forceShimmer,
        )
    }

    //val navigationBarPosition by rememberPreference(navigationBarPositionKey, NavigationBarPosition.BottomFloating)
    //val contentWidth = context.preferences.getFloat(contentWidthKey,0.8f)
    var downloadState by remember {
        mutableStateOf(Download.STATE_STOPPED)
    }
    val songThumbnailSizeDp = Dimensions.thumbnails.song
    val songThumbnailSizePx = songThumbnailSizeDp.px
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current
    val binder = LocalPlayerServiceBinder.current

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    SearchResultsViewport(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = rememberSearchTabContentPadding(topContentInsetProvider),
            modifier = Modifier
                .fillMaxSize()
        ) {
            item(key = "search_controls", contentType = "search_controls") {
                Column {
                    SearchTabHeader(R.string.search_online, R.drawable.globe)
                    UnifiedSearchBar(
                        input = textFieldValue,
                        onInputChanged = onTextFieldValueChanged,
                        placeholderRes = R.string.search_title,
                        actionBubbles = actionBubbles,
                        onClear = { onTextFieldValueChanged(TextFieldValue("")) },
                        onSearch = {
                            if (textFieldValue.text.isNotEmpty()) {
                                recordOnlineQuery()
                                onSearch(textFieldValue.text)
                            }
                        },
                        focusRequester = barFocusRequester,
                        modifier = Modifier.fillMaxWidth().padding(SettingsSearchBarPadding),
                    )
                }
            }
            if (forceShimmer || showSuggestionPlaceholders) {
                if (searchDisplayOrder == SearchDisplayOrder.SavedSearchesFirst) {
                    item(key = "search_history", contentType = "history") { searchHistoryContent() }
                }
                val suggestions = suggestionsResult?.getOrNull()
                if (suggestionsPending || suggestions?.hasContent() == true) {
                    item(key = "search_suggestions_title", contentType = "section_title") {
                        TitleMiniSection(
                            title = stringResource(R.string.search_suggestions_title),
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    }
                    suggestions?.recommendedSong?.let { song ->
                        item(key = "search_recommended_song:${song.key}", contentType = "song") {
                            val model = song.asMediaItem.asSong
                            SongItemPlaceholder(title = model.cleanTitle(), artist = model.cleanArtistsText())
                        }
                    }
                    suggestions?.recommendedAlbum?.let { album ->
                        item(key = "search_recommended_album:${album.key}", contentType = "album") {
                            val metadata = buildList {
                                album.authors?.let { add(cleanPrefix(it.artistEntryNames().joinToString(", "))) }
                                album.year?.let(::add)
                            }
                            AlbumItemPlaceholder(
                                thumbnailSizeDp = songThumbnailSizeDp,
                                title = cleanPrefix(album.title.orEmpty()),
                                metadata = metadata,
                                metadataLines = metadata.size,
                            )
                        }
                    }
                    suggestions?.recommendedArtist?.let { artist ->
                        item(key = "search_recommended_artist:${artist.key}", contentType = "artist") {
                            ArtistItemPlaceholder(
                                thumbnailSizeDp = songThumbnailSizeDp,
                                title = artist.title,
                                subscribers = artist.subscribersCountText,
                                showSubscribers = artist.subscribersCountText != null,
                            )
                        }
                    }
                    suggestions?.recommendedPlaylist?.let { playlist ->
                        item(key = "search_recommended_playlist:${playlist.key}", contentType = "playlist") {
                            PlaylistItemPlaceholder(songThumbnailSizeDp,
                                showTitle = playlist.info?.name != null, showChannel = playlist.channel?.name != null,
                                title = playlist.title, channel = playlist.channel?.name)
                        }
                    }
                    suggestions?.recommendedVideo?.let { video ->
                        item(key = "search_recommended_video:${video.key}", contentType = "video") {
                            VideoItemPlaceholder(songThumbnailSizeDp, songThumbnailSizeDp, video = video)
                        }
                    }
                    val queries = suggestions?.queries.orEmpty().distinct()
                    items(
                        if (suggestionsPending) 4 else queries.size,
                        key = { index -> queries.getOrNull(index)?.let { "search_query:$it" } ?: "suggestion-loading-$index" },
                        contentType = { "search_query" },
                    ) { index ->
                        SearchQuerySuggestionPlaceholder(queries.getOrNull(index))
                    }
                }
                if (searchDisplayOrder == SearchDisplayOrder.SuggestionsFirst) {
                    item(key = "search_history", contentType = "history") { searchHistoryContent() }
                }
                return@LazyColumn
            }
            if (suggestionsResult?.isFailure == true && historyMatches.isEmpty()) {
                item(key = "search_suggestions_error", contentType = "empty") {
                    SearchNoResults(fillRemainingViewport = true, text = stringResource(R.string.common_load_failed))
                }
                return@LazyColumn
            }
            if (textFieldValue.text.isNotBlank() && suggestionsResult?.getOrNull()?.hasContent() == false && historyMatches.isEmpty()) {
                item(key = "search_no_results", contentType = "empty") {
                    SearchEmptySpace(fillRemainingViewport = true)
                }
            }
            if (searchDisplayOrder == SearchDisplayOrder.SavedSearchesFirst) {
                item(key = "search_history", contentType = "history") { searchHistoryContent() }
            }
            suggestionsResult?.getOrNull()?.takeIf { it.hasContent() }?.let { suggestions ->
                item(key = "search_suggestions_title", contentType = "section_title") {
                    TitleMiniSection(title = stringResource(R.string.search_suggestions_title),
                        modifier = Modifier.animateItem().padding(start = 24.dp)
                    )
                }

                suggestions.recommendedSong?.asMediaItem?.let { mediaItem ->
                    item(key = "search_recommended_song:${mediaItem.mediaId}", contentType = "song") {
                        SongItem(
                            song = mediaItem.asSong,
                            sourceMediaItem = { mediaItem },
                            navController = navController,
                            modifier = Modifier.animateItem()
                                .clip(uiRoundnessShape()),
                            onClick = {
                                if (!suggestionsPending) {
                                    recordOnlineQuery()
                                    binder?.player?.forcePlay(mediaItem)
                                }
                            }
                        )
                    }
                }
                suggestions.recommendedAlbum?.let { album ->
                    item(key = "search_recommended_album:${album.key}", contentType = "album") {
                        AlbumItem(
                            yearCentered = false,
                            album = album,
                            thumbnailSizePx = songThumbnailSizePx,
                            thumbnailSizeDp = songThumbnailSizeDp,
                            modifier = Modifier.animateItem()
                                .clip(uiRoundnessShape()).combinedClickable(
                                    enabled = !suggestionsPending,
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.display {
                                            OnlineAlbumItemMenu(
                                                navController = navController,
                                                album = album
                                            ).MenuComponent()
                                        }
                                    },
                                    onClick = {
                                        recordOnlineQuery()
                                        navController.navigate(route = "${NavRoutes.album.name}/${album.key}")
                                    }
                                ),
                            disableScrollingText = disableScrollingText
                        )
                    }
                }
                suggestions.recommendedArtist?.let { artist ->
                    item(key = "search_recommended_artist:${artist.key}", contentType = "artist") {
                        ArtistItem(
                            artist = artist,
                            thumbnailSizePx = songThumbnailSizePx,
                            thumbnailSizeDp = songThumbnailSizeDp,
                            modifier = Modifier.animateItem()
                                .clip(uiRoundnessShape()).combinedClickable(
                                    enabled = !suggestionsPending,
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.display {
                                            OnlineArtistItemMenu(
                                                navController = navController,
                                                artist = artist
                                            ).MenuComponent()
                                        }
                                    },
                                    onClick = {
                                        recordOnlineQuery()
                                        navController.navigate(route = "${NavRoutes.artist.name}/${artist.key}")
                                    }
                                ),
                            disableScrollingText = disableScrollingText
                        )
                    }
                }
                suggestions.recommendedPlaylist?.let { playlist ->
                    item(key = "search_recommended_playlist:${playlist.key}", contentType = "playlist") {
                        PlaylistItem(
                            playlist = playlist,
                            thumbnailSizePx = songThumbnailSizePx,
                            thumbnailSizeDp = songThumbnailSizeDp,
                            modifier = Modifier.animateItem()
                                .clip(uiRoundnessShape()).combinedClickable(
                                    enabled = !suggestionsPending,
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.display {
                                            OnlinePlaylistItemMenu(
                                                navController = navController,
                                                playlist = playlist
                                            ).MenuComponent()
                                        }
                                    },
                                    onClick = {
                                        recordOnlineQuery()
                                        navController.navigate(route = onlinePlaylistRoute(playlist.key))
                                    }
                                ),
                            disableScrollingText = disableScrollingText
                        )
                    }
                }
                suggestions.recommendedVideo?.let { video ->
                    item(key = "search_recommended_video:${video.key}", contentType = "video") {
                        VideoItem(
                            video = video,
                            thumbnailHeightDp = songThumbnailSizeDp,
                            thumbnailWidthDp = songThumbnailSizeDp,
                            modifier = Modifier.animateItem()
                                .clip(uiRoundnessShape()).combinedClickable(
                                    enabled = !suggestionsPending,
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.display {
                                            VideoItemMenu(
                                                navController = navController,
                                                mediaItem = video.asMediaItem
                                            ).MenuComponent()
                                        }
                                    },
                                    onClick = {
                                        recordOnlineQuery()
                                        binder?.player?.forcePlay(video.asMediaItem)
                                    }
                                ),
                            disableScrollingText = disableScrollingText
                        )
                    }
                }

                items(
                    items = suggestions.queries.distinct(),
                    key = { "search_query:$it" },
                    contentType = { "search_query" },
                ) { query ->
                    val editQuery: () -> Unit = {
                        onTextFieldValueChanged(
                            TextFieldValue(
                                text = query,
                                selection = TextRange(query.length)
                            )
                        )
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(0)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.animateItem()
                            .clip(uiRoundnessShape()).clickable (
                                onClick = {
                                    recordOnlineQuery(query)
                                    onSearch(query)
                                }
                            )
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(all = 16.dp)
                    ) {
                        Spacer(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(20.dp)
                        )

                        BasicText(
                            text = query,
                            style = typography().s.secondary,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .weight(1f)
                        )

                        SearchSuggestionEditAction(onClick = editQuery) {
                            Image(
                                painter = painterResource(R.drawable.pencil),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(colorPalette().textDisabled),
                                modifier = Modifier
                                    .size(HeaderNativeActionIconSize)
                            )
                        }
                    }
                }
            } ?: suggestionsResult?.exceptionOrNull()?.let {
                item(key = "search_suggestions_error", contentType = "error") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        TitleMiniSection(title = stringResource(R.string.search_suggestions_empty_message),
                            modifier = Modifier.padding(start = 24.dp)
                        )
                    }
                }
            }
            if (searchDisplayOrder == SearchDisplayOrder.SuggestionsFirst) {
                item(key = "search_history", contentType = "history") { searchHistoryContent() }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }

}

@Composable
private fun SearchQuerySuggestionPlaceholder(query: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(16.dp),
    ) {
        Spacer(Modifier.padding(horizontal = 8.dp).size(20.dp))
        Box(Modifier.padding(horizontal = 8.dp).weight(1f)) {
            ItemTextPlaceholder(query, typography().s.secondary, maxLines = Int.MAX_VALUE)
        }
        SearchSuggestionEditAction(onClick = {}, enabled = false) {
            Image(
                painter = painterResource(R.drawable.pencil),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette().textDisabled),
                modifier = Modifier.size(HeaderNativeActionIconSize),
            )
        }
    }
}
