// Adapted from RiPlay (GPL-3.0, license checked at upstream revision f69d6760bd09b29e18591cdc1339d3dd8b6bdf55).
package app.n_zik.android.components.ui.screens.album

import app.n_zik.android.appTextResources
import app.n_zik.android.components.glass.GlassArtworkBackdrop
import app.n_zik.android.components.ui.screens.CollectionPageStatus
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import android.content.Intent
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
import androidx.media3.common.MediaItem
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
import app.n_zik.android.ui.components.themed.AutoResizeText
import app.n_zik.android.ui.components.themed.Enqueue
import app.n_zik.android.ui.components.themed.FontSizeRange
import app.n_zik.android.components.ui.HeaderIconAction
import app.n_zik.android.components.ui.CollectionButtonToolbar
import app.n_zik.android.components.glass.GlassToolbarRow
import app.n_zik.android.ui.components.themed.ItemsList
import app.n_zik.android.components.ui.FullPageLoader
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
import app.n_zik.android.utils.asSong
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
import app.n_zik.android.components.dialog.album.ChangeAlbumCoverDialog
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.n_zik.android.components.musicbrainz.InfoAndCommunity
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.Locator
import app.n_zik.android.components.tab.Radio
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
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.player.addNextOffMain
import app.n_zik.android.utils.player.enqueueOffMain
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.ui.components.SwipeablePlaylistItem
import timber.log.Timber

