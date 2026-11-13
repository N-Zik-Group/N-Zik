package app.n_zik.android.utils

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.searchPage
import app.n_zik.innertube.requests.searchPageContinuation
import app.n_zik.innertube.utils.from
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.SwipeablePlaylistItem
import app.n_zik.android.ui.components.themed.Title
import app.n_zik.android.ui.items.VideoItem
import app.n_zik.android.ui.items.VideoItemPlaceholder
import app.n_zik.android.components.menu.video.VideoItemMenu
import app.n_zik.android.ui.screens.searchresult.ItemsPage

import app.n_zik.android.core.utils.Toaster

@ExperimentalAnimationApi
@ExperimentalTextApi
@ExperimentalFoundationApi
@UnstableApi
@Composable
fun SearchYoutubeEntity (
    navController: NavController,
    onDismiss: () -> Unit,
    query: String,
    filter: Innertube.SearchFilter = Innertube.SearchFilter.Video,
    disableScrollingText: Boolean
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    //val context = LocalContext.current
    val thumbnailHeightDp = 72.dp
    val thumbnailWidthDp = 128.dp
    val emptyItemsText = stringResource(R.string.search_no_results_message)
    val headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit = {
        Title(
            title = stringResource(id = R.string.search_videos_title),
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
    val isVideoEnabled = LocalContext.current.preferences.getBoolean(showButtonPlayerVideoKey, false)

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .padding(WindowInsets.systemBars.asPaddingValues())
                .padding(horizontal = 16.dp)
        ) {
            ItemsPage(
                searchQuery = query,
                tag = "searchYTEntity/$query/videos",
                itemsPageProvider = { continuation ->
                    if (continuation == null) {
                        Innertube.searchPage(
                            query = query,
                            params = Innertube.SearchFilter.Video.value,
                            fromMusicShelfRendererContent = Innertube.VideoItem::from
                        )
                    } else {
                        Innertube.searchPageContinuation(
                            continuation = continuation,
                            fromMusicShelfRendererContent = Innertube.VideoItem::from
                        )
                    }
                },
                emptyItemsText = emptyItemsText,
                headerContent = headerContent,
                itemContent = { video ->
                    SwipeablePlaylistItem(
                        mediaItem = video.asMediaItem,
                        onPlayNext = {
                            binder?.player?.addNext(video.asMediaItem)
                        },
                        onDownload = {
                            Toaster.w( R.string.search_video_download_unsupported_message )
                        },
                        onEnqueue = {
                            binder?.player?.enqueue(video.asMediaItem)
                        }
                    ) {
                        VideoItem(
                            video = video,
                            thumbnailWidthDp = thumbnailWidthDp,
                            thumbnailHeightDp = thumbnailHeightDp,
                            modifier = Modifier
                                .background(colorPalette().background0)
                                .clip(uiRoundnessShape()).combinedClickable(
                                    onLongClick = {
                                        menuState.display {
                                            VideoItemMenu(
                                                navController = navController,
                                                song = video.asMediaItem.asSong
                                            ).MenuComponent()
                                        }
                                        hapticFeedback.performHapticFeedback(

                                            HapticFeedbackType.LongPress
                                        )
                                    },
                                    onClick = {
                                        binder?.stopRadio()
                                        if (isVideoEnabled)
                                            binder?.player?.playVideo(video.asMediaItem)
                                        else
                                            binder?.player?.forcePlay(video.asMediaItem)
                                        //binder?.setupRadio(video.info?.endpoint)
                                        onDismiss()
                                    }
                                ),
                            disableScrollingText = disableScrollingText
                        )
                    }
                },
                itemPlaceholderContent = {
                    VideoItemPlaceholder(
                        thumbnailHeightDp = thumbnailHeightDp,
                        thumbnailWidthDp = thumbnailWidthDp
                    )
                }
            )
        }
    }
}







