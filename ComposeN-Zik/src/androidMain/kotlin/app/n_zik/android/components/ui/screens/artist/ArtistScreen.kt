// Adapted from RiPlay (GPL-3.0, license checked at upstream revision f69d6760bd09b29e18591cdc1339d3dd8b6bdf55).
package app.n_zik.android.components.ui.screens.artist

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastFirstOrNull
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.R
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.components.OfflineContent
import app.n_zik.android.components.artist.ArtistFollowButton
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.components.musicbrainz.InfoAndCommunity
import app.n_zik.android.components.navigation.Skeleton
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import app.n_zik.android.components.navigation.rememberTopContentInsetProvider
import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.components.settings.appearance.preview.themePreviewArtistPage
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.Radio
import app.n_zik.android.components.tab.SongShuffler
import app.n_zik.android.components.ui.CollectionButtonToolbar
import app.n_zik.android.components.settings.ui.ToolbarAction as ToolbarControl
import app.n_zik.android.components.settings.ui.ToolbarSurface
import app.n_zik.android.components.settings.ui.visibleActions
import app.n_zik.android.components.ui.screens.CollectionArtworkHeader
import app.n_zik.android.components.ui.screens.CollectionArtworkShareButton
import app.n_zik.android.components.ui.screens.CollectionPageStatus
import app.n_zik.android.components.ui.screens.DynamicOrientationLayout
import app.n_zik.android.compose.persist.PersistMapCleanup
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.core.utils.PropUtils
import app.n_zik.android.enums.PlayerPosition
import app.n_zik.android.enums.TransitionEffect
import app.n_zik.android.extensions.musicbrainz.MBMetadataHelper
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import app.n_zik.android.typography
import app.n_zik.android.ui.components.themed.Enqueue
import app.n_zik.android.ui.screens.artist.artistLibraryToolbarAction
import app.n_zik.android.ui.components.themed.ItemTextPlaceholder
import app.n_zik.android.ui.components.themed.PlayNext
import app.n_zik.android.ui.items.SongItemPlaceholders
import app.n_zik.android.ui.screens.artist.ArtistLocalSongs
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.playerPositionKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.transitionEffectKey
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YtMusic
import app.n_zik.innertube.requests.ArtistPage
import app.n_zik.innertube.requests.queue

internal fun artistScreenTabIndex(offlineMode: Boolean, tabIndex: Int): Int =
    if (offlineMode) 1 else tabIndex

