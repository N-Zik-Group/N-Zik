package app.it.fast4x.rimusic.ui.screens.mood

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.n_zik.android.R
import com.valentinilk.shimmer.shimmer
import app.it.fast4x.compose.persist.persist
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.BrowseResult
import it.fast4x.innertube.requests.browseCategory
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.models.Mood
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.ShimmerHost
import app.it.fast4x.rimusic.ui.components.themed.HeaderPlaceholder
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.components.themed.TextPlaceholder
import app.it.fast4x.rimusic.ui.items.AlbumItem
import app.it.fast4x.rimusic.ui.items.AlbumItemPlaceholder
import app.it.fast4x.rimusic.ui.items.ArtistItem
import app.it.fast4x.rimusic.ui.items.PlaylistItem
import app.it.fast4x.rimusic.ui.components.themed.Loader
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.px
import app.it.fast4x.rimusic.utils.center
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.n_zik.android.components.menu.artist.OnlineArtistItemMenu
import app.n_zik.android.components.menu.playlist.OnlinePlaylistItemMenu
import app.it.fast4x.rimusic.ui.items.VideoItem
import app.n_zik.android.components.SongItem
import app.it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import app.n_zik.android.components.menu.song.SongItemMenu
import app.n_zik.android.components.menu.video.VideoItemMenu
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.models.Song
import app.kreate.android.me.knighthat.utils.Toaster
import app.it.fast4x.rimusic.utils.showButtonPlayerVideoKey

import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.forcePlay
import app.it.fast4x.rimusic.utils.isDownloadedSong
import app.it.fast4x.rimusic.utils.manageDownload
import app.it.fast4x.rimusic.utils.playVideo

