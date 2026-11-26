package app.n_zik.android.ui.screens.searchresult

import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.SearchEmptySpace
import app.n_zik.android.components.search.SearchNoResults
import app.n_zik.android.components.search.SearchResultContent
import app.n_zik.android.components.search.rememberSearchResultOpacity
import androidx.compose.runtime.key
import androidx.compose.animation.ExperimentalAnimationApi
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

import app.n_zik.android.compose.persist.persist
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.utils.plus
import app.n_zik.android.enums.ContentType
import app.n_zik.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext
import app.n_zik.android.colorPalette
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.n_zik.android.components.OfflineContent
import app.n_zik.android.components.ui.UiButton
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val PAGE_TIMEOUT_MS = 30_000L
private const val MAX_NO_PROGRESS_PAGES = 3

private class ItemsPageLoadState(initialPending: Boolean) {
    var initialPending by mutableStateOf(initialPending)
    var loadingMore by mutableStateOf(false)
    var continuationFailed by mutableStateOf(false)
    var initialRetry by mutableIntStateOf(0)
    var continuationRetry by mutableIntStateOf(0)
}

private suspend fun <T : Innertube.Item> requestItemsPage(
    provider: suspend (String?) -> Result<Innertube.ItemsPage<T>?>?,
    continuation: String?,
): Result<Innertube.ItemsPage<T>?>? = try {
    withTimeoutOrNull(PAGE_TIMEOUT_MS) {
        withContext(NzikDispatchers.DATA) { provider(continuation) }
    }?.onFailure {
        currentCoroutineContext().ensureActive()
        Timber.tag("ItemsPage").w(it, "Failed to load items")
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Timber.tag("ItemsPage").w(error, "Failed to load items")
    Result.failure(error)
}

@Composable
private fun <T : Innertube.Item> rememberItemsPageLoad(
    tag: String,
    searchQuery: String?,
    pageState: MutableState<Innertube.ItemsPage<T>?>,
    provider: (suspend (String?) -> Result<Innertube.ItemsPage<T>?>?)?,
    isOnline: Boolean,
    forceShimmer: Boolean,
    isNearEnd: () -> Boolean,
): ItemsPageLoadState {
    val updatedProvider by rememberUpdatedState(provider)
    val updatedIsNearEnd by rememberUpdatedState(isNearEnd)
    val state = remember(tag, searchQuery, pageState) {
        ItemsPageLoadState(pageState.value == null && provider != null)
    }
    val hasProvider = provider != null
    LaunchedEffect(state, hasProvider, isOnline, state.initialRetry) {
        val load = updatedProvider
        if (pageState.value != null || load == null) {
            state.initialPending = false
            return@LaunchedEffect
        }
        state.initialPending = true
        try {
            requestItemsPage(load, null)?.onSuccess { page ->
                pageState.value = page ?: Innertube.ItemsPage(null, null)
            }
        } finally {
            state.initialPending = false
        }
    }
    LaunchedEffect(state, hasProvider, isOnline, forceShimmer, state.continuationRetry) {
        val load = updatedProvider ?: return@LaunchedEffect
        if (!isOnline || forceShimmer || state.continuationFailed) return@LaunchedEffect
        var retry = state.continuationRetry > 0
        val requestedTokens = mutableSetOf<String>()
        var noProgressPages = 0
        while (true) {
            val page = snapshotFlow {
                pageState.value?.takeIf { it.continuation != null && (retry || updatedIsNearEnd()) }
            }.first { it != null } ?: return@LaunchedEffect
            val token = page.continuation ?: return@LaunchedEffect
            retry = false
            state.loadingMore = true
            try {
                requestedTokens.add(token)
                val result = requestItemsPage(load, token)
                if (result == null || result.isFailure) {
                    state.continuationFailed = true
                    return@LaunchedEffect
                }
                val next = result.getOrNull()
                if (next == null) {
                    pageState.value = page.copy(continuation = null)
                    return@LaunchedEffect
                }

                val merged = withContext(NzikDispatchers.DATA) { page + next }
                noProgressPages = if (merged.items.orEmpty().size > page.items.orEmpty().size) {
                    0
                } else {
                    noProgressPages + 1
                }
                val stalled = next.continuation != null &&
                    (next.continuation in requestedTokens || noProgressPages >= MAX_NO_PROGRESS_PAGES)
                pageState.value = if (stalled) merged.copy(continuation = token) else merged
                if (stalled) {
                    state.continuationFailed = true
                    return@LaunchedEffect
                }
                delay(250) // Let the loaded rows reach the viewport before checking its end again.
            } finally {
                state.loadingMore = false
            }
        }
    }
    return state
}

@Composable
private fun ItemsPageRetry(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.common_load_failed))
        UiButton(onClick = onRetry) { Text(stringResource(R.string.common_action_retry)) }
    }
}

