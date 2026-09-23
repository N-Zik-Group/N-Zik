// Ported from RiPlay (AGPL-3.0) — base: it.fast4x.riplay.ui.screens.artist / it.fast4x.riplay.extensions.musicbrainz
package app.n_zik.android.components.ui.screens.artist

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastMap
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.it.fast4x.compose.persist.PersistMapCleanup
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.enums.Languages
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.PlayerPosition
import app.it.fast4x.rimusic.enums.TransitionEffect
import app.it.fast4x.rimusic.enums.DownloadedStateMedia
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.Skeleton
import app.it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.components.themed.AutoResizeText
import app.it.fast4x.rimusic.ui.components.themed.Enqueue
import app.it.fast4x.rimusic.ui.components.themed.FontSizeRange
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.components.themed.Loader
import app.it.fast4x.rimusic.ui.components.themed.PlayNext
import app.it.fast4x.rimusic.ui.components.themed.ValueSelectorDialog
import app.it.fast4x.rimusic.ui.items.AlbumItem
import app.it.fast4x.rimusic.ui.items.ArtistItem
import app.it.fast4x.rimusic.ui.items.PlaylistItem
import app.it.fast4x.rimusic.ui.items.VideoItem
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.px
import app.it.fast4x.rimusic.ui.screens.artist.ArtistLocalSongs
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.align
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.fadingEdge
import app.it.fast4x.rimusic.utils.forcePlay
import app.it.fast4x.rimusic.utils.forcePlayAtIndex
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.otherLanguageAppArtistKey
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.playerPositionKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.transitionEffectKey
import app.kreate.android.me.knighthat.utils.PropUtils
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.LocalDownloadStatesMap
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.artist.FollowButton
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.n_zik.android.components.menu.artist.OnlineArtistItemMenu
import app.n_zik.android.components.menu.playlist.OnlinePlaylistItemMenu
import app.n_zik.android.components.menu.video.VideoItemMenu
import app.n_zik.android.components.musicbrainz.InfoAndCommunity
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.Radio
import app.n_zik.android.components.ui.screens.DynamicOrientationLayout
import app.n_zik.android.components.ui.screens.album.Translate
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.extensions.musicbrainz.MBMetadataHelper
import app.n_zik.android.playback.utils.Shuffler
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.requests.ArtistPage
import it.fast4x.innertube.requests.ArtistSection
import it.fast4x.innertube.requests.queue
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.player.addNextOffMain
import app.n_zik.android.utils.player.enqueueOffMain
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val ARTIST_INSIGHTS_ROUTE = "artistInsights"

