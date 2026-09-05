package app.it.fast4x.rimusic.ui.screens.podcast

import app.it.fast4x.rimusic.utils.asSong
import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import app.n_zik.android.LocalPlayerAwareWindowInsets

import app.n_zik.android.core.database.*

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.resize

import app.n_zik.android.colorPalette
import it.fast4x.innertube.Innertube
import app.it.fast4x.compose.persist.persist
import it.fast4x.innertube.requests.podcastPage
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.models.Playlist
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import app.it.fast4x.rimusic.ui.components.themed.AutoResizeText
import app.it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.it.fast4x.rimusic.ui.components.themed.FontSizeRange
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.n_zik.android.components.dialog.playlist.ImportPlaylistDialog
import app.it.fast4x.rimusic.ui.components.themed.LayoutWithAdaptiveThumbnail
import app.it.fast4x.rimusic.ui.components.themed.PlaylistsItemMenu
import app.it.fast4x.rimusic.ui.components.themed.adaptiveThumbnailContent
import app.it.fast4x.rimusic.ui.components.themed.Loader
import app.it.fast4x.rimusic.ui.items.SongItem
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.ui.styling.px
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.addToYtPlaylist
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.durationTextToMillis
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.fadingEdge
import app.it.fast4x.rimusic.utils.forcePlayAtIndex
import app.it.fast4x.rimusic.utils.forcePlayFromBeginning
import app.it.fast4x.rimusic.utils.formatAsTime
import app.it.fast4x.rimusic.utils.getDownloadState
import app.it.fast4x.rimusic.utils.isDownloadedSong
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.isNowPlaying
import app.it.fast4x.rimusic.utils.manageDownload
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.showFloatingIconKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.it.fast4x.rimusic.utils.ExternalUris
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.playback.utils.Shuffler
import app.n_zik.android.components.menu.song.SongItemMenu
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.n_zik.android.thumbnailShape


