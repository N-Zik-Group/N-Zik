// Adapted from RiPlay (GPL-3.0, license checked at upstream revision f69d6760bd09b29e18591cdc1339d3dd8b6bdf55).
package app.n_zik.android.components.ui.screens.artist

import java.util.Locale

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.LocalDownloadStatesMap
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.appTextResources
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.colorPalette
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.artist.ArtistFollowButton
import app.n_zik.android.components.artist.FollowButton
import app.n_zik.android.components.artist.artistStatisticsText
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.components.glass.GlassActionCellWidth
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSectionAction
import app.n_zik.android.components.glass.GlassSectionActions
import app.n_zik.android.components.navigation.header.HeaderActionCanvasSize
import app.n_zik.android.components.navigation.header.headerActionEndPadding
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.n_zik.android.components.menu.artist.OnlineArtistItemMenu
import app.n_zik.android.components.menu.playlist.OnlinePlaylistItemMenu
import app.n_zik.android.components.menu.video.VideoItemMenu
import app.n_zik.android.components.musicbrainz.InfoAndCommunity
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.components.shuffleButtonIconRes
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.Radio
import app.n_zik.android.components.ui.CollectionButtonToolbar
import app.n_zik.android.components.ui.InlineCircularLoader
import app.n_zik.android.components.ui.screens.CollectionArtworkHeader
import app.n_zik.android.components.ui.screens.CollectionArtworkShareButton
import app.n_zik.android.components.ui.screens.DynamicOrientationLayout
import app.n_zik.android.components.ui.screens.album.Translate
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.enums.DownloadedStateMedia
import app.n_zik.android.enums.Languages
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.extensions.musicbrainz.MBMetadataHelper
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import app.n_zik.android.playback.utils.Shuffler
import app.n_zik.android.typography
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.SwipeablePlaylistItem
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.ui.components.themed.Enqueue
import app.n_zik.android.ui.components.themed.MeasuredMetadataPlaceholder
import app.n_zik.android.ui.components.themed.PlayNext
import app.n_zik.android.ui.items.AlbumItem
import app.n_zik.android.ui.items.AlbumItemPlaceholder
import app.n_zik.android.ui.items.ArtistItem
import app.n_zik.android.ui.items.ArtistItemPlaceholder
import app.n_zik.android.ui.items.PlaylistItem
import app.n_zik.android.ui.items.PlaylistItemPlaceholder
import app.n_zik.android.ui.items.SongItemPlaceholder
import app.n_zik.android.ui.items.VideoItem
import app.n_zik.android.ui.items.VideoItemPlaceholder
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.px
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.ExternalUris
import app.n_zik.android.utils.addNext
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.contentTranslationChoices
import app.n_zik.android.utils.contentTranslationLanguage
import app.n_zik.android.utils.contentTranslationTarget
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.forcePlay
import app.n_zik.android.utils.forcePlayAtIndex
import app.n_zik.android.utils.otherLanguageAppArtistKey
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.player.addNextOffMain
import app.n_zik.android.utils.player.enqueueOffMain
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YtMusic
import app.n_zik.innertube.requests.ArtistPage
import app.n_zik.innertube.requests.ArtistSection

private const val ARTIST_INSIGHTS_ROUTE = "artistInsights"

