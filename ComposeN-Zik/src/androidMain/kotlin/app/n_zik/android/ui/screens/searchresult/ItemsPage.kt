package app.n_zik.android.ui.screens.searchresult

import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.SearchEmptySpace
import app.n_zik.android.components.search.SearchNoResults
import androidx.compose.runtime.key
import androidx.compose.animation.ExperimentalAnimationApi
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import app.n_zik.android.colorPalette
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.n_zik.android.components.OfflineContent
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import kotlinx.coroutines.delay
import timber.log.Timber
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.ui.unit.Dp

@PublishedApi
internal suspend fun <T : Innertube.Item> loadInitialItemsPage(
    provider: suspend (String?) -> Result<Innertube.ItemsPage<T>?>?,
): Result<Innertube.ItemsPage<T>>? = withContext(NzikDispatchers.DATA) { provider(null) }
    ?.onFailure { Timber.tag("ItemsPage").e(it, "Failed to load initial items") }
    ?.map { it ?: Innertube.ItemsPage<T>(null, null) }

/** Chrome clearance is supplied by the page host and shared by loading and loaded content. */
@ExperimentalAnimationApi
@Composable
inline fun <T : Innertube.Item> ItemsPage(
    tag: String,
    crossinline headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    crossinline itemContent: @Composable LazyItemScope.(T) -> Unit,
    noinline itemPlaceholderContent: @Composable (T?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = Dimensions.bottomSpacer),
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 3,
    emptyItemsText: String = "",
    filterContentType: ContentType = ContentType.All,
    noinline itemsPageProvider: (suspend (String?) -> Result<Innertube.ItemsPage<T>?>?)? = null,
    loadedItemsState: androidx.compose.runtime.MutableState<List<T>>? = null,
    searchQuery: String? = null,
    noinline showEmptyMessage: (String) -> Unit = { Toaster.i(it) },
) {
    val updatedItemsPageProvider by rememberUpdatedState(itemsPageProvider)
    val isOnline by NetworkQualityHelper.isNetworkAvailableComposable(LocalContext.current)
    val forceShimmer = forceLoadingShimmerPreview()

    val lazyListState = rememberLazyListState()
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)

    var itemsPage by key(tag, searchQuery) { persist<Innertube.ItemsPage<T>?>(tag) }
    var hasScrolledToTop by remember { mutableStateOf(false) }

    var isLoadingMore by remember(tag, searchQuery) { mutableStateOf(false) }
    var initialLoadPending by remember(tag, searchQuery) { mutableStateOf(itemsPage == null && itemsPageProvider != null) }

    LaunchedEffect(tag, searchQuery, lazyListState, updatedItemsPageProvider, forceShimmer) {
        if (forceShimmer) return@LaunchedEffect
        val currentItemsPageProvider = updatedItemsPageProvider ?: return@LaunchedEffect

        while (true) {
            val shouldLoad = snapshotFlow {
                val info = lazyListState.layoutInfo
                val total = info.totalItemsCount
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                val hasContinuation = itemsPage?.continuation != null
                (hasContinuation && total > 0 && lastVisible >= total - 20) && !isLoadingMore
            }
                .distinctUntilChanged()
                .first { it }

            if (shouldLoad) {
                isLoadingMore = true
                try {
                    val currentContinuation = itemsPage?.continuation
                    withContext(NzikDispatchers.DATA) {
                        currentItemsPageProvider(currentContinuation)
                    }?.onSuccess { newPage ->
                        if (newPage == null) {
                            if (itemsPage == null) {
                                itemsPage = Innertube.ItemsPage(null, null)
                            }
                        } else {
                            val merged = withContext(NzikDispatchers.DATA) {
                                itemsPage + newPage
                            }
                            itemsPage = merged
                            loadedItemsState?.value = merged.items ?: emptyList()
                        }
                    }?.onFailure {
                        Timber.tag("ItemsPage").e(it, "Failed to load more items")
                        delay(2000) // Avoid rapid retry on failure
                    }
                    delay(250) // Allow UI to recompose and update layout info
                } finally {
                    isLoadingMore = false
                }
            }
        }
    }

    LaunchedEffect(tag, searchQuery, itemsPage, updatedItemsPageProvider, isOnline) {
        val provider = updatedItemsPageProvider
        if (itemsPage != null || provider == null) {
            initialLoadPending = false
            return@LaunchedEffect
        }
        initialLoadPending = true
        try {
            loadInitialItemsPage(provider)?.onSuccess {
                itemsPage = it
                loadedItemsState?.value = it.items.orEmpty()
            }
        } finally {
            initialLoadPending = false
        }
    }

    LaunchedEffect(itemsPage?.items?.isNotEmpty()) {
        if (itemsPage?.items?.isNotEmpty() == true && !hasScrolledToTop) {
            lazyListState.scrollToItem(0)
            hasScrolledToTop = true
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
    SearchEmptyFeedback(
        query = searchQuery.orEmpty(),
        empty = itemsPage != null && itemsPage?.continuation == null && visibleItems.isEmpty(),
        loading = forceShimmer || initialLoadPending || isLoadingMore,
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
                if (forceShimmer || (isOnline && initialLoadPending)) {
                    items(initialPlaceholderCount, key = { "loading_$it" }) {
                        itemPlaceholderContent(null)
                    }
                } else {
                    item(key = "loading_status") {
                        if (isOnline) {
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
                if (forceShimmer) itemPlaceholderContent(item) else itemContent(item)
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

            if (itemsPage?.continuation != null) {
                val count = if (visibleItems.isEmpty()) initialPlaceholderCount else continuationPlaceholderCount
                items(count, key = { "loading_more_$it" }) { itemPlaceholderContent(null) }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}

/** Chrome clearance is supplied by the page host and shared by loading and loaded content. */
@ExperimentalAnimationApi
@Composable
inline fun <T : Innertube.Item> ItemsGridPage(
    tag: String,
    crossinline headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    noinline itemContent: @Composable LazyGridItemScope.(T) -> Unit,
    noinline itemPlaceholderContent: @Composable (T?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = Dimensions.bottomSpacer),
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 6,
    emptyItemsText: String = "",
    filterContentType: ContentType = ContentType.All,
    noinline itemsPageProvider: (suspend (String?) -> Result<Innertube.ItemsPage<T>?>?)? = null,
    thumbnailSizeDp: Dp,
    loadedItemsState: androidx.compose.runtime.MutableState<List<T>>? = null,
    searchQuery: String? = null,
    noinline showEmptyMessage: (String) -> Unit = { Toaster.i(it) },
) {
    val updatedItemsPageProvider by rememberUpdatedState(itemsPageProvider)
    val isOnline by NetworkQualityHelper.isNetworkAvailableComposable(LocalContext.current)
    val forceShimmer = forceLoadingShimmerPreview()

    val lazyGridState = rememberLazyGridState()
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)

    var itemsPage by key(tag, searchQuery) { persist<Innertube.ItemsPage<T>?>(tag) }
    var hasScrolledToTop by remember { mutableStateOf(false) }

    var isLoadingMore by remember(tag, searchQuery) { mutableStateOf(false) }
    var initialLoadPending by remember(tag, searchQuery) { mutableStateOf(itemsPage == null && itemsPageProvider != null) }

    LaunchedEffect(tag, searchQuery, lazyGridState, updatedItemsPageProvider, forceShimmer) {
        if (forceShimmer) return@LaunchedEffect
        val currentItemsPageProvider = updatedItemsPageProvider ?: return@LaunchedEffect

        while (true) {
            val shouldLoad = snapshotFlow {
                val info = lazyGridState.layoutInfo
                val total = info.totalItemsCount
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                val hasContinuation = itemsPage?.continuation != null
                (hasContinuation && total > 0 && lastVisible >= total - 20) && !isLoadingMore
            }
                .distinctUntilChanged()
                .first { it }

            if (shouldLoad) {
                isLoadingMore = true
                try {
                    val currentContinuation = itemsPage?.continuation
                    withContext(NzikDispatchers.DATA) {
                        currentItemsPageProvider(currentContinuation)
                    }?.onSuccess { newPage ->
                        if (newPage == null) {
                            if (itemsPage == null) {
                                itemsPage = Innertube.ItemsPage(null, null)
                            }
                        } else {
                            val merged = withContext(NzikDispatchers.DATA) {
                                itemsPage + newPage
                            }
                            itemsPage = merged
                            loadedItemsState?.value = merged.items ?: emptyList()
                        }
                    }?.onFailure {
                        Timber.tag("ItemsPage").e(it, "Failed to load more items")
                        delay(2000) // Avoid rapid retry on failure
                    }
                    delay(250) // Allow UI to recompose and update layout info
                } finally {
                    isLoadingMore = false
                }
            }
        }
    }

    LaunchedEffect(tag, searchQuery, itemsPage, updatedItemsPageProvider, isOnline) {
        val provider = updatedItemsPageProvider
        if (itemsPage != null || provider == null) {
            initialLoadPending = false
            return@LaunchedEffect
        }
        initialLoadPending = true
        try {
            loadInitialItemsPage(provider)?.onSuccess {
                itemsPage = it
                loadedItemsState?.value = it.items.orEmpty()
            }
        } finally {
            initialLoadPending = false
        }
    }

    LaunchedEffect(itemsPage?.items?.isNotEmpty()) {
        if (itemsPage?.items?.isNotEmpty() == true && !hasScrolledToTop) {
            lazyGridState.scrollToItem(0)
            hasScrolledToTop = true
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
    SearchEmptyFeedback(
        query = searchQuery.orEmpty(),
        empty = itemsPage != null && itemsPage?.continuation == null && visibleItems.isEmpty(),
        loading = forceShimmer || initialLoadPending || isLoadingMore,
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
                if (forceShimmer || (isOnline && initialLoadPending)) {
                    items(initialPlaceholderCount, key = { "loading_$it" }) {
                        itemPlaceholderContent(null)
                    }
                } else {
                    item(key = "loading_status", span = { GridItemSpan(maxLineSpan) }) {
                        if (isOnline) {
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
                if (forceShimmer) itemPlaceholderContent(item) else itemContent(item)
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

            if (itemsPage?.continuation != null) {
                val count = if (visibleItems.isEmpty()) initialPlaceholderCount else continuationPlaceholderCount
                items(count, key = { "loading_more_$it" }) { itemPlaceholderContent(null) }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyGridState = lazyGridState)
    }
}




