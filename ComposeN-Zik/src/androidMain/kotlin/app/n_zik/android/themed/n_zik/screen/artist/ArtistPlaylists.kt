@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package app.n_zik.android.themed.n_zik.screen.artist

import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import androidx.annotation.OptIn
import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.BrowseResponse
import app.n_zik.innertube.requests.ArtistItemsPage
import app.n_zik.android.colorPalette
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.components.navigation.Skeleton
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.ui.items.PlaylistItem
import app.n_zik.android.ui.items.PlaylistItemPlaceholder
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.px
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import app.n_zik.android.core.utils.Toaster
import io.ktor.client.statement.bodyAsText
import timber.log.Timber
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.components.AppPullToRefreshBox
import app.n_zik.android.cleanPrefix
import app.n_zik.android.typography
import app.n_zik.android.utils.secondary
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

@OptIn(UnstableApi::class)
@ExperimentalMaterial3Api
@Composable
fun ArtistPlaylists(
    navController: NavController,
    browseId: String,
    params: String?,
    miniPlayer: @Composable () -> Unit
) {
    val lazyGridState = rememberLazyGridState()

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val thumbnailSizeDp = Dimensions.thumbnails.album + 24.dp
    val thumbnailSizePx = thumbnailSizeDp.px

    val playlists = remember { mutableStateListOf<Innertube.PlaylistItem>() }

    suspend fun fetchPlaylists() {
        Timber.tag("ArtistPlaylists").d("fetching browseId=$browseId params=${params?.take(20)}..")
        val result = runCatching {
            var currentBrowseId = browseId.removePrefix(MODIFIED_PREFIX)
            var currentParams = params.takeIf { !it.isNullOrBlank() }
            
            var responseText = Innertube.browse(
                browseId = currentBrowseId,
                params = currentParams
            ).bodyAsText()
            
            val jsonParser = Json { ignoreUnknownKeys = true; explicitNulls = false }
            var response = jsonParser.decodeFromString<BrowseResponse>(responseText)
            
            var contents = response.contents
            var tabs = contents?.singleColumnBrowseResultsRenderer?.tabs.orEmpty()
            
            var sectionContent = tabs.mapNotNull { tab ->
                tab.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            }.firstOrNull()
                ?: contents?.sectionListRenderer?.contents?.firstOrNull()

            if (sectionContent == null) {
                // Some queries (like specific artist playlists) return a tab with an endpoint instead of inline content
                val tabEndpoint = tabs.mapNotNull { it.tabRenderer?.endpoint?.browseEndpoint }.firstOrNull()
                if (tabEndpoint != null) {
                    Timber.tag("ArtistPlaylists").d("following tab endpoint params=${tabEndpoint.params}")
                    val originalParams = currentParams
                    currentBrowseId = tabEndpoint.browseId ?: currentBrowseId
                    currentParams = tabEndpoint.params
                    
                    responseText = Innertube.browse(
                        browseId = currentBrowseId,
                        params = currentParams
                    ).bodyAsText()
                    
                    response = jsonParser.decodeFromString<BrowseResponse>(responseText)
                    contents = response.contents
                    tabs = contents?.singleColumnBrowseResultsRenderer?.tabs.orEmpty()
                    
                    sectionContent = tabs.mapNotNull { tab ->
                        val listContents = tab.tabRenderer?.content?.sectionListRenderer?.contents
                        listContents?.firstOrNull { content ->
                            val moreParams = content.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.params
                            moreParams != null && originalParams != null && (
                                moreParams == originalParams ||
                                (moreParams.length > 50 && originalParams.length > 50 && moreParams.takeLast(50) == originalParams.takeLast(50))
                            )
                        } ?: listContents?.firstOrNull()
                    }.firstOrNull()
                        ?: contents?.sectionListRenderer?.contents?.firstOrNull { content ->
                            val moreParams = content.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.params
                            moreParams != null && originalParams != null && (
                                moreParams == originalParams ||
                                (moreParams.length > 50 && originalParams.length > 50 && moreParams.takeLast(50) == originalParams.takeLast(50))
                            )
                        }
                        ?: contents?.sectionListRenderer?.contents?.firstOrNull()
                }
            }
            
            sectionContent // return the final sectionContent
        }

        loadFailed = result.isFailure
        result.fold(
            onSuccess = { sectionContent ->
                if (sectionContent == null) {
                    Timber.tag("ArtistPlaylists").d("no section content found in any path!")
                    return@fold
                }

                // Local vals required: Kotlin can't smart-cast public API properties from other modules
                val gridRenderer = sectionContent.gridRenderer
                val carouselRenderer = sectionContent.musicCarouselShelfRenderer
                val shelfRenderer = sectionContent.musicShelfRenderer
                val playlistShelfRenderer = sectionContent.musicPlaylistShelfRenderer

                val fetched = when {
                    gridRenderer != null -> {
                        (gridRenderer.items ?: emptyList())
                            .mapNotNull { it.musicTwoRowItemRenderer }
                            .mapNotNull { ArtistItemsPage.fromMusicTwoRowItemRenderer(it) as? Innertube.PlaylistItem }
                    }

                    carouselRenderer != null -> {
                        carouselRenderer.contents
                            .mapNotNull { it.musicTwoRowItemRenderer }
                            .mapNotNull { ArtistItemsPage.fromMusicTwoRowItemRenderer(it) as? Innertube.PlaylistItem }
                            .filter { it.info?.endpoint?.browseId != null }
                    }

                    shelfRenderer != null -> {
                        shelfRenderer.contents
                            ?.mapNotNull { it.musicResponsiveListItemRenderer }
                            ?.mapNotNull { renderer ->
                                val endpoint = renderer.navigationEndpoint?.browseEndpoint
                                if (endpoint?.browseId == null) return@mapNotNull null
                                Innertube.PlaylistItem(
                                    info = Innertube.Info(
                                        renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text,
                                        endpoint
                                    ),
                                    songCount = null,
                                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull(),
                                    channel = null,
                                    isEditable = false
                                )
                            }
                            ?: emptyList()
                    }

                    playlistShelfRenderer != null -> {
                        playlistShelfRenderer.contents
                            ?.mapNotNull { it.musicResponsiveListItemRenderer }
                            ?.mapNotNull { renderer ->
                                val endpoint = renderer.navigationEndpoint?.browseEndpoint
                                if (endpoint?.browseId == null) return@mapNotNull null
                                Innertube.PlaylistItem(
                                    info = Innertube.Info(
                                        renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text,
                                        endpoint
                                    ),
                                    songCount = null,
                                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull(),
                                    channel = null,
                                    isEditable = false
                                )
                            }
                            ?: emptyList()
                    }

                    else -> {
                        Timber.tag("ArtistPlaylists").d("no known renderer found in sectionContent")
                        emptyList()
                    }
                }

                Timber.tag("ArtistPlaylists").d("total fetched=${fetched.size}")
                val existing = playlists.toSet()
                playlists.addAll(fetched.filterNot { it in existing })
            },
            onFailure = {
                if (it is CancellationException) throw it
                Timber.tag("ArtistPlaylists").e("error ${it.message}")
                Toaster.e(R.string.common_error_unknown)
            }
        )
        isLoading = false
    }

    LaunchedEffect(Unit) {
        fetchPlaylists()
        isRefreshing = false
    }

    val forceShimmer = forceLoadingShimmerPreview()
    Skeleton(
        navController = navController,
        miniPlayer = miniPlayer,
        topInsetHandledByContent = true,
        navBarContent = {}
    ) {
            AppPullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                        fetchPlaylists()
                        isRefreshing = false
                    }
                }
            ) {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive(thumbnailSizeDp),
                    contentPadding = rememberPageChromeContentPadding(PaddingValues(bottom = Dimensions.bottomSpacer)),
                    modifier = Modifier.background(colorPalette().background0)
                ) {
                    val visiblePlaylists = playlists.distinctBy(Innertube.PlaylistItem::key)
                    if (isLoading && visiblePlaylists.isEmpty()) {
                        items(8, key = { "artist-playlist-loading-$it" }) {
                            PlaylistItemPlaceholder(
                                thumbnailSizeDp,
                                alternative = true,
                                showChannel = false,
                            )
                        }
                    } else if (visiblePlaylists.isEmpty()) {
                        item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                            BasicText(
                                text = stringResource(if (loadFailed) R.string.common_load_failed else R.string.common_empty_items),
                                style = typography().s.secondary,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else items(
                        items = visiblePlaylists,
                        key = Innertube.PlaylistItem::key
                    ) { playlist ->
                        if (isLoading || forceShimmer) {
                            val channel = playlist.channel?.name
                            PlaylistItemPlaceholder(
                                thumbnailSizeDp,
                                alternative = true,
                                showChannel = channel != null,
                                title = playlist.title?.let(::cleanPrefix),
                                channel = channel,
                            )
                        } else {
                            PlaylistItem(
                                playlist = playlist,
                                thumbnailSizePx = thumbnailSizePx,
                                thumbnailSizeDp = thumbnailSizeDp,
                                alternative = true,
                                disableScrollingText = disableScrollingText,
                                modifier = Modifier.clip(uiRoundnessShape()).clickable {
                                    NavRoutes.playlist.navigateHere(navController, playlist.key)
                                }
                            )
                        }
                    }
                }
            }
    }
}
