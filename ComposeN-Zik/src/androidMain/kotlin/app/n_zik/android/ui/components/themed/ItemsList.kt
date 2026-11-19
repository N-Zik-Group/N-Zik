package app.n_zik.android.ui.components.themed

import app.n_zik.android.components.search.SearchNoResults
import app.n_zik.android.LocalPlayerAwareWindowInsets

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.compose.persist.persist
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.utils.plus
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.utils.center
import app.n_zik.android.utils.secondary
import app.n_zik.android.R
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext
import app.n_zik.android.typography
import timber.log.Timber

@ExperimentalAnimationApi
@Composable
inline fun <T : Innertube.Item> ItemsList(
    tag: String,
    crossinline headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    crossinline itemContent: @Composable LazyItemScope.(T) -> Unit,
    noinline itemPlaceholderContent: @Composable (Int) -> Unit,
    modifier: Modifier = Modifier,
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 3,
    emptyItemsText: String = "",
    noinline itemsPageProvider: (suspend (String?) -> Result<Innertube.ItemsPage<T>?>?)? = null,
    knownItems: List<T>? = null,
) {
    val updatedItemsPageProvider by rememberUpdatedState(itemsPageProvider)
    val forceShimmer = forceLoadingShimmerPreview()

    val lazyListState = rememberLazyListState()

    var itemsPage by persist<Innertube.ItemsPage<T>?>(tag)

    LaunchedEffect(lazyListState, updatedItemsPageProvider, forceShimmer, knownItems) {
        if (forceShimmer || knownItems != null) return@LaunchedEffect
        val currentItemsPageProvider = updatedItemsPageProvider ?: return@LaunchedEffect

        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" } }
            .collect { shouldLoadMore ->
                if (!shouldLoadMore) return@collect

                withContext(NzikDispatchers.DATA) {
                    currentItemsPageProvider(itemsPage?.continuation)
                }?.onSuccess {
                    if (it == null) {
                        if (itemsPage == null) {
                            itemsPage = Innertube.ItemsPage(null, null)
                        }
                    } else {
                        itemsPage += it
                    }
                }?.onFailure { e -> Timber.tag("ItemsList").e(e, "Failed to load more items") }
            }
    }

        LazyRow(
            state = lazyListState,
            //contentPadding = LocalPlayerAwareWindowInsets.current
            //    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
            modifier = modifier
                //.fillMaxSize()
        ) {
            /*
            item(
                key = "header",
                contentType = "header",
            ) {
                headerContent(null)
            }

             */

            val loadedItems = knownItems ?: itemsPage?.items.orEmpty()
            itemsIndexed(loadedItems, key = { _, item -> item.key }) { index, item ->
                if (forceShimmer) itemPlaceholderContent(index)
                else itemContent(item)
            }

            if (!forceShimmer && (knownItems != null || itemsPage != null) && loadedItems.isEmpty()) {
                item(key = "empty") {
                    SearchNoResults(
                        modifier = Modifier.fillParentMaxSize(),
                        text = emptyItemsText.ifEmpty { stringResource(R.string.search_items_empty_message) },
                    )
                }
            }

            val showInitialPlaceholders = loadedItems.isEmpty() && (forceShimmer || (knownItems == null && itemsPage == null))
            val showContinuation = !forceShimmer && knownItems == null && itemsPage?.continuation != null
            if (showInitialPlaceholders || showContinuation) {
                val initial = loadedItems.isEmpty()
                val count = if (initial) initialPlaceholderCount else continuationPlaceholderCount
                items(count, key = { index -> if (index == 0) "loading" else "loading_$index" }) { index ->
                    itemPlaceholderContent(index)
                }
            }

        }

        //FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)


}




