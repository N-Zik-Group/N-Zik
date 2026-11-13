package app.n_zik.android.themed.n_zik.screen.artist

import app.n_zik.android.ui.styling.Dimensions
import androidx.compose.foundation.layout.PaddingValues
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YtMusic
import app.n_zik.innertube.models.BrowseEndpoint
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.components.navigation.Skeleton
import app.n_zik.android.ui.components.SwipeablePlaylistItem
import app.n_zik.android.ui.components.themed.Title
import app.n_zik.android.ui.items.VideoItem
import app.n_zik.android.ui.items.VideoItemPlaceholder
import app.n_zik.android.components.menu.video.VideoItemMenu
import app.n_zik.android.ui.screens.searchresult.ItemsPage

import app.n_zik.android.utils.addNext
import app.n_zik.android.core.database.artistEntryNames
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.enqueue
import app.n_zik.android.utils.forcePlay
import app.n_zik.android.utils.playVideo
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.showButtonPlayerVideoKey
import app.n_zik.android.compose.persist.persist
import app.n_zik.android.MODIFIED_PREFIX

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@ExperimentalTextApi
@ExperimentalAnimationApi
@Composable
fun ArtistVideos(
    navController: NavController,
    browseId: String,
    params: String?,
    miniPlayer: @Composable () -> Unit
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    val isVideoEnabled = context.preferences.getBoolean(showButtonPlayerVideoKey, false)

    val thumbnailHeightDp = 72.dp
    val thumbnailWidthDp = 128.dp

    val itemsPage by persist<Innertube.ItemsPage<Innertube.Item>?>("artist/$browseId/videos")

    Skeleton(
        navController = navController,
        miniPlayer = miniPlayer,
        topInsetHandledByContent = true,
        navBarContent = {}
    ) {
        ItemsPage(
            contentPadding = rememberPageChromeContentPadding(PaddingValues(bottom = Dimensions.bottomSpacer)),
            tag = "artist/$browseId/videos",
            headerContent = {
                Title(
                    title = itemsPage?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.search_videos_title),
                    verticalPadding = 4.dp,
                )
            },
            itemContent = { item ->
                val video = item as? Innertube.VideoItem
                val song = item as? Innertube.SongItem

                if (video != null || song != null) {
                    val mediaItem = video?.asMediaItem ?: song?.asMediaItem!!
                    SwipeablePlaylistItem(
                        mediaItem = mediaItem,
                        onPlayNext = {
                            binder?.player?.addNext(mediaItem)
                        },
                        onDownload = {
                            // Downloading videos not supported
                        },
                        onEnqueue = {
                            binder?.player?.enqueue(mediaItem)
                        }
                    ) {
                        VideoItem(
                            thumbnailUrl = video?.thumbnail?.url ?: song?.thumbnail?.url,
                            duration = video?.durationText ?: song?.durationText,
                            title = item.title,
                            uploader = (video?.authors ?: song?.authors).artistEntryNames().joinToString(", "),
                            views = video?.viewsText,
                            thumbnailWidthDp = thumbnailWidthDp,
                            thumbnailHeightDp = thumbnailHeightDp,
                            modifier = Modifier
                                .background(colorPalette().background0)
                                .clip(uiRoundnessShape()).combinedClickable(
                                    onLongClick = {
                                        menuState.display {
                                            VideoItemMenu(
                                                navController = navController,
                                                song = mediaItem.asSong
                                            ).MenuComponent()
                                        }
                                        hapticFeedback.performHapticFeedback(

                                            HapticFeedbackType.LongPress
                                        )
                                    },
                                    onClick = {
                                        binder?.stopRadio()
                                        if (isVideoEnabled)
                                            binder?.player?.playVideo(mediaItem)
                                        else
                                            binder?.player?.forcePlay(mediaItem)
                                    }
                                ),
                            disableScrollingText = disableScrollingText
                        )
                    }
                }
            },
            itemPlaceholderContent = {
                VideoItemPlaceholder(
                    thumbnailHeightDp = thumbnailHeightDp,
                    thumbnailWidthDp = thumbnailWidthDp
                )
            },
            itemsPageProvider = { continuation ->
                if (continuation == null) {
                    YtMusic.getArtistItemsPage(BrowseEndpoint(browseId.removePrefix(MODIFIED_PREFIX), params)).map {
                        Innertube.ItemsPage(it.items, it.continuation, it.title)
                    }
                } else {
                    YtMusic.getArtistItemsContinuation(continuation).map { continuationPage ->
                        Innertube.ItemsPage(
                            continuationPage?.items,
                            continuationPage?.continuation
                        )
                    }
                }
            },
            emptyItemsText = stringResource(R.string.search_no_results_message)
        )
    }
}






