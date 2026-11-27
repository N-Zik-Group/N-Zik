package app.n_zik.android.ui.screens.podcast

import timber.log.Timber
import app.n_zik.android.components.glass.GlassArtworkBackdrop
import app.n_zik.android.components.podcast.PodcastBannerThumbnail
import app.n_zik.android.components.podcast.PodcastBookmarkAction
import app.n_zik.android.components.podcast.PodcastEmptyEpisodes
import app.n_zik.android.components.podcast.PodcastLoadingStatus
import app.n_zik.android.components.podcast.PodcastPageLoader
import app.n_zik.android.components.podcast.podcastEpisodeLoadingLabel
import app.n_zik.android.components.podcast.PodcastPageStatus
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import app.n_zik.android.utils.asSong
import androidx.compose.ui.draw.clip

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.dialog.common.ConfirmationDialog

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.n_zik.android.cleanPrefix
import app.n_zik.android.components.menu.isCurrentMenu
import app.n_zik.android.R
import app.n_zik.android.components.radioButtonIcon
import app.n_zik.android.components.shuffleButtonIcon
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.resize

import app.n_zik.android.colorPalette
import app.n_zik.innertube.Innertube
import app.n_zik.android.compose.persist.persist
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.models.Playlist
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.typography
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.SwipeablePlaylistItem
import app.n_zik.android.ui.components.themed.AutoResizeText
import app.n_zik.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.android.ui.components.themed.FontSizeRange
import app.n_zik.android.ui.components.themed.HeaderIconButton
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.stopActiveRadio
import app.n_zik.android.components.ui.CollectionToolbar
import app.n_zik.android.components.settings.ui.ToolbarAction as ToolbarControl
import app.n_zik.android.components.settings.ui.ToolbarSurface
import app.n_zik.android.components.settings.ui.visibleActions
import app.n_zik.android.components.ui.CollectionFavoritesKind
import app.n_zik.android.components.ui.rememberCollectionFavoritesButton
import app.n_zik.android.ui.components.themed.PlayNext
import app.n_zik.android.utils.player.addNextOffMain
import app.n_zik.android.listentogether.rememberListenTogetherGuestLock
import app.n_zik.android.core.database.collectionFavoriteSongId
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.tab.toolbar.EllipsisMenuComponent
import app.n_zik.android.components.ui.screens.CollectionArtworkShareButton
import app.n_zik.android.components.ui.screens.CollectionArtworkTopAction
import app.n_zik.android.components.dialog.playlist.ImportPlaylistDialog
import app.n_zik.android.components.menu.playlist.CollectionImportStatus
import app.n_zik.android.components.menu.playlist.rememberCollectionAddAction
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.components.ui.screens.LayoutWithAdaptiveThumbnail
import app.n_zik.android.ui.components.themed.PlaylistsMenu
import app.n_zik.android.components.SongItem
import app.n_zik.android.ui.components.ArtworkShimmerPlaceholder
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.items.FullSongListPlaceholderCount
import app.n_zik.android.ui.items.SongItemPlaceholder
import app.n_zik.android.ui.components.themed.MeasuredMetadataPlaceholder
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.rememberSongRowArtistCredits
import app.n_zik.android.components.menu.playlist.withSourcePlaylistOwner
import app.n_zik.android.components.playlist.launchLoadedPlaylistImport
import app.n_zik.android.core.database.findMusicBookmarkByBrowseId
import app.n_zik.android.components.rememberSongRowMetadata

