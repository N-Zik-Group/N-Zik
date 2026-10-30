@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package app.kreate.android.themed.rimusic.screen.artist

import app.n_zik.android.components.ui.screens.CollectionPageStatus
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.n_zik.android.R
import io.ktor.client.call.body
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.BrowseResponse
import it.fast4x.innertube.models.GridRenderer
import it.fast4x.innertube.utils.from
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.NavRoutes
import app.n_zik.android.components.navigation.Skeleton
import app.n_zik.android.components.navigation.Skeleton
import app.it.fast4x.rimusic.ui.components.themed.Loader

import app.it.fast4x.rimusic.ui.items.AlbumItem
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.px
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import app.kreate.android.me.knighthat.utils.Toaster
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.n_zik.android.components.AppPullToRefreshBox

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

    var isRefreshing by remember { mutableStateOf( false ) }
    val thumbnailSizeDp = Dimensions.thumbnails.album + 24.dp
    val thumbnailSizePx = thumbnailSizeDp.px

    val albums = remember { mutableStateListOf<Innertube.AlbumItem>() }
    suspend fun fetchAlbums() {
        val response = runCatching {
            Innertube.browse( browseId = browseId.removePrefix(MODIFIED_PREFIX), params = params ).body<BrowseResponse>()
        }

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
            onFailure = { Toaster.e( R.string.failed_to_fetch_album ) }
        )
    }
    LaunchedEffect( Unit ) {
        fetchAlbums()
        isRefreshing = false
    }

    Skeleton(
        navController = navController,
        miniPlayer = miniplayer,
        topInsetHandledByContent = true,
        navBarContent = {}
    ) {
        if (albums.isEmpty()) {
            CollectionPageStatus { Loader() }
        } else {
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
                    items(
                        items = albums.distinctBy(Innertube.AlbumItem::key),
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

}




