// Adapted from RiPlay (GPL-3.0, license checked at upstream revision f69d6760bd09b29e18591cdc1339d3dd8b6bdf55).
package app.n_zik.android.components.ui.screens.album

import androidx.compose.animation.animateContentSize
import android.net.Uri

import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.components.settings.appearance.preview.ThemePreviewDemo
import app.n_zik.android.appTextResources
import app.n_zik.android.components.ui.screens.CollectionArtworkHeader
import app.n_zik.android.components.ui.screens.CollectionPageStatus
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.n_zik.android.compose.persist.PersistMapCleanup
import app.n_zik.android.compose.persist.persist
import app.n_zik.android.compose.persist.persistList
import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.cleanPrefix
import app.n_zik.android.enums.Languages
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.DownloadedStateMedia
import app.n_zik.android.enums.PlaylistSwipeAction
import app.n_zik.android.enums.PlayerPosition
import app.n_zik.android.enums.TransitionEffect
import app.n_zik.android.models.Album
import app.n_zik.android.models.Song
import app.n_zik.android.models.SongAlbumMap
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.navigation.header.TabToolBar
import app.n_zik.android.ui.components.themed.Enqueue
import app.n_zik.android.components.ui.screens.CollectionArtworkShareButton
import app.n_zik.android.components.ui.CollectionButtonToolbar
import app.n_zik.android.components.settings.ui.ToolbarAction as ToolbarControl
import app.n_zik.android.components.settings.ui.ToolbarSurface
import app.n_zik.android.components.settings.ui.ordered
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.components.ui.CollectionFavoritesKind
import app.n_zik.android.components.ui.rememberCollectionFavoritesButton
import app.n_zik.android.ui.components.themed.ItemsList
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.ui.components.themed.MeasuredMetadataPlaceholder
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.items.SongItemPlaceholder
import app.n_zik.android.ui.items.SongItemPlaceholders
import app.n_zik.android.ui.components.themed.MultiFloatingActionsContainer
import app.n_zik.android.ui.components.themed.PlayNext
import app.n_zik.android.ui.components.themed.PlaylistsMenu
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.ui.items.AlbumItem
import app.n_zik.android.ui.items.AlbumItemPlaceholder
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.px
import app.n_zik.android.utils.addNext
import app.n_zik.android.utils.align
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.withPlaybackAlbum
import app.n_zik.android.utils.center
import app.n_zik.android.utils.color
import app.n_zik.android.utils.conditional
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.durationTextToMillis
import app.n_zik.android.utils.fadingEdge
import app.n_zik.android.utils.forcePlayAtIndex
import app.n_zik.android.utils.formatAsTime
import app.n_zik.android.utils.isLandscape
import app.n_zik.android.utils.medium
import app.n_zik.android.utils.otherLanguageAppAlbumKey
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.core.database.artistEntryNames
import app.n_zik.android.utils.playerPositionKey
import app.n_zik.android.utils.playlistSwipeLeftActionKey
import app.n_zik.android.utils.playlistSwipeRightActionKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.showFloatingIconKey
import app.n_zik.android.utils.transitionEffectKey
import app.n_zik.android.core.utils.PropUtils
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.LocalDownloadStatesMap
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.shuffleButtonIcon
import app.n_zik.android.components.album.AlbumModifier
import app.n_zik.android.components.menu.album.toAlbumCreditArtists
import app.n_zik.android.components.dialog.album.ChangeAlbumCoverDialog
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.n_zik.android.components.musicbrainz.InfoAndCommunity
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.Locator
import app.n_zik.android.components.tab.Radio
import app.n_zik.android.components.tab.Search

import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.matchesCollectionSearch
import app.n_zik.android.components.tab.SongShuffler
import app.n_zik.android.components.ui.screens.DynamicOrientationLayout
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.BookmarkStateManager
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.extensions.musicbrainz.MBMetadataHelper
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassFloatingActions
import app.n_zik.android.components.glass.glassFloatingActionContent
import app.n_zik.android.components.glass.rememberGlassFloatingActionBackdrop
import app.n_zik.android.typography
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YtMusic
import app.n_zik.android.utils.contentTranslationChoices
import app.n_zik.android.utils.contentTranslationLanguage
import app.n_zik.android.utils.contentTranslationTarget
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.player.addNextOffMain
import app.n_zik.android.utils.player.enqueueOffMain
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.ui.components.SwipeablePlaylistItem
import timber.log.Timber

