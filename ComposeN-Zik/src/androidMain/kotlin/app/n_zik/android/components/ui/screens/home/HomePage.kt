@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package app.n_zik.android.components.ui.screens.home

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.it.fast4x.compose.persist.persist
import app.it.fast4x.rimusic.enums.Countries
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.components.themed.Loader
import app.it.fast4x.rimusic.ui.components.themed.MultiFloatingActionsContainer
import app.it.fast4x.rimusic.ui.items.AlbumItem
import app.it.fast4x.rimusic.ui.items.ArtistItem
import app.it.fast4x.rimusic.ui.items.PlaylistItem
import app.it.fast4x.rimusic.ui.items.VideoItem
import app.n_zik.android.components.SongItem
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.px
import app.it.fast4x.rimusic.utils.*
import app.n_zik.android.R
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.n_zik.android.components.menu.artist.OnlineArtistItemMenu
import app.n_zik.android.components.menu.playlist.OnlinePlaylistItemMenu
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.requests.HomePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import app.n_zik.android.components.AppPullToRefreshBox

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun HomePage(
    navController: NavController,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onMoodClick: (mood: Innertube.Mood.Item) -> Unit,
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    var homePageResult by persist<Result<HomePage?>>("home/home/pageResult")

    val refreshScope = rememberCoroutineScope()
    var loadedData by rememberPreference(loadedDataKey, false)

    suspend fun loadData() {
        homePageResult = YtMusic.getHomePage()
        if (loadedData) return

        runCatching {
            refreshScope.launch(Dispatchers.IO) {}
        }.onFailure {
            Timber.tag("HomePage").e("Failed loadData ${it.stackTraceToString()}")
            loadedData = false
        }.onSuccess {
            Timber.tag("HomePage").d("Success loadData")
            loadedData = true
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    var refreshing by remember { mutableStateOf(false) }

    fun refresh() {
        if (refreshing) return
        loadedData = false
        refreshScope.launch(Dispatchers.IO) {
            refreshing = true
            loadData()
            delay(500)
            refreshing = false
        }
    }

    val songThumbnailSizeDp = Dimensions.thumbnails.song
    val songThumbnailSizePx = songThumbnailSizeDp.px
    val albumThumbnailSizeDp = 108.dp
    val albumThumbnailSizePx = albumThumbnailSizeDp.px
    val artistThumbnailSizeDp = 92.dp
    val artistThumbnailSizePx = artistThumbnailSizeDp.px
    val playlistThumbnailSizeDp = 108.dp
    val playlistThumbnailSizePx = playlistThumbnailSizeDp.px

    val scrollState = rememberScrollState()
    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)
        .padding(endPaddingValues)

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    val showSearchTab by rememberPreference(showSearchTabKey, false)

    var showLoader by remember { mutableStateOf(!loadedData) }
    LaunchedEffect(loadedData) {
        if (loadedData) {
            delay(600)
            showLoader = false
        } else {
            showLoader = true
        }
    }

    AppPullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = ::refresh
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
            ) {
                if (UiType.ViMusic.isCurrent())
                    HeaderWithIcon(
                        title = stringResource(R.string.txt_home),
                        iconId = R.drawable.search,
                        enabled = true,
                        showIcon = !showSearchTab,
                        modifier = Modifier,
                        onClick = onSearchClick
                    )

                if (showLoader) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(this@BoxWithConstraints.maxHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Loader()
                    }
                } else {
                    WelcomeMessage()
                    homePageResult?.getOrNull()?.sections?.forEach {
                        BasicText(
                            text = it.title,
                            style = typography().l.semiBold,
                            modifier = sectionTextModifier
                        )
                        LazyRow(contentPadding = endPaddingValues) {
                            items(it.items) { item ->
                                when (item) {
                                    is Innertube.SongItem -> {
                                        SongItem(
                                            song = item.asSong,
                                            modifier = Modifier.clip(uiRoundnessShape()),
                                            onClick = {
                                                binder?.player?.forcePlay(item.asMediaItem)
                                            }
                                        )
                                    }
                                    is Innertube.AlbumItem -> {
                                        AlbumItem(
                                            album = item,
                                            alternative = true,
                                            thumbnailSizePx = albumThumbnailSizePx,
                                            thumbnailSizeDp = albumThumbnailSizeDp,
                                            disableScrollingText = disableScrollingText,
                                            modifier = Modifier.clip(uiRoundnessShape()).clickable(onClick = {
                                                navController.navigate("${NavRoutes.album.name}/${item.key}")
                                            })
                                        )
                                    }
                                    is Innertube.ArtistItem -> {
                                        ArtistItem(
                                            artist = item,
                                            thumbnailSizePx = artistThumbnailSizePx,
                                            thumbnailSizeDp = artistThumbnailSizeDp,
                                            disableScrollingText = disableScrollingText,
                                            modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                                onClick = {
                                                    navController.navigate("${NavRoutes.artist.name}/${item.key}")
                                                },
                                                onLongClick = {
                                                    menuState.display { OnlineArtistItemMenu(navController = navController, artist = item).MenuComponent() }
                                                }
                                            )
                                        )
                                    }
                                    is Innertube.PlaylistItem -> {
                                        PlaylistItem(
                                            playlist = item,
                                            alternative = true,
                                            thumbnailSizePx = playlistThumbnailSizePx,
                                            thumbnailSizeDp = playlistThumbnailSizeDp,
                                            disableScrollingText = disableScrollingText,
                                            modifier = Modifier.clip(uiRoundnessShape()).clickable(onClick = {
                                                navController.navigate("${NavRoutes.playlist.name}/${item.key}")
                                            })
                                        )
                                    }
                                    is Innertube.VideoItem -> {
                                        VideoItem(
                                            video = item,
                                            thumbnailHeightDp = playlistThumbnailSizeDp,
                                            thumbnailWidthDp = playlistThumbnailSizeDp,
                                            disableScrollingText = disableScrollingText
                                        )
                                    }
                                    null -> {}
                                }
                            }
                        }
                    }
                }
            }

            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
            if (showFloatingIcon)
                MultiFloatingActionsContainer(
                    iconId = R.drawable.search,
                    onClick = onSearchClick,
                    onClickSettings = onSettingsClick,
                    onClickSearch = onSearchClick
                )
        }
    }
}
