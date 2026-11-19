@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package app.n_zik.android.themed.n_zik.screen.artist

import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.n_zik.android.R
import io.ktor.client.call.body
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.BrowseResponse
import app.n_zik.innertube.models.GridRenderer
import app.n_zik.innertube.utils.from
import app.n_zik.android.colorPalette
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.components.navigation.Skeleton

import app.n_zik.android.ui.items.AlbumItem
import app.n_zik.android.ui.items.albumGridPlaceholders
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.px
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import app.n_zik.android.core.utils.Toaster
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.components.AppPullToRefreshBox
import app.n_zik.android.typography
import app.n_zik.android.utils.secondary
import kotlinx.coroutines.CancellationException

@ExperimentalMaterial3Api
@Composable
fun ArtistAlbums(
    navController: NavController,
    browseId: String,
    params: String?,
    miniplayer: @Composable () -> Unit
) {
    val lazyGridState = rememberLazyGridState()
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val thumbnailSizeDp = Dimensions.thumbnails.album + 24.dp
    val thumbnailSizePx = thumbnailSizeDp.px

    val albums = remember { mutableStateListOf<Innertube.AlbumItem>() }
    suspend fun fetchAlbums() {
        val response = runCatching {
            Innertube.browse( browseId = browseId.removePrefix(MODIFIED_PREFIX), params = params ).body<BrowseResponse>()
        }

        loadFailed = response.isFailure
        response.fold(
            onSuccess = { browseResponse ->
                browseResponse.contents
                              ?.singleColumnBrowseResultsRenderer
                              ?.tabs
                              ?.firstOrNull()
                              ?.tabRenderer
                              ?.content
                              ?.sectionListRenderer
                              ?.contents
                              ?.firstOrNull()
                              ?.gridRenderer
                              ?.items
                              ?.mapNotNull( GridRenderer.Item::musicTwoRowItemRenderer )
                              ?.mapNotNull( Innertube.AlbumItem::from )
                              ?.also {  albumItem ->
                                  val listingAlbums = albums.toSet()
                                  albums.addAll( albumItem.filterNot { it in listingAlbums } )
                              }
            },
            onFailure = {
                if (it is CancellationException) throw it
                Toaster.e(R.string.album_lookup_failed)
            }
        )
        isLoading = false
    }
    LaunchedEffect( Unit ) {
        fetchAlbums()
        isRefreshing = false
    }

    val forceShimmer = forceLoadingShimmerPreview()
    Skeleton(
        navController = navController,
        miniPlayer = miniplayer,
        topInsetHandledByContent = true,
        navBarContent = {}
    ) {
            AppPullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                        fetchAlbums()
                        isRefreshing = false
                    }
                }
            ) {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive(Dimensions.thumbnails.album + 24.dp),
                    contentPadding = rememberPageChromeContentPadding(PaddingValues(bottom = Dimensions.bottomSpacer)),
                    modifier = Modifier.background(colorPalette().background0)
                ) {
                    val visibleAlbums = albums.distinctBy(Innertube.AlbumItem::key)
                    if (isLoading || forceShimmer && visibleAlbums.isNotEmpty()) {
                        albumGridPlaceholders(visibleAlbums.takeUnless { isLoading }, thumbnailSizeDp)
                    } else if (visibleAlbums.isEmpty()) {
                        item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                            BasicText(
                                text = stringResource(if (loadFailed) R.string.common_load_failed else R.string.common_empty_items),
                                style = typography().s.secondary,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else items(
                        items = visibleAlbums,
                        key = Innertube.AlbumItem::key
                    ) {
                        AlbumItem(
                            album = it,
                            thumbnailSizePx = thumbnailSizePx,
                            thumbnailSizeDp = thumbnailSizeDp,
                            alternative = true,
                            modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                onClick = {
                                    NavRoutes.album.navigateHere(navController, it.key)
                                },
                                onLongClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.display {
                                        OnlineAlbumItemMenu(
                                            navController = navController,
                                            album = it
                                        ).MenuComponent()
                                    }
                                }
                            ),
                            disableScrollingText = disableScrollingText
                        )
                    }
                }
            }
    }

}