@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, ExperimentalTextApi::class)
@UnstableApi
@ExperimentalFoundationApi
@ExperimentalComposeUiApi
@Composable
fun ArtistScreen(
    navController: NavController,
    browseId: String,
    modifier: Modifier = Modifier,
    miniPlayer: @Composable () -> Unit = {}
) {
    PersistMapCleanup("artist/$browseId/")

    val saveableStateHolder = rememberSaveableStateHolder()

    val transitionEffect by rememberPreference(transitionEffectKey, TransitionEffect.Fade)
    val playerPosition by rememberPreference(playerPositionKey, PlayerPosition.Bottom)

    var selectedTabIndex by remember { mutableStateOf(0) }

    var localArtist: Artist? by remember { mutableStateOf(null) }
    var mbSyncing by remember { mutableStateOf(false) }
    var mbPausedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        Database.artistTable
            .findById(browseId)
            .flowOn(NzikDispatchers.DATA)
            .distinctUntilChanged()
            .collect { localArtist = it }
    }

    var artistPage: ArtistPage? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        YtMusic.getArtistPage(browseId.removePrefix(MODIFIED_PREFIX))
            .onSuccess { online ->
                artistPage = online

                Database.asyncTransaction {
                    val now = System.currentTimeMillis()
                    val inserted = artistTable.insertMetadata(
                        id = browseId,
                        name = online.artist.title,
                        thumbnailUrl = online.artist.thumbnail?.url,
                        timestamp = now,
                        isYoutubeArtist = false,
                        position = -1
                    )
                    if (inserted == -1L) {
                        artistTable.updateMetadata(
                            id = browseId,
                            name = PropUtils.retainIfModified(localArtist?.name, online.artist.title),
                            thumbnailUrl = PropUtils.retainIfModified(localArtist?.thumbnailUrl, online.artist.thumbnail?.url),
                            isYoutubeArtist = localArtist?.isYoutubeArtist == true,
                            position = localArtist?.position ?: -1
                        )
                    }

                    online.sections
                        .fastFirstOrNull { section ->
                            section.items.fastAll { it is Innertube.SongItem }
                        }
                        ?.items
                        ?.map { (it as Innertube.SongItem).asMediaItem }
                        ?.also {
                            localArtist?.let { artist -> mapIgnore(artist, *it.toTypedArray()) }
                        }

                    // Store the YouTube description so the Insights page can reuse it
                    // as a bio fallback without refetching the artist page.
                    val ytDescription = online.description?.takeIf { it.isNotBlank() }
                    if (ytDescription != null) {
                        artistTable.findByIdDirect(browseId)?.let { current ->
                            if (current.description != ytDescription) {
                                artistTable.update(current.copy(description = ytDescription))
                            }
                        }
                    }
                }

                val itemsToFetch = online.sections.flatMap { it.items }
                    .filter { (it is Innertube.VideoItem && it.durationText == null) || (it is Innertube.SongItem && it.durationText == null) }
                    .map { it.key }
                    .distinct()

                if (itemsToFetch.isNotEmpty()) {
                    Innertube.queue(videoIds = itemsToFetch)?.onSuccess { queueItems ->
                        val durationsMap = queueItems?.associate { it.key to it.durationText } ?: emptyMap()
                        artistPage = artistPage?.withUpdatedVideoDurations(durationsMap)
                    }
                }
            }
    }

    LaunchedEffect(localArtist?.id) {
        val artist = localArtist ?: return@LaunchedEffect
        mbSyncing = true
        try {
            withContext(NzikDispatchers.DATA) {
                val channelId = browseId.removePrefix(MODIFIED_PREFIX)
                if (artist.youtubeChannelId != channelId) {
                    Database.artistTable.update(artist.copy(youtubeChannelId = channelId))
                }
                MBMetadataHelper.Default.onArtistViewed(artist.id)
            }
        } finally {
            mbSyncing = false
            mbPausedSeconds = MBMetadataHelper.Default.circuitOpenRemainingSeconds()
        }
    }

    val thumbnailPainter = ImageCacheFactory.Painter(localArtist?.thumbnailUrl)

    Skeleton(
        navController = navController,
        tabIndex = selectedTabIndex,
        onTabChanged = { index -> selectedTabIndex = index },
        miniPlayer = miniPlayer,
        navBarContent = { item ->
            item(0, stringResource(R.string.overview), R.drawable.artist)
            item(1, stringResource(R.string.library), R.drawable.library)
        }
    ) { currentTabIndex ->
        when (currentTabIndex) {
            0 -> {
                if (artistPage == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Loader()
                    }
                } else {
                    ArtistOverview(
                        navController, localArtist, artistPage, thumbnailPainter,
                        mbSyncing, { mbSyncing = it },
                        mbPausedSeconds, { mbPausedSeconds = it }
                    )
                }
            }
            1 -> {
                if (localArtist == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Loader()
                    }
                } else {
                    ArtistLocalSongs(navController, localArtist, artistPage, thumbnailPainter)
                }
            }
        }
    }
}

