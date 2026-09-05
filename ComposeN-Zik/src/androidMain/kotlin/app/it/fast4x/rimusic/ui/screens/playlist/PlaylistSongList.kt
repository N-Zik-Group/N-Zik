package app.it.fast4x.rimusic.ui.screens.playlist

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import app.n_zik.android.core.database.*

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastFirst
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.resize
import app.it.fast4x.compose.persist.persist
import app.it.fast4x.compose.persist.persistList
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.requests.PlaylistPage
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.cleanPrefix

import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import app.it.fast4x.rimusic.ui.components.themed.AutoResizeText
import app.it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import app.it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.it.fast4x.rimusic.ui.components.themed.FontSizeRange
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.n_zik.android.components.dialog.playlist.ImportPlaylistDialog
import app.it.fast4x.rimusic.ui.components.themed.LayoutWithAdaptiveThumbnail
import app.it.fast4x.rimusic.ui.components.themed.PlaylistsItemMenu
import app.it.fast4x.rimusic.ui.components.themed.adaptiveThumbnailContent
import app.it.fast4x.rimusic.ui.components.themed.Loader

import app.it.fast4x.rimusic.ui.items.SongItemPlaceholder
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.addToYtLikedSongs
import app.it.fast4x.rimusic.utils.align
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.durationTextToMillis
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.fadingEdge
import app.it.fast4x.rimusic.utils.forcePlayAtIndex
import app.it.fast4x.rimusic.utils.forcePlayFromBeginning
import app.it.fast4x.rimusic.utils.formatAsDuration
import app.it.fast4x.rimusic.utils.formatAsTime
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.it.fast4x.rimusic.utils.isDownloadedSong
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.isNetworkConnected
import app.it.fast4x.rimusic.enums.Languages
import app.it.fast4x.rimusic.utils.otherLanguageAppPlaylistKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.manageDownload
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.showFloatingIconKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.it.fast4x.rimusic.utils.ExternalUris
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import app.n_zik.android.components.SongItem
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.playback.utils.Shuffler
import app.it.fast4x.rimusic.ui.components.themed.ValueSelectorDialog
import timber.log.Timber
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.n_zik.android.thumbnailShape
import java.util.Locale