internal const val defaultBrowseId = "FEmusic_moods_and_genres_category"

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun MoodList(
    navController: NavController,
    mood: Mood
) {
    val windowInsets = LocalPlayerAwareWindowInsets.current

    val browseId = mood.browseId ?: defaultBrowseId
    var moodPage by persist<Result<BrowseResult>>("moods/$browseId${mood.params?.let { "/$it" } ?: ""}")

    LaunchedEffect(Unit) {
        moodPage = Innertube.browseCategory(browseId = browseId, params = mood.params)
    }

    val thumbnailSizeDp = Dimensions.thumbnails.album
    val thumbnailSizePx = thumbnailSizeDp.px

    val lazyListState = rememberLazyListState()

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)
        .padding(endPaddingValues)

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val isVideoEnabled = remember { context.preferences.getBoolean(showButtonPlayerVideoKey, false) }

    Column (
        modifier = Modifier
            .background(colorPalette().background0)
            //.fillMaxSize()
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        moodPage?.getOrNull()?.let { moodResult ->
            LazyColumn(
                state = lazyListState,
                //contentPadding = LocalPlayerAwareWindowInsets.current
                //    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxSize()
            ) {
                item(
                    key = "header",
                    contentType = 0
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HeaderWithIcon(
                            title = mood.name,
                            iconId = R.drawable.globe,
                            enabled = true,
                            showIcon = true,
                            modifier = Modifier,
                            onClick = {}
                        )
                    }
                }

                moodResult.items.forEach { item ->
                    item {
                        BasicText(
                            text = item.title,
                            style = typography().m.semiBold,
                            modifier = sectionTextModifier
                        )
                    }
                    item {
                        LazyRow {
                            items(items = item.items, key = { it.key }) { childItem ->
                                if (childItem.key == defaultBrowseId) return@items
                                when (childItem) {
                                    is Innertube.AlbumItem -> AlbumItem(
                                        album = childItem,
                                        thumbnailSizePx = thumbnailSizePx,
                                        thumbnailSizeDp = thumbnailSizeDp,
                                        alternative = true,
                                        modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                            onClick = {
                                                childItem.info?.endpoint?.browseId?.let {
                                                    navController.navigate(route = "${NavRoutes.album.name}/$it")
                                                }
                                            },
                                            onLongClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.display {
                                                    OnlineAlbumItemMenu(
                                                        navController = navController,
                                                        album = childItem
                                                    ).MenuComponent()
                                                }
                                            }
                                        ),
                                        disableScrollingText = disableScrollingText
                                    )

                                    is Innertube.ArtistItem -> ArtistItem(
                                        artist = childItem,
                                        thumbnailSizePx = thumbnailSizePx,
                                        thumbnailSizeDp = thumbnailSizeDp,
                                        alternative = true,
                                        modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                            onClick = {
                                                childItem.info?.endpoint?.browseId?.let {
                                                    navController.navigate(route = "${NavRoutes.artist.name}/$it")
                                                }
                                            },
                                            onLongClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.display {
                                                    OnlineArtistItemMenu(navController = navController, artist = childItem).MenuComponent()
                                                }
                                            }
                                        ),
                                        disableScrollingText = disableScrollingText
                                    )

                                    is Innertube.PlaylistItem -> PlaylistItem(
                                        playlist = childItem,
                                        thumbnailSizePx = thumbnailSizePx,
                                        thumbnailSizeDp = thumbnailSizeDp,
                                        alternative = true,
                                        modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                            onClick = {
                                                childItem.info?.endpoint?.let { endpoint ->
                                                    navController.navigate(route = "${NavRoutes.playlist.name}/${endpoint.browseId}")
                                                }
                                            },
                                            onLongClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.display {
                                                    OnlinePlaylistItemMenu(
                                                        navController = navController,
                                                        playlist = childItem
                                                    ).MenuComponent()
                                                }
                                            }
                                        ),
                                        disableScrollingText = disableScrollingText
                                    )

                                    is Innertube.SongItem -> {
                                        val isDownloaded = isDownloadedSong(childItem.asMediaItem.mediaId)
                                        SwipeablePlaylistItem(
                                            mediaItem = childItem.asMediaItem,
                                            onPlayNext = { binder?.player?.addNext(childItem.asMediaItem) },
                                            onDownload = {
                                                binder?.cache?.removeResource(childItem.asMediaItem.mediaId ?: "")
                                                Database.asyncTransaction {
                                                    Database.formatTable.updateContentLengthOf(childItem.key ?: "")
                                                }
                                                manageDownload(context, childItem.asMediaItem, isDownloaded)
                                            },
                                            onEnqueue = { binder?.player?.enqueue(childItem.asMediaItem) }
                                        ) {
                                            SongItem(
                                                song = childItem.asMediaItem.asSong ?: Song.makePlaceholder(""),
                                                navController = navController,
                                                modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                                    onClick = {
                                                        binder?.stopRadio()
                                                        if (isVideoEnabled) binder?.player?.playVideo(childItem.asMediaItem)
                                                        else binder?.player?.forcePlay(childItem.asMediaItem)
                                                    },
                                                    onLongClick = {
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        menuState.display {
                                                            SongItemMenu(
                                                                navController = navController,
                                                                song = childItem.asMediaItem.asSong ?: Song.makePlaceholder("")
                                                            ).MenuComponent()
                                                        }
                                                    }
                                                ),
                                                onClick = {
                                                    binder?.stopRadio()
                                                    if (isVideoEnabled) binder?.player?.playVideo(childItem.asMediaItem)
                                                    else binder?.player?.forcePlay(childItem.asMediaItem)
                                                }
                                            )
                                        }
                                    }

                                    is Innertube.VideoItem -> {
                                        SwipeablePlaylistItem(
                                            mediaItem = childItem.asMediaItem,
                                            onPlayNext = { binder?.player?.addNext(childItem.asMediaItem) },
                                            onDownload = { Toaster.w(R.string.downloading_videos_not_supported) },
                                            onEnqueue = { binder?.player?.enqueue(childItem.asMediaItem) }
                                        ) {
                                            VideoItem(
                                                video = childItem,
                                                thumbnailWidthDp = thumbnailSizeDp,
                                                thumbnailHeightDp = thumbnailSizeDp,
                                                modifier = Modifier
                                                    .background(colorPalette().background0)
                                                    .clip(uiRoundnessShape()).combinedClickable(
                                                        onClick = {
                                                            binder?.stopRadio()
                                                            if (isVideoEnabled) binder?.player?.playVideo(childItem.asMediaItem)
                                                            else binder?.player?.forcePlay(childItem.asMediaItem)
                                                        },
                                                        onLongClick = {
                                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            menuState.display {
                                                                VideoItemMenu(
                                                                    navController = navController,
                                                                    song = childItem.asMediaItem.asSong ?: Song.makePlaceholder("")
                                                                ).MenuComponent()
                                                            }
                                                        }
                                                    ),
                                                disableScrollingText = disableScrollingText
                                            )
                                        }
                                    }

                                    else -> {}
                                }
                            }
                        }
                    }
                }

                item(key = "bottom") {
                    Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                }

            }
        } ?: moodPage?.exceptionOrNull()?.let {
            BasicText(
                text = stringResource(R.string.page_not_been_loaded),
                style = typography().s.secondary.center,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(all = 16.dp)
            )
        } ?: Loader(
            modifier = Modifier
                .fillMaxSize()
                .padding(windowInsets.asPaddingValues())
        )
    }
}