@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun Podcast(
    navController: NavController,
    browseId: String,
    params: String?,
    maxDepth: Int?,
) {
    val binder = LocalPlayerServiceBinder.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current

    var podcastPage by persist<Innertube.Podcast?>("podcast/$browseId/listEpisodes")

    var filter: String? by rememberSaveable { mutableStateOf(null) }
    var showConfirmDownloadAllDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteDownloadDialog by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        if (podcastPage == null) {
            podcastPage = withContext(Dispatchers.IO) {
                Innertube.podcastPage(browseId = browseId.removePrefix(MODIFIED_PREFIX)).getOrNull()
            }
        }
    }

    var filterCharSequence: CharSequence
    filterCharSequence = filter.toString()

    var searching by rememberSaveable { mutableStateOf(false) }

    val songThumbnailSizeDp = Dimensions.thumbnails.song
    val songThumbnailSizePx = songThumbnailSizeDp.px

    var isImportingPlaylist by rememberSaveable {
        mutableStateOf(false)
    }

    var downloadState by remember {
        mutableStateOf(Download.STATE_STOPPED)
    }

    
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    var totalPlayTimes = 0L
    podcastPage?.listEpisode?.forEach {
        totalPlayTimes += it.durationString?.let { it1 ->
            durationTextToMillis(it1) }?.toLong() ?: 0
    }

    val importDialog = ImportPlaylistDialog(
        initialValue = podcastPage?.title ?: "",
        onImport = { text ->
            Database.asyncTransaction {
                val playlist = Playlist(name = text, browseId = browseId)

                podcastPage?.listEpisode
                           ?.map( Innertube.Podcast.EpisodeItem::asMediaItem )
                           ?.let {
                               mapIgnore( playlist, *it.toTypedArray() )
                           }

                Toaster.done()
            }
            isImportingPlaylist = false
        }
    )

    if (isImportingPlaylist) {
        importDialog.showDialog()
        importDialog.Render()
    }

    var position by remember {
        mutableIntStateOf(0)
    }

    val thumbnailContent = adaptiveThumbnailContent(podcastPage == null, podcastPage?.thumbnail?.firstOrNull()?.url)

    val lazyListState = rememberLazyListState()

    LayoutWithAdaptiveThumbnail(thumbnailContent = thumbnailContent) {
        Box(
            modifier = Modifier
                .background(colorPalette().background0)
                //.fillMaxSize()
                .fillMaxHeight()
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (podcastPage == null) {
                Loader()
            } else {
                LazyColumn(
                    state = lazyListState,
                    //contentPadding = LocalPlayerAwareWindowInsets.current
                    //.only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
                    modifier = Modifier
                        .background(colorPalette().background0)
                        .fillMaxSize()
                ) {

                    item(
                        key = "header"
                    ) {

                    val modifierArt = if (isLandscape) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().aspectRatio(4f / 3)

                    Box(
                        modifier = modifierArt
                    ) {
                        if (podcastPage != null) {
                            if(!isLandscape)
                                ImageCacheFactory.AsyncImage(
                                    thumbnailUrl = podcastPage!!.thumbnail.firstOrNull()?.url?.resize(1000, 1000),
                                    contentDescription = stringResource(R.string.cd_loading),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.Center)
                                        .fadingEdge(
                                            top = WindowInsets.systemBars
                                                .asPaddingValues()
                                                .calculateTopPadding() + Dimensions.fadeSpacingTop,
                                            bottom = Dimensions.fadeSpacingBottom
                                        )
                                )

                            AutoResizeText(
                                text = podcastPage?.title ?: "",
                                style = typography().l.semiBold,
                                fontSizeRange = FontSizeRange(32.sp, 38.sp),
                                fontWeight = typography().l.semiBold.fontWeight,
                                fontFamily = typography().l.semiBold.fontFamily,
                                color = typography().l.semiBold.color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 30.dp)
                                    .padding(bottom = 20.dp)
                            )

                            BasicText(
                                text = podcastPage!!.listEpisode.size.toString() + " "
                                        + stringResource(R.string.songs)
                                        + " - " + formatAsTime(totalPlayTimes),
                                style = typography().xs.medium,
                                maxLines = 1,
                                modifier = Modifier
                                    //.padding(top = 10.dp)
                                    .align(Alignment.BottomCenter)
                            )


                            HeaderIconButton(
                                icon = R.drawable.share_social,
                                color = colorPalette().text,
                                iconSize = 24.dp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 5.dp, end= 5.dp),
                                onClick = {
                                    (ExternalUris.youtubeMusicPlaylist(browseId.removePrefix(MODIFIED_PREFIX).removePrefix("VL"))).let { url ->
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, url)
                                        }

                                        context.startActivity(Intent.createChooser(sendIntent, null))
                                    }
                                }
                            )

                        }
                    }

                }

                item(
                    key = "actions",
                    contentType = 0
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                    ) {

                        //if (!isLandscape) thumbnailContent()

                        if (podcastPage != null) {

                            //actionsContent()

                            HeaderIconButton(
                                onClick = { searching = !searching },
                                icon = R.drawable.search_circle,
                                color = colorPalette().text,
                                iconSize = 24.dp,
                                modifier = Modifier
                                    .padding(horizontal = 5.dp)
                            )

                            HeaderIconButton(
                                icon = R.drawable.download,
                                color = colorPalette().text,
                                modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                        onClick = {
                                            showConfirmDownloadAllDialog = true
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.info_download_all_songs )
                                        }
                            )

                            if (showConfirmDownloadAllDialog) {
                                ConfirmationDialog(
                                    text = stringResource(R.string.do_you_really_want_to_download_all),
                                    onDismiss = { showConfirmDownloadAllDialog = false },
                                    onConfirm = {
                                        showConfirmDownloadAllDialog = false
                                        downloadState = Download.STATE_DOWNLOADING
                                        if (podcastPage?.listEpisode?.isNotEmpty() == true)
                                            podcastPage?.listEpisode?.forEach {
                                                binder?.cache?.removeResource(it.asMediaItem.mediaId)
                                                Database.asyncTransaction {
                                                    formatTable.findBySongId( it.asMediaItem.mediaId )
                                                }
                                                manageDownload(
                                                    context = context,
                                                    mediaItem = it.asMediaItem,
                                                    downloadState = false
                                                )
                                            }
                                    }
                                )
                            }

                            HeaderIconButton(
                                icon = R.drawable.downloaded,
                                color = colorPalette().text,
                                modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                        onClick = {
                                            showConfirmDeleteDownloadDialog = true
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.info_remove_all_downloaded_songs )
                                        }
                            )

                            if (showConfirmDeleteDownloadDialog) {
                                ConfirmationDialog(
                                    text = stringResource(R.string.do_you_really_want_to_delete_download),
                                    onDismiss = { showConfirmDeleteDownloadDialog = false },
                                    onConfirm = {
                                        showConfirmDeleteDownloadDialog = false
                                        downloadState = Download.STATE_DOWNLOADING
                                        if (podcastPage?.listEpisode?.isNotEmpty() == true)
                                            podcastPage?.listEpisode?.forEach {
                                                binder?.cache?.removeResource(it.asMediaItem.mediaId)
                                                Database.asyncTransaction {
                                                    formatTable.findBySongId( it.asMediaItem.mediaId )
                                                }
                                                manageDownload(
                                                    context = context,
                                                    mediaItem = it.asMediaItem,
                                                    downloadState = true
                                                )
                                            }
                                    }
                                )
                            }



                            HeaderIconButton(
                                icon = R.drawable.enqueue,
                                enabled = podcastPage?.listEpisode?.isNotEmpty() == true,
                                color =  if (podcastPage?.listEpisode?.isNotEmpty() == true) colorPalette().text else colorPalette().textDisabled,
                                modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                        onClick = {
                                            podcastPage?.listEpisode?.map(Innertube.Podcast.EpisodeItem::asMediaItem)?.let { mediaItems ->
                                                binder?.player?.enqueue(mediaItems, context)
                                            }
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.info_enqueue_songs )
                                        }
                            )

                            HeaderIconButton(
                                icon = R.drawable.shuffle,
                                enabled = podcastPage?.listEpisode?.isNotEmpty() == true,
                                color = if (podcastPage?.listEpisode?.isNotEmpty() ==true) colorPalette().text else colorPalette().textDisabled,
                                modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                        onClick = {
                                            if (podcastPage?.listEpisode?.isNotEmpty() == true) {
                                                val mediaItems = podcastPage?.listEpisode
                                                    ?.map(Innertube.Podcast.EpisodeItem::asMediaItem)
                                                    ?: emptyList()
                                                binder?.let { Shuffler.play(it, mediaItems) }
                                            }
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.info_shuffle )
                                        }
                            )

                            HeaderIconButton(
                                icon = R.drawable.radio,
                                enabled = podcastPage?.listEpisode?.isNotEmpty() == true,
                                color = if (podcastPage?.listEpisode?.isNotEmpty() != true) colorPalette().textDisabled 
                                        else if (binder?.isRadioActive == true) colorPalette().accent 
                                        else colorPalette().text,
                                modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                        onClick = {
                                            val mediaItem = binder?.player?.currentMediaItem ?: podcastPage?.listEpisode?.first()?.asMediaItem
                                            mediaItem?.let { binder?.startRadio( it, false, null, true ) }
                                        },
                                        onLongClick = {
                                            Toaster.i( binder?.radioActionTextRes ?: R.string.info_start_radio )
                                        }
                            )


                            HeaderIconButton(
                                icon = R.drawable.add_in_playlist,
                                color = colorPalette().text,
                                modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                        onClick = {
                                            menuState.display {
                                                PlaylistsItemMenu(
                                                    navController = navController,
                                                    modifier = Modifier.fillMaxHeight(0.4f),
                                                    onDismiss = menuState::hide,
                                                    onImportOnlinePlaylist = {
                                                        isImportingPlaylist = true
                                                    },

                                                    //NOT NECESSARY IN ONLINE PLAYLIST USE IMPORT
                                                    onAddToPlaylist = { playlistPreview ->
                                                        position =
                                                            playlistPreview.songCount.minus(1) ?: 0
                                                        if (position > 0) position++ else position = 0

                                                        if (!isYouTubeSyncEnabled() || !playlistPreview.playlist.isYoutubePlaylist) {
                                                            val songs = podcastPage?.listEpisode
                                                                                                     ?.map( Innertube.Podcast.EpisodeItem::asMediaItem )
                                                                                                     .orEmpty()
                                                            Database.asyncTransaction {
                                                                mapIgnore( playlistPreview.playlist, *songs.toTypedArray() )
                                                            }
                                                        } else {
                                                            CoroutineScope(Dispatchers.IO).launch {
                                                                playlistPreview.playlist.browseId?.let { id ->
                                                                    addToYtPlaylist(playlistPreview.playlist.id,
                                                                        position,
                                                                        id,
                                                                        podcastPage?.listEpisode?.map { it.asMediaItem } ?: emptyList())
                                                                }
                                                            }
                                                        }

                                                        CoroutineScope(Dispatchers.Main).launch {
                                                            Toaster.done()
                                                        }
                                                    },
                                                    onGoToPlaylist = {
                                                        navController.navigate("${NavRoutes.localPlaylist.name}/$it")
                                                    },
                                                    disableScrollingText = disableScrollingText
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.info_add_in_playlist )
                                        }
                            )

                        }
                    }
                    Row (
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier
                            .padding(all = 10.dp)
                            .fillMaxWidth()
                    ) {
                        AnimatedVisibility(visible = searching) {
                            val focusRequester = remember { FocusRequester() }
                            val focusManager = LocalFocusManager.current
                            val keyboardController = LocalSoftwareKeyboardController.current

                            LaunchedEffect(searching) {
                                focusRequester.requestFocus()
                            }

                            BasicTextField(
                                value = filter ?: "",
                                onValueChange = { filter = it },
                                textStyle = typography().xs.semiBold,
                                singleLine = true,
                                maxLines = 1,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (filter.isNullOrBlank()) filter = ""
                                    focusManager.clearFocus()
                                }),
                                cursorBrush = SolidColor(colorPalette().text),
                                decorationBox = { innerTextField ->
                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 10.dp)
                                    ) {
                                        IconButton(
                                            onClick = {},
                                            icon = R.drawable.search,
                                            color = colorPalette().favoritesIcon,
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .size(16.dp)
                                        )
                                    }
                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 30.dp)
                                    ) {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = filter?.isEmpty() ?: true,
                                            enter = fadeIn(tween(100)),
                                            exit = fadeOut(tween(100)),
                                        ) {
                                            BasicText(
                                                text = stringResource(R.string.search),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = typography().xs.semiBold.secondary.copy(color = colorPalette().textDisabled)
                                            )
                                        }

                                        innerTextField()
                                    }
                                },
                                modifier = Modifier
                                    .height(30.dp)
                                    .fillMaxWidth()
                                    .background(
                                        colorPalette().background4,
                                        shape = thumbnailShape()
                                    )
                                    .focusRequester(focusRequester)
                                    .onFocusChanged {
                                        if (!it.hasFocus) {
                                            keyboardController?.hide()
                                            if (filter?.isBlank() == true) {
                                                filter = null
                                                searching = false
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = podcastPage?.listEpisode?.filter {item ->
                        if (!filter.isNullOrBlank()) {
                            item.asMediaItem.mediaMetadata.title?.contains(
                                filterCharSequence,
                                true
                            ) ?: false
                                    || item.asMediaItem.mediaMetadata.artist?.contains(
                                filterCharSequence,
                                true
                            ) ?: false
                        } else true
                    } ?: emptyList())
                { index, song ->
                    val isLocal by remember { derivedStateOf { song.asMediaItem.isLocal } }
                    downloadState = getDownloadState(song.asMediaItem.mediaId)
                    val isDownloaded = if (!isLocal) isDownloadedSong(song.asMediaItem.mediaId) else true

                    SwipeablePlaylistItem(
                        mediaItem = song.asMediaItem,
                        onPlayNext = {
                            binder?.player?.addNext(song.asMediaItem)
                        },
                        onDownload = {
                            binder?.cache?.removeResource(song.asMediaItem.mediaId)
                            Database.asyncTransaction {
                                formatTable.updateContentLengthOf( song.asMediaItem.mediaId )
                            }

                            if (!isLocal)
                                manageDownload(
                                    context = context,
                                    mediaItem = song.asMediaItem,
                                    downloadState = isDownloaded
                                )
                        },
                        onEnqueue = {
                            binder?.player?.enqueue(song.asMediaItem)
                        }
                    ) {
                        var forceRecompose by remember { mutableStateOf(false) }
                        SongItem(
                            song = song.asMediaItem,
                            onDownloadClick = {
                                binder?.cache?.removeResource(song.asMediaItem.mediaId)
                                Database.asyncTransaction {
                                    formatTable.findBySongId( song.asMediaItem.mediaId )
                                }
                                if (!isLocal)
                                    manageDownload(
                                        context = context,
                                        mediaItem = song.asMediaItem,
                                        downloadState = isDownloaded
                                    )
                            },
                            downloadState = downloadState,
                            thumbnailSizePx = songThumbnailSizePx,
                            thumbnailSizeDp = songThumbnailSizeDp,
                            modifier = Modifier
                                .background(colorPalette().background0)
                                .clip(uiRoundnessShape()).combinedClickable(
                                    onLongClick = {
                                        menuState.display {
                                            SongItemMenu(
                                                navController = navController,
                                                song = song.asMediaItem.asSong
                                            ).MenuComponent()
                                        }
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onClick = {
                                        searching = false
                                        filter = null
                                        podcastPage?.listEpisode?.map(Innertube.Podcast.EpisodeItem::asMediaItem)
                                            ?.let { mediaItems ->
                                                binder?.stopRadio()
                                                binder?.player?.forcePlayAtIndex(mediaItems, index)
                                            }
                                    }
                                ),
                            disableScrollingText = disableScrollingText,
                            isNowPlaying = binder?.player?.isNowPlaying(song.videoId) ?: false,
                            forceRecompose = forceRecompose
                        )
                    }
                }

                item(
                    key = "footer",
                    contentType = 0,
                ) {
                    Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                }
            }

            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
            if( showFloatingIcon )
            FloatingActionsContainerWithScrollToTop(
                lazyListState = lazyListState,
                iconId = R.drawable.shuffle,
                onClick = {
                    podcastPage?.listEpisode?.let { episodes ->
                        if (episodes.isNotEmpty()) {
                            val mediaItems = episodes.map(Innertube.Podcast.EpisodeItem::asMediaItem)
                            binder?.let { Shuffler.play(it, mediaItems) }
                        }
                    }
                }
            )
            }
        }
    }
}