@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun PlaylistSongList(
    navController: NavController,
    browseId: String,
) {
    // Context
    val binder = LocalPlayerServiceBinder.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()

    // Settings
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    var playlistPage by persist<PlaylistPage?>("playlist/$browseId/playlistPage")
    var continuation: String? by remember { mutableStateOf( null ) }
    var playlistSongs by persistList<Innertube.SongItem>("playlist/$browseId/songs")
    var loadedSongsCount by remember { mutableIntStateOf(0) }

    val updatedItemsPageProvider: suspend (String?) -> Result<PlaylistPage> by rememberUpdatedState {
        if( it == null )
            YtMusic.getPlaylist( browseId.removePrefix(MODIFIED_PREFIX) )
        else
            YtMusic.getPlaylistContinuation( it )
                   .map { fetchedPlaylist ->
                       playlistPage!!.copy(
                           songs = fetchedPlaylist?.songs.orEmpty(),
                           songsContinuation = fetchedPlaylist?.continuation
                       )
                   }
    }

    LaunchedEffect( browseId ) {
        loadedSongsCount = 0
        if (playlistPage == null) {
            withContext(Dispatchers.IO) {
                val firstPage = YtMusic.getPlaylist( browseId.removePrefix(MODIFIED_PREFIX) ).getOrNull() ?: return@withContext
                val allSongs = firstPage.songs.toMutableList()
                loadedSongsCount = allSongs.fastDistinctBy( Innertube.SongItem::key ).size
                var cont = firstPage.songsContinuation
                var maxPages = 50
                while (cont != null && maxPages-- > 0) {
                    val next = YtMusic.getPlaylistContinuation( cont ).getOrNull()
                    if (next != null) {
                        allSongs += next.songs
                        loadedSongsCount = allSongs.fastDistinctBy( Innertube.SongItem::key ).size
                        cont = next.continuation
                    } else {
                        cont = null
                    }
                }
                playlistPage = firstPage.copy(songs = allSongs, songsContinuation = null)
                playlistSongs = allSongs
                    .fastFilter { !parentalControlEnabled || !it.explicit }
                    .fastDistinctBy( Innertube.SongItem::key )
                continuation = null
            }
        }
    }

    LaunchedEffect( lazyListState ) {

        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" } }
            .collect { shouldLoadMore ->
                if ( !shouldLoadMore ) return@collect

                withContext(Dispatchers.IO) {
                    updatedItemsPageProvider(continuation)
                }.onSuccess { onlinePlaylist ->
                    if( continuation == null )
                        playlistPage = onlinePlaylist

                    playlistSongs += onlinePlaylist.songs
                                                   .fastFilter { !parentalControlEnabled || !it.explicit }
                                                   .fastDistinctBy( Innertube.SongItem::key )
                    continuation = onlinePlaylist.songsContinuation
                }.onFailure { e -> Timber.tag("PlaylistSongList").e(e, "Failed to load more playlist songs") }
            }
    }

    var filter: String? by rememberSaveable { mutableStateOf(null) }
    var showConfirmDownloadAllDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteDownloadDialog by remember { mutableStateOf(false) }
    var saveCheck by remember { mutableStateOf(false) }

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)

    var translateEnabled by remember {
        mutableStateOf(false)
    }
    var showTranslateLanguageDialog by remember { mutableStateOf(false) }

    val translator = Translator(NetworkClientFactory.getTranslatorClient())
    var otherLanguageApp by rememberPreference(otherLanguageAppPlaylistKey, Languages.System)
    val appLang = Locale.getDefault().language
    val activeTranslateLang = remember(otherLanguageApp, appLang) {
        if (otherLanguageApp != Languages.System) otherLanguageApp
        else Languages.entries.firstOrNull { it.code == appLang } ?: Languages.English
    }
    val languageDestination = activeTranslateLang.translatorLanguage

    val localPlaylist by remember( saveCheck ) {
        Database.playlistTable
                .findByBrowseId( browseId )
    }.collectAsState( null, Dispatchers.IO )

    var filterCharSequence: CharSequence
    filterCharSequence = filter.toString()
    //Log.d("mediaItemFilter", "<${filter}>  <${filterCharSequence}>")
    if (!filter.isNullOrBlank()) {
        playlistPage?.songs =
            playlistPage?.songs?.filter { songItem ->
                songItem.asMediaItem.mediaMetadata.title?.contains(
                    filterCharSequence,
                    true
                ) ?: false
                        || songItem.asMediaItem.mediaMetadata.artist?.contains(
                    filterCharSequence,
                    true
                ) ?: false
                        || songItem.asMediaItem.mediaMetadata.albumTitle?.contains(
                    filterCharSequence,
                    true
                ) ?: false
            }!!
    } else playlistPage?.songs = playlistSongs

    var playlistNotLikedSongs by persistList<Innertube.SongItem>("playlist/$browseId/notLikedSongs")

    var searching by rememberSaveable { mutableStateOf(false) }

    
    var showYoutubeLikeConfirmDialog by remember {
        mutableStateOf(false)
    }
    var totalMinutesToLike by remember { mutableStateOf("") }

    if (showYoutubeLikeConfirmDialog) {
        LaunchedEffect(playlistSongs) {
            playlistNotLikedSongs = playlistSongs.filter {
                Database.songTable.isLiked( it.asSong.id ).first()
            }
            totalMinutesToLike = formatAsDuration(playlistNotLikedSongs.size.toLong()*1000)
        }
        ConfirmationDialog(
            text = "$totalMinutesToLike "+stringResource(R.string.do_you_really_want_to_like_all),
            onDismiss = { showYoutubeLikeConfirmDialog = false },
            onConfirm = {
                showYoutubeLikeConfirmDialog = false
                CoroutineScope(Dispatchers.IO).launch {
                    addToYtLikedSongs(playlistNotLikedSongs.map {it.asMediaItem})
                }
            }
        )
    }

    var totalPlayTimes = 0L
    playlistPage?.songs?.forEach {
        totalPlayTimes += it.durationText?.let { it1 ->
            durationTextToMillis(it1) }?.toLong() ?: 0
    }

    val dislikedSongs by remember {
        Database.songTable
                .allDisliked()
                .map { list ->
                    list.map( Song::id )
                }
                .distinctUntilChanged()
    }.collectAsState( emptyList(), Dispatchers.IO )

    val importPlaylistDialog = ImportPlaylistDialog(
        initialValue = playlistPage?.playlist?.title ?: ""
    ) { text ->
        Database.asyncTransaction {
            val playlist = Playlist(name = text, browseId = browseId)

            playlistPage?.songs
                        ?.map( Innertube.SongItem::asMediaItem )
                        ?.let {
                            mapIgnore( playlist, *it.toTypedArray() )
                        }

            Toaster.done()
        }
    }
    importPlaylistDialog.Render()

    var position by remember {
        mutableIntStateOf(0)
    }

    val thumbnailContent = adaptiveThumbnailContent(
        isLoading = playlistPage == null,
        url = playlistPage?.playlist?.thumbnail?.url,
        isYoutubePlaylist = true
    )

    LayoutWithAdaptiveThumbnail(thumbnailContent = thumbnailContent) {
        Box(
            modifier = Modifier
                .background(colorPalette().background0)
                //.fillMaxSize()
                .fillMaxHeight()
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (playlistPage == null && playlistSongs.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Loader()
                    if (loadedSongsCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        BasicText(
                            text = stringResource(R.string.loading_songs_count, loadedSongsCount),
                            style = typography().xxs.copy(
                                color = colorPalette().textDisabled
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer),
                    modifier = Modifier.background( colorPalette().background0 )
                                       .fillMaxSize()
                ) {
                    item(
                        key = "header"
                    ) {

                    val modifierArt = Modifier.fillMaxWidth()

                    Box(
                        modifier = modifierArt
                    ) {
                        if (playlistPage != null) {
                            if(!isLandscape)
                                Box {
                                    ImageCacheFactory.AsyncImage(
                                        thumbnailUrl = playlistPage!!.playlist.thumbnail?.url?.resize(
                                            1000,
                                            1000
                                        ),
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
                                }
                                Image(
                                    painter = when {
                                        browseId?.startsWith("SPOTIFY_IMPORT") == true -> painterResource(R.drawable.spotify)
                                        browseId?.startsWith("RIPLAY_IMPORT") == true -> painterResource(R.drawable.riplay)
                                        browseId.isNullOrEmpty() -> painterResource(R.drawable.musical_notes)
                                        else -> painterResource(R.drawable.ytmusic)
                                    },
                                    contentDescription = null,
                                    colorFilter = when {
                                        browseId?.startsWith("SPOTIFY_IMPORT") == true || browseId?.startsWith("RIPLAY_IMPORT") == true -> null
                                        else -> ColorFilter.tint(Color.Red.copy(0.75f).compositeOver(Color.White))
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .offset(5.dp, 5.dp)
                                )

                            AutoResizeText(
                                text = playlistPage?.playlist?.title ?: "",
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
                                text = playlistPage!!.songs?.size.toString() + " "
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
                                    .padding(top = 5.dp, end = 5.dp),
                                onClick = {
                                    ExternalUris.youtubeMusicPlaylist(browseId.removePrefix(MODIFIED_PREFIX).removePrefix("VL")).let { url ->
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

                        if (playlistPage != null) {

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
                                            Toaster.i(R.string.info_download_all_songs)
                                        }
                            )

                            if (showConfirmDownloadAllDialog) {
                                ConfirmationDialog(
                                    text = stringResource(R.string.do_you_really_want_to_download_all),
                                    onDismiss = { showConfirmDownloadAllDialog = false },
                                    onConfirm = {
                                        showConfirmDownloadAllDialog = false
                                        if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true) {
                                            if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true)
                                                playlistPage?.songs?.filter { it.asMediaItem.mediaId !in dislikedSongs }
                                                    ?.forEach {
                                                        binder?.cache?.removeResource(it.asMediaItem.mediaId)
                                                        Database.asyncTransaction {
                                                            formatTable.findBySongId( it.key )
                                                        }
                                                        manageDownload(
                                                            context = context,
                                                            mediaItem = it.asMediaItem,
                                                            downloadState = false
                                                        )
                                                    } else
                                                Toaster.e(R.string.disliked_this_collection)
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
                                            Toaster.n(R.string.info_remove_all_downloaded_songs)
                                        }
                            )

                            if (showConfirmDeleteDownloadDialog) {
                                ConfirmationDialog(
                                    text = stringResource(R.string.do_you_really_want_to_delete_download),
                                    onDismiss = { showConfirmDeleteDownloadDialog = false },
                                    onConfirm = {
                                        showConfirmDeleteDownloadDialog = false
                                        if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true) {
                                            if (playlistPage?.songs?.isNotEmpty() == true)
                                                playlistPage?.songs?.forEach {
                                                    binder?.cache?.removeResource(it.asMediaItem.mediaId)
                                                    Database.asyncTransaction {
                                                        formatTable.findBySongId( it.key )
                                                    }
                                                    manageDownload(
                                                        context = context,
                                                        mediaItem = it.asMediaItem,
                                                        downloadState = true
                                                    )
                                                } else {
                                                Toaster.e(R.string.disliked_this_collection)
                                            }
                                        }
                                    }
                                )
                            }



                            HeaderIconButton(
                                icon = R.drawable.enqueue,
                                enabled = playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true,
                                color =  if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true) colorPalette().text else colorPalette().textDisabled,
                                modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                        onClick = {
                                            if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true) {
                                                playlistPage?.songs?.filter { it.asMediaItem.mediaId !in dislikedSongs }
                                                    ?.map(Innertube.SongItem::asMediaItem)
                                                    ?.let { mediaItems ->
                                                        binder?.player?.enqueue(mediaItems, context)
                                                    }
                                            } else
                                                Toaster.e(R.string.disliked_this_collection)
                                        },
                                        onLongClick = {
                                            Toaster.i(R.string.info_enqueue_songs)
                                        }
                            )

                            HeaderIconButton(
                                icon = R.drawable.shuffle,
                                enabled = playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true,
                                color = if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true) colorPalette().text else colorPalette().textDisabled,
                                modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                        onClick = {
                                            if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true) {
                                                val mediaItems = playlistPage?.songs
                                                    ?.filter { it.asMediaItem.mediaId !in dislikedSongs }
                                                    ?.map(Innertube.SongItem::asMediaItem)
                                                    ?: emptyList()
                                                binder?.let { Shuffler.play(it, mediaItems) }
                                            } else
                                                Toaster.e( R.string.disliked_this_collection )
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.info_shuffle )
                                        }
                            )

                            HeaderIconButton(
                                icon = R.drawable.radio,
                                enabled = playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true,
                                color = if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } != true) colorPalette().textDisabled
                                        else if (binder?.isRadioActive == true) colorPalette().accent
                                        else colorPalette().text,
                                modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                        onClick = {
                                            val songs = playlistPage?.songs.orEmpty()
                                            if( songs.fastAny { it.key in dislikedSongs } ) {
                                                Toaster.e( R.string.disliked_this_collection )
                                                return@HeaderIconButton
                                            }

                                            val mediaItem =
                                                // [songs.fastFirst] won't throw NoSuchElementException
                                                // because of the checking above.
                                                binder?.player?.currentMediaItem ?: songs.fastFirst { it.key !in dislikedSongs }.asMediaItem
                                            mediaItem.let { binder?.startRadio( it, false, null, true ) }
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
                                                        importPlaylistDialog.onShortClick()
                                                    },

                                                    onAddToPlaylist = { playlistPreview ->
                                                        position =
                                                            playlistPreview.songCount.minus(1) ?: 0
                                                        if (position > 0) position++ else position =
                                                            0

                                                        val playlistSize = playlistPage?.songs?.size ?: 0

                                                        if ((playlistSize + playlistPreview.songCount) > 5000 && playlistPreview.playlist.isYoutubePlaylist && isYouTubeSyncEnabled()){
                                                            Toaster.e( R.string.yt_playlist_limited )
                                                        } else if (!isYouTubeSyncEnabled() || !playlistPreview.playlist.isYoutubePlaylist) {
                                                            Database.asyncTransaction {
                                                                val songs = playlistPage?.songs
                                                                                                          ?.map( Innertube.SongItem::asMediaItem )
                                                                                                          .orEmpty()
                                                                mapIgnore( playlistPreview.playlist, *songs.toTypedArray() )
                                                            }
                                                        } else {
                                                            CoroutineScope(Dispatchers.IO).launch {
                                                                YtMusic.addPlaylistToPlaylist(
                                                                    cleanPrefix(playlistPreview.playlist.browseId ?: ""),
                                                                    browseId.substringAfter("VL")

                                                                    )
                                                                }
                                                        }
                                                        Toaster.done()
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
                            HeaderIconButton(
                                icon = R.drawable.heart,
                                enabled = playlistPage?.songs?.isNotEmpty() == true,
                                color = colorPalette().text,
                                modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                        onClick = {
                                            if (!isNetworkConnected(appContext()) && isYouTubeSyncEnabled()) {
                                                Toaster.noInternet()
                                            } else if (!isYouTubeSyncEnabled()){
                                                CoroutineScope( Dispatchers.IO ).launch {
                                                    playlistPage!!.songs
                                                                  .map{ it.asSong.id }
                                                                  .filter {
                                                                      !Database.songTable.isLiked( it ).first()
                                                                  }
                                                                  .forEach( Database.songTable::toggleLike )

                                                    Toaster.done()
                                                }
                                            } else {
                                                showYoutubeLikeConfirmDialog = true
                                            }
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.add_to_favorites )
                                        }
                            )
                            HeaderIconButton(
                                icon = if (localPlaylist?.isYoutubePlaylist == true) R.drawable.bookmark else R.drawable.bookmark_outline,
                                color = colorPalette().text,
                                modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                onClick = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        if (localPlaylist?.isYoutubePlaylist == true) {
                                            if (isYouTubeSyncEnabled() && isNetworkConnected(context)) {
                                                YtMusic.removelikePlaylistOrAlbum(browseId.substringAfter("VL"))
                                            }
                                            Database.playlistTable
                                                .findByBrowseId(browseId)
                                                .first()
                                                ?.let( Database.playlistTable::delete )
                                        } else {
                                            if (isYouTubeSyncEnabled() && isNetworkConnected(context)) {
                                                YtMusic.likePlaylistOrAlbum(browseId.substringAfter("VL"))
                                            }
                                            Database.asyncTransaction {
                                                val normalizedBrowseId = if (browseId.startsWith("VL")) browseId else "VL$browseId"
                                                val playlist = Playlist(
                                                    name = (playlistPage?.playlist?.title ?: ""),
                                                    browseId = normalizedBrowseId,
                                                    isYoutubePlaylist = true,
                                                    isEditable = false
                                                )
                                                playlistPage?.songs
                                                    ?.map( Innertube.SongItem::asMediaItem )
                                                    ?.let { mapIgnore( playlist, *it.toTypedArray() ) }
                                            }
                                        }
                                        Toaster.done()
                                        saveCheck = !saveCheck
                                    }
                                },
                                onLongClick = {
                                    Toaster.i( R.string.save_youtube_library )
                                }
                            )



                        } else {
                            BasicText(
                                text = stringResource(R.string.info_wait_it_may_take_a_few_minutes),
                                style = typography().xxs.medium,
                                maxLines = 1
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

                playlistPage?.description?.let { description ->
                    if( description.isNotBlank() ) {
                        item(
                            key = "playlistInfo"
                        ) {
                            var isDescriptionExpanded by remember { mutableStateOf(false) }
                            val attributionsIndex = description.lastIndexOf("\n\nFrom Wikipedia")
                            val nonTranslatedText by remember {
                                mutableStateOf(
                                    if (attributionsIndex == -1) description
                                    else description.substring(0, attributionsIndex)
                                )
                            }

                            var translatedText by remember { mutableStateOf("") }
                            if (translateEnabled == true) {
                                LaunchedEffect(Unit) {
                                    val result = withContext(Dispatchers.IO) {
                                        try {
                                            translator.translate(
                                                nonTranslatedText,
                                                languageDestination,
                                                Language.AUTO
                                            ).translatedText
                                        } catch (e: Exception) {
                                            Timber.tag("PlaylistSongList").e(e, "Failed to translate text")
                                        }
                                    }
                                    translatedText = if (result.toString() == "kotlin.Unit") "" else result.toString()
                                }
                            } else translatedText = nonTranslatedText

                            var showMoreButton by remember { mutableStateOf(false) }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .animateContentSize()
                                    .clip(uiRoundnessShape())
                                    .clickable(enabled = showMoreButton || isDescriptionExpanded) { isDescriptionExpanded = !isDescriptionExpanded }
                                    .padding(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    IconButton(
                                        icon = R.drawable.translate,
                                        color = if (translateEnabled == true) colorPalette().text else colorPalette().textDisabled,
                                        enabled = true,
                                        modifier = Modifier.padding(all = 8.dp).size(18.dp).clip(uiRoundnessShape()),
                                        onClick = { translateEnabled = !translateEnabled },
                                        onLongClick = { showTranslateLanguageDialog = true }
                                    )
                                    
                                    if (showTranslateLanguageDialog) {
                                        ValueSelectorDialog(
                                            title = stringResource(R.string.info_translation),
                                            selectedValue = otherLanguageApp,
                                            onValueSelected = {
                                                otherLanguageApp = it
                                                translateEnabled = it.translatorLanguage != Language.ENGLISH
                                                showTranslateLanguageDialog = false
                                            },
                                            valueText = { it.text },
                                            values = Languages.entries.toList(),
                                            onDismiss = { showTranslateLanguageDialog = false }
                                        )
                                    }
                                    
                                    BasicText(
                                        text = "“",
                                        style = typography().xxl.semiBold,
                                        modifier = Modifier.offset(y = (-8).dp)
                                    )
                                    
                                    BasicText(
                                        text = translatedText,
                                        style = typography().xs.secondary.align(TextAlign.Justify),
                                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp).weight(1f),
                                        onTextLayout = { textLayoutResult ->
                                            if (textLayoutResult.hasVisualOverflow) {
                                                showMoreButton = true
                                            } else if (!isDescriptionExpanded) {
                                                showMoreButton = false
                                            }
                                        }
                                    )

                                    BasicText(
                                        text = "”",
                                        style = typography().xxl.semiBold,
                                        modifier = Modifier.offset(y = 4.dp).align(Alignment.Bottom)
                                    )
                                }

                                if (showMoreButton || isDescriptionExpanded) {
                                    BasicText(
                                        text = if (isDescriptionExpanded) stringResource(R.string.show_less) else stringResource(R.string.show_more),
                                        style = typography().xs.semiBold.copy(colorPalette().text),
                                        modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally)
                                    )
                                }

                                if (isDescriptionExpanded && attributionsIndex != -1) {
                                    BasicText(
                                        text = stringResource(R.string.from_wikipedia_cca),
                                        style = typography().xxs
                                            .color(colorPalette().textDisabled)
                                            .align(TextAlign.Start),
                                        modifier = Modifier.padding(top = 8.dp, start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                items(
                    items = playlistSongs,
                    key = Innertube.SongItem::key
                ) { ytSong ->
                    val isLocal by remember { derivedStateOf { ytSong.asMediaItem.isLocal } }
                    val isDownloaded = !isLocal && isDownloadedSong( ytSong.key )

                    SwipeablePlaylistItem(
                        mediaItem = ytSong.asMediaItem,
                        onPlayNext = {
                            binder?.player?.addNext(ytSong.asMediaItem)
                        },
                        onDownload = {
                            binder?.cache?.removeResource( ytSong.key )
                            Database.asyncTransaction {
                                formatTable.updateContentLengthOf( ytSong.key )
                            }

                            if (!isLocal)
                                manageDownload(
                                    context = context,
                                    mediaItem = ytSong.asMediaItem,
                                    downloadState = isDownloaded
                                )
                        },
                        onEnqueue = {
                            binder?.player?.enqueue(ytSong.asMediaItem)
                        }
                    ) {
                        SongItem(
                            song = ytSong.asSong,
                            navController = navController,
                            modifier = Modifier,

                            onClick = {
                                if ( ytSong.key !in dislikedSongs ) {
                                    searching = false
                                    filter = null
                                    playlistPage?.songs
                                                ?.filter { it.key !in dislikedSongs }
                                                ?.map(Innertube.SongItem::asMediaItem)
                                                ?.let { mediaItems ->
                                                    binder?.stopRadio()
                                                    binder?.player?.forcePlayAtIndex(
                                                        mediaItems,
                                                        mediaItems.indexOf( ytSong.asMediaItem )
                                                    )
                                                }
                                } else
                                    Toaster.e( R.string.disliked_this_song )
                            }
                        )
                    }
                }

                if (playlistPage != null && continuation != null) {
                    item("loading") { SongItemPlaceholder() }
                }
            }

            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
            if( showFloatingIcon )
            FloatingActionsContainerWithScrollToTop(
                lazyListState = lazyListState,
                iconId = R.drawable.shuffle,
                onClick = {
                    if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true) {
                        val mediaItems = playlistPage?.songs
                            ?.filter { it.asMediaItem.mediaId !in dislikedSongs }
                            ?.map(Innertube.SongItem::asMediaItem)
                            ?: emptyList()
                        binder?.let { Shuffler.play(it, mediaItems) }
                    } else
                        Toaster.e( R.string.disliked_this_collection )
                }
            )
            }
        }
    }
}








