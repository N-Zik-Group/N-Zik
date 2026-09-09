package app.it.fast4x.rimusic.ui.screens.searchresult
import androidx.compose.animation.ExperimentalAnimationApi
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment

import androidx.compose.ui.unit.dp
import app.it.fast4x.compose.persist.persist
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.utils.plus
import app.it.fast4x.rimusic.enums.ContentType
import app.it.fast4x.rimusic.ui.components.ShimmerHost
import app.it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.center
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.it.fast4x.rimusic.ui.items.AlbumPlaceholder
import app.it.fast4x.rimusic.ui.items.SongItemPlaceholder
import app.it.fast4x.rimusic.ui.components.themed.Loader
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import kotlinx.coroutines.delay
import timber.log.Timber
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.ui.unit.Dp


@ExperimentalAnimationApi
@Composable
inline fun <T : Innertube.Item> ItemsPage(
    tag: String,
    crossinline headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    crossinline itemContent: @Composable LazyItemScope.(T) -> Unit,
    noinline itemPlaceholderContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 3,
    emptyItemsText: String = "",
    filterContentType: ContentType = ContentType.All,
    noinline itemsPageProvider: (suspend (String?) -> Result<Innertube.ItemsPage<T>?>?)? = null,
    loadedItemsState: androidx.compose.runtime.MutableState<List<T>>? = null,
) {
    val updatedItemsPageProvider by rememberUpdatedState(itemsPageProvider)

    val lazyListState = rememberLazyListState()
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)

    var itemsPage by persist<Innertube.ItemsPage<T>?>(tag)
    var hasScrolledToTop by remember { mutableStateOf(false) }
    var isInitialLoad by remember { mutableStateOf(true) }

    var isLoadingMore by remember { mutableStateOf(false) }

    LaunchedEffect(lazyListState, updatedItemsPageProvider) {
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
                val currentContinuation = itemsPage?.continuation
                withContext(Dispatchers.IO) {
                    currentItemsPageProvider(currentContinuation)
                }?.onSuccess { newPage ->
                    if (newPage == null) {
                        if (itemsPage == null) {
                            itemsPage = Innertube.ItemsPage(null, null)
                        }
                    } else {
                        val merged = withContext(Dispatchers.IO) {
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
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(itemsPage, updatedItemsPageProvider) {
        if (itemsPage == null && updatedItemsPageProvider != null) {
            withContext(Dispatchers.IO) {
                updatedItemsPageProvider?.invoke(null)
            }?.onSuccess {
                if (it == null) {
                    itemsPage = Innertube.ItemsPage(null, null)
                } else {
                    itemsPage = it
                    loadedItemsState?.value = it.items ?: emptyList()
                }
            }?.onFailure { e -> Timber.tag("ItemsPage").e(e, "Failed to load initial items") }
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

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        if (itemsPage == null) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                headerContent(null)
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Loader()
                }
            }
        } else {
            LazyColumn(
                state = lazyListState,
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

                items(
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
                    }?.distinctBy { item -> "${item::class.simpleName}_${item.key.ifEmpty { System.identityHashCode(item) }}" } ?: emptyList(),
                    key = { item -> "${item::class.simpleName}_${item.key.ifEmpty { System.identityHashCode(item) }}" },
                    itemContent = itemContent
                )

                if (itemsPage != null && itemsPage?.items.isNullOrEmpty()) {
                    item {
                        BasicText(
                            text = emptyItemsText.ifEmpty { stringResource(R.string.no_items_found) },
                            style = typography().xs.secondary.center,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 32.dp)
                                .fillMaxWidth()
                        )
                    }
                }

                if (!(itemsPage != null && itemsPage?.continuation == null)) {
                    item {
                        val isFirstLoad = itemsPage?.items.isNullOrEmpty()
                        Column {
                            repeat(if (isFirstLoad) initialPlaceholderCount else continuationPlaceholderCount) {
                                itemPlaceholderContent()
                            }
                        }
                    }
                }

                item(
                    contentType = 0,
                ) {
                    Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}


@ExperimentalAnimationApi
@Composable
inline fun <T : Innertube.Item> ItemsGridPage(
    tag: String,
    crossinline headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    noinline itemContent: @Composable LazyGridItemScope.(T) -> Unit,
    noinline itemPlaceholderContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 6,
    emptyItemsText: String = "",
    filterContentType: ContentType = ContentType.All,
    noinline itemsPageProvider: (suspend (String?) -> Result<Innertube.ItemsPage<T>?>?)? = null,
    thumbnailSizeDp: Dp,
    loadedItemsState: androidx.compose.runtime.MutableState<List<T>>? = null,
) {
    val updatedItemsPageProvider by rememberUpdatedState(itemsPageProvider)
    val lazyGridState = rememberLazyGridState()
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
    var itemsPage by persist<Innertube.ItemsPage<T>?>(tag)
    var hasScrolledToTop by remember { mutableStateOf(false) }

    var isLoadingMore by remember { mutableStateOf(false) }

    LaunchedEffect(lazyGridState, updatedItemsPageProvider) {
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
                val currentContinuation = itemsPage?.continuation
                withContext(Dispatchers.IO) {
                    currentItemsPageProvider(currentContinuation)
                }?.onSuccess { newPage ->
                    if (newPage == null) {
                        if (itemsPage == null) {
                            itemsPage = Innertube.ItemsPage(null, null)
                        }
                    } else {
                        val merged = withContext(Dispatchers.IO) {
                            itemsPage + newPage
                        }
                        itemsPage = merged
                        loadedItemsState?.value = merged.items ?: emptyList()
                    }
                }?.onFailure {
                    Timber.tag("ItemsPage").e(it, "Failed to load more items")
                    delay(2000)
                }
                delay(250) // Allow UI to recompose and update layout info
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(itemsPage, updatedItemsPageProvider) {
        if (itemsPage == null && updatedItemsPageProvider != null) {
            withContext(Dispatchers.IO) {
                updatedItemsPageProvider?.invoke(null)
            }?.onSuccess {
                if (it == null) {
                    itemsPage = Innertube.ItemsPage(null, null)
                } else {
                    itemsPage = it
                    loadedItemsState?.value = it.items ?: emptyList()
                }
            }?.onFailure { e -> Timber.tag("ItemsPage").e(e, "Failed to load initial items") }
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

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        if (itemsPage == null) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                headerContent(null)
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Loader()
                }
            }
        } else {
            LazyVerticalGrid(
                state = lazyGridState,
                columns = GridCells.Adaptive(thumbnailSizeDp),
                contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer),
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

                items(
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
                    }?.distinctBy { item -> "${item::class.simpleName}_${item.key.ifEmpty { System.identityHashCode(item) }}" } ?: emptyList(),
                    key = { item -> "${item::class.simpleName}_${item.key.ifEmpty { System.identityHashCode(item) }}" },
                    itemContent = itemContent
                )

                if (itemsPage != null && itemsPage?.items.isNullOrEmpty()) {
                    item(
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        BasicText(
                            text = emptyItemsText.ifEmpty { stringResource(R.string.no_items_found) },
                            style = typography().xs.secondary.center,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 32.dp)
                                .fillMaxWidth()
                        )
                    }
                }

                if (!(itemsPage != null && itemsPage?.continuation == null)) {
                    item(
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        val isFirstLoad = itemsPage?.items.isNullOrEmpty()
                        Column {
                            repeat(if (isFirstLoad) initialPlaceholderCount else continuationPlaceholderCount) {
                                itemPlaceholderContent()
                            }
                        }
                    }
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyGridState = lazyGridState)
    }
}