@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, ExperimentalTextApi::class)
@UnstableApi
@ExperimentalFoundationApi
@Composable
fun ArtistOverview(
    navController: NavController,
    localArtist: Artist?,
    artistPage: ArtistPage?,
    thumbnailPainter: Painter,
    mbSyncing: Boolean = false,
    onMbSyncingChange: (Boolean) -> Unit = {},
    mbPausedSeconds: Long = 0,
    onMbPausedSecondsChange: (Long) -> Unit = {}
) {
    artistPage ?: return

    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)
    val albumThumbnailSizeDp = 108.dp
    val albumThumbnailSizePx = albumThumbnailSizeDp.px

    var isGlobalLoading by remember { mutableStateOf(false) }
    var sectionLoadingId by remember { mutableStateOf<String?>(null) }

    val songs = remember(parentalControlEnabled) {
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
    fun getMediaItems() = getSongs().map(Song::asMediaItem)

    val followButton = localArtist?.let { artist -> FollowButton { artist } }
    val shuffler = object : MenuIcon, Descriptive {
        override val iconId: Int = R.drawable.shuffle
        override val messageId: Int = R.string.info_shuffle
        override val menuIconTitle: String
            @Composable get() = stringResource(R.string.shuffle)

        override fun onShortClick() {
            scope.launch {
                isGlobalLoading = true
                // Shuffler.play() is fire-and-forget (issue #606 M2): when it's actually invoked,
                // isGlobalLoading is cleared from its onComplete once work actually finishes, not
                // as soon as play() returns. shufflerWillClear guards the `finally` below so an
                // exception thrown before reaching Shuffler.play() (e.g. getSongs()) still clears
                // the flag, matching this block's original exception-safety.
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
                        Toaster.i(R.string.no_song_to_shuffle)
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
    val appLang = Locale.getDefault().language
    val activeTranslateLang = remember(otherLanguageApp, appLang) {
        if (otherLanguageApp != Languages.System) otherLanguageApp
        else Languages.entries.firstOrNull { it.code == appLang } ?: Languages.English
    }
    val languageDestination = activeTranslateLang.translatorLanguage

    downloadAllDialog.Render()
    deleteAllDownloadsDialog.Render()

    if (showTranslateLanguageDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.info_translation),
            selectedValue = otherLanguageApp,
            onValueSelected = {
                otherLanguageApp = it
                translate.isActive = it.translatorLanguage != Language.ENGLISH
                showTranslateLanguageDialog = false
            },
            valueText = { it.text },
            values = Languages.entries.toList(),
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

    DynamicOrientationLayout(thumbnailPainter, artistThumbnailShape()) {
        CompositionLocalProvider(LocalDownloadStatesMap provides downloadStatesMap) {
            LazyColumn(
                state = lazyListState,
                userScrollEnabled = artistPage.sections.isNotEmpty(),
                contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer),
            ) {
                item("header") {
                    Box(Modifier.fillMaxWidth()) {
                        if (!isLandscape)
                            Image(
                                painter = thumbnailPainter,
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.aspectRatio(4f / 3)
                                    .fillMaxWidth()
                                    .align(Alignment.Center)
                                    .fadingEdge(
                                        top = WindowInsets.systemBars
                                            .asPaddingValues()
                                            .calculateTopPadding() + Dimensions.fadeSpacingTop,
                                        bottom = Dimensions.fadeSpacingBottom
                                    )
                            )

                        Column(Modifier.align(Alignment.BottomCenter)) {
                            AutoResizeText(
                                text = cleanPrefix(localArtist?.name ?: artistPage.artist.title ?: "..."),
                                style = typography().l.semiBold,
                                fontSizeRange = FontSizeRange(32.sp, 38.sp),
                                fontWeight = typography().l.semiBold.fontWeight,
                                fontFamily = typography().l.semiBold.fontFamily,
                                color = typography().l.semiBold.color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 30.dp)
                                    .conditional(!disableScrollingText) {
                                        basicMarquee(iterations = Int.MAX_VALUE)
                                    }
                                    .align(Alignment.CenterHorizontally)
                            )

                            val statsText = buildString {
                                val subs = artistPage.subscribers
                                val listeners = artistPage.listeners
                                if (!subs.isNullOrBlank()) append(subs)
                                if (!subs.isNullOrBlank() && !listeners.isNullOrBlank()) append(" • ")
                                if (!listeners.isNullOrBlank()) append(listeners)
                            }
                            if (statsText.isNotBlank()) {
                                BasicText(
                                    text = statsText,
                                    style = typography().s.copy(colorPalette().textSecondary),
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }

                        HeaderIconButton(
                            icon = R.drawable.share_social,
                            color = colorPalette().text,
                            iconSize = 24.dp,
                            modifier = Modifier.align(Alignment.TopEnd)
                                .padding(top = 5.dp, end = 5.dp),
                            onClick = {
                                val url = "https://music.youtube.com/channel/${localArtist?.id ?: artistPage.artist.key}"
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, url)
                                }

                                context.startActivity(
                                    Intent.createChooser(sendIntent, null)
                                )
                            }
                        )
                    }
                }

                item("action_buttons") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            followButton?.ToolBarButton()

                            Spacer(Modifier.width(5.dp))

                            if (isGlobalLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = colorPalette().accent
                                )
                            } else {
                                TabToolBar.Buttons(
                                    shuffler,
                                    playNext,
                                    enqueue,
                                    radio,
                                    itemSelector,
                                    downloadAllDialog,
                                    deleteAllDownloadsDialog,
                                    modifier = Modifier.fillMaxWidth(.8f)
                                )
                            }
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
                                        success -> Toaster.s(R.string.mb_resync_success)
                                        paused > 0 -> Toaster.e(
                                            appContext().getString(R.string.mb_resync_paused, (paused / 60).coerceAtLeast(1))
                                        )
                                        else -> Toaster.e(R.string.mb_resync_error)
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

                        if (!section.items.fastAll { it is Innertube.ArtistItem }) {
                            val context = LocalContext.current
                            val sectionId = section.title
                            val isSectionLoading = sectionLoadingId == sectionId

                            if (isSectionLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 12.dp).size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = colorPalette().textSecondary
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.dice),
                                    contentDescription = null,
                                    tint = colorPalette().textSecondary,
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .clip(uiRoundnessShape()).clickable {
                                            scope.launch(NzikDispatchers.DATA) {
                                                sectionLoadingId = sectionId
                                                // Shuffler.play() is fire-and-forget (issue #606 M2): when it's actually
                                                // invoked, sectionLoadingId is cleared from its onComplete once playback
                                                // dispatch actually finishes. shufflerWillClear guards the `finally`
                                                // below so an exception before reaching Shuffler.play() still clears it.
                                                var shufflerWillClear = false
                                                try {
                                                    val allMediaItems = mutableListOf<MediaItem>()
                                                    if (section.items.fastAll { it is Innertube.SongItem } && section.moreEndpoint?.browseId != null) {
                                                        YtMusic.getPlaylist(section.moreEndpoint!!.browseId!!).getOrNull()?.songs?.map { it.asMediaItem }?.let { allMediaItems.addAll(it) }
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
                                                            Toaster.e(R.string.no_song_found)
                                                        }
                                                    }
                                                } finally {
                                                    if (!shufflerWillClear) sectionLoadingId = null
                                                }
                                            }
                                        }
                                )

                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = null,
                                    tint = colorPalette().textSecondary,
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .clip(uiRoundnessShape()).clickable {
                                            scope.launch(NzikDispatchers.DATA) {
                                                sectionLoadingId = sectionId
                                                try {
                                                    val allMediaItems = mutableListOf<MediaItem>()
                                                    if (section.items.fastAll { it is Innertube.SongItem } && section.moreEndpoint?.browseId != null) {
                                                        YtMusic.getPlaylist(section.moreEndpoint!!.browseId!!).getOrNull()?.songs?.map { it.asMediaItem }?.let { allMediaItems.addAll(it) }
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
                                                            Toaster.e(R.string.no_song_found)
                                                        }
                                                    }
                                                } finally {
                                                    sectionLoadingId = null
                                                }
                                            }
                                        }
                                )
                            }
                        }

                        section.moreEndpoint?.browseId?.let { browseId ->
                            Icon(
                                painter = painterResource(R.drawable.chevron_forward),
                                contentDescription = null,
                                tint = colorPalette().textSecondary,
                                modifier = Modifier.clip(uiRoundnessShape()).clickable {
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
                                        return@clickable

                                    route.navigateHere(navController, path)
                                }
                            )
                        }
                    }

                    if (section.items.fastAll { it is Innertube.SongItem })
                        songs.forEachIndexed { index, song ->
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
                                AlbumItem(
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
                                PlaylistItem(
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
                                VideoItem(
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
                                ArtistItem(
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