/** Displays an artist overview and local library. */
@OptIn(ExperimentalAnimationApi::class, ExperimentalTextApi::class)
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
    val preview = LocalThemePreview.current
    PersistMapCleanup("artist/$browseId/")

    val saveableStateHolder = rememberSaveableStateHolder()

    val transitionEffect by rememberPreference(transitionEffectKey, TransitionEffect.Fade)
    val playerPosition by rememberPreference(playerPositionKey, PlayerPosition.Bottom)

    var selectedTabIndex by rememberSaveable(browseId) { mutableStateOf(0) }

    val retained: ArtistScreenData = if (preview == null) viewModel(key = "artist:$browseId", factory = ArtistScreenData.Factory)
    else remember(preview) { ArtistScreenData() }
    var localArtist by retained.localArtist
    var localArtistResolved by retained.localArtistResolved
    var mbSyncing by remember { mutableStateOf(false) }
    var mbPausedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        if (preview != null) return@LaunchedEffect
        Database.artistTable
            .findById(browseId)
            .flowOn(NzikDispatchers.DATA)
            .distinctUntilChanged()
            .collect {
                localArtist = it
                localArtistResolved = true
            }
    }

    var fetchedPage by retained.artistPage
    val previewPage = remember(preview) {
        preview?.let { themePreviewArtistPage() }
    }
    val artistPage = fetchedPage ?: previewPage

    val context = LocalContext.current
    val isOnline by NetworkQualityHelper.isNetworkAvailableComposable(context)

    val offlineMode by OfflineMode.rememberEnabled()
    LaunchedEffect(offlineMode) {
        if (offlineMode && selectedTabIndex != 1) selectedTabIndex = 1
    }

    var artistFetchInProgress by retained.fetching
    var artistFetchFailed by retained.failed

    fun fetchArtistPage() {
        if (artistFetchInProgress) return
        artistFetchInProgress = true
        artistFetchFailed = false
        retained.viewModelScope.launch {
            try {
                withContext(NzikDispatchers.DATA) {
                    YtMusic.getArtistPage(browseId.removePrefix(MODIFIED_PREFIX))
                }
                    .onSuccess { online ->
                        fetchedPage = online

                        Database.asyncTransaction {
                            transaction {
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
                                    val current = artistTable.findByIdDirect(browseId)
                                    artistTable.updateMetadata(
                                        id = browseId,
                                        name = PropUtils.retainIfModified(current?.name, online.artist.title),
                                        thumbnailUrl = PropUtils.retainIfModified(current?.thumbnailUrl, online.artist.thumbnail?.url),
                                        isYoutubeArtist = current?.isYoutubeArtist == true,
                                        position = current?.position ?: -1
                                    )
                                }

                                online.sections
                                    .fastFirstOrNull { section ->
                                        section.items.fastAll { it is Innertube.SongItem }
                                    }
                                    ?.items
                                    ?.map { (it as Innertube.SongItem).asMediaItem }
                                    ?.also {
                                        artistTable.findByIdDirect(browseId)?.let { artist -> mapIgnore(artist, *it.toTypedArray()) }
                                    }

                                // Insights can use the YouTube description without requesting the artist page again.
                                val ytDescription = online.description?.takeIf { it.isNotBlank() }
                                if (ytDescription != null) {
                                    artistTable.findByIdDirect(browseId)?.let { current ->
                                        if (current.description != ytDescription) {
                                            artistTable.update(current.copy(description = ytDescription))
                                        }
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
                                fetchedPage = fetchedPage?.withUpdatedVideoDurations(durationsMap)
                            }
                        }
                    }
                    .onFailure { artistFetchFailed = true }
            } finally {
                artistFetchInProgress = false
            }
        }
    }

    LaunchedEffect(offlineMode, isOnline) {
        if (preview == null && !offlineMode && isOnline && fetchedPage == null) fetchArtistPage()
    }

    LaunchedEffect(localArtist?.id) {
        val artist = localArtist ?: return@LaunchedEffect
        mbSyncing = true
        try {
            withContext(NzikDispatchers.DATA) {
                val channelId = browseId.removePrefix(MODIFIED_PREFIX)
                Database.transaction {
                    val current = artistTable.findByIdDirect(artist.id)
                    if (current != null && current.youtubeChannelId != channelId) {
                        artistTable.update(current.copy(youtubeChannelId = channelId))
                    }
                }
                MBMetadataHelper.Default.onArtistViewed(artist.id)
            }
        } finally {
            mbSyncing = false
            mbPausedSeconds = MBMetadataHelper.Default.circuitOpenRemainingSeconds()
        }
    }

    val thumbnailPainter = ImageCacheFactory.Painter(
        if (preview != null) artistPage?.artist?.thumbnail?.url else localArtist?.thumbnailUrl,
    )

    val topContentInset = rememberTopContentInsetProvider(hasNavBar = !offlineMode)

    Skeleton(
        navController = navController,
        tabIndex = selectedTabIndex,
        onTabChanged = { index -> selectedTabIndex = index },
        miniPlayer = miniPlayer,
        topInsetHandledByContent = true,
        topContentInsetProvider = topContentInset,
        navBarContent = { item ->
            if (offlineMode)
                item(1, stringResource(R.string.library_title), R.drawable.library)
            else {
                item(0, stringResource(R.string.artist_overview_title), R.drawable.artist)
                item(1, stringResource(R.string.library_title), R.drawable.library)
            }
        }
    ) { currentTabIndex ->
        val contentPadding = rememberPageChromeContentPadding(
            PaddingValues(bottom = Dimensions.bottomSpacer), topContentInset
        )
        saveableStateHolder.SaveableStateProvider(currentTabIndex) {
            val listState = rememberLazyListState()
            when (artistScreenTabIndex(offlineMode, currentTabIndex)) {
                0 -> {
                    if (artistPage != null) {
                        ArtistOverview(
                            navController, localArtist, artistPage, thumbnailPainter,
                            mbSyncing, { mbSyncing = it },
                            mbPausedSeconds, { mbPausedSeconds = it }, contentPadding, listState
                        )
                    } else if (!isOnline) {
                        CollectionPageStatus(contentPadding) { OfflineContent() }
                    } else if (artistFetchFailed) {
                        CollectionPageStatus(contentPadding) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.common_load_failed))
                                TextButton(onClick = ::fetchArtistPage) {
                                    Text(stringResource(R.string.common_action_retry))
                                }
                            }
                        }
                    } else {
                        ArtistLoadingContent(localArtist?.name, thumbnailPainter, contentPadding, listState,
                            overview = true, cachedArtist = localArtist)
                    }
                }
                1 -> {
                    if (localArtist != null) {
                        ArtistLocalSongs(navController, localArtist, artistPage, thumbnailPainter, retained.songs, contentPadding, listState)
                    } else if (preview != null || (localArtistResolved && (offlineMode || !isOnline || artistFetchFailed))) {
                        CollectionPageStatus(contentPadding) {
                            Text(stringResource(R.string.artist_songs_unavailable_message))
                        }
                    } else {
                        ArtistLoadingContent(artistPage?.artist?.title, thumbnailPainter, contentPadding, listState, overview = false)
                    }
                }
            }
        }
    }
}