private const val ALBUM_INSIGHTS_ROUTE = "albumInsights"
private const val ALBUM_SONGS_HEADER_ITEMS = 6

@Composable
internal fun OnlineAlbumToolbar(
    bookmark: @Composable () -> Unit,
    actions: Map<ToolbarControl, Button>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    CollectionButtonToolbar(
        bookmark = bookmark,
        buttons = ToolbarSurface.ALBUM_ONLINE.ordered(actions),
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
internal fun AlbumSongsPageStatus(loadedSongsCount: Int, loading: Boolean, loadFailed: Boolean) {
    CollectionPageStatus {
        if (loading) {
            Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                SongItemPlaceholders(count = 4, showThumbnail = false)
                if (loadedSongsCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        text = pluralStringResource(R.plurals.album_loading_song_count, loadedSongsCount, loadedSongsCount),
                        style = typography().xxs.copy(color = colorPalette().textDisabled),
                    )
                }
            }
        } else {
            val isOnline by NetworkQualityHelper.isNetworkAvailableComposable(LocalContext.current)
            val offlineMode by OfflineMode.rememberEnabled()
            BasicText(
                text = stringResource(when {
                    !loadFailed -> R.string.common_empty_items
                    offlineMode || !isOnline -> R.string.common_load_failed_no_connection
                    else -> R.string.common_load_failed
                }),
                style = typography().s.copy(color = colorPalette().textSecondary),
            )
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@ExperimentalTextApi
@Composable
fun AlbumScreen(
    navController: NavController,
    browseId: String,
    modifier: Modifier = Modifier,
    miniPlayer: @Composable () -> Unit = {}
) {
    PersistMapCleanup(tagPrefix = "album/$browseId/")

    val saveableStateHolder = rememberSaveableStateHolder()

    val transitionEffect by rememberPreference(transitionEffectKey, TransitionEffect.Fade)
    val playerPosition by rememberPreference(playerPositionKey, PlayerPosition.Bottom)

    val preview = LocalThemePreview.current
    var storedAlbum by persist<Album?>("album/$browseId")
    val album = if (preview != null) ThemePreviewDemo.albums.firstOrNull { it.id == browseId } else storedAlbum
    var mbSyncing by remember { mutableStateOf(false) }
    var mbPausedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        if (preview != null) return@LaunchedEffect
        Database.albumTable
            .findById(browseId)
            .flowOn(NzikDispatchers.DATA)
            .distinctUntilChanged()
            .collect { storedAlbum = it }
    }

    var alternatives by persistList<Innertube.AlbumItem>("album/$browseId/alternatives")
    var description by rememberSaveable { mutableStateOf("") }
    var albumArtistId by rememberSaveable(browseId) { mutableStateOf<String?>(null) }
    var loadedSongsCount by remember(browseId) { mutableIntStateOf(0) }
    var songLoadResult by remember(browseId, preview) { mutableStateOf<Result<Boolean>?>(null) }
    LaunchedEffect(browseId, preview) {
        if (preview != null) return@LaunchedEffect
        val cleanedBrowseId = browseId.removePrefix(MODIFIED_PREFIX)

        songLoadResult = withContext(NzikDispatchers.DATA) {
            YtMusic.getAlbum(cleanedBrowseId, true, onProgress = { count ->
                launch(NzikDispatchers.UI) { loadedSongsCount = count }
            })
        }
            .mapCatching { online ->
                val onlineAlbum = online.album
                val authorsText = onlineAlbum.authors.artistEntryNames().joinToString(", ").takeIf(String::isNotBlank)
                val canonicalArtistId = onlineAlbum.authors.toAlbumCreditArtists().singleOrNull()?.id

                Database.awaitWriteBatch {
                    transaction {
                        val now = System.currentTimeMillis()
                        val inserted = albumTable.insertMetadata(
                            id = browseId,
                            title = onlineAlbum.title,
                            thumbnailUrl = onlineAlbum.thumbnail?.url,
                            year = onlineAlbum.year,
                            authorsText = authorsText,
                            shareUrl = online.url,
                            timestamp = now,
                            isYoutubeAlbum = false,
                            position = -1,
                            lastFetch = now
                        )
                        if (inserted == -1L) {
                            val current = albumTable.findByIdDirect(browseId)
                            albumTable.updateMetadata(
                                id = browseId,
                                title = PropUtils.retainIfModified(current?.title, onlineAlbum.title),
                                thumbnailUrl = PropUtils.retainIfModified(current?.thumbnailUrl, onlineAlbum.thumbnail?.url),
                                year = onlineAlbum.year ?: current?.year,
                                authorsText = PropUtils.retainIfModified(current?.authorsText, authorsText ?: current?.authorsText),
                                shareUrl = online.url,
                                isYoutubeAlbum = current?.isYoutubeAlbum == true,
                                position = current?.position ?: -1,
                                lastFetch = now
                            )
                        }

                        songAlbumMapTable.clear(browseId)

                        online.songs
                            .map(Innertube.SongItem::asMediaItem)
                            .onEach { insertIgnore(it) }
                            .mapIndexed { position, mediaItem ->
                                SongAlbumMap(
                                    songId = mediaItem.mediaId,
                                    albumId = browseId,
                                    position = position
                                )
                            }
                            .also { songAlbumMapTable.upsert(it) }

                        // Store the YouTube description so the Insights page can reuse it
                        // as a bio fallback without refetching the album page.
                        val ytDescription = online.description?.takeIf { it.isNotBlank() }
                        if (ytDescription != null) {
                            albumTable.findByIdDirect(browseId)?.let { current ->
                                if (current.description != ytDescription) {
                                    albumTable.updateReplace(current.copy(description = ytDescription))
                                }
                            }
                        }
                    }
                }

                albumArtistId = canonicalArtistId
                alternatives = online.otherVersions
                description = online.description ?: ""
                online.songs.isNotEmpty()
            }
            .onFailure { error ->
                if (error is CancellationException) throw error
                Timber.tag("AlbumScreen").w(error, "Unable to load album tracks")
            }
    }

    LaunchedEffect(album?.id) {
        if (preview != null) return@LaunchedEffect
        val albumEntity = album ?: return@LaunchedEffect
        mbSyncing = true
        try {
            withContext(NzikDispatchers.DATA) {
                val albumId = browseId.removePrefix(MODIFIED_PREFIX)
                Database.transaction {
                    val current = albumTable.findByIdDirect(albumEntity.id)
                    if (current != null && current.youtubeAlbumId != albumId) {
                        albumTable.updateReplace(current.copy(youtubeAlbumId = albumId))
                    }
                }
                MBMetadataHelper.Default.onAlbumViewed(albumEntity.id)
            }
        } finally {
            mbSyncing = false
            mbPausedSeconds = MBMetadataHelper.Default.circuitOpenRemainingSeconds()
        }
    }

    val thumbnailPainter = ImageCacheFactory.Painter(album?.thumbnailUrl)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorPalette().background0)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            val topPadding = 0.dp

            AnimatedContent(
                targetState = 0,
                transitionSpec = {
                    when (transitionEffect) {
                        TransitionEffect.None ->
                            EnterTransition.None togetherWith ExitTransition.None

                        TransitionEffect.Expand ->
                            expandIn(
                                animationSpec = tween(
                                    350,
                                    easing = LinearOutSlowInEasing
                                ),
                                expandFrom = Alignment.BottomStart
                            ).togetherWith(
                                shrinkOut(
                                    animationSpec = tween(
                                        350,
                                        easing = FastOutSlowInEasing
                                    ),
                                    shrinkTowards = Alignment.CenterStart
                                )
                            )

                        TransitionEffect.Fade ->
                            fadeIn(
                                animationSpec = tween(350)
                            ).togetherWith(
                                fadeOut(
                                    animationSpec = tween(350)
                                )
                            )

                        TransitionEffect.Scale ->
                            scaleIn(
                                animationSpec = tween(350)
                            ).togetherWith(
                                scaleOut(
                                    animationSpec = tween(350)
                                )
                            )

                        TransitionEffect.SlideHorizontal,
                        TransitionEffect.SlideVertical -> {
                            val slideDirection = if (targetState > initialState) {
                                if (transitionEffect == TransitionEffect.SlideHorizontal)
                                    AnimatedContentTransitionScope.SlideDirection.Left
                                else
                                    AnimatedContentTransitionScope.SlideDirection.Up
                            } else {
                                if (transitionEffect == TransitionEffect.SlideHorizontal)
                                    AnimatedContentTransitionScope.SlideDirection.Right
                                else
                                    AnimatedContentTransitionScope.SlideDirection.Down
                            }

                            val animationSpec = spring(
                                dampingRatio = 0.9f,
                                stiffness = Spring.StiffnessLow,
                                visibilityThreshold = IntOffset.VisibilityThreshold
                            )

                            slideIntoContainer(slideDirection, animationSpec) togetherWith
                                    slideOutOfContainer(slideDirection, animationSpec)
                        }
                    }
                },
                label = "album",
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(top = topPadding)
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    AlbumDetails(
                        navController = navController,
                        browseId = browseId,
                        album = album,
                        thumbnailPainter = thumbnailPainter,
                        alternatives = alternatives,
                        description = description,
                        loadedSongsCount = loadedSongsCount,
                        albumArtistId = albumArtistId,
                        songsLoading = preview == null && songLoadResult == null,
                        songsLoadFailed = songLoadResult?.isFailure == true,
                        expectsTracks = songLoadResult?.getOrNull() == true,
                        mbSyncing = mbSyncing,
                        onMbSyncingChange = { mbSyncing = it },
                        mbPausedSeconds = mbPausedSeconds,
                        onMbPausedSecondsChange = { mbPausedSeconds = it },
                        onSearchClick = {
                            navController.navigate(NavRoutes.search.name)
                        },
                        onSettingsClick = {
                            navController.navigate(NavRoutes.settings.name)
                        }
                    )
                }
            }
        }

        Box(
            modifier = modifier
                .padding(vertical = 5.dp)
                .align(if (playerPosition == PlayerPosition.Top) Alignment.TopCenter else Alignment.BottomCenter)
        ) {
            miniPlayer.invoke()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@UnstableApi
@Composable
fun AlbumDetails(
    navController: NavController,
    browseId: String,
    album: Album?,
    thumbnailPainter: Painter,
    alternatives: List<Innertube.AlbumItem>,
    description: String,
    loadedSongsCount: Int = 0,
    mbSyncing: Boolean = false,
    onMbSyncingChange: (Boolean) -> Unit = {},
    mbPausedSeconds: Long = 0,
    onMbPausedSecondsChange: (Long) -> Unit = {},
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    songsLoading: Boolean = false,
    songsLoadFailed: Boolean = false,
    expectsTracks: Boolean = false,
    albumArtistId: String? = null,
) {
    PersistMapCleanup("album/$browseId/songs")

    val binder = LocalPlayerServiceBinder.current
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current

    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val preview = LocalThemePreview.current
    val rawItems by remember(browseId, preview) {
        if (preview != null) kotlinx.coroutines.flow.flowOf(ThemePreviewDemo.songsForAlbum(browseId))
        else Database.songAlbumMapTable
            .allSongsOf(browseId)
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle<List<Song>?>(initialValue = null, context = NzikDispatchers.DATA)

    val items = remember(rawItems, parentalControlEnabled) {
        rawItems.orEmpty().filter { !parentalControlEnabled || it.title.startsWith(EXPLICIT_PREFIX, true) != true }
    }
    val forceSongPlaceholders = forceLoadingShimmerPreview()
    val songsPublished = !rawItems.isNullOrEmpty()
    var hasPublishedSongs by remember(browseId, preview) { mutableStateOf(false) }
    LaunchedEffect(songsPublished) {
        if (songsPublished) hasPublishedSongs = true
    }
    // Room may publish the first tracks after the fetch completes.
    val initialSongsLoading = rawItems == null ||
        (!songsPublished && !hasPublishedSongs && (songsLoading || expectsTracks))

    val itemSelector = ItemSelector<Song>()
    val search = Search.detail()
    val query = search.filterQuery
    val visibleTracks = remember(items, query) {
        items.withIndex().filter { (_, song) -> query.isBlank() || song.matchesCollectionSearch(query) }
    }
    val visibleSongs = remember(visibleTracks) { visibleTracks.map { it.value } }
    val visibleAlternatives = remember(alternatives, query) {
        if (query.isBlank()) alternatives else alternatives.filter { it.matchesCollectionSearch(query) }
    }
    SearchEmptyFeedback(
        query = query,
        empty = visibleTracks.isEmpty() && visibleAlternatives.isEmpty(),
        loading = songsLoading || initialSongsLoading || forceSongPlaceholders,
    )

    fun getSongs() = itemSelector.actionTargets(visibleSongs, Song::id, Song::id, selectionOrder = true)
    fun hasSelection() = getSongs().isNotEmpty()
    fun albumMediaItem(song: Song) = song.asMediaItem.withPlaybackAlbum(browseId, album?.cleanTitle())
    fun getMediaItems() = getSongs().map(::albumMediaItem)

    val bookmark = AlbumBookmark(browseId)
    val albumSongIds = remember(items) { items.map(Song::id) }
    val likeStatesMap by remember(albumSongIds, preview) {
        if (preview != null) kotlinx.coroutines.flow.flowOf(emptyMap()) else LikeStateManager.getLikeStates(albumSongIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
    val favoriteMediaItems = itemSelector.actionTargets(visibleSongs, Song::id, Song::id).map(Song::asMediaItem)
    val favorites = rememberCollectionFavoritesButton(
        mediaItems = favoriteMediaItems,
        kind = CollectionFavoritesKind.Album,
        enabled = !songsLoading && !songsLoadFailed && !forceSongPlaceholders && preview == null,
        collectionKey = browseId,
        likeStates = likeStatesMap,
    )

    val deleteAllDownloadsDialog = DeleteAllDownloadedSongsDialog(::getSongs)
    val downloadALlDialog = DownloadAllSongsDialog(::getSongs)
    val shuffle = SongShuffler.fromMediaItems(::getMediaItems)
    val radio = Radio.fromMediaItem { getSongs().randomOrNull()?.let(::albumMediaItem) }
    val locator = Locator(lazyListState, listItemOffset = ALBUM_SONGS_HEADER_ITEMS) { visibleSongs }
    val playNext = PlayNext {
        val songsToAdd = getSongs().ifEmpty { return@PlayNext }
        itemSelector.isActive = false
        coroutineScope.launch {
            val mediaItems = withContext(NzikDispatchers.DATA) { songsToAdd.map(::albumMediaItem) }
            binder?.player?.addNextOffMain(mediaItems, appContext())
        }
    }
    val enqueue = Enqueue {
        val songsToAdd = getSongs().ifEmpty { return@Enqueue }
        itemSelector.isActive = false
        coroutineScope.launch {
            val mediaItems = withContext(NzikDispatchers.DATA) { songsToAdd.map(::albumMediaItem) }
            binder?.player?.enqueueOffMain(mediaItems, appContext())
        }
    }
    val addToPlaylist = PlaylistsMenu.deferred(
        navController = navController,
        captureMediaItems = {
            val songs = getSongs()
            if (itemSelector.isActive && songs.isEmpty()) null else ({ songs.map(Song::asMediaItem) })
        },
        onFailure = { throwable, preview ->
            Timber.tag("AlbumDetails").e(throwable, "Failed to add songs to playlist ${preview.playlist.name}")
        },
        itemSelector = itemSelector,
    )
    val changeTitle = AlbumModifier(
        iconId = R.drawable.title_edit,
        messageId = R.string.metadata_action_change_title,
        getDefaultValue = { album?.cleanTitle() ?: "" },
    ) {
        updateTitle(browseId, "$MODIFIED_PREFIX$it")
    }
    val changeAuthors = AlbumModifier(
        iconId = R.drawable.artists_edit,
        messageId = R.string.metadata_action_change_artists,
        getDefaultValue = { album?.cleanAuthorsText() ?: "" },
    ) {
        updateAuthors(browseId, "$MODIFIED_PREFIX$it")
    }
    val changeCover = ChangeAlbumCoverDialog.forAlbumDetails { album }
    var showTranslateLanguageDialog by remember { mutableStateOf(false) }
    val translate = Translate.init(onLongClick = { showTranslateLanguageDialog = true })
    val translator = Translator(NetworkClientFactory.getTranslatorClient())
    var otherLanguageApp by rememberPreference(otherLanguageAppAlbumKey, Languages.System)
    val appLangAlbum = Locale.getDefault()
    val activeTranslateLang = remember(otherLanguageApp, appLangAlbum) {
        if (otherLanguageApp != Languages.System) otherLanguageApp
        else contentTranslationLanguage(appLangAlbum)
    }
    val languageDestination = activeTranslateLang.contentTranslationTarget

    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailAlbumSizeDp = Dimensions.thumbnails.album

    val thumbnailAlbumSizePx = thumbnailAlbumSizeDp.px

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)

    downloadALlDialog.Render()
    deleteAllDownloadsDialog.Render()
    changeTitle.Render()
    changeAuthors.Render()
    changeCover.Render()

    DialogPresence(visible = showTranslateLanguageDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.common_translation_title),
            selectedValue = otherLanguageApp,
            onValueSelected = {
                otherLanguageApp = it
                translate.isActive = it.translatorLanguage != Language.ENGLISH
                showTranslateLanguageDialog = false
            },
            valueText = { it.text },
            values = contentTranslationChoices,
            onDismiss = { showTranslateLanguageDialog = false }
        )
    }

    val floatingBackdrop = rememberGlassFloatingActionBackdrop()

    DynamicOrientationLayout(
        thumbnailPainter,
        thumbnailContentPadding = rememberPageChromeContentPadding(),
        isLoading = initialSongsLoading,
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(colorPalette().background0),
            contentAlignment = Alignment.Center
        ) {
            if (items.isEmpty() && !initialSongsLoading) {
                AlbumSongsPageStatus(loadedSongsCount, forceSongPlaceholders, songsLoadFailed)
            } else {
                val playlistStatesMap by remember(albumSongIds) {
                    if (preview != null) kotlinx.coroutines.flow.flowOf(emptyMap()) else PlaylistStateManager.getPlaylistStates(albumSongIds)
                }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

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
                        items.associate { song ->
                            song.id to if (song.id in downloadedIds) DownloadedStateMedia.DOWNLOADED else DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
                        }
                    }
                }

                val playlistSwipeLeftAction by rememberPreference(playlistSwipeLeftActionKey, PlaylistSwipeAction.Favourite)
                val playlistSwipeRightAction by rememberPreference(playlistSwipeRightActionKey, PlaylistSwipeAction.PlayNext)

                CompositionLocalProvider(LocalDownloadStatesMap provides downloadStatesMap) {
                    LazyColumn(
                        state = lazyListState,
                        userScrollEnabled = items.isNotEmpty(),
                        contentPadding = rememberPageChromeContentPadding(PaddingValues(bottom = Dimensions.bottomSpacer)),
                        modifier = Modifier.fillMaxSize()
                            .glassFloatingActionContent(floatingBackdrop)
                            .background(colorPalette().background0)
                    ) {
                        item("header") {
                            CollectionArtworkHeader(
                                album?.title ?: "...",
                                thumbnailPainter,
                                disableScrollingText,
                                isLoading = initialSongsLoading,
                                fullWidthTitleShimmer = true,
                            ) {
                                CollectionArtworkShareButton(album?.shareUrl)
                            }
                        }

                        item("album_details") {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val totalDuration = formatAsTime(items.sumOf {
                                    durationTextToMillis(it.durationText ?: "")
                                })

                                val summary = pluralStringResource(
                                    R.plurals.library_song_count_with_duration, items.size, items.size, totalDuration,
                                )
                                val style = typography().xs.medium
                                if (initialSongsLoading) BasicText(
                                    if (loadedSongsCount > 0) {
                                        pluralStringResource(R.plurals.album_loading_song_count, loadedSongsCount, loadedSongsCount)
                                    } else stringResource(R.string.common_loading_message),
                                    style = style.copy(color = colorPalette().textSecondary),
                                    maxLines = 1,
                                )
                                else if (forceSongPlaceholders) MeasuredMetadataPlaceholder(summary, style, maxLines = 1)
                                else BasicText(summary, style = style, maxLines = 1)
                            }
                        }

                        item("action_buttons") {
                            val actions = mapOf(
                                ToolbarControl.SEARCH to search,
                                ToolbarControl.DOWNLOAD to itemSelector.guard(downloadALlDialog, ::hasSelection),
                                ToolbarControl.DELETE_DOWNLOADS to itemSelector.guard(deleteAllDownloadsDialog, ::hasSelection),
                                ToolbarControl.SHUFFLE to itemSelector.guard(shuffle, ::hasSelection),
                                ToolbarControl.RADIO to itemSelector.guard(radio) { radio.active || hasSelection() },
                                ToolbarControl.LOCATE to locator,
                                ToolbarControl.SELECT to itemSelector,
                                ToolbarControl.CHANGE_TITLE to changeTitle,
                                ToolbarControl.CHANGE_ARTISTS to changeAuthors,
                                ToolbarControl.CHANGE_COVER to changeCover,
                                ToolbarControl.PLAY_NEXT to itemSelector.guard(playNext, ::hasSelection),
                                ToolbarControl.ENQUEUE to itemSelector.guard(enqueue, ::hasSelection),
                                ToolbarControl.ADD_TO_PLAYLIST to itemSelector.guard(addToPlaylist, ::hasSelection),
                                ToolbarControl.FAVORITES to favorites,
                            )
                            OnlineAlbumToolbar(
                                modifier = Modifier.padding(top = 8.dp),
                                bookmark = {
                                    if (initialSongsLoading && album == null) TabToolBar.Icon(
                                        icon = bookmark.icon,
                                        tint = colorPalette().textDisabled,
                                        enabled = false,
                                        onClick = {},
                                    )
                                    else bookmark.ToolBarButton()
                                },
                                actions = actions,
                                enabled = !initialSongsLoading,
                            )
                        }

                        item("search") {
                            Column { search.SearchBar(this, barPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) }
                        }

                        if (query.isBlank()) item("info_and_community") {
                            InfoAndCommunity(
                                modifier = Modifier.animateItem(placementSpec = null).animateContentSize(),
                                rating = album?.rating,
                                ratingVotes = album?.ratingVotes,
                                year = album?.year,
                                countryCode = null,
                                keywords = album?.keywords,
                                links = album?.links,
                                bio = album?.wikipediaInfo ?: album?.description ?: description,
                                translate = translate,
                                translator = translator,
                                languageDestination = languageDestination,
                                lastSyncAt = album?.mbLastFetch,
                                lastSyncFailed = album?.mbLastFetch != null && album?.genres == null,
                                pausedForSeconds = mbPausedSeconds,
                                isSyncing = mbSyncing,
                                isLoading = initialSongsLoading,
                                onResyncClick = {
                                    album?.id?.let { id ->
                                        coroutineScope.launch(NzikDispatchers.DATA) {
                                            onMbSyncingChange(true)
                                            try {
                                                val success = MBMetadataHelper.Default.onAlbumViewed(id, force = true)
                                                val paused = MBMetadataHelper.Default.circuitOpenRemainingSeconds()
                                                onMbPausedSecondsChange(paused)
                                                when {
                                                    success -> Toaster.s(R.string.metadata_musicbrainz_resync_success)
                                                    paused > 0 -> Toaster.e(
                                                        appTextResources().getString(R.string.metadata_musicbrainz_resync_paused, (paused / 60).coerceAtLeast(1))
                                                    )
                                                    else -> Toaster.e(R.string.metadata_musicbrainz_resync_error)
                                                }
                                            } finally {
                                                onMbSyncingChange(false)
                                            }
                                        }
                                    }
                                },
                                onInsightsClick = {
                                    val path = "$ALBUM_INSIGHTS_ROUTE/${Uri.encode(browseId)}"
                                    val artistId = albumArtistId?.takeUnless {
                                        album?.authorsText?.startsWith(MODIFIED_PREFIX, true) == true
                                    }
                                    navController.navigate(
                                        if (artistId != null) "$path?artistId=${Uri.encode(artistId)}" else path
                                    )
                                }
                            )
                        }

                        if (query.isBlank() || visibleTracks.isNotEmpty() || initialSongsLoading) item("songsTitle") {
                            BasicText(
                                text = stringResource(R.string.library_songs_title),
                                style = typography().m.semiBold.align(TextAlign.Start),
                                modifier = sectionTextModifier.fillMaxWidth().animateItem()
                            )
                        }

                        if (initialSongsLoading) item("album-songs-preview") {
                            SongItemPlaceholders(count = 4, showThumbnail = false)
                        } else itemsIndexed(
                            items = visibleTracks,
                            key = { _, track -> track.value.id }
                        ) { visibleIndex, (index, song) ->
                            if (forceSongPlaceholders) {
                                SongItemPlaceholder(
                                    trackNumber = index + 1,
                                    title = song.cleanTitle(),
                                    artist = song.cleanArtistsText(),
                                )
                                return@itemsIndexed
                            }

                            SwipeablePlaylistItem(
                                mediaItem = albumMediaItem(song),
                                modifier = Modifier.animateItem(),
                                onPlayNext = {
                                    binder?.player?.addNext(albumMediaItem(song))
                                },
                                downloadStateParam = downloadsMapState[song.id]?.state ?: Download.STATE_STOPPED,
                                downloadedStateMediaParam = downloadStatesMap[song.id] ?: DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED,
                                swipeLeftActionParam = playlistSwipeLeftAction,
                                swipeRightActionParam = playlistSwipeRightAction
                            ) {
                                SongItem(
                                    song = song,
                                    isLiked = likeStatesMap[song.id],
                                    inPlaylist = playlistStatesMap[song.id],
                                    itemSelector = itemSelector,
                                    navController = navController,
                                    playbackAlbumId = browseId,
                                    playbackAlbumTitle = album?.cleanTitle(),
                                    showThumbnail = false,

                                    thumbnailOverlay = {
                                        BasicText(
                                            text = "${index + 1}",
                                            style = typography().s
                                                .semiBold
                                                .center
                                                .color(
                                                    colorPalette().textDisabled
                                                ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .width(thumbnailSizeDp)
                                                .align(Alignment.Center)
                                        )
                                    },
                                    onClick = {
                                        binder?.stopRadio()
                                        binder?.player?.forcePlayAtIndex(
                                            visibleSongs.map(::albumMediaItem),
                                            visibleIndex,
                                        )
                                    }
                                )
                            }
                        }

                        if (visibleAlternatives.isNotEmpty())
                            item("alternatives") {
                                BasicText(
                                    text = stringResource(R.string.album_alternative_versions),
                                    style = typography().m.semiBold,
                                    maxLines = 1,
                                    modifier = Modifier.animateItem().padding(all = 16.dp)
                                )

                                val altAlbumKeys = remember(visibleAlternatives) { visibleAlternatives.map { it.key } }
                                val altBookmarkStatesMap by remember(altAlbumKeys) {
                                    BookmarkStateManager.getAlbumBookmarkStates(altAlbumKeys)
                                }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

                                ItemsList(
                                    tag = "album/$browseId/alternatives_list",
                                    modifier = Modifier.animateItem(),
                                    headerContent = {},
                                    initialPlaceholderCount = visibleAlternatives.size,
                                    continuationPlaceholderCount = 1,
                                    emptyItemsText = stringResource(R.string.album_no_alternative_version),
                                    knownItems = visibleAlternatives,
                                    itemContent = { album ->
                                        AlbumItem(
                                            alternative = true,
                                            album = album,
                                            thumbnailSizePx = thumbnailAlbumSizePx,
                                            thumbnailSizeDp = thumbnailAlbumSizeDp,
                                            bookmarkState = altBookmarkStatesMap[album.key],
                                            observeBookmarkState = false,
                                            modifier = Modifier
                                                .animateItem()
                                                .clip(uiRoundnessShape()).combinedClickable(
                                                    onClick = {
                                                        navController.navigate(route = "${NavRoutes.album.name}/${album.key}")
                                                    },
                                                    onLongClick = {
                                                        menuState.display {
                                                            OnlineAlbumItemMenu(
                                                                navController = navController,
                                                                album = album
                                                            ).MenuComponent()
                                                        }
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                ),
                                            disableScrollingText = disableScrollingText
                                        )
                                    },
                                    itemPlaceholderContent = { index ->
                                        val alternative = visibleAlternatives.getOrNull(index)
                                        AlbumItemPlaceholder(
                                            thumbnailSizeDp = thumbnailAlbumSizeDp,
                                            alternative = true,
                                            metadataLines = if (alternative?.year.isNullOrBlank()) 0 else 1,
                                            title = alternative?.info?.name?.let(::cleanPrefix),
                                            metadata = alternative?.year?.let(::listOf),
                                        )
                                    }
                                )
                            }
                    }
                }

                GlassFloatingActions(floatingBackdrop) {
                    val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
                    if (showFloatingIcon && !initialSongsLoading)
                        MultiFloatingActionsContainer(
                            // Issue #866 (gh-866): app-wide shuffle confirmation flash (the click
                            // goes through SongShuffler, which triggers the flash).
                            useAsActionsMenu = !hasSelection(),
                            iconId = if (hasSelection()) shuffleButtonIcon() else R.drawable.ellipsis_horizontal,
                            onClick = { if (hasSelection()) shuffle.onShortClick() },
                            onClickSettings = onSettingsClick,
                            onClickSearch = onSearchClick
                        )
                }
            }
        }
    }
}
