package app.it.fast4x.rimusic.ui.screens.search

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import app.n_zik.android.core.database.*

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
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
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.searchSuggestionsWithItems
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.n_zik.android.components.search.SearchBarBubble
import app.n_zik.android.components.search.hasContent
import app.n_zik.android.components.search.filterSearchHistory
import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.SearchEmptySpace
import app.n_zik.android.components.search.SearchResultsViewport
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.UnifiedSearchBar
import app.n_zik.android.components.search.rememberSearchHistory
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.SearchDisplayOrder
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.it.fast4x.rimusic.ui.components.themed.Header
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.n_zik.android.components.menu.artist.OnlineArtistItemMenu
import app.n_zik.android.components.menu.playlist.OnlinePlaylistItemMenu
import app.n_zik.android.components.menu.video.VideoItemMenu
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.ui.components.themed.TitleMiniSection
import app.it.fast4x.rimusic.ui.items.AlbumItem
import app.it.fast4x.rimusic.ui.items.ArtistItem
import app.it.fast4x.rimusic.ui.items.PlaylistItem
import app.it.fast4x.rimusic.ui.items.VideoItem
import app.n_zik.android.components.SongItem
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.px
import app.it.fast4x.rimusic.utils.align
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.forcePlay
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.searchDisplayOrderKey
import app.it.fast4x.rimusic.utils.secondary
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
    LaunchedEffect( Unit ) { barFocusRequester.requestFocus() }

    // Record only explicit submissions; fetching suggestions never persists input.
    fun recordOnlineQuery(input: String = textFieldValue.text) {
        val query = input.trim()
        if( query.isEmpty() || isHistoryPaused ) return
        history.value = SearchHistoryStore.record( context.preferences, SearchHistoryStore.PAGE_ONLINE, query )
    }

    //var suggestionsResult by persist<Result<List<String>?>?>("search/online/suggestionsResult")
    var suggestionsResult by remember(textFieldValue.text) {
        mutableStateOf<Result<Innertube.SearchSuggestions>?>(null)
    }

    LaunchedEffect(textFieldValue.text) {
        if (textFieldValue.text.isNotEmpty()) {
            delay(200)
            suggestionsResult = withContext(NzikDispatchers.DATA) {
                Innertube.searchSuggestionsWithItems(input = textFieldValue.text)
            }
        } else {
            // The query was cleared (X bubble): drop the previous query's
            // suggestions so the history is shown again, not stale results
            suggestionsResult = null
        }
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

    SearchEmptyFeedback(
        query = textFieldValue.text,
        empty = suggestionsResult?.getOrNull()?.hasContent() == false && historyMatches.isEmpty(),
        loading = suggestionsResult?.isSuccess != true
    )

    val rippleIndication = ripple(bounded = false)

    val coroutineScope = rememberCoroutineScope()

    val lazyListState = rememberLazyListState()

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
                        onSearch = {
                            if (textFieldValue.text.isNotEmpty()) {
                                recordOnlineQuery()
                                onSearch( textFieldValue.text )
                            }
                        },
                        focusRequester = barFocusRequester,
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
                modifier = Modifier
                    .fillMaxSize()
            ) {
                if (textFieldValue.text.isNotBlank() && suggestionsResult?.getOrNull()?.hasContent() == false && historyMatches.isEmpty()) {
                    item(key = "search_no_results") {
                        SearchEmptySpace(fillRemainingViewport = true)
                    }
                }
                // Results display order (user preference)
                when (searchDisplayOrder) {
                    SearchDisplayOrder.SuggestionsFirst -> {
                        // Afficher d'abord les suggestions
                        suggestionsResult?.getOrNull()?.takeIf { it.hasContent() }?.let { suggestions ->
                            item {
                                TitleMiniSection(title = stringResource(R.string.searches_suggestions),
                                    modifier = Modifier.padding(start = 24.dp)
                                )
                            }

                            suggestions.recommendedSong.let {
                                item{
                                    it?.asMediaItem?.let { mediaItem ->
                                        SongItem(
                                            song = mediaItem.asSong,
                                            navController = navController,
                                            modifier = Modifier
                                                .clip(uiRoundnessShape()),
                                            onClick = {
                                                recordOnlineQuery()
                                                binder?.player?.forcePlay(mediaItem)
                                            }
                                        )
                                    }
                                }
                            }
                            suggestions.recommendedAlbum?.let { album ->
                                item{
                                    AlbumItem(
                                        yearCentered = false,
                                        album = album,
                                        thumbnailSizePx = songThumbnailSizePx,
                                        thumbnailSizeDp = songThumbnailSizeDp,
                                        modifier = Modifier
                                            .clip(uiRoundnessShape()).combinedClickable(
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
                                item{
                                    ArtistItem(
                                        artist = artist,
                                        thumbnailSizePx = songThumbnailSizePx,
                                        thumbnailSizeDp = songThumbnailSizeDp,
                                        modifier = Modifier
                                            .clip(uiRoundnessShape()).combinedClickable(
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
                                item{
                                    PlaylistItem(
                                        playlist = playlist,
                                        thumbnailSizePx = songThumbnailSizePx,
                                        thumbnailSizeDp = songThumbnailSizeDp,
                                        modifier = Modifier
                                            .clip(uiRoundnessShape()).combinedClickable(
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
                                                    navController.navigate(route = "${NavRoutes.playlist.name}/${playlist.key}")
                                                }
                                            ),
                                        disableScrollingText = disableScrollingText
                                    )
                                }
                            }
                            suggestions.recommendedVideo?.let { video ->
                                item{
                                    VideoItem(
                                        video = video,
                                        thumbnailHeightDp = songThumbnailSizeDp,
                                        thumbnailWidthDp = songThumbnailSizeDp,
                                        modifier = Modifier
                                            .clip(uiRoundnessShape()).combinedClickable(
                                                onLongClick = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.display {
                                                        VideoItemMenu(
                                                            navController = navController,
                                                            song = video.asMediaItem.asSong
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

                            items(items = suggestions.queries) { query ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(uiRoundnessShape()).clickable (
                                            onClick = {
                                                recordOnlineQuery(query)
                                                onSearch(query.replace("/", "", true))
                                                /*
                                                onTextFieldValueChanged(
                                                    TextFieldValue(
                                                        cleanString(query)
                                                    )
                                                )
                                                 */
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

                                    Image(
                                        painter = painterResource(R.drawable.pencil),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(colorPalette().textDisabled),
                                        modifier = Modifier
                                            .clip(uiRoundnessShape()).clickable(
                                                indication = rippleIndication,
                                                interactionSource = remember { MutableInteractionSource() },
                                                onClick = {
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
                                            )
                                            //.rotate(225f)
                                            .padding(horizontal = 8.dp)
                                            .size(22.dp)
                                    )
                                }
                            }
                        } ?: suggestionsResult?.exceptionOrNull()?.let {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                ) {
                                    TitleMiniSection(title = stringResource(R.string.searches_no_suggestions),
                                        modifier = Modifier.padding(start = 24.dp)
                                    )
                                    /*
                                    BasicText(
                                        text = stringResource(R.string.error),
                                        style = typography().s.secondary.center,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                    )
                                     */
                                }
                            }
                        }

                        // Puis afficher l'historique (toujours visible)
                        item(key = "search_history") {
                            SearchHistorySection(
                                visible = historyMatches.isNotEmpty(),
                                queries = historyMatches,
                                onQueryClick = { query ->
                                    recordOnlineQuery(query)
                                    onSearch(query.replace("/", "", true))
                                },
                                onQueryDelete = {
                                    history.value = SearchHistoryStore.delete(context.preferences, SearchHistoryStore.PAGE_ONLINE, it)
                                },
                                onQueryDeleteAll = {
                                    history.value = SearchHistoryStore.deleteAll(context.preferences, SearchHistoryStore.PAGE_ONLINE)
                                },
                                onQueryEdit = { query ->
                                    onTextFieldValueChanged(TextFieldValue(query, selection = TextRange(query.length)))
                                    barFocusRequester.requestFocus()
                                    coroutineScope.launch { lazyListState.animateScrollToItem(0) }
                                },
                            )
                        }
                    }

                    SearchDisplayOrder.SavedSearchesFirst -> {
                        // Afficher d'abord l'historique (toujours visible)
                        item(key = "search_history") {
                            SearchHistorySection(
                                visible = historyMatches.isNotEmpty(),
                                queries = historyMatches,
                                onQueryClick = { query ->
                                    recordOnlineQuery(query)
                                    onSearch(query.replace("/", "", true))
                                },
                                onQueryDelete = {
                                    history.value = SearchHistoryStore.delete(context.preferences, SearchHistoryStore.PAGE_ONLINE, it)
                                },
                                onQueryDeleteAll = {
                                    history.value = SearchHistoryStore.deleteAll(context.preferences, SearchHistoryStore.PAGE_ONLINE)
                                },
                                onQueryEdit = { query ->
                                    onTextFieldValueChanged(TextFieldValue(query, selection = TextRange(query.length)))
                                    barFocusRequester.requestFocus()
                                    coroutineScope.launch { lazyListState.animateScrollToItem(0) }
                                },
                            )
                        }

                        // Puis afficher les suggestions
                        suggestionsResult?.getOrNull()?.takeIf { it.hasContent() }?.let { suggestions ->
                            item {
                                TitleMiniSection(title = stringResource(R.string.searches_suggestions),
                                    modifier = Modifier.padding(start = 24.dp)
                                )
                            }

                            suggestions.recommendedSong.let {
                                item{
                                    it?.asMediaItem?.let { mediaItem ->
                                        SongItem(
                                            song = mediaItem.asSong,
                                            navController = navController,
                                            modifier = Modifier
                                                .clip(uiRoundnessShape()),
                                            onClick = {
                                                recordOnlineQuery()
                                                binder?.player?.forcePlay(mediaItem)
                                            }
                                        )
                                    }
                                }
                            }
                            suggestions.recommendedAlbum?.let { album ->
                                item{
                                    AlbumItem(
                                        yearCentered = false,
                                        album = album,
                                        thumbnailSizePx = songThumbnailSizePx,
                                        thumbnailSizeDp = songThumbnailSizeDp,
                                        modifier = Modifier
                                            .clip(uiRoundnessShape()).combinedClickable(
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
                                item{
                                    ArtistItem(
                                        artist = artist,
                                        thumbnailSizePx = songThumbnailSizePx,
                                        thumbnailSizeDp = songThumbnailSizeDp,
                                        modifier = Modifier
                                            .clip(uiRoundnessShape()).combinedClickable(
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
                                item{
                                    PlaylistItem(
                                        playlist = playlist,
                                        thumbnailSizePx = songThumbnailSizePx,
                                        thumbnailSizeDp = songThumbnailSizeDp,
                                        modifier = Modifier
                                            .clip(uiRoundnessShape()).combinedClickable(
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
                                                    navController.navigate(route = "${NavRoutes.playlist.name}/${playlist.key}")
                                                }
                                            ),
                                        disableScrollingText = disableScrollingText
                                    )
                                }
                            }
                            suggestions.recommendedVideo?.let { video ->
                                item{
                                    VideoItem(
                                        video = video,
                                        thumbnailHeightDp = songThumbnailSizeDp,
                                        thumbnailWidthDp = songThumbnailSizeDp,
                                        modifier = Modifier
                                            .clip(uiRoundnessShape()).combinedClickable(
                                                onLongClick = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.display {
                                                        VideoItemMenu(
                                                            navController = navController,
                                                            song = video.asMediaItem.asSong
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

                            items(items = suggestions.queries) { query ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(uiRoundnessShape()).clickable (
                                            onClick = {
                                                recordOnlineQuery(query)
                                                onSearch(query.replace("/", "", true))
                                                /*
                                                onTextFieldValueChanged(
                                                    TextFieldValue(
                                                        cleanString(query)
                                                    )
                                                )
                                                 */
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

                                    Image(
                                        painter = painterResource(R.drawable.pencil),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(colorPalette().textDisabled),
                                        modifier = Modifier
                                            .clip(uiRoundnessShape()).clickable(
                                                indication = rippleIndication,
                                                interactionSource = remember { MutableInteractionSource() },
                                                onClick = {
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
                                            )
                                            //.rotate(225f)
                                            .padding(horizontal = 8.dp)
                                            .size(22.dp)
                                    )
                                }
                            }
                        } ?: suggestionsResult?.exceptionOrNull()?.let {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                ) {
                                    TitleMiniSection(title = stringResource(R.string.searches_no_suggestions),
                                        modifier = Modifier.padding(start = 24.dp)
                                    )
                                    /*
                                    BasicText(
                                        text = stringResource(R.string.error),
                                        style = typography().s.secondary.center,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                    )
                                     */
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }

}