private class ArtistScreenData : ViewModel() {
    val localArtist = mutableStateOf<Artist?>(null)
    val localArtistResolved = mutableStateOf(false)
    val artistPage = mutableStateOf<ArtistPage?>(null)
    val songs = mutableStateOf<List<Song>?>(null)
    val fetching = mutableStateOf(false)
    val failed = mutableStateOf(false)

    companion object {
        val Factory = viewModelFactory { initializer { ArtistScreenData() } }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ArtistLoadingContent(
    title: String?,
    artwork: Painter,
    contentPadding: PaddingValues,
    listState: LazyListState,
    overview: Boolean,
    cachedArtist: Artist? = null,
) {
    val follow: @Composable () -> Unit = {
        ArtistFollowButton(likeState = null, onClick = {}, enabled = false)
    }
    DynamicOrientationLayout(artwork, artistThumbnailShape(), contentPadding, isLoading = true) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item("header") {
                CollectionArtworkHeader(
                    title = title ?: stringResource(R.string.library_artists_title),
                    artwork = artwork,
                    disableScrollingText = true,
                    subtitle = { ItemTextPlaceholder(null, typography().s, width = 0.45f) },
                    isLoading = true,
                    action = {
                        CollectionArtworkShareButton(shareUrl = null)
                    },
                )
            }
            item("action_buttons") {
                val padding = Modifier.padding(top = 12.dp, bottom = 8.dp)
                if (overview) {
                    val visible = ToolbarSurface.ARTIST_OVERVIEW.visibleActions()
                    val actions = mapOf(
                        ToolbarControl.SEARCH to artistLibraryToolbarAction(ToolbarControl.SEARCH),
                        ToolbarControl.SHUFFLE to SongShuffler { emptyList() },
                        ToolbarControl.PLAY_NEXT to PlayNext {},
                        ToolbarControl.ENQUEUE to Enqueue {},
                        ToolbarControl.RADIO to Radio { emptyList() },
                        ToolbarControl.SELECT to ItemSelector<Song>(),
                        ToolbarControl.ADD_TO_PLAYLIST to artistLibraryToolbarAction(ToolbarControl.ADD_TO_PLAYLIST),
                        ToolbarControl.DOWNLOAD to DownloadAllSongsDialog { emptyList() },
                        ToolbarControl.DELETE_DOWNLOADS to DeleteAllDownloadedSongsDialog { emptyList() },
                    )
                    CollectionButtonToolbar(
                        bookmark = follow,
                        buttons = visible.mapNotNull(actions::get),
                        bookmarkWidth = 100.dp,
                        enabled = false, modifier = padding,
                    )
                } else {
                    val visible = ToolbarSurface.ARTIST_LIBRARY.visibleActions()
                    val actions = visible.filterNot { it == ToolbarControl.FOLLOW }
                    CollectionButtonToolbar(
                        bookmark = follow,
                        buttons = actions.map { artistLibraryToolbarAction(it) },
                        enabled = false,
                        bookmarkWidth = 100.dp, modifier = padding,
                    )
                }
            }
            if (overview) item("info_and_community") {
                InfoAndCommunity(
                    rating = cachedArtist?.rating,
                    ratingVotes = cachedArtist?.ratingVotes,
                    year = cachedArtist?.beginYear?.toString(),
                    countryCode = cachedArtist?.countryCode,
                    keywords = cachedArtist?.keywords,
                    links = cachedArtist?.links,
                    bio = cachedArtist?.wikipediaBio,
                    lastSyncAt = cachedArtist?.mbLastFetch,
                    isLoading = true,
                )
            }
            item("songs") { SongItemPlaceholders(count = 6) }
        }
    }
}