@OptIn(ExperimentalAnimationApi::class, ExperimentalTextApi::class)
@UnstableApi
@ExperimentalFoundationApi
@Composable
internal fun ArtistOverview(
    navController: NavController,
    localArtist: Artist?,
    artistPage: ArtistPage?,
    thumbnailPainter: Painter,
    mbSyncing: Boolean = false,
    onMbSyncingChange: (Boolean) -> Unit = {},
    mbPausedSeconds: Long = 0,
    onMbPausedSecondsChange: (Long) -> Unit = {},
    contentPadding: PaddingValues = rememberPageChromeContentPadding(PaddingValues(bottom = Dimensions.bottomSpacer)),
    lazyListState: LazyListState = rememberLazyListState(),
) {
    artistPage ?: return
    val forceShimmer = forceLoadingShimmerPreview()

    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)

    val sectionActionsUseGlass = rememberGlassSettings().usesGlassMaterial(GlassArea.ActionButtons)
    val sectionTextModifier = Modifier
        .padding(start = 12.dp, end = headerActionEndPadding(sectionActionsUseGlass))
        .padding(top = 24.dp, bottom = 8.dp)
    val albumThumbnailSizeDp = 108.dp
    val albumThumbnailSizePx = albumThumbnailSizeDp.px

    var isGlobalLoading by remember { mutableStateOf(false) }
    var sectionLoadingId by remember { mutableStateOf<String?>(null) }

    val songs = remember(artistPage, parentalControlEnabled) {
        artistPage.sections
            .fastFirstOrNull { section ->
                section.items.fastAll { it is Innertube.SongItem }
            }
            ?.items
            ?.mapNotNull {
                (it as? Innertube.SongItem)?.asSong
            }
            ?.filter { !parentalControlEnabled || it.title.startsWith(EXPLICIT_PREFIX, true) != true }
            .orEmpty()
    }

    val itemSelector = ItemSelector<Song>()
    val scope = rememberCoroutineScope()

    fun getSongs() = itemSelector.ifEmpty { songs }

    val followButton = localArtist?.let { artist -> FollowButton { artist } }
    val isPreview = LocalThemePreview.current != null
    val followAction: @Composable () -> Unit = {
        if (isPreview) {
            ArtistFollowButton(likeState = null, onClick = {})
        } else {
            followButton?.ToolBarButton()
        }
    }
    val shuffler = object : MenuIcon, Descriptive {
        override val iconId: Int get() = shuffleButtonIconRes(binder)
        override val messageId: Int = R.string.player_action_shuffle_hint
        override val menuIconTitle: String
            @Composable get() = stringResource(R.string.player_action_shuffle)

        override fun onShortClick() {
            binder?.triggerShuffleOkFlash()
            scope.launch {
                isGlobalLoading = true
                // Keep the loader visible until the asynchronous shuffle dispatch completes.
                var shufflerWillClear = false
                try {
                    val allSongs = getSongs()
                    if (allSongs.isNotEmpty()) {
                        val b = binder
                        if (b != null) {
                            shufflerWillClear = true
                            Shuffler.play(b, allSongs, onComplete = { isGlobalLoading = false })
                        }
                    } else {
                        Toaster.i(R.string.player_shuffle_empty_message)
                    }
                } finally {
                    if (!shufflerWillClear) isGlobalLoading = false
                }
            }
        }
    }

    val downloadAllDialog = DownloadAllSongsDialog(::getSongs)
    val deleteAllDownloadsDialog = DeleteAllDownloadedSongsDialog(::getSongs)
    val radio = Radio(::getSongs)
    val playNext = PlayNext {
        scope.launch {
            isGlobalLoading = true
            try {
                val allSongs = getSongs().toList()
                val mediaItems = withContext(NzikDispatchers.DATA) { allSongs.map(Song::asMediaItem) }
                binder?.player?.addNextOffMain(mediaItems, appContext())
                itemSelector.isActive = false
            } finally {
                isGlobalLoading = false
            }
        }
    }
    val enqueue = Enqueue {
        scope.launch {
            isGlobalLoading = true
            try {
                val allSongs = getSongs().toList()
                val mediaItems = withContext(NzikDispatchers.DATA) { allSongs.map(Song::asMediaItem) }
                binder?.player?.enqueueOffMain(mediaItems, appContext())
                itemSelector.isActive = false
            } finally {
                isGlobalLoading = false
            }
        }
    }

    var showTranslateLanguageDialog by remember { mutableStateOf(false) }
    val translate = Translate.init(onLongClick = { showTranslateLanguageDialog = true })
    val translator = Translator(NetworkClientFactory.getTranslatorClient())
    var otherLanguageApp by rememberPreference(otherLanguageAppArtistKey, Languages.System)
    val appLang = Locale.getDefault()
    val activeTranslateLang = remember(otherLanguageApp, appLang) {
        if (otherLanguageApp != Languages.System) otherLanguageApp
        else contentTranslationLanguage(appLang)
    }
    val languageDestination = activeTranslateLang.contentTranslationTarget

    downloadAllDialog.Render()
    deleteAllDownloadsDialog.Render()

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

    val songIds = remember(songs) { songs.map { it.id } }
    val likeStatesMap by remember(songIds) {
        LikeStateManager.getLikeStates(songIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
    val playlistStatesMap by remember(songIds) {
        PlaylistStateManager.getPlaylistStates(songIds)
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
            songs.associate { song ->
                song.id to when {
                    song.id in downloadedIds -> DownloadedStateMedia.DOWNLOADED
                    else -> DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
                }
            }
        }
    }

    DynamicOrientationLayout(thumbnailPainter, artistThumbnailShape(), thumbnailContentPadding = contentPadding) {
        CompositionLocalProvider(LocalDownloadStatesMap provides downloadStatesMap) {
            LazyColumn(
                state = lazyListState,
                contentPadding = contentPadding,
            ) {
                item("header") {
                    CollectionArtworkHeader(
                        localArtist?.name ?: artistPage.artist.title ?: "...",
                        thumbnailPainter,
                        disableScrollingText,
                        subtitle = {
                            val statsText = artistStatisticsText(artistPage.subscribers, artistPage.listeners)
                            if (statsText.isNotBlank()) {
                                if (forceShimmer) {
                                    MeasuredMetadataPlaceholder(statsText, typography().s, maxLines = 1)
                                } else BasicText(statsText, style = typography().s.copy(colorPalette().textSecondary))
                            }
                        },
                    ) {
                        val channelId = localArtist?.id ?: artistPage.artist.info?.endpoint?.browseId ?: artistPage.artist.channelId
                        val shareUrl = channelId?.removePrefix(MODIFIED_PREFIX)?.takeIf(String::isNotBlank)
                            ?.let(ExternalUris::youtubeMusicChannel)
                        CollectionArtworkShareButton(shareUrl)
                    }
                }

                item("action_buttons") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isGlobalLoading) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                            ) {
                                followAction()
                                Box(Modifier.size(176.dp, 40.dp),
                                    contentAlignment = Alignment.Center) {
                                    InlineCircularLoader(
                                        modifier = Modifier.size(24.dp),
                                        color = colorPalette().accent,
                                    )
                                }
                            }
                        } else {
                            CollectionButtonToolbar(
                                bookmark = followAction,
                                buttons = listOf(shuffler, playNext, enqueue, radio, itemSelector,
                                    downloadAllDialog, deleteAllDownloadsDialog),
                                bookmarkWidth = 100.dp,
                                actionCellWidth = GlassActionCellWidth,
                                originalBookmarkGap = 10.dp,
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp,
                                    top = 12.dp, bottom = 8.dp),
                            )
                        }
                    }
                }

                item("info_and_community") {
                    InfoAndCommunity(
                        rating = localArtist?.rating,
                        ratingVotes = localArtist?.ratingVotes,
                        year = localArtist?.beginYear?.toString(),
                        countryCode = localArtist?.countryCode,
                        keywords = localArtist?.keywords,
                        links = localArtist?.links,
                        bio = localArtist?.wikipediaBio ?: artistPage.description,
                        translate = translate,
                        translator = translator,
                        languageDestination = languageDestination,
                        lastSyncAt = localArtist?.mbLastFetch,
                        lastSyncFailed = localArtist?.mbLastFetch != null && localArtist?.genres == null,
                        pausedForSeconds = mbPausedSeconds,
                        isSyncing = mbSyncing,
                        onResyncClick = {
                            val id = localArtist?.id ?: artistPage.artist.key
                            scope.launch(NzikDispatchers.DATA) {
                                onMbSyncingChange(true)
                                try {
                                    val success = MBMetadataHelper.Default.onArtistViewed(id, force = true)
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
                        },
                        onInsightsClick = {
                            val id = localArtist?.id ?: artistPage.artist.key
                            navController.navigate("$ARTIST_INSIGHTS_ROUTE/$id")
                        }
                    )
                }

                items(
                    items = artistPage.sections,
                    key = ArtistSection::title
                ) { section ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = sectionTextModifier.fillMaxWidth()
                    ) {
                        Text(
                            text = section.title,
                            style = typography().m.semiBold,
                            modifier = Modifier.weight(1f)
                        )

                        val sectionId = section.title
                        val canPlaySection = !section.items.fastAll { it is Innertube.ArtistItem }
                        val isSectionLoading = canPlaySection && sectionLoadingId == sectionId
                        val shuffleDescription = stringResource(R.string.glass_effect_action_shuffle)
                        val playDescription = stringResource(R.string.glass_effect_action_play_all)
                        if (isSectionLoading) {
                            Box(
                                Modifier.size(HeaderActionCanvasSize * 2, HeaderActionCanvasSize),
                                contentAlignment = Alignment.Center,
                            ) {
                                InlineCircularLoader(
                                    modifier = Modifier.size(24.dp),
                                    color = colorPalette().textSecondary,
                                )
                            }
                        }
                        val sectionActions = buildList {
                            if (canPlaySection && !isSectionLoading) {
                                add(GlassSectionAction(
                                    icon = R.drawable.dice,
                                    contentDescription = shuffleDescription,
                                    nativeEndPadding = 12.dp,
                                    onClick = {
                                        scope.launch(NzikDispatchers.DATA) {
                                            sectionLoadingId = sectionId
                                            // Keep the loader visible until the asynchronous shuffle dispatch completes.
                                            var shufflerWillClear = false
                                            try {
                                                val allMediaItems = mutableListOf<MediaItem>()
                                                val moreBrowseId = section.moreEndpoint?.browseId
                                                if (section.items.fastAll { it is Innertube.SongItem } && moreBrowseId != null) {
                                                    YtMusic.getPlaylist(moreBrowseId).getOrNull()?.songs?.map { it.asMediaItem }?.let { allMediaItems.addAll(it) }
                                                }
                                                if (allMediaItems.isEmpty()) {
                                                    section.items.forEach { item ->
                                                        when (item) {
                                                            is Innertube.SongItem -> item.asSong?.asMediaItem?.let { allMediaItems.add(it) }
                                                            is Innertube.VideoItem -> allMediaItems.add(item.asMediaItem)
                                                            is Innertube.AlbumItem -> YtMusic.getAlbum(item.key).getOrNull()?.songs?.map { it.asMediaItem }?.let { allMediaItems.addAll(it) }
                                                            is Innertube.PlaylistItem -> YtMusic.getPlaylist(item.key).getOrNull()?.songs?.map { it.asMediaItem }?.let { allMediaItems.addAll(it) }
                                                            else -> {}
                                                        }
                                                    }
                                                }
                                                if (allMediaItems.isNotEmpty()) {
                                                    val b = binder
                                                    if (b != null) {
                                                        shufflerWillClear = true
                                                        Shuffler.play(b, allMediaItems, onComplete = { sectionLoadingId = null })
                                                    }
                                                } else {
                                                    withContext(NzikDispatchers.UI) {
                                                        Toaster.e(R.string.player_no_song_found)
                                                    }
                                                }
                                            } finally {
                                                if (!shufflerWillClear) sectionLoadingId = null
                                            }
                                        }
                                    },
                                ))
                                add(GlassSectionAction(
                                    icon = R.drawable.play,
                                    contentDescription = playDescription,
                                    nativeEndPadding = 12.dp,
                                    onClick = {
                                        scope.launch(NzikDispatchers.DATA) {
                                            sectionLoadingId = sectionId
                                            try {
                                                val allMediaItems = mutableListOf<MediaItem>()
                                                val moreBrowseId = section.moreEndpoint?.browseId
                                                if (section.items.fastAll { it is Innertube.SongItem } && moreBrowseId != null) {
                                                    YtMusic.getPlaylist(moreBrowseId).getOrNull()?.songs?.map { it.asMediaItem }?.let { allMediaItems.addAll(it) }
                                                }
                                                if (allMediaItems.isEmpty()) {
                                                    section.items.forEach { item ->
                                                        when (item) {
                                                            is Innertube.SongItem -> item.asSong?.asMediaItem?.let { allMediaItems.add(it) }
                                                            is Innertube.VideoItem -> allMediaItems.add(item.asMediaItem)
                                                            is Innertube.AlbumItem -> YtMusic.getAlbum(item.key).getOrNull()?.songs?.map { it.asMediaItem }?.let { allMediaItems.addAll(it) }
                                                            is Innertube.PlaylistItem -> YtMusic.getPlaylist(item.key).getOrNull()?.songs?.map { it.asMediaItem }?.let { allMediaItems.addAll(it) }
                                                            else -> {}
                                                        }
                                                    }
                                                }
                                                if (allMediaItems.isNotEmpty()) {
                                                    withContext(NzikDispatchers.UI) {
                                                        binder?.stopRadio()
                                                        binder?.player?.forcePlay(allMediaItems.first())
                                                        binder?.player?.addMediaItems(allMediaItems.drop(1))
                                                    }
                                                } else {
                                                    withContext(NzikDispatchers.UI) {
                                                        Toaster.e(R.string.player_no_song_found)
                                                    }
                                                }
                                            } finally {
                                                sectionLoadingId = null
                                            }
                                        }
                                    },
                                ))
                            }
                            section.moreEndpoint?.browseId?.let { browseId ->
                                add(GlassSectionAction(
                                    icon = R.drawable.chevron_forward,
                                    separateBefore = true,
                                    contentDescription = section.title,
                                    onClick = onMore@ {
                                        val path = "$browseId?params=${section.moreEndpoint?.params}"

                                        val route: NavRoutes = if (section.items.fastAll { it is Innertube.SongItem })
                                            NavRoutes.playlist
                                        else if (section.items.fastAll { it is Innertube.AlbumItem })
                                            NavRoutes.artistAlbums
                                        else if (section.items.fastAll { it is Innertube.VideoItem })
                                            NavRoutes.artistVideos
                                        else if (section.items.fastAll { it is Innertube.PlaylistItem })
                                            NavRoutes.artistPlaylists
                                        else
                                            return@onMore

                                        route.navigateHere(navController, path)
                                    },
                                ))
                            }
                        }
                        GlassSectionActions(sectionActions)
                    }

                    if (section.items.fastAll { it is Innertube.SongItem })
                        songs.forEachIndexed { index, song ->
                            if (forceShimmer) {
                                SongItemPlaceholder(title = song.cleanTitle(), artist = song.cleanArtistsText())
                                return@forEachIndexed
                            }
                            SwipeablePlaylistItem(
                                mediaItem = song.asMediaItem,
                                onPlayNext = {
                                    binder?.player?.addNext(song.asMediaItem)
                                }
                            ) {
                                SongItem(
                                    song = song,
                                    isLiked = likeStatesMap[song.id],
                                    inPlaylist = playlistStatesMap[song.id],
                                    itemSelector = itemSelector,
                                    navController = navController,
                                    showThumbnail = true,
                                    modifier = Modifier.background(colorPalette().background0),
                                    onClick = {
                                        binder?.stopRadio()
                                        binder?.player?.forcePlayAtIndex(
                                            songs.map(Song::asMediaItem),
                                            index
                                        )
                                    }
                                )
                            }
                        }

                    if (section.items.fastAll { it is Innertube.AlbumItem })
                        LazyRow {
                            items(
                                items = section.items.fastMap { it as Innertube.AlbumItem },
                                key = Innertube.AlbumItem::key
                            ) { album ->
                                if (forceShimmer) {
                                    AlbumItemPlaceholder(
                                        albumThumbnailSizeDp, alternative = true, title = album.title,
                                        metadataLines = if (album.year.isNullOrBlank()) 0 else 1,
                                        metadata = album.year?.let(::listOf),
                                    )
                                } else AlbumItem(
                                    album = album,
                                    alternative = true,
                                    thumbnailSizePx = albumThumbnailSizePx,
                                    thumbnailSizeDp = albumThumbnailSizeDp,
                                    disableScrollingText = disableScrollingText,
                                    modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                        onClick = {
                                            navController.navigate("${NavRoutes.album.name}/${album.key}")
                                        },
                                        onLongClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.display {
                                                OnlineAlbumItemMenu(
                                                    navController = navController,
                                                    album = album
                                                ).MenuComponent()
                                            }
                                        }
                                    )
                                )
                            }
                        }

                    if (section.items.fastAll { it is Innertube.PlaylistItem })
                        LazyRow {
                            items(
                                items = section.items.fastMap { it as Innertube.PlaylistItem },
                                key = Innertube.PlaylistItem::key
                            ) { playlist ->
                                if (forceShimmer) {
                                    PlaylistItemPlaceholder(
                                        albumThumbnailSizeDp, alternative = true,
                                        showTitle = playlist.title != null, title = playlist.title,
                                        showChannel = playlist.channel?.name != null, channel = playlist.channel?.name,
                                    )
                                } else PlaylistItem(
                                    playlist = playlist,
                                    alternative = true,
                                    thumbnailSizePx = albumThumbnailSizePx,
                                    thumbnailSizeDp = albumThumbnailSizeDp,
                                    disableScrollingText = disableScrollingText,
                                    modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                        onClick = { navController.navigate("${NavRoutes.playlist.name}/${playlist.key}") },
                                        onLongClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.display {
                                                OnlinePlaylistItemMenu(
                                                    navController = navController,
                                                    playlist = playlist
                                                ).MenuComponent()
                                            }
                                        }
                                    )
                                )
                            }
                        }

                    if (section.items.fastAll { it is Innertube.VideoItem }) {
                        val videoKeys = remember(section.items) { section.items.fastMap { (it as Innertube.VideoItem).key } }
                        val videoLikeStatesMap by remember(videoKeys) {
                            LikeStateManager.getLikeStates(videoKeys)
                        }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
                        LazyRow {
                            items(
                                items = section.items.fastMap { it as Innertube.VideoItem },
                                key = Innertube.VideoItem::key
                            ) { video ->
                                if (forceShimmer) {
                                    VideoItemPlaceholder(72.dp, 128.dp, alternative = true, video = video)
                                } else VideoItem(
                                    video = video,
                                    thumbnailHeightDp = 72.dp,
                                    thumbnailWidthDp = 128.dp,
                                    alternative = true,
                                    likeState = videoLikeStatesMap[video.key],
                                    disableScrollingText = disableScrollingText,
                                    modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                        onLongClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.display {
                                                VideoItemMenu(
                                                    navController = navController,
                                                    song = video.asMediaItem.asSong
                                                ).MenuComponent()
                                            }
                                        },
                                        onClick = {
                                            binder?.stopRadio()
                                            binder?.player?.forcePlay(video.asMediaItem)
                                        }
                                    )
                                )
                            }
                        }
                    }

                    if (section.items.fastAll { it is Innertube.ArtistItem })
                        LazyRow {
                            items(
                                items = section.items.fastMap { it as Innertube.ArtistItem },
                                key = Innertube.ArtistItem::key
                            ) { artist ->
                                if (forceShimmer) {
                                    ArtistItemPlaceholder(
                                        albumThumbnailSizeDp, alternative = true, title = artist.title,
                                        showSubscribers = artist.subscribersCountText != null,
                                        subscribers = artist.subscribersCountText,
                                    )
                                } else ArtistItem(
                                    artist = artist,
                                    alternative = true,
                                    thumbnailSizePx = albumThumbnailSizePx,
                                    thumbnailSizeDp = albumThumbnailSizeDp,
                                    disableScrollingText = disableScrollingText,
                                    modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                        onClick = { navController.navigate("${NavRoutes.artist.name}/${artist.key}") },
                                        onLongClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.display {
                                                OnlineArtistItemMenu(
                                                    navController = navController,
                                                    artist = artist
                                                ).MenuComponent()
                                            }
                                        }
                                    )
                                )
                            }
                        }
                }
            }
        }
    }
}