import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.matchesCollectionSearch
import app.n_zik.android.components.song.resolveSongDisplayMetadata
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.utils.addNext
import app.n_zik.android.utils.asPodcastMediaItem
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.durationTextToMillis
import app.n_zik.android.utils.enqueue
import app.n_zik.android.utils.excludeMediaItems
import app.n_zik.android.utils.fadingEdge
import app.n_zik.android.utils.forcePlayAtIndex
import app.n_zik.android.utils.formatAsTime
import app.n_zik.android.utils.getDownloadState
import app.n_zik.android.utils.isDownloadedSong
import app.n_zik.android.utils.isLandscape
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.manageDownload
import app.n_zik.android.utils.medium
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.showFloatingIconKey
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.utils.ExternalUris
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.playback.utils.Shuffler
import app.n_zik.android.components.menu.song.SongItemMenu
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.LocalDownloadStatesMap
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.enums.DownloadedStateMedia
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle


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

    val isOnline by NetworkQualityHelper.isNetworkAvailableComposable(context)

    var podcastPage by persist<Innertube.Podcast?>("podcast/$browseId/listEpisodes")
    val podcastLoader = remember(browseId) { PodcastPageLoader(podcastPage) }
    val podcastLoadState by podcastLoader.state.collectAsStateWithLifecycle(context = NzikDispatchers.DATA)
    val storedPodcast by remember(browseId) { Database.playlistTable.findMusicBookmarkByBrowseId(browseId) }
        .collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)
    val podcastTitle = storedPodcast?.cleanName() ?: podcastPage?.title
    val podcastAuthor = storedPodcast?.displayAuthorName() ?: podcastPage?.author
    val owner = podcastPage?.owner
    var retryAttempt by remember(browseId) { mutableIntStateOf(0) }

    val search = key(browseId) { Search.detail() }
    val filter = search.filterQuery
    var pendingDownloadSongs by remember(browseId) { mutableStateOf<List<MediaItem>?>(null) }
    var pendingDeleteDownloads by remember(browseId) { mutableStateOf<List<MediaItem>?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(podcastLoader, retryAttempt) {
        if (podcastPage == null) {
            podcastLoader.load(browseId.removePrefix(MODIFIED_PREFIX))
            podcastPage = podcastLoader.state.value.page
        }
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

    val thumbnailContent: @Composable () -> Unit = {
        PodcastBannerThumbnail(
            podcastLoadState.status == PodcastPageStatus.Loading,
            podcastPage?.thumbnail?.firstOrNull()?.url,
        )
    }

    val lazyListState = rememberLazyListState()
    val forceShimmer = forceLoadingShimmerPreview()
    val initialLoading = podcastPage == null && podcastLoadState.status == PodcastPageStatus.Loading
    val showShimmer = initialLoading || forceShimmer
    val itemSelector = key(browseId) { ItemSelector<Song>() }
    val collectionEpisodes = podcastPage?.listEpisode.orEmpty()
    val episodeIds = remember(collectionEpisodes) {
        collectionEpisodes.map { collectionFavoriteSongId(it.videoId) }.distinct()
    }
    val storedEpisodes by rememberSongRowMetadata(episodeIds)
    val episodeCredits by rememberSongRowArtistCredits(episodeIds)
    val visibleEpisodes = remember(collectionEpisodes, filter, storedEpisodes) {
        val seenEpisodeIds = HashSet<String>()
        collectionEpisodes.mapIndexedNotNull { index, episode ->
            val key = if (seenEpisodeIds.add(episode.videoId)) "episode-id-${episode.videoId}"
                else "episode-repeat-$index"
            val matches = filter.isBlank() || resolveSongDisplayMetadata(
                episode.asPodcastMediaItem(browseId).asSong, storedEpisodes[collectionFavoriteSongId(episode.videoId)]
            ).matchesCollectionSearch(filter)
            if (matches) Triple(key, index, episode) else null
        }
    }
    SearchEmptyFeedback(
        query = filter.orEmpty(),
        empty = visibleEpisodes.isEmpty(),
        loading = showShimmer || podcastLoadState.status != PodcastPageStatus.Content,
    )
    val searchTargets = remember(visibleEpisodes) { visibleEpisodes.map { it.third } }
    fun targetEpisodes() = itemSelector.actionTargets(searchTargets, Song::id,
        { collectionFavoriteSongId(it.videoId) })
    fun targetMediaItems() = targetEpisodes().map { it.asPodcastMediaItem(browseId) }
    fun shuffleSelection() {
        if (!Shuffler.canStart()) return
        val mediaItems = targetMediaItems()
        if (mediaItems.isEmpty()) return
        binder?.let {
            Shuffler.play(it, mediaItems, onQueueApplied = it::triggerShuffleOkFlash)
        }
    }
    val episodes = targetEpisodes()
    val hasEpisodeTargets = episodes.isNotEmpty()
    val collectionActionEnabled = !itemSelector.isActive || hasEpisodeTargets
    val radioActive = binder?.isRadioActive == true
    val canUseRadio = hasEpisodeTargets || radioActive
    val ltGuestLocked = rememberListenTogetherGuestLock()
    val favoriteMediaItems = remember(browseId, episodes) { episodes.map { it.asPodcastMediaItem(browseId) } }
    val playNext = PlayNext(enabled = hasEpisodeTargets && !ltGuestLocked && binder != null, dismissOnClick = false) {
        val player = binder?.player ?: return@PlayNext
        val items = targetMediaItems().ifEmpty { return@PlayNext }
        val completeSelection = itemSelector.captureSelectionCompletion()
        val menuKey = menuState.transitionKey
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            if (player.addNextOffMain(items, context)) {
                completeSelection()
                if (menuState.isCurrentMenu(menuKey)) menuState.hide()
            }
        }
    }
    val favorites = rememberCollectionFavoritesButton(
        mediaItems = favoriteMediaItems,
        kind = CollectionFavoritesKind.Podcast,
        enabled = !showShimmer && podcastLoadState.status == PodcastPageStatus.Content,
        collectionKey = browseId,
    )
    val addToPlaylist = PlaylistsMenu.deferred(
        navController = navController,
        captureMediaItems = {
            val items = targetMediaItems()
            if (items.isEmpty()) null else suspend { items }
        },
        onFailure = { error, _ -> Timber.tag("Podcast").e(error, "Could not add items to playlist") },
        itemSelector = itemSelector,
    )
    val offlineMode by OfflineMode.rememberEnabled()
    val canImportCollection = browseId.isNotBlank() && !forceShimmer
    val importStatus = when {
        !canImportCollection -> CollectionImportStatus.Unavailable
        podcastLoadState.status == PodcastPageStatus.Content -> CollectionImportStatus.Ready
        podcastLoadState.status == PodcastPageStatus.Empty -> CollectionImportStatus.Empty
        !offlineMode && initialLoading -> CollectionImportStatus.Loading
        else -> CollectionImportStatus.Unavailable
    }
    val importDialog = key(browseId) {
        ImportPlaylistDialog(
            initialValue = podcastTitle.orEmpty(),
            unavailableMessage = importStatus.unavailableMessage,
        ) { text ->
            val page = checkNotNull(podcastLoadState.page)
            val playlist = Playlist(name = text, browseId = browseId).withSourcePlaylistOwner(page.owner)
            val songs = page.listEpisode.map { it.asPodcastMediaItem(browseId).asSong }
            launchLoadedPlaylistImport(context, playlist, songs)
        }
    }
    importDialog.RenderAboveMenu(menuState)
    val addAction = rememberCollectionAddAction(
        sourceId = browseId,
        menuState = menuState,
        canImportCollection = importStatus.canOpenImport && !itemSelector.isActive,
        hasTracks = hasEpisodeTargets,
        importDialogOpen = importDialog.isActive,
        openImport = importDialog::open,
        openPicker = addToPlaylist::openPicker,
    )
    val addToolbarAction: @Composable () -> Unit = {
        HeaderIconButton(
            icon = addAction.iconId,
            contentDescription = addAction.menuIconTitle,
            enabled = addAction.isEnabled,
            color = if (addAction.isEnabled) colorPalette().text else colorPalette().textDisabled,
            modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
            onClick = addAction::onShortClick,
            onLongClick = { Toaster.i(R.string.playlist_action_add_hint) },
        )
    }
    val toolbarOrder = ToolbarSurface.PODCAST.visibleActions()
    val overflowActions = toolbarOrder.mapNotNull {
        when (it) {
            ToolbarControl.PLAY_NEXT -> playNext
            ToolbarControl.FAVORITES -> favorites
            ToolbarControl.SELECT -> itemSelector
            else -> null
        }
    }
    val currentOverflowActions by rememberUpdatedState(overflowActions)
    val moreActions = EllipsisMenuComponent.init { currentOverflowActions }
    val hasOverflowActions = overflowActions.isNotEmpty()
    val inlineActionIds = toolbarOrder.filterNot {
        it == ToolbarControl.BOOKMARK || it == ToolbarControl.PLAY_NEXT ||
            it == ToolbarControl.FAVORITES || it == ToolbarControl.SELECT
    } + listOfNotNull(ToolbarControl.MENU.takeIf { hasOverflowActions })

    LayoutWithAdaptiveThumbnail(
        thumbnailContent = thumbnailContent,
        thumbnailContentPadding = rememberPageChromeContentPadding(),
    ) {
        // Download state cache
        val downloadsMapState by MyDownloadHelper.downloads.collectAsStateWithLifecycle(initialValue = MyDownloadHelper.downloads.value, context = NzikDispatchers.DATA)
        val downloadedIds by remember {
            derivedStateOf {
                downloadsMapState.values
                    .filter { it.state == Download.STATE_COMPLETED }
                    .mapTo(HashSet()) { it.request.id }
            }
        }
        val downloadStatesMap by remember {
            derivedStateOf {
                podcastPage?.listEpisode.orEmpty().associate { episode ->
                    val songId = episode.asPodcastMediaItem(browseId).mediaId
                    songId to when {
                        songId in downloadedIds -> DownloadedStateMedia.DOWNLOADED
                        else -> DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .background(colorPalette().background0)
                .fillMaxHeight()
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (podcastPage == null && !initialLoading) {
                PodcastLoadingStatus(
                    status = podcastLoadState.status,
                    isOnline = isOnline,
                    onRetry = { retryAttempt++ },
                )
            } else {
                CompositionLocalProvider(LocalDownloadStatesMap provides downloadStatesMap) {
                LazyColumn(
                contentPadding = rememberPageChromeContentPadding(PaddingValues(bottom = Dimensions.bottomSpacer)),
                    state = lazyListState,

                    modifier = Modifier
                        .background(colorPalette().background0)
                        .fillMaxSize()
                ) {

                    item(
                        key = "header"
                    ) {
                    val titleStyle = typography().l.semiBold
                    val summaryStyle = typography().xs.medium
                    val episodeCount = podcastPage?.listEpisode.orEmpty().size
                    val episodeSummary = pluralStringResource(
                        R.plurals.podcast_episode_count_with_duration,
                        episodeCount, episodeCount, formatAsTime(totalPlayTimes),
                    )
                    val summary = listOfNotNull(
                        podcastAuthor?.let(::cleanPrefix)?.takeIf(String::isNotBlank), episodeSummary,
                    ).joinToString(" · ")
                    val artworkModifier = if (isLandscape) Modifier.fillMaxWidth()
                        else Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                    GlassArtworkBackdrop(hasArtwork = !isLandscape && !showShimmer) { artworkBackdrop ->
                        Box(artworkModifier) {
                            if (!isLandscape) {
                                val imageModifier = Modifier.then(artworkBackdrop).fillMaxWidth()
                                    .align(Alignment.Center)
                                    .fadingEdge(
                                        top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + Dimensions.fadeSpacingTop,
                                        bottom = Dimensions.fadeSpacingBottom,
                                    )
                                if (showShimmer) {
                                    ArtworkShimmerPlaceholder(imageModifier, aspectRatio = 4f / 3f)
                                } else {
                                    ImageCacheFactory.AsyncImage(
                                        shape = null, thumbnailUrl = podcastPage?.thumbnail?.firstOrNull()?.url?.resize(1000, 1000),
                                        contentDescription = stringResource(R.string.accessibility_loading),
                                        modifier = imageModifier,
                                    )
                                }
                            }
                            AutoResizeText(
                                text = podcastTitle ?: stringResource(R.string.podcast_title),
                                style = titleStyle,
                                fontSizeRange = FontSizeRange(32.sp, 38.sp),
                                fontWeight = titleStyle.fontWeight,
                                fontFamily = titleStyle.fontFamily,
                                color = titleStyle.color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                shimmer = showShimmer,
                                modifier = Modifier.align(Alignment.BottomCenter)
                                    .padding(horizontal = 30.dp).padding(bottom = 20.dp),
                            )
                            val summaryModifier = Modifier.align(Alignment.BottomCenter)
                            if (initialLoading) {
                                BasicText(
                                    podcastEpisodeLoadingLabel(podcastLoadState.loadedEpisodeCount),
                                    style = summaryStyle.copy(color = colorPalette().textSecondary),
                                    maxLines = 1,
                                    modifier = summaryModifier,
                                )
                            } else if (forceShimmer) {
                                MeasuredMetadataPlaceholder(summary, summaryStyle, summaryModifier, maxLines = 1)
                            } else {
                                BasicText(summary, style = summaryStyle, maxLines = 1, modifier = summaryModifier)
                            }
                            CollectionArtworkTopAction {
                                CollectionArtworkShareButton(
                                    ExternalUris.youtubeMusicPodcast(browseId),
                                )
                            }
                        }
                    }
                }

                item(
                    key = "actions",
                    contentType = 0
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                    ) {

                        if (podcastPage != null) {

                            val bookmarkAction: @Composable () -> Unit = {
                                PodcastBookmarkAction(
                                    browseId = browseId,
                                    title = podcastPage?.title.orEmpty(),
                                    episodes = podcastPage?.listEpisode.orEmpty()
                                        .map { it.asPodcastMediaItem(browseId) },
                                    owner = owner,
                                    onExistingImport = importDialog::onShortClick,
                                )
                            }

                            val toolbarActions = mapOf<ToolbarControl, @Composable () -> Unit>(
                                ToolbarControl.SEARCH to {
                                    HeaderIconButton(
                                        onClick = search::onShortClick,
                                        icon = R.drawable.search_circle,
                                        color = colorPalette().text,
                                        iconSize = 24.dp,
                                        modifier = Modifier
                                            .padding(horizontal = 5.dp)
                                    )

                                },
                                ToolbarControl.DOWNLOAD to {
                                    HeaderIconButton(
                                        icon = R.drawable.download,
                                        enabled = collectionActionEnabled,
                                        color = if (collectionActionEnabled) colorPalette().text else colorPalette().textDisabled,
                                        modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                                onClick = {
                                                    pendingDownloadSongs = targetMediaItems().toList()
                                                },
                                                onLongClick = {
                                                    Toaster.i( R.string.downloads_action_download_all )
                                                }
                                    )

                                    DialogPresence(visible = pendingDownloadSongs != null) {
                                        ConfirmationDialog(
                                            title = stringResource(R.string.downloads_action_download_all),
                                            confirmText = stringResource(R.string.downloads_title),
                                            text = stringResource(R.string.downloads_download_all_confirmation),
                                            onDismiss = { pendingDownloadSongs = null },
                                            dismissOnConfirm = false,
                                            onConfirm = {
                                                if (!isNetworkConnected(context)) {
                                                    Toaster.noInternet()
                                                    return@ConfirmationDialog
                                                }
                                                val episodes = pendingDownloadSongs.orEmpty()
                                                pendingDownloadSongs = null
                                                if (episodes.isNotEmpty()) {
                                                    downloadState = Download.STATE_DOWNLOADING
                                                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                                        for (mediaItem in episodes) {
                                                            if (!isNetworkConnected(context)) {
                                                                Toaster.noInternet()
                                                                break
                                                            }
                                                            binder?.cache?.removeResource(mediaItem.mediaId)
                                                            Database.asyncTransaction {
                                                                formatTable.findBySongId(mediaItem.mediaId)
                                                            }
                                                            manageDownload(
                                                                context = context,
                                                                mediaItem = mediaItem,
                                                                downloadState = false
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }

                                },
                                ToolbarControl.DELETE_DOWNLOADS to {
                                    HeaderIconButton(
                                        icon = R.drawable.downloaded,
                                        enabled = collectionActionEnabled,
                                        color = if (collectionActionEnabled) colorPalette().text else colorPalette().textDisabled,
                                        modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                                onClick = {
                                                    val downloadRequests = MyDownloadHelper.downloads.value
                                                    val targets = targetMediaItems()
                                                        .filter { it.mediaId in downloadRequests }
                                                        .distinctBy { it.mediaId }
                                                    if (targets.isEmpty()) Toaster.i(R.string.downloads_nothing_to_delete)
                                                    else pendingDeleteDownloads = targets
                                                },
                                                onLongClick = {
                                                    Toaster.i( R.string.downloads_action_remove_all )
                                                }
                                    )

                                    DialogPresence(visible = pendingDeleteDownloads != null) {
                                        ConfirmationDialog(
                                            title = stringResource(R.string.downloads_delete_dialog_title),
                                            confirmText = stringResource(R.string.common_action_delete),
                                            text = stringResource(
                                                R.string.downloads_delete_all_count_confirmation,
                                                pendingDeleteDownloads.orEmpty().size,
                                            ),
                                            onDismiss = { pendingDeleteDownloads = null },
                                            onConfirm = {
                                                val episodes = pendingDeleteDownloads.orEmpty()
                                                pendingDeleteDownloads = null
                                                NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                                    episodes.forEach { mediaItem ->
                                                        if (mediaItem.mediaId in MyDownloadHelper.downloads.value) {
                                                            MyDownloadHelper.removeDownload(context, mediaItem)
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }



                                },
                                ToolbarControl.ENQUEUE to {
                                    HeaderIconButton(
                                        icon = R.drawable.enqueue,
                                        enabled = hasEpisodeTargets,
                                        color =  if (hasEpisodeTargets) colorPalette().text else colorPalette().textDisabled,
                                        modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                                onClick = {
                                                    targetMediaItems().takeIf { it.isNotEmpty() }?.let { mediaItems ->
                                        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                                            val filtered = withContext(NzikDispatchers.DATA) {
                                                val player = binder?.player ?: return@withContext emptyList()
                                                player.excludeMediaItems(mediaItems, context)
                                            }
                                            val player = binder?.player ?: return@launch
                                            player.enqueue(filtered)
                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    Toaster.i( R.string.player_action_enqueue_songs )
                                                }
                                    )

                                },
                                ToolbarControl.SHUFFLE to {
                                    HeaderIconButton(
                                        // Issue #866 (gh-866): app-wide shuffle confirmation flash.
                                        icon = shuffleButtonIcon(),
                                        enabled = hasEpisodeTargets,
                                        color = if (hasEpisodeTargets) colorPalette().text else colorPalette().textDisabled,
                                        modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                                onClick = ::shuffleSelection,
                                                onLongClick = {
                                                    Toaster.i( R.string.player_action_shuffle_hint )
                                                }
                                    )

                                },
                                ToolbarControl.RADIO to {
                                    HeaderIconButton(
                                        // Issue #866 (gh-866): state icon, radio_stop while the radio
                                        // is active (the state change is the press feedback).
                                        icon = radioButtonIcon(),
                                        enabled = canUseRadio,
                                        color = if (!canUseRadio) colorPalette().textDisabled
                                                else if (radioActive) colorPalette().accent
                                                else colorPalette().text,
                                        modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                                onClick = {
                                                    if (binder?.stopActiveRadio() == true) return@HeaderIconButton
                                                    val episodes = targetMediaItems()
                                                    val first = episodes.firstOrNull() ?: return@HeaderIconButton
                                                    val currentId = binder?.player?.currentMediaItem?.mediaId
                                                    val seed = episodes.firstOrNull { it.mediaId == currentId } ?: first
                                                    binder?.startRadio(seed, false, null, true)
                                                },
                                                onLongClick = {
                                                    Toaster.i( binder?.radioActionTextRes ?: R.string.radio_action_start_hint )
                                                }
                                    )


                                },
                                ToolbarControl.ADD_TO_PLAYLIST to addToolbarAction,
                                ToolbarControl.MENU to {
                                    val menuLabel = stringResource(R.string.common_menu_label)
                                    HeaderIconButton(
                                        icon = moreActions.iconId,
                                        color = if (forceShimmer) colorPalette().textDisabled else colorPalette().text,
                                        enabled = !forceShimmer,
                                        modifier = Modifier.padding(horizontal = 5.dp),
                                        contentDescription = menuLabel,
                                        onClick = moreActions::onShortClick,
                                    )
                                },
                            )
                            CollectionToolbar(
                                bookmark = bookmarkAction,
                                actions = inlineActionIds.mapNotNull(toolbarActions::get),
                                trailingSeparator = hasOverflowActions,
                            )
                        } else {
                            val loadingActions = inlineActionIds.map<ToolbarControl, @Composable () -> Unit> { action ->
                                {
                                    if (action == ToolbarControl.ADD_TO_PLAYLIST) addToolbarAction()
                                    else DisabledPodcastAction(when (action) {
                                            ToolbarControl.SHUFFLE -> shuffleButtonIcon()
                                            ToolbarControl.RADIO -> radioButtonIcon()
                                            ToolbarControl.MENU -> moreActions.iconId
                                            else -> action.icon
                                        })
                                }
                            }
                            CollectionToolbar(
                                bookmark = { DisabledPodcastAction(R.drawable.bookmark_outline) },
                                actions = loadingActions,
                                trailingSeparator = hasOverflowActions,
                            )
                        }
                    }
                    Column { search.SearchBar(this, barPadding = PaddingValues(10.dp)) }
                }

                if (podcastLoadState.status == PodcastPageStatus.Empty) {
                    item(key = "empty-episodes", contentType = "empty-episodes") {
                        PodcastEmptyEpisodes()
                    }
                }

                val episodes = podcastPage?.listEpisode.orEmpty()
                if (initialLoading && episodes.isEmpty()) {
                    items(FullSongListPlaceholderCount, key = { "episode-loading-$it" }) {
                        SongItemPlaceholder()
                    }
                }
                itemsIndexed(items = visibleEpisodes, key = { _, (key, _, _) -> key }) { _, (_, sourceIndex, song) ->
                    if (showShimmer) {
                        SongItemPlaceholder(title = song.title, artist = song.author)
                        return@itemsIndexed
                    }
                    val episodeMedia = remember(song, browseId) { song.asPodcastMediaItem(browseId) }
                    val isLocal = episodeMedia.isLocal
                    downloadState = getDownloadState(episodeMedia.mediaId)
                    val isDownloaded = if (!isLocal) isDownloadedSong(episodeMedia.mediaId) else true

                    SwipeablePlaylistItem(
                        mediaItem = episodeMedia,
                        modifier = Modifier.animateItem(),
                        onPlayNext = {
                            binder?.player?.addNext(episodeMedia)
                        },
                        onDownload = {
                            binder?.cache?.removeResource(episodeMedia.mediaId)
                            Database.asyncTransaction {
                                formatTable.updateContentLengthOf( episodeMedia.mediaId )
                            }

                            if (!isLocal)
                                manageDownload(
                                    context = context,
                                    mediaItem = episodeMedia,
                                    downloadState = isDownloaded
                                )
                        },
                        onEnqueue = {
                            binder?.player?.enqueue(episodeMedia)
                        }
                    ) {
                        val episode = resolveSongDisplayMetadata(
                            episodeMedia.asSong, storedEpisodes[collectionFavoriteSongId(song.videoId)],
                        )
                        SongItem(
                            song = episode,
                            observeStoredMetadata = false,
                            artistCredits = episodeCredits[episode.id].orEmpty(),
                            sourceMediaItem = { episodeMedia },
                            itemSelector = itemSelector,
                            navController = navController,
                            modifier = Modifier
                                .background(colorPalette().background0)
                                .clip(uiRoundnessShape()),
                            onLongClick = {
                                menuState.display {
                                    SongItemMenu(
                                        navController = navController,
                                        song = episode,
                                        podcastBrowseId = browseId,
                                        sourceMediaItem = { episodeMedia },
                                    ).MenuComponent()
                                }
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onClick = {
                                if (itemSelector.isActive) {
                                    itemSelector.toggle(episode, Song::id)
                                } else {
                                    search.clearAndClose()
                                    podcastPage?.listEpisode?.map { it.asPodcastMediaItem(browseId) }
                                        ?.let { mediaItems ->
                                            binder?.stopRadio()
                                            binder?.player?.forcePlayAtIndex(mediaItems, sourceIndex)
                                        }
                                }
                            }
                        )
                    }
                }
            }
            } // CompositionLocalProvider

            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
            if( showFloatingIcon )
            FloatingActionsContainerWithScrollToTop(
                lazyListState = lazyListState,
                // Issue #866 (gh-866): app-wide shuffle confirmation flash.
                iconId = shuffleButtonIcon(),
                actionEnabled = !itemSelector.isActive || hasEpisodeTargets,
                onClick = ::shuffleSelection
            )
            }
        }
    }
}

@Composable
private fun DisabledPodcastAction(icon: Int) {
    HeaderIconButton(
        icon = icon,
        color = colorPalette().textDisabled,
        enabled = false,
        iconSize = if (icon == R.drawable.search_circle) 24.dp else 20.dp,
        modifier = Modifier.padding(horizontal = 5.dp),
        onClick = {},
    )
}