/** Chrome clearance is supplied by the page host and shared by loading and loaded content. */
@ExperimentalAnimationApi
@Composable
fun <T : Innertube.Item> ItemsPage(
    tag: String,
    headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    itemContent: @Composable LazyItemScope.(T) -> Unit,
    itemPlaceholderContent: @Composable (T?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = Dimensions.bottomSpacer),
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 3,
    emptyItemsText: String = "",
    filterContentType: ContentType = ContentType.All,
    itemsPageProvider: (suspend (String?) -> Result<Innertube.ItemsPage<T>?>?)? = null,
    loadedItemsState: MutableState<List<T>>? = null,
    searchQuery: String? = null,
    showEmptyMessage: (String) -> Unit = { Toaster.i(it) },
) {
    val isOnline by NetworkQualityHelper.isNetworkAvailableComposable(LocalContext.current)
    val forceShimmer = forceLoadingShimmerPreview()

    val lazyListState = key(tag, searchQuery) { rememberLazyListState() }
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)

    val pageState = key(tag, searchQuery) { persist<Innertube.ItemsPage<T>?>(tag) }
    val itemsPage by pageState
    var awaitingFirstPage by remember(tag, searchQuery, filterContentType) { mutableStateOf(itemsPage == null) }
    val loadState = rememberItemsPageLoad(tag, searchQuery, pageState, itemsPageProvider, isOnline, forceShimmer) {
        val layout = lazyListState.layoutInfo
        val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
        layout.totalItemsCount > 0 && lastVisible >= 0 && lastVisible >= layout.totalItemsCount - 20
    }

    LaunchedEffect(lazyListState, itemsPage != null) {
        if (itemsPage != null && awaitingFirstPage) {
            lazyListState.scrollToItem(0)
            awaitingFirstPage = false
        }
    }

    LaunchedEffect(itemsPage?.items) {
        loadedItemsState?.value = itemsPage?.items ?: emptyList()
    }

    val visibleItems = remember(itemsPage, parentalControlEnabled, filterContentType) {
        itemsPage?.items?.filter { item ->
                        if (parentalControlEnabled && item is Innertube.SongItem && item.explicit) return@filter false
                        when {
                            item is Innertube.SongItem -> {
                                when (filterContentType) {
                                    ContentType.All -> true
                                    ContentType.Official -> !item.isUserGeneratedContent
                                    ContentType.UserGenerated -> item.isUserGeneratedContent
                                }
                            }
                            item is Innertube.VideoItem -> {
                                when (filterContentType) {
                                    ContentType.All -> true
                                    ContentType.Official -> !item.isUserGeneratedContent
                                    ContentType.UserGenerated -> item.isUserGeneratedContent
                                }
                            }
                            else -> true
                        }
                    }?.distinctBy { item -> "${item::class.simpleName}_${item.key.ifEmpty { System.identityHashCode(item) }}" } ?: emptyList()
    }
    val resultOpacity = rememberSearchResultOpacity(searchQuery == null || (itemsPage != null && !forceShimmer))
    SearchEmptyFeedback(
        query = searchQuery.orEmpty(),
        empty = itemsPage != null && itemsPage?.continuation == null && visibleItems.isEmpty(),
        loading = forceShimmer || loadState.initialPending || loadState.loadingMore,
        showMessage = showEmptyMessage,
    )
    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = contentPadding,
            modifier = modifier
                .fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = "header",
            ) {
                Column {
                    headerContent(null)
                }
            }

            if (itemsPage == null) {
                if (forceShimmer || (isOnline && loadState.initialPending)) {
                    items(initialPlaceholderCount, key = { "loading_$it" }) {
                        itemPlaceholderContent(null)
                    }
                } else {
                    item(key = "loading_status") {
                        if (isOnline && itemsPageProvider != null) {
                            ItemsPageRetry { loadState.initialRetry++ }
                        } else if (isOnline) {
                            SearchNoResults(
                                text = stringResource(R.string.common_load_failed),
                                fillRemainingViewport = true,
                            )
                        } else {
                            OfflineContent(Modifier.fillMaxWidth())
                        }
                    }
                }
                return@LazyColumn
            }

            items(
                visibleItems,
                key = { item -> "${item::class.simpleName}_${item.key.ifEmpty { System.identityHashCode(item) }}" },
            ) { item ->
                if (forceShimmer) itemPlaceholderContent(item)
                else SearchResultContent(resultOpacity, if (searchQuery == null) Modifier else Modifier.animateItem(fadeInSpec = null)) { itemContent(item) }
            }

            if (itemsPage != null && visibleItems.isEmpty() && itemsPage?.continuation == null) {
                item {
                    if (searchQuery == null) {
                        SearchNoResults(
                            text = emptyItemsText.ifEmpty { stringResource(R.string.search_items_empty_message) },
                            fillRemainingViewport = true,
                        )
                    } else {
                        SearchEmptySpace(fillRemainingViewport = true)
                    }
                }
            }

            if (itemsPage?.continuation != null && (forceShimmer || loadState.loadingMore)) {
                val count = if (visibleItems.isEmpty()) initialPlaceholderCount else continuationPlaceholderCount
                items(count, key = { "loading_more_$it" }, contentType = { "placeholder" }) { itemPlaceholderContent(null) }
            } else if (itemsPage?.continuation != null && loadState.continuationFailed && isOnline) {
                item(key = "continuation_retry") {
                    ItemsPageRetry {
                        loadState.continuationFailed = false
                        loadState.continuationRetry++
                    }
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}

/** Chrome clearance is supplied by the page host and shared by loading and loaded content. */
@ExperimentalAnimationApi
@Composable
fun <T : Innertube.Item> ItemsGridPage(
    tag: String,
    headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    itemContent: @Composable LazyGridItemScope.(T) -> Unit,
    itemPlaceholderContent: @Composable (T?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = Dimensions.bottomSpacer),
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 6,
    emptyItemsText: String = "",
    filterContentType: ContentType = ContentType.All,
    itemsPageProvider: (suspend (String?) -> Result<Innertube.ItemsPage<T>?>?)? = null,
    thumbnailSizeDp: Dp,
    loadedItemsState: MutableState<List<T>>? = null,
    searchQuery: String? = null,
    showEmptyMessage: (String) -> Unit = { Toaster.i(it) },
) {
    val isOnline by NetworkQualityHelper.isNetworkAvailableComposable(LocalContext.current)
    val forceShimmer = forceLoadingShimmerPreview()

    val lazyGridState = key(tag, searchQuery) { rememberLazyGridState() }
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)

    val pageState = key(tag, searchQuery) { persist<Innertube.ItemsPage<T>?>(tag) }
    val itemsPage by pageState
    var awaitingFirstPage by remember(tag, searchQuery, filterContentType) { mutableStateOf(itemsPage == null) }
    val loadState = rememberItemsPageLoad(tag, searchQuery, pageState, itemsPageProvider, isOnline, forceShimmer) {
        val layout = lazyGridState.layoutInfo
        val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
        layout.totalItemsCount > 0 && lastVisible >= 0 && lastVisible >= layout.totalItemsCount - 20
    }

    LaunchedEffect(lazyGridState, itemsPage != null) {
        if (itemsPage != null && awaitingFirstPage) {
            lazyGridState.scrollToItem(0)
            awaitingFirstPage = false
        }
    }

    LaunchedEffect(itemsPage?.items) {
        loadedItemsState?.value = itemsPage?.items ?: emptyList()
    }

    val visibleItems = remember(itemsPage, parentalControlEnabled, filterContentType) {
        itemsPage?.items?.filter { item ->
                        if (parentalControlEnabled && item is Innertube.SongItem && item.explicit) return@filter false
                        when {
                            item is Innertube.SongItem -> {
                                when (filterContentType) {
                                    ContentType.All -> true
                                    ContentType.Official -> !item.isUserGeneratedContent
                                    ContentType.UserGenerated -> item.isUserGeneratedContent
                                }
                            }
                            item is Innertube.VideoItem -> {
                                when (filterContentType) {
                                    ContentType.All -> true
                                    ContentType.Official -> !item.isUserGeneratedContent
                                    ContentType.UserGenerated -> item.isUserGeneratedContent
                                }
                            }
                            else -> true
                        }
                    }?.distinctBy { item -> "${item::class.simpleName}_${item.key.ifEmpty { System.identityHashCode(item) }}" } ?: emptyList()
    }
    val resultOpacity = rememberSearchResultOpacity(searchQuery == null || (itemsPage != null && !forceShimmer))
    SearchEmptyFeedback(
        query = searchQuery.orEmpty(),
        empty = itemsPage != null && itemsPage?.continuation == null && visibleItems.isEmpty(),
        loading = forceShimmer || loadState.initialPending || loadState.loadingMore,
        showMessage = showEmptyMessage,
    )
    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Adaptive(thumbnailSizeDp),
            contentPadding = contentPadding,
            modifier = modifier
                .background(colorPalette().background0)
                .fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = 0,
                span = { GridItemSpan(maxLineSpan) }
            ) {
                Column {
                    headerContent(null)
                }
            }

            if (itemsPage == null) {
                if (forceShimmer || (isOnline && loadState.initialPending)) {
                    items(initialPlaceholderCount, key = { "loading_$it" }) {
                        itemPlaceholderContent(null)
                    }
                } else {
                    item(key = "loading_status", span = { GridItemSpan(maxLineSpan) }) {
                        if (isOnline && itemsPageProvider != null) {
                            ItemsPageRetry { loadState.initialRetry++ }
                        } else if (isOnline) {
                            SearchNoResults(
                                text = stringResource(R.string.common_load_failed),
                                fillRemainingViewport = true,
                            )
                        } else {
                            OfflineContent(Modifier.fillMaxWidth())
                        }
                    }
                }
                return@LazyVerticalGrid
            }

            items(
                visibleItems,
                key = { item -> "${item::class.simpleName}_${item.key.ifEmpty { System.identityHashCode(item) }}" },
            ) { item ->
                if (forceShimmer) itemPlaceholderContent(item)
                else SearchResultContent(resultOpacity, if (searchQuery == null) Modifier else Modifier.animateItem(fadeInSpec = null)) { itemContent(item) }
            }

            if (itemsPage != null && visibleItems.isEmpty() && itemsPage?.continuation == null) {
                item(
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    if (searchQuery == null) {
                        SearchNoResults(
                            text = emptyItemsText.ifEmpty { stringResource(R.string.search_items_empty_message) },
                            fillRemainingViewport = true,
                        )
                    } else {
                        SearchEmptySpace(fillRemainingViewport = true)
                    }
                }
            }

            if (itemsPage?.continuation != null && (forceShimmer || loadState.loadingMore)) {
                val count = if (visibleItems.isEmpty()) initialPlaceholderCount else continuationPlaceholderCount
                items(count, key = { "loading_more_$it" }, contentType = { "placeholder" }) { itemPlaceholderContent(null) }
            } else if (itemsPage?.continuation != null && loadState.continuationFailed && isOnline) {
                item(key = "continuation_retry", span = { GridItemSpan(maxLineSpan) }) {
                    ItemsPageRetry {
                        loadState.continuationFailed = false
                        loadState.continuationRetry++
                    }
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyGridState = lazyGridState)
    }
}