private const val ALBUM_INSIGHTS_ROUTE = "albumInsights"

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

    var album by persist<Album?>("album/$browseId")
    var mbSyncing by remember { mutableStateOf(false) }
    var mbPausedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        Database.albumTable
            .findById(browseId)
            .flowOn(NzikDispatchers.DATA)
            .distinctUntilChanged()
            .collect { album = it }
    }

    var alternatives by persistList<Innertube.AlbumItem>("album/$browseId/alternatives")
    var description by rememberSaveable { mutableStateOf("") }
    var loadedSongsCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val cleanedBrowseId = browseId.removePrefix(MODIFIED_PREFIX)

        withContext(NzikDispatchers.DATA) {
            YtMusic.getAlbum(cleanedBrowseId, true, onProgress = { count ->
                launch(NzikDispatchers.UI) { loadedSongsCount = count }
            })
        }
            .onSuccess { online ->
                val onlineAlbum = online.album
                val authorsText: String? = onlineAlbum.authors.artistEntryNames().joinToString(", ")

                Database.asyncTransaction {
                    syncTransaction {
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
                                authorsText = PropUtils.retainIfModified(current?.authorsText, authorsText),
                                shareUrl = online.url,
                                isYoutubeAlbum = current?.isYoutubeAlbum == true,
                                position = current?.position ?: -1,
                                lastFetch = now
                            )
                        }

                        songAlbumMapTable.clear(browseId)

                        online.songs
                            .map(Innertube.SongItem::asMediaItem)
                            .onEach(::insertIgnore)
                            .mapIndexed { position, mediaItem ->
                                SongAlbumMap(
                                    songId = mediaItem.mediaId,
                                    albumId = browseId,
                                    position = position
                                )
                            }
                            .also(songAlbumMapTable::upsert)

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

                alternatives = online.otherVersions
                description = online.description ?: ""
            }
    }

    LaunchedEffect(album?.id) {
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
    onSettingsClick: () -> Unit
) {
    PersistMapCleanup("album/$browseId/songs")

    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current

    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val rawItems by remember {
        Database.songAlbumMapTable
            .allSongsOf(browseId)
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(emptyList(), context = NzikDispatchers.DATA)

    val items = remember(rawItems, parentalControlEnabled) {
        rawItems.filter { !parentalControlEnabled || it.title.startsWith(EXPLICIT_PREFIX, true) != true }
    }

    val itemSelector = ItemSelector<Song>()

    fun getSongs() = itemSelector.ifEmpty { items }
    fun getMediaItems() = getSongs().map(Song::asMediaItem)

    val bookmark = AlbumBookmark(browseId)

    val deleteAllDownloadsDialog = DeleteAllDownloadedSongsDialog(::getSongs)
    val downloadALlDialog = DownloadAllSongsDialog(::getSongs)
    val shuffle = SongShuffler {
        getMediaItems().map(MediaItem::asSong)
    }
    val radio = Radio(::getSongs)
    val locator = Locator(lazyListState, ::getSongs)
    val playNext = PlayNext {
        val songsToAdd = getSongs().toList()
        itemSelector.isActive = false
        coroutineScope.launch {
            val mediaItems = withContext(NzikDispatchers.DATA) { songsToAdd.map(Song::asMediaItem) }
            binder?.player?.addNextOffMain(mediaItems, appContext())
        }
    }
    val enqueue = Enqueue {
        val songsToAdd = getSongs().toList()
        itemSelector.isActive = false
        coroutineScope.launch {
            val mediaItems = withContext(NzikDispatchers.DATA) { songsToAdd.map(Song::asMediaItem) }
            binder?.player?.enqueueOffMain(mediaItems, appContext())
        }
    }
    val addToPlaylist = PlaylistsMenu.init(
        navController,
        { getMediaItems() },
        { throwable, preview ->
            Timber.tag("AlbumDetails").e(throwable, "Failed to add songs to playlist ${preview.playlist.name}")
        },
        {
            itemSelector.isActive = false
        }
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

    DynamicOrientationLayout(thumbnailPainter, thumbnailContentPadding = rememberPageChromeContentPadding()) {
        Box(
            Modifier.fillMaxSize()
                .background(colorPalette().background0),
            contentAlignment = Alignment.Center
        ) {
            if (items.isEmpty()) {
                CollectionPageStatus {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FullPageLoader()
                        if (loadedSongsCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            BasicText(
                                text = pluralStringResource(R.plurals.album_loading_song_count, loadedSongsCount, loadedSongsCount),
                                style = typography().xxs.copy(
                                    color = colorPalette().textDisabled
                                )
                            )
                        }
                    }
                }
            } else {
                val albumSongIds = remember(items) { items.map { it.id } }
                val likeStatesMap by remember(albumSongIds) {
                    LikeStateManager.getLikeStates(albumSongIds)
                }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
                val playlistStatesMap by remember(albumSongIds) {
                    PlaylistStateManager.getPlaylistStates(albumSongIds)
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
                            GlassArtworkBackdrop(hasArtwork = !isLandscape) { artworkBackdrop ->
                            Box(Modifier.fillMaxWidth()) {
                                if (!isLandscape)
                                    Image(
                                        painter = thumbnailPainter,
                                        contentDescription = null,
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier.then(artworkBackdrop).aspectRatio(4f / 3)
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
                                    text = cleanPrefix(album?.title ?: "..."),
                                    style = typography().l.semiBold,
                                    fontSizeRange = FontSizeRange(32.sp, 38.sp),
                                    fontWeight = typography().l.semiBold.fontWeight,
                                    fontFamily = typography().l.semiBold.fontFamily,
                                    color = typography().l.semiBold.color,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                        .padding(horizontal = 30.dp)
                                        .conditional(!disableScrollingText) {
                                            basicMarquee(iterations = Int.MAX_VALUE)
                                        }
                                )

                                GlassToolbarRow(1,
                                    modifier = Modifier.align(Alignment.TopEnd)
                                        .padding(top = 5.dp, end = 5.dp),
                                ) {
                                HeaderIconAction(
                                    icon = R.drawable.share_social,
                                    color = colorPalette().text,
                                    iconSize = 24.dp,
                                    onClick = {
                                        album?.shareUrl?.let { url ->
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, url)
                                            }

                                            context.startActivity(
                                                Intent.createChooser(sendIntent, null)
                                            )
                                        }
                                    }
                                )
                                }
                            }
                            }
                        }

                        item("album_details") {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val songCount = pluralStringResource(R.plurals.library_song_count, items.size, items.size)
                                val totalDuration = items.sumOf {
                                    durationTextToMillis(it.durationText ?: "")
                                }.let(::formatAsTime)

                                BasicText(
                                    text = "$songCount - $totalDuration",
                                    style = typography().xs.medium,
                                    maxLines = 1
                                )
                            }
                        }

                        item("action_buttons") {
                            CollectionButtonToolbar(
                                modifier = Modifier.padding(top = 8.dp),
                                bookmark = { bookmark.ToolBarButton() },
                                originalNativeBookmarkWidth = 48.dp,
                                buttons = listOf(
                                    downloadALlDialog,
                                    deleteAllDownloadsDialog,
                                    shuffle,
                                    radio,
                                    locator,
                                    itemSelector,
                                    changeTitle,
                                    changeAuthors,
                                    changeCover,
                                    playNext,
                                    enqueue,
                                    addToPlaylist,
                                ),
                            )
                        }

                        item("info_and_community") {
                            InfoAndCommunity(
                                rating = album?.rating,
                                ratingVotes = album?.ratingVotes,
                                year = album?.year,
                                countryCode = null,
                                keywords = album?.keywords,
                                links = album?.links,
                        bio = album?.wikipediaInfo ?: description,
                        translate = translate,
                        translator = translator,
                        languageDestination = languageDestination,
                        lastSyncAt = album?.mbLastFetch,
                        lastSyncFailed = album?.mbLastFetch != null && album?.genres == null,
                        pausedForSeconds = mbPausedSeconds,
                        isSyncing = mbSyncing,
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
                            navController.navigate("$ALBUM_INSIGHTS_ROUTE/$browseId")
                        }
                    )
                }

                        item("songsTitle") {
                            BasicText(
                                text = stringResource(R.string.library_songs_title),
                                style = typography().m.semiBold.align(TextAlign.Start),
                                modifier = sectionTextModifier.fillMaxWidth()
                            )
                        }

                        itemsIndexed(
                            items = items,
                            key = { _, song -> song.id }
                        ) { index, song ->

                            SwipeablePlaylistItem(
                                mediaItem = song.asMediaItem,
                                onPlayNext = {
                                    binder?.player?.addNext(song.asMediaItem)
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
                                    showThumbnail = false,
                                    modifier = Modifier,

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
                                            getMediaItems(),
                                            index
                                        )
                                    }
                                )
                            }
                        }

                        if (alternatives.isNotEmpty())
                            item("alternatives") {
                                BasicText(
                                    text = stringResource(R.string.album_alternative_versions),
                                    style = typography().m.semiBold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(all = 16.dp)
                                )

                                val altAlbumKeys = remember(alternatives) { alternatives.map { it.key } }
                                val altBookmarkStatesMap by remember(altAlbumKeys) {
                                    BookmarkStateManager.getAlbumBookmarkStates(altAlbumKeys)
                                }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

                                ItemsList(
                                    tag = "album/$browseId/alternatives_list",
                                    headerContent = {},
                                    initialPlaceholderCount = 1,
                                    continuationPlaceholderCount = 1,
                                    emptyItemsText = stringResource(R.string.album_no_alternative_version),
                                    itemsPageProvider = {
                                        Result.success(
                                            Innertube.ItemsPage(alternatives, null)
                                        )
                                    },
                                    itemContent = { album ->
                                        AlbumItem(
                                            alternative = true,
                                            album = album,
                                            thumbnailSizePx = thumbnailAlbumSizePx,
                                            thumbnailSizeDp = thumbnailAlbumSizeDp,
                                            bookmarkState = altBookmarkStatesMap[album.key],
                                            modifier = Modifier
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
                                    itemPlaceholderContent = {
                                        AlbumItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp)
                                    }
                                )
                            }
                    }
                }

                GlassFloatingActions(floatingBackdrop) {
                    val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
                    if (showFloatingIcon)
                        MultiFloatingActionsContainer(
                            // Issue #866 (gh-866): app-wide shuffle confirmation flash (the click
                            // goes through SongShuffler, which triggers the flash).
                            iconId = shuffleButtonIcon(),
                            onClick = shuffle::onShortClick,
                            onClickSettings = onSettingsClick,
                            onClickSearch = onSearchClick
                        )
                }
            }
        }
    }
}
